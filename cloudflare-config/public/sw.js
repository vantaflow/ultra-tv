// Ultra TV : service worker (voir src/pwa.js).
const CACHE = "utv-pwa-v1";
const OFFLINE = "/offline";
const PRECACHE = [OFFLINE, "/pwa/icon-192.png", "/pwa/icon-512.png"];
self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(PRECACHE)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (req.mode === "navigate") {
    // Jamais de mise en cache d'une page : seulement la page hors ligne si le réseau manque.
    e.respondWith(fetch(req).catch(() => caches.match(OFFLINE)));
    return;
  }
  if (PRECACHE.includes(url.pathname)) e.respondWith(caches.match(req).then((r) => r || fetch(req)));
});
