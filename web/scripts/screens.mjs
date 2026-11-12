// Rendus de contrôle de l'interface : construit l'app avec un catalogue fictif (VITE_DEMO=1, voir src/demo.ts),
// la sert en local et prend des captures 1920×1080 avec Chrome headless (protocole DevTools, sans dépendance).
// Usage : node scripts/screens.mjs            (sombre + clair, toutes les pages)
//         node scripts/screens.mjs home guide  (seulement certaines pages)
// Langue : SCREENS_LANG=en (défaut fr) ; hors français, sortie dans web/.screens/<langue>/.
// Sortie : web/.screens/<thème>-<page>.png. Chrome : variable CHROME_PATH, sinon chemins usuels.
import { spawn, spawnSync } from "node:child_process";
import { createServer } from "node:http";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const LANG = process.env.SCREENS_LANG || "fr";
const base = join(root, ".screens");
const out = LANG === "fr" ? base : join(base, LANG);
const dist = join(base, "dist");
const PORT = 4179;
const DEBUG = 9339;

const CHROMES = [
  process.env.CHROME_PATH,
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
].filter(Boolean);
const chrome = CHROMES.find((p) => existsSync(p));
if (!chrome) { console.error("Chrome introuvable : définissez CHROME_PATH."); process.exit(1); }

// Pages : route (hash) + action facultative exécutée dans la page avant la capture.
const click = (sel, nth = 0, ev = "click") => `(() => { const e = document.querySelectorAll(${JSON.stringify(sel)})[${nth}]; if (e) e.dispatchEvent(new MouseEvent(${JSON.stringify(ev)}, { bubbles: true })); })()`;
const PAGES = {
  home: { route: "/" },
  live: { route: "/live" },
  "live-osd": { route: "/live", run: click(".preview .actions .btn", 0), wait: 2500 },
  "live-channels": { route: "/live", run: `${click(".preview .actions .btn", 0)}; setTimeout(() => ${click('.pdock .dbtn[aria-label]', 4)}, 1500)`, wait: 3500 },
  guide: { route: "/guide" },
  movies: { route: "/movies" },
  movie: { route: "/movie/1001" },
  serie: { route: "/serie/2000" },
  settings: { route: "/settings/display" },
  "vod-player": { route: "/movie/1001", run: click(".detail .actions .btn.primary", 0), wait: 2500 },
};
const wanted = process.argv.slice(2);
const pages = Object.entries(PAGES).filter(([k]) => !wanted.length || wanted.includes(k));

// 1. Build avec données fictives.
rmSync(dist, { recursive: true, force: true });
mkdirSync(out, { recursive: true });
mkdirSync(base, { recursive: true });
const b = spawnSync("npx", ["vite", "build", "--outDir", dist, "--emptyOutDir"], { cwd: root, stdio: "inherit", shell: true, env: { ...process.env, VITE_DEMO: "1" } });
if (b.status !== 0) process.exit(b.status ?? 1);

// 2. Serveur statique.
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml", ".json": "application/json", ".woff2": "font/woff2", ".woff": "font/woff", ".webmanifest": "application/manifest+json" };
const server = createServer((req, res) => {
  const p = normalize(decodeURIComponent((req.url ?? "/").split("?")[0])).replace(/^([/\\])+/, "") || "index.html";
  const f = join(dist, p);
  if (!f.startsWith(dist) || !existsSync(f)) { res.writeHead(404).end(); return; }
  res.writeHead(200, { "content-type": TYPES[extname(f)] ?? "application/octet-stream" }).end(readFileSync(f));
}).listen(PORT, "127.0.0.1");

// 3. Chrome headless piloté par DevTools.
const profile = join(tmpdir(), `utv-screens-${Date.now()}`);
const proc = spawn(chrome, [`--remote-debugging-port=${DEBUG}`, "--headless=new", `--user-data-dir=${profile}`, "--window-size=1920,1080", "--hide-scrollbars", "--force-device-scale-factor=1", "--no-first-run", `--lang=${LANG}`, "about:blank"], { stdio: "ignore" });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function pageWs() {
  for (let i = 0; i < 50; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${DEBUG}/json/list`)).json();
      const pg = list.find((t) => t.type === "page");
      if (pg) return pg.webSocketDebuggerUrl;
    } catch { /* Chrome démarre */ }
    await sleep(200);
  }
  throw new Error("Chrome ne répond pas");
}
const ws = new WebSocket(await pageWs());
await new Promise((r) => ws.addEventListener("open", r, { once: true }));
let n = 0;
const waiting = new Map();
ws.addEventListener("message", (m) => { const d = JSON.parse(m.data); if (d.id && waiting.has(d.id)) { waiting.get(d.id)(d); waiting.delete(d.id); } });
const send = (method, params = {}) => new Promise((resolve, reject) => { const id = ++n; waiting.set(id, (d) => (d.error ? reject(new Error(d.error.message)) : resolve(d.result))); ws.send(JSON.stringify({ id, method, params })); });

try {
  await send("Page.enable");
  await send("Emulation.setLocaleOverride", { locale: LANG }).catch(() => {});
  await send("Page.addScriptToEvaluateOnNewDocument", { source: `Object.defineProperty(navigator, "language", { get: () => "${LANG}" }); Object.defineProperty(navigator, "languages", { get: () => ["${LANG}"] });` });
  await send("Network.setUserAgentOverride", { userAgent: "Mozilla/5.0", acceptLanguage: LANG, platform: "Win32" }).catch(() => {});
  await send("Emulation.setDeviceMetricsOverride", { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
  let first = true;
  for (const theme of ["dark", "light"]) {
    await send("Emulation.setEmulatedMedia", { features: [{ name: "prefers-color-scheme", value: theme }] });
    for (const [name, p] of pages) {
      await send("Page.navigate", { url: `http://127.0.0.1:${PORT}/index.html#${p.route}` });
      await sleep(first ? 4500 : 1500);
      first = false;
      await send("Page.reload", {}); // repart d'une page propre (lecteur fermé) ; le catalogue est déjà semé
      await sleep(2500);
      if (p.run) { await send("Runtime.evaluate", { expression: p.run }); await sleep(p.wait ?? 1500); }
      const shot = await send("Page.captureScreenshot", { format: "png" });
      writeFileSync(join(out, `${theme}-${name}.png`), Buffer.from(shot.data, "base64"));
      console.log(`${theme}-${name}.png`);
    }
  }
} finally {
  ws.close();
  proc.kill();
  server.close();
  await sleep(500);
  try { rmSync(profile, { recursive: true, force: true }); } catch { /* Chrome tient encore des fichiers : sans importance */ }
}
