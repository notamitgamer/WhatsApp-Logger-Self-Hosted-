function handleLogout() { localStorage.removeItem('wp_api_token'); localStorage.removeItem('wp_auth_expiry'); localStorage.removeItem('wp_app_pin'); localStorage.removeItem('wp_app_bio'); location.reload(); }

function closeChatMobile() { 
    window.history.back(); 
    activeChatId = null;
    activeChatIds = [];
}

window.addEventListener('popstate', () => { document.getElementById('chatArea').classList.remove('remove'); document.getElementById('chatArea').classList.remove('active'); });

async function downloadChat() {
    if(!activeChatId || !activeChatIds || activeChatIds.length === 0) return;
    const token = localStorage.getItem('wp_api_token');
    try {
        const res = await fetch(`${RENDER_BACKEND_URL}/api/export?token=${token}`, { headers: { 'Authorization': `Bearer ${token}` } });
        
        if (res.ok) {
            const exportData = await res.json();
            
            let allMessages = [];
            activeChatIds.forEach(id => {
                if (exportData.messages[id]) {
                    allMessages = allMessages.concat(exportData.messages[id]);
                }
            });
            
            if (allMessages.length === 0) return;
            
            allMessages.sort((a,b) => a.timestamp - b.timestamp);
            const text = allMessages.map(m => `[${new Date(m.timestamp*1000).toLocaleString()}] ${m.senderName}: ${m.text}`).join('\n');
            
            const blob = new Blob([text], {type:'text/plain'});
            const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'chat.txt'; a.click();
        }
    } catch (err) {
        console.error("Export download error:", err);
    }
}

function showRenameModal() { if(!activeChatId) return; document.getElementById('renameModal').classList.add('active'); document.getElementById('renameInput').value = document.getElementById('headerName').innerText; document.getElementById('chatMenu').classList.remove('show'); }

async function saveRename() { 
    const name = document.getElementById('renameInput').value; 
    const token = localStorage.getItem('wp_api_token');
    if(name && token) { 
        await fetch(`${RENDER_BACKEND_URL}/api/rename`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
            body: JSON.stringify({ id: activeChatId, customName: name })
        });
        
        document.getElementById('headerName').innerText = name; 
        closeModal('renameModal'); 
    } 
}

function showLogoutModal() { document.getElementById('logoutModal').classList.add('active'); }

document.getElementById('searchInput').addEventListener('input', (e) => {
    const val = e.target.value.toLowerCase();
    document.querySelectorAll('.contact-item').forEach(el => { el.style.display = el.innerText.toLowerCase().includes(val) ? 'flex' : 'none'; });
});
