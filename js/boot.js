function isMobileApp() {
    const isStandalone = (window.matchMedia('(display-mode: standalone)').matches) || (window.navigator.standalone) || document.referrer.includes('android-app://');
    return isStandalone && window.innerWidth <= 768;
}

window.onload = async () => {
    try {
        await initIndexedDB();

        if (!navigator.onLine) {
            document.getElementById('offlineIndicator').classList.remove('hidden');
        }
        window.addEventListener('online', () => {
            document.getElementById('offlineIndicator').classList.add('hidden');
        });
        window.addEventListener('offline', () => {
            document.getElementById('offlineIndicator').classList.remove('hidden');
        });

        const savedTheme = localStorage.getItem('theme') || 'latte';
        document.body.setAttribute('data-theme', savedTheme);
        const themeSelector = document.getElementById('themeSelector');
        if(themeSelector) themeSelector.value = savedTheme;
        
        document.getElementById('profileName').value = localStorage.getItem('profileName') || '';
        document.getElementById('profilePhone').value = localStorage.getItem('profilePhone') || '';
        
        populateFAQ();

        const authValid = localStorage.getItem('wp_api_token') && localStorage.getItem('wp_auth_expiry') > Date.now();
        
        if (authValid) {
            document.getElementById('auth-screen').style.opacity = '0';
            
            setTimeout(() => {
                document.getElementById('auth-screen').style.display = 'none';
                if (isMobileApp()) {
                    const hasPin = localStorage.getItem('wp_app_pin');
                    const useBio = localStorage.getItem('wp_app_bio') === 'true';
                    document.getElementById('mobileSecuritySection').classList.remove('hidden');
                    document.getElementById('pinLockToggle').checked = !!hasPin;
                    document.getElementById('bioLockToggle').checked = useBio;
                    
                    if (hasPin || useBio) {
                        initLockScreen(hasPin, useBio);
                    } else {
                        document.getElementById('app-layout').classList.add('visible');
                        startAppSync();
                    }
                } else {
                    document.getElementById('app-layout').classList.add('visible');
                    startAppSync();
                }
            }, 400); 
        } else {
            document.getElementById('authLoader').classList.add('hidden');
            document.getElementById('authForm').classList.remove('hidden');
            document.getElementById('authForm').classList.add('fade-in');
            
            if (isMobileApp()) {
                document.getElementById('mobileSecuritySection').classList.remove('hidden');
            }
        }
        
        document.getElementById('messagesContainer').addEventListener('scroll', function() {
            const fab = document.getElementById('scrollFab');
            if (this.scrollHeight - this.scrollTop - this.clientHeight > 500) {
                fab.classList.add('visible');
            } else {
                fab.classList.remove('visible');
                document.getElementById('newMsgBadge').classList.add('hidden');
            }
        });

        document.querySelectorAll('input[type="text"], input[type="password"]').forEach(input => {
            input.addEventListener('focus', () => {
                if (window.innerWidth <= 768) {
                    setTimeout(() => input.scrollIntoView({ behavior: 'smooth', block: 'center' }), 300);
                }
            });
        });
    } catch (err) {
        console.error("Critical Boot Error:", err);
        const loader = document.getElementById('authLoader');
        if (loader) {
            loader.innerHTML = `<div style="color:var(--md-sys-color-error); padding: 16px; text-align: center;">
                <span class="material-symbols-rounded" style="font-size: 32px; margin-bottom: 8px;">error</span>
                <p style="font-size: 14px; font-weight: 500;">App failed to load.</p>
                <p style="font-size: 12px; opacity: 0.8; margin-top: 4px;">${err.message}</p>
            </div>`;
        }
    }
};

async function startAppSync() {
    loadChats();
    connectGlobalSyncStream();
}
