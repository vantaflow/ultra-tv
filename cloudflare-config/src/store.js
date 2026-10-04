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
  // Version monotone du jeu de fournisseurs : sert d'ETag aux appareils (synchro incrémentale).
  acct.cfgVersion = (acct.cfgVersion || 0) + 1;
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

const KINDS = ["XTREAM", "M3U"]; // Stalker est retiré de l'application

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
export function parseProvider(form, origin = null) {
  const kind = String(form.get("kind") || "").toUpperCase();
  if (!KINDS.includes(kind)) return { error: "kind" };
  const url = validUrl(form.get("url"));
  if (!url) return { error: "url" };
  const p = {
    id: [...crypto.getRandomValues(new Uint8Array(4))].map((b) => b.toString(16).padStart(2, "0")).join(""),
    kind, name: cleanLine(form.get("name"), 64) || kind, url, username: "", password: "",
    createdAt: Date.now(), updatedAt: Date.now(),
    // Appareil d'origine (ou « dashboard ») : affiché dans le tableau de bord.
    originDeviceId: origin?.deviceId || "", originName: cleanLine(origin?.name || "", 64),
  };
  if (kind === "XTREAM") {
    p.username = cleanLine(form.get("username"), 256);
    p.password = cleanLine(form.get("password"), 256);
    if (!p.username || !p.password) return { error: "creds" };
  }
  return { provider: p };
}

/** Forme renvoyée à l'application (sans l'identifiant interne). */
export function publicProvider({ id: _id, ...rest }) {
  return rest;
}

/** Forme de synchro : AVEC l'identifiant stable (clé de fusion côté appareil). */
export function syncProvider(p) {
  return {
    sharedWith: p.assign === undefined ? "all" : p.assign,
    id: p.id, kind: p.kind, name: p.name, url: p.url, username: p.username || "", password: p.password || "",
    originDeviceId: p.originDeviceId || "", originName: p.originName || "", createdAt: p.createdAt || 0, updatedAt: p.updatedAt || p.createdAt || 0,
  };
}

/**
 * Corps JSON d'un appareil → validation identique au tableau de bord. Les champs doivent être des chaînes
 * (un objet ou un tableau serait converti en « [object Object] » et passerait la validation).
 */
export function parseDeviceProvider(body, origin) {
  const FIELDS = ["kind", "name", "url", "username", "password"]; // `mac` est ignoré en entrée
  for (const k of FIELDS) {
    if (body[k] !== undefined && typeof body[k] !== "string") return { error: k };
  }
  return parseProvider({ get: (k) => body[k] }, origin);
}

/** URL affichable : schéma + hôte seulement (le chemin et la requête peuvent contenir des identifiants). */
export function displayUrl(raw) {
  try { const u = new URL(raw); return `${u.protocol}//${u.host}`; }
  catch { return ""; }
}

// ---- affectations (quel appareil reçoit quel fournisseur) ---------------------

/** Fournisseur sans champ `assign` (données antérieures) = « tous les appareils ». */
export const assignmentOf = (p) => (p.assign === undefined || p.assign === "all" ? "all" : Array.isArray(p.assign) ? p.assign : "all");

export function isVisibleTo(p, deviceId) {
  const a = assignmentOf(p);
  return a === "all" || a.includes(deviceId);
}

/**
 * Valide une affectation : "all" ou un tableau NON vide d'identifiants d'appareils de CE compte.
 * Un identifiant inconnu (autre compte, forgé) est refusé, jamais ignoré silencieusement.
 */
export function parseAssign(value, acct) {
  if (value === "all") return { assign: "all" };
  if (!Array.isArray(value) || value.length === 0 || value.length > MAX_DEVICES) return { error: "assign" };
  const known = new Set((acct.devices || []).map((d) => d.id));
  const out = [];
  for (const id of value) {
    if (typeof id !== "string" || !known.has(id)) return { error: "assign" };
    if (!out.includes(id)) out.push(id);
  }
  return { assign: out };
}

/** Révocation : l'appareil disparaît des listes explicites (une liste vidée reste, invisible des appareils, à réaffecter). */
export function dropDeviceFromAssignments(providers, deviceId) {
  return providers.map((p) => (Array.isArray(p.assign) ? { ...p, assign: p.assign.filter((id) => id !== deviceId) } : p));
}

export async function renameDevice(env, acct, deviceId, rawName) {
  const name = cleanLine(rawName, 40);
  if (!name || !(acct.devices || []).some((d) => d.id === deviceId)) return false;
  acct.devices = acct.devices.map((d) => (d.id === deviceId ? { ...d, name } : d));
  await putAccount(env, acct);
  return true;
}

/** Types encore pris en charge : un ancien fournisseur d'un autre type n'est plus envoyé aux appareils. */
export const isSupportedKind = (p) => KINDS.includes(p.kind);
