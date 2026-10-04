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
  secretsSecure: !!boot.secretsSecure,
  cloudRequest: (req) => ipcRenderer.invoke("ut:cloud:request", req),
  encrypt: (plain) => ipcRenderer.invoke("ut:encrypt", plain),
  decrypt: (cipher) => ipcRenderer.invoke("ut:decrypt", cipher),
  toggleFullscreen: () => ipcRenderer.invoke("ut:fullscreen:toggle"),
  setFullscreen: (value) => ipcRenderer.invoke("ut:fullscreen:set", !!value),
  isFullscreen: () => ipcRenderer.invoke("ut:fullscreen:get"),
  onFullscreenChange: (cb) => subscribe("ut:fullscreen:changed", cb),
  checkForUpdates: () => ipcRenderer.invoke("ut:update:check"),
  onUpdateStatus: (cb) => subscribe("ut:update:status", cb),
  openExternal: (url) => ipcRenderer.invoke("ut:open-external", String(url)),
  setTitleBarTheme: (isDark) => ipcRenderer.invoke("ut:titlebar-theme", !!isDark),
});
