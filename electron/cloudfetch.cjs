"use strict";
// Requetes vers le Worker de configuration cloud, faites par le processus principal.
//  - HTTPS obligatoire (HTTP seulement vers la boucle locale, pour les tests) ;
//  - jamais de redirection suivie (un 30x vers http:// ferait fuiter le jeton) ;
//  - en-tetes limites a Authorization / Content-Type / Accept / If-None-Match ;
//  - delai et taille de reponse bornes ; rien n'est journalise (jeton, corps).

const ALLOWED_HEADERS = new Set(["authorization", "content-type", "accept", "if-none-match"]);
const MAX_BODY = 64 * 1024;
const MAX_RESPONSE = 1024 * 1024;
const TIMEOUT_MS = 15000;
const METHODS = new Set(["GET", "POST", "DELETE", "PUT", "PATCH"]);

function isLoopback(host) {
  return host === "127.0.0.1" || host === "localhost" || host === "[::1]";
}

/** Valide l'URL ; renvoie l'objet URL ou null. */
function checkUrl(raw) {
  let u;
  try {
    u = new URL(String(raw));
  } catch {
    return null;
  }
  if (u.username || u.password || u.hash) return null;
  if (u.protocol === "https:") return u;
  if (u.protocol === "http:" && isLoopback(u.hostname)) return u;
  return null;
}

/**
 * @param {{url:string, method?:string, headers?:Record<string,string>, body?:string}} req
 * @param {(url:string, init:object) => Promise<Response>} fetchImpl  net.fetch d'Electron (ou fetch en test)
 */
async function cloudRequest(req, fetchImpl) {
  if (!req || typeof req !== "object") throw new Error("invalid-request");
  const u = checkUrl(req.url);
  if (!u) throw new Error("invalid-url");
  const method = String(req.method || "GET").toUpperCase();
  if (!METHODS.has(method)) throw new Error("invalid-method");
  const headers = {};
  for (const [k, v] of Object.entries(req.headers || {})) {
    const key = String(k).toLowerCase();
    if (ALLOWED_HEADERS.has(key) && typeof v === "string" && v.length < 4096) headers[key] = v;
  }
  let body;
  if (req.body !== undefined && req.body !== null) {
    if (typeof req.body !== "string" || req.body.length > MAX_BODY) throw new Error("invalid-body");
    body = req.body;
  }
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    const res = await fetchImpl(u.toString(), { method, headers, body, redirect: "manual", signal: ctrl.signal });
    const reader = res.body ? res.body.getReader() : null;
    const chunks = [];
    let size = 0;
    if (reader) {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > MAX_RESPONSE) {
          ctrl.abort();
          throw new Error("response-too-large");
        }
        chunks.push(Buffer.from(value));
      }
    }
    return {
      status: res.status,
      retryAfter: res.headers.get("retry-after") || "",
      etag: res.headers.get("etag") || "",
      text: Buffer.concat(chunks).toString("utf8"),
    };
  } catch (e) {
    if (e && e.name === "AbortError") throw new Error("timeout");
    throw new Error(e && e.message === "response-too-large" ? "response-too-large" : "network");
  } finally {
    clearTimeout(timer);
  }
}

module.exports = { cloudRequest, checkUrl };
