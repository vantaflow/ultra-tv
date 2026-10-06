"use strict";
// Mise a jour automatique via electron-updater (GitHub Releases).
//
// Etats envoyes au renderer : { state, version?, percent?, message? } avec state
// dans checking | available | not-available | downloading | downloaded |
// unavailable | error. "unavailable" couvre : mode dev, application non signee
// sur macOS (Squirrel.Mac refuse d'installer une mise a jour non signee), absence
// de metadonnees de publication. Rien ne plante : tout est dans des try/catch.

const { execFile, spawn } = require("node:child_process");
const path = require("node:path");
const fs = require("node:fs");
const os = require("node:os");

function macAppBundle() {
  // .../Ultra TV.app/Contents/MacOS/Ultra TV -> .../Ultra TV.app
  return path.resolve(process.execPath, "..", "..", "..");
}

function isMacSigned() {
  return new Promise((resolve) => {
    execFile("codesign", ["-dvv", macAppBundle()], { timeout: 8000 }, (err, _out, stderr) => {
      if (err) return resolve(false);
      // Signature ad hoc ou sans equipe => pas de mise a jour possible.
      const team = /TeamIdentifier=(.+)/.exec(stderr || "");
      resolve(!!team && team[1].trim() !== "not set");
    });
  });
}

const REPO = "khalilbenaz/ultra-tv";

// La release GitHub "Latest" est celle de l'application Android (vX.Y.Z), sans latest.yml :
// le fournisseur GitHub d'electron-updater ne trouvait donc jamais la version de bureau.
// On cherche la release de bureau publiee la plus recente (desktop-vX.Y.Z) et on pointe dessus.
// Exporte pour les tests.
function pickDesktopTag(releases) {
  const tags = (Array.isArray(releases) ? releases : [])
    .filter((r) => r && !r.draft && !r.prerelease && /^desktop-v\d+\.\d+\.\d+$/.test(r.tag_name || ""))
    .map((r) => r.tag_name);
  const num = (t) => t.slice("desktop-v".length).split(".").map(Number);
  tags.sort((a, b) => {
    const x = num(a), y = num(b);
    for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return y[i] - x[i];
    return 0;
  });
  return tags[0] || null;
}

async function latestDesktopTag() {
  const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=50`, {
    headers: { Accept: "application/vnd.github+json", "User-Agent": "UltraTV-Updater" },
    signal: AbortSignal.timeout(15000),
  });
  if (!res.ok) throw new Error(`github ${res.status}`);
  return pickDesktopTag(await res.json());
}

/** Compare deux versions « X.Y.Z » : > 0 si a est plus récente. Exporté pour les tests. */
function compareVersions(a, b) {
  const x = String(a).split(".").map((n) => parseInt(n, 10) || 0);
  const y = String(b).split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) - (y[i] || 0);
  return 0;
}

/**
 * macOS sans signature Apple : Squirrel.Mac refuse toute mise à jour. On la fait nous-mêmes :
 * téléchargement du zip universel de la dernière release de bureau, puis, à l'installation, un petit script
 * détaché attend la fin de l'application, remplace le paquet .app, retire la quarantaine et relance.
 * Un fichier téléchargé par l'application n'a pas d'attribut de quarantaine : Gatekeeper ne bloque pas.
 */
function createMacUnsignedUpdater({ app, publish }) {
  let downloaded = null; // { version, appPath }
  const bundle = macAppBundle();

  function writable() {
    try { fs.accessSync(path.dirname(bundle), fs.constants.W_OK); fs.accessSync(bundle, fs.constants.W_OK); return true; } catch { return false; }
  }

  async function download(url, dest, onPct) {
    const res = await fetch(url, { headers: { "User-Agent": "UltraTV-Updater" }, redirect: "follow", signal: AbortSignal.timeout(15 * 60_000) });
    if (!res.ok || !res.body) throw new Error(`download ${res.status}`);
    const total = Number(res.headers.get("content-length")) || 0;
    const out = fs.createWriteStream(dest);
    let got = 0; let lastPct = -1;
    // Flux d'écriture : une erreur disque (plein, droits) sans écouteur 'error' ferait planter le processus.
    const failed = new Promise((_, j) => out.once("error", j));
    failed.catch(() => undefined);
    const write = async () => {
      for await (const chunk of res.body) {
        got += chunk.length;
        if (!out.write(chunk)) await Promise.race([new Promise((r) => out.once("drain", r)), failed]);
        const pct = total ? Math.floor((got / total) * 100) : 0;
        if (pct !== lastPct) { lastPct = pct; onPct(pct); }
      }
      await Promise.race([new Promise((r, j) => out.end((e) => (e ? j(e) : r()))), failed]);
    };
    try {
      await write();
    } catch (e) {
      out.destroy();
      throw e;
    }
  }

  const run = (cmd, args) => new Promise((resolve, reject) => execFile(cmd, args, { timeout: 5 * 60_000 }, (e) => (e ? reject(e) : resolve())));

  let running = null;
  // Non ré-entrant : un second appel (clic + vérification périodique) rejoint le téléchargement en cours.
  function check() {
    if (!running) running = doCheck().finally(() => { running = null; });
    return running;
  }

  async function doCheck() {
    if (!writable()) { publish({ state: "unavailable", message: "not-writable" }); return; }
    publish({ state: "checking" });
    const tag = await latestDesktopTag();
    const version = tag && tag.slice("desktop-v".length);
    if (!version || compareVersions(version, app.getVersion()) <= 0) { publish({ state: "not-available" }); return; }
    if (downloaded && downloaded.version === version) { publish({ state: "downloaded", version }); return; }
    publish({ state: "available", version });
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "ultratv-update-"));
    let ok = false;
    try {
      const zip = path.join(dir, "update.zip");
      await download(`https://github.com/${REPO}/releases/download/${tag}/UltraTV-${version}-mac-universal.zip`, zip, (percent) => publish({ state: "downloading", percent, version }));
      const extract = path.join(dir, "app");
      fs.mkdirSync(extract);
      await run("ditto", ["-x", "-k", zip, extract]);
      const name = fs.readdirSync(extract).find((f) => f.endsWith(".app"));
      if (!name) throw new Error("no-app-in-zip");
      downloaded = { version, appPath: path.join(extract, name) };
      ok = true;
      publish({ state: "downloaded", version });
    } finally {
      // Échec : on ne laisse pas un zip partiel de plusieurs centaines de Mo dans /tmp. Succès : le .app extrait sert à l'installation.
      if (!ok) fs.rmSync(dir, { recursive: true, force: true });
    }
  }

  function install() {
    if (!downloaded) return false;
    // Script détaché : attend la fin de ce processus, remplace le paquet (l'ancien est gardé jusqu'au succès), relance.
    const sh = [
      `while kill -0 ${process.pid} 2>/dev/null; do sleep 0.3; done`,
      `OLD="$2.old-$$"`,
      `mv "$2" "$OLD" || exit 1`,
      `if ditto "$1" "$2"; then rm -rf "$OLD"; else rm -rf "$2"; mv "$OLD" "$2"; fi`,
      `xattr -dr com.apple.quarantine "$2" 2>/dev/null`,
      `open "$2"`,
    ].join("\n");
    const child = spawn("/bin/sh", ["-c", sh, "ultratv-update", downloaded.appPath, bundle], { detached: true, stdio: "ignore" });
    child.unref();
    setImmediate(() => app.quit());
    return true;
  }

  return { check, install };
}

function createUpdater({ app, send }) {
  let autoUpdater = null;
  let macUpdater = null;
  let last = { state: "unavailable", message: "not-initialised" };
  let initPromise = null;

  function publish(status) {
    last = status;
    try {
      send(status);
    } catch {
      /* fenetre fermee */
    }
  }

  async function init() {
    if (!app.isPackaged) {
      publish({ state: "unavailable", message: "dev" });
      return false;
    }
    if (process.platform === "darwin" && !(await isMacSigned())) {
      // Pas de signature Apple : mise à jour faite par l'application elle-même (voir createMacUnsignedUpdater).
      macUpdater = createMacUnsignedUpdater({ app, publish });
      return true;
    }
    try {
      ({ autoUpdater } = require("electron-updater"));
      autoUpdater.autoDownload = true;
      autoUpdater.autoInstallOnAppQuit = true;
      autoUpdater.allowPrerelease = false;
      autoUpdater.on("checking-for-update", () => publish({ state: "checking" }));
      autoUpdater.on("update-available", (i) => publish({ state: "available", version: i && i.version }));
      autoUpdater.on("update-not-available", () => publish({ state: "not-available" }));
      autoUpdater.on("download-progress", (p) =>
        publish({ state: "downloading", percent: Math.round((p && p.percent) || 0) }),
      );
      autoUpdater.on("update-downloaded", (i) => publish({ state: "downloaded", version: i && i.version }));
      autoUpdater.on("error", () => publish({ state: "error", message: "update-failed" }));
      return true;
    } catch {
      autoUpdater = null;
      publish({ state: "unavailable", message: "updater-missing" });
      return false;
    }
  }

  let checking = null;
  // Non ré-entrant : deux vérifications simultanées (bouton + minuterie) partagent la même promesse.
  function check() {
    if (!checking) checking = doCheck().finally(() => { checking = null; });
    return checking;
  }

  async function doCheck() {
    if (!initPromise) initPromise = init();
    const ok = await initPromise;
    if (ok && macUpdater) {
      try { await macUpdater.check(); } catch { publish({ state: "error", message: "update-failed" }); }
      return last;
    }
    if (!ok || !autoUpdater) return last;
    try {
      const tag = await latestDesktopTag();
      if (!tag) {
        publish({ state: "not-available" });
        return last;
      }
      autoUpdater.setFeedURL({ provider: "generic", url: `https://github.com/${REPO}/releases/download/${tag}` });
      await autoUpdater.checkForUpdates();
    } catch {
      publish({ state: "error", message: "update-failed" });
    }
    return last;
  }

  // Installation immediate d'une mise a jour deja telechargee (silencieuse, relance l'application).
  function install() {
    if (macUpdater) return last.state === "downloaded" ? macUpdater.install() : false;
    if (!autoUpdater || last.state !== "downloaded") return false;
    setImmediate(() => {
      try { autoUpdater.quitAndInstall(true, true); } catch { publish({ state: "error", message: "update-failed" }); }
    });
    return true;
  }

  return { check, install, status: () => last };
}

module.exports = { createUpdater, pickDesktopTag, compareVersions };
