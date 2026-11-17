"use strict";
// Pont renderer <-> main. Expose `window.ultratv` (contextIsolation + sandbox).
// Aucune API Node n'est exposee ; chaque appel passe par IPC et le main valide
// l'expediteur (origine app://ultratv ou serveur de dev).

const { contextBridge, ipcRenderer } = require("electron");

// Lecture synchrone UNE fois au chargement : le renderer a besoin de proxyBase
// des le premier rendu.
const boot = ipcRenderer.sendSync("ut:boot") || {};

function subscribe(channel, cb) {
  if (typeof cb !== "function") return () => {};
  const listener = (_event, payload) => cb(payload);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
}

contextBridge.exposeInMainWorld("ultratv", {
  isElectron: true,
  platform: process.platform,
  arch: process.arch,
  version: String(boot.version || ""),
  proxyBase: String(boot.proxyBase || ""),
  transcode: !!boot.transcode,
  videoTranscode: !!boot.videoTranscode,
  vlc: !!boot.vlc,
  secretsSecure: !!boot.secretsSecure,
  cloudRequest: (req) => ipcRenderer.invoke("ut:cloud:request", req),
  encrypt: (plain) => ipcRenderer.invoke("ut:encrypt", plain),
  decrypt: (cipher) => ipcRenderer.invoke("ut:decrypt", cipher),
  toggleFullscreen: () => ipcRenderer.invoke("ut:fullscreen:toggle"),
  setFullscreen: (value) => ipcRenderer.invoke("ut:fullscreen:set", !!value),
  isFullscreen: () => ipcRenderer.invoke("ut:fullscreen:get"),
  onFullscreenChange: (cb) => subscribe("ut:fullscreen:changed", cb),
  checkForUpdates: () => ipcRenderer.invoke("ut:update:check"),
  installUpdate: () => ipcRenderer.invoke("ut:update:install"),
  getAutoUpdate: () => ipcRenderer.invoke("ut:update:auto:get"),
  setAutoUpdate: (value) => ipcRenderer.invoke("ut:update:auto:set", !!value),
  onUpdateStatus: (cb) => subscribe("ut:update:status", cb),
  // Lecteur externe : VLC détecté par le main, qui valide l'adresse.
  openInVlc: (req) => ipcRenderer.invoke("ut:vlc:open", req && typeof req === "object" ? { url: String(req.url), userAgent: req.userAgent ? String(req.userAgent) : null, referer: req.referer ? String(req.referer) : null, title: req.title ? String(req.title) : null } : null),
  openExternal: (url) => ipcRenderer.invoke("ut:open-external", String(url)),
  setTitleBarTheme: (isDark) => ipcRenderer.invoke("ut:titlebar-theme", !!isDark),
  // DNS personnalise : le main valide et normalise le reglage ; testDns ne recoit qu'un nom d'hote.
  getDns: () => ipcRenderer.invoke("ut:dns:get"),
  setDns: (settings) => ipcRenderer.invoke("ut:dns:set", settings && typeof settings === "object" ? { ...settings } : {}),
  testDns: (host) => ipcRenderer.invoke("ut:dns:test", String(host)),
  // Rappels : notification systeme ; le clic renvoie l'identifiant du rappel.
  notify: (n) => ipcRenderer.invoke("ut:notify", n && typeof n === "object" ? { id: String(n.id), title: String(n.title), body: String(n.body || "") } : null),
  onNotifyClick: (cb) => subscribe("ut:notify:click", cb),
  // Enregistrements (recorder.cjs) : le main valide chaque demande ; seuls les fichiers de ses dossiers sont accessibles.
  recStart: (job) => ipcRenderer.invoke("ut:rec:start", job && typeof job === "object" ? { ...job } : null),
  recStop: (id) => ipcRenderer.invoke("ut:rec:stop", String(id)),
  recList: () => ipcRenderer.invoke("ut:rec:list"),
  recDir: () => ipcRenderer.invoke("ut:rec:dir"),
  recChooseDir: () => ipcRenderer.invoke("ut:rec:choose-dir"),
  recOpenDir: () => ipcRenderer.invoke("ut:rec:open-dir"),
  recReveal: (p) => ipcRenderer.invoke("ut:rec:reveal", String(p)),
  recOpen: (p) => ipcRenderer.invoke("ut:rec:open", String(p)),
  recDelete: (p) => ipcRenderer.invoke("ut:rec:delete", String(p)),
  recSetLabels: (l) => ipcRenderer.invoke("ut:rec:labels", l && typeof l === "object" ? { ...l } : null),
  // Partages réseau montés par le système (nas.cjs) : le main parcourt le dossier et le déclare au proxy local.
  nasChoose: () => ipcRenderer.invoke("ut:nas:choose"),
  nasScan: (root, probe) => ipcRenderer.invoke("ut:nas:scan", String(root), !!probe),
  // Détection des serveurs multimédia du réseau local (discovery.cjs).
  discoverServers: () => ipcRenderer.invoke("ut:discover"),
  nasRegister: (root) => ipcRenderer.invoke("ut:nas:register", String(root)),
  onRecEvent: (cb) => subscribe("ut:rec:event", cb),
});

// Classe de plateforme posee DES le chargement (avant le premier rendu React) : le CSS reserve
// une zone de titre sous les boutons feu tricolore de macOS (titleBarStyle hiddenInset).
function tagPlatform() {
  document.documentElement.classList.add("platform-" + process.platform);
}
if (document.documentElement) tagPlatform();
else window.addEventListener("DOMContentLoaded", tagPlatform, { once: true });
