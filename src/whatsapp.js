const {
    default: makeWASocket,
    DisconnectReason,
    fetchLatestBaileysVersion
} = require('@whiskeysockets/baileys');
const pino = require('pino');
const { db } = require('./firebase');
const { useFirestoreAuthState } = require('./authState');

// --- BAILEYS SETUP ---
let qrCodeData = null;
let sock = null;
let isConnected = false;

let consecutiveAuthFailures = 0;
let consecutiveConnectFailures = 0;

async function startWhatsApp() {
    const logger = pino({ level: 'silent' });

    let authResult;
    try {
        authResult = await useFirestoreAuthState(db, 'whatsapp_auth');
    } catch (err) {
        consecutiveAuthFailures++;
        const backoff = Math.min(5000 * Math.pow(2, consecutiveAuthFailures), 300000); // cap 5 min
        console.error(`System: Auth state read failed (attempt ${consecutiveAuthFailures}). Retrying in ${backoff / 1000}s.`);
        setTimeout(startWhatsApp, backoff);
        return;
    }
    consecutiveAuthFailures = 0; // Reset on success

    const { state, saveCreds, clearState } = authResult;
    const { version } = await fetchLatestBaileysVersion();

    console.log("System: Connecting to WhatsApp servers...");

    sock = makeWASocket({
        version,
        logger,
        auth: state,
        browser: ["WhatsApp Logger v4.2.1", "Chrome", "4.2.1"],
        syncFullHistory: true
    });

    sock.ev.on('connection.update', async (update) => {
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

            if (shouldReconnect) {
                consecutiveConnectFailures++;
                const backoff = Math.min(5000 * Math.pow(2, consecutiveConnectFailures), 300000); // cap 5 min
                console.log(`System: Connection closed (Status: ${statusCode}). Reconnecting in ${backoff / 1000}s...`);
                setTimeout(startWhatsApp, backoff);
            } else {
                console.log("System: Device Logged Out. Wiping session from Firestore.");
                await clearState();
                qrCodeData = null;
                consecutiveConnectFailures = 0;
                startWhatsApp();
            }
        } else if (connection === 'open') {
            console.log("System: Connection Open and Authenticated. Firebase Auth Sync Active.");
            qrCodeData = null;
            isConnected = true;
            consecutiveConnectFailures = 0; // Reset on success
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
                    await db.collection('Chats').doc(primaryId).set(updateData, { merge: true });

                    if (contact.lid && contact.id !== contact.lid) {
                        await db.collection('Chats').doc(contact.id).set(updateData, { merge: true });
                    }
                } catch (err) {}
            }
        }
    });

    sock.ev.on('messages.upsert', async ({ messages, type }) => {
        if (type !== 'notify' && type !== 'append') return;

        for (const msg of messages) {
            try {
                if (!msg.message) continue;

                const remoteJid = msg.key.remoteJid;
                if (remoteJid === 'status@broadcast') continue;

                const textContent =
                    msg.message.conversation ||
                    msg.message.extendedTextMessage?.text ||
                    msg.message.imageMessage?.caption ||
                    msg.message.videoMessage?.caption ||
                    "";

                if (!textContent) continue;

                const timestamp = msg.messageTimestamp
                    ? (typeof msg.messageTimestamp === 'number' ? msg.messageTimestamp : msg.messageTimestamp.low)
                    : Math.floor(Date.now() / 1000);

                const isFromMe = msg.key.fromMe || false;
                const senderName = isFromMe ? "Me" : (msg.pushName || "Unknown");

                // 1. Ensure Chat Document Exists
                await db.collection('Chats').doc(remoteJid).set({
                    lastActive: timestamp,
                    id: remoteJid,
                    preview: textContent
                }, { merge: true });

                // 2. Save Message
                await db.collection('Chats')
                    .doc(remoteJid)
                    .collection('Messages')
                    .doc(msg.key.id)
                    .set({
                        text: textContent,
                        senderId: remoteJid,
                        senderName: senderName,
                        timestamp: timestamp,
                        fromMe: isFromMe,
                        id: msg.key.id
                    }, { merge: true });

            } catch (err) {}
        }
    });
}

module.exports = {
    startWhatsApp,
    getQrCodeData: () => qrCodeData,
    getIsConnected: () => isConnected
};
