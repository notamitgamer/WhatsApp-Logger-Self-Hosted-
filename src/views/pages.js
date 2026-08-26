const { VERSION } = require('../config');

// --- SHARED LAYOUT ---
// One small design system so every page (login, status, logs) shares the
// same responsive behavior instead of hand-rolled inline styles per page.
const BASE_STYLES = `
    :root {
        --accent: #25D366;
        --accent-dark: #1da851;
        --bg: #f0f2f5;
        --card: #ffffff;
        --text: #1c1e21;
        --muted: #65676b;
        --border: #e4e6eb;
        --danger: #ff4444;
        --radius: 12px;
    }
    * { box-sizing: border-box; }
    body {
        margin: 0;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        background: var(--bg);
        color: var(--text);
        min-height: 100vh;
        padding: 16px;
    }
    .page {
        max-width: 480px;
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        min-height: calc(100vh - 32px);
        justify-content: center;
        gap: 16px;
    }
    .card {
        background: var(--card);
        border-radius: var(--radius);
        box-shadow: 0 2px 10px rgba(0,0,0,0.06);
        padding: 28px 24px;
        width: 100%;
    }
    .brand {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        margin-bottom: 4px;
    }
    .brand-mark {
        width: 36px;
        height: 36px;
        flex-shrink: 0;
        display: block;
    }
    .brand-mark img { width: 100%; height: 100%; display: block; }
    .brand-text { text-align: left; }
    .brand-text h1 { font-size: 17px; margin: 0; line-height: 1.2; }
    .brand-text .version { font-size: 12px; color: var(--muted); }
    .subtitle {
        text-align: center;
        color: var(--muted);
        font-size: 13.5px;
        margin: 0 0 20px;
    }
    label { display: block; margin-bottom: 6px; font-size: 13.5px; font-weight: 600; }
    input[type="text"], input[type="password"] {
        width: 100%;
        padding: 11px 12px;
        border: 1px solid var(--border);
        border-radius: 8px;
        font-size: 15px;
        background: #fafafa;
    }
    input[type="text"]:focus, input[type="password"]:focus {
        outline: none;
        border-color: var(--accent);
        background: white;
    }
    .field { margin-bottom: 14px; }
    .checkbox-row {
        display: flex;
        align-items: center;
        font-size: 13.5px;
        color: var(--muted);
        margin-bottom: 18px;
    }
    .checkbox-row input { margin-right: 8px; }
    button, .btn {
        width: 100%;
        padding: 12px;
        background: var(--accent);
        color: white;
        border: none;
        border-radius: 8px;
        font-weight: 600;
        font-size: 15px;
        cursor: pointer;
        text-align: center;
        text-decoration: none;
        display: inline-block;
    }
    button:hover, .btn:hover { background: var(--accent-dark); }
    .btn-secondary {
        background: transparent;
        color: var(--text);
        border: 1px solid var(--border);
    }
    .btn-secondary:hover { background: #f5f5f5; }
    .btn-danger { background: var(--danger); }
    .error-text { color: var(--danger); font-size: 13px; margin-top: 10px; text-align: center; }
    .topbar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        max-width: 480px;
        margin: 0 auto 4px;
        width: 100%;
    }
    .topbar h1 { font-size: 16px; margin: 0; }
    .status-badge {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        font-weight: 600;
        padding: 4px 10px 4px 8px;
        border-radius: 999px;
        white-space: nowrap;
    }
    .status-badge .dot { width: 8px; height: 8px; border-radius: 50%; }
    .status-connected { background: #e6f8ec; color: #1a7a3d; }
    .status-connected .dot { background: #22c55e; }
    .status-waiting { background: #fff6e0; color: #92650a; }
    .status-waiting .dot { background: #f59e0b; }
    .status-init { background: #eee; color: var(--muted); }
    .status-init .dot { background: #9ca3af; }
    .qr-wrap { text-align: center; margin: 6px 0 4px; }
    .qr-wrap img { width: 100%; max-width: 260px; border: 1px solid var(--border); border-radius: 8px; padding: 8px; background: white; }
    .hint { color: var(--muted); font-size: 13px; text-align: center; margin: 8px 0 0; }
    .section-title { font-size: 13px; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); margin: 0 0 10px; font-weight: 700; }
    .link-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 12px 14px;
        border: 1px solid var(--border);
        border-radius: 8px;
        text-decoration: none;
        color: var(--text);
        font-size: 14.5px;
        font-weight: 600;
        margin-bottom: 8px;
    }
    .link-row:hover { background: #fafafa; }
    .link-row .arrow { color: var(--muted); font-weight: 400; }
    .endpoint {
        display: grid;
        grid-template-columns: 52px 1fr;
        gap: 10px;
        padding: 10px 0;
        border-bottom: 1px solid var(--border);
        font-size: 13px;
    }
    .endpoint:last-child { border-bottom: none; }
    .method {
        font-weight: 700;
        font-size: 11px;
        padding: 3px 0;
        border-radius: 5px;
        text-align: center;
        height: fit-content;
        color: white;
    }
    .method-get { background: #3b82f6; }
    .method-post { background: #8b5cf6; }
    .endpoint-path { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; word-break: break-all; }
    .endpoint-desc { color: var(--muted); font-size: 12.5px; margin-top: 2px; }
    .endpoint-auth { display: inline-block; margin-top: 4px; font-size: 11px; color: var(--muted); background: #f2f3f5; padding: 1px 7px; border-radius: 4px; }
    @media (max-width: 400px) {
        .card { padding: 22px 18px; }
        body { padding: 12px; }
    }
`;

function layout({ title, refresh, content, wide }) {
    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <link rel="icon" href="/icon.svg" type="image/svg+xml">
    <title>${title}</title>
    ${refresh ? `<meta http-equiv="refresh" content="${refresh}">` : ''}
    <style>${BASE_STYLES}${wide ? ' .page { max-width: 560px; }' : ''}</style>
</head>
<body>
    ${content}
</body>
</html>`;
}

const brand = `
    <div class="brand">
        <div class="brand-mark"><img src="/icon.svg" alt="WhatsApp Logger"></div>
        <div class="brand-text">
            <h1>WhatsApp Logger</h1>
            <div class="version">v${VERSION}</div>
        </div>
    </div>
`;

function logoutBtn() {
    return `<a href="/logout" class="btn btn-secondary" style="width:auto; padding: 8px 16px; font-size: 13px;">Logout</a>`;
}

// --- LOGIN PAGE ---
function loginPage() {
    const content = `
        <div class="page">
            <div class="card">
                ${brand}
                <p class="subtitle">Sign in to view your archived chats</p>
                <form action="/login" method="POST">
                    <div class="field">
                        <label>Username</label>
                        <input type="text" name="username" autocomplete="username" required>
                    </div>
                    <div class="field">
                        <label>Password</label>
                        <input type="password" name="password" autocomplete="current-password" required>
                    </div>
                    <label class="checkbox-row">
                        <input type="checkbox" name="remember" value="yes">
                        Keep me logged in for 5 mins
                    </label>
                    <button type="submit">Log in</button>
                </form>
            </div>
            <p class="hint">Self-hosted &middot; your data never leaves your own Firebase project</p>
        </div>
    `;
    return layout({ title: 'Login · WhatsApp Logger', content });
}

function loginFailedPage() {
    const content = `
        <div class="page">
            <div class="card" style="text-align: center;">
                ${brand}
                <p class="error-text" style="margin-bottom: 18px;">Invalid username or password.</p>
                <a class="btn" href="/login">Try again</a>
            </div>
        </div>
    `;
    return layout({ title: 'Login failed · WhatsApp Logger', content });
}

// --- API DOCS (shown on the status page) ---
const ENDPOINTS = [
    { method: 'POST', path: '/api/verify', desc: 'Exchange username/password for a bearer token.', auth: 'none' },
    { method: 'GET', path: '/api/chats/stream', desc: 'Server-sent events stream of chat list updates.', auth: 'token' },
    { method: 'GET', path: '/api/messages/stream', desc: 'Server-sent events stream of messages for a chat (?chatId=&since=).', auth: 'token' },
    { method: 'POST', path: '/api/rename', desc: 'Set a custom name for a chat. Body: { id, customName }.', auth: 'token' },
    { method: 'GET', path: '/api/export', desc: 'Dump all cached chats and messages as JSON.', auth: 'token' },
    { method: 'GET', path: '/logs', desc: 'View server logs. Add ?format=json for raw JSON.', auth: 'token/cookie' },
    { method: 'GET', path: '/ping', desc: 'Health check. Always returns 200.', auth: 'none' }
];

function apiDocsSection() {
    const rows = ENDPOINTS.map(e => `
        <div class="endpoint">
            <span class="method method-${e.method.toLowerCase()}">${e.method}</span>
            <div>
                <div class="endpoint-path">${e.path}</div>
                <div class="endpoint-desc">${e.desc}</div>
                <span class="endpoint-auth">auth: ${e.auth}</span>
            </div>
        </div>
    `).join('');

    return `
        <div class="card">
            <p class="section-title">API Endpoints</p>
            ${rows}
        </div>
    `;
}

// --- STATUS PAGE (unified: connected / waiting for QR scan / initializing) ---
function statusBody({ badgeHtml, innerContent }) {
    return `
        <div class="page">
            <div class="topbar">
                <h1>WhatsApp Logger</h1>
                ${logoutBtn()}
            </div>
            <div class="card">
                ${badgeHtml}
                ${innerContent}
            </div>
            <div class="card">
                <p class="section-title">Quick links</p>
                <a class="link-row" href="/logs">
                    <span>View server logs</span>
                    <span class="arrow">&rarr;</span>
                </a>
            </div>
            ${apiDocsSection()}
        </div>
    `;
}

function connectedPage() {
    const badgeHtml = `<span class="status-badge status-connected"><span class="dot"></span>Connected</span>`;
    const innerContent = `
        <p style="margin: 14px 0 0; color: var(--muted); font-size: 14px;">
            Linked to WhatsApp and syncing to Firestore in real time.
        </p>
    `;
    return layout({
        title: 'Status · WhatsApp Logger',
        content: statusBody({ badgeHtml, innerContent })
    });
}

function qrPage(qrImage) {
    const badgeHtml = `<span class="status-badge status-waiting"><span class="dot"></span>Waiting for scan</span>`;
    const innerContent = `
        <div class="qr-wrap">
            <img src="${qrImage}" alt="QR Code">
        </div>
        <p class="hint">Open WhatsApp &rarr; Linked Devices &rarr; Link a Device, then scan this code. Refreshes every 5 seconds.</p>
    `;
    return layout({
        title: 'Scan to link · WhatsApp Logger',
        refresh: 5,
        content: statusBody({ badgeHtml, innerContent })
    });
}

function qrErrorPage() {
    const content = `
        <div class="page">
            <div class="card" style="text-align: center;">
                ${brand}
                <p class="error-text">Couldn't generate the QR code. Refresh to try again.</p>
            </div>
        </div>
    `;
    return layout({ title: 'Error · WhatsApp Logger', content });
}

function initializingPage() {
    const badgeHtml = `<span class="status-badge status-init"><span class="dot"></span>Initializing</span>`;
    const innerContent = `
        <p style="margin: 14px 0 0; color: var(--muted); font-size: 14px;">
            Connecting to WhatsApp and restoring the saved session. This page refreshes automatically.
        </p>
    `;
    return layout({
        title: 'Initializing · WhatsApp Logger',
        refresh: 2,
        content: statusBody({ badgeHtml, innerContent })
    });
}

// --- LOGS PAGE ---
function logsPage(logBuffer) {
    const logLines = logBuffer.map(l => {
        const time = new Date(l.timestamp).toLocaleTimeString();
        const color = l.level === 'error' ? '#ff6b6b' : '#a9dc76';
        return `<div style="color: ${color}">[${time}] [${l.level.toUpperCase()}] ${l.message}</div>`;
    }).join('');

    const content = `
        <div style="max-width: 100%;">
            <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;">
                <h2 style="color: #fff; margin: 0; font-size: 18px;">System Logs</h2>
                <a href="/" style="color: #a9dc76; font-size: 13px; text-decoration: none;">&larr; Back to status</a>
            </div>
            <div class="log-container">
                ${logLines.length > 0 ? logLines : '<div>No logs yet...</div>'}
            </div>
        </div>
    `;

    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <link rel="icon" href="/icon.svg" type="image/svg+xml">
    <meta http-equiv="refresh" content="5">
    <title>System Logs</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; background: #1e1e1e; color: #d4d4d4; padding: 16px; margin: 0; }
        .log-container { background: #000; padding: 14px; border-radius: 8px; overflow-x: auto; max-width: 100%; white-space: pre-wrap; word-break: break-word; font-size: 13px; line-height: 1.6; }
        @media (max-width: 400px) { body { padding: 10px; } .log-container { padding: 10px; font-size: 12px; } }
    </style>
</head>
<body onload="window.scrollTo(0,document.body.scrollHeight);">
    ${content}
</body>
</html>`;
}

module.exports = {
    logsPage,
    loginPage,
    loginFailedPage,
    connectedPage,
    qrPage,
    qrErrorPage,
    initializingPage
};
