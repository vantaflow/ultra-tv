"use strict";
// Lecteur externe (VLC) : quand Chromium ne sait pas décoder un flux, l'utilisateur peut l'ouvrir dans VLC.
// Détection aux emplacements usuels (fonctions pures avec un système de fichiers injecté, donc testées), lancement détaché.
// Les adresses contiennent souvent les identifiants du fournisseur : rien n'est journalisé ici, ni adresse ni arguments.

const fs = require("node:fs");
const path = require("node:path");
const { spawn, spawnSync } = require("node:child_process");

/**
 * Lecteurs VLC possibles, dans l'ordre : [{ command, prefix }] où `prefix` précède les arguments (flatpak run …).
 * `exists(p)` : le fichier existe. Fonction pure, testée.
 */
function vlcCandidates({ platform = process.platform, env = process.env, home = "", registryDir = null } = {}) {
  const out = [];
  if (platform === "win32") {
    const join = path.win32.join;
    const roots = [env.ProgramFiles, env.ProgramW6432, env["ProgramFiles(x86)"], "C:\\Program Files", "C:\\Program Files (x86)"];
    if (env.LOCALAPPDATA) roots.push(join(env.LOCALAPPDATA, "Programs"));
    if (registryDir) out.push({ command: join(registryDir, "vlc.exe"), prefix: [] });
    for (const r of roots) if (r) out.push({ command: join(r, "VideoLAN", "VLC", "vlc.exe"), prefix: [] });
  } else if (platform === "darwin") {
    out.push({ command: "/Applications/VLC.app/Contents/MacOS/VLC", prefix: [] });
    if (home) out.push({ command: path.posix.join(home, "Applications/VLC.app/Contents/MacOS/VLC"), prefix: [] });
  } else {
    for (const d of String(env.PATH || "").split(":")) if (d) out.push({ command: path.posix.join(d, "vlc"), prefix: [] });
    out.push({ command: "/usr/bin/vlc", prefix: [] }, { command: "/usr/local/bin/vlc", prefix: [] });
    out.push({ command: "/snap/bin/vlc", prefix: [], kind: "snap" });
    for (const base of ["/var/lib/flatpak", home ? path.posix.join(home, ".local/share/flatpak") : ""]) {
      if (base) out.push({ command: "flatpak", prefix: ["run", "org.videolan.VLC"], kind: "flatpak", marker: path.posix.join(base, "app/org.videolan.VLC") });
    }
  }
  return out;
}

/** Lit le dossier d'installation de VLC dans le registre Windows (best effort). `run` = spawnSync. */
function windowsRegistryDir(run = spawnSync) {
  for (const key of ["HKLM\\SOFTWARE\\VideoLAN\\VLC", "HKLM\\SOFTWARE\\WOW6432Node\\VideoLAN\\VLC", "HKCU\\SOFTWARE\\VideoLAN\\VLC"]) {
    try {
      const r = run("reg", ["query", key, "/ve"], { timeout: 3000, windowsHide: true, encoding: "utf8" });
      if (!r || r.status !== 0) continue;
      const m = /REG_SZ\s+(.+?)\s*$/m.exec(String(r.stdout || ""));
      if (m) return path.win32.dirname(m[1].trim());
    } catch { /* suivant */ }
  }
  return null;
}

/** Premier VLC présent : { command, prefix } ou null. */
function findVlc(opts = {}, exists = fs.existsSync) {
  const platform = opts.platform || process.platform;
  const registryDir = opts.registryDir ?? (platform === "win32" && opts.run !== null ? windowsRegistryDir(opts.run || spawnSync) : null);
  for (const c of vlcCandidates({ ...opts, registryDir })) {
    if (c.marker ? exists(c.marker) : exists(c.command)) return { command: c.command, prefix: c.prefix };
  }
  return null;
}

const URL_OK = /^(?:https?|rtsp|rtmp|udp|mms):\/\//i;

/** Arguments VLC : options d'identité avant l'adresse (fonction pure, testée). null si l'adresse n'est pas autorisée. */
function vlcArgs(url, { userAgent = null, referer = null, title = null } = {}) {
  if (typeof url !== "string" || url.length > 4096 || !URL_OK.test(url) || /[\r\n\0]/.test(url)) return null;
  const clean = (s, n) => String(s).replace(/[\r\n\0]/g, " ").slice(0, n);
  const a = [];
  if (userAgent) a.push(`--http-user-agent=${clean(userAgent, 300)}`);
  if (referer) a.push(`--http-referrer=${clean(referer, 500)}`);
  if (title) a.push(`--meta-title=${clean(title, 120)}`);
  a.push("--", url);
  return a;
}

/** Lance VLC détaché ; renvoie true si le processus a démarré. Jamais d'adresse dans les erreurs. */
function launchVlc(vlc, request, spawnFn = spawn) {
  if (!vlc || !request) return false;
  const args = vlcArgs(request.url, request);
  if (!args) return false;
  try {
    const child = spawnFn(vlc.command, [...vlc.prefix, ...args], { detached: true, stdio: "ignore", windowsHide: true });
    child.on?.("error", () => { /* lancement impossible : rien à journaliser (l'adresse porte des identifiants) */ });
    child.unref?.();
    return true;
  } catch {
    return false;
  }
}

module.exports = { vlcCandidates, windowsRegistryDir, findVlc, vlcArgs, launchVlc };
