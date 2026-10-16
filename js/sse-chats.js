async function loadChats() {
    const list = document.getElementById('contactList'); 
    const token = localStorage.getItem('wp_api_token');
    if(!token) return handleLogout();
    
    const cachedChats = await getCachedChats();
    if (cachedChats && cachedChats.length > 0) {
        cachedChats.sort((a,b) => (b.lastActive || 0) - (a.lastActive || 0) || a.id.localeCompare(b.id));
        renderChatList(cachedChats);
    } else {
        list.innerHTML = '';
        for(let i=0; i<5; i++) list.innerHTML += `<div class="skel-contact fade-in"><div class="skeleton skel-avatar"></div><div class="skel-text-col"><div class="skeleton" style="width:60%; height:16px;"></div><div class="skeleton" style="width:40%; height:12px;"></div></div></div>`;
    }

    if (!navigator.onLine) return; 

    if (chatEventSource) {
        chatEventSource.close();
        chatEventSource = null;
    }

    function connectChatsStream() {
        chatEventSource = new EventSource(`${RENDER_BACKEND_URL}/api/chats/stream?token=${token}`);

        chatEventSource.addEventListener('initial', async (e) => {
            chatEventRetryCount = 0;
            try {
                const rawChats = JSON.parse(e.data);
                // Normalize subIds to ids to prevent silent crashes
                const chats = rawChats.map(g => ({ ...g, ids: g.subIds || g.ids }));
                
                rawChatsMap.clear();
                chats.forEach(g => {
                    if (g.ids) {
                        g.ids.forEach(id => {
                            rawChatsMap.set(id, { id: id, lastActive: g.lastActive, phoneNumber: g.phoneNumber, customName: g.customName, displayName: g.displayName, preview: g.preview });
                        });
                    }
                });
                
                chats.sort((a,b) => (b.lastActive || 0) - (a.lastActive || 0) || a.id.localeCompare(b.id));
                await saveCachedChats(chats);
                renderChatList(chats);
            } catch (err) {
                console.error("Chat list initial parse error:", err);
            }
        });

        chatEventSource.addEventListener('update', async (e) => {
            try {
                const changes = JSON.parse(e.data);
                let needsRebuild = false;
                
                changes.forEach(change => {
                    if (change.type === 'added' || change.type === 'modified') {
                        rawChatsMap.set(change.doc.id, change.doc);
                        needsRebuild = true;
                    } else if (change.type === 'removed') {
                        rawChatsMap.delete(change.doc.id);
                        needsRebuild = true;
                    }
                });
                
                if (needsRebuild) {
                    const groups = {};
                    rawChatsMap.forEach((d, docId) => {
                        const phone = d.phoneNumber || docId.split('@')[0];
                        if (!groups[phone]) {
                            groups[phone] = { main: { id: docId, ...d }, ids: [docId] };
                        } else {
                            groups[phone].ids.push(docId);
                            if ((d.lastActive || 0) > (groups[phone].main.lastActive || 0)) {
                                groups[phone].main = { id: docId, ...d };
                            }
                        }
                    });
                    
                    const chats = Object.values(groups).map(g => ({...g.main, ids: g.ids})).sort((a,b) => (b.lastActive || 0) - (a.lastActive || 0) || a.id.localeCompare(b.id));
                    await saveCachedChats(chats);
                    renderChatList(chats);
                }
            } catch (err) {
                console.error("Chat list update parse error:", err);
            }
        });

        chatEventSource.onerror = (err) => {
            chatEventSource.close();
            chatEventRetryCount++;
            const backoff = Math.min(1000 * Math.pow(2, chatEventRetryCount), 30000);
            setTimeout(connectChatsStream, backoff);
        };
    }
    
    connectChatsStream();
}

function renderChatList(chats) {
    const list = document.getElementById('contactList');
    
    if (chats.length === 0) {
        list.innerHTML = `<div style="text-align:center; padding:32px; color:var(--md-sys-color-outline);">No chats found</div>`;
        return;
    }

    if (list.querySelector('.skel-contact') || list.innerHTML.includes('No chats found')) {
        list.innerHTML = '';
    }

    const currentIds = new Set(chats.map(c => 'chat-' + c.id.replace(/[^a-zA-Z0-9]/g, '-')));

    chats.forEach((c) => {
        const safeDomId = 'chat-' + c.id.replace(/[^a-zA-Z0-9]/g, '-');
        let el = document.getElementById(safeDomId);
        
        const primaryName = c.customName || c.displayName || c.phoneNumber || c.id;
        const isGroup = c.id.includes('@g.us');

        if (!el) {
            el = document.createElement('div');
            el.id = safeDomId;
            el.className = 'contact-item ripple-surface';
            el.innerHTML = `
                <div class="avatar">${isGroup ? '<span class="material-symbols-rounded">group</span>' : escapeHTML(primaryName[0]).toUpperCase()}</div>
                <div class="contact-info" style="min-width: 0;">
                    <div class="contact-name" style="display:flex; justify-content:space-between; align-items:baseline; width:100%;">
                        <span class="name-text" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis; flex:1;">${escapeHTML(primaryName)}</span>
                        <span class="chat-time" style="font-size: 12px; color: var(--md-sys-color-on-surface-variant); font-weight: 400; margin-left: 8px; flex-shrink: 0;"></span>
                    </div>
                    <div class="contact-sub preview-text" style="display:flex; align-items:center; margin-top:2px; color:var(--md-sys-color-on-surface-variant);">
                        <span class="preview-content" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis; width:100%;"></span>
                    </div>
                </div>
            `;
            el.dataset.lastActive = c.lastActive;
        } else {
            const nameSpan = el.querySelector('.name-text');
            if (nameSpan.innerText !== primaryName) {
                nameSpan.innerText = primaryName;
                el.querySelector('.avatar').innerHTML = isGroup ? '<span class="material-symbols-rounded">group</span>' : escapeHTML(primaryName[0]).toUpperCase();
            }
            el.dataset.lastActive = c.lastActive;
        }

        list.appendChild(el);
        
        el.onclick = () => openChat(c.ids, primaryName, el, isGroup, c.phoneNumber || c.id, c.lastActive);

        // Update server-side preview directly without extra network reads
        const contentSpan = el.querySelector('.preview-content');
        const timeSpan = el.querySelector('.chat-time');

        if (c.lastActive) {
            const msgDate = new Date(c.lastActive * 1000);
            const today = new Date();
            const isToday = msgDate.toDateString() === today.toDateString();
            
            timeSpan.innerText = isToday 
                ? msgDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) 
                : msgDate.toLocaleDateString([], { month: 'short', day: 'numeric' });
                
            contentSpan.innerHTML = escapeHTML(c.preview || 'Media / Call');
        } else {
            contentSpan.innerHTML = `<span style="font-style: italic; opacity: 0.7;">No messages</span>`;
        }

        if (activeChatId && c.ids.includes(activeChatId)) {
            const baseId = c.phoneNumber || c.id;
            document.getElementById('headerId').innerText = `${baseId} • ${formatLastActive(c.lastActive)}`;
        }
    });

    Array.from(list.children).forEach(child => {
        if (child.id && child.id.startsWith('chat-') && !currentIds.has(child.id)) {
            list.removeChild(child);
        }
    });
}
