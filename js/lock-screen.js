function initLockScreen(hasPin, useBio) {
    const lockScreen = document.getElementById('lock-screen');
    lockScreen.classList.add('active'); isLocked = true; enteredPin = "";
    updatePinDots(0);
    if (checkLockoutState()) return;

    if(hasPin) {
        document.getElementById('pinUI').classList.remove('hidden'); document.getElementById('bioUI').classList.add('hidden');
        document.getElementById('lockTitle').innerText = "Enter PIN"; document.getElementById('lockIcon').innerText = "lock";
    } else if (useBio && !hasPin) {
        document.getElementById('pinUI').classList.add('hidden'); document.getElementById('bioUI').classList.remove('hidden');
        document.getElementById('lockTitle').innerText = "Scan Fingerprint"; document.getElementById('lockIcon').innerText = "fingerprint";
        triggerBiometricAuth();
    }
}

function checkLockoutState() {
    const now = Date.now();
    if (failAttempts >= 5) { renderPermanentLock(); return true; }
    if (lockUntil > now) { startLockoutTimer(); return true; }
    if (lockUntil > 0 && lockUntil <= now) {
        lockUntil = 0; localStorage.setItem('wp_lock_until', '0');
        document.getElementById('lockoutUI').classList.add('hidden');
        document.getElementById('lockHeader').classList.remove('hidden');
        document.getElementById('lock-screen').classList.remove('locked-mode');
    }
    return false;
}

function handleFailedAttempt() {
    failAttempts++; localStorage.setItem('wp_fail_attempts', failAttempts);
    if (failAttempts === 3 || failAttempts === 4) {
        lockUntil = Date.now() + (30 * 1000); localStorage.setItem('wp_lock_until', lockUntil);
        startLockoutTimer();
    } else if (failAttempts >= 5) {
        renderPermanentLock();
    } else {
        document.getElementById('pinDots').classList.add('shake'); updatePinDots(6, true);
        setTimeout(() => { document.getElementById('pinDots').classList.remove('shake'); enteredPin = ""; updatePinDots(0); }, 400);
    }
}

function startLockoutTimer() {
    const ui = document.getElementById('lockoutUI'); const screen = document.getElementById('lock-screen'); const timerDisplay = document.getElementById('lockoutTimer');
    ui.classList.remove('hidden'); document.getElementById('lockHeader').classList.add('hidden'); screen.classList.add('locked-mode'); 
    document.getElementById('lockoutTitle').innerText = "Locked"; document.getElementById('lockoutMsg').innerText = `Too many attempts (${failAttempts})`;
    document.getElementById('lockoutSub').innerText = "Try again later"; document.getElementById('permanentLockActions').classList.add('hidden');
    document.getElementById('lockoutTimer').classList.remove('hidden');
    if(lockoutTimerInterval) clearInterval(lockoutTimerInterval);
    
    lockoutTimerInterval = setInterval(() => {
        const left = Math.ceil((lockUntil - Date.now()) / 1000);
        if (left <= 0) {
            clearInterval(lockoutTimerInterval); timerDisplay.innerText = "00:00";
            initLockScreen(localStorage.getItem('wp_app_pin'), localStorage.getItem('wp_app_bio') === 'true');
            document.getElementById('lockoutUI').classList.add('hidden'); document.getElementById('lockHeader').classList.remove('hidden');
            screen.classList.remove('locked-mode');
        } else {
            timerDisplay.innerText = `00:${left < 10 ? '0'+left : left}`;
        }
    }, 1000);
}

function renderPermanentLock() {
    document.getElementById('lockoutUI').classList.remove('hidden');
    document.getElementById('lockHeader').classList.add('hidden');
    document.getElementById('lock-screen').classList.add('locked-mode');
    document.getElementById('lockoutIcon').innerText = "gpp_bad";
    document.getElementById('lockoutTitle').innerText = "Security Lockout";
    document.getElementById('lockoutMsg').innerText = "Maximum attempts exceeded. App is locked.";
    document.getElementById('lockoutSub').innerText = "Use Forgot Password to reset.";
    document.getElementById('lockoutTimer').classList.add('hidden');
    document.getElementById('permanentLockActions').classList.remove('hidden');
}

function pressKey(num) {
    if(num === -1) enteredPin = enteredPin.slice(0, -1);
    else if (enteredPin.length < 6) enteredPin += num;
    updatePinDots(enteredPin.length);
    if(enteredPin.length === 6) verifyPin();
}

function updatePinDots(count, isError=false) {
    document.querySelectorAll('#pinDots .pin-dot').forEach((d, i) => {
        if(isError) { d.classList.add('error'); setTimeout(() => d.classList.remove('error'), 400); } 
        else { d.classList.remove('error'); if(i < count) d.classList.add('filled'); else d.classList.remove('filled'); }
    });
}

function verifyPin() {
    if(checkLockoutState()) return;
    const storedPin = localStorage.getItem('wp_app_pin');
    if(storedPin && enteredPin === storedPin) {
        failAttempts = 0; localStorage.setItem('wp_fail_attempts', '0'); localStorage.setItem('wp_lock_until', '0');
        if (localStorage.getItem('wp_app_bio') === 'true') {
            document.getElementById('pinUI').classList.add('hidden'); document.getElementById('bioUI').classList.remove('hidden');
            document.getElementById('lockTitle').innerText = "Verify Identity"; document.getElementById('lockIcon').innerText = "fingerprint";
            triggerBiometricAuth();
        } else unlockApp();
    } else handleFailedAttempt();
}

async function triggerBiometricAuth() {
    if(checkLockoutState()) return;
    if(!document.getElementById('bioUI').classList.contains('hidden')) document.getElementById('lockTitle').innerText = "Verify Identity";
    try {
        const storedId = localStorage.getItem('wp_bio_id');
        const options = { publicKey: { challenge: new Uint8Array([1, 2, 3, 4]), timeout: 60000, userVerification: "required" } };
        if (storedId) options.publicKey.allowCredentials = [{ type: 'public-key', id: base64ToArrayBuffer(storedId), transports: ['internal'] }];
        const credential = await navigator.credentials.get(options);
        if(credential) { failAttempts = 0; localStorage.setItem('wp_fail_attempts', '0'); localStorage.setItem('wp_lock_until', '0'); unlockApp(); }
    } catch (e) { console.warn("Bio failed", e); }
}

function unlockApp() {
    isLocked = false;
    document.getElementById('lock-screen').classList.remove('active');
    document.getElementById('app-layout').classList.add('visible');
    startAppSync();
}
