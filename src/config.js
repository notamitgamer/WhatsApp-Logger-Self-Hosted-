// --- CONFIGURATION ---
const PORT = process.env.PORT || 3000;
const AUTH_USER = process.env.AUTH_USER;
const AUTH_PASS = process.env.AUTH_PASS;
const MAX_LOGS = 500;
const MAX_CONNECTIONS_PER_TOKEN = 15;
const VERSION = '4.2.1';
const EXCLUDED_JIDS = new Set(['917278779512@s.whatsapp.net', '201554426618024@lid']);

module.exports = {
    PORT,
    AUTH_USER,
    AUTH_PASS,
    MAX_LOGS,
    MAX_CONNECTIONS_PER_TOKEN,
    VERSION,
    EXCLUDED_JIDS
};
