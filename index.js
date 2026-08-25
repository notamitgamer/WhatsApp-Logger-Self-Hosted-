const {
    default: makeWASocket,
    DisconnectReason,
    fetchLatestBaileysVersion,
    BufferJSON,
    initAuthCreds,
    proto
} = require('@whiskeysockets/baileys');
const express = require('express');
const QRCode = require('qrcode');
const pino = require('pino');
const admin = require('firebase-admin');
const crypto = require('crypto');

// --- CONFIGURATION ---
const PORT = process.env.PORT || 3000;
const AUTH_USER = process.env.AUTH_USER;
const AUTH_PASS = process.env.AUTH_PASS;

// Initialize Express
const app = express();
app.use(express.urlencoded({ extended: true }));
app.use(express.json()); 

app.use((req, res, next) => {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");
    next();
});

// Answer every OPTIONS preflight immediately — before ANY auth middleware sees it
app.options('*', (req, res) => {
    res.sendStatus(200);
});

// --- IN-MEMORY LOGGING BUFFER ---
const MAX_LOGS = 500;
const logBuffer = [];

const originalLog = console.log;
const originalError = console.error;

function teeLog(level, originalFn, ...args) {
    const message = args.map(arg => 
        typeof arg === 'object' ? JSON.stringify(arg) : String(arg)
    ).join(' ');
    
    logBuffer.push({ timestamp: Date.now(), level, message });
    if (logBuffer.length > MAX_LOGS) logBuffer.shift();
    
    originalFn.apply(console, args);
}

console.log = (...args) => teeLog('log', originalLog, ...args);
console.error = (...args) => teeLog('error', originalError, ...args);

// --- FIREBASE SETUP ---
let serviceAccount;
try {
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
        serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    } else {
        serviceAccount = require('./serviceAccountKey.json');
    }

    admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
    });
    console.log("System: Firebase Admin initialized successfully.");
} catch (error) {
    console.error("System Error: Failed to initialize Firebase. Make sure FIREBASE_SERVICE_ACCOUNT env var is set.");
    process.exit(1);
}

const db = admin.firestore();

// --- IN-MEMORY CACHE ---
const EXCLUDED_JIDS = new Set(['917278779512@s.whatsapp.net', '201554426618024@lid']);

let chatsCache = new Map();      // chatId -> chat data
let messagesCache = new Map();   // chatId -> Map(msgId -> message data)
let cacheReady = false;
let consecutiveCacheFailures = 0;

async function warmCache() {
    try {
        console.log("System: Warming cache from Firestore...");
        
        // 1. Fetch Chats
        const chatsSnap = await db.collection('Chats').get();
        chatsSnap.forEach(doc => {
            if (!EXCLUDED_JIDS.has(doc.id)) {
                chatsCache.set(doc.id, { id: doc.id, ...doc.data() });
            }
        });

        // 2. Fetch all Messages via CollectionGroup
        const msgsSnap = await db.collectionGroup('Messages').get();
        msgsSnap.forEach(doc => {
            const chatId = doc.ref.parent.parent.id;
            if (!messagesCache.has(chatId)) messagesCache.set(chatId, new Map());
            messagesCache.get(chatId).set(doc.id, doc.data());
        });
        
        cacheReady = true;
        consecutiveCacheFailures = 0;
        console.log(`System: Cache warm. ${chatsCache.size} chats, ${msgsSnap.size} messages.`);

        // 3. Start Permanent Listeners
        startPermanentListeners();

    } catch (err) {
        consecutiveCacheFailures++;
        const backoff = Math.min(5000 * Math.pow(2, consecutiveCacheFailures), 300000); // Max 5 min
        console.error(`System: Cache warm failed (attempt ${consecutiveCacheFailures}). Retrying in ${backoff/1000}s.`, err.message);
        setTimeout(warmCache, backoff);
    }
}

function startPermanentListeners() {
    // Shared Permanent Listener for Chats
    db.collection('Chats').onSnapshot(snapshot => {
        const changes = [];
        snapshot.docChanges().forEach(change => {
            if (EXCLUDED_JIDS.has(change.doc.id)) return;
            const data = { id: change.doc.id, ...change.doc.data() };
            chatsCache.set(change.doc.id, data);
            changes.push({ type: change.type, doc: data });
        });
        if (changes.length > 0) {
            const payload = `event: update\ndata: ${JSON.stringify(changes)}\n\n`;
            clients.chats.forEach(res => { try { res.write(payload); } catch(e){} });
        }
    });

    // Shared Permanent Listener for Messages
    db.collectionGroup('Messages').onSnapshot(snapshot => {
        snapshot.docChanges().forEach(change => {
            const chatId = change.doc.ref.parent.parent.id;
            if (!messagesCache.has(chatId)) messagesCache.set(chatId, new Map());
            
            const data = change.doc.data();
            messagesCache.get(chatId).set(change.doc.id, data);

            const chatClients = clients.messages.get(chatId);
            if (chatClients) {
                const payload = `event: update\ndata: ${JSON.stringify([{ type: change.type, doc: data }])}\n\n`;
                chatClients.forEach(res => { try { res.write(payload); } catch(e){} });
            }
        });
    });
}

// --- FIRESTORE AUTH ADAPTER FOR BAILEYS ---
async function useFirestoreAuthState(db, collectionName = 'whatsapp_auth') {
    const collection = db.collection(collectionName);

    const writeData = async (data, id) => {
        try {
            const str = JSON.stringify(data, BufferJSON.replacer);
            await collection.doc(id).set({ data: str });
        } catch (err) {
            console.error("System: Error writing auth state:", err.message);
        }
    };

    const readData = async (id, throwOnError = false) => {
        try {
            const doc = await collection.doc(id).get();
            if (doc.exists) {
                return JSON.parse(doc.data().data, BufferJSON.reviver);
            }
        } catch (err) {
            console.error("System: Error reading auth state:", err.message);
            if (throwOnError) throw err;
        }
        return null;
    };

    const removeData = async (id) => {
        try {
            await collection.doc(id).delete();
        } catch (err) {
            console.error("System: Error removing auth state:", err.message);
        }
    };

    let creds;
    try {
        // Pass true to strictly enforce failure up to startWhatsApp for backoff
        creds = (await readData('creds', true)) || initAuthCreds();
    } catch (err) {
        throw err;
    }

    return {
        state: {
            creds,
            keys: {
                get: async (type, ids) => {
                    const data = {};
                    await Promise.all(ids.map(async id => {
                        let value = await readData(`${type}-${id}`);
                        if (type === 'app-state-sync-key' && value) {
                            value = proto.Message.AppStateSyncKeyData.fromObject(value);
                        }
                        data[id] = value;
                    }));
                    return data;
                },
                set: async (data) => {
                    const tasks = [];
                    for (const category in data) {
                        for (const id in data[category]) {
                            const value = data[category][id];
                            const docId = `${category}-${id}`;
                            if (value) {
                                tasks.push(writeData(value, docId));
                            } else {
                                tasks.push(removeData(docId));
                            }
                        }
                    }
                    await Promise.all(tasks);
                }
            }
        },
        saveCreds: () => {
            return writeData(creds, 'creds');
        },
        clearState: async () => {
            await removeData('creds');
        }
    };
}

// --- BAILEYS SETUP ---
let qrCodeData = null; 
let sock = null;
let isConnected = false; 

let consecutiveAuthFailures = 0;
let consecutiveConnectFailures = 0;

async function startWhatsApp() {
    const logger = pino({ level: 'silent' });
    
    let authResult;
    try {
        authResult = await useFirestoreAuthState(db, 'whatsapp_auth');
    } catch (err) {
        consecutiveAuthFailures++;
        const backoff = Math.min(5000 * Math.pow(2, consecutiveAuthFailures), 300000); // cap 5 min
        console.error(`System: Auth state read failed (attempt ${consecutiveAuthFailures}). Retrying in ${backoff/1000}s.`);
        setTimeout(startWhatsApp, backoff);
        return;
    }
    consecutiveAuthFailures = 0; // Reset on success

    const { state, saveCreds, clearState } = authResult;
    const { version } = await fetchLatestBaileysVersion();

    console.log("System: Connecting to WhatsApp servers...");

    sock = makeWASocket({
        version,
        logger,
        auth: state,
        browser: ["WhatsApp Logger v4.2.1", "Chrome", "4.2.1"],
        syncFullHistory: true 
    });

    sock.ev.on('connection.update', async (update) => {
        const { connection, lastDisconnect, qr } = update;

        if (qr) {
            console.log("System: No valid credentials. New QR Code generated.");
            qrCodeData = qr;
            isConnected = false;
        }

        if (connection === 'close') {
            isConnected = false;
            const statusCode = lastDisconnect?.error?.output?.statusCode;
            const shouldReconnect = statusCode !== DisconnectReason.loggedOut;

            if (shouldReconnect) {
                consecutiveConnectFailures++;
                const backoff = Math.min(5000 * Math.pow(2, consecutiveConnectFailures), 300000); // cap 5 min
                console.log(`System: Connection closed (Status: ${statusCode}). Reconnecting in ${backoff/1000}s...`);
                setTimeout(startWhatsApp, backoff);
            } else {
                console.log("System: Device Logged Out. Wiping session from Firestore.");
                await clearState();
                qrCodeData = null;
                consecutiveConnectFailures = 0;
                startWhatsApp(); 
            }
        } else if (connection === 'open') {
            console.log("System: Connection Open and Authenticated. Firebase Auth Sync Active.");
            qrCodeData = null;
            isConnected = true;
            consecutiveConnectFailures = 0; // Reset on success
        }
    });

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('contacts.upsert', async (contacts) => {
        for (const contact of contacts) {
            let updateData = {};
            const displayName = contact.name || contact.notify;
            
            if (displayName) updateData.displayName = displayName;

            if (contact.id && contact.id.endsWith('@s.whatsapp.net')) {
                updateData.phoneNumber = contact.id.split('@')[0];
            }

            const primaryId = contact.lid || contact.id;

            if (primaryId && Object.keys(updateData).length > 0) {
                try {
                    await db.collection('Chats').doc(primaryId).set(updateData, { merge: true });
                    
                    if (contact.lid && contact.id !== contact.lid) {
                        await db.collection('Chats').doc(contact.id).set(updateData, { merge: true });
                    }
                } catch (err) {}
            }
        }
    });

    sock.ev.on('messages.upsert', async ({ messages, type }) => {
        if (type !== 'notify' && type !== 'append') return;

        for (const msg of messages) {
            try {
                if (!msg.message) continue;

                const remoteJid = msg.key.remoteJid;
                if (remoteJid === 'status@broadcast') continue;

                const textContent = 
                    msg.message.conversation || 
                    msg.message.extendedTextMessage?.text || 
                    msg.message.imageMessage?.caption || 
                    msg.message.videoMessage?.caption || 
                    "";

                if (!textContent) continue;

                const timestamp = msg.messageTimestamp 
                    ? (typeof msg.messageTimestamp === 'number' ? msg.messageTimestamp : msg.messageTimestamp.low) 
                    : Math.floor(Date.now() / 1000);

                const isFromMe = msg.key.fromMe || false;
                const senderName = isFromMe ? "Me" : (msg.pushName || "Unknown");

                // 1. Ensure Chat Document Exists
                await db.collection('Chats').doc(remoteJid).set({
                    lastActive: timestamp,
                    id: remoteJid,
                    preview: textContent 
                }, { merge: true });

                // 2. Save Message
                await db.collection('Chats')
                    .doc(remoteJid)
                    .collection('Messages')
                    .doc(msg.key.id)
                    .set({
                        text: textContent,
                        senderId: remoteJid,
                        senderName: senderName,
                        timestamp: timestamp,
                        fromMe: isFromMe,
                        id: msg.key.id
                    }, { merge: true });

            } catch (err) {}
        }
    });
}

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

// --- SSE CONNECTION MANAGER ---
const MAX_CONNECTIONS_PER_TOKEN = 15;
const activeConnections = []; 
const clients = {
    chats: new Set(),
    messages: new Map() 
};

function enforceConnectionCeiling(req, res, cleanupFunction) {
    const token = req.query.token || req.headers.authorization?.split(' ')[1];
    activeConnections.push({ res, token, cleanup: cleanupFunction });

    const userConns = activeConnections.filter(c => c.token === token);
    if (userConns.length > MAX_CONNECTIONS_PER_TOKEN) {
        const oldestIdx = activeConnections.findIndex(c => c.token === token);
        if (oldestIdx > -1) {
            const oldest = activeConnections[oldestIdx];
            oldest.cleanup();
            oldest.res.end();
            activeConnections.splice(oldestIdx, 1);
        }
    }

    res.on('close', () => {
        const idx = activeConnections.findIndex(c => c.res === res);
        if (idx > -1) activeConnections.splice(idx, 1);
        cleanupFunction();
    });
}

// Global heartbeat to keep Render connections alive
setInterval(() => {
    activeConnections.forEach(({ res }) => {
        try { res.write(': ping\n\n'); } catch (e) {}
    });
}, 25000);


// --- EXPRESS ROUTES ---

app.get('/ping', (req, res) => {
    res.status(200).send('Pong');
});

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

app.post('/api/verify', (req, res) => {
    const { username, password } = req.body;

    if (username === AUTH_USER && password === AUTH_PASS) {
        return res.json({ success: true, token: SESSION_SECRET });
    } else {
        return res.status(401).json({ success: false });
    }
});

// --- API: Server Sent Events ---
app.get('/api/chats/stream', verifyApiToken, async (req, res) => {
    if (!cacheReady) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'X-Accel-Buffering': 'no'
    });
    if (res.flushHeaders) res.flushHeaders();
    if (res.socket) res.socket.setNoDelay(true);
    res.write('\n');

    const cleanup = () => {
        clients.chats.delete(res);
    };
    
    enforceConnectionCeiling(req, res, cleanup);
    clients.chats.add(res);

    try {
        const grouped = {};
        
        chatsCache.forEach((data, docId) => {
            const phone = data.phoneNumber || data.id.split('@')[0];
            
            if (!grouped[phone]) {
                grouped[phone] = { ...data, ids: [data.id] };
            } else {
                grouped[phone].ids.push(data.id);
                if ((data.lastActive || 0) > (grouped[phone].lastActive || 0)) {
                    grouped[phone].lastActive = data.lastActive;
                    grouped[phone].preview = data.preview || grouped[phone].preview;
                }
                if (data.customName) grouped[phone].customName = data.customName;
            }
        });

        res.write(`event: initial\ndata: ${JSON.stringify(Object.values(grouped))}\n\n`);
    } catch (e) {
        console.error("Error sending initial chats:", e);
    }
});

app.get('/api/messages/stream', verifyApiToken, async (req, res) => {
    if (!cacheReady) return res.status(503).json({ error: 'Cache still warming, retry shortly' });
    
    const { chatId, since } = req.query;
    if (!chatId) return res.status(400).send('Missing chatId');

    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'X-Accel-Buffering': 'no'
    });
    if (res.flushHeaders) res.flushHeaders();
    if (res.socket) res.socket.setNoDelay(true);
    res.write('\n');

    const cleanup = () => {
        const chatClients = clients.messages.get(chatId);
        if (chatClients) {
            chatClients.delete(res);
            if (chatClients.size === 0) clients.messages.delete(chatId);
        }
    };
    
    enforceConnectionCeiling(req, res, cleanup);

    if (!clients.messages.has(chatId)) {
        clients.messages.set(chatId, new Set());
    }
    clients.messages.get(chatId).add(res);

    try {
        let initialMessages = [];
        const chatMsgs = messagesCache.get(chatId);
        
        if (chatMsgs) {
            const sinceTs = since ? parseInt(since, 10) : 0;
            for (const msg of chatMsgs.values()) {
                if (msg.timestamp > sinceTs) {
                    initialMessages.push(msg);
                }
            }
            initialMessages.sort((a, b) => a.timestamp - b.timestamp);
        }
        
        res.write(`event: initial\ndata: ${JSON.stringify(initialMessages)}\n\n`);
    } catch (e) {
        console.error("Error sending initial messages:", e);
    }
});

// --- API: Standard Actions ---
app.post('/api/rename', verifyApiToken, async (req, res) => {
    if (!cacheReady) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    const { id, customName } = req.body;
    if (!id || !customName) return res.status(400).json({ error: 'Missing parameters' });
    
    try {
        // Fire & Forget to DB
        await db.collection('Chats').doc(id).set({ customName }, { merge: true });
        
        // Instant RAM update
        if (chatsCache.has(id)) {
            chatsCache.get(id).customName = customName;
        }
        
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get('/api/export', verifyApiToken, async (req, res) => {
    if (!cacheReady) return res.status(503).json({ error: 'Cache still warming, retry shortly' });

    try {
        const exportData = { chats: {}, messages: {} };
        
        chatsCache.forEach((data, id) => exportData.chats[id] = data);
        messagesCache.forEach((msgMap, chatId) => {
            exportData.messages[chatId] = Array.from(msgMap.values());
        });
        
        res.json(exportData);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// --- SYSTEM LOGS ROUTE ---
app.get('/logs', verifyLogsAccess, (req, res) => {
    const wantsJson = req.query.format === 'json' || (req.headers.accept && req.headers.accept.includes('application/json'));
    
    if (wantsJson) {
        return res.json(logBuffer);
    }

    const logLines = logBuffer.map(l => {
        const time = new Date(l.timestamp).toLocaleTimeString();
        const color = l.level === 'error' ? '#ff6b6b' : '#a9dc76';
        return `<div style="color: ${color}">[${time}] [${l.level.toUpperCase()}] ${l.message}</div>`;
    }).join('');

    res.send(`
        <html>
            <head>
                <meta http-equiv="refresh" content="5">
                <title>System Logs</title>
                <style>
                    body { font-family: monospace; background: #1e1e1e; color: #d4d4d4; padding: 20px; }
                    .log-container { background: #000; padding: 15px; border-radius: 5px; overflow-x: auto; max-width: 100%; white-space: pre-wrap; font-size: 14px; line-height: 1.5; }
                </style>
            </head>
            <body onload="window.scrollTo(0,document.body.scrollHeight);">
                <h2 style="color: #fff; margin-top: 0;">System Logs</h2>
                <div class="log-container">
                    ${logLines.length > 0 ? logLines : '<div>No logs yet...</div>'}
                </div>
            </body>
        </html>
    `);
});

// --- WEB UI ROUTES ---
app.get('/login', (req, res) => {
    res.send(`
        <html>
            <body style="font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; background: #f0f2f5;">
                <form action="/login" method="POST" style="background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); width: 300px;">
                    <h2 style="margin-top: 0; text-align: center;">WhatsApp Logger <span style="font-size: 14px; font-weight: normal; color: #666;">v4.2.1</span></h2>
                    <div style="margin-bottom: 1rem;">
                        <label style="display: block; margin-bottom: 0.5rem;">Username</label>
                        <input type="text" name="username" required style="width: 100%; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;">
                    </div>
                    <div style="margin-bottom: 1rem;">
                        <label style="display: block; margin-bottom: 0.5rem;">Password</label>
                        <input type="password" name="password" required style="width: 100%; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;">
                    </div>
                    <div style="margin-bottom: 1rem;">
                        <label style="display: flex; align-items: center; font-size: 0.9rem;">
                            <input type="checkbox" name="remember" value="yes" style="margin-right: 0.5rem;">
                            Keep me logged in for 5 mins
                        </label>
                    </div>
                    <button type="submit" style="width: 100%; padding: 0.75rem; background: #25D366; color: white; border: none; border-radius: 4px; font-weight: bold; cursor: pointer;">Login</button>
                </form>
            </body>
        </html>
    `);
});

app.post('/login', (req, res) => {
    const { username, password, remember } = req.body;

    if (username === AUTH_USER && password === AUTH_PASS) {
        let cookieSettings = 'HttpOnly; Path=/;'; 
        if (remember === 'yes') cookieSettings += ' Max-Age=300;';
        
        res.setHeader('Set-Cookie', `auth_session=${SESSION_SECRET}; ${cookieSettings}`);
        return res.redirect('/');
    }
    res.status(401).send('Invalid credentials. <a href="/login">Try again</a>');
});

app.get('/logout', (req, res) => {
    res.setHeader('Set-Cookie', 'auth_session=; Max-Age=0; Path=/;');
    res.redirect('/login');
});

const checkAuth = (req, res, next) => {
    if (!AUTH_USER || !AUTH_PASS) return next();
    const cookies = parseCookies(req);
    if (cookies.auth_session === SESSION_SECRET) return next();
    
    if (req.path.startsWith('/api')) res.status(401).send('Unauthorized');
    else res.redirect('/login');
};

app.use(checkAuth);

app.get('/', async (req, res) => {
    const logoutBtn = `<a href="/logout" style="position: absolute; top: 10px; right: 10px; padding: 8px 16px; background: #ff4444; color: white; text-decoration: none; border-radius: 4px; font-size: 14px;">Logout</a>`;

    if (isConnected) {
        return res.send(`
            <html>
                <body style="font-family: sans-serif; text-align: center; padding-top: 50px; background-color: #f0f2f5;">
                    ${logoutBtn}
                    <div style="background: white; padding: 40px; border-radius: 10px; display: inline-block; box-shadow: 0 4px 12px rgba(0,0,0,0.1);">
                        <h2 style="color: green;">System Operational</h2>
                        <p style="color: #555;">Connected to WhatsApp. State synced to Firestore.</p>
                        <p style="color: #999; font-size: 12px;">Back-end Service</p>
                    </div>
                </body>
            </html>
        `);
    }

    if (qrCodeData) {
        try {
            const qrImage = await QRCode.toDataURL(qrCodeData);
            return res.send(`
                <html>
                    <head><meta http-equiv="refresh" content="5"></head>
                    <body style="font-family: sans-serif; text-align: center; padding-top: 50px; background-color: #f0f2f5;">
                        ${logoutBtn}
                        <div style="background: white; padding: 40px; border-radius: 10px; display: inline-block; box-shadow: 0 4px 12px rgba(0,0,0,0.1);">
                            <h2>Scan to Link</h2>
                            <img src="${qrImage}" alt="QR Code" />
                            <p style="color: #666;">Refreshes every 5 seconds...</p>
                        </div>
                    </body>
                </html>
            `);
        } catch (e) {
            return res.send("Error generating QR.");
        }
    }

    return res.send(`
        <html>
            <head><meta http-equiv="refresh" content="2"></head>
            <body style="font-family: sans-serif; text-align: center; padding-top: 50px;">
                <p>Initializing connection or restoring auth state... please wait.</p>
                ${logoutBtn}
            </body>
        </html>
    `);
});

// --- START SERVER ---
app.listen(PORT, () => {
    warmCache();
    startWhatsApp();
    console.log(`Server running on port ${PORT}`);
});
