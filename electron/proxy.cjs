"use strict";
// Proxy loopback d'Ultra TV.
//
// Serveur HTTP lie a 127.0.0.1 sur un port aleatoire. Chaque requete doit porter
// un jeton aleatoire (genere au demarrage) dans le chemin :
//
//   http://127.0.0.1:<port>/<token>/<base64url(urlCible)>
//
// Le proxy relaie GET/HEAD/POST vers la cible http(s), stream le corps, suit les
// redirections cote main, reecrit les manifestes HLS et ajoute les en-tetes CORS.
// La tolerance TLS (certificats invalides/expires, frequents sur les panneaux
// IPTV) ne s'applique QU'AUX requetes sortantes de ce module via un agent dedie.
//
// Aucune URL cible, aucun identifiant n'est jamais journalise : seuls des codes.

const http = require("node:http");
const https = require("node:https");
const crypto = require("node:crypto");
const zlib = require("node:zlib");
const { pipeline } = require("node:stream");

const MAX_MANIFEST_BYTES = 2 * 1024 * 1024;
const MAX_POST_BYTES = 2 * 1024 * 1024;
const MAX_REDIRECTS = 5;
const UPSTREAM_TIMEOUT_MS = 30000;
// Les pages d'API et le guide XMLTV sont générés à la demande : certains panneaux mettent plus de 30 s avant le premier octet.
const SLOW_HEADERS_TIMEOUT_MS = 150000;
// Zapping : temps maximal d'attente de la fermeture de l'ancien flux avant d'ouvrir le nouveau.
const UPSTREAM_RELEASE_WAIT_MS = 400;
const LOOPBACK_HOSTS = new Set(["127.0.0.1", "localhost", "::1", "[::1]", "0.0.0.0", "[::]"]);

// ---------------------------------------------------------------------------
// Fonctions pures
// ---------------------------------------------------------------------------

function encodeTarget(url) {
  return Buffer.from(String(url), "utf8").toString("base64url");
}

/** Decode un segment base64url en URL http(s). Retourne null si invalide. */
function decodeTarget(segment) {
  if (typeof segment !== "string" || segment.length === 0 || segment.length > 8192) return null;
  if (!/^[A-Za-z0-9_-]+$/.test(segment)) return null;
  let parsed;
  try {
    parsed = new URL(Buffer.from(segment, "base64url").toString("utf8"));
  } catch {
    return null;
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") return null;
  return parsed;
}

/** `base` se termine par "/" (ex. http://127.0.0.1:1234/). */
function buildProxyUrl(base, token, targetUrl) {
  return `${base}${token}/${encodeTarget(targetUrl)}`;
}

function tokenMatches(expected, given) {
  if (typeof given !== "string") return false;
  const a = crypto.createHash("sha256").update(expected).digest();
  const b = crypto.createHash("sha256").update(given).digest();
  return crypto.timingSafeEqual(a, b);
}

/**
 * Analyse le chemin d'une requete. Retourne { target: URL } ou { error: code }.
 */
function parseProxyPath(pathname, token) {
  const segs = pathname.split("/");
  // ["", token, b64]
  if (segs.length !== 3 || segs[0] !== "") return { error: 404 };
  if (!tokenMatches(token, segs[1])) return { error: 403 };
  const target = decodeTarget(segs[2]);
  if (!target) return { error: 400 };
  return { target };
}

/** Pages générées à la demande (API Xtream, guide, liste M3U) : délai d'en-têtes long. */
function isSlowEndpoint(url) {
  return /\/(player_api|xmltv|get)\.php$/i.test(url.pathname);
}

/** Flux « direct » : segments/manifestes HLS, flux TS, chemin /live/. Seuls eux sont coupés sur inactivité. */
function isLivePath(url) {
  return /^\/live\//i.test(url.pathname) || /\.(ts|m3u8?)$/i.test(url.pathname);
}

/**
 * Ouverture d'un flux Xtream (/live/, /movie/, /series/) : un compte à connexion unique n'en tolère qu'une.
 * Les manifestes .m3u8 sont exclus : un rechargement de playlist ne doit jamais couper un segment en cours.
 */
function isStreamOpen(url) {
  return /^\/(live|movie|series)\//i.test(url.pathname) && !/\.m3u8?$/i.test(url.pathname);
}

/** Seul un flux direct brut est « tenu » ouvert et remplacé au zapping (les VOD font des Range parallèles légitimes). */
function isTrackedLive(url) {
  return /^\/live\//i.test(url.pathname) && isStreamOpen(url);
}

/**
 * Délais d'une requête sortante : `headersMs` = attente de la réponse ; `bodyMs` = inactivité tolérée pendant
 * le corps (0 = aucune : une VOD en pause ne doit pas être coupée, le client qui part ferme déjà l'amont).
 */
function timeoutPolicy(url) {
  return {
    headersMs: isSlowEndpoint(url) ? SLOW_HEADERS_TIMEOUT_MS : UPSTREAM_TIMEOUT_MS,
    bodyMs: isLivePath(url) ? UPSTREAM_TIMEOUT_MS : 0,
  };
}

/** Vrai si la cible pointe sur le proxy lui-meme (boucle). */
function isSelfTarget(targetUrl, ownPort) {
  const host = targetUrl.hostname.toLowerCase();
  const port = Number(targetUrl.port || (targetUrl.protocol === "https:" ? 443 : 80));
  return LOOPBACK_HOSTS.has(host) && port === ownPort;
}

function looksLikeHlsManifest(text) {
  const t = text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
  return t.startsWith("#EXTM3U") && t.includes("#EXT-X-");
}

/**
 * Reecrit un manifeste HLS : chaque URL (lignes d'URL et URI="..." des balises)
 * est resolue contre `baseUrl` puis enveloppee par `wrap(absoluteUrl)`.
 * Les URI non http(s) (data:, skd:...) sont laissees telles quelles.
 */
function rewriteManifest(text, baseUrl, wrap) {
  const resolve = (ref) => {
    try {
      const abs = new URL(ref, baseUrl);
      if (abs.protocol !== "http:" && abs.protocol !== "https:") return null;
      return abs.toString();
    } catch {
      return null;
    }
  };
  return text
    .split(/\r?\n/)
    .map((line) => {
      const trimmed = line.trim();
      if (trimmed === "") return line;
      if (trimmed.startsWith("#")) {
        return line.replace(/(URI=)"([^"]*)"/g, (whole, key, ref) => {
          if (ref === "") return whole;
          const abs = resolve(ref);
          return abs ? `${key}"${wrap(abs)}"` : whole;
        });
      }
      const abs = resolve(trimmed);
      return abs ? wrap(abs) : line;
    })
    .join("\n");
}

// ---------------------------------------------------------------------------
// Serveur
// ---------------------------------------------------------------------------

const FORWARD_REQ_HEADERS = [
  "range",
  "if-range",
  "if-none-match",
  "if-modified-since",
  "accept",
  "accept-language",
  "content-type",
];
const PASS_RES_HEADERS = [
  "content-type",
  "content-length",
  "content-range",
  "accept-ranges",
  "content-encoding",
  "etag",
  "last-modified",
  "cache-control",
  "expires",
];

function isManifestType(ct) {
  return /mpegurl/i.test(ct);
}
function isGenericType(ct) {
  return ct === "" || /^(text\/plain|application\/octet-stream|binary\/octet-stream)/i.test(ct);
}
function hasManifestExt(url) {
  return /\.m3u8?$/i.test(url.pathname);
}

/** Lit au plus `max` octets, puis remet le flux en pause avec les octets non consommes. */
function bufferUpTo(stream, max) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    const cleanup = () => {
      stream.off("data", onData);
      stream.off("end", onEnd);
      stream.off("error", onError);
    };
    const onData = (c) => {
      chunks.push(c);
      total += c.length;
      if (total > max) {
        stream.pause();
        cleanup();
        stream.unshift(Buffer.concat(chunks));
        resolve({ overflow: true });
      }
    };
    const onEnd = () => {
      cleanup();
      resolve({ overflow: false, body: Buffer.concat(chunks) });
    };
    const onError = (e) => {
      cleanup();
      reject(e);
    };
    stream.on("data", onData);
    stream.once("end", onEnd);
    stream.once("error", onError);
    stream.resume(); // le flux peut avoir ete mis en pause par peekBytes
  });
}

/** Lit `n` octets (ou fin de flux) sans les consommer definitivement. */
function peekBytes(stream, n) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    const done = (ended) => {
      stream.off("data", onData);
      stream.off("end", onEnd);
      stream.off("error", onError);
      const buf = Buffer.concat(chunks);
      if (!ended) {
        stream.pause();
        if (buf.length) stream.unshift(buf);
      }
      resolve({ buf, ended });
    };
    const onData = (c) => {
      chunks.push(c);
      total += c.length;
      if (total >= n) done(false);
    };
    const onEnd = () => done(true);
    const onError = (e) => {
      stream.off("data", onData);
      stream.off("end", onEnd);
      reject(e);
    };
    stream.on("data", onData);
    stream.once("end", onEnd);
    stream.once("error", onError);
  });
}

function decodeBody(buf, encoding) {
  const enc = (encoding || "").toLowerCase();
  if (enc === "" || enc === "identity") return buf;
  if (enc === "gzip" || enc === "x-gzip") return zlib.gunzipSync(buf);
  if (enc === "deflate") return zlib.inflateSync(buf);
  if (enc === "br") return zlib.brotliDecompressSync(buf);
  return null;
}

function createProxyServer({ allowedOrigin, token, onError } = {}) {
  if (!allowedOrigin) throw new Error("allowedOrigin requis");
  const secret = token || crypto.randomBytes(24).toString("base64url");
  const report = typeof onError === "function" ? onError : () => {};

  // Tolerance TLS limitee a ce chemin : agent dedie, jamais de switch global.
  const httpsAgent = new https.Agent({ keepAlive: true, rejectUnauthorized: false, maxSockets: 64 });
  const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 64 });

  let port = 0;
  let base = "";
  // Flux direct en cours, par hôte : au zapping on ferme l'ancien amont (et on attend sa fermeture) avant d'ouvrir le suivant.
  const activeLive = new Map();

  function trackLive(host, state, res) {
    const entry = { state, res, closed: false, waiters: [] };
    const markClosed = () => {
      if (entry.closed) return;
      entry.closed = true;
      for (const w of entry.waiters) w();
      if (activeLive.get(host) === entry) activeLive.delete(host);
    };
    entry.markClosed = markClosed;
    state.onUpstream = (u) => u.once("close", markClosed);
    activeLive.set(host, entry);
    res.once("close", () => {
      // Client parti sans amont créé (ou déjà fermé) : rien à attendre.
      if (!state.upstream && !state.upRes) markClosed();
    });
    return entry;
  }

  /** Détruit l'amont précédent du même hôte et attend son « close » (au plus UPSTREAM_RELEASE_WAIT_MS). */
  async function releaseEntry(prev) {
    if (!prev || prev.closed) return;
    prev.state.aborted = true;
    if (prev.state.upRes) prev.state.upRes.destroy();
    if (prev.state.upstream) prev.state.upstream.destroy();
    if (!prev.state.upRes && !prev.state.upstream) prev.markClosed();
    try { prev.res.destroy(); } catch { /* déjà fermée */ }
    if (prev.closed) return;
    await new Promise((resolve) => {
      const t = setTimeout(resolve, UPSTREAM_RELEASE_WAIT_MS);
      prev.waiters.push(() => { clearTimeout(t); resolve(); });
    });
  }

  function corsHeaders(req) {
    const h = {
      "Access-Control-Allow-Origin": allowedOrigin,
      Vary: "Origin",
      "Access-Control-Allow-Headers": req.headers["access-control-request-headers"] || "*",
      "Access-Control-Allow-Methods": "GET, HEAD, POST, OPTIONS",
      "Access-Control-Expose-Headers": "Content-Length, Content-Range, Accept-Ranges",
      "Access-Control-Max-Age": "600",
    };
    if (req.headers["access-control-request-private-network"]) {
      h["Access-Control-Allow-Private-Network"] = "true";
    }
    return h;
  }

  function fail(res, req, status) {
    if (res.headersSent) {
      res.destroy();
      return;
    }
    res.writeHead(status, { ...corsHeaders(req), "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" });
    res.end(String(status));
  }

  function upstreamHeaders(req, method, bodyLength) {
    const h = {};
    for (const name of FORWARD_REQ_HEADERS) {
      if (req.headers[name]) h[name] = req.headers[name];
    }
    const ua = req.headers["x-ut-ua"] || req.headers["user-agent"];
    if (ua) h["user-agent"] = ua;
    const ref = req.headers["x-ut-referer"];
    if (ref) h["referer"] = ref;
    h["accept-encoding"] = req.headers["range"] ? "identity" : "gzip, deflate";
    if (method === "POST" && bodyLength !== undefined) h["content-length"] = String(bodyLength);
    return h;
  }

  /** Une requete sortante ; resout avec la reponse brute (sans suivre de redirection). */
  function requestOnce(target, method, headers, body, state) {
    return new Promise((resolve, reject) => {
      const lib = target.protocol === "https:" ? https : http;
      const policy = timeoutPolicy(target);
      const options = {
        method,
        headers,
        agent: target.protocol === "https:" ? httpsAgent : httpAgent,
        timeout: policy.headersMs,
      };
      if (target.username) {
        options.auth = `${decodeURIComponent(target.username)}:${decodeURIComponent(target.password)}`;
      }
      const up = lib.request(target, options, (res) => {
        if (state.onUpstream) state.onUpstream(res);
        // Réponse reçue : le délai d'en-têtes ne vaut plus, on applique celui du corps (aucun pour une VOD).
        try { if (res.socket) res.socket.setTimeout(policy.bodyMs); } catch { /* socket déjà fermé */ }
        resolve(res);
      });
      state.upstream = up;
      if (state.onUpstream) state.onUpstream(up);
      up.on("timeout", () => up.destroy(Object.assign(new Error("timeout"), { code: "ETIMEDOUT" })));
      up.on("error", reject);
      if (body && body.length) up.write(body);
      up.end();
    });
  }

  async function fetchFollowing(target, method, headers, body, state) {
    let current = target;
    let m = method;
    let b = body;
    let h = { ...headers };
    for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
      if (state.aborted) throw Object.assign(new Error("aborted"), { code: "ABORTED" });
      if (isSelfTarget(current, port)) throw Object.assign(new Error("self"), { code: "SELF" });
      const up = await requestOnce(current, m, h, b, state);
      const status = up.statusCode || 0;
      const loc = up.headers.location;
      if ([301, 302, 303, 307, 308].includes(status) && loc) {
        up.resume();
        let next;
        try {
          next = new URL(loc, current);
        } catch {
          throw Object.assign(new Error("bad-redirect"), { code: "BADREDIRECT" });
        }
        if (next.protocol !== "http:" && next.protocol !== "https:") {
          throw Object.assign(new Error("bad-redirect"), { code: "BADREDIRECT" });
        }
        if (status === 303 || ((status === 301 || status === 302) && m === "POST")) {
          m = "GET";
          b = undefined;
          h = { ...h };
          delete h["content-length"];
          delete h["content-type"];
        }
        current = next;
        continue;
      }
      return { up, finalUrl: current };
    }
    throw Object.assign(new Error("too-many-redirects"), { code: "TOOMANYREDIRECTS" });
  }

  function readBody(req, max) {
    return new Promise((resolve, reject) => {
      const chunks = [];
      let total = 0;
      req.on("data", (c) => {
        total += c.length;
        if (total > max) {
          reject(Object.assign(new Error("too-large"), { code: "TOOLARGE" }));
          req.destroy();
          return;
        }
        chunks.push(c);
      });
      req.on("end", () => resolve(Buffer.concat(chunks)));
      req.on("error", reject);
    });
  }

  function passHeaders(up) {
    const out = {};
    for (const name of PASS_RES_HEADERS) {
      if (up.headers[name] !== undefined) out[name] = up.headers[name];
    }
    return out;
  }

  async function handle(req, res) {
    const origin = req.headers.origin;
    if (origin && origin !== allowedOrigin) return fail(res, req, 403);

    let parsedUrl;
    try {
      parsedUrl = new URL(req.url, "http://127.0.0.1");
    } catch {
      return fail(res, req, 400);
    }
    const parsed = parseProxyPath(parsedUrl.pathname, secret);
    if (parsed.error) return fail(res, req, parsed.error);
    const { target } = parsed;

    if (req.method === "OPTIONS") {
      res.writeHead(204, { ...corsHeaders(req), "Content-Length": "0" });
      return res.end();
    }
    if (req.method !== "GET" && req.method !== "HEAD" && req.method !== "POST") return fail(res, req, 405);
    if (isSelfTarget(target, port)) return fail(res, req, 400);

    const state = { aborted: false, upstream: null, upRes: null };
    // Annulation propagee : fermeture du client => destruction de l'amont.
    res.on("close", () => {
      if (!res.writableFinished) {
        state.aborted = true;
        if (state.upRes) state.upRes.destroy();
        if (state.upstream) state.upstream.destroy();
      }
    });

    // Ouverture d'un flux : l'ancien flux direct du même hôte est libéré d'abord (comptes à connexion unique).
    if (req.method === "GET" && isStreamOpen(target)) {
      const host = target.host.toLowerCase();
      const prev = activeLive.get(host);
      if (isTrackedLive(target)) trackLive(host, state, res);
      await releaseEntry(prev);
      if (state.aborted) return;
    }

    try {
      const body = req.method === "POST" ? await readBody(req, MAX_POST_BYTES) : undefined;
      const headers = upstreamHeaders(req, req.method, body ? body.length : undefined);
      const { up, finalUrl } = await fetchFollowing(target, req.method, headers, body, state);
      state.upRes = up;
      if (state.aborted) {
        up.destroy();
        return;
      }
      await respond(req, res, up, finalUrl);
    } catch (err) {
      if (state.aborted) return;
      const code = (err && err.code) || "EUPSTREAM";
      report(code);
      if (code === "SELF") return fail(res, req, 400);
      if (code === "TOOLARGE") return fail(res, req, 413);
      if (code === "ETIMEDOUT") return fail(res, req, 504);
      return fail(res, req, 502);
    }
  }

  async function respond(req, res, up, finalUrl) {
    const status = up.statusCode || 502;
    const ct = String(up.headers["content-type"] || "");
    const encoding = String(up.headers["content-encoding"] || "").toLowerCase();
    const cors = corsHeaders(req);
    const headers = { ...passHeaders(up), ...cors };

    const passthrough = () => {
      res.writeHead(status, headers);
      if (req.method === "HEAD") {
        up.destroy();
        res.end();
        return;
      }
      pipeline(up, res, () => {});
    };

    if (req.method !== "GET" || status !== 200) return passthrough();

    const explicit = isManifestType(ct) || hasManifestExt(finalUrl);
    const length = Number(up.headers["content-length"]);
    const generic = isGenericType(ct) && (encoding === "" || encoding === "identity") && !(length > MAX_MANIFEST_BYTES);
    if (!explicit && !generic) return passthrough();
    if (length > MAX_MANIFEST_BYTES) return passthrough();

    if (!explicit) {
      const { buf, ended } = await peekBytes(up, 10);
      if (ended) {
        // Corps minuscule, deja entierement lu : renvoye tel quel.
        res.writeHead(status, headers);
        res.end(buf);
        return;
      }
      const head = buf.toString("utf8").replace(/^﻿/, "");
      if (!head.startsWith("#EXTM3U")) return passthrough();
    }

    const result = await bufferUpTo(up, MAX_MANIFEST_BYTES);
    if (result.overflow) return passthrough();

    let text = null;
    try {
      const decoded = decodeBody(result.body, encoding);
      if (decoded) text = decoded.toString("utf8");
    } catch {
      text = null;
    }
    if (text === null || !looksLikeHlsManifest(text)) {
      // Pas un manifeste HLS (ex. liste de chaines M3U) : renvoye tel quel.
      res.writeHead(status, headers);
      res.end(result.body);
      return;
    }
    const rewritten = rewriteManifest(text, finalUrl, (abs) => buildProxyUrl(base, secret, abs));
    const out = Buffer.from(rewritten, "utf8");
    const h = { ...headers, "content-type": "application/vnd.apple.mpegurl", "content-length": String(out.length) };
    delete h["content-encoding"];
    delete h["content-range"];
    delete h["etag"];
    res.writeHead(200, h);
    res.end(out);
  }

  const server = http.createServer((req, res) => {
    handle(req, res).catch((err) => {
      report((err && err.code) || "EINTERNAL");
      fail(res, req, 500);
    });
  });
  server.keepAliveTimeout = 5000;

  return {
    token: secret,
    start() {
      return new Promise((resolve, reject) => {
        server.once("error", reject);
        server.listen(0, "127.0.0.1", () => {
          port = server.address().port;
          base = `http://127.0.0.1:${port}/`;
          resolve({ port, base: base + secret + "/" });
        });
      });
    },
    stop() {
      httpsAgent.destroy();
      httpAgent.destroy();
      return new Promise((resolve) => {
        server.close(() => resolve());
        server.closeAllConnections();
      });
    },
    /** Base a prefixer : "http://127.0.0.1:PORT/TOKEN/". */
    get base() {
      return base + secret + "/";
    },
    get port() {
      return port;
    },
  };
}

module.exports = {
  encodeTarget,
  decodeTarget,
  buildProxyUrl,
  parseProxyPath,
  isSelfTarget,
  timeoutPolicy,
  isStreamOpen,
  isTrackedLive,
  looksLikeHlsManifest,
  rewriteManifest,
  createProxyServer,
};
