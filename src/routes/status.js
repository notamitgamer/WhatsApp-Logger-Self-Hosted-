const express = require('express');
const QRCode = require('qrcode');
const { checkAuth } = require('../auth');
const { getIsConnected, getQrCodeData } = require('../whatsapp');
const { connectedPage, qrPage, qrErrorPage, initializingPage } = require('../views/pages');

const router = express.Router();

router.use(checkAuth);

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
