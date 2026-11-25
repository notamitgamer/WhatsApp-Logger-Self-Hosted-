const express = require('express');
const { SESSION_SECRET, AUTH_CONFIGURED, credentialsMatch, loginLimited, recordLoginFailure, clearLoginFailures } = require('../auth');
const { loginPage, loginFailedPage } = require('../views/pages');

const router = express.Router();

router.get('/login', (req, res) => {
    res.send(loginPage());
});

router.post('/login', (req, res) => {
    const { username, password, remember } = req.body;
    if (!AUTH_CONFIGURED) return res.status(503).send('Server login is not configured');
    if (loginLimited(req.ip)) return res.status(429).send('Too many attempts. Try again in 15 minutes.');

    if (credentialsMatch(username, password)) {
        clearLoginFailures(req.ip);
        let cookieSettings = 'HttpOnly; SameSite=Lax; Path=/;';
        if (req.secure) cookieSettings += ' Secure;';
        if (remember === 'yes') cookieSettings += ' Max-Age=300;';

        res.setHeader('Set-Cookie', `auth_session=${SESSION_SECRET}; ${cookieSettings}`);
        return res.redirect('/');
    }
    recordLoginFailure(req.ip);
    res.status(401).send(loginFailedPage());
});

router.get('/logout', (req, res) => {
    res.setHeader('Set-Cookie', 'auth_session=; Max-Age=0; Path=/;');
    res.redirect('/login');
});

router.post('/api/verify', (req, res) => {
    const { username, password } = req.body;
    if (!AUTH_CONFIGURED) return res.status(503).json({ success: false, error: 'Server login is not configured' });
    if (loginLimited(req.ip)) return res.status(429).json({ success: false, error: 'Too many attempts. Try again later.' });

    if (credentialsMatch(username, password)) {
        clearLoginFailures(req.ip);
        return res.json({ success: true, token: SESSION_SECRET });
    }
    recordLoginFailure(req.ip);
    return res.status(401).json({ success: false });
});

module.exports = router;
