import { b64uEncode, b64uDecode, timingSafeEqual } from "./crypto.js";

/** Erreur de configuration serveur : réponse 500 générique, détail dans les logs. */
export class ConfigError extends Error {}

export const SECURITY_HEADERS = {
  "x-content-type-options": "nosniff",
  "referrer-policy": "no-referrer",
  "strict-transport-security": "max-age=63072000; includeSubDomains",
  "x-frame-options": "DENY",
  "cache-control": "no-store",
  "permissions-policy": "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  "cross-origin-opener-policy": "same-origin",
  "cross-origin-resource-policy": "same-origin",
};

/** Applique les en-têtes de sécurité à n'importe quelle réponse (sans écraser). */
export function withSecurityHeaders(res) {
  const out = new Response(res.body, res);
  for (const [k, v] of Object.entries(SECURITY_HEADERS)) if (!out.headers.has(k)) out.headers.set(k, v);
  return out;
}

export function json(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...headers },
  });
}

export function redirect(to, headers = {}) {
  return new Response(null, { status: 302, headers: { location: to, ...headers } });
}

export function tooMany(retryAfter, asJson = false) {
  const headers = { "retry-after": String(Math.max(1, retryAfter || 1)) };
  return asJson
    ? json({ error: "too_many_requests", retryAfter }, 429, headers)
    : new Response("Trop de tentatives. Réessaie plus tard.", { status: 429, headers: { ...headers, "content-type": "text/plain; charset=utf-8" } });
}

export function nonce() {
  return b64uEncode(crypto.getRandomValues(new Uint8Array(16)));
}

export function html(body, n, status = 200) {
  const csp = [
    "default-src 'none'",
    `script-src 'nonce-${n}'`,
    `style-src 'nonce-${n}'`,
    "img-src 'self' data:",
    "form-action 'self'",
    "base-uri 'none'",
    "frame-ancestors 'none'",
  ].join("; ");
  return new Response(body, { status, headers: { "content-type": "text/html; charset=utf-8", "content-security-policy": csp } });
}

export function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"'`]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;", "`": "&#96;" }[c]));
}

/** IP cliente telle que vue par Cloudflare (non falsifiable par l'appelant). */
export function clientIp(req) {
  return req.headers.get("cf-connecting-ip") || "unknown";
}

/** Lit un corps en s'arrêtant à `max` octets : on ne bufferise jamais un corps géant. */
export async function readLimited(req, max) {
  const declared = Number(req.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > max) return { tooLarge: true };
  if (!req.body) return { text: "" };
  const reader = req.body.getReader();
  const chunks = []; let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > max) { await reader.cancel(); return { tooLarge: true }; }
    chunks.push(value);
  }
  const all = new Uint8Array(size); let off = 0;
  for (const c of chunks) { all.set(c, off); off += c.byteLength; }
  return { text: new TextDecoder().decode(all) };
}

export async function readJson(req, max) {
  const r = await readLimited(req, max);
  if (r.tooLarge) return { error: json({ error: "too_large" }, 413) };
  try {
    const v = r.text ? JSON.parse(r.text) : {};
    if (v === null || typeof v !== "object" || Array.isArray(v)) throw new Error("not an object");
    return { value: v };
  } catch {
    return { error: json({ error: "invalid_json" }, 400) };
  }
}

export async function readForm(req, max = 16 * 1024) {
  const ct = req.headers.get("content-type") || "";
  if (!ct.startsWith("application/x-www-form-urlencoded")) return { error: new Response("Unsupported Media Type", { status: 415 }) };
  const r = await readLimited(req, max);
  if (r.tooLarge) return { error: new Response("Payload Too Large", { status: 413 }) };
  return { form: new URLSearchParams(r.text) };
}

/**
 * Défense CSRF en profondeur, en plus du jeton : si le navigateur envoie
 * Origin (toujours le cas sur un POST), il doit être celui du Worker.
 */
export function sameOrigin(req) {
  const origin = req.headers.get("origin");
  if (origin) return origin === new URL(req.url).origin;
  const site = req.headers.get("sec-fetch-site");
  return !site || site === "same-origin" || site === "none";
}

// ---- cookies & sessions signés ----------------------------------------------

export const COOKIE = "__Host-utv_sess";
export const SESSION_SECONDS = 7 * 24 * 3600;

export function readCookie(req, name) {
  for (const part of (req.headers.get("cookie") || "").split(";")) {
    const i = part.indexOf("=");
    if (i > 0 && part.slice(0, i).trim() === name) return part.slice(i + 1).trim();
  }
  return null;
}

export function sessionCookieHeader(value) {
  return `${COOKIE}=${value}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${SESSION_SECONDS}`;
}
export function clearCookieHeader() {
  return `${COOKIE}=; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=0`;
}

export function assertSessionSecret(secret) {
  if (!secret || secret.length < 32) throw new ConfigError("SESSION_SECRET absent ou trop court (>= 32 caractères)");
}

async function hmacKey(secret) {
  assertSessionSecret(secret);
  return crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
}

export async function hmac(secret, msg) {
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), new TextEncoder().encode(msg));
  return b64uEncode(sig);
}

/** Cookie : `<payload b64u>.<signature>`, payload = {l: login, e: exp, v: époque, s: id}. */
export async function signSession(secret, { login, epoch }) {
  const payload = b64uEncode(new TextEncoder().encode(JSON.stringify({
    l: login, v: epoch, e: Date.now() + SESSION_SECONDS * 1000,
    s: b64uEncode(crypto.getRandomValues(new Uint8Array(12))),
  })));
  return `${payload}.${await hmac(secret, payload)}`;
}

/** Renvoie {login, epoch, sid} ou null. */
export async function verifySession(secret, value) {
  if (!value) return null;
  const [payload, sig, extra] = value.split(".");
  if (!payload || !sig || extra !== undefined) return null;
  if (!timingSafeEqual(await hmac(secret, payload), sig)) return null;
  try {
    const p = JSON.parse(new TextDecoder().decode(b64uDecode(payload)));
    if (typeof p.l !== "string" || !(p.e > Date.now())) return null;
    return { login: p.l, epoch: p.v ?? 0, sid: p.s };
  } catch { return null; }
}

/** Jeton CSRF lié à la session (change à chaque connexion, mort à la déconnexion). */
export async function csrfToken(secret, sid) {
  return (await hmac(secret, `csrf:${sid}`)).slice(0, 32);
}

export function parseBearer(req) {
  const m = (req.headers.get("authorization") || "").match(/^Bearer\s+(\S+)$/i);
  return m ? m[1] : null;
}

/** Basic (le navigateur affiche sa boîte de dialogue) ou Bearer. Renvoie le secret fourni. */
export function parseOpsSecret(req) {
  const bearer = parseBearer(req);
  if (bearer) return bearer;
  const m = (req.headers.get("authorization") || "").match(/^Basic\s+(\S+)$/i);
  if (!m) return null;
  try {
    const decoded = atob(m[1]);
    return decoded.slice(decoded.indexOf(":") + 1);
  } catch { return null; }
}
