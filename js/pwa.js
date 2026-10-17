if ('serviceWorker' in navigator && (window.location.protocol === 'https:' || window.location.protocol === 'http:')) {
    navigator.serviceWorker.register('sw.js').catch(err => {
        console.warn('SW Register Fail:', err);
    });
    window.addEventListener('online', () => {
        if (navigator.serviceWorker.controller) navigator.serviceWorker.controller.postMessage('sync_on_reconnect');
    });
}

window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault(); deferredPrompt = e;
    document.getElementById('installAppSection').classList.remove('hidden');
    if (window.innerWidth <= 768) {
        setTimeout(() => document.getElementById('installSheet').classList.add('visible'), 2000);
    }
});

async function installPWA() {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === 'accepted') {
        closeInstallSheet();
        document.getElementById('installAppSection').classList.add('hidden');
    }
    deferredPrompt = null;
}

function closeInstallSheet() { document.getElementById('installSheet').classList.remove('visible'); }
