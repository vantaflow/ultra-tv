// Ultra TV service worker. Caches the app shell so the UI loads offline, but
// never caches API/proxy responses — those must always hit the network so live
// catalog and EPG stay fresh.

const CACHE = "ultratv-shell-v1";
const SHELL = [
  "/",
  "/index.html",
  "/manifest.webmanifest",
  "/icon-192.svg",
  "/icon-512.svg",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))),
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);

  // Bypass proxy and any cross-origin API calls — let them go straight to network.
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/api/")) return;

  // Network-first for HTML so deploys propagate without a long stale window.
  if (event.request.mode === "navigate") {
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(event.request, copy));
          return res;
        })
        .catch(() => caches.match(event.request).then((r) => r ?? caches.match("/index.html"))),
    );
    return;
  }

  // Cache-first for assets (hashed by Vite anyway).
  event.respondWith(
    caches.match(event.request).then((hit) => hit ?? fetch(event.request).then((res) => {
      const copy = res.clone();
      if (res.ok) caches.open(CACHE).then((c) => c.put(event.request, copy));
      return res;
    })),
  );
});
