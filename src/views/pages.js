const { VERSION } = require('../config');


// --- SHARED LAYOUT ---
// One small design system so every page (login, status, logs) shares the
// same responsive behavior instead of hand-rolled inline styles per page.
const BASE_STYLES = `
    /* Material 3 tonal palette generated from the app icon green (#006C4C).
       Follows the system light/dark setting; no animations anywhere. */
    :root {
        color-scheme: light dark;
        --primary: #006C4C;
        --on-primary: #FFFFFF;
        --primary-container: #89F8C7;
        --on-primary-container: #002114;
        --surface: #F5FBF5;
        --surface-low: #EFF5EF;
        --surface-container: #E9EFE9;
        --surface-high: #E3EAE4;
        --surface-highest: #DEE4DE;
        --on-surface: #171D1A;
        --on-surface-variant: #404944;
        --outline: #707973;
        --outline-variant: #BFC9C2;
        --error: #BA1A1A;
        --error-container: #FFDAD6;
        --on-error-container: #410002;
        --ok-container: #C8F2DC;
        --on-ok-container: #002114;
        --warn-container: #FFE2A8;
        --on-warn-container: #3B2900;
        --code: #E3EAE4;
    }
    @media (prefers-color-scheme: dark) {
        :root {
            --primary: #6CDBAC;
            --on-primary: #003826;
            --primary-container: #005138;
            --on-primary-container: #89F8C7;
            --surface: #0F1512;
            --surface-low: #161D1A;
            --surface-container: #1B211E;
            --surface-high: #252B28;
            --surface-highest: #303633;
            --on-surface: #DFE4DE;
            --on-surface-variant: #BFC9C2;
            --outline: #89938D;
            --outline-variant: #404944;
            --error: #FFB4AB;
            --error-container: #93000A;
            --on-error-container: #FFDAD6;
            --ok-container: #005138;
            --on-ok-container: #89F8C7;
            --warn-container: #5A4300;
            --on-warn-container: #FFE2A8;
            --code: #252B28;
        }
    }
    * { box-sizing: border-box; }
    html { background: var(--surface); }
    body {
        margin: 0;
        font-family: system-ui, -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        background:
            radial-gradient(900px 480px at 50% -120px, color-mix(in srgb, var(--primary) 16%, transparent), transparent 70%),
            var(--surface);
        background-repeat: no-repeat;
        color: var(--on-surface);
        min-height: 100vh;
        padding: 16px;
        -webkit-font-smoothing: antialiased;
    }
    .page {
        max-width: 440px;
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        min-height: calc(100vh - 32px);
        justify-content: center;
        gap: 14px;
    }
    .card {
        background: var(--surface-container);
        border-radius: 28px;
        padding: 28px 24px;
        width: 100%;
    }
    .card-login { padding: 32px 24px 28px; }
    .brand {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 14px;
        margin-bottom: 6px;
    }
    .brand-mark {
        width: 72px;
        height: 72px;
        border-radius: 24px;
        overflow: hidden;
        display: block;
    }
    .brand-mark img { width: 100%; height: 100%; display: block; }
    .brand-text h1 { font-size: 26px; font-weight: 500; margin: 0; line-height: 1.2; letter-spacing: -0.2px; }
    .brand-text .version {
        display: inline-block;
        margin-top: 8px;
        font-size: 12px;
        font-weight: 500;
        padding: 3px 10px;
        border-radius: 999px;
        background: var(--primary-container);
        color: var(--on-primary-container);
    }
    .subtitle {
        text-align: center;
        color: var(--on-surface-variant);
        font-size: 15px;
        margin: 0 0 24px;
    }
    /* Filled text field (M3). Label is static above the value. */
    .field {
        background: var(--surface-highest);
        border-radius: 14px 14px 0 0;
        border-bottom: 1px solid var(--outline);
        padding: 9px 16px 8px;
        margin-bottom: 14px;
        display: block;
        position: relative;
    }
    .field:focus-within { border-bottom: 2px solid var(--primary); padding-bottom: 7px; }
    .field label {
        display: block;
        font-size: 12px;
        font-weight: 500;
        color: var(--on-surface-variant);
        margin: 0 0 2px;
    }
    .field:focus-within label { color: var(--primary); }
    .field input {
        width: 100%;
        border: none;
        outline: none;
        background: transparent;
        color: var(--on-surface);
        font: inherit;
        font-size: 17px;
        padding: 2px 0;
    }
    .field.has-toggle input { padding-right: 44px; }
    .toggle-pw {
        position: absolute;
        right: 6px;
        bottom: 6px;
        width: auto;
        padding: 6px 10px;
        background: transparent;
        color: var(--primary);
        border-radius: 999px;
        font-size: 13px;
        font-weight: 500;
    }
    .toggle-pw:hover { background: color-mix(in srgb, var(--primary) 12%, transparent); }
    .checkbox-row {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 14px;
        color: var(--on-surface-variant);
        margin: 4px 0 22px;
        cursor: pointer;
    }
    .checkbox-row input { width: 20px; height: 20px; accent-color: var(--primary); margin: 0; }
    button, .btn {
        width: 100%;
        height: 52px;
        padding: 0 24px;
        background: var(--primary);
        color: var(--on-primary);
        border: none;
        border-radius: 999px;
        font: inherit;
        font-weight: 500;
        font-size: 16px;
        letter-spacing: .1px;
        cursor: pointer;
        text-align: center;
        text-decoration: none;
        display: inline-flex;
        align-items: center;
        justify-content: center;
    }
    button:hover, .btn:hover { filter: brightness(1.08); }
    .btn-secondary {
        background: transparent;
        color: var(--primary);
        border: 1px solid var(--outline);
    }
    .btn-secondary:hover { background: color-mix(in srgb, var(--primary) 10%, transparent); filter: none; }
    .btn-small { width: auto; height: 36px; padding: 0 16px; font-size: 14px; }
    .error-banner {
        display: flex;
        align-items: center;
        gap: 10px;
        background: var(--error-container);
        color: var(--on-error-container);
        border-radius: 16px;
        padding: 12px 16px;
        font-size: 14px;
        margin-bottom: 18px;
    }
    .error-text { color: var(--error); font-size: 14px; margin: 10px 0; text-align: center; }
    .topbar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        max-width: 440px;
        margin: 0 auto 2px;
        width: 100%;
        padding: 0 4px;
    }
    .topbar h1 { font-size: 22px; font-weight: 500; margin: 0; }
    .status-badge {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        font-size: 14px;
        font-weight: 500;
        padding: 6px 14px 6px 12px;
        border-radius: 999px;
        white-space: nowrap;
    }
    .status-badge .dot { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
    .status-connected { background: var(--ok-container); color: var(--on-ok-container); }
    .status-waiting { background: var(--warn-container); color: var(--on-warn-container); }
    .status-init { background: var(--surface-highest); color: var(--on-surface-variant); }
    .qr-wrap { text-align: center; margin: 14px 0 4px; }
    .qr-wrap img { width: 100%; max-width: 260px; border-radius: 20px; padding: 12px; background: #fff; }
    .hint { color: var(--on-surface-variant); font-size: 13px; text-align: center; margin: 10px 0 0; line-height: 1.5; }
    .hint-chip {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        color: var(--on-surface-variant);
        font-size: 13px;
        text-align: center;
        padding: 0 12px;
    }
    .section-title { font-size: 13px; letter-spacing: .06em; text-transform: uppercase; color: var(--primary); margin: 0 0 12px; font-weight: 600; }
    .link-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 14px 16px;
        background: var(--surface-high);
        border-radius: 16px;
        text-decoration: none;
        color: var(--on-surface);
        font-size: 15px;
        font-weight: 500;
    }
    .link-row:hover { background: var(--surface-highest); }
    .link-row .arrow { color: var(--on-surface-variant); }
    .endpoint {
        display: grid;
        grid-template-columns: 54px 1fr;
        gap: 12px;
        padding: 12px 0;
        border-bottom: 1px solid var(--outline-variant);
        font-size: 13px;
    }
    .endpoint:last-child { border-bottom: none; padding-bottom: 0; }
    .method {
        font-weight: 600;
        font-size: 11px;
        padding: 4px 0;
        border-radius: 8px;
        text-align: center;
        height: fit-content;
        background: var(--primary-container);
        color: var(--on-primary-container);
    }
    .method-post { background: var(--warn-container); color: var(--on-warn-container); }
    .endpoint-path { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; word-break: break-all; }
    .endpoint-desc { color: var(--on-surface-variant); font-size: 12.5px; margin-top: 3px; }
    .endpoint-auth { display: inline-block; margin-top: 6px; font-size: 11px; color: var(--on-surface-variant); background: var(--surface-highest); padding: 2px 8px; border-radius: 8px; }
    /* --- formal, flat look shared with the login page --- */
    body { background: var(--surface); }
    .card { border-radius: 6px; border: 1px solid var(--outline-variant); padding: 22px; }
    .nav {
        display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
        margin: 0; padding: 10px 14px;
        background: var(--surface-container); border: 1px solid var(--outline-variant); border-radius: 6px;
    }
    .nav .nav-brand { display: flex; align-items: center; gap: 10px; font-weight: 600; margin-right: auto; font-size: 16px; }
    .nav .nav-brand img { width: 28px; height: 28px; border-radius: 8px; display: block; }
    .nav .nav-brand small { font-weight: 400; color: var(--on-surface-variant); font-size: 12px; }
    .nav a { color: var(--on-surface-variant); text-decoration: none; font-size: 14px; padding: 7px 12px; border-radius: 4px; }
    .nav a:hover { background: var(--surface-highest); }
    .nav a.active { color: var(--on-surface); background: var(--surface-highest); font-weight: 600; }
    .shell { max-width: 900px; margin: 0 auto; display: flex; flex-direction: column; gap: 14px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; }
    .stat { background: var(--surface-high); border-radius: 4px; padding: 14px 16px; }
    .stat .k { font-size: 12px; color: var(--on-surface-variant); margin-bottom: 4px; }
    .stat .v { font-size: 22px; font-weight: 500; }
    .stat .v.small { font-size: 15px; }
    .pill { display: inline-block; font-size: 12px; font-weight: 600; padding: 3px 10px; border-radius: 4px; }
    .pill-ok { background: var(--ok-container); color: var(--on-ok-container); }
    .pill-warn { background: var(--warn-container); color: var(--on-warn-container); }
    .pill-bad { background: var(--error-container); color: var(--on-error-container); }
    table.kv { width: 100%; border-collapse: collapse; font-size: 14px; }
    table.kv td { padding: 11px 4px; border-bottom: 1px solid var(--outline-variant); vertical-align: top; }
    table.kv tr:last-child td { border-bottom: none; }
    table.kv td:first-child { color: var(--on-surface-variant); width: 46%; }
    .link-row { border-radius: 4px; }
    .btn-small { border-radius: 4px; }
    .qr-wrap img { border-radius: 6px; }
    @media (max-width: 400px) {
        .card { padding: 18px 14px; }
        body { padding: 12px; }
    }
`;

function layout({ title, refresh, content, wide }) {
    return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, interactive-widget=resizes-content">
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
            <span class="version">v${VERSION}</span>
        </div>
    </div>
`;

function logoutBtn() {
    return `<a href="/logout" class="btn btn-secondary btn-small">Log out</a>`;
}

// --- LOGIN PAGE ---
function loginPage(errorMessage) {
    const content = `
        <style>
            /* Plain, formal split login: form on the left, accent panel with a lock on the right */
            body { background: var(--surface); display: flex; align-items: center; justify-content: center; min-height: 100vh; min-height: 100dvh; }
            .login-shell {
                width: 100%; max-width: 780px; min-height: 420px; display: flex;
                background: var(--surface-container); border-radius: 6px; overflow: hidden;
                border: 1px solid var(--outline-variant);
            }
            .login-form { flex: 1.25; min-width: 0; padding: 44px 44px 36px; display: flex; flex-direction: column; justify-content: center; }
            .login-form h1 { margin: 0 0 6px; text-align: center; font-size: 30px; font-weight: 300; letter-spacing: 3px; }
            .login-form .login-sub { margin: 0 0 26px; text-align: center; font-size: 13px; color: var(--on-surface-variant); }
            .login-panel {
                flex: 1; background: var(--panel); display: flex; flex-direction: column;
                align-items: center; justify-content: center; gap: 18px; color: var(--on-panel); padding: 24px;
            }
            .login-panel .lock { opacity: .35; }
            .login-panel .panel-name { font-size: 15px; letter-spacing: 1px; opacity: .85; text-align: center; }
            :root { --panel: #00382A; --on-panel: #FFFFFF; --btn: #00382A; --on-btn: #FFFFFF; }
            @media (prefers-color-scheme: dark) { :root { --panel: #0B2A21; --btn: #6CDBAC; --on-btn: #003826; } }
            .input-row { display: flex; align-items: stretch; margin-bottom: 14px; background: var(--surface-highest); border-radius: 3px; border: 1px solid transparent; }
            .input-row:focus-within { border-color: var(--primary); }
            .input-row .ico { width: 44px; display: flex; align-items: center; justify-content: center; color: var(--on-surface-variant); }
            .input-row input { flex: 1; width: 0; min-width: 0; border: none; outline: none; background: transparent; color: var(--on-surface); font: inherit; font-size: 15px; padding: 13px 12px 13px 0; }
            .input-row .toggle-pw { position: static; align-self: center; margin-right: 6px; height: auto; border-radius: 3px; }
            .row-between { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 2px 0 18px; font-size: 13px; color: var(--on-surface-variant); }
            .row-between label { display: flex; align-items: center; gap: 8px; cursor: pointer; }
            .row-between input { width: 16px; height: 16px; accent-color: var(--primary); margin: 0; }
            .login-btn { height: 46px; border-radius: 3px; background: var(--btn); color: var(--on-btn); text-transform: uppercase; letter-spacing: 2px; font-size: 14px; font-weight: 600; }
            .login-btn:hover { filter: brightness(1.25); }
            .forgot { font-size: 12px; color: var(--on-surface-variant); text-align: center; margin: 14px 0 0; }
            .login-err { background: var(--error-container); color: var(--on-error-container); border-radius: 3px; padding: 10px 12px; font-size: 13px; margin-bottom: 14px; }
            .login-foot { margin: 18px 0 0; text-align: center; font-size: 12px; color: var(--on-surface-variant); }
            @media (max-width: 640px) {
                /* top-aligned so the on-screen keyboard never hides the fields */
                body { align-items: flex-start; padding-top: 28px; }
                .login-panel { display: none; }
                .login-form { padding: 32px 22px 26px; }
                .login-shell { border-radius: 4px; }
            }
        </style>
        <div class="login-shell">
            <div class="login-form">
                <h1>USER LOGIN</h1>
                <p class="login-sub">WhatsApp Logger &middot; v${VERSION}</p>
                ${errorMessage ? `<div class="login-err" role="alert">${errorMessage}</div>` : ''}
                <form action="/login" method="POST">
                    <div class="input-row">
                        <span class="ico"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg></span>
                        <input type="text" name="username" placeholder="Username" aria-label="Username" autocomplete="username" autocapitalize="none" spellcheck="false" required>
                    </div>
                    <div class="input-row">
                        <span class="ico"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM12 17c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM9 8V6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9z"/></svg></span>
                        <input id="password" type="password" name="password" placeholder="Password" aria-label="Password" autocomplete="current-password" required>
                        <button type="button" class="toggle-pw" onclick="var i=document.getElementById('password');var s=i.type==='password';i.type=s?'text':'password';this.textContent=s?'Hide':'Show';">Show</button>
                    </div>
                    <div class="row-between">
                        <label><input type="checkbox" name="remember" value="yes"> Remember me (5 min)</label>
                    </div>
                    <button type="submit" class="login-btn">Login</button>
                    <p class="forgot">Forgot password? Change <code>AUTH_PASS</code> in your server settings.</p>
                </form>
                <p class="login-foot">Self-hosted. Your data stays in your own Firebase project.</p>
            </div>
            <div class="login-panel">
                <svg class="lock" width="150" height="150" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM9 8V6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9z"/></svg>
                <div class="panel-name">Private archive</div>
            </div>
        </div>
    `;
    const focusFix = `<script>
        // Keep the focused field visible when the keyboard opens
        document.querySelectorAll('.input-row input').forEach(function (el) {
            el.addEventListener('focus', function () {
                setTimeout(function () { el.scrollIntoView({ block: 'center' }); }, 300);
            });
        });
    </script>`;
    return layout({ title: 'Login · WhatsApp Logger', content: content + focusFix });
}

function loginFailedPage() {
    return loginPage('Wrong username or password.');
}

// --- API DOCS (shown on the status page) ---
const ENDPOINTS = [
    { method: 'POST', path: '/api/verify', desc: 'Exchange username/password for a bearer token.', auth: 'none' },
    { method: 'GET', path: '/api/chats/stream', desc: 'Server-sent events stream of chat list updates.', auth: 'token' },
    { method: 'GET', path: '/api/messages/stream', desc: 'Server-sent events stream of messages for a chat (?chatId=&since=).', auth: 'token' },
    { method: 'POST', path: '/api/rename', desc: 'Set a custom name for a chat. Body: { id, customName }.', auth: 'token' },
    { method: 'GET', path: '/api/export', desc: 'Dump all cached chats and messages as JSON.', auth: 'token' },
    { method: 'GET', path: '/logs', desc: 'View server logs. Add ?format=json for raw JSON.', auth: 'token/cookie' },
    { method: 'GET', path: '/api/health', desc: 'Private health report as JSON (cache, queue, snapshot, quota).', auth: 'token' },
    { method: 'GET', path: '/ping', desc: 'Liveness check. Always returns 200.', auth: 'none' }
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

// --- NAV + STATUS PAGE ---
function nav(active) {
    const item = (href, name, key) => `<a href="${href}"${active === key ? ' class="active"' : ''}>${name}</a>`;
    return `
        <div class="nav">
            <div class="nav-brand"><img src="/icon.svg" alt=""><span>WhatsApp Logger <small>v${VERSION}</small></span></div>
            ${item('/', 'Status', 'status')}
            ${item('/health', 'Health', 'health')}
            ${item('/logs', 'Logs', 'logs')}
            <a href="/logout">Log out</a>
        </div>`;
}

function statusBody({ badgeHtml, innerContent }) {
    return `
        <div class="shell">
            ${nav('status')}
            <div class="card">
                ${badgeHtml}
                ${innerContent}
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
        ${relogForm}
        ${legacyCleanupBox}
    `;
    return layout({
        title: 'Status · WhatsApp Logger',
        content: statusBody({ badgeHtml, innerContent })
    });
}

const relogForm = `
    <form method="POST" action="/relog" style="margin-top: 18px;"
          onsubmit="return confirm('Unlink this device and delete the saved session? You will need to scan a new QR code.');">
        <button type="submit" class="btn-secondary btn-small">Relink device</button>
    </form>
`;

const legacyCleanupBox = `
    <div style="margin-top:22px; padding-top:18px; border-top:1px solid var(--outline-variant, #8883);">
        <p style="margin:0 0 6px; font-weight:600; font-size:14px;">Migrating from an older version?</p>
        <p style="margin:0 0 12px; color:var(--muted); font-size:13px; line-height:1.55;">
            Versions before 4.4.5 kept your WhatsApp session as thousands of tiny documents in the
            <code>whatsapp_auth</code> collection. The session now lives in one compressed document
            (<code>auth_store/session</code>), so the old collection is never read again. It only takes up space,
            and the old key churn was what ate most of the Firestore quota. Deleting it is safe, but the
            cleanup itself uses about one read and one write per old document, so run it when your quota is healthy.
            It only touches <code>whatsapp_auth</code>.
        </p>
        <form id="legacy-form" method="POST" action="/cleanup-legacy"
              onsubmit="return confirm('Delete the old whatsapp_auth collection from Firestore? Your current session is not affected.');">
            <button type="submit" class="btn-secondary btn-small">Delete old whatsapp_auth data</button>
        </form>
    </div>
`;

function legacyDonePage(n, err) {
    const badgeHtml = err
        ? `<span class="status-badge status-init"><span class="dot"></span>Cleanup failed</span>`
        : `<span class="status-badge status-connected"><span class="dot"></span>Cleanup done</span>`;
    const innerContent = `<p style="margin:14px 0 0; color:var(--muted); font-size:14px;">${
        err ? 'Stopped after ' + n + ' documents: ' + String(err).replace(/[<>&]/g, '') + '. You can run it again later.'
            : 'Deleted ' + n + ' old documents from <code>whatsapp_auth</code>.'
    }</p><p style="margin-top:14px;"><a href="/">Back to status</a></p>`;
    return layout({ title: 'Cleanup · WhatsApp Logger', content: statusBody({ badgeHtml, innerContent }) });
}

function qrPage(qrImage) {
    const badgeHtml = `<span class="status-badge status-waiting"><span class="dot"></span>Waiting to link</span>`;
    const innerContent = `
        <div class="tabs" role="tablist">
            <button type="button" class="tab active" id="tab-qr" role="tab">QR code</button>
            <button type="button" class="tab" id="tab-phone" role="tab">Phone number</button>
        </div>

        <div id="pane-qr">
            <div class="qr-wrap"><img id="qr-img" src="${qrImage}" alt="QR Code"></div>
            <ol class="steps">
                <li>Open WhatsApp on your phone</li>
                <li>Go to <b>Settings &rarr; Linked devices &rarr; Link a device</b></li>
                <li>Point the camera at this code</li>
            </ol>
            <p class="hint">The code refreshes by itself. After scanning, give it up to 20 seconds to finish.</p>
        </div>

        <div id="pane-phone" hidden>
            <ol class="steps">
                <li>Enter your WhatsApp number with country code, digits only</li>
                <li>On your phone: <b>Linked devices &rarr; Link a device &rarr; Link with phone number instead</b></li>
                <li>Type the 8-character code shown here</li>
            </ol>
            <form id="pair-form" style="display:flex; gap:8px; margin-top:14px;">
                <input id="pair-phone" type="tel" inputmode="numeric" placeholder="919876543210" autocomplete="tel" class="pair-input" aria-label="Phone number with country code">
                <button type="submit" id="pair-btn" class="btn-small">Get code</button>
            </form>
            <div id="pair-code" class="pair-code" hidden></div>
            <p class="hint" id="pair-msg"></p>
        </div>

        <form method="POST" action="/relog" style="display:flex; justify-content:center; margin-top:16px;"
              onsubmit="return confirm('Delete the saved session and start over?');">
            <button type="submit" class="btn-secondary btn-small">Start over</button>
        </form>
        <style>
            .tabs { display:flex; gap:4px; padding:4px; background:var(--surface-highest); border-radius:12px; margin-top:16px; }
            .tab { flex:1; height:36px; border:0; border-radius:9px; background:transparent; color:var(--on-surface-variant); font:inherit; font-size:14px; font-weight:500; cursor:pointer; }
            .tab.active { background:var(--surface); color:var(--on-surface); box-shadow:0 1px 2px rgba(0,0,0,.2); }
            .steps { margin:14px 0 0; padding-left:20px; color:var(--on-surface-variant); font-size:14px; line-height:1.7; }
            .steps b { color:var(--on-surface); font-weight:500; }
            .pair-input { flex:1; min-width:0; height:36px; padding:0 12px; border-radius:8px; border:1px solid var(--outline, #8884); background:var(--surface-highest); color:var(--on-surface); font:inherit; font-size:15px; }
            .pair-code { margin-top:16px; text-align:center; font-size:32px; letter-spacing:6px; font-weight:600; font-family:ui-monospace,Menlo,Consolas,monospace; color:var(--on-surface); user-select:all; }
        </style>
        <script>
        (function () {
            var $ = function (id) { return document.getElementById(id); };
            var panes = { qr: $('pane-qr'), phone: $('pane-phone') };
            function show(which) {
                panes.qr.hidden = which !== 'qr'; panes.phone.hidden = which !== 'phone';
                $('tab-qr').classList.toggle('active', which === 'qr');
                $('tab-phone').classList.toggle('active', which === 'phone');
            }
            $('tab-qr').onclick = function () { show('qr'); };
            $('tab-phone').onclick = function () { show('phone'); };

            // Keep the QR fresh and jump to the status page as soon as the link completes
            setInterval(function () {
                if (document.hidden) return;
                fetch('/qr.json', { credentials: 'same-origin', cache: 'no-store' })
                    .then(function (r) { return r.json(); })
                    .then(function (d) {
                        if (d.connected) location.href = '/';
                        else if (d.image) $('qr-img').src = d.image;
                    }).catch(function () {});
            }, 3000);

            $('pair-form').addEventListener('submit', function (e) {
                e.preventDefault();
                var btn = $('pair-btn'), msg = $('pair-msg'), box = $('pair-code');
                btn.disabled = true; msg.textContent = ''; box.hidden = true;
                fetch('/pair', {
                    method: 'POST', credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ phone: $('pair-phone').value })
                }).then(function (r) { return r.json(); }).then(function (d) {
                    if (d.code) { box.textContent = d.code; box.hidden = false; msg.textContent = 'Enter this code on your phone within a minute or so.'; }
                    else msg.textContent = d.error || 'Could not get a code.';
                }).catch(function () { msg.textContent = 'Request failed. Check your connection.'; })
                  .then(function () { btn.disabled = false; });
            });
        })();
        </script>
    `;
    return layout({
        title: 'Link device · WhatsApp Logger',
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

// --- HEALTH PAGE ---
function fmtAgo(ms) {
    if (!ms) return 'never';
    const m = Math.floor((Date.now() - ms) / 60000);
    if (m < 1) return 'just now';
    if (m < 60) return `${m} min ago`;
    if (m < 1440) return `${Math.floor(m / 60)} h ago`;
    return `${Math.floor(m / 1440)} d ago`;
}
function fmtUptime(sec) {
    const d = Math.floor(sec / 86400), h = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60);
    return d ? `${d}d ${h}h` : h ? `${h}h ${m}m` : `${m}m`;
}

function healthPage({ stats, connected }) {
    const pill = (ok, okText, badText, warn) =>
        `<span class="pill ${ok ? 'pill-ok' : warn ? 'pill-warn' : 'pill-bad'}">${ok ? okText : badText}</span>`;

    const quota = stats.quotaPaused
        ? pill(false, '', `Paused until ${new Date(stats.quotaPausedUntil).toLocaleTimeString()}`, true)
        : pill(true, 'OK', '');

    const content = `
        <div class="shell">
            ${nav('health')}
            <div class="grid">
                <div class="stat"><div class="k">WhatsApp</div><div class="v small">${pill(connected, 'Connected', 'Not connected')}</div></div>
                <div class="stat"><div class="k">Cache</div><div class="v small">${pill(stats.cacheReady, 'Ready', 'Loading', true)}</div></div>
                <div class="stat"><div class="k">Firestore writes</div><div class="v small">${quota}</div></div>
                <div class="stat"><div class="k">Uptime</div><div class="v">${fmtUptime(Math.floor(process.uptime()))}</div></div>
                <div class="stat"><div class="k">Memory (of ~512 MB)</div><div class="v">${stats.memoryMB} MB</div></div>
            </div>
            <div class="card">
                <p class="section-title">Archive</p>
                <table class="kv">
                    <tr><td>Chats</td><td>${stats.chats.toLocaleString()}</td></tr>
                    <tr><td>Messages</td><td>${stats.messages.toLocaleString()}</td></tr>
                    <tr><td>Writes waiting for Firestore</td><td>${stats.queuedWrites}${stats.queuedWrites ? ' (will retry automatically)' : ''}</td></tr>
                    <tr><td>Last snapshot saved</td><td>${fmtAgo(stats.lastSnapshotAt)}${stats.snapshotDirty ? ' &middot; changes pending' : ''}</td></tr>
                    <tr><td>Snapshot size</td><td>${stats.snapshotParts} document${stats.snapshotParts === 1 ? '' : 's'}</td></tr>
                    <tr><td>Version</td><td>v${VERSION}</td></tr>
                </table>
            </div>
            <p class="hint">This page refreshes every 15 seconds. JSON version: <code>/api/health</code></p>
        </div>`;
    return layout({ title: 'Health · WhatsApp Logger', refresh: 15, content });
}

// --- LOGS PAGE ---
const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function logsPage(logBuffer) {
    const logLines = logBuffer.map(l => {
        const time = new Date(l.timestamp).toLocaleTimeString();
        return `<div class="log-line ${l.level === 'error' ? 'log-error' : 'log-info'}"><span class="log-time">${time}</span> <span class="log-level">${l.level.toUpperCase()}</span> ${esc(l.message)}</div>`;
    }).join('');

    const content = `
        <div class="shell">
            ${nav('logs')}
            <div class="log-container">
                ${logLines.length > 0 ? logLines : '<div class="log-line log-info">No logs yet...</div>'}
            </div>
            <p class="hint">Newest at the bottom. Refreshes every 5 seconds.</p>
        </div>
        <style>
            .log-container { background: var(--surface-container); border: 1px solid var(--outline-variant); border-radius: 6px; padding: 10px 16px; overflow-x: auto; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; line-height: 1.6; word-break: break-word; }
            .log-line { padding: 4px 0; border-bottom: 1px solid var(--outline-variant); }
            .log-line:last-child { border-bottom: none; }
            .log-time { color: var(--on-surface-variant); }
            .log-level { font-weight: 600; font-size: 11px; padding: 1px 6px; border-radius: 4px; margin-right: 4px; }
            .log-info .log-level { background: var(--primary-container); color: var(--on-primary-container); }
            .log-error .log-level { background: var(--error-container); color: var(--on-error-container); }
            .log-error { color: var(--error); }
        </style>
        <script>window.scrollTo(0, document.body.scrollHeight);</script>`;
    return layout({ title: 'System logs · WhatsApp Logger', refresh: 5, content });
}

module.exports = {
    healthPage,
    logsPage,
    loginPage,
    loginFailedPage,
    connectedPage,
    qrPage,
    legacyDonePage,
    qrErrorPage,
    initializingPage
};
