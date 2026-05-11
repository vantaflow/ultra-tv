// Preload bridge. Currently exposes a simple flag so the web app can detect it
// is running inside Electron and skip the Cloudflare/Vercel proxy (we don't need
// it: webSecurity is disabled in main.cjs).

const { contextBridge } = require("electron");

contextBridge.exposeInMainWorld("__ultratv_env", {
  isElectron: true,
  platform: process.platform,
  arch: process.arch,
});
