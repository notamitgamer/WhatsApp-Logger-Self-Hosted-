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

// Which backend version is this app talking to?
async function loadBackendVersion() {
    const el = document.getElementById('backendVersion');
    const head = document.getElementById('headerVersion');
    const token = localStorage.getItem('wp_api_token');
    // Show the last known backend version straight away (works offline too)
    const known = localStorage.getItem('wp_backend_version');
    if (head && known) head.textContent = known;
    if (!el) return;
    if (!token) { el.textContent = 'Not signed in'; return; }
    el.textContent = 'Checking...';
    try {
        const res = await fetch(`${RENDER_BACKEND_URL}/api/health`, { headers: { 'Authorization': `Bearer ${token}` } });
        const d = res.ok ? await res.json() : null;
        el.textContent = d && d.version ? `v${d.version}` : `Unavailable (HTTP ${res.status})`;
        if (d && d.version) {
            localStorage.setItem('wp_backend_version', `v${d.version}`);
            if (head) head.textContent = `v${d.version}`;
        }
    } catch (e) { el.textContent = 'Unreachable'; if (head && !known) head.textContent = 'offline'; }
}
window.addEventListener('load', () => setTimeout(loadBackendVersion, 300));
