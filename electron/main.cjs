// Ultra TV — Electron shell.
//
// Why Electron exists here: the browser can't decode HEVC / AC3 / EAC3 / MKV that
// many IPTV providers serve. Electron bundles Chromium with native codecs enabled
// (proprietary codecs available in unofficial Electron builds, or via system
// FFmpeg on Linux). We also strip CORS so the app can hit any upstream directly —
// no Cloudflare/Vercel proxy needed.

const { app, BrowserWindow, session, shell } = require("electron");
const path = require("node:path");
const url = require("node:url");

// Force-enable extra command-line codecs where supported. Has no effect on
// codecs the underlying Chromium doesn't ship — for full HEVC support use the
// `@castlabs/electron-releases` fork.
app.commandLine.appendSwitch("enable-features", "PlatformHEVCDecoderSupport");
app.commandLine.appendSwitch("ignore-certificate-errors");

let mainWindow = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 800,
    minHeight: 500,
    backgroundColor: "#0b1020",
    title: "Ultra TV",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, "preload.cjs"),
      webSecurity: false, // disable CORS for IPTV upstreams
    },
    autoHideMenuBar: true,
  });

  // Strip restrictive response headers (CSP, X-Frame-Options) on IPTV calls so
  // hls.js / shaka can reach segments straight from the providers.
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    const headers = details.responseHeaders || {};
    for (const k of Object.keys(headers)) {
      const lk = k.toLowerCase();
      if (lk === "x-frame-options" || lk === "content-security-policy") delete headers[k];
    }
    headers["access-control-allow-origin"] = ["*"];
    callback({ responseHeaders: headers });
  });

  // External links open in the system browser, not in the app.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });

  const isDev = !!process.env.SV_DEV;
  if (isDev) {
    mainWindow.loadURL("http://localhost:5173");
    mainWindow.webContents.openDevTools({ mode: "detach" });
  } else {
    const indexPath = path.join(__dirname, "..", "web", "dist", "index.html");
    mainWindow.loadURL(url.pathToFileURL(indexPath).toString());
  }

  mainWindow.on("closed", () => { mainWindow = null; });
}

app.whenReady().then(() => {
  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
