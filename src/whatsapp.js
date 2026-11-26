const {
    default: makeWASocket,
    DisconnectReason,
    fetchLatestBaileysVersion,
    Browsers
} = require('@whiskeysockets/baileys');
const pino = require('pino');
const { db } = require('./firebase');
const { useFirestoreAuthState } = require('./authState');
const { upsertChat, upsertMessage, markMessageDeleted, editMessage, isQuotaError, chatsCache, messagesCache } = require('./cache');
const { describeMessage, extractEdit, extractRevoke, unwrap } = require('./messageParser');
const { decryptSecretEdit, isMessageEdit } = require('./secretEdit');

// --- BAILEYS SETUP ---
let qrCodeData = null;
let sock = null;
let isConnected = false;

let consecutiveAuthFailures = 0;
let consecutiveConnectFailures = 0;

// Each start gets a generation number; events from older sockets are ignored.
// This is what makes relog safe (the old socket can never restart or rewrite auth).
let generation = 0;
let startTimer = null;
let currentAuth = null;

function scheduleStart(ms) {
    if (startTimer) clearTimeout(startTimer);
    startTimer = setTimeout(() => { startTimer = null; startWhatsApp(); }, ms);
}

// Finds a stored message by id (the edit may arrive under a different JID spelling than the original)
function findStoredMessage(chatId, id) {
    const direct = messagesCache.get(chatId)?.get(id);
    if (direct) return { chatId, msg: direct };
    for (const [cid, map] of messagesCache) {
        const m = map.get(id);
        if (m) return { chatId: cid, msg: m };
    }
    return null;
}

// Who sent a message: normalized user parts of every JID spelling (phone/LID), group JID excluded
function authorIds(key, meIds) {
    if (key.fromMe) return ['me'];
    const out = new Set();
    for (const j of [key.participant, key.participantAlt, key.remoteJid, key.remoteJidAlt]) {
        if (!j || typeof j !== 'string' || j.endsWith('@g.us') || j.endsWith('@broadcast')) continue;
        out.add(j.split('@')[0].split(':')[0]);
    }
    return [...out];
}

async function handleSecretEdit(msg, se, remoteJid) {
    const id = se.targetMessageKey?.id;
    const found = id && findStoredMessage(se.targetMessageKey.remoteJid || remoteJid, id);
    if (!found || !found.msg.ms) {
        console.log(`System: Encrypted edit for ${id} skipped (${found ? 'original has no saved secret' : 'original not in log'}).`);
        if (found) editMessage(found.chatId, id, '[--- Edited: new text could not be read ---]', Math.floor(Date.now() / 1000));   // at least flag it as edited
        return;
    }
    const k = msg.key;
    // Only the author may edit: ignore forged edits from other group members
    const origIds = found.msg.sa;
    if (!Array.isArray(origIds) || !origIds.length) {
        console.log(`System: Encrypted edit for ${id} skipped (original sender unknown).`);
        return;
    }
    const editIds = authorIds(k);
    if (!editIds.some(x => origIds.includes(x))) {
        console.log(`System: Encrypted edit for ${id} rejected (editor is not the original sender).`);
        return;
    }
    const me = sock?.user || {};
    const senders = [k.participant, k.remoteJid, k.participantAlt, k.remoteJidAlt, ...(k.fromMe ? [me.id, me.lid] : [])];
    // The key may have been derived with the phone JID while the chat is addressed by LID (or the reverse)
    const chat = chatsCache.get(found.chatId) || chatsCache.get(k.remoteJid);
    if (chat?.phoneNumber) senders.push(`${chat.phoneNumber}@s.whatsapp.net`);
    const lm = sock?.signalRepository?.lidMapping;
    for (const j of [k.participant, k.remoteJid]) {
        if (!j || j.endsWith('@g.us')) continue;
        try {
            if (j.endsWith('@lid') && lm?.getPNForLID) senders.push(await lm.getPNForLID(j));
            if (j.endsWith('@s.whatsapp.net') && lm?.getLIDForPN) senders.push(await lm.getLIDForPN(j));
        } catch (e) {}
    }
    const decoded = decryptSecretEdit(se, { secret: Buffer.from(found.msg.ms, 'base64'), senders });
    if (!decoded) {
        console.log(`System: Encrypted edit for ${id} could not be decrypted. tried=[${[...new Set(senders.filter(Boolean))].join(' | ')}] iv=${se.encIv?.length} payload=${se.encPayload?.length} secret=${found.msg.ms ? Buffer.from(found.msg.ms, 'base64').length : 0}`);
        editMessage(found.chatId, id, '[--- Edited: new text could not be read ---]', Math.floor(Date.now() / 1000));
        return;
    }
    const edit = extractEdit(decoded, { id, remoteJid: found.chatId }, undefined);
    let text = edit?.text;
    let ts = edit?.ts;
    if (!text) {
        const d = describeMessage(decoded.protocolMessage?.editedMessage || decoded);
        text = d?.text;
    }
    if (!text) {
        console.log(`System: Encrypted edit for ${id} decrypted but had no text (keys=[${Object.keys(decoded).join(',')}]).`);
        return;
    }
    if (!ts) {
        const ms = decoded.protocolMessage?.timestampMs;
        ts = ms ? Math.floor((typeof ms.toNumber === 'function' ? ms.toNumber() : Number(ms)) / 1000) : Math.floor(Date.now() / 1000);
    }
    editMessage(found.chatId, id, text, ts);
}

async function startWhatsApp() {
    const logger = pino({ level: 'silent' });
    const myGen = ++generation;

    let authResult;
    try {
        authResult = await useFirestoreAuthState(db, 'auth_store');
    } catch (err) {
        consecutiveAuthFailures++;
        const backoff = isQuotaError(err) ? 30 * 60 * 1000 : Math.min(5000 * Math.pow(2, consecutiveAuthFailures), 300000); // cap 5 min (30 min on quota errors)
        console.error(`System: Auth state read failed (attempt ${consecutiveAuthFailures}). Retrying in ${backoff / 1000}s.`);
        scheduleStart(backoff);
        return;
    }
    if (myGen !== generation) return;   // a relog happened while loading
    consecutiveAuthFailures = 0; // Reset on success

    const { state, saveCreds } = authResult;
    currentAuth = authResult;
    const { version } = await fetchLatestBaileysVersion();

    console.log("System: Connecting to WhatsApp servers...");

    sock = makeWASocket({
        version,
        logger,
        auth: state,
        browser: Browsers.ubuntu('Chrome'),
        syncFullHistory: true
    });

    sock.ev.on('connection.update', async (update) => {
        if (myGen !== generation) return;
        const { connection, lastDisconnect, qr } = update;

        if (qr) {
            console.log("System: No valid credentials. New QR Code generated.");
            qrCodeData = qr;
            isConnected = false;
        }

        if (connection === 'close') {
            isConnected = false;
            const statusCode = lastDisconnect?.error?.output?.statusCode;
            const shouldReconnect = statusCode !== DisconnectReason.loggedOut;

            if (statusCode === DisconnectReason.restartRequired) {
                // Normal right after pairing: flush fresh creds to Firestore before restarting.
                console.log("System: Pairing finished, flushing auth and restarting socket...");
                try {
                    await authResult.flushNow();
                } catch (err) {
                    console.error("System: Flush before restart failed:", err.message);
                }
                scheduleStart(0);
            } else if (shouldReconnect) {
                consecutiveConnectFailures++;
                const backoff = Math.min(5000 * Math.pow(2, consecutiveConnectFailures), 300000); // cap 5 min
                console.log(`System: Connection closed (Status: ${statusCode}). Reconnecting in ${backoff / 1000}s...`);
                scheduleStart(backoff);
            } else {
                console.log("System: Device Logged Out. Wiping session from Firestore.");
                let wiped = true;
                try { await authResult.clearAll(); } catch (err) { wiped = false; console.error("System: Could not wipe auth:", err.message); }
                qrCodeData = null;
                consecutiveConnectFailures = 0;
                scheduleStart(wiped ? 0 : 60 * 1000);   // if the wipe failed, don't hammer the dead session
            }
        } else if (connection === 'open') {
            console.log("System: Connection Open and Authenticated. Firebase Auth Sync Active.");
            qrCodeData = null;
            isConnected = true;
            consecutiveConnectFailures = 0; // Reset on success
            authResult.flushNow().catch(() => {});   // persist the fresh session right away
        }
    });

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('contacts.upsert', async (contacts) => {
        for (const contact of contacts) {
            let updateData = {};
            const displayName = contact.name || contact.notify;

            if (displayName) updateData.displayName = displayName;

            if (contact.id && contact.id.endsWith('@s.whatsapp.net')) {
                updateData.phoneNumber = contact.id.split('@')[0];
            }

            const primaryId = contact.lid || contact.id;

            if (primaryId && Object.keys(updateData).length > 0) {
                try {
                    upsertChat(primaryId, updateData);

                    if (contact.lid && contact.id !== contact.lid) {
                        upsertChat(contact.id, updateData);
                    }
                } catch (err) {}
            }
        }
    });

    // --- Group names -----------------------------------------------------------
    const groupAttempted = new Map();   // jid -> last attempt time
    const ensureGroupName = async (jid) => {
        if (!jid.endsWith('@g.us')) return;
        if (chatsCache.get(jid)?.displayName) return;
        const last = groupAttempted.get(jid) || 0;
        if (Date.now() - last < 10 * 60 * 1000) return;
        groupAttempted.set(jid, Date.now());
        try {
            const meta = await sock.groupMetadata(jid);
            if (meta?.subject) upsertChat(jid, { displayName: meta.subject, id: jid });
        } catch (err) {}
    };
    sock.ev.on('groups.upsert', (groups) => {
        for (const g of groups || []) if (g?.id && g.subject) upsertChat(g.id, { displayName: g.subject, id: g.id });
    });
    sock.ev.on('groups.update', (updates) => {
        for (const g of updates || []) if (g?.id && g.subject) upsertChat(g.id, { displayName: g.subject, id: g.id });
    });

    // --- Revokes / edits that arrive as updates --------------------------------
    sock.ev.on('messages.update', async (updates) => {
        for (const { key, update } of updates) {
            try {
                if (!key?.id || key.remoteJid === 'status@broadcast') continue;
                const revoked = update?.messageStubType === 1 || update?.messageStubType === 'REVOKE' ||
                    extractRevoke(update?.message);
                if (revoked) { markMessageDeleted(key.remoteJid, key.id); continue; }
                const ts = update?.messageTimestamp ? Number(update.messageTimestamp.low ?? update.messageTimestamp) : undefined;
                const edit = extractEdit(update?.message, key, ts);
                if (edit) editMessage(edit.key.remoteJid || key.remoteJid, edit.key.id, edit.text, edit.ts);
            } catch (err) { console.error('System: messages.update handling failed:', err.message); }
        }
    });

    sock.ev.on('messages.upsert', async ({ messages, type }) => {
        if (type !== 'notify' && type !== 'append') return;

        for (const msg of messages) {
            try {
                if (!msg.message) continue;

                const remoteJid = msg.key.remoteJid;
                if (remoteJid === 'status@broadcast') continue;

                // "Delete for everyone": keep the original text and just flag it
                const revokedKey = extractRevoke(msg.message);
                if (revokedKey) {
                    markMessageDeleted(revokedKey.remoteJid || remoteJid, revokedKey.id);
                    continue;
                }

                // Encrypted edits (secretEncryptedMessage): decrypt with the original's messageSecret
                const secretEdit = unwrap(msg.message).content.secretEncryptedMessage;
                if (isMessageEdit(secretEdit)) {
                    await handleSecretEdit(msg, secretEdit, remoteJid);
                    continue;
                }

                // Edits: keep the original AND every edited version
                const edit = extractEdit(msg.message);
                if (edit) {
                    editMessage(edit.key.remoteJid || remoteJid, edit.key.id, edit.text, edit.ts);
                    continue;
                }

                // Text, plus a readable placeholder for images, voice notes, stickers, etc.
                const described = describeMessage(msg.message);
                if (!described || !described.text) continue;

                const rawTs = msg.messageTimestamp;
                let timestamp = rawTs ? Number(typeof rawTs === 'object' ? (rawTs.low ?? rawTs) : rawTs) : 0;
                if (!timestamp || !isFinite(timestamp)) {
                    timestamp = Math.floor(Date.now() / 1000);
                    console.log(`System: Message ${msg.key.id} had no timestamp (type=${type}, fromMe=${!!msg.key.fromMe}); used server time.`);
                } else if (timestamp > 1e11) {
                    timestamp = Math.floor(timestamp / 1000);   // milliseconds -> seconds
                }

                const isFromMe = msg.key.fromMe || false;
                // In groups the pushName is the sender; fromMe is always "Me"
                const senderName = isFromMe ? "Me" : (msg.pushName || "Unknown");

                // Memory first, Firestore second (queued and retried if it is down)
                upsertChat(remoteJid, {
                    lastActive: timestamp,
                    id: remoteJid,
                    preview: described.text
                });

                // Keep the message secret: it is the only way to decrypt a later edit of this message
                const ctx = unwrap(msg.message).content.messageContextInfo || msg.message.messageContextInfo;
                const secretB64 = ctx?.messageSecret ? Buffer.from(ctx.messageSecret).toString('base64') : null;

                const data = {
                    text: described.text,
                    type: described.type,
                    senderId: remoteJid,
                    senderName: senderName,
                    timestamp: timestamp,
                    fromMe: isFromMe,
                    id: msg.key.id
                };
                if (described.viewOnce) data.viewOnce = true;
                if (secretB64) data.ms = secretB64;
                data.sa = authorIds(msg.key);
                upsertMessage(remoteJid, msg.key.id, data);

                ensureGroupName(remoteJid);
            } catch (err) {}
        }
    });
}

// Relog: unlink, wipe the stored session, and start over so a fresh QR is shown.
async function relog() {
    console.log("System: Relog requested. Wiping session and restarting.");
    generation++;                         // silence the old socket
    if (startTimer) { clearTimeout(startTimer); startTimer = null; }
    const oldSock = sock;
    const oldAuth = currentAuth;
    sock = null;
    currentAuth = null;
    isConnected = false;
    qrCodeData = null;
    consecutiveConnectFailures = 0;
    consecutiveAuthFailures = 0;

    if (oldSock) {
        try { await Promise.race([oldSock.logout(), new Promise(r => setTimeout(r, 5000))]); } catch (e) {}
        try { oldSock.end(undefined); } catch (e) {}
    }
    // Wipe through the live adapter if there is one, otherwise straight from Firestore
    try {
        if (oldAuth) {
            await oldAuth.clearAll();
        } else {
            const col = db.collection('auth_store');
            const batch = db.batch();
            const snap = await col.get();
            snap.forEach(d => batch.delete(d.ref));
            await batch.commit();
        }
    } catch (e) {
        scheduleStart(30 * 1000);   // never leave the server without a socket
        throw e;
    }
    scheduleStart(500);
}

// Link with a phone number instead of a QR code (WhatsApp -> Linked devices -> Link with phone number)
async function requestPairing(phone) {
    const digits = String(phone || '').replace(/\D/g, '');
    if (digits.length < 8 || digits.length > 15) throw new Error('Enter the number with country code, digits only.');
    if (isConnected) throw new Error('Already linked.');
    if (!sock || !qrCodeData) throw new Error('Not ready yet. Wait a few seconds and try again.');
    if (sock.authState.creds.registered) throw new Error('Session already registered.');
    return sock.requestPairingCode(digits);
}

module.exports = {
    startWhatsApp,
    requestPairing,
    relog,
    flushAuth: () => (currentAuth ? currentAuth.flushNow() : Promise.resolve()),
    getQrCodeData: () => qrCodeData,
    getIsConnected: () => isConnected
};
