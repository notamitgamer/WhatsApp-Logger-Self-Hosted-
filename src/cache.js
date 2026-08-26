const { db } = require('./firebase');
const { EXCLUDED_JIDS } = require('./config');
const { clients } = require('./sseManager');

// --- IN-MEMORY CACHE ---
let chatsCache = new Map();      // chatId -> chat data
let messagesCache = new Map();   // chatId -> Map(msgId -> message data)
let cacheReady = false;
let consecutiveCacheFailures = 0;

async function warmCache() {
    try {
        console.log("System: Warming cache from Firestore...");

        // 1. Fetch Chats
        const chatsSnap = await db.collection('Chats').get();
        chatsSnap.forEach(doc => {
            if (!EXCLUDED_JIDS.has(doc.id)) {
                chatsCache.set(doc.id, { id: doc.id, ...doc.data() });
            }
        });

        // 2. Fetch all Messages via CollectionGroup
        const msgsSnap = await db.collectionGroup('Messages').get();
        msgsSnap.forEach(doc => {
            const chatId = doc.ref.parent.parent.id;
            if (!messagesCache.has(chatId)) messagesCache.set(chatId, new Map());
            messagesCache.get(chatId).set(doc.id, doc.data());
        });

        cacheReady = true;
        consecutiveCacheFailures = 0;
        console.log(`System: Cache warm. ${chatsCache.size} chats, ${msgsSnap.size} messages.`);

        // 3. Start Permanent Listeners
        startPermanentListeners();

    } catch (err) {
        consecutiveCacheFailures++;
        const backoff = Math.min(5000 * Math.pow(2, consecutiveCacheFailures), 300000); // Max 5 min
        console.error(`System: Cache warm failed (attempt ${consecutiveCacheFailures}). Retrying in ${backoff / 1000}s.`, err.message);
        setTimeout(warmCache, backoff);
    }
}

function startPermanentListeners() {
    // Shared Permanent Listener for Chats
    db.collection('Chats').onSnapshot(snapshot => {
        const changes = [];
        snapshot.docChanges().forEach(change => {
            if (EXCLUDED_JIDS.has(change.doc.id)) return;
            const data = { id: change.doc.id, ...change.doc.data() };
            chatsCache.set(change.doc.id, data);
            changes.push({ type: change.type, doc: data });
        });
        if (changes.length > 0) {
            const payload = `event: update\ndata: ${JSON.stringify(changes)}\n\n`;
            clients.chats.forEach(res => { try { res.write(payload); } catch (e) {} });
        }
    });

    // Shared Permanent Listener for Messages
    db.collectionGroup('Messages').onSnapshot(snapshot => {
        snapshot.docChanges().forEach(change => {
            const chatId = change.doc.ref.parent.parent.id;
            if (!messagesCache.has(chatId)) messagesCache.set(chatId, new Map());

            const data = change.doc.data();
            messagesCache.get(chatId).set(change.doc.id, data);

            const chatClients = clients.messages.get(chatId);
            if (chatClients) {
                const payload = `event: update\ndata: ${JSON.stringify([{ type: change.type, doc: data }])}\n\n`;
                chatClients.forEach(res => { try { res.write(payload); } catch (e) {} });
            }
        });
    });
}

module.exports = {
    chatsCache,
    messagesCache,
    warmCache,
    isCacheReady: () => cacheReady
};
