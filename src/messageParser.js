// Turns a raw Baileys message into something we can store.
// Handles the wrappers WhatsApp puts around messages (disappearing chats, view once,
// document-with-caption, edits) and gives every non-text message a readable placeholder.

const WRAPPERS = [
    'ephemeralMessage',
    'viewOnceMessage',
    'viewOnceMessageV2',
    'viewOnceMessageV2Extension',
    'documentWithCaptionMessage',
    'deviceSentMessage',
    'lottieStickerMessage',
    'groupMentionedMessage'
];

function unwrap(message) {
    let m = message;
    let viewOnce = false;
    for (let i = 0; i < 6 && m; i++) {
        const key = WRAPPERS.find(k => m[k]?.message);
        if (!key) break;
        if (key.startsWith('viewOnce')) viewOnce = true;
        m = m[key].message;
    }
    if (m?.imageMessage?.viewOnce || m?.videoMessage?.viewOnce || m?.audioMessage?.viewOnce) viewOnce = true;
    return { content: m || {}, viewOnce };
}

const label = (name, viewOnce, extra) =>
    `[--- ${viewOnce ? 'View once ' : ''}${name}${extra ? ': ' + extra : ''} ---]`;

function withCaption(placeholder, caption) {
    return caption ? `${placeholder}\n${caption}` : placeholder;
}

/**
 * @returns {{ type: string, text: string, viewOnce: boolean } | null}  null = nothing worth logging
 */
function describeMessage(message) {
    if (!message) return null;
    const { content: c, viewOnce } = unwrap(message);

    if (c.conversation) return { type: 'text', text: c.conversation, viewOnce };
    if (c.extendedTextMessage?.text) return { type: 'text', text: c.extendedTextMessage.text, viewOnce };

    if (c.imageMessage) return { type: 'image', text: withCaption(label('Image', viewOnce), c.imageMessage.caption), viewOnce };
    if (c.videoMessage) {
        const name = c.videoMessage.gifPlayback ? 'GIF' : 'Video';
        return { type: c.videoMessage.gifPlayback ? 'gif' : 'video', text: withCaption(label(name, viewOnce), c.videoMessage.caption), viewOnce };
    }
    if (c.audioMessage) {
        const ptt = !!c.audioMessage.ptt;
        return { type: ptt ? 'voice' : 'audio', text: label(ptt ? 'Voice note' : 'Audio', viewOnce), viewOnce };
    }
    if (c.stickerMessage) return { type: 'sticker', text: label('Sticker'), viewOnce };
    if (c.documentMessage) {
        const name = c.documentMessage.fileName || c.documentMessage.title;
        return { type: 'document', text: withCaption(label('Document', false, name), c.documentMessage.caption), viewOnce };
    }
    if (c.locationMessage) {
        const l = c.locationMessage;
        return { type: 'location', text: label('Location', false, l.name || l.address), viewOnce };
    }
    if (c.liveLocationMessage) return { type: 'location', text: withCaption(label('Live location'), c.liveLocationMessage.caption), viewOnce };
    if (c.contactMessage) return { type: 'contact', text: label('Contact', false, c.contactMessage.displayName), viewOnce };
    if (c.contactsArrayMessage) {
        const names = (c.contactsArrayMessage.contacts || []).map(x => x.displayName).filter(Boolean).join(', ');
        return { type: 'contact', text: label('Contacts', false, names), viewOnce };
    }
    const poll = c.pollCreationMessage || c.pollCreationMessageV2 || c.pollCreationMessageV3;
    if (poll) return { type: 'poll', text: label('Poll', false, poll.name), viewOnce };
    if (c.eventMessage) return { type: 'event', text: label('Event', false, c.eventMessage.name), viewOnce };

    // Replies to buttons / lists / templates
    const reply = c.buttonsResponseMessage?.selectedDisplayText ||
        c.listResponseMessage?.title ||
        c.templateButtonReplyMessage?.selectedDisplayText;
    if (reply) return { type: 'text', text: reply, viewOnce };
    const bodyText = c.buttonsMessage?.contentText || c.templateMessage?.hydratedTemplate?.hydratedContentText ||
        c.listMessage?.description;
    if (bodyText) return { type: 'text', text: bodyText, viewOnce };

    return null;   // reactions, protocol messages, call logs, key distribution, etc.
}

/** Edit events: original key + the new content. Works for upsert and update shapes. */
function extractEdit(message, fallbackKey, fallbackTs) {
    if (!message) return null;
    const { content: c } = unwrap(message);

    // Shape 1: Baileys' processed messages.update -> { editedMessage: { message: <the new message> } }
    // (key and timestamp come from the update itself)
    const inner = c.editedMessage?.message;
    if (inner && !inner.protocolMessage && fallbackKey?.id) {
        const d = describeMessage(inner);
        if (!d || !d.text) return null;
        return { key: fallbackKey, text: d.text, ts: fallbackTs || Math.floor(Date.now() / 1000) };
    }

    // Shape 2: raw protocolMessage of type MESSAGE_EDIT (possibly wrapped in editedMessage)
    const proto = inner?.protocolMessage || c.protocolMessage;
    if (!proto || !proto.key?.id) return null;
    if (!(proto.type === 14 || proto.type === 'MESSAGE_EDIT')) return null;
    const described = describeMessage(proto.editedMessage);
    if (!described) return null;
    const tm = proto.timestampMs;
    const tsMs = tm ? (typeof tm.toNumber === 'function' ? tm.toNumber() : Number(tm)) : Date.now();
    return { key: proto.key, text: described.text, ts: Math.floor(tsMs / 1000) };
}

/** "Delete for everyone" events */
function extractRevoke(message) {
    if (!message) return null;
    const { content: c } = unwrap(message);
    const proto = c.protocolMessage;
    if (proto && (proto.type === 0 || proto.type === 'REVOKE') && proto.key?.id) return proto.key;
    return null;
}

module.exports = { describeMessage, extractEdit, extractRevoke, unwrap };
