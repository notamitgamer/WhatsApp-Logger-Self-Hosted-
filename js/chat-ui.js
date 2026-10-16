async function openChat(ids, name, el, isGroup, displayId, lastActive) {
    activeChatId = ids[0];
    activeChatIds = ids;
    isCurrentChatGroup = isGroup;
    
    document.getElementById('headerName').innerText = name;
    document.getElementById('chatHeaderContent').style.opacity = '1';
    
    const timeStr = lastActive ? formatLastActive(lastActive) : "Online";
    document.getElementById('headerId').innerText = `${displayId} • ${timeStr}`;
    document.getElementById('headerAvatar').innerHTML = isGroup ? `<span class="material-symbols-rounded">group</span>` : escapeHTML(name[0]).toUpperCase();
    
    if (window.matchMedia('(max-width: 768px)').matches) {
        window.history.pushState({chatOpen: true}, "");
        document.getElementById('sidebar').classList.add('inactive');
        document.getElementById('chatArea').classList.add('active');
    }
    
    const container = document.getElementById('messagesContainer');
    container.innerHTML = `<div class="skel-msg-group"><div class="skeleton skel-bubble skel-left"></div><div class="skeleton skel-bubble skel-right" style="width:60%"></div><div class="skeleton skel-bubble skel-left" style="width:30%"></div></div>`;
    
    await refreshActiveChatUI(true);
}

async function refreshActiveChatUI(isInitialOpen = false) {
    if (!activeChatIds || activeChatIds.length === 0) return;
    
    let cachedMsgs = [];
    for (const id of activeChatIds) {
        const msgs = await getCachedMessages(id);
        cachedMsgs = cachedMsgs.concat(msgs);
    }
    
    const msgsMap = new Map();
    cachedMsgs.forEach(m => msgsMap.set(m.id, m));
    
    const c = document.getElementById('messagesContainer');
    const wasAtBottom = (c.scrollHeight - c.scrollTop - c.clientHeight) <= 100;
    
    if (cachedMsgs.length > 0) {
        renderMsgs(msgsMap);
        
        if (isInitialOpen || wasAtBottom) {
            if (isInitialOpen) {
                scrollToBottom('auto');
            } else {
                setTimeout(() => scrollToBottom('smooth'), 50);
            }
        } else if (!isInitialOpen && !wasAtBottom) {
            document.getElementById('newMsgBadge').classList.remove('hidden');
            document.getElementById('scrollFab').classList.add('visible');
        }
    } else {
        c.innerHTML = '<div class="empty-state"><p>No messages recorded</p></div>';
    }
}

function renderMsgs(map) {
    const container = document.getElementById('messagesContainer');
    container.innerHTML = '';
    currentMessages = Array.from(map.values()).sort((a,b) => a.timestamp - b.timestamp);
    
    if(currentMessages.length === 0) {
        container.innerHTML = '<div class="empty-state"><p>No messages recorded</p></div>';
        return;
    }

    let lastDateStr = ""; let currentGroup = null;

    currentMessages.forEach((m, index) => {
        const date = new Date(m.timestamp * 1000);
        const dateStr = date.toDateString();

        if(dateStr !== lastDateStr) {
            if(currentGroup) container.appendChild(currentGroup);
            currentGroup = document.createElement('div');
            currentGroup.className = 'chat-date-group';
            const div = document.createElement('div'); div.className = 'date-divider'; div.innerText = formatDividerDate(m.timestamp);
            currentGroup.appendChild(div);
            lastDateStr = dateStr;
        }

        const div = document.createElement('div');
        div.className = `msg-bubble ${m.fromMe ? 'msg-out' : 'msg-in'}`;
        
        const nextMsg = currentMessages[index + 1]; const prevMsg = currentMessages[index - 1];
        const isSamePrev = (prevMsg && prevMsg.fromMe === m.fromMe && (!isCurrentChatGroup || prevMsg.senderName === m.senderName) && new Date(prevMsg.timestamp*1000).toDateString() === dateStr);
        const isSameNext = (nextMsg && nextMsg.fromMe === m.fromMe && (!isCurrentChatGroup || nextMsg.senderName === m.senderName) && new Date(nextMsg.timestamp*1000).toDateString() === dateStr);

        if (isSamePrev && isSameNext) div.classList.add('group-mid'); else if (isSamePrev) div.classList.add('group-bot'); else if (isSameNext) div.classList.add('group-top');
        
        const showSenderName = !m.fromMe && isCurrentChatGroup && m.senderName && m.senderName !== "Unknown" && !isSamePrev; 
        div.id = `msg-${m.timestamp}`; div.dataset.date = dateStr;

        const safeText = escapeHTML(m.text || '');
        const urlRegex = /(https?:\/\/[^\s]+)/g;
        const formattedText = safeText ? safeText.replace(urlRegex, url => `<a href="${url}" target="_blank" rel="noopener noreferrer" style="color: inherit; text-decoration: underline; font-weight: 600;">${url}</a>`) : '';

        div.innerHTML = `${showSenderName ? `<div class="msg-sender">${escapeHTML(m.senderName)}</div>` : ''}<div class="msg-text">${formattedText || 'Media'}</div><div class="msg-meta">${date.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}</div>`;
        if (currentGroup) currentGroup.appendChild(div);
    });

    if(currentGroup) container.appendChild(currentGroup);
}

function scrollToBottom(behavior = 'auto') {
    const c = document.getElementById('messagesContainer');
    c.scrollTo({ top: c.scrollHeight, behavior: behavior });
    document.getElementById('newMsgBadge').classList.add('hidden');
}
