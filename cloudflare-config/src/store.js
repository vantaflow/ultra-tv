// Modèle de données (KV `CONFIG`) :
//   acct:<login>      compte : hash du mot de passe, fournisseurs CHIFFRÉS, liste d'appareils
//   dev:<sha256(jeton)>  appareil appairé (le jeton lui-même n'est jamais stocké)
//   crash:*, event:*  télémétrie (TTL)
// L'unicité d'un login est garantie par le Durable Object (claim), pas par KV.

import { encryptJson, decryptJson, keysFromEnv, sha256Hex, randomToken, timingSafeEqual } from "./crypto.js";

export const MAX_PROVIDERS = 20;
export const MAX_DEVICES = 10;

export function guardStub(env, name) {
  return env.GUARD.get(env.GUARD.idFromName(name));
}

const MAC_RE = /^[0-9a-f]{2}([:-]?[0-9a-f]{2}){5}$/i;

/** Un identifiant qui ressemble à une MAC est ramené à `aa:bb:cc:dd:ee:ff` (comptes hérités). */
export function normalizeLogin(raw) {
  const s = String(raw ?? "").trim().toLowerCase();
  if (MAC_RE.test(s)) return s.replace(/[^0-9a-f]/g, "").match(/../g).join(":");
  return /^[a-z0-9][a-z0-9._@:+-]{2,63}$/.test(s) ? s : null;
}

export const isMacLogin = (login) => MAC_RE.test(login);

// ---- comptes ---------------------------------------------------------------

export async function getAccount(env, login) {
  const raw = await env.CONFIG.get(`acct:${login}`);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

export async function putAccount(env, acct) {
  await env.CONFIG.put(`acct:${acct.login}`, JSON.stringify(acct));
}

export async function loadProviders(env, acct) {
  if (!acct.providersEnc) return [];
  return decryptJson(keysFromEnv(env), acct.providersEnc, acct.login);
}

export async function saveProviders(env, acct, providers) {
  acct.providersEnc = await encryptJson(keysFromEnv(env), providers, acct.login);
}

export async function deleteAccount(env, acct) {
  await Promise.all((acct.devices || []).map((d) => env.CONFIG.delete(`dev:${d.hash}`)));
  await env.CONFIG.delete(`acct:${acct.login}`);
  await guardStub(env, `acct:${acct.login}`).release();
}

// ---- appareils -------------------------------------------------------------

export const DEVICE_TOKEN_PREFIX = "utv_";

export function newDeviceToken() {
  return DEVICE_TOKEN_PREFIX + randomToken(32);
}

export async function registerDevice(env, acct, { token, deviceId, name, label }) {
  const hash = await sha256Hex(token);
  const now = Date.now();
  await env.CONFIG.put(`dev:${hash}`, JSON.stringify({ login: acct.login, deviceId, createdAt: now, seen: now }));
  acct.devices = [...(acct.devices || []), { id: deviceId, hash, name, label, createdAt: now }];
  await putAccount(env, acct);
}

/** Authentifie un jeton d'appareil. Renvoie {acct, device, hash} ou null. */
export async function authDevice(env, token) {
  if (!token || !token.startsWith(DEVICE_TOKEN_PREFIX) || token.length > 128) return null;
  const hash = await sha256Hex(token);
  const raw = await env.CONFIG.get(`dev:${hash}`);
  if (!raw) return null;
  let rec; try { rec = JSON.parse(raw); } catch { return null; }
  const acct = await getAccount(env, rec.login);
  const device = acct?.devices?.find((d) => d.id === rec.deviceId && timingSafeEqual(d.hash, hash));
  if (!device) return null; // révoqué : l'entrée du compte fait foi
  if (Date.now() - (rec.seen || 0) > 3600_000) {
    await env.CONFIG.put(`dev:${hash}`, JSON.stringify({ ...rec, seen: Date.now() }));
  }
  return { acct, device, hash };
}

export async function revokeDevice(env, acct, deviceId) {
  const d = (acct.devices || []).find((x) => x.id === deviceId);
  if (!d) return false;
  acct.devices = acct.devices.filter((x) => x.id !== deviceId);
  await putAccount(env, acct);
  await env.CONFIG.delete(`dev:${d.hash}`);
  return true;
}

export async function rotateDevice(env, acct, device) {
  const token = newDeviceToken();
  const hash = await sha256Hex(token);
  await env.CONFIG.put(`dev:${hash}`, JSON.stringify({ login: acct.login, deviceId: device.id, createdAt: Date.now(), seen: Date.now() }));
  acct.devices = acct.devices.map((d) => (d.id === device.id ? { ...d, hash, rotatedAt: Date.now() } : d));
  await putAccount(env, acct);
  await env.CONFIG.delete(`dev:${device.hash}`);
  return token;
}

// ---- validation des fournisseurs -------------------------------------------

const KINDS = ["XTREAM", "M3U", "STALKER"];

function cleanLine(v, max) {
  // eslint-disable-next-line no-control-regex
  return String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, max);
}

function validUrl(raw) {
  const s = cleanLine(raw, 2048);
  try {
    const u = new URL(s);
    return (u.protocol === "http:" || u.protocol === "https:") && u.hostname ? s : null;
  } catch { return null; }
}

/** Renvoie {provider} ou {error: <code>}. Le serveur ne contacte jamais ces URL (pas de SSRF). */
export function parseProvider(form) {
  const kind = String(form.get("kind") || "").toUpperCase();
  if (!KINDS.includes(kind)) return { error: "kind" };
  const url = validUrl(form.get("url"));
  if (!url) return { error: "url" };
  const p = {
    id: [...crypto.getRandomValues(new Uint8Array(4))].map((b) => b.toString(16).padStart(2, "0")).join(""),
    kind, name: cleanLine(form.get("name"), 64) || kind, url, username: "", password: "", mac: "",
  };
  if (kind === "XTREAM") {
    p.username = cleanLine(form.get("username"), 256);
    p.password = cleanLine(form.get("password"), 256);
    if (!p.username || !p.password) return { error: "creds" };
  } else if (kind === "STALKER") {
    const mac = normalizeLogin(form.get("mac"));
    if (!mac || !isMacLogin(mac)) return { error: "mac" };
    p.mac = mac.toUpperCase();
  }
  return { provider: p };
}

/** Forme renvoyée à l'application (sans l'identifiant interne). */
export function publicProvider({ id: _id, ...rest }) {
  return rest;
}

/** URL affichable : schéma + hôte seulement (le chemin et la requête peuvent contenir des identifiants). */
export function displayUrl(raw) {
  try { const u = new URL(raw); return `${u.protocol}//${u.host}`; }
  catch { return ""; }
}
