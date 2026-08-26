const crypto = require('crypto');
const { AUTH_PASS, AUTH_USER } = require('./config');

// --- AUTH UTILS ---
const SESSION_SECRET = crypto.createHash('sha256').update(AUTH_PASS || 'default').digest('hex');

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

    if (token === SESSION_SECRET) return next();
    res.status(401).json({ error: 'Unauthorized' });
};

const verifyLogsAccess = (req, res, next) => {
    let token = req.query.token;
    if (req.headers.authorization && req.headers.authorization.startsWith('Bearer ')) {
        token = req.headers.authorization.split(' ')[1];
    }
    const cookies = parseCookies(req);

    if (token === SESSION_SECRET || cookies.auth_session === SESSION_SECRET) {
        return next();
    }
    res.status(401).send('Unauthorized');
};

const checkAuth = (req, res, next) => {
    if (!AUTH_USER || !AUTH_PASS) return next();
    const cookies = parseCookies(req);
    if (cookies.auth_session === SESSION_SECRET) return next();

    if (req.path.startsWith('/api')) res.status(401).send('Unauthorized');
    else res.redirect('/login');
};

module.exports = {
    SESSION_SECRET,
    parseCookies,
    verifyApiToken,
    verifyLogsAccess,
    checkAuth
};
