let logsInterval = null;

function toggleLogs() {
    const isChecked = document.getElementById('logsToggle').checked;
    const viewer = document.getElementById('logsViewer');
    const token = localStorage.getItem('wp_api_token');
    
    if (isChecked && token) {
        viewer.classList.remove('hidden');
        fetchAndRenderLogs(token);
        logsInterval = setInterval(() => fetchAndRenderLogs(token), 10000);
    } else {
        viewer.classList.add('hidden');
        if (logsInterval) clearInterval(logsInterval);
    }
}

async function fetchAndRenderLogs(token) {
    try {
        const res = await fetch(`${RENDER_BACKEND_URL}/logs?format=json&token=${token}`);
        if (res.ok) {
            const logs = await res.json();
            const viewer = document.getElementById('logsViewer');
            viewer.innerHTML = '';
            
            logs.forEach(l => {
                const time = new Date(l.timestamp).toLocaleTimeString();
                const div = document.createElement('div');
                if (l.level === 'error') div.className = 'error';
                div.innerText = `[${time}] [${l.level.toUpperCase()}] ${l.message}`;
                viewer.appendChild(div);
            });
            
            viewer.scrollTop = viewer.scrollHeight;
        }
    } catch (e) {}
}
