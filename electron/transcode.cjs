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

/**
 * Analyse /<token>/tx/<b64>?ss=…&a=…&live=1 (conversion), /<token>/txi/<b64> (liste des pistes) et
 * /<token>/txs/<b64>?s=… (sous-titre en WebVTT). Retourne { kind, token, b64, ss, live, a, s } ou null.
 */
function parseTranscodePath(pathname, search) {
  const segs = String(pathname).split("/");
  const kinds = { tx: "tx", txi: "info", txs: "sub" };
  if (segs.length !== 4 || segs[0] !== "" || !Object.hasOwn(kinds, segs[2])) return null;
  const q = new URLSearchParams(search || "");
  const ssRaw = q.get("ss");
  const ss = ssRaw && /^\d{1,6}(\.\d{1,3})?$/.test(ssRaw) ? Number(ssRaw) : 0;
  const idx = (k) => { const v = q.get(k); return v && /^\d{1,2}$/.test(v) ? Number(v) : 0; };
  return { kind: kinds[segs[2]], token: segs[1], b64: segs[3], ss, live: q.get("live") === "1", a: idx("a"), s: idx("s") };
}

/** Arguments ffmpeg (fonction pure, testée). */
function transcodeArgs(url, { ss = 0, userAgent = null, referer = null, live = false, audio = 0 } = {}) {
  // -re : la source est lue au rythme réel de la lecture. Sans lui, ffmpeg convertit à pleine vitesse : le tampon
  // du lecteur se remplit en quelques dizaines de secondes, il cesse de lire, ffmpeg reste bloqué sur son tube de
  // sortie et SA connexion au fournisseur devient inactive — certains serveurs la coupent ; quand le lecteur
  // reprend, la conversion est morte et la lecture patine jusqu'à la réouverture (boucle de chargement).
  const a = ["-hide_banner", "-loglevel", "error", "-nostdin", "-re"];
  if (userAgent) a.push("-user_agent", String(userAgent).slice(0, 300));
  if (referer) a.push("-referer", String(referer).slice(0, 500));
  // Reconnexion seulement en direct : sur un fichier sans Content-Length, ffmpeg relirait le film en boucle.
  if (live) a.push("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5");
  // -ss AVANT -i : recherche rapide côté source (reprise / saut dans un film).
  if (ss > 0) a.push("-ss", String(ss));
  a.push(
    "-i", String(url),
    "-map", "0:v:0?", "-map", `0:a:${audio}?`,
    "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-ac", "2",
    "-sn", "-dn", "-f", "mpegts", "-muxdelay", "0", "pipe:1",
  );
  return a;
}

// --- pistes audio / sous-titres : lues dans l'en-tête du fichier par `ffmpeg -i` (le build minimal n'a pas ffprobe) ---

/** Sous-titres texte convertibles en WebVTT ; les sous-titres image (PGS, VobSub, DVB) ne le sont pas. */
const TEXT_SUBS = new Set(["subrip", "srt", "ass", "ssa", "webvtt", "mov_text", "text"]);

/**
 * Extrait { audio, subs } de la sortie d'erreur de `ffmpeg -i` (fonction pure, testée). `i` est l'index de la piste
 * dans son type (celui de `-map 0:a:i` / `0:s:i`) ; les sous-titres image comptent dans l'index mais ne sont pas listés.
 */
function parseStreams(stderr) {
  const audio = [];
  const subs = [];
  let nAudio = 0;
  let nSub = 0;
  let cur = null;
  for (const line of String(stderr).split(/\r?\n/)) {
    const m = /^\s*Stream #\d+:\d+(?:\[[^\]]*\])?(?:\(([^)]*)\))?[^:]*: (Audio|Subtitle|Video|Data|Attachment): ([A-Za-z0-9_]+)/.exec(line);
    if (m) {
      const lang = m[1] && m[1] !== "und" ? m[1].toLowerCase().slice(0, 12) : "";
      const codec = m[3].toLowerCase();
      cur = null;
      if (m[2] === "Audio") { cur = { i: nAudio++, lang, title: "", codec }; audio.push(cur); }
      else if (m[2] === "Subtitle") {
        if (TEXT_SUBS.has(codec)) { cur = { i: nSub, lang, title: "", codec }; subs.push(cur); }
        nSub++;
      }
      continue;
    }
    const t = /^\s+title\s*:\s*(.+)$/i.exec(line);
    if (t && cur && !cur.title) cur.title = t[1].trim().slice(0, 80);
  }
  return { audio, subs };
}

/** Lance `ffmpeg -i` sans sortie (code 1 normal) et renvoie les pistes ; null si rien n'est lisible. */
function probeStreams(ffmpeg, url, { userAgent = null, referer = null, spawnFn = spawn, timeoutMs = 20000 } = {}) {
  return new Promise((resolve) => {
    const a = ["-hide_banner", "-nostdin"];
    if (userAgent) a.push("-user_agent", String(userAgent).slice(0, 300));
    if (referer) a.push("-referer", String(referer).slice(0, 500));
    a.push("-i", String(url));
    let err = "";
    let done = false;
    const child = spawnFn(ffmpeg, a, { windowsHide: true, stdio: ["ignore", "ignore", "pipe"] });
    const end = (v) => { if (done) return; done = true; clearTimeout(to); try { child.kill("SIGKILL"); } catch { /* terminé */ } resolve(v); };
    const to = setTimeout(() => end(null), timeoutMs);
    child.stderr.on("data", (d) => { if (err.length < 200000) err += d.toString("utf8"); });
    child.on("error", () => end(null));
    child.on("close", () => end(/Stream #/.test(err) ? parseStreams(err) : null));
  });
}

/** GET /txi : pistes audio et sous-titres texte du média, en JSON. Rien n'est journalisé. */
async function serveStreamInfo(req, res, { ffmpeg, target, headers, probe = probeStreams }) {
  const info = await probe(ffmpeg, target.toString(), { userAgent: req.headers["x-ut-ua"] || null, referer: req.headers["x-ut-referer"] || null });
  if (!info) { res.writeHead(502, headers); return res.end(); }
  res.writeHead(200, { ...headers, "Content-Type": "application/json", "Cache-Control": "no-store" });
  res.end(JSON.stringify(info));
}

/** Arguments ffmpeg d'extraction d'un sous-titre texte en WebVTT (fonction pure, testée). */
/** Arguments ffmpeg d'extraction d'un sous-titre texte en WebVTT. `ss` : départ (reprise / conversion en cours) —
 * sans lui, l'extraction part du début du fichier : sur un MKV/MP4 où les sous-titres sont entrelacés, la position
 * demandée n'est atteinte qu'après une longue lecture ; depuis `ss`, ffmpeg y arrive en un instant. */
function subtitleArgs(url, { s = 0, ss = 0, userAgent = null, referer = null } = {}) {
  const a = ["-hide_banner", "-loglevel", "error", "-nostdin"];
  if (userAgent) a.push("-user_agent", String(userAgent).slice(0, 300));
  if (referer) a.push("-referer", String(referer).slice(0, 500));
  if (ss > 0) a.push("-ss", String(ss));
  a.push("-i", String(url), "-map", `0:s:${s}`, "-c:s", "webvtt", "-vn", "-an", "-f", "webvtt", "pipe:1");
  return a;
}

/**
 * GET /txs : un sous-titre en WebVTT, servi AU FIL de l'extraction — ffmpeg écrit petit à petit, la réponse
 * aussi. Les sous-titres sont entrelacés dans un MKV/MP4 : l'extraction complète peut prendre plusieurs minutes
 * (lecture du fichier entier) ; en servant au fil de l'eau, le lecteur affiche les premiers repères en quelques
 * secondes. Le build ffmpeg n'a pas ffprobe, l'extraction coupe à 8 Mo au-delà du dernier repère utile.
 */
function serveSubtitle(req, res, { ffmpeg, target, s, ss = 0, headers, report = () => {}, spawnFn = spawn }) {
  const child = spawnFn(ffmpeg, subtitleArgs(target.toString(), { s, ss, userAgent: req.headers["x-ut-ua"] || null, referer: req.headers["x-ut-referer"] || null }), { windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
  let started = false;
  let size = 0;
  const stop = () => { try { child.kill("SIGKILL"); } catch { /* déjà terminé */ } };
  res.on("close", stop);
  child.stderr.on("data", () => { /* ignoré : peut contenir l'URL */ });
  child.on("error", () => { report("ETRANSCODE"); if (!res.headersSent) { res.writeHead(502, headers); res.end(); } });
  child.stdout.once("data", () => {
    started = true;
    if (!res.headersSent) res.writeHead(200, { ...headers, "Content-Type": "text/vtt; charset=utf-8", "Cache-Control": "no-store" });
  });
  child.stdout.on("data", (c) => { size += c.length; if (size > 8_000_000) stop(); else if (started && !res.writableEnded) res.write(c); });
  child.on("close", (code) => {
    if (res.writableEnded) return;
    // Rien n'a été écrit (piste absente, ffmpeg en échec) : erreur à l'appelant.
    report(started ? "ETRANSCODE_SUB_TRUNCATED" : code === 0 ? "ETRANSCODE_SUB_EMPTY" : "ETRANSCODE_SUB");
    if (!res.headersSent) { res.writeHead(502, headers); res.end(); return; }
    res.end();
  });
}

/**
 * Sert une requête de conversion : ffmpeg écrit le MPEG-TS dans la réponse ; fermeture du client = arrêt de ffmpeg.
 * `headers` : en-têtes CORS du proxy. `onSpawn(stop)` : crochet appelé dès le lancement d'ffmpeg (suivi du côté proxy).
 */
function serveTranscode(req, res, { ffmpeg, target, ss, live = false, audio = 0, headers, report = () => {}, spawnFn = spawn, onSpawn }) {
  const ua = req.headers["x-ut-ua"] || null;
  const ref = req.headers["x-ut-referer"] || null;
  const child = spawnFn(ffmpeg, transcodeArgs(target.toString(), { ss, userAgent: ua, referer: ref, live, audio }), { windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
  let started = false;
  const stop = () => { try { child.kill("SIGKILL"); } catch { /* déjà terminé */ } };
  if (typeof onSpawn === "function") onSpawn(stop);
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

module.exports = { ffmpegCandidates, findFfmpeg, parseTranscodePath, transcodeArgs, parseStreams, probeStreams, serveStreamInfo, subtitleArgs, serveSubtitle, serveTranscode };
