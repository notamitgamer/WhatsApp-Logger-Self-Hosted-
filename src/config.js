// --- CONFIGURATION ---
const PORT = process.env.PORT || 3000;
const AUTH_USER = process.env.AUTH_USER;
const AUTH_PASS = process.env.AUTH_PASS;
const MAX_LOGS = 500;
const MAX_CONNECTIONS_PER_TOKEN = 15;
const VERSION = '4.5.6';
// Optional: comma-separated origins allowed to call the API from a browser (default: any)
const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || '').split(',').map(o => o.trim()).filter(Boolean);
// Optional: dedicated secret for signing login tokens (falls back to AUTH_PASS)
const TOKEN_SECRET = process.env.TOKEN_SECRET || '';

// Add the jids here to exclude them, this is a array remember that
const EXCLUDED_JIDS = new Set(['']); 

module.exports = {
    PORT,
    AUTH_USER,
    AUTH_PASS,
    MAX_LOGS,
    MAX_CONNECTIONS_PER_TOKEN,
    VERSION,
    ALLOWED_ORIGINS,
    TOKEN_SECRET,
    EXCLUDED_JIDS
};
