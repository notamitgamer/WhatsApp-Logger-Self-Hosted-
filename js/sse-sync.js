// Instead of a connection per chat, we open ONE connection globally to pull missing messages
async function connectGlobalSyncStream() {
    const token = localStorage.getItem('wp_api_token');
    if(!token || !navigator.onLine) return;
    
    if(syncEventSource) {
        syncEventSource.close();
        syncEventSource = null;
    }
    
    maxCachedTimestamp = await getMaxCachedTimestamp();
    
    // Wait slightly for IDB to settle, show small UI if caching up
    const spinner = document.getElementById('globalSyncSpinner');
    if(maxCachedTimestamp > 0) spinner.classList.add('active');
    
    syncEventSource = new EventSource(`${RENDER_BACKEND_URL}/api/sync/stream?since=${maxCachedTimestamp}&token=${token}`);
    
    const handleSyncData = async (dataArray, eventType) => {
        const newToCache = [];
        let activeChatUpdated = false;
        
        dataArray.forEach(item => {
            const data = eventType === 'initial' ? item : item.doc;
            if (eventType === 'update' && item.type === 'removed') return;
            
            // The server must include chatId for this wildcard payload to work
            if(!data.chatId) return;
            
            newToCache.push(data);
            
            if (activeChatIds && activeChatIds.includes(data.chatId)) {
                activeChatUpdated = true;
            }
        });
        
        if (newToCache.length > 0) {
            await saveCachedMessages(newToCache);
            const maxTs = Math.max(...newToCache.map(m => m.timestamp || 0));
            if (maxTs > maxCachedTimestamp) maxCachedTimestamp = maxTs;
        }
        
        // If the currently open chat received messages, re-render from cache
        if (activeChatUpdated && activeChatId) {
            refreshActiveChatUI();
        }
        
        if (eventType === 'initial') {
            spinner.classList.remove('active');
        }
    };
    
    syncEventSource.addEventListener('initial', (e) => {
        syncEventRetryCount = 0;
        try { handleSyncData(JSON.parse(e.data), 'initial'); } catch(err) { console.error("Sync initial err", err); }
    });
    
    syncEventSource.addEventListener('update', (e) => {
        try { handleSyncData(JSON.parse(e.data), 'update'); } catch(err) { console.error("Sync update err", err); }
    });
    
    syncEventSource.onerror = (err) => {
        syncEventSource.close();
        syncEventRetryCount++;
        spinner.classList.remove('active');
        const backoff = Math.min(1000 * Math.pow(2, syncEventRetryCount), 60000);
        setTimeout(connectGlobalSyncStream, backoff);
    };
}
