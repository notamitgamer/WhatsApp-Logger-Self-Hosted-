let enteredPin = ""; let setupPin = ""; let setupStep = 0; let isLocked = false;
let failAttempts = parseInt(localStorage.getItem('wp_fail_attempts') || '0');
let lockUntil = parseInt(localStorage.getItem('wp_lock_until') || '0');
let lockoutTimerInterval;
let calCurrentDate = new Date(); let calSelectedDate = new Date();

const arrayBufferToBase64 = (buffer) => {
    let binary = ''; const bytes = new Uint8Array(buffer); const len = bytes.byteLength;
    for (let i = 0; i < len; i++) binary += String.fromCharCode(bytes[i]);
    return window.btoa(binary);
};
const base64ToArrayBuffer = (base64) => {
    const binary_string = window.atob(base64); const len = binary_string.length; const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) bytes[i] = binary_string.charCodeAt(i);
    return bytes.buffer;
};

function formatDividerDate(ts) {
    const date = new Date(ts * 1000); const today = new Date(); const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    if (date.toDateString() === today.toDateString()) return "Today";
    if (date.toDateString() === yesterday.toDateString()) return "Yesterday";
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function formatLastActive(ts) {
    if (!ts) return 'Unknown';
    const date = new Date(ts * 1000);
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    
    const timeOpts = { hour: '2-digit', minute: '2-digit' };
    if (date.toDateString() === today.toDateString()) {
        return `Today at ${date.toLocaleTimeString([], timeOpts)}`;
    } else if (date.toDateString() === yesterday.toDateString()) {
        return `Yesterday at ${date.toLocaleTimeString([], timeOpts)}`;
    }
    return `${date.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' })} at ${date.toLocaleTimeString([], timeOpts)}`;
}

// HTML Sanitizer to prevent Code Injection via chat messages
function escapeHTML(str) {
    if (!str) return '';
    return str.replace(/[&<>'"]/g, 
        tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag] || tag)
    );
}
