"use strict";
// Conversion audio à la volée pour le bureau : Chromium (donc Electron) ne décode ni l'AC-3, ni l'E-AC-3, ni le DTS.
// Quand le lecteur constate « image sans son », il redemande le flux via :
//
//   http://127.0.0.1:<port>/<token>/tx/<base64url(urlCible)>?ss=<secondes>
//
// et ce module lance ffmpeg : vidéo copiée telle quelle, audio converti en AAC stéréo, sortie MPEG-TS en flux.
// ffmpeg vient de l'installation (resources/ffmpeg, version LGPL embarquée sous Windows) ou du système (macOS :
// Homebrew). Sans ffmpeg, la fonction est simplement indisponible. Aucune URL n'est journalisée.

const fs = require("node:fs");
const path = require("node:path");
const { spawn, spawnSync } = require("node:child_process");

/** Chemins candidats, dans l'ordre : binaire embarqué, puis emplacements usuels du système, puis PATH. */
function ffmpegCandidates({ resourcesPath, platform = process.platform, env = process.env } = {}) {
  const exe = platform === "win32" ? "ffmpeg.exe" : "ffmpeg";
  const out = [];
  if (resourcesPath) out.push(path.join(resourcesPath, "ffmpeg", exe));
  if (env.ULTRATV_FFMPEG) out.push(env.ULTRATV_FFMPEG);
  if (platform === "darwin") out.push("/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg");
  if (platform === "linux") out.push("/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg");
  out.push(exe); // PATH
  return out;
}

/** Premier ffmpeg exécutable trouvé (vérifié par `-version`), ou null. */
function findFfmpeg(opts = {}, run = spawnSync) {
  for (const c of ffmpegCandidates(opts)) {
    if (path.isAbsolute(c) && !fs.existsSync(c)) continue;
    try {
      const r = run(c, ["-hide_banner", "-version"], { timeout: 5000, windowsHide: true });
      if (r && r.status === 0) return c;
    } catch { /* suivant */ }
  }
  return null;
}

/** Analyse /<token>/tx/<b64>?ss=…&live=1. Retourne { token, b64, ss, live } ou null. */
function parseTranscodePath(pathname, search) {
  const segs = String(pathname).split("/");
  if (segs.length !== 4 || segs[0] !== "" || segs[2] !== "tx") return null;
  const q = new URLSearchParams(search || "");
  const ssRaw = q.get("ss");
  const ss = ssRaw && /^\d{1,6}(\.\d{1,3})?$/.test(ssRaw) ? Number(ssRaw) : 0;
  return { token: segs[1], b64: segs[3], ss, live: q.get("live") === "1" };
}

/** Arguments ffmpeg (fonction pure, testée). */
function transcodeArgs(url, { ss = 0, userAgent = null, referer = null, live = false } = {}) {
  const a = ["-hide_banner", "-loglevel", "error", "-nostdin"];
  if (userAgent) a.push("-user_agent", String(userAgent).slice(0, 300));
  if (referer) a.push("-referer", String(referer).slice(0, 500));
  // Reconnexion seulement en direct : sur un fichier sans Content-Length, ffmpeg relirait le film en boucle.
  if (live) a.push("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5");
  // -ss AVANT -i : recherche rapide côté source (reprise / saut dans un film).
  if (ss > 0) a.push("-ss", String(ss));
  a.push(
    "-i", String(url),
    "-map", "0:v:0?", "-map", "0:a:0?",
    "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-ac", "2",
    "-sn", "-dn", "-f", "mpegts", "-muxdelay", "0", "pipe:1",
  );
  return a;
}

/**
 * Sert une requête de conversion : ffmpeg écrit le MPEG-TS dans la réponse ; fermeture du client = arrêt de ffmpeg.
 * `headers` : en-têtes CORS du proxy.
 */
function serveTranscode(req, res, { ffmpeg, target, ss, live = false, headers, report = () => {}, spawnFn = spawn }) {
  const ua = req.headers["x-ut-ua"] || null;
  const ref = req.headers["x-ut-referer"] || null;
  const child = spawnFn(ffmpeg, transcodeArgs(target.toString(), { ss, userAgent: ua, referer: ref, live }), { windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
  let started = false;
  const stop = () => { try { child.kill("SIGKILL"); } catch { /* déjà terminé */ } };
  res.on("close", stop);
  child.stderr.on("data", () => { /* messages ffmpeg ignorés : ils peuvent contenir l'URL */ });
  child.on("error", () => { report("ETRANSCODE"); if (!res.headersSent) { res.writeHead(502, headers); res.end(); } });
  child.stdout.once("data", () => {
    started = true;
    if (!res.headersSent) res.writeHead(200, { ...headers, "Content-Type": "video/mp2t", "Cache-Control": "no-store" });
  });
  child.stdout.on("data", (chunk) => { if (started && !res.writableEnded) res.write(chunk); });
  child.on("close", (code) => {
    if (!started && !res.headersSent) { report(code === 0 ? "ETRANSCODE_EMPTY" : "ETRANSCODE"); res.writeHead(502, headers); }
    if (!res.writableEnded) res.end();
  });
}

module.exports = { ffmpegCandidates, findFfmpeg, parseTranscodePath, transcodeArgs, serveTranscode };
