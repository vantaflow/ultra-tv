"use strict";
// Enregistrements de programmes (processus principal).
//
// Le renderer demande « enregistre ce flux jusqu'à telle heure dans un fichier » : ce module copie les octets HTTP tels
// quels (aucun transcodage) dans un .ts du dossier choisi. Flux MPEG-TS continu : copié en direct, la connexion est rouverte
// si le serveur la coupe avant la fin. Flux HLS : la liste de lecture est relue et chaque nouveau segment ajouté au fichier.
//
// La tolérance TLS (certificats des panneaux IPTV) ne s'applique qu'aux requêtes de ce module (agent dédié), comme pour
// proxy.cjs. AUCUNE URL de flux n'est journalisée, renvoyée au renderer ni écrite ailleurs que dans la requête sortante.

const http = require("node:http");
const https = require("node:https");
const fs = require("node:fs");
const path = require("node:path");

const MAX_REDIRECTS = 5;
const HEADERS_TIMEOUT_MS = 20000;
const IDLE_TIMEOUT_MS = 30000;
/** Garde-fou : jamais plus de 8 h 10 (le renderer borne déjà à 8 h). */
const MAX_DURATION_MS = (8 * 60 + 10) * 60000;
/** Tentatives de reconnexion consécutives sans recevoir un octet avant d'abandonner. */
const MAX_DRY_RETRIES = 20;
const RETRY_DELAY_MS = 2000;
const PROGRESS_EVERY_MS = 3000;
const MAX_MANIFEST_BYTES = 2 * 1024 * 1024;
const MAX_SEGMENT_BYTES = 64 * 1024 * 1024;

// ---------------------------------------------------------------------------
// Fonctions pures
// ---------------------------------------------------------------------------

/** Nom de fichier sûr (Windows, macOS, Linux) : caractères interdits retirés, pas de point final, 120 caractères au plus. */
function sanitizeFileName(name) {
  let s = String(name == null ? "" : name).normalize("NFC");
  s = s.replace(/[\u0000-\u001f<>:"/\\|?*]+/g, " ").replace(/\s+/g, " ").trim();
  s = s.slice(0, 120).trim().replace(/[. ]+$/, "").replace(/^[. ]+/, "");
  // Noms réservés de Windows.
  if (/^(con|prn|aux|nul|com[0-9]|lpt[0-9])$/i.test(s)) s = `_${s}`;
  return s || "Ultra TV";
}

/** Chemin libre « nom.ts », sinon « nom (2).ts », « nom (3).ts »… */
function uniquePath(dir, base, ext = ".ts", exists = fs.existsSync) {
  let p = path.join(dir, `${base}${ext}`);
  for (let i = 2; exists(p) && i < 1000; i++) p = path.join(dir, `${base} (${i})${ext}`);
  return p;
}

/** `p` est-il DANS `dir` (jamais le dossier lui-même, jamais en dehors via « .. ») ? */
function isInside(dir, p) {
  if (typeof dir !== "string" || typeof p !== "string" || !dir || !p) return false;
  const rel = path.relative(path.resolve(dir), path.resolve(p));
  return !!rel && !rel.startsWith("..") && !path.isAbsolute(rel);
}

/** Fichier d'enregistrement que le renderer peut lire, révéler ou supprimer : un .ts dans un dossier connu. */
function isRecordingFile(dirs, p) {
  return typeof p === "string" && p.length < 4096 && path.extname(p).toLowerCase() === ".ts" && dirs.some((d) => isInside(d, p));
}

function isHttpUrl(u) {
  try {
    const p = new URL(u);
    return p.protocol === "http:" || p.protocol === "https:";
  } catch {
    return false;
  }
}

/** Demande de démarrage validée (types, bornes) ; message d'erreur court sinon. */
function validateJob(job, now = Date.now()) {
  if (!job || typeof job !== "object") return { error: "invalid" };
  const { id, url, endAt, name, userAgent, referer } = job;
  if (typeof id !== "string" || !/^[\w:.-]{1,200}$/.test(id)) return { error: "invalid-id" };
  if (typeof url !== "string" || url.length > 8192 || !isHttpUrl(url)) return { error: "invalid-url" };
  if (typeof endAt !== "number" || !Number.isFinite(endAt) || endAt <= now) return { error: "invalid-end" };
  const opt = (v) => (typeof v === "string" && v.length <= 1024 && !/[\r\n]/.test(v) ? v : null);
  return {
    job: { id, url, endAt: Math.min(endAt, now + MAX_DURATION_MS), name: sanitizeFileName(name), userAgent: opt(userAgent), referer: opt(referer) },
  };
}

const looksLikeManifest = (text) => /^\s*#EXTM3U/.test(text);
const isManifestType = (ct) => /mpegurl/i.test(ct || "");
const hasManifestExt = (u) => {
  try { return /\.m3u8?$/i.test(new URL(u).pathname); } catch { return false; }
};

/**
 * Liste de lecture HLS : variantes (liste maîtresse) ou segments (liste média, numérotés par EXT-X-MEDIA-SEQUENCE).
 * Les URI sont résolues par rapport à l'adresse de la liste.
 */
function parseM3u8(text, baseUrl) {
  const lines = String(text).split(/\r?\n/).map((l) => l.trim());
  const variants = [];
  const segments = [];
  let seq = 0;
  let target = 6;
  let endList = false;
  let pendingBw = null;
  let inSegment = false;
  const abs = (u) => { try { return new URL(u, baseUrl).toString(); } catch { return null; } };
  for (const l of lines) {
    if (!l) continue;
    if (l.startsWith("#EXT-X-STREAM-INF")) { const m = /BANDWIDTH=(\d+)/i.exec(l); pendingBw = m ? Number(m[1]) : 0; continue; }
    if (l.startsWith("#EXT-X-MEDIA-SEQUENCE:")) { seq = Number(l.split(":")[1]) || 0; continue; }
    if (l.startsWith("#EXT-X-TARGETDURATION:")) { target = Number(l.split(":")[1]) || target; continue; }
    if (l.startsWith("#EXT-X-ENDLIST")) { endList = true; continue; }
    if (l.startsWith("#EXTINF")) { inSegment = true; continue; }
    if (l.startsWith("#")) continue;
    const u = abs(l);
    if (!u) continue;
    if (pendingBw !== null) { variants.push({ bandwidth: pendingBw, uri: u }); pendingBw = null; }
    else if (inSegment) { segments.push({ seq: seq + segments.length, uri: u }); inSegment = false; }
  }
  return { master: variants.length > 0, variants, segments, targetDuration: target, endList };
}

/** Variante enregistrée : la meilleure (débit le plus élevé). */
const pickVariant = (variants) => variants.reduce((best, v) => (!best || v.bandwidth > best.bandwidth ? v : best), null);

// ---------------------------------------------------------------------------
// Enregistreur
// ---------------------------------------------------------------------------

/**
 * `onEvent({ type: "progress" | "done" | "error", id, bytes, path?, reason?, code? })`. `getDir()` : dossier de destination
 * (créé au besoin). `request` : injection pour les tests.
 */
function createRecorder({ getDir, lookup, onEvent, now = () => Date.now(), request = null, retryDelayMs = RETRY_DELAY_MS } = {}) {
  const emit = typeof onEvent === "function" ? onEvent : () => {};
  const dnsOpt = typeof lookup === "function" ? { lookup } : {};
  const httpsAgent = new https.Agent({ keepAlive: true, rejectUnauthorized: false, maxSockets: 16, ...dnsOpt });
  const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 16, ...dnsOpt });
  /** id -> tâche en cours. */
  const jobs = new Map();
  /** Derniers résultats (pour un renderer rechargé pendant la fin d'un enregistrement). */
  const finished = new Map();

  function requestOnce(target, headers, job) {
    if (request) return request(target, headers, job);
    return new Promise((resolve, reject) => {
      const lib = target.protocol === "https:" ? https : http;
      const opts = { method: "GET", headers, agent: target.protocol === "https:" ? httpsAgent : httpAgent, timeout: HEADERS_TIMEOUT_MS };
      if (target.username) opts.auth = `${decodeURIComponent(target.username)}:${decodeURIComponent(target.password)}`;
      const req = lib.request(target, opts, (res) => {
        try { if (res.socket) res.socket.setTimeout(IDLE_TIMEOUT_MS); } catch { /* socket fermé */ }
        resolve(res);
      });
      job.current = req;
      req.on("timeout", () => req.destroy(Object.assign(new Error("timeout"), { code: "ETIMEDOUT" })));
      req.on("error", reject);
      req.end();
    });
  }

  async function get(url, job) {
    let current = new URL(url);
    const headers = { "accept-encoding": "identity" };
    if (job.userAgent) headers["user-agent"] = job.userAgent;
    if (job.referer) headers.referer = job.referer;
    for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
      if (job.stopped) throw Object.assign(new Error("stopped"), { code: "STOPPED" });
      const res = await requestOnce(current, headers, job);
      const status = res.statusCode || 0;
      const loc = res.headers && res.headers.location;
      if ([301, 302, 303, 307, 308].includes(status) && loc) {
        res.resume();
        const next = new URL(loc, current);
        if (next.protocol !== "http:" && next.protocol !== "https:") throw Object.assign(new Error("bad-redirect"), { code: "BADREDIRECT" });
        current = next;
        continue;
      }
      return { res, finalUrl: current.toString() };
    }
    throw Object.assign(new Error("too-many-redirects"), { code: "TOOMANYREDIRECTS" });
  }

  function readAll(res, max) {
    return new Promise((resolve, reject) => {
      const chunks = [];
      let n = 0;
      res.on("data", (c) => {
        n += c.length;
        if (n > max) { res.destroy(); reject(Object.assign(new Error("too-big"), { code: "TOOBIG" })); return; }
        chunks.push(c);
      });
      res.on("end", () => resolve(Buffer.concat(chunks)));
      res.on("error", reject);
      res.on("close", () => { if (!res.complete) reject(Object.assign(new Error("closed"), { code: "ECONNRESET" })); });
    });
  }

  const sleep = (ms, job) => new Promise((r) => {
    const t = setTimeout(r, ms);
    job.wake = () => { clearTimeout(t); r(); };
  });

  function write(job, buf) {
    if (!buf || !buf.length) return Promise.resolve();
    job.bytes += buf.length;
    job.dry = 0;
    maybeProgress(job);
    return new Promise((resolve, reject) => job.out.write(buf, (e) => (e ? reject(Object.assign(e, { code: e.code || "DISK" })) : resolve())));
  }

  function maybeProgress(job) {
    const t = now();
    if (t - job.lastProgress < PROGRESS_EVERY_MS) return;
    job.lastProgress = t;
    emit({ type: "progress", id: job.id, bytes: job.bytes });
  }

  /** Copie du corps d'une réponse MPEG-TS jusqu'à la fin du créneau (ou coupure du serveur). */
  function pipeBody(job, res) {
    return new Promise((resolve) => {
      let done = false;
      const finish = () => { if (done) return; done = true; clearTimeout(timer); resolve(); };
      const timer = setTimeout(() => { res.destroy(); finish(); }, Math.max(0, job.endAt - now()));
      job.wake = () => { res.destroy(); finish(); };
      res.on("data", (c) => {
        job.bytes += c.length;
        job.dry = 0;
        maybeProgress(job);
        if (!job.out.write(c)) { res.pause(); job.out.once("drain", () => res.resume()); }
      });
      res.on("end", finish);
      res.on("close", finish);
      res.on("error", finish);
    });
  }

  async function recordHls(job, firstText, manifestUrl) {
    let text = firstText;
    let url = manifestUrl;
    const seen = new Set();
    let lastSeq = -1;
    while (!job.stopped && now() < job.endAt) {
      let pl = parseM3u8(text, url);
      if (pl.master) {
        const v = pickVariant(pl.variants);
        if (!v) throw Object.assign(new Error("no-variant"), { code: "HLS" });
        url = v.uri;
        const { res } = await get(url, job);
        if ((res.statusCode || 0) >= 400) { res.resume(); throw Object.assign(new Error("status"), { code: `HTTP_${res.statusCode}` }); }
        text = (await readAll(res, MAX_MANIFEST_BYTES)).toString("utf8");
        pl = parseM3u8(text, url);
        if (pl.master) throw Object.assign(new Error("nested-master"), { code: "HLS" });
      }
      for (const s of pl.segments) {
        if (job.stopped || now() >= job.endAt) break;
        if (s.seq <= lastSeq || seen.has(s.uri)) continue;
        // Première lecture d'une liste en direct : on ne remonte pas tout l'historique, seulement les 3 derniers segments.
        if (lastSeq < 0 && !pl.endList && s.seq < pl.segments[pl.segments.length - 1].seq - 2) continue;
        const { res } = await get(s.uri, job);
        if ((res.statusCode || 0) >= 400) { res.resume(); job.dry++; continue; }
        await write(job, await readAll(res, MAX_SEGMENT_BYTES));
        lastSeq = s.seq;
        seen.add(s.uri);
        if (seen.size > 2000) seen.clear();
      }
      if (pl.endList) break;
      await sleep(Math.min(6000, Math.max(1000, (pl.targetDuration * 1000) / 2)), job);
      if (job.stopped || now() >= job.endAt) break;
      const { res } = await get(url, job);
      if ((res.statusCode || 0) >= 400) { res.resume(); job.dry++; if (job.dry > MAX_DRY_RETRIES) throw Object.assign(new Error("status"), { code: `HTTP_${res.statusCode}` }); continue; }
      text = (await readAll(res, MAX_MANIFEST_BYTES)).toString("utf8");
    }
  }

  async function run(job) {
    let lastCode = "NETWORK";
    while (!job.stopped && now() < job.endAt) {
      try {
        const { res, finalUrl } = await get(job.url, job);
        const status = res.statusCode || 0;
        if (status >= 400) {
          res.resume();
          lastCode = `HTTP_${status}`;
          throw Object.assign(new Error("status"), { code: lastCode });
        }
        const ct = String((res.headers && res.headers["content-type"]) || "");
        if (isManifestType(ct) || hasManifestExt(finalUrl)) {
          const text = (await readAll(res, MAX_MANIFEST_BYTES)).toString("utf8");
          if (!looksLikeManifest(text)) throw Object.assign(new Error("not-hls"), { code: "HLS" });
          await recordHls(job, text, finalUrl);
          if (job.stopped || now() >= job.endAt) break;
        } else {
          await pipeBody(job, res);
          if (job.stopped || now() >= job.endAt) break;
          job.dry++;
        }
      } catch (e) {
        if (job.stopped) break;
        if (e && e.code === "DISK") throw e;
        if (e && typeof e.code === "string" && e.code !== "STOPPED") lastCode = e.code;
        job.dry++;
      }
      if (job.dry > MAX_DRY_RETRIES) throw Object.assign(new Error("gave-up"), { code: lastCode });
      // Coupure avant la fin : on rouvre la même adresse (direct coupé par le serveur, réseau).
      await sleep(retryDelayMs, job);
    }
  }

  function start(raw) {
    const v = validateJob(raw, now());
    if (v.error) return { ok: false, error: v.error };
    const spec = v.job;
    if (jobs.has(spec.id)) return { ok: true, path: jobs.get(spec.id).path, already: true };
    let dir;
    try {
      dir = getDir();
      fs.mkdirSync(dir, { recursive: true });
    } catch {
      return { ok: false, error: "dir" };
    }
    const file = uniquePath(dir, spec.name, ".ts");
    let out;
    try {
      out = fs.createWriteStream(file, { flags: "a" });
    } catch {
      return { ok: false, error: "dir" };
    }
    const job = { ...spec, path: file, out, bytes: 0, dry: 0, stopped: false, lastProgress: now(), current: null, wake: null };
    out.on("error", () => { job.diskError = true; stopJob(job); });
    jobs.set(job.id, job);
    finished.delete(job.id);
    const end = (type, extra) => {
      jobs.delete(job.id);
      out.end(() => {
        let bytes = job.bytes;
        try { bytes = fs.statSync(file).size; } catch { /* fichier absent */ }
        if (bytes === 0) { try { fs.unlinkSync(file); } catch { /* déjà absent */ } }
        const ev = bytes === 0 && type === "done"
          ? { type: "error", id: job.id, code: "EMPTY", bytes: 0 }
          : { type, id: job.id, path: bytes > 0 ? file : undefined, bytes, ...extra };
        finished.set(job.id, ev);
        if (finished.size > 50) finished.delete(finished.keys().next().value);
        emit(ev);
      });
    };
    run(job).then(
      () => end(job.diskError ? "error" : "done", job.diskError ? { code: "DISK" } : { reason: job.stopped ? "stopped" : "end" }),
      (e) => end("error", { code: job.diskError ? "DISK" : (e && typeof e.code === "string" ? e.code : "NETWORK") }),
    );
    return { ok: true, path: file };
  }

  function stopJob(job) {
    job.stopped = true;
    try { if (job.current) job.current.destroy(); } catch { /* déjà fermée */ }
    if (job.wake) job.wake();
  }

  function stop(id) {
    const job = jobs.get(id);
    if (!job) return false;
    stopJob(job);
    return true;
  }

  return {
    start,
    stop,
    stopAll: () => { for (const j of jobs.values()) stopJob(j); },
    active: () => jobs.size,
    list: () => ({
      active: [...jobs.values()].map((j) => ({ id: j.id, bytes: j.bytes, path: j.path })),
      finished: [...finished.values()],
    }),
  };
}

module.exports = {
  sanitizeFileName, uniquePath, isInside, isRecordingFile, validateJob, parseM3u8, pickVariant, createRecorder, MAX_DURATION_MS,
};
