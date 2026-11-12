/*
 * theme-sync.js
 * Keeps the Android status bar / navigation bar (the PWA "theme-color")
 * in sync with the active app theme (latte / mocha / black).
 *
 * It watches <body data-theme="..."> so it works with the existing
 * boot.js + changeTheme() without modifying them.
 *
 * Load it AFTER boot.js / settings.js, at the end of <body>.
 */
(function () {
    // Fallbacks, identical to --md-sys-color-surface in css/tokens.css
    var FALLBACK = { latte: '#eff1f5', mocha: '#1e1e2e', black: '#000000' };

    function getMeta() {
        var m = document.querySelector('meta[name="theme-color"]');
        if (!m) {
            m = document.createElement('meta');
            m.setAttribute('name', 'theme-color');
            document.head.appendChild(m);
        }
        return m;
    }

    function currentColor() {
        var theme = document.body.getAttribute('data-theme') ||
                    localStorage.getItem('theme') || 'latte';
        // Prefer the real CSS token so this never drifts from tokens.css
        var css = getComputedStyle(document.body)
            .getPropertyValue('--md-sys-color-surface').trim();
        return css || FALLBACK[theme] || FALLBACK.latte;
    }

    function apply() {
        var color = currentColor();
        getMeta().setAttribute('content', color);
        // Paint the area behind the system bars (edge-to-edge) the same colour
        document.documentElement.style.backgroundColor = color;
    }

    apply();

    new MutationObserver(apply).observe(document.body, {
        attributes: true,
        attributeFilter: ['data-theme']
    });
})();
