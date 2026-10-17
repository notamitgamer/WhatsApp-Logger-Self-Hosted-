function refreshApp() {
    document.getElementById('appOptionsModal').classList.add('active');
}

function executePageRefresh() {
    window.location.reload(true);
}

function openHardReset() {
    closeModal('appOptionsModal');
    document.getElementById('refreshAuthStep').classList.remove('hidden');
    document.getElementById('refreshProgressStep').classList.add('hidden');
    document.getElementById('refreshUsername').value = '';
    document.getElementById('refreshPassword').value = '';
    document.getElementById('refreshError').style.display = 'none';
    document.getElementById('refreshModal').classList.add('active');
}

async function startHardRefresh() {
    const btn = document.getElementById('refreshBtn'); const err = document.getElementById('refreshError');
    const u = document.getElementById('refreshUsername').value; const p = document.getElementById('refreshPassword').value;

    btn.classList.add('loading'); btn.disabled = true; err.style.display = 'none';

    try {
        const response = await fetch(`${RENDER_BACKEND_URL}/api/verify`, {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ username: u, password: p })
        });
        const data = await response.json();

        if (!data.success) throw new Error();

        if (data.token) localStorage.setItem('wp_api_token', data.token);

        document.getElementById('refreshAuthStep').classList.add('hidden');
        document.getElementById('refreshProgressStep').classList.remove('hidden');
        
        await executeHardRefresh();

    } catch (e) {
        err.style.display = 'block';
        btn.classList.remove('loading'); btn.disabled = false;
    }
}

function updateRefreshProgress(percent, text) {
    document.getElementById('refreshProgressBar').style.width = `${percent}%`;
    document.getElementById('refreshPercentText').innerText = `${Math.floor(percent)}%`;
    document.getElementById('refreshStatusText').innerText = text;
}

async function executeHardRefresh() {
    try {
        updateRefreshProgress(5, "Clearing caches...");
        if ('caches' in window) { try { const keys = await caches.keys(); await Promise.all(keys.map(key => caches.delete(key))); } catch(e) {} }
        if ('serviceWorker' in navigator) { const regs = await navigator.serviceWorker.getRegistrations(); for (let r of regs) await r.unregister(); }
        
        updateRefreshProgress(10, "Clearing local storage...");
        const theme = localStorage.getItem('theme');
        const tkn = localStorage.getItem('wp_api_token');
        localStorage.clear();
        if(theme) localStorage.setItem('theme', theme);
        if(tkn) localStorage.setItem('wp_api_token', tkn);

        updateRefreshProgress(15, "Wiping database...");
        if(dbInstance) { dbInstance.close(); dbInstance = null; }
        
        await new Promise(r => setTimeout(r, 100));

        await new Promise(res => { 
            try {
                const req = indexedDB.deleteDatabase(DB_NAME); 
                req.onsuccess = res; 
                req.onerror = res; 
                req.onblocked = () => {
                    res();
                };
            } catch(e) { res(); }
        });
        
        await new Promise(r => setTimeout(r, 200));
        
        updateRefreshProgress(25, "Rebuilding database...");
        isIdbSupported = true;
        await initIndexedDB();

        updateRefreshProgress(30, "Fetching chat list...");
        
        const token = localStorage.getItem('wp_api_token');
        const res = await fetch(`${RENDER_BACKEND_URL}/api/export/chats`, { headers: { 'Authorization': `Bearer ${token}` } });
        const exportData = await res.json();
        
        const chatsData = exportData.chats || [];
        const totalMessages = Math.max(1, exportData.totalMessages || 1);
        const groups = {};
        const excluded = ['917278779512@s.whatsapp.net', '201554426618024@lid'];

        chatsData.forEach(d => {
            if (!d.lastActive || excluded.includes(d.id)) return;
            const phone = d.phoneNumber || d.id.split('@')[0];
            if (!groups[phone]) groups[phone] = { main: d, ids: [d.id] };
            else {
                groups[phone].ids.push(d.id);
                if (d.lastActive > groups[phone].main.lastActive) groups[phone].main = d;
            }
        });

        const chats = Object.values(groups).map(g => ({...g.main, ids: g.ids})).sort((a,b) => (b.lastActive || 0) - (a.lastActive || 0) || a.id.localeCompare(b.id));
        await saveCachedChats(chats);

        let messagesFetchedSoFar = 0;

        for (const chat of chats) {
            const name = chat.customName || chat.displayName || chat.phoneNumber || chat.id;
            
            let currentPercent = Math.min(99, 30 + ((messagesFetchedSoFar / totalMessages) * 70));
            updateRefreshProgress(currentPercent, `Fetching: ${name}...`);
            
            for (const id of chat.ids) {
                try {
                    const msgRes = await fetch(`${RENDER_BACKEND_URL}/api/export/messages?chatId=${encodeURIComponent(id)}&token=${token}`, { headers: { 'Authorization': `Bearer ${token}` } });
                    if (msgRes.ok) {
                        const msgData = await msgRes.json();
                        const msgs = msgData.messages || [];
                        if (msgs.length > 0) {
                            msgs.forEach(m => m.chatId = id);
                            await saveCachedMessages(msgs);
                            messagesFetchedSoFar += msgs.length;
                            
                            currentPercent = Math.min(99, 30 + ((messagesFetchedSoFar / totalMessages) * 70));
                            updateRefreshProgress(currentPercent, `Fetching: ${name}...`);
                        }
                    }
                } catch (err) {
                    console.warn(`Failed to fetch messages for ${id}`, err);
                }
            }
        }

        updateRefreshProgress(100, "Sync complete! Restarting...");
        setTimeout(() => window.location.reload(), 1000);

    } catch (e) {
        console.error("Hard refresh error", e);
        updateRefreshProgress(100, "Error during sync. Reloading...");
        setTimeout(() => window.location.reload(), 2000);
    }
}

function calculateLocalStorageUsage() {
    let totalBytes = 0;
    for (let key in localStorage) {
        if (!localStorage.hasOwnProperty(key)) continue;
        totalBytes += ((key.length + localStorage[key].length) * 2);
    }
    
    const sizeInKB = (totalBytes / 1024).toFixed(2);
    const displayElement = document.getElementById('localStorageSizeDisplay');
    
    if (displayElement) {
        if (sizeInKB >= 1024) {
            displayElement.innerText = `${(sizeInKB / 1024).toFixed(2)} MB occupied`;
        } else {
            displayElement.innerText = `${sizeInKB} KB occupied`;
        }
    }
}
