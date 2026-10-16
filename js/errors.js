window.onerror = function(msg, url, line) {
    const loader = document.getElementById('authLoader');
    if(loader && !loader.classList.contains('hidden')) {
        loader.innerHTML = `<div style="color:var(--md-sys-color-error); font-size:13px; text-align:left; padding:16px; border: 1px solid var(--md-sys-color-error); border-radius: 8px;">
            <strong>System Error:</strong><br>${msg}<br>Line: ${line}
        </div>`;
    }
};
