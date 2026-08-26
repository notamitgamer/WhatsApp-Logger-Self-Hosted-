const express = require('express');
const { db } = require('../firebase');
const { chatsCache, messagesCache, isCacheReady } = require('../cache');
const { clients, enforceConnectionCeiling } = require('../sseManager');
const { verifyApiToken } = require('../auth');

const router = express.Router();

router.get('/ping', (req, res) => {
    res.status(200).send('Pong');
});

// --- API: Server Sent Events ---
router.get('/api/chats/stream', verifyApiToken, async (req, res) => {
    if (!isCacheReady()) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'X-Accel-Buffering': 'no'
    });
    if (res.flushHeaders) res.flushHeaders();
    if (res.socket) res.socket.setNoDelay(true);
    res.write('\n');

    const cleanup = () => {
        clients.chats.delete(res);
    };

    enforceConnectionCeiling(req, res, cleanup);
    clients.chats.add(res);

    try {
        const grouped = {};

        chatsCache.forEach((data, docId) => {
            const phone = data.phoneNumber || data.id.split('@')[0];

            if (!grouped[phone]) {
                grouped[phone] = { ...data, ids: [data.id] };
            } else {
                grouped[phone].ids.push(data.id);
                if ((data.lastActive || 0) > (grouped[phone].lastActive || 0)) {
                    grouped[phone].lastActive = data.lastActive;
                    grouped[phone].preview = data.preview || grouped[phone].preview;
                }
                if (data.customName) grouped[phone].customName = data.customName;
            }
        });

        res.write(`event: initial\ndata: ${JSON.stringify(Object.values(grouped))}\n\n`);
    } catch (e) {
        console.error("Error sending initial chats:", e);
    }
});

router.get('/api/messages/stream', verifyApiToken, async (req, res) => {
    if (!isCacheReady()) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    const { chatId, since } = req.query;
    if (!chatId) return res.status(400).send('Missing chatId');

    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'X-Accel-Buffering': 'no'
    });
    if (res.flushHeaders) res.flushHeaders();
    if (res.socket) res.socket.setNoDelay(true);
    res.write('\n');

    const cleanup = () => {
        const chatClients = clients.messages.get(chatId);
        if (chatClients) {
            chatClients.delete(res);
            if (chatClients.size === 0) clients.messages.delete(chatId);
        }
    };

    enforceConnectionCeiling(req, res, cleanup);

    if (!clients.messages.has(chatId)) {
        clients.messages.set(chatId, new Set());
    }
    clients.messages.get(chatId).add(res);

    try {
        let initialMessages = [];
        const chatMsgs = messagesCache.get(chatId);

        if (chatMsgs) {
            const sinceTs = since ? parseInt(since, 10) : 0;
            for (const msg of chatMsgs.values()) {
                if (msg.timestamp > sinceTs) {
                    initialMessages.push(msg);
                }
            }
            initialMessages.sort((a, b) => a.timestamp - b.timestamp);
        }

        res.write(`event: initial\ndata: ${JSON.stringify(initialMessages)}\n\n`);
    } catch (e) {
        console.error("Error sending initial messages:", e);
    }
});

// --- API: Standard Actions ---
router.post('/api/rename', verifyApiToken, async (req, res) => {
    if (!isCacheReady()) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    const { id, customName } = req.body;
    if (!id || !customName) return res.status(400).json({ error: 'Missing parameters' });

    try {
        // Fire & Forget to DB
        await db.collection('Chats').doc(id).set({ customName }, { merge: true });

        // Instant RAM update
        if (chatsCache.has(id)) {
            chatsCache.get(id).customName = customName;
        }

        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

router.get('/api/export', verifyApiToken, async (req, res) => {
    if (!isCacheReady()) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    try {
        const exportData = { chats: {}, messages: {} };

        chatsCache.forEach((data, id) => exportData.chats[id] = data);
        messagesCache.forEach((msgMap, chatId) => {
            exportData.messages[chatId] = Array.from(msgMap.values());
        });

        res.json(exportData);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
