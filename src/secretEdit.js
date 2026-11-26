const crypto = require('crypto');
const { proto, jidNormalizedUser } = require('@whiskeysockets/baileys');

// WhatsApp now sends many edits as a `secretEncryptedMessage` (type MESSAGE_EDIT): the new
// content is AES-GCM encrypted with a key derived from the ORIGINAL message's messageSecret.
// Baileys 6.x does not decrypt these, so we do it here.
// key = HKDF-SHA256(secret, info = origId + origSender + editSender + useCase), aad = origId \0 editSender

const USE_CASES = ['Message Edit', 'Edit', 'Edit Message'];

function tryDecrypt({ encPayload, encIv, secret, origId, origSender, modSender }) {
    for (const useCase of USE_CASES) {
        try {
            const info = Buffer.from(origId + origSender + modSender + useCase);
            const key = Buffer.from(crypto.hkdfSync('sha256', secret, Buffer.alloc(0), info, 32));
            const payload = Buffer.from(encPayload);
            // Associated data: the edit's author is the documented form; others are cheap fallbacks
            const aads = [`${origId}\u0000${modSender}`, `${origId}\u0000${origSender}`, null];
            for (const a of aads) {
                try {
                    const d = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(encIv));
                    if (a !== null) d.setAAD(Buffer.from(a));
                    d.setAuthTag(payload.subarray(payload.length - 16));
                    return Buffer.concat([d.update(payload.subarray(0, payload.length - 16)), d.final()]);
                } catch (e) { /* tag check failed: try the next */ }
            }
        } catch (e) { /* bad key derivation inputs */ }
    }
    return null;
}

// Every spelling of a JID the sender could have been addressed by (phone, LID, normalized)
function jidVariants(list) {
    const out = new Set();
    for (const j of list) {
        if (!j || typeof j !== 'string') continue;
        out.add(j);
        try { out.add(jidNormalizedUser(j)); } catch (e) {}
    }
    return [...out];
}

/**
 * @param se   the secretEncryptedMessage
 * @param ctx  { secret: Buffer, senders: string[] }  secret of the original message, possible sender JIDs
 * @returns a decoded proto.Message, or null
 */
function decryptSecretEdit(se, { secret, senders }) {
    const origId = se.targetMessageKey?.id;
    if (!origId || !se.encPayload || !se.encIv || !secret) return null;
    const list = jidVariants(senders);
    for (const origSender of list) {
        for (const modSender of list) {
            const plain = tryDecrypt({ encPayload: se.encPayload, encIv: se.encIv, secret, origId, origSender, modSender });
            if (plain) {
                try { return proto.Message.decode(plain); } catch (e) { return null; }
            }
        }
    }
    return null;
}

const isMessageEdit = (se) => se && (se.secretEncType === 2 || se.secretEncType === 'MESSAGE_EDIT');

module.exports = { decryptSecretEdit, isMessageEdit, tryDecrypt };
