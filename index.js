require('./src/logger').install();

const express = require('express');
const { PORT } = require('./src/config');
const { warmCache } = require('./src/cache');
const { startWhatsApp } = require('./src/whatsapp');
const { startHeartbeat } = require('./src/sseManager');

const authRoutes = require('./src/routes/auth');
const apiRoutes = require('./src/routes/api');
const logsRoutes = require('./src/routes/logs');
const statusRoutes = require('./src/routes/status');

// Initialize Express
const app = express();
app.use(express.urlencoded({ extended: true }));
app.use(express.json());
// Static assets (favicon/logo) — served unauthenticated, before any login check
app.use(express.static('public'));

app.use((req, res, next) => {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");
    next();
});

// Answer every OPTIONS preflight immediately — before ANY auth middleware sees it
app.options('*', (req, res) => {
    res.sendStatus(200);
});

startHeartbeat();

// --- ROUTES ---
// Unauthenticated: /ping, SSE + action endpoints (own token-based auth), and system logs
app.use(apiRoutes);
app.use(logsRoutes);
// Login/logout/verify (unauthenticated by nature)
app.use(authRoutes);
// Cookie-authenticated web UI (home/status/QR page) — must be last, applies checkAuth
app.use(statusRoutes);

// --- START SERVER ---
app.listen(PORT, () => {
    warmCache();
    startWhatsApp();
    console.log(`Server running on port ${PORT}`);
});
