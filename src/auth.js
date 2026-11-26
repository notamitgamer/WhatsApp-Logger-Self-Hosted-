const crypto = require('crypto');
const { AUTH_PASS, AUTH_USER, TOKEN_SECRET } = require('./config');

// --- AUTH UTILS ---
const AUTH_CONFIGURED = !!(AUTH_USER && AUTH_PASS);
if (!AUTH_CONFIGURED) {
    console.error("System: AUTH_USER / AUTH_PASS are not set. Login and the API are disabled until you set them.");
}

// Token = HMAC of a fixed label, keyed by TOKEN_SECRET (or AUTH_PASS). Not a bare hash of the
// password, so it can't be computed or brute-forced offline from a guess of the password.
// With no credentials configured a random key is used, so the token can't be guessed.
const SESSION_SECRET = crypto
    .createHmac('sha256', TOKEN_SECRET || AUTH_PASS || crypto.randomBytes(32).toString('hex'))
    .update('whatsapp-logger-session-v2')
    .digest('hex');

// Constant-time string compare
function safeEqual(a, b) {
    const ba = Buffer.from(String(a ?? ''));
    const bb = Buffer.from(String(b ?? ''));
    if (ba.length !== bb.length) {
        crypto.timingSafeEqual(ba, ba);   // keep timing flat
        return false;
    }
    return crypto.timingSafeEqual(ba, bb);
}

// Simple in-memory login rate limit: 10 failures per 15 minutes per IP
const attempts = new Map();
const WINDOW_MS = 15 * 60 * 1000;
const MAX_FAILURES = 10;
function loginLimited(ip) {
    const e = attempts.get(ip);
    if (!e) return false;
    if (Date.now() - e.first > WINDOW_MS) { attempts.delete(ip); return false; }
    return e.count >= MAX_FAILURES;
}
function recordLoginFailure(ip) {
    const e = attempts.get(ip);
    if (!e || Date.now() - e.first > WINDOW_MS) attempts.set(ip, { first: Date.now(), count: 1 });
    else e.count++;
}
function clearLoginFailures(ip) { attempts.delete(ip); }
setInterval(() => {
    const now = Date.now();
    attempts.forEach((e, ip) => { if (now - e.first > WINDOW_MS) attempts.delete(ip); });
}, WINDOW_MS).unref();

function credentialsMatch(username, password) {
    // evaluate both so timing doesn't reveal which one was wrong
    const u = safeEqual(username, AUTH_USER);
    const p = safeEqual(password, AUTH_PASS);
    return AUTH_CONFIGURED && u && p;
}

function parseCookies(request) {
    const list = {};
    const rc = request.headers.cookie;
    if (rc) {
        rc.split(';').forEach((cookie) => {
            const parts = cookie.split('=');
            list[parts.shift().trim()] = decodeURI(parts.join('='));
        });
    }
    return list;
}

// Auth Middleware for APIs
const verifyApiToken = (req, res, next) => {
    const authHeader = req.headers.authorization;
    let token = req.query.token;

    if (authHeader && authHeader.startsWith('Bearer ')) {
        token = authHeader.split(' ')[1];
    }

    if (AUTH_CONFIGURED && safeEqual(token, SESSION_SECRET)) return next();
    res.status(401).json({ error: 'Unauthorized' });
};

const verifyLogsAccess = (req, res, next) => {
    let token = req.query.token;
    if (req.headers.authorization && req.headers.authorization.startsWith('Bearer ')) {
        token = req.headers.authorization.split(' ')[1];
    }
    const cookies = parseCookies(req);

    if (AUTH_CONFIGURED && (safeEqual(token, SESSION_SECRET) || safeEqual(cookies.auth_session, SESSION_SECRET))) {
        return next();
    }
    res.status(401).send('Unauthorized');
};

const checkAuth = (req, res, next) => {
    const cookies = parseCookies(req);
    if (AUTH_CONFIGURED && safeEqual(cookies.auth_session, SESSION_SECRET)) return next();

    if (req.path.startsWith('/api')) res.status(401).send('Unauthorized');
    else res.redirect('/login');
};

module.exports = {
    SESSION_SECRET,
    AUTH_CONFIGURED,
    safeEqual,
    credentialsMatch,
    loginLimited,
    recordLoginFailure,
    clearLoginFailures,
    parseCookies,
    verifyApiToken,
    verifyLogsAccess,
    checkAuth
};
