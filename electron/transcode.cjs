"use strict";
// Conversion audio à la volée pour le bureau : Chromium (donc Electron) ne décode ni l'AC-3, ni l'E-AC-3, ni le DTS.
// Quand le lecteur constate « image sans son », il redemande le flux via :
//
//   http://127.0.0.1:<port>/<token>/tx/<base64url(urlCible)>?ss=<secondes>
//
// et ce module lance ffmpeg : vidéo copiée telle quelle (ou convertie en H.264 si Chromium ne sait pas la décoder), audio converti en AAC stéréo, sortie MPEG-TS en flux.
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
 * Premier ffmpeg avec un encodeur H.264 : { path, encoder } ou null. Le ffmpeg embarqué (LGPL, minimal) n'en a pas toujours
 * (aucun encodeur logiciel libre sous Linux) : on cherche alors celui du système (apt / dnf install ffmpeg : libx264).
 */
function findVideoFfmpeg(opts = {}, run = spawnSync) {
  for (const c of ffmpegCandidates(opts)) {
    if (path.isAbsolute(c) && !fs.existsSync(c)) continue;
    try {
      const r = run(c, ["-hide_banner", "-encoders"], { timeout: 8000, windowsHide: true, encoding: "utf8" });
      if (!r || r.status !== 0) continue;
      const encoder = pickVideoEncoder(String(r.stdout || ""), opts.platform || process.platform);
      if (encoder) return { path: c, encoder };
    } catch { /* suivant */ }
  }
  return null;
}

/**
 * Analyse /<token>/tx/<b64>?ss=…&a=…&live=1 (conversion), /<token>/txi/<b64> (liste des pistes) et
 * /<token>/txs/<b64>?s=… (sous-titre en WebVTT), /<token>/txd/<b64>?hevc=0|1 (diagnostic d'un flux qui ne s'affiche pas).
 * `v=h264` demande la conversion vidéo. Retourne { kind, token, b64, ss, live, a, s, video, hevc } ou null.
 */
function parseTranscodePath(pathname, search) {
  const segs = String(pathname).split("/");
  const kinds = { tx: "tx", txi: "info", txs: "sub", txd: "diag" };
  if (segs.length !== 4 || segs[0] !== "" || !Object.hasOwn(kinds, segs[2])) return null;
  const q = new URLSearchParams(search || "");
  const ssRaw = q.get("ss");
  const ss = ssRaw && /^\d{1,6}(\.\d{1,3})?$/.test(ssRaw) ? Number(ssRaw) : 0;
  const idx = (k) => { const v = q.get(k); return v && /^\d{1,2}$/.test(v) ? Number(v) : 0; };
  return { kind: kinds[segs[2]], token: segs[1], b64: segs[3], ss, live: q.get("live") === "1", a: idx("a"), s: idx("s"), video: q.get("v") === "h264" ? "h264" : "copy", hevc: q.get("hevc") === "1" };
}

/** User-Agent de repli (celui de VLC) quand la source n'en impose pas : « Lavf » est souvent refusé par les fournisseurs. */
const FALLBACK_UA = "VLC/3.0.20 LibVLC/3.0.20";

/** Hauteur maximale de la vidéo convertie. */
const MAX_HEIGHT = 1080;

/** Encodeurs H.264 acceptés, par ordre de préférence (libx264 : ffmpeg du système ; les autres : plateformes). */
const ENCODER_PREFERENCE = ["libx264", "libopenh264", "h264_videotoolbox", "h264_mf"];

/** Premier encodeur H.264 utilisable d'après la sortie de `ffmpeg -encoders` (fonction pure, testée) ; null sinon. */
function pickVideoEncoder(listing, platform = process.platform) {
  const have = new Set();
  for (const line of String(listing).split(/\r?\n/)) {
    const m = /^\s*V[.\w]{5}\s+(\S+)/.exec(line);
    if (m) have.add(m[1]);
  }
  const ok = (e) => (e === "h264_videotoolbox" ? platform === "darwin" : e === "h264_mf" ? platform === "win32" : true);
  return ENCODER_PREFERENCE.find((e) => have.has(e) && ok(e)) || null;
}

/** Arguments de conversion vidéo en H.264 (fonction pure, testée) : yuv420p, 1080p maximum, latence minimale en direct. */
function videoEncodeArgs(encoder = "libx264", { live = false } = {}) {
  const out = ["-vf", `yadif=deint=interlaced,scale=-2:'min(${MAX_HEIGHT},ih)',format=yuv420p`, "-c:v", encoder];
  if (encoder === "libx264") {
    out.push("-preset", "veryfast");
    if (live) out.push("-tune", "zerolatency");
    out.push("-crf", "23", "-maxrate", "8M", "-bufsize", "16M");
  } else {
    out.push("-b:v", "5M");
  }
  out.push("-pix_fmt", "yuv420p", "-g", "50", "-bf", "0");
  return out;
}

/** Arguments ffmpeg (fonction pure, testée). `video` : "copy" (défaut) ou "h264" (vidéo illisible par Chromium). */
function transcodeArgs(url, { ss = 0, userAgent = null, referer = null, live = false, audio = 0, video = "copy", encoder = "libx264" } = {}) {
  const a = ["-hide_banner", "-loglevel", "error", "-nostdin"];
  a.push("-user_agent", String(userAgent || FALLBACK_UA).slice(0, 300));
  if (referer) a.push("-referer", String(referer).slice(0, 500));
  // Reconnexion seulement en direct : sur un fichier sans Content-Length, ffmpeg relirait le film en boucle.
  if (live) a.push("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5");
  // -ss AVANT -i : recherche rapide côté source (reprise / saut dans un film).
  if (ss > 0) a.push("-ss", String(ss));
  a.push("-i", String(url), "-map", "0:v:0?", "-map", `0:a:${audio}?`);
  if (video === "h264") a.push(...videoEncodeArgs(encoder, { live }));
  else a.push("-c:v", "copy");
  a.push(
    "-c:a", "aac", "-b:a", "192k", "-ac", "2",
    "-sn", "-dn", "-f", "mpegts", "-muxdelay", "0", "pipe:1",
  );
  return a;
}

// --- diagnostic d'un flux qui ne s'affiche pas : vidéo, statut HTTP ---

/** Vidéo du flux d'après `ffmpeg -i` : { codec, profile, pix, w, h } ou null (fonction pure, testée). */
function parseVideo(stderr) {
  for (const line of String(stderr).split(/\r?\n/)) {
    if (/attached pic/i.test(line)) continue;
    const m = /Stream #\d+:\d+[^:]*: Video: ([A-Za-z0-9_]+)(?: \(([^)]*)\))?/.exec(line);
    if (!m) continue;
    const rest = line.slice(m.index + m[0].length);
    const pix = /,\s*((?:yuvj?|nv|gray|rgb|bgr|argb|bgra|p0|xyz)[a-z0-9]*)/i.exec(rest);
    const size = /\b(\d{2,5})x(\d{2,5})\b/.exec(rest);
    return { codec: m[1].toLowerCase(), profile: m[2] || "", pix: pix ? pix[1].toLowerCase() : "", w: size ? Number(size[1]) : 0, h: size ? Number(size[2]) : 0 };
  }
  return null;
}

const NATIVE_VIDEO = new Set(["h264", "vp8", "vp9", "av1", "theora"]);

/**
 * Vrai si Chromium ne peut pas décoder cette vidéo (fonction pure, testée) : MPEG-2, VC-1, MPEG-4 partie 2…, HEVC sans décodeur
 * de plateforme (`hevc` : le renderer sait si le décodeur existe), H.264 High 10 / 4:2:2 / 4:4:4.
 */
function needsVideoConversion(video, { hevc = false } = {}) {
  if (!video || !video.codec) return false;
  if (video.codec === "hevc") return !hevc;
  if (!NATIVE_VIDEO.has(video.codec)) return true;
  if (video.codec === "h264") {
    if (/high\s*(10|4:?2:?2|4:?4:?4)|hi(10|422|444)|predictive|intra/i.test(video.profile || "")) return true;
    if (/(10|12|16)(le|be)$|422|444|p010/.test(video.pix || "")) return true;
  }
  return false;
}

/** Durée du média en secondes d'après « Duration: 01:32:10.50 » ; 0 si inconnue (direct : N/A). */
function parseDuration(stderr) {
  const m = /Duration:\s*(\d+):(\d{2}):(\d{2})(?:\.(\d+))?/.exec(String(stderr));
  return m ? Number(m[1]) * 3600 + Number(m[2]) * 60 + Number(m[3]) + (m[4] ? Number(`0.${m[4]}`) : 0) : 0;
}

/** Classe la sortie d'erreur de `ffmpeg -i` : { reason: "ok"|"http"|"network"|"other", status?, video?, hasAudio? } (fonction pure, testée). */
function classifyProbe(stderr, { hevc = false } = {}) {
  const err = String(stderr || "");
  const video = parseVideo(err);
  const hasAudio = /Stream #\d+:\d+[^:]*: Audio:/.test(err);
  if (video || hasAudio) return { reason: "ok", video, hasAudio, convert: needsVideoConversion(video, { hevc }), duration: parseDuration(err) };
  const h = /(?:HTTP error|Server returned|returned)\s+(\d{3})\b/i.exec(err);
  if (h && Number(h[1]) >= 400) return { reason: "http", status: Number(h[1]) };
  if (/Server returned [45]XX/i.test(err)) return { reason: "http", status: null };
  if (/Connection (?:refused|reset|timed out)|timed out|Name or service not known|Could not resolve|No route to host|Network is unreachable|getaddrinfo|Failed to resolve|Input\/output error|Immediate exit requested/i.test(err)) return { reason: "network" };
  return { reason: "other" };
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

/** Lance `ffmpeg -i` sans sortie (code 1 normal) et renvoie sa sortie d'erreur (peut contenir l'URL : ne jamais la journaliser) ; null si ffmpeg ne démarre pas. */
function probeRaw(ffmpeg, url, { userAgent = null, referer = null, spawnFn = spawn, timeoutMs = 20000 } = {}) {
  return new Promise((resolve) => {
    const a = ["-hide_banner", "-nostdin"];
    a.push("-user_agent", String(userAgent || FALLBACK_UA).slice(0, 300));
    if (referer) a.push("-referer", String(referer).slice(0, 500));
    a.push("-i", String(url));
    let err = "";
    let done = false;
    const child = spawnFn(ffmpeg, a, { windowsHide: true, stdio: ["ignore", "ignore", "pipe"] });
    const end = (v) => { if (done) return; done = true; clearTimeout(to); try { child.kill("SIGKILL"); } catch { /* terminé */ } resolve(v); };
    const to = setTimeout(() => end(err || null), timeoutMs);
    child.stderr.on("data", (d) => { if (err.length < 200000) err += d.toString("utf8"); });
    child.on("error", () => end(null));
    child.on("close", () => end(err));
  });
}

/** Pistes audio / sous-titres du média ; null si rien n'est lisible. */
async function probeStreams(ffmpeg, url, opts = {}) {
  const err = await probeRaw(ffmpeg, url, opts);
  return err && /Stream #/.test(err) ? parseStreams(err) : null;
}

/** Diagnostic d'un flux : vidéo, statut HTTP, besoin de conversion (voir classifyProbe). Ne renvoie jamais l'URL. */
async function diagnoseStream(ffmpeg, url, { hevc = false, timeoutMs = 15000, ...opts } = {}) {
  const err = await probeRaw(ffmpeg, url, { ...opts, timeoutMs });
  return classifyProbe(err, { hevc });
}

/** GET /txd : diagnostic en JSON, plus la disponibilité de la conversion vidéo. */
async function serveDiagnosis(req, res, { ffmpeg, target, headers, hevc = false, canEncode = false, diagnose = diagnoseStream }) {
  const d = await diagnose(ffmpeg, target.toString(), { hevc, userAgent: req.headers["x-ut-ua"] || null, referer: req.headers["x-ut-referer"] || null });
  res.writeHead(200, { ...headers, "Content-Type": "application/json", "Cache-Control": "no-store" });
  res.end(JSON.stringify({ ...d, canEncode: !!canEncode }));
}

/** GET /txi : pistes audio et sous-titres texte du média, en JSON. Rien n'est journalisé. */
async function serveStreamInfo(req, res, { ffmpeg, target, headers, probe = probeStreams }) {
  const info = await probe(ffmpeg, target.toString(), { userAgent: req.headers["x-ut-ua"] || null, referer: req.headers["x-ut-referer"] || null });
  if (!info) { res.writeHead(502, headers); return res.end(); }
  res.writeHead(200, { ...headers, "Content-Type": "application/json", "Cache-Control": "no-store" });
  res.end(JSON.stringify(info));
}

/** Arguments ffmpeg d'extraction d'un sous-titre texte en WebVTT (fonction pure, testée). */
function subtitleArgs(url, { s = 0, userAgent = null, referer = null } = {}) {
  const a = ["-hide_banner", "-loglevel", "error", "-nostdin"];
  if (userAgent) a.push("-user_agent", String(userAgent).slice(0, 300));
  if (referer) a.push("-referer", String(referer).slice(0, 500));
  a.push("-i", String(url), "-map", `0:s:${s}`, "-c:s", "webvtt", "-vn", "-an", "-f", "webvtt", "pipe:1");
  return a;
}

/** GET /txs : un sous-titre en WebVTT. Le fichier est lu en entier (les sous-titres MKV sont entrelacés) : à la demande seulement. */
function serveSubtitle(req, res, { ffmpeg, target, s, headers, report = () => {}, spawnFn = spawn }) {
  const child = spawnFn(ffmpeg, subtitleArgs(target.toString(), { s, userAgent: req.headers["x-ut-ua"] || null, referer: req.headers["x-ut-referer"] || null }), { windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
  const chunks = [];
  let size = 0;
  const stop = () => { try { child.kill("SIGKILL"); } catch { /* déjà terminé */ } };
  res.on("close", stop);
  child.stderr.on("data", () => { /* ignoré : peut contenir l'URL */ });
  child.stdout.on("data", (c) => { size += c.length; if (size > 8_000_000) stop(); else chunks.push(c); });
  child.on("error", () => { report("ETRANSCODE"); if (!res.headersSent) { res.writeHead(502, headers); res.end(); } });
  child.on("close", (code) => {
    if (res.headersSent) return;
    if (code === 0 && size > 0 && size <= 8_000_000) {
      res.writeHead(200, { ...headers, "Content-Type": "text/vtt; charset=utf-8", "Cache-Control": "no-store" });
      res.end(Buffer.concat(chunks));
    } else { report("ETRANSCODE_SUB"); res.writeHead(502, headers); res.end(); }
  });
}

/**
 * Sert une requête de conversion : ffmpeg écrit le MPEG-TS dans la réponse ; fermeture du client = arrêt de ffmpeg.
 * `headers` : en-têtes CORS du proxy. `onSpawn(stop)` : crochet appelé dès le lancement d'ffmpeg (suivi du côté proxy).
 */
function serveTranscode(req, res, { ffmpeg, target, ss, live = false, audio = 0, video = "copy", encoder = "libx264", headers, report = () => {}, spawnFn = spawn, onSpawn }) {
  const ua = req.headers["x-ut-ua"] || null;
  const ref = req.headers["x-ut-referer"] || null;
  const child = spawnFn(ffmpeg, transcodeArgs(target.toString(), { ss, userAgent: ua, referer: ref, live, audio, video, encoder }), { windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
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

module.exports = { parseDuration, ffmpegCandidates, findFfmpeg, findVideoFfmpeg, pickVideoEncoder, videoEncodeArgs, parseVideo, needsVideoConversion, classifyProbe, probeRaw, diagnoseStream, serveDiagnosis, parseTranscodePath, transcodeArgs, parseStreams, probeStreams, serveStreamInfo, subtitleArgs, serveSubtitle, serveTranscode };
