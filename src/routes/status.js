const express = require('express');
const QRCode = require('qrcode');
const { checkAuth } = require('../auth');
const { getIsConnected, getQrCodeData, relog, requestPairing } = require('../whatsapp');
const { db } = require('./../firebase');
const { getStats } = require('../cache');
const { connectedPage, legacyDonePage, qrPage, qrErrorPage, initializingPage, healthPage } = require('../views/pages');

const router = express.Router();

router.use(checkAuth);

router.get('/health', (req, res) => {
    res.send(healthPage({ stats: getStats(), connected: getIsConnected() }));
});

// Fresh QR as JSON for the "Refresh QR" button (no page reload)
router.get('/qr.json', async (req, res) => {
    res.set('Cache-Control', 'no-store');
    if (getIsConnected()) return res.json({ connected: true });
    const qr = getQrCodeData();
    if (!qr) return res.json({ waiting: true });
    try {
        res.json({ image: await QRCode.toDataURL(qr) });
    } catch (e) {
        res.status(500).json({ error: 'qr_failed' });
    }
});

// Wipe the saved session and start over with a new QR. POST only; cookie is SameSite=Lax.
router.post('/relog', async (req, res) => {
    try {
        await relog();
        res.redirect('/');
    } catch (e) {
        console.error('System: Relog failed:', e.message);
        res.status(500).send('Relog failed: ' + e.message);
    }
});

// Pairing code (phone number login). Body: phone=91XXXXXXXXXX
router.post('/pair', express.urlencoded({ extended: false }), express.json(), async (req, res) => {
    res.set('Cache-Control', 'no-store');
    try {
        const code = await requestPairing(req.body && req.body.phone);
        res.json({ code: code.length === 8 ? code.slice(0, 4) + '-' + code.slice(4) : code });
    } catch (e) {
        res.status(400).json({ error: e.message });
    }
});

// One-click removal of the pre-4.4.5 per-key auth collection. POST only; touches nothing else.
router.post('/cleanup-legacy', async (req, res) => {
    let n = 0;
    try {
        const col = db.collection('whatsapp_auth');
        for (;;) {
            const refs = await col.listDocuments();
            if (!refs.length) break;
            for (let i = 0; i < refs.length; i += 400) {
                const batch = db.batch();
                refs.slice(i, i + 400).forEach(r => batch.delete(r));
                await batch.commit();
                n += Math.min(400, refs.length - i);
            }
        }
        console.log(`System: Deleted legacy whatsapp_auth collection (${n} docs).`);
        res.send(legacyDonePage(n));
    } catch (e) {
        console.error('System: Legacy cleanup failed:', e.message);
        res.status(500).send(legacyDonePage(n, e.message));
    }
});

router.get('/', async (req, res) => {
    if (getIsConnected()) {
        return res.send(connectedPage());
    }

    const qrCodeData = getQrCodeData();
    if (qrCodeData) {
        try {
            const qrImage = await QRCode.toDataURL(qrCodeData);
            return res.send(qrPage(qrImage));
        } catch (e) {
            return res.send(qrErrorPage());
        }
    }

    return res.send(initializingPage());
});

module.exports = router;
