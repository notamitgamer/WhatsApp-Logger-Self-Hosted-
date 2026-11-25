const express = require('express');
const { db } = require('../firebase');
const { chatsCache, messagesCache, isCacheReady, upsertChat, getStats, pub } = require('../cache');
const { VERSION } = require('../config');
const { getIsConnected } = require('../whatsapp');
const { clients, enforceConnectionCeiling } = require('../sseManager');
const { verifyApiToken } = require('../auth');

const router = express.Router();

router.get('/ping', (req, res) => {
    res.status(200).send('Pong');
});

// Private health report: cache size, queued writes, snapshot and quota state
router.get('/api/health', verifyApiToken, (req, res) => {
    res.json({
        version: VERSION,
        uptimeSeconds: Math.floor(process.uptime()),
        whatsappConnected: getIsConnected(),
        ...getStats()
    });
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
                if (msg.timestamp > sinceTs || (msg.deletedAt && msg.deletedAt > sinceTs) || (msg.editedAt && msg.editedAt > sinceTs)) {
                    initialMessages.push(pub(msg));
                }
            }
            initialMessages.sort((a, b) => a.timestamp - b.timestamp);
        }

        res.write(`event: initial\ndata: ${JSON.stringify(initialMessages)}\n\n`);
    } catch (e) {
        console.error("Error sending initial messages:", e);
    }
});

// Global sync stream: one connection covers messages for ALL chats, so the
// client doesn't need to open a new SSE connection per chat it opens.
router.get('/api/sync/stream', verifyApiToken, async (req, res) => {
    if (!isCacheReady()) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    const { since } = req.query;

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
        clients.sync.delete(res);
    };

    enforceConnectionCeiling(req, res, cleanup);
    clients.sync.add(res);

    try {
        const sinceTs = since ? parseInt(since, 10) : 0;
        const initialMessages = [];

        messagesCache.forEach((msgMap, chatId) => {
            for (const msg of msgMap.values()) {
                if (msg.timestamp > sinceTs || (msg.deletedAt && msg.deletedAt > sinceTs) || (msg.editedAt && msg.editedAt > sinceTs)) {
                    initialMessages.push({ ...pub(msg), chatId });
                }
            }
        });

        initialMessages.sort((a, b) => a.timestamp - b.timestamp);

        res.write(`event: initial\ndata: ${JSON.stringify(initialMessages)}\n\n`);
    } catch (e) {
        console.error("Error sending initial sync payload:", e);
    }
});

// --- API: Full Resync (used by the client's "Reset" flow) ---
// Lightweight manifest: chat metadata + per-chat message counts, no message
// bodies. Lets the client know the true total up front so it can show real
// download progress instead of a fake/local-only percentage.
router.get('/api/export/chats', verifyApiToken, async (req, res) => {
    if (!isCacheReady()) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    try {
        const chats = [];
        let totalMessages = 0;

        chatsCache.forEach((data, id) => {
            const count = messagesCache.has(id) ? messagesCache.get(id).size : 0;
            totalMessages += count;
            chats.push({ ...data, messageCount: count });
        });

        res.json({ chats, totalMessages });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Plain JSON (not SSE) — all messages for a single chat. Called once per
// chat by the client's resync loop so progress can be updated after each
// chat finishes, instead of waiting on one all-chats blob.
router.get('/api/export/messages', verifyApiToken, async (req, res) => {
    if (!isCacheReady()) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    const { chatId } = req.query;
    if (!chatId) return res.status(400).json({ error: 'Missing chatId' });

    try {
        const msgMap = messagesCache.get(chatId);
        const messages = msgMap ? Array.from(msgMap.values()).map(pub) : [];
        res.json({ chatId, messages });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// --- API: Standard Actions ---
router.post('/api/rename', verifyApiToken, async (req, res) => {
    if (!isCacheReady()) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    const { id, customName } = req.body;
    if (!id || !customName) return res.status(400).json({ error: 'Missing parameters' });

    try {
        // RAM now, Firestore queued (retried if it is down)
        upsertChat(id, { customName });

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
            exportData.messages[chatId] = Array.from(msgMap.values()).map(pub);
        });

        res.json(exportData);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
