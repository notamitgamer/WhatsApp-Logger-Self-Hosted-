const { MAX_CONNECTIONS_PER_TOKEN } = require('./config');

// --- SSE CONNECTION MANAGER ---
const activeConnections = [];
const clients = {
    chats: new Set(),
    messages: new Map(),
    sync: new Set() // Global message-sync clients (one connection covers all chats)
};

function enforceConnectionCeiling(req, res, cleanupFunction) {
    const token = req.query.token || req.headers.authorization?.split(' ')[1];
    activeConnections.push({ res, token, cleanup: cleanupFunction });

    const userConns = activeConnections.filter(c => c.token === token);
    if (userConns.length > MAX_CONNECTIONS_PER_TOKEN) {
        const oldestIdx = activeConnections.findIndex(c => c.token === token);
        if (oldestIdx > -1) {
            const oldest = activeConnections[oldestIdx];
            oldest.cleanup();
            oldest.res.end();
            activeConnections.splice(oldestIdx, 1);
        }
    }

    res.on('close', () => {
        const idx = activeConnections.findIndex(c => c.res === res);
        if (idx > -1) activeConnections.splice(idx, 1);
        cleanupFunction();
    });
}

function startHeartbeat() {
    // Global heartbeat to keep Render connections alive
    setInterval(() => {
        activeConnections.forEach(({ res }) => {
            try { res.write(': ping\n\n'); } catch (e) {}
        });
    }, 25000);
}

module.exports = { clients, enforceConnectionCeiling, startHeartbeat };
