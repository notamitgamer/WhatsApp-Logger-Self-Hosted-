const express = require('express');
const { AUTH_USER, AUTH_PASS } = require('../config');
const { SESSION_SECRET } = require('../auth');
const { loginPage, loginFailedPage } = require('../views/pages');

const router = express.Router();

router.get('/login', (req, res) => {
    res.send(loginPage());
});

router.post('/login', (req, res) => {
    const { username, password, remember } = req.body;

    if (username === AUTH_USER && password === AUTH_PASS) {
        let cookieSettings = 'HttpOnly; Path=/;';
        if (remember === 'yes') cookieSettings += ' Max-Age=300;';

        res.setHeader('Set-Cookie', `auth_session=${SESSION_SECRET}; ${cookieSettings}`);
        return res.redirect('/');
    }
    res.status(401).send(loginFailedPage());
});

router.get('/logout', (req, res) => {
    res.setHeader('Set-Cookie', 'auth_session=; Max-Age=0; Path=/;');
    res.redirect('/login');
});

router.post('/api/verify', (req, res) => {
    const { username, password } = req.body;

    if (username === AUTH_USER && password === AUTH_PASS) {
        return res.json({ success: true, token: SESSION_SECRET });
    } else {
        return res.status(401).json({ success: false });
    }
});

module.exports = router;
