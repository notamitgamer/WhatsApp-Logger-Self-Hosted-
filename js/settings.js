function togglePinSetting() {
    if(document.getElementById('pinLockToggle').checked) openPinSetup(); else localStorage.removeItem('wp_app_pin');
}

function toggleBioSetting() {
    if(document.getElementById('bioLockToggle').checked) registerBiometric();
    else { localStorage.removeItem('wp_app_bio'); localStorage.removeItem('wp_bio_id'); }
}

function openPinSetup() {
    document.getElementById('pinSetupModal').classList.add('active');
    setupStep = 1; setupPin = ""; enteredPin = ""; updateSetupDots(0);
    document.getElementById('pinSetupTitle').innerText = "Set 6-Digit PIN";
    document.getElementById('pinSetupMsg').innerText = "Enter a new PIN";
}

function updateSetupDots(count) {
    document.querySelectorAll('#setupDots .pin-dot').forEach((d, i) => i < count ? d.classList.add('filled') : d.classList.remove('filled'));
}

function pressSetupKey(num) {
    if(num === -1) enteredPin = enteredPin.slice(0, -1);
    else if (enteredPin.length < 6) enteredPin += num;
    updateSetupDots(enteredPin.length);

    if(enteredPin.length === 6) {
        if(setupStep === 1) {
            setupPin = enteredPin; enteredPin = ""; setupStep = 2;
            setTimeout(() => { updateSetupDots(0); document.getElementById('pinSetupTitle').innerText = "Confirm PIN"; document.getElementById('pinSetupMsg').innerText = "Re-enter to confirm"; }, 200);
        } else if (setupStep === 2) {
            if(enteredPin === setupPin) {
                localStorage.setItem('wp_app_pin', setupPin);
                closeModal('pinSetupModal'); enteredPin = "";
            } else {
                const dots = document.getElementById('setupDots'); dots.classList.add('shake');
                setTimeout(() => { dots.classList.remove('shake'); enteredPin = ""; setupPin = ""; setupStep = 1; updateSetupDots(0); document.getElementById('pinSetupTitle').innerText = "Mismatch. Try Again"; document.getElementById('pinSetupMsg').innerText = "Enter a new PIN"; }, 500);
            }
        }
    }
}

function cancelPinSetup() { document.getElementById('pinLockToggle').checked = false; closeModal('pinSetupModal'); }

async function registerBiometric() {
    try {
        const iconPath = new URL('public/icon.svg', window.location.href).toString();
        const credential = await navigator.credentials.create({
            publicKey: { challenge: new Uint8Array([1, 2, 3, 4]), rp: { name: "WhatsApp Logger", icon: iconPath }, user: { id: new Uint8Array([1]), name: "User", displayName: "App User" }, pubKeyCredParams: [{type: "public-key", alg: -7}], authenticatorSelection: { authenticatorAttachment: "platform", userVerification: "required" }, timeout: 60000 }
        });
        
        if(credential) {
            localStorage.setItem('wp_bio_id', arrayBufferToBase64(credential.rawId));
            localStorage.setItem('wp_app_bio', 'true');
        } else throw new Error();
    } catch (e) { document.getElementById('bioLockToggle').checked = false; }
}

function showForgotPin() { document.getElementById('forgotPinModal').classList.add('active'); }

async function attemptPinReset() {
     const btn = document.getElementById('resetBtn'); const err = document.getElementById('resetError');
     btn.classList.add('loading'); btn.disabled = true; err.style.display = 'none';
     
     try {
        const response = await fetch(`${RENDER_BACKEND_URL}/api/verify`, {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ username: document.getElementById('resetUsername').value, password: document.getElementById('resetPassword').value })
        });
        const data = await response.json();
        
        if(data.success) {
            ['wp_app_pin','wp_app_bio','wp_bio_id','wp_fail_attempts','wp_lock_until'].forEach(k => localStorage.removeItem(k));
            failAttempts = 0; lockUntil = 0;
            document.getElementById('pinLockToggle').checked = false; document.getElementById('bioLockToggle').checked = false;
            document.getElementById('lockoutUI').classList.add('hidden'); document.getElementById('lockHeader').classList.remove('hidden');
            document.getElementById('lock-screen').classList.remove('locked-mode');
            closeModal('forgotPinModal'); unlockApp();
        } else throw new Error();
     } catch (e) { err.style.display = 'block'; } finally { btn.classList.remove('loading'); btn.disabled = false; }
}

function togglePasswordVisibility(id, iconId) {
    const input = document.getElementById(id); const icon = document.getElementById(iconId);
    if (input.type === 'password') { input.type = 'text'; icon.innerText = 'visibility_off'; } 
    else { input.type = 'password'; icon.innerText = 'visibility'; }
}

async function attemptLogin() {
    const btn = document.getElementById('loginBtn'); const err = document.getElementById('authError');
    btn.classList.add('loading'); btn.disabled = true; err.style.opacity = '0';
    try {
        const response = await fetch(`${RENDER_BACKEND_URL}/api/verify`, {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ username: document.getElementById('username').value, password: document.getElementById('password').value })
        });
        const data = await response.json();
        if(data.success && data.token) {
            localStorage.setItem('wp_api_token', data.token);
            
            if(document.getElementById('rememberMe').checked) localStorage.setItem('wp_auth_expiry', Date.now() + (3 * 24 * 60 * 60 * 1000));
            else localStorage.setItem('wp_auth_expiry', Date.now() + (1 * 60 * 60 * 1000));
            
            revealApp();
        } else throw new Error();
    } catch (e) { err.style.opacity = '1'; btn.classList.remove('loading'); btn.disabled = false; }
}

function revealApp() {
     document.getElementById('auth-screen').style.opacity = '0';
     setTimeout(() => {
         document.getElementById('auth-screen').style.display = 'none';
         document.getElementById('app-layout').classList.add('visible'); 
         startAppSync();
     }, 400);
}

function openSettings() { 
    document.getElementById('settingsSidebar').classList.add('active'); 
    calculateLocalStorageUsage();
}

function closeSettingsSidebar() {
    document.getElementById('settingsSidebar').classList.remove('active');
}

async function clearAppCacheData() {
    if (confirm("Are you absolutely sure you want to clear all cached data? This will clear your offline cache databases and instantly reload the workspace application.")) {
        closeSettingsSidebar();
        document.getElementById('refreshModal').classList.add('active');
        document.getElementById('refreshAuthStep').classList.add('hidden');
        document.getElementById('refreshProgressStep').classList.remove('hidden');
        
        await executeHardRefresh();
    }
}
function closeModal(id) { document.getElementById(id).classList.remove('active'); }

function changeTheme(themeName) {
    document.body.setAttribute('data-theme', themeName);
    localStorage.setItem('theme', themeName);
}

function changeFontSize(val) { document.documentElement.style.setProperty('--base-font-size', `${val}px`); }

function saveProfile() { localStorage.setItem('profileName', document.getElementById('profileName').value); localStorage.setItem('profilePhone', document.getElementById('profilePhone').value); }
function populateFAQ() {
    const faqs = [
        {q: "Is this secure?", a: "Yes, data is secured on your own custom backend server and access is guarded by your API Token."},
        {q: "Does it track deleted messages?", a: "Yes, once a message is received by the server, it is logged forever, even if the sender deletes it."},
        {q: "Does my phone need to be online?", a: "No. Once linked, your Render server handles logging even if your phone is powered off."},
        {q: "Is this legal?", a: "The tool uses standard WhatsApp Web protocols to function as a linked device. It is intended for personal archiving of your own conversations."},
        {q: "Does it save photos/videos?", a: "No. To keep the project free, the logger only saves text content. Media files would fill up your free tier storage very quickly."},
        {q: "Does it save View Once messages?", a: "No. WhatsApp does not send the content of `View Once` media to linked devices/web clients, so the logger cannot see them."}
    ];
    const container = document.getElementById('faqContainer');
    faqs.forEach(item => {
        const div = document.createElement('div'); div.className = 'faq-item';
        div.innerHTML = `<button class="faq-question" onclick="this.parentElement.classList.toggle('open')">${item.q} <span class="material-symbols-rounded">expand_more</span></button><div class="faq-answer">${item.a}</div>`;
        container.appendChild(div);
    });
}

function toggleChatMenu(event) { if(event) event.stopPropagation(); document.getElementById('chatMenu').classList.toggle('show'); }
document.addEventListener('click', function(e) { const menu = document.getElementById('chatMenu'); if(menu.classList.contains('show') && !e.target.closest('.menu-container')) menu.classList.remove('show'); });

function toggleChatSearch() { 
    const searchContainer = document.getElementById('inChatSearch');
    searchContainer.classList.toggle('hidden'); 
    document.getElementById('chatMenu').classList.remove('show'); 
    
    const input = document.getElementById('msgSearchInput');
    if (searchContainer.classList.contains('hidden')) {
        input.value = '';
        input.dispatchEvent(new Event('input'));
    } else {
        input.focus(); 
    }
}

document.getElementById('msgSearchInput').addEventListener('input', (e) => {
    const term = e.target.value.toLowerCase();
    let count = 0;

    document.querySelectorAll('.msg-bubble').forEach(b => {
        const textDiv = b.querySelector('.msg-text'); 
        if(textDiv) {
            if (!textDiv.hasAttribute('data-orig')) {
                textDiv.setAttribute('data-orig', textDiv.innerHTML);
            }
            
            const originalHTML = textDiv.getAttribute('data-orig');
            const rawText = textDiv.textContent || "";
            
            if(term && rawText.toLowerCase().includes(term)) {
                b.style.display = 'flex';
                const safeTerm = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
                const regex = new RegExp(`(?![^<]*>)(${safeTerm})`, 'gi');
                
                textDiv.innerHTML = originalHTML.replace(regex, '<span class="highlight">$1</span>');
                count++;
            } else {
                b.style.display = term ? 'none' : 'flex';
                textDiv.innerHTML = originalHTML; 
            }
        }
    });
    document.getElementById('searchCount').innerText = term ? `${count} found` : '';
});
