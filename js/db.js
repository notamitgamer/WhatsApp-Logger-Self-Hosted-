const DB_NAME = 'WPLoggerDB';
const DB_VERSION = 2; 
let dbInstance = null;
let isIdbSupported = true;
let maxCachedTimestamp = 0;

function initIndexedDB() {
    return new Promise((resolve) => {
        let idb;
        try {
            idb = window.indexedDB;
        } catch (e) {
            console.warn("IDB Access Denied:", e);
            isIdbSupported = false;
            resolve();
            return;
        }

        if (!idb) {
            isIdbSupported = false;
            resolve();
            return;
        }

        let isResolved = false;
        const safeResolve = () => {
            if(!isResolved) {
                isResolved = true;
                resolve();
            }
        };

        const fallbackTimeout = setTimeout(() => {
            console.warn('IDB init timed out');
            isIdbSupported = false;
            safeResolve();
        }, 3000);

        try {
            const req = indexedDB.open(DB_NAME, DB_VERSION);
            req.onupgradeneeded = (e) => {
                const database = e.target.result;
                if (!database.objectStoreNames.contains('messages')) {
                    const store = database.createObjectStore('messages', { keyPath: 'id' });
                    store.createIndex('chatId', 'chatId', { unique: false });
                    store.createIndex('timestamp', 'timestamp', { unique: false });
                }
                if (!database.objectStoreNames.contains('chats')) {
                    database.createObjectStore('chats', { keyPath: 'id' });
                }
            };
            req.onsuccess = (e) => { 
                clearTimeout(fallbackTimeout);
                dbInstance = e.target.result; 
                
                dbInstance.onversionchange = () => {
                    dbInstance.close();
                    dbInstance = null;
                };
                
                safeResolve(); 
            };
            req.onerror = (e) => { 
                clearTimeout(fallbackTimeout);
                console.warn('IDB init error', e); 
                isIdbSupported = false; 
                safeResolve(); 
            };
            req.onblocked = (e) => {
                console.warn('IDB init blocked. Waiting for other connections to close...');
            };
        } catch (e) {
            clearTimeout(fallbackTimeout);
            console.warn('IDB open exception', e);
            isIdbSupported = false;
            safeResolve();
        }
    });
}

async function getCachedMessages(chatId) {
    if(!isIdbSupported || !dbInstance) return [];
    return new Promise((resolve) => {
        try {
            const tx = dbInstance.transaction('messages', 'readonly');
            const store = tx.objectStore('messages');
            const index = store.index('chatId');
            const req = index.getAll(chatId);
            req.onsuccess = () => resolve(req.result || []);
            req.onerror = () => resolve([]);
        } catch(e) { resolve([]); }
    });
}

async function getMaxCachedTimestamp() {
    if(!isIdbSupported || !dbInstance) return 0;
    return new Promise((resolve) => {
        try {
            const tx = dbInstance.transaction('messages', 'readonly');
            const store = tx.objectStore('messages');
            const index = store.index('timestamp');
            // Open a cursor going backwards to get the absolute highest timestamp stored
            const req = index.openCursor(null, 'prev');
            req.onsuccess = (e) => {
                const cursor = e.target.result;
                if (cursor) resolve(cursor.key);
                else resolve(0);
            };
            req.onerror = () => resolve(0);
        } catch(e) { resolve(0); }
    });
}

async function saveCachedMessages(messages) {
    if(!isIdbSupported || !dbInstance) return;
    return new Promise((resolve) => {
        try {
            const tx = dbInstance.transaction('messages', 'readwrite');
            const store = tx.objectStore('messages');
            messages.forEach(m => store.put(m));
            tx.oncomplete = () => resolve();
            tx.onerror = () => resolve();
        } catch(e) { resolve(); }
    });
}

async function getCachedChats() {
    if(!isIdbSupported || !dbInstance) return [];
    return new Promise((resolve) => {
        try {
            const tx = dbInstance.transaction('chats', 'readonly');
            const store = tx.objectStore('chats');
            const req = store.getAll();
            req.onsuccess = () => resolve(req.result || []);
            req.onerror = () => resolve([]);
        } catch(e) { resolve([]); }
    });
}

async function saveCachedChats(chats) {
    if(!isIdbSupported || !dbInstance) return;
    return new Promise((resolve) => {
        try {
            const tx = dbInstance.transaction('chats', 'readwrite');
            const store = tx.objectStore('chats');
            chats.forEach(c => store.put(c));
            tx.oncomplete = () => resolve();
            tx.onerror = () => resolve();
        } catch(e) { resolve(); }
    });
}
