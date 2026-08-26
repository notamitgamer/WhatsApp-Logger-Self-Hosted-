const { MAX_LOGS } = require('./config');

// --- IN-MEMORY LOGGING BUFFER ---
const logBuffer = [];

const originalLog = console.log;
const originalError = console.error;

function teeLog(level, originalFn, ...args) {
    const message = args.map(arg =>
        typeof arg === 'object' ? JSON.stringify(arg) : String(arg)
    ).join(' ');

    logBuffer.push({ timestamp: Date.now(), level, message });
    if (logBuffer.length > MAX_LOGS) logBuffer.shift();

    originalFn.apply(console, args);
}

function install() {
    console.log = (...args) => teeLog('log', originalLog, ...args);
    console.error = (...args) => teeLog('error', originalError, ...args);
}

module.exports = { install, logBuffer };
