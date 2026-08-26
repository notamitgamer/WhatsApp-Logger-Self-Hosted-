const express = require('express');
const { verifyLogsAccess } = require('../auth');
const { logBuffer } = require('../logger');
const { logsPage } = require('../views/pages');

const router = express.Router();

// --- SYSTEM LOGS ROUTE ---
router.get('/logs', verifyLogsAccess, (req, res) => {
    const wantsJson = req.query.format === 'json' || (req.headers.accept && req.headers.accept.includes('application/json'));

    if (wantsJson) {
        return res.json(logBuffer);
    }

    res.send(logsPage(logBuffer));
});

module.exports = router;
