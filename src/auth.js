const zlib = require('zlib');
const { promisify } = require('util');
const { BufferJSON, initAuthCreds, proto } = require('@whiskeysockets/baileys');

const gzip = promisify(zlib.gzip);
const gunzip = promisify(zlib.gunzip);

// --- FIRESTORE AUTH ADAPTER FOR BAILEYS (single compressed snapshot) ---
// The whole session (creds + every signal key) is kept in memory, gzipped and stored
// as ONE Firestore document. Loading the session costs 1 read instead of thousands.
// Only if the compressed snapshot outgrows a document (1 MiB) is it split into a few parts.
const PART_SIZE = 900 * 1024;
const FLUSH_DELAY_MS = 20 * 1000;     // debounce: many key updates -> one write
const MAIN_ID = 'session';

async function useFirestoreAuthState(db, collectionName = 'auth_store') {
    const collection = db.collection(collectionName);
    const mainRef = collection.doc(MAIN_ID);
    const partRef = (i) => collection.doc(`${MAIN_ID}_${i}`);

    const keys = new Map();      // "type-id" -> serialized string
    let credsRef = null;         // live creds object handed to Baileys
    let dirty = false;
    let closed = false;          // set by clearAll(): no more writes
    let knownParts = 1;          // parts currently stored, so shrinking cleans up leftovers
    let timer = null;
    let flushing = null;

    // ---- load (1 read, or a few if split) ----
    let creds = null;
    const main = await mainRef.get();
    if (main.exists) {
        const d = main.data();
        try {
            const parts = d.parts || 1;
            const bufs = [Buffer.from(d.data)];
            if (parts > 1) {
                const rest = await db.getAll(...Array.from({ length: parts - 1 }, (_, i) => partRef(i + 1)));
                for (const s of rest) bufs.push(Buffer.from(s.data().data));
            }
            const json = (await gunzip(Buffer.concat(bufs))).toString('utf8');
            const snap = JSON.parse(json);
            creds = JSON.parse(snap.creds, BufferJSON.reviver);
            for (const [id, str] of Object.entries(snap.keys || {})) keys.set(id, str);
            knownParts = parts;
            console.log(`System: Auth snapshot loaded (${keys.size} keys, ${parts} part(s)).`);
        } catch (err) {
            // Unreadable snapshot: do not overwrite it blindly, let the caller back off
            throw new Error(`Auth snapshot is corrupt: ${err.message}`);
        }
    }
    credsRef = creds || initAuthCreds();

    // ---- save ----
    async function flush() {
        if (closed || !dirty) return;
        if (flushing) return flushing;
        flushing = (async () => {
            const wasDirty = dirty;
            dirty = false;
            try {
                const snap = {
                    creds: JSON.stringify(credsRef, BufferJSON.replacer),
                    keys: Object.fromEntries(keys)
                };
                const packed = await gzip(Buffer.from(JSON.stringify(snap), 'utf8'));
                const parts = Math.max(1, Math.ceil(packed.length / PART_SIZE));
                const batch = db.batch();
                const meta = { updatedAt: Date.now(), parts };
                for (let i = 0; i < parts; i++) {
                    const chunk = packed.subarray(i * PART_SIZE, (i + 1) * PART_SIZE);
                    if (i === 0) batch.set(mainRef, { ...meta, data: chunk });
                    else batch.set(partRef(i), { data: chunk });
                }
                for (let i = parts; i < knownParts; i++) batch.delete(partRef(i));
                if (closed) return;
                await batch.commit();
                knownParts = parts;
            } catch (err) {
                if (wasDirty) dirty = true;      // retry on the next tick
                console.error('System: Error saving auth snapshot (will retry):', err.message);
                schedule(/quota|RESOURCE_EXHAUSTED|exceeded/i.test(err.message) || err.code === 8 ? 10 * 60 * 1000 : FLUSH_DELAY_MS);
            } finally {
                flushing = null;
                if (dirty && !timer) schedule();   // changed while saving
            }
        })();
        return flushing;
    }

    function schedule(delay = FLUSH_DELAY_MS) {
        if (closed || timer) return;
        timer = setTimeout(() => { timer = null; flush(); }, delay);
        timer.unref();
    }

    function markDirty() {
        dirty = true;
        schedule();
    }

    async function flushNow() {
        if (timer) { clearTimeout(timer); timer = null; }
        if (flushing) await flushing;
        dirty = dirty || false;
        return flush();
    }

    // ---- wipe everything (relog / logged out) ----
    async function clearAll() {
        closed = true;
        if (timer) { clearTimeout(timer); timer = null; }
        if (flushing) { try { await flushing; } catch (e) {} }
        keys.clear();
        const batch = db.batch();
        batch.delete(mainRef);
        for (let i = 1; i < Math.max(knownParts, 1); i++) batch.delete(partRef(i));
        await batch.commit();
    }

    return {
        state: {
            creds: credsRef,
            keys: {
                get: async (type, ids) => {
                    const data = {};
                    for (const id of ids) {
                        const str = keys.get(`${type}-${id}`);
                        let value = str ? JSON.parse(str, BufferJSON.reviver) : null;
                        if (type === 'app-state-sync-key' && value) {
                            value = proto.Message.AppStateSyncKeyData.fromObject(value);
                        }
                        data[id] = value;
                    }
                    return data;
                },
                set: async (data) => {
                    for (const category in data) {
                        for (const id in data[category]) {
                            const value = data[category][id];
                            const docId = `${category}-${id}`;
                            if (value) keys.set(docId, JSON.stringify(value, BufferJSON.replacer));
                            else keys.delete(docId);
                        }
                    }
                    markDirty();
                }
            }
        },
        saveCreds: async () => { markDirty(); },
        flushNow,
        clearAll
    };
}

module.exports = { useFirestoreAuthState };
