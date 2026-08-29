const CACHE_NAME = 'walogger-4.2.3'; // Incremented to trigger an update
const FONT_CACHE = 'walogger-fonts-4.2.3'; // New cache specifically for offline Google Fonts
const ASSETS = [
    './',
    '<--- website link --->',
    '<--- icon --->',
    'https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700&display=swap',
    'https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0'
];

self.addEventListener('install', (e) => {
    // skipWaiting ensures the new Service Worker takes control immediately
    self.skipWaiting();
    e.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS)));
});

self.addEventListener('activate', (e) => {
    // Automatically clean up old caches when the new one is activated
    e.waitUntil(
        caches.keys().then(keys => Promise.all(
            keys.map(key => {
                if (key !== CACHE_NAME && key !== FONT_CACHE) {
                    return caches.delete(key);
                }
            })
        ))
    );
});

self.addEventListener('fetch', (e) => {
    const url = new URL(e.request.url);

    // Dynamic Caching for Google Fonts (CSS & WOFF2 font files) to fix offline icons
    if (url.origin === 'https://fonts.googleapis.com' || url.origin === 'https://fonts.gstatic.com') {
        e.respondWith(
            caches.match(e.request).then(cachedResponse => {
                // Return from cache if we already have it
                if (cachedResponse) {
                    return cachedResponse;
                }
                
                // If it isn't cached yet, fetch it from the network and save it to the FONT_CACHE
                return fetch(e.request).then(networkResponse => {
                    return caches.open(FONT_CACHE).then(cache => {
                        // Put a clone of the response in the cache
                        cache.put(e.request, networkResponse.clone());
                        return networkResponse;
                    });
                }).catch(() => {
                    // Fallback if offline and font not previously cached
                    return new Response('', { status: 404, statusText: 'Offline' });
                });
            })
        );
    } else {
        // Standard Strategy for app files
        e.respondWith(
            caches.match(e.request).then(response => response || fetch(e.request))
        );
    }
});
