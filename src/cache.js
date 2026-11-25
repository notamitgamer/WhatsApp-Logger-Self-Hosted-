const zlib = require('zlib');
const fs = require('fs');
const { db } = require('./firebase');
const { EXCLUDED_JIDS } = require('./config');
const { clients } = require('./sseManager');

// ---------------------------------------------------------------------------
// In-memory store, write-through to Firestore, with a compressed snapshot.
//
//  * Everything the API serves lives in RAM (chatsCache / messagesCache).
//  * This server is the only writer, so new data goes to RAM first and is then
//    queued for Firestore. No permanent listeners (they re-read every document).
//  * Boot cost: the whole cache is stored gzipped in a few "CacheSnapshot"
//    documents, so a boot is ~2 reads instead of one read per message.
//  * If Firestore is down or over quota, nothing is lost: writes stay in a
//    queue (also mirrored to disk) and are retried until they go through.
// ---------------------------------------------------------------------------

const SNAP_COLLECTION = 'CacheSnapshot';
const PART_BYTES = 800 * 1024;          // Firestore documents max out at 1 MiB
const SNAPSHOT_EVERY_MS = 10 * 60 * 1000;
const PARTS_PER_COMMIT = 6;            // keep each commit well under Firestore's 10 MiB request limit
const FLUSH_EVERY_MS = 15 * 1000;
const QUEUE_FILE = './pending-writes.json';

const chatsCache = new Map();      // chatId -> chat data
const messagesCache = new Map();   // chatId -> Map(msgId -> message data)
let cacheReady = false;
let dirty = false;                 // memory changed since the last snapshot
let failures = 0;
let lastSnapshotAt = null;
let knownParts = 0;
let knownStamp = null;            // id prefix of the stored snapshot ('' = legacy p0,p1 names, null = none)               // snapshot parts currently stored (tracked in RAM, so saving needs no read)

// key -> { path: [..], data }  (all writes are merge-sets, so they coalesce by key)
const queue = new Map();
const deferredDeletes = [];        // revokes that arrived before the cache finished loading
let flushing = false;
let saving = false;
let lastQueueWarn = 0;

// ------------------------------- helpers ----------------------------------
// Quota errors only clear at the daily reset, so hammering Firestore just burns more of it
function isQuotaError(err) {
    return !!err && (err.code === 8 || /RESOURCE_EXHAUSTED|Quota exceeded/i.test(err.message || ''));
}
// INVALID_ARGUMENT (3) / FAILED_PRECONDITION (9) / OUT_OF_RANGE (11): the data itself is the problem
function isDataError(err) { return !!err && (err.code === 3 || err.code === 11); }
const QUOTA_BACKOFF_MS = 30 * 60 * 1000;
let flushPausedUntil = 0;

function backoffMs() {
    return Math.min(5000 * Math.pow(2, failures), 300000);
}

function maxTimestamp() {
    let max = 0;
    messagesCache.forEach(map => map.forEach(m => { if (m.timestamp > max) max = m.timestamp; }));
    return max;
}

function broadcastChat(type, doc) {
    const payload = `event: update\ndata: ${JSON.stringify([{ type, doc }])}\n\n`;
    clients.chats.forEach(res => { try { res.write(payload); } catch (e) {} });
}

// `ms` (message secret, needed to decrypt later edits) stays on the server
function pub(m) {
    if (!m || (m.ms === undefined && m.sa === undefined)) return m;
    const { ms, sa, ...rest } = m;
    return rest;
}

function broadcastMessage(type, chatId, rawData) {
    const data = pub(rawData);
    const chatClients = clients.messages.get(chatId);
    if (chatClients) {
        const payload = `event: update\ndata: ${JSON.stringify([{ type, doc: data }])}\n\n`;
        chatClients.forEach(res => { try { res.write(payload); } catch (e) {} });
    }
    if (clients.sync.size > 0) {
        const payload = `event: update\ndata: ${JSON.stringify([{ type, doc: { ...data, chatId } }])}\n\n`;
        clients.sync.forEach(res => { try { res.write(payload); } catch (e) {} });
    }
}

// ------------------------- write queue (offline safe) ----------------------
let persistTimer = null;
function persistQueue() {
    if (persistTimer) return;
    persistTimer = setTimeout(() => {
        persistTimer = null;
        try {
            fs.writeFileSync(QUEUE_FILE, JSON.stringify([...queue.entries()]));
        } catch (e) {}
    }, 2000);
}

function loadQueueFromDisk() {
    try {
        if (!fs.existsSync(QUEUE_FILE)) return;
        const entries = JSON.parse(fs.readFileSync(QUEUE_FILE, 'utf8'));
        entries.forEach(([k, op]) => { if (!queue.has(k)) queue.set(k, op); });
        if (entries.length) console.log(`System: Restored ${entries.length} unsent writes from disk.`);
    } catch (e) {}
}

function enqueue(key, path, data) {
    const existing = queue.get(key);
    queue.set(key, { path, data: existing ? { ...existing.data, ...data } : data });
    persistQueue();
}

function refFor(path) {
    // [ 'Chats', id ] or [ 'Chats', id, 'Messages', msgId ]
    let ref = db.collection(path[0]).doc(path[1]);
    if (path.length === 4) ref = ref.collection(path[2]).doc(path[3]);
    return ref;
}

async function flushQueue() {
    if (flushing || queue.size === 0 || Date.now() < flushPausedUntil) return;
    flushing = true;
    try {
        while (queue.size > 0) {
            const batchEntries = [...queue.entries()].slice(0, 400);
            try {
                const batch = db.batch();
                batchEntries.forEach(([, op]) => batch.set(refFor(op.path), op.data, { merge: true }));
                await batch.commit();
            } catch (err) {
                // A permanently invalid write (bad data) must not block everything behind it.
                // Quota / network errors are retried later; anything else is isolated per write.
                if (isQuotaError(err) || !isDataError(err)) throw err;
                console.error(`System: Batch rejected (${err.message}). Writing individually to find the bad one.`);
                for (const [k, op] of batchEntries) {
                    try {
                        await refFor(op.path).set(op.data, { merge: true });
                    } catch (e2) {
                        if (isQuotaError(e2) || !isDataError(e2)) throw e2;
                        console.error(`System: Dropping unwritable ${op.path.join('/')}: ${e2.message}`);
                    }
                    if (queue.get(k) === op) queue.delete(k);
                }
                persistQueue();
                continue;
            }
            batchEntries.forEach(([k, op]) => {
                // keep the entry if it was updated again while the batch was in flight
                if (queue.get(k) === op) queue.delete(k);
            });
            persistQueue();
        }
        failures = 0;
    } catch (err) {
        if (isQuotaError(err)) flushPausedUntil = Date.now() + 10 * 60 * 1000;
        const now = Date.now();
        if (now - lastQueueWarn > 60000) {
            lastQueueWarn = now;
            console.error(`System: Firestore unavailable (${err.message}). ${queue.size} writes queued, will retry.`);
        }
    } finally {
        flushing = false;
    }
}

// ------------------------------ public writers ------------------------------
// true if `data` would change anything on `existing`
function changed(existing, data) {
    if (!existing) return true;
    return Object.keys(data).some(k => JSON.stringify(existing[k]) !== JSON.stringify(data[k]));
}

function upsertChat(id, data) {
    if (!id) return;
    if (!EXCLUDED_JIDS.has(id) && !changed(chatsCache.get(id), data)) return;   // e.g. contacts re-sent on every connect
    enqueue(`c:${id}`, ['Chats', id], data);   // always saved, like before
    if (EXCLUDED_JIDS.has(id)) return;
    const existed = chatsCache.has(id);
    const merged = { ...(chatsCache.get(id) || {}), id, ...data };
    chatsCache.set(id, merged);
    dirty = true;
    broadcastChat(existed ? 'modified' : 'added', merged);
}

function upsertMessage(chatId, msgId, data) {
    if (!chatId || !msgId) return;
    if (!changed(messagesCache.get(chatId)?.get(msgId), data)) return;           // re-delivered message: nothing new
    enqueue(`m:${chatId}/${msgId}`, ['Chats', chatId, 'Messages', msgId], data);
    if (!messagesCache.has(chatId)) messagesCache.set(chatId, new Map());
    const map = messagesCache.get(chatId);
    const existed = map.has(msgId);
    const merged = { ...(map.get(msgId) || {}), ...data };
    map.set(msgId, merged);
    dirty = true;
    broadcastMessage(existed ? 'modified' : 'added', chatId, merged);
}

// "Delete for everyone": flag the message we already logged (never create a stub)
function markMessageDeleted(chatId, msgId) {
    const apply = () => {
        let cid = chatId;
        let existing = messagesCache.get(cid)?.get(msgId);
        if (!existing) {
            // The revoke can name the chat differently (phone vs LID id) than where the message was stored,
            // which is exactly what happens for messages that were edited first. Find it by message id.
            for (const [otherId, m] of messagesCache) {
                if (m.has(msgId)) { cid = otherId; existing = m.get(msgId); break; }
            }
        }
        if (!existing) { console.log(`System: Delete event for unknown message ${msgId} (chat ${chatId}) ignored.`); return; }
        if (existing.deleted) return;
        console.log(`System: Message ${msgId} marked deleted.`);
        upsertMessage(cid, msgId, { deleted: true, deletedAt: Math.floor(Date.now() / 1000) });
    };
    if (!cacheReady) deferredDeletes.push(apply); else apply();
}

// Edits: the original text stays in `text`; every later version is appended to `edits`.
function editMessage(chatId, msgId, newText, ts) {
    const apply = () => {
        const existing = messagesCache.get(chatId)?.get(msgId);
        if (!existing) {
            // We never saw the original (sent before the logger was linked): store the edit as the message
            upsertMessage(chatId, msgId, { id: msgId, text: newText, type: 'text', timestamp: ts, fromMe: false, edited: true, editedAt: Math.floor(Date.now() / 1000), edits: [] });
            return;
        }
        const edits = Array.isArray(existing.edits) ? existing.edits : [];
        const latest = edits.length ? edits[edits.length - 1].text : existing.text;
        if (latest === newText) return;                      // duplicate event
        upsertMessage(chatId, msgId, { edited: true, editedAt: Math.floor(Date.now() / 1000), edits: [...edits, { text: newText, ts }] });
        console.log(`System: Edit logged for message ${msgId} (${edits.length + 1} edit(s)).`);
        const chat = chatsCache.get(chatId);
        if (chat && (chat.lastActive || 0) <= Math.max(ts, existing.timestamp || 0)) upsertChat(chatId, { preview: newText });
    };
    if (!cacheReady) deferredDeletes.push(apply); else apply();
}

function getStats() {
    let messages = 0;
    messagesCache.forEach(m => { messages += m.size; });
    return {
        cacheReady,
        chats: chatsCache.size,
        messages,
        queuedWrites: queue.size,
        snapshotParts: knownParts,
        snapshotDirty: dirty,
        quotaPaused: Date.now() < flushPausedUntil,
        quotaPausedUntil: flushPausedUntil > Date.now() ? flushPausedUntil : null,
        lastSnapshotAt,
        memoryMB: Math.round(process.memoryUsage().rss / 1048576)
    };
}

// -------------------------------- snapshot ---------------------------------
function buildSnapshotParts() {
    const messages = {};
    messagesCache.forEach((map, chatId) => { messages[chatId] = [...map.values()]; });
    const json = JSON.stringify({ v: 1, chats: [...chatsCache.values()], messages });
    const gz = zlib.gzipSync(Buffer.from(json), { level: 9 });
    const parts = [];
    for (let i = 0; i < gz.length; i += PART_BYTES) parts.push(gz.subarray(i, i + PART_BYTES));
    return { parts, bytes: gz.length };
}

async function saveSnapshot() {
    if (saving || !cacheReady || !dirty || Date.now() < flushPausedUntil) return;
    saving = true;
    dirty = false;          // anything that changes while we save sets it again
    try {
        const { parts, bytes } = buildSnapshotParts();
        // New parts get a fresh id prefix, so a half-finished save can never mix with the old snapshot.
        const stamp = Date.now().toString(36);
        const col = db.collection(SNAP_COLLECTION);

        for (let i = 0; i < parts.length; i += PARTS_PER_COMMIT) {
            const batch = db.batch();
            parts.slice(i, i + PARTS_PER_COMMIT).forEach((buf, j) => batch.set(col.doc(`s${stamp}_p${i + j}`), { data: buf }));
            await batch.commit();
        }

        // The meta document switches readers to the new parts in a single write
        const oldStamp = knownStamp, oldParts = knownParts;
        await col.doc('meta').set({
            stamp,
            parts: parts.length,
            savedAt: Math.floor(Date.now() / 1000),
            maxTs: maxTimestamp(),
            messages: [...messagesCache.values()].reduce((n, m) => n + m.size, 0),
            chats: chatsCache.size
        });
        knownStamp = stamp;
        knownParts = parts.length;
        lastSnapshotAt = Date.now();
        console.log(`System: Cache snapshot saved (${(bytes / 1024).toFixed(0)} KB in ${parts.length} doc(s)).`);

        // Clean up the previous snapshot (best effort)
        if (oldStamp !== null && oldStamp !== stamp) {
            try {
                const del = db.batch();
                for (let i = 0; i < oldParts; i++) del.delete(col.doc(oldStamp ? `s${oldStamp}_p${i}` : `p${i}`));
                await del.commit();
            } catch (e) {}
        }
    } catch (err) {
        dirty = true;
        if (isQuotaError(err)) flushPausedUntil = Date.now() + 10 * 60 * 1000;
        console.error(`System: Snapshot save failed (${err.message}). Will retry.`);
    } finally {
        saving = false;
    }
}

function absorb(chats, messages) {
    // Anything that arrived while loading (live WhatsApp messages) wins over stored data
    chats.forEach(c => {
        if (EXCLUDED_JIDS.has(c.id)) return;
        chatsCache.set(c.id, { ...c, ...(chatsCache.get(c.id) || {}) });
    });
    Object.entries(messages).forEach(([chatId, list]) => {
        if (!messagesCache.has(chatId)) messagesCache.set(chatId, new Map());
        const map = messagesCache.get(chatId);
        list.forEach(m => { map.set(m.id, { ...m, ...(map.get(m.id) || {}) }); });
    });
}

async function loadSnapshot() {
    const metaSnap = await db.collection(SNAP_COLLECTION).doc('meta').get();   // 1 read
    if (!metaSnap.exists) return null;
    const meta = metaSnap.data();
    knownParts = meta.parts || 0;
    knownStamp = meta.stamp || '';
    try {
        const bufs = [];
        for (let i = 0; i < meta.parts; i++) {
            const id = meta.stamp ? `s${meta.stamp}_p${i}` : `p${i}`;
            const part = await db.collection(SNAP_COLLECTION).doc(id).get();   // 1 read each
            if (!part.exists) throw new SnapshotCorrupt(`snapshot part ${i} missing`);
            bufs.push(part.data().data);   // Buffer
        }
        const parsed = JSON.parse(zlib.gunzipSync(Buffer.concat(bufs)).toString());
        absorb(parsed.chats || [], parsed.messages || {});
    } catch (err) {
        if (err instanceof SnapshotCorrupt || err instanceof SyntaxError || /incorrect header|unexpected end/i.test(err.message)) {
            console.error(`System: Snapshot unreadable (${err.message}); rebuilding from Firestore.`);
            return null;
        }
        throw err;
    }
    return meta;
}
class SnapshotCorrupt extends Error {}

// One-time migration when no snapshot exists yet (the only full read)
async function loadEverything() {
    console.log("System: No snapshot found. Doing the one-time full read from Firestore...");
    const chats = [];
    (await db.collection('Chats').get()).forEach(doc => chats.push({ id: doc.id, ...doc.data() }));
    const messages = {};
    (await db.collectionGroup('Messages').get()).forEach(doc => {
        const chatId = doc.ref.parent.parent.id;
        (messages[chatId] = messages[chatId] || []).push(doc.data());
    });
    absorb(chats, messages);
}

// Messages written after the snapshot (e.g. the server crashed before saving one)
async function catchUp(meta) {
    try {
        const msgs = await db.collectionGroup('Messages')
            .where('timestamp', '>', meta.maxTs || 0).get();
        const messages = {};
        msgs.forEach(doc => {
            const chatId = doc.ref.parent.parent.id;
            (messages[chatId] = messages[chatId] || []).push(doc.data());
        });
        const chats = [];
        (await db.collection('Chats').where('lastActive', '>', meta.maxTs || 0).get())
            .forEach(doc => chats.push({ id: doc.id, ...doc.data() }));
        absorb(chats, messages);
        if (msgs.size) dirty = true;
        console.log(`System: Catch-up found ${msgs.size} newer messages.`);
    } catch (err) {
        // Needs a one-time collection-group index; Firestore puts a creation link in the message.
        console.error(`System: Catch-up skipped (${err.message})`);
    }
}

async function warmCache() {
    try {
        console.log("System: Warming cache from Firestore...");
        loadQueueFromDisk();
        const meta = await loadSnapshot();
        if (meta) {
            await catchUp(meta);
        } else {
            await loadEverything();
            dirty = true;
        }

        cacheReady = true;
        failures = 0;
        let msgCount = 0;
        messagesCache.forEach(m => { msgCount += m.size; });
        console.log(`System: Cache warm. ${chatsCache.size} chats, ${msgCount} messages.`);

        deferredDeletes.splice(0).forEach(fn => fn());
        await saveSnapshot();
    } catch (err) {
        failures++;
        const backoff = isQuotaError(err) ? QUOTA_BACKOFF_MS : backoffMs();
        console.error(`System: Cache warm failed (attempt ${failures}). Retrying in ${backoff / 1000}s.`, err.message);
        setTimeout(warmCache, backoff);
    }
}

// Background jobs + graceful shutdown (Render sends SIGTERM before restarting)
setInterval(flushQueue, FLUSH_EVERY_MS).unref();
setInterval(saveSnapshot, SNAPSHOT_EVERY_MS).unref();

let shuttingDown = false;
async function shutdown() {
    if (shuttingDown) return;
    shuttingDown = true;
    console.log("System: Shutting down. Saving pending writes and snapshot...");
    try {
        await Promise.race([
            (async () => { try { await require('./whatsapp').flushAuth(); } catch (e) {} await flushQueue(); await saveSnapshot(); })(),
            new Promise(r => setTimeout(r, 8000))
        ]);
    } catch (e) {}
    try { fs.writeFileSync(QUEUE_FILE, JSON.stringify([...queue.entries()])); } catch (e) {}
    process.exit(0);
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

module.exports = {
    isQuotaError,
    chatsCache,
    messagesCache,
    warmCache,
    isCacheReady: () => cacheReady,
    upsertChat,
    upsertMessage,
    markMessageDeleted,
    editMessage,
    pub,
    getStats
};
