// Application web installable (PWA) : manifeste, service worker, page hors ligne, icônes.
//
// Le service worker ne met en cache QUE des ressources publiques et sans données (page hors ligne, icônes) :
// jamais une page du compte ni de l'administration (identifiants de sources, liste d'appareils…). Les pages
// passent toujours par le réseau ; sans réseau, la page hors ligne s'affiche à la place.

import { ICON_192, ICON_512, APPLE_TOUCH_ICON } from "./pwa-assets/icons.js";

/** Version du cache : à changer quand la page hors ligne ou les icônes changent (l'ancien cache est supprimé). */
export const PWA_CACHE = "utv-pwa-v1";
export const OFFLINE_PATH = "/offline";

const ICONS = {
  "/pwa/icon-192.png": ICON_192,
  "/pwa/icon-512.png": ICON_512,
  "/apple-touch-icon.png": APPLE_TOUCH_ICON,
};

export const MANIFEST = {
  name: "Ultra TV",
  short_name: "Ultra TV",
  description: "Sources, appareils et réglages de vos appareils Ultra TV.",
  id: "/",
  start_url: "/?source=pwa",
  scope: "/",
  display: "standalone",
  orientation: "any",
  background_color: "#0A0A0C",
  theme_color: "#0A0A0C",
  lang: "fr",
  icons: [
    { src: "/pwa/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
    { src: "/pwa/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
    { src: "/pwa/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
  ],
  shortcuts: [
    { name: "Associer un appareil", url: "/pair", icons: [{ src: "/pwa/icon-192.png", sizes: "192x192", type: "image/png" }] },
    { name: "Envoyer une source à ma TV", url: "/envoyer", icons: [{ src: "/pwa/icon-192.png", sizes: "192x192", type: "image/png" }] },
  ],
};

/** Service worker : réseau d'abord pour les pages, page hors ligne en repli ; icônes servies depuis le cache. */
export const SERVICE_WORKER = `// Ultra TV : service worker (voir src/pwa.js).
const CACHE = ${JSON.stringify(PWA_CACHE)};
const OFFLINE = ${JSON.stringify(OFFLINE_PATH)};
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
`;

/** Réponse PWA pour [path] (GET/HEAD), ou null si la route n'est pas une ressource PWA. */
export function pwaResponse(path, offlinePage) {
  if (path === "/manifest.webmanifest") {
    return new Response(JSON.stringify(MANIFEST), {
      headers: { "content-type": "application/manifest+json; charset=utf-8", "cache-control": "public, max-age=3600", "x-content-type-options": "nosniff" },
    });
  }
  if (path === "/sw.js") {
    return new Response(SERVICE_WORKER, {
      headers: {
        "content-type": "text/javascript; charset=utf-8",
        // Toujours revalidé : une nouvelle version du service worker doit être prise au prochain passage.
        "cache-control": "no-cache",
        "service-worker-allowed": "/",
        "x-content-type-options": "nosniff",
      },
    });
  }
  if (Object.hasOwn(ICONS, path)) {
    const bin = Uint8Array.from(atob(ICONS[path]), (c) => c.charCodeAt(0));
    return new Response(bin, { headers: { "content-type": "image/png", "cache-control": "public, max-age=604800", "x-content-type-options": "nosniff" } });
  }
  if (path === OFFLINE_PATH) return offlinePage();
  return null;
}

/** Balises du <head> : manifeste, icône d'écran d'accueil iOS, mode plein écran installé. */
export const PWA_HEAD = `<link rel="manifest" href="/manifest.webmanifest"/><link rel="apple-touch-icon" href="/apple-touch-icon.png"/><meta name="mobile-web-app-capable" content="yes"/><meta name="apple-mobile-web-app-capable" content="yes"/><meta name="apple-mobile-web-app-status-bar-style" content="black"/><meta name="apple-mobile-web-app-title" content="Ultra TV"/>`;

/** Enregistrement du service worker (script inline, nonce de la page). */
export const PWA_REGISTER = `if("serviceWorker" in navigator){addEventListener("load",function(){navigator.serviceWorker.register("/sw.js").catch(function(){});});}`;
