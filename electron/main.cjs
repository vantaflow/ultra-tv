"use strict";
// Ultra TV - processus principal Electron.
//
// Modele de securite (voir README.md) :
//  - contextIsolation + sandbox, nodeIntegration off, webSecurity ON.
//  - l'app est servie par le protocole app://ultratv/ (jamais file://), avec une
//    CSP stricte ; en dev (SV_DEV=1) elle vient du serveur Vite.
//  - les flux IPTV (CORS/mixed content) passent par un proxy loopback protege par
//    jeton (proxy.cjs) ; la tolerance TLS n'existe que sur ce chemin.
//  - aucune URL de source ni identifiant n'est journalise.

const { app, BrowserWindow, Menu, Notification, dialog, protocol, session, shell, ipcMain, nativeTheme, safeStorage, net, screen } = require("electron");
const path = require("node:path");
const fs = require("node:fs");
const { createProxyServer } = require("./proxy.cjs");
const { findFfmpeg } = require("./transcode.cjs");
const { resolveAppFile, mimeFor } = require("./appfiles.cjs");
const { createSecrets } = require("./secrets.cjs");
const { createUpdater } = require("./updates.cjs");
const autoUpdateMod = require("./autoupdate.cjs");
const { cloudRequest } = require("./cloudfetch.cjs");
const { computeWindowFit } = require("./windowfit.cjs");
const dnsMod = require("./dns.cjs");
const trailer = require("./trailer.cjs");
const recorderMod = require("./recorder.cjs");

const APP_SCHEME = "app";
const APP_HOST = "ultratv";
const APP_ORIGIN = `${APP_SCHEME}://${APP_HOST}`;
const IS_DEV = !!process.env.SV_DEV;
const HEADLESS = process.env.ULTRATV_HEADLESS === "1";
const DEV_URL = process.env.ULTRATV_DEV_URL || "http://localhost:5173";
const DEV_ORIGIN = new URL(DEV_URL).origin;
const ALLOWED_ORIGIN = IS_DEV ? DEV_ORIGIN : APP_ORIGIN;
const WEB_ROOT = path.join(__dirname, "web-dist");

const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "font-src 'self' data:",
  "img-src 'self' data: blob: http://127.0.0.1:*",
  "media-src 'self' blob: http://127.0.0.1:*",
  "connect-src 'self' http://127.0.0.1:*",
  // Bande-annonce : lecteur YouTube integre en iframe (aucun script distant : l'API est pilotee par postMessage).
  `frame-src ${trailer.TRAILER_FRAME_SRC.join(" ")}`,
  "worker-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'none'",
  "frame-ancestors 'none'",
].join("; ");

// Profil isole (tests) : doit etre fixe avant ready et avant le verrou d'instance.
if (process.env.ULTRATV_USER_DATA) app.setPath("userData", path.resolve(process.env.ULTRATV_USER_DATA));

// Decodage HEVC via le decodeur de la plateforme (VideoToolbox / D3D11 / VAAPI).
app.commandLine.appendSwitch("enable-features", "PlatformHEVCDecoderSupport");

protocol.registerSchemesAsPrivileged([
  {
    scheme: APP_SCHEME,
    privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true },
  },
]);

let mainWindow = null;
let proxy = null;
let ffmpegPath = null;
let updater = null;
const secrets = createSecrets(safeStorage);

// ---------------------------------------------------------------------------
// DNS personnalise (dns.cjs) : reglage local, enregistre dans userData/dns.json pour etre applique
// des le demarrage, avant la premiere requete (le renderer n'est pas encore charge a ce moment-la).
// ---------------------------------------------------------------------------

let dnsSettings = { ...dnsMod.DEFAULT_SETTINGS };
const resolver = dnsMod.createResolver({ getSettings: () => dnsSettings });
const dnsFile = () => path.join(app.getPath("userData"), "dns.json");

function loadDnsSettings() {
  try {
    dnsSettings = dnsMod.normalizeSettings(JSON.parse(fs.readFileSync(dnsFile(), "utf8")));
  } catch {
    dnsSettings = { ...dnsMod.DEFAULT_SETTINGS };
  }
}

// Verification automatique des mises a jour (autoupdate.cjs) : activee par defaut, memorisee dans userData/autoupdate.json.
let autoUpdateEnabled = true;
const autoUpdateFile = () => path.join(app.getPath("userData"), "autoupdate.json");

/** Applique le reglage a Chromium (configureHostResolver peut etre rappele a chaud). */
function applyChromiumDns() {
  const { appliesToChromium, ...cfg } = dnsMod.chromiumResolverConfig(dnsSettings);
  try {
    app.configureHostResolver(cfg);
  } catch {
    console.warn("[ultra-tv] dns: configureHostResolver failed");
  }
  return appliesToChromium;
}

function setDnsSettings(raw) {
  const next = dnsMod.normalizeSettings(raw);
  const changed = JSON.stringify(next) !== JSON.stringify(dnsSettings);
  dnsSettings = next;
  if (changed) {
    resolver.clearCache();
    try {
      fs.writeFileSync(dnsFile(), JSON.stringify(next));
    } catch {
      console.warn("[ultra-tv] dns: save failed");
    }
  }
  const appliesToChromium = applyChromiumDns();
  return { settings: next, appliesToChromium };
}

/** Test : resout `host` (nom d'hote seul, jamais d'URL) et mesure la latence. */
async function testDns(host) {
  if (typeof host !== "string" || host.length > 253 || !/^[A-Za-z0-9.-]+$|^\[?[0-9a-fA-F:.]+\]?$/.test(host)) {
    return { ok: false, reason: "invalid-host" };
  }
  const h = host.replace(/^\[|\]$/g, "");
  resolver.clearCache();
  const t0 = Date.now();
  try {
    const r = await resolver.resolveHost(h);
    const a = r.addresses.find((x) => x.family === 4) || r.addresses[0];
    if (!a) return { ok: false, reason: "empty", ms: Date.now() - t0 };
    return { ok: true, address: a.address, ms: Date.now() - t0, via: r.via === "cache" ? "custom" : r.via, reason: r.reason };
  } catch (e) {
    return { ok: false, reason: (e && e.code) || "error", ms: Date.now() - t0 };
  }
}

// ---------------------------------------------------------------------------
// Enregistrements (recorder.cjs) : dossier choisi par l'utilisateur (par defaut Videos/Ultra TV), memorise dans
// userData/recorder.json avec les dossiers deja utilises (seuls fichiers que le renderer peut lire, reveler ou supprimer).
// ---------------------------------------------------------------------------

let recorder = null;
let recConf = { dir: "", dirs: [] };
/** Textes de la confirmation de fermeture, fournis par le renderer dans la langue de l'interface. */
let recLabels = null;
const recConfFile = () => path.join(app.getPath("userData"), "recorder.json");
const defaultRecDir = () => path.join(app.getPath("videos"), "Ultra TV");

function loadRecConf() {
  try {
    const raw = JSON.parse(fs.readFileSync(recConfFile(), "utf8"));
    const dirs = Array.isArray(raw.dirs) ? raw.dirs.filter((d) => typeof d === "string" && path.isAbsolute(d)).slice(-10) : [];
    recConf = { dir: typeof raw.dir === "string" && path.isAbsolute(raw.dir) ? raw.dir : "", dirs };
  } catch {
    recConf = { dir: "", dirs: [] };
  }
}
function recDir() {
  return recConf.dir || defaultRecDir();
}
function knownRecDirs() {
  return [...new Set([recDir(), defaultRecDir(), ...recConf.dirs])];
}
function setRecDir(dir) {
  recConf = { dir, dirs: [...recConf.dirs.filter((d) => d !== dir), dir].slice(-10) };
  try {
    fs.writeFileSync(recConfFile(), JSON.stringify(recConf));
  } catch {
    console.warn("[ultra-tv] recorder: save failed");
  }
}

/** Notifications systeme gardees en memoire jusqu'au clic ou a la fermeture (sinon le ramasse-miettes les retire). */
const liveNotifications = new Set();

// ---------------------------------------------------------------------------
// Utilitaires d'origine
// ---------------------------------------------------------------------------

function isAppUrl(u) {
  try {
    const p = new URL(u);
    return p.protocol === `${APP_SCHEME}:` && p.host === APP_HOST;
  } catch {
    return false;
  }
}
function isTrustedUrl(u) {
  if (isAppUrl(u)) return true;
  if (!IS_DEV) return false;
  try {
    return new URL(u).origin === DEV_ORIGIN;
  } catch {
    return false;
  }
}
function trustedSender(event) {
  if (!mainWindow || mainWindow.isDestroyed()) return false;
  if (event.sender !== mainWindow.webContents) return false;
  const frame = event.senderFrame;
  return !!frame && frame === mainWindow.webContents.mainFrame && isTrustedUrl(frame.url);
}
function isHttpUrl(u) {
  try {
    const p = new URL(u);
    return p.protocol === "http:" || p.protocol === "https:";
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// Page d'erreur de repli (inline, aucune ressource externe)
// ---------------------------------------------------------------------------

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

function buildErrorPage(title, detail, retryHref) {
  const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ultra TV - Error</title>
<style>
  html,body{height:100%;margin:0}
  body{background:#0A0A0C;color:#F5F5F7;font-family:system-ui,Segoe UI,Roboto,sans-serif;
       display:flex;align-items:center;justify-content:center;text-align:center;padding:24px}
  .card{max-width:560px}
  h1{font-size:20px;margin:0 0 12px}
  p{opacity:.7;margin:0 0 20px;font-size:14px;word-break:break-word}
  code{display:block;background:#17171B;border:1px solid #2A2A30;padding:12px;border-radius:8px;font-size:12px;
       text-align:left;white-space:pre-wrap;margin:0 0 20px;opacity:.85}
  a.btn{display:inline-block;background:#D91E2B;color:#fff;border-radius:8px;padding:10px 22px;
        font-size:14px;text-decoration:none}
  a.btn:hover{background:#E8323F}
</style></head>
<body><div class="card">
  <h1>${esc(title)}</h1>
  <p>Ultra TV could not load the application.</p>
  <code>${esc(detail)}</code>
  <a class="btn" href="${esc(retryHref)}">Retry</a>
</div></body></html>`;
  return "data:text/html;charset=utf-8," + encodeURIComponent(html);
}

function appEntryUrl() {
  return IS_DEV ? DEV_URL : `${APP_ORIGIN}/`;
}

function showFallback(win, title, detail) {
  console.error(`[ultra-tv] ${title}`);
  if (!win || win.isDestroyed()) return;
  win.loadURL(buildErrorPage(title, detail, appEntryUrl())).catch(() => {});
}

function attachFailureHandlers(win) {
  const wc = win.webContents;
  wc.on("did-fail-load", (_e, errorCode, errorDescription, validatedURL, isMainFrame) => {
    if (!isMainFrame || errorCode === -3) return;
    if (typeof validatedURL === "string" && validatedURL.startsWith("data:text/html")) return;
    showFallback(win, "Load failed", `${errorDescription} (${errorCode})`);
  });
  wc.on("render-process-gone", (_e, details) => {
    showFallback(win, "Renderer crashed", `${details.reason} (exitCode ${details.exitCode})`);
  });
  win.on("unresponsive", () => {
    showFallback(win, "Application unresponsive", "The renderer stopped responding.");
  });
}

// ---------------------------------------------------------------------------
// Protocole app:// et en-tetes de securite
// ---------------------------------------------------------------------------

function securityHeaders() {
  return {
    "Content-Security-Policy": CSP,
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "Cross-Origin-Opener-Policy": "same-origin",
  };
}

function registerAppProtocol() {
  protocol.handle(APP_SCHEME, async (request) => {
    let url;
    try {
      url = new URL(request.url);
    } catch {
      return new Response("Bad request", { status: 400 });
    }
    if (url.host !== APP_HOST || (request.method !== "GET" && request.method !== "HEAD")) {
      return new Response("Not found", { status: 404 });
    }
    const file = resolveAppFile(WEB_ROOT, url.pathname);
    if (!file) return new Response("Not found", { status: 404, headers: securityHeaders() });
    try {
      const data = await fs.promises.readFile(file);
      return new Response(request.method === "HEAD" ? null : data, {
        status: 200,
        headers: { "Content-Type": mimeFor(file), "Cache-Control": "no-cache", ...securityHeaders() },
      });
    } catch {
      return new Response("Not found", { status: 404, headers: securityHeaders() });
    }
  });
}

function setupSession() {
  const ses = session.defaultSession;

  // CSP injectee sur NOTRE origine uniquement (jamais sur les reponses distantes).
  // Les reponses du protocole la portent deja ; ceci couvre aussi les cas ou
  // Chromium relaie l'evenement. En dev (Vite : HMR, scripts inline) on n'injecte pas.
  if (!IS_DEV) {
    ses.webRequest.onHeadersReceived((details, callback) => {
      if (!isAppUrl(details.url)) return callback({ responseHeaders: details.responseHeaders });
      const headers = { ...(details.responseHeaders || {}) };
      for (const k of Object.keys(headers)) {
        if (k.toLowerCase() === "content-security-policy") delete headers[k];
      }
      headers["Content-Security-Policy"] = [CSP];
      callback({ responseHeaders: headers });
    });
  }

  // Lecteur de bande-annonce : app:// n'envoie pas de Referer, YouTube refuse alors l'integration (voir trailer.cjs).
  ses.webRequest.onBeforeSendHeaders({ urls: trailer.TRAILER_EMBED_URLS }, (details, callback) => {
    callback({ requestHeaders: trailer.withTrailerReferer(details.requestHeaders) });
  });

  const ALLOWED_PERMISSIONS = new Set(["fullscreen", "clipboard-sanitized-write"]);
  ses.setPermissionRequestHandler((wc, permission, callback, details) => {
    const origin = details && details.requestingUrl ? details.requestingUrl : wc.getURL();
    callback(ALLOWED_PERMISSIONS.has(permission) && isTrustedUrl(origin));
  });
  ses.setPermissionCheckHandler((_wc, permission, requestingOrigin) => {
    return ALLOWED_PERMISSIONS.has(permission) && isTrustedUrl(requestingOrigin);
  });
}

// ---------------------------------------------------------------------------
// IPC (tous les canaux valident l'expediteur)
// ---------------------------------------------------------------------------

function registerIpc() {
  ipcMain.on("ut:boot", (event) => {
    if (!trustedSender(event)) {
      event.returnValue = null;
      return;
    }
    event.returnValue = {
      version: app.getVersion(),
      proxyBase: proxy ? proxy.base : "",
      // Conversion audio AC-3 / DTS disponible (ffmpeg embarqué ou installé).
      transcode: !!(proxy && ffmpegPath),
      secretsSecure: secrets.isSecure(),
    };
  });

  const handle = (channel, fn) =>
    ipcMain.handle(channel, (event, ...args) => {
      if (!trustedSender(event)) throw new Error("forbidden");
      return fn(...args);
    });

  handle("ut:cloud:request", (req) => cloudRequest(req, (u, init) => net.fetch(u, init)));
  handle("ut:encrypt", (plain) => secrets.encrypt(plain));
  handle("ut:decrypt", (cipher) => secrets.decrypt(cipher));
  // Fenêtre fermée (macOS : application encore vivante) : mainWindow vaut null, on ne lève pas d'exception.
  const win = () => (mainWindow && !mainWindow.isDestroyed() ? mainWindow : null);
  handle("ut:fullscreen:toggle", () => {
    const w = win();
    if (!w) return false;
    w.setFullScreen(!w.isFullScreen());
    return w.isFullScreen();
  });
  handle("ut:fullscreen:set", (value) => {
    const w = win();
    if (!w) return false;
    w.setFullScreen(!!value);
    return !!value;
  });
  handle("ut:fullscreen:get", () => { const w = win(); return w ? w.isFullScreen() : false; });
  handle("ut:update:check", () => updater.check());
  handle("ut:update:install", () => updater.install());
  handle("ut:update:auto:get", () => autoUpdateEnabled);
  handle("ut:update:auto:set", (value) => {
    autoUpdateEnabled = !!value;
    autoUpdateMod.saveAutoUpdate(autoUpdateFile(), autoUpdateEnabled);
    return autoUpdateEnabled;
  });
  handle("ut:open-external", async (url) => {
    if (typeof url !== "string" || url.length > 4096 || !isHttpUrl(url)) throw new Error("invalid-url");
    await shell.openExternal(url);
    return true;
  });
  handle("ut:dns:get", () => ({ settings: dnsSettings, appliesToChromium: dnsMod.chromiumResolverConfig(dnsSettings).appliesToChromium }));
  handle("ut:dns:set", (raw) => setDnsSettings(raw));
  handle("ut:dns:test", (host) => testDns(host));
  // Notification systeme (rappels) : le clic ramene la fenetre et renvoie l'identifiant au renderer.
  handle("ut:notify", (n) => {
    if (!Notification.isSupported() || !n || typeof n !== "object") return false;
    const id = typeof n.id === "string" ? n.id.slice(0, 200) : "";
    const title = typeof n.title === "string" ? n.title.slice(0, 200) : "";
    const body = typeof n.body === "string" ? n.body.slice(0, 400) : "";
    if (!id || !title) return false;
    const notif = new Notification({ title, body });
    liveNotifications.add(notif);
    const forget = () => liveNotifications.delete(notif);
    notif.on("click", () => {
      forget();
      const w = win();
      if (!w) return;
      if (w.isMinimized()) w.restore();
      w.show();
      w.focus();
      w.webContents.send("ut:notify:click", id);
    });
    notif.on("close", forget);
    notif.show();
    return true;
  });
  // Enregistrements : l'URL du flux ne fait que transiter vers recorder.cjs (jamais journalisee ni renvoyee).
  handle("ut:rec:start", (job) => (recorder ? recorder.start(job) : { ok: false, error: "unavailable" }));
  handle("ut:rec:stop", (id) => (recorder && typeof id === "string" ? recorder.stop(id) : false));
  handle("ut:rec:list", () => (recorder ? recorder.list() : { active: [], finished: [] }));
  handle("ut:rec:dir", () => recDir());
  handle("ut:rec:choose-dir", async () => {
    const w = win();
    const r = await (w ? dialog.showOpenDialog(w, { defaultPath: recDir(), properties: ["openDirectory", "createDirectory"] }) : dialog.showOpenDialog({ defaultPath: recDir(), properties: ["openDirectory", "createDirectory"] }));
    if (r.canceled || !r.filePaths[0]) return null;
    setRecDir(r.filePaths[0]);
    return recDir();
  });
  handle("ut:rec:open-dir", async () => {
    try {
      fs.mkdirSync(recDir(), { recursive: true });
    } catch {
      return false;
    }
    return (await shell.openPath(recDir())) === "";
  });
  handle("ut:rec:reveal", (p) => {
    if (!recorderMod.isRecordingFile(knownRecDirs(), p) || !fs.existsSync(p)) return false;
    shell.showItemInFolder(p);
    return true;
  });
  handle("ut:rec:open", async (p) => {
    if (!recorderMod.isRecordingFile(knownRecDirs(), p) || !fs.existsSync(p)) return false;
    return (await shell.openPath(p)) === "";
  });
  handle("ut:rec:delete", async (p) => {
    if (!recorderMod.isRecordingFile(knownRecDirs(), p)) return false;
    try {
      await fs.promises.unlink(p);
    } catch (e) {
      return !!(e && e.code === "ENOENT");
    }
    return true;
  });
  handle("ut:rec:labels", (l) => {
    const ok = (v) => typeof v === "string" && v.length > 0 && v.length <= 300;
    if (l && ok(l.title) && ok(l.message) && ok(l.quit) && ok(l.cancel)) recLabels = { title: l.title, message: l.message, quit: l.quit, cancel: l.cancel };
    return true;
  });
  handle("ut:titlebar-theme", (isDark) => {
    nativeTheme.themeSource = isDark ? "dark" : "light";
    return true;
  });
}

// ---------------------------------------------------------------------------
// Fenetre et menu
// ---------------------------------------------------------------------------

function buildMenu() {
  if (process.platform !== "darwin") {
    Menu.setApplicationMenu(null);
    return;
  }
  const view = [{ role: "togglefullscreen" }];
  if (IS_DEV) view.unshift({ role: "reload" }, { role: "toggleDevTools" }, { type: "separator" });
  Menu.setApplicationMenu(
    Menu.buildFromTemplate([
      {
        label: app.name,
        submenu: [
          { role: "about" },
          { type: "separator" },
          { role: "hide" },
          { role: "hideOthers" },
          { role: "unhide" },
          { type: "separator" },
          { role: "quit" },
        ],
      },
      {
        label: "Edit",
        submenu: [
          { role: "undo" },
          { role: "redo" },
          { type: "separator" },
          { role: "cut" },
          { role: "copy" },
          { role: "paste" },
          { role: "selectAll" },
        ],
      },
      { label: "View", submenu: view },
      { label: "Window", submenu: [{ role: "minimize" }, { role: "zoom" }, { type: "separator" }, { role: "front" }] },
    ]),
  );
}

function createWindow() {
  const isMac = process.platform === "darwin";
  // Taille bornée à la zone de travail de l'écran sous le curseur (zoom Windows pris en compte :
  // workArea est en DIP). Évite une fenêtre coupée en bas sur un portable 1366x768 à 125 %.
  let fit;
  try {
    const display = screen.getDisplayNearestPoint(screen.getCursorScreenPoint());
    fit = computeWindowFit(display.workArea, display.scaleFactor, null);
  } catch {
    fit = computeWindowFit(null, 1, null);
  }
  mainWindow = new BrowserWindow({
    ...fit.bounds,
    minWidth: fit.minWidth,
    minHeight: fit.minHeight,
    backgroundColor: "#0A0A0C",
    title: "Ultra TV",
    show: !HEADLESS,
    ...(isMac ? { titleBarStyle: "hiddenInset", trafficLightPosition: { x: 16, y: 18 } } : {}),
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      webviewTag: false,
      allowRunningInsecureContent: false,
      devTools: IS_DEV || process.env.ULTRATV_DEVTOOLS === "1",
      autoplayPolicy: "no-user-gesture-required",
      preload: path.join(__dirname, "preload.cjs"),
    },
  });
  const wc = mainWindow.webContents;

  wc.on("will-attach-webview", (e) => e.preventDefault());

  // Liens externes : navigateur systeme, http(s) uniquement ; jamais de fenetre Electron.
  wc.setWindowOpenHandler(({ url }) => {
    if (isHttpUrl(url)) void shell.openExternal(url);
    return { action: "deny" };
  });

  // Navigation verrouillee sur notre origine (ou le serveur de dev).
  const lock = (event, url) => {
    if (!isTrustedUrl(url)) event.preventDefault();
  };
  wc.on("will-navigate", lock);
  wc.on("will-redirect", lock);

  // F11 / Echap : le menu est nul hors macOS, donc pas d'accelerateur natif.
  wc.on("before-input-event", (event, input) => {
    if (input.type === "keyDown" && input.key === "F11") {
      mainWindow.setFullScreen(!mainWindow.isFullScreen());
      event.preventDefault();
    }
  });

  const notifyFullscreen = (value) => () => {
    if (!wc.isDestroyed()) wc.send("ut:fullscreen:changed", value);
  };
  mainWindow.on("enter-full-screen", notifyFullscreen(true));
  mainWindow.on("leave-full-screen", notifyFullscreen(false));
  wc.on("did-finish-load", () => {
    const status = updater.status();
    if (status.state !== "unavailable" || status.message !== "not-initialised") wc.send("ut:update:status", status);
  });

  attachFailureHandlers(mainWindow);
  mainWindow.loadURL(appEntryUrl()).catch(() => {});
  if (IS_DEV) wc.openDevTools({ mode: "detach" });

  // Enregistrement en cours : la fermeture de la fenetre l'arreterait (sauf macOS, ou l'application reste ouverte).
  mainWindow.on("close", (e) => {
    if (process.platform === "darwin" || !recorder || recorder.active() === 0 || !recLabels) return;
    const choice = dialog.showMessageBoxSync(mainWindow, {
      type: "warning",
      title: recLabels.title,
      message: recLabels.message,
      buttons: [recLabels.cancel, recLabels.quit],
      defaultId: 0,
      cancelId: 0,
      noLink: true,
    });
    if (choice === 0) e.preventDefault();
  });

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

// ---------------------------------------------------------------------------
// Cycle de vie
// ---------------------------------------------------------------------------

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
  });

  app.whenReady().then(async () => {
    // DNS applique avant toute requete (proxy, fenetre, mises a jour).
    loadDnsSettings();
    autoUpdateEnabled = autoUpdateMod.loadAutoUpdate(autoUpdateFile());
    applyChromiumDns();
    ffmpegPath = findFfmpeg({ resourcesPath: app.isPackaged ? process.resourcesPath : null });
    proxy = createProxyServer({
      ffmpeg: ffmpegPath,
      allowedOrigin: ALLOWED_ORIGIN,
      lookup: resolver.lookup,
      onError: (code) => console.warn(`[ultra-tv] proxy error ${code}`),
    });
    try {
      await proxy.start();
    } catch {
      // Sans proxy local, l'application démarre quand même (proxyBase vide : le renderer passe en accès direct).
      console.warn("[ultra-tv] proxy start failed");
      proxy = null;
    }

    // Windows : identite des notifications (meme identifiant que l'installateur NSIS).
    if (process.platform === "win32") app.setAppUserModelId("com.ultratv.desktop");
    loadRecConf();
    recorder = recorderMod.createRecorder({
      getDir: recDir,
      lookup: resolver.lookup,
      onEvent: (ev) => {
        if (ev.type === "error") console.warn(`[ultra-tv] recorder error ${ev.code}`);
        if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send("ut:rec:event", ev);
      },
    });

    updater = createUpdater({
      app,
      send: (status) => {
        if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send("ut:update:status", status);
      },
    });

    registerAppProtocol();
    setupSession();
    registerIpc();
    buildMenu();
    createWindow();

    if (app.isPackaged && !IS_DEV && !HEADLESS) {
      // 5 s apres le lancement, puis toutes les 6 h : seulement si le reglage est actif (le bouton manuel reste utilisable).
      autoUpdateMod.scheduleAutoChecks({ check: () => updater.check(), isEnabled: () => autoUpdateEnabled });
    }

    app.on("activate", () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });

  app.on("will-quit", () => {
    if (recorder) recorder.stopAll();
    if (proxy) void proxy.stop();
  });
}
