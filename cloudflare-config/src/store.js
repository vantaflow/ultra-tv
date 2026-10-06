// Modèle de données (KV `CONFIG`) :
//   acct:<login>      compte : hash du mot de passe, fournisseurs CHIFFRÉS, liste d'appareils
//   dev:<sha256(jeton)>  appareil appairé (le jeton lui-même n'est jamais stocké)
//   crash:*, event:*  télémétrie (TTL)
// L'unicité d'un login est garantie par le Durable Object (claim), pas par KV.

import { encryptJson, decryptJson, keysFromEnv, sha256Hex, randomToken, timingSafeEqual } from "./crypto.js";

// Plafonds par compte (taille du compte en KV, configuration téléchargée par chaque appareil : ~50 Ko à 100 sources).
// Le tableau de bord pagine et filtre ses listes au-delà de quelques éléments.
export const MAX_PROVIDERS = 100;
export const MAX_DEVICES = 50;

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
  // État partagé (favoris, reprises) de chaque source du compte.
  const st = await env.CONFIG.list({ prefix: `st:${acct.login}:` });
  await Promise.all(st.keys.map((k) => env.CONFIG.delete(k.name)));
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

const KINDS = ["XTREAM", "M3U"];   // Stalker retiré en 1.1.1 (jamais validé)

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
    // Réglages d'affichage partagés (langues, catégories désactivées), null si jamais publiés.
    prefs: p.prefs || null,
    sharedWith: p.assign === undefined ? "all" : p.assign,
    // Source posée par le revendeur (édition Pro) : signalée comme telle aux appareils.
    managed: p.managed === "reseller" ? "reseller" : null,
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
  const r = parseProvider({ get: (k) => body[k] }, origin);
  // Édition Pro : source posée par le revendeur (seule valeur acceptée).
  if (!r.error && body.managed === "reseller") r.provider.managed = "reseller";
  return r;
}

/** URL affichable : schéma + hôte seulement (le chemin et la requête peuvent contenir des identifiants). */
export function displayUrl(raw) {
  try { const u = new URL(raw); return `${u.protocol}//${u.host}`; }
  catch { return ""; }
}

/**
 * Lien IPTV complet (avec identifiants), montré au titulaire du compte qui le demande sur le tableau de bord.
 * Xtream : la playlist M3U équivalente (get.php) ; M3U : l'URL telle qu'enregistrée.
 */
export function iptvLink(p) {
  if (p.kind !== "XTREAM") return p.url || "";
  try {
    const u = new URL(p.url);
    const base = `${u.protocol}//${u.host}${u.pathname.replace(/\/+$/, "")}`;
    const q = new URLSearchParams({ username: p.username || "", password: p.password || "", type: "m3u_plus", output: "ts" });
    return `${base}/get.php?${q}`;
  } catch { return ""; }
}

/** Ports HTTP(S) qu'un Worker Cloudflare peut joindre (les autres échouent : on le dit plutôt que d'attendre). */
const WORKER_PORTS = new Set(["", "80", "443", "8080", "8880", "2052", "2082", "2086", "2095", "2053", "2083", "2087", "2096", "8443"]);

/**
 * Identifiants Xtream d'un fournisseur : un Xtream, ou une liste M3U qui est en fait un lien Xtream
 * (…/get.php?username=…&password=…). null sinon.
 */
export function xtreamCredsOf(p) {
  let u;
  try { u = new URL(p.url); } catch { return null; }
  if (p.kind === "XTREAM") return { u, username: p.username || "", password: p.password || "", base: `${u.protocol}//${u.host}${u.pathname.replace(/\/+$/, "")}` };
  if (/\/get\.php$/i.test(u.pathname) && u.searchParams.get("username") && u.searchParams.get("password")) {
    return { u, username: u.searchParams.get("username"), password: u.searchParams.get("password"), base: `${u.protocol}//${u.host}${u.pathname.replace(/\/get\.php$/i, "")}` };
  }
  return null;
}

/**
 * Abonnement d'un fournisseur Xtream (player_api.php → user_info), interrogé PAR LE WORKER : les identifiants ne
 * quittent jamais le serveur. Renvoie un objet normalisé, ou { error, detail? } : "unsupported" (M3U sans identifiants
 * Xtream), "port" (port non joignable depuis Cloudflare, y compris après redirection), "denied" (identifiants refusés),
 * "unreachable" (réseau, délai, réponse invalide ; `detail` dit pourquoi).
 */
export async function xtreamAccount(p, fetchImpl = fetch, timeoutMs = 8000) {
  const c = xtreamCredsOf(p);
  if (!c) return { error: p.kind === "XTREAM" ? "unreachable" : "unsupported", ...(p.kind === "XTREAM" ? { detail: "adresse invalide" } : {}) };
  if (c.u.protocol !== "http:" && c.u.protocol !== "https:") return { error: "unreachable", detail: "adresse invalide" };
  if (!WORKER_PORTS.has(c.u.port)) return { error: "port", port: c.u.port };
  const q = new URLSearchParams({ username: c.username, password: c.password });
  let url = `${c.base}/player_api.php?${q}`;
  let body;
  try {
    // Redirections suivies À LA MAIN : beaucoup de serveurs renvoient vers un autre hôte ou port ; un port que Cloudflare
    // ne peut pas joindre est signalé comme tel (sinon : échec muet « ne répond pas »).
    for (let hop = 0; ; hop++) {
      const res = await fetchImpl(url, {
        headers: { "user-agent": "UltraTV/1.0", accept: "application/json" },
        signal: AbortSignal.timeout(timeoutMs),
        redirect: "manual",
      });
      if (res.status >= 300 && res.status < 400 && res.headers.get("location")) {
        if (hop >= 3) return { error: "unreachable", detail: "trop de redirections" };
        const next = new URL(res.headers.get("location"), url);
        if (next.protocol !== "http:" && next.protocol !== "https:") return { error: "unreachable", detail: "redirection invalide" };
        if (!WORKER_PORTS.has(next.port)) return { error: "port", port: next.port };
        url = next.toString();
        continue;
      }
      if (res.status === 401 || res.status === 403) return { error: "denied", detail: `HTTP ${res.status}` };
      if (!res.ok) return { error: "unreachable", detail: `HTTP ${res.status}` };
      const text = await res.text();
      try { body = JSON.parse(text); } catch { return { error: "unreachable", detail: "réponse non reconnue (pas une API Xtream)" }; }
      break;
    }
  } catch (e) {
    return { error: "unreachable", detail: e && (e.name === "TimeoutError" || e.name === "AbortError") ? "délai dépassé (8 s)" : "connexion impossible depuis Cloudflare" };
  }
  const ui = body && typeof body === "object" ? body.user_info : null;
  if (!ui || typeof ui !== "object") return { error: "unreachable", detail: "réponse sans informations d'abonnement" };
  if (String(ui.auth) === "0") return { error: "denied" };
  const num = (v) => { const n = Number(v); return Number.isFinite(n) ? n : null; };
  const exp = num(ui.exp_date);
  const created = num(ui.created_at);
  return {
    status: String(ui.status || "").slice(0, 20) || null,
    // exp_date absent / 0 / null = illimité.
    expiresAt: exp && exp > 0 ? exp * 1000 : null,
    createdAt: created && created > 0 ? created * 1000 : null,
    trial: String(ui.is_trial) === "1",
    activeCons: num(ui.active_cons),
    maxCons: num(ui.max_connections),
  };
}

// ---- édition Pro : licence signée par le panneau revendeur ---------------------

/** Clé publique Ed25519 (SPKI, base64) des licences Ultra TV Pro — publique, la même que celle des applications Pro. */
export const PRO_LICENSE_PUBLIC_KEY = "MCowBQYDK2VwAyEAct7h7rfzeaA4nLuL2k0R14nTdc/IrzxStqx8+jdnF4M=";
const LICENSE_MAX_AGE_MS = 8 * 86_400_000;

const b64uBytes = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));

/**
 * Statut de licence Pro transmis par un appareil : { payload, sig } tel que signé par le panneau revendeur
 * (payload = JSON en base64url, sig = Ed25519(payload)). La signature et la fraîcheur sont VÉRIFIÉES : un appareil ne
 * peut pas s'attribuer une licence. Renvoie les seuls champs affichés, ou null.
 */
export async function verifyProLicense(payload, sig, now = Date.now(), publicKey = PRO_LICENSE_PUBLIC_KEY) {
  if (typeof payload !== "string" || typeof sig !== "string" || payload.length > 4096 || sig.length > 200) return null;
  try {
    const key = await crypto.subtle.importKey("spki", Uint8Array.from(atob(publicKey), (c) => c.charCodeAt(0)), { name: "Ed25519" }, false, ["verify"]);
    const ok = await crypto.subtle.verify({ name: "Ed25519" }, key, b64uBytes(sig), new TextEncoder().encode(payload));
    if (!ok) return null;
    const v = JSON.parse(new TextDecoder().decode(b64uBytes(payload)));
    if (!v || v.v !== 1 || typeof v.issuedAt !== "number") return null;
    if (v.issuedAt > now + 3_600_000 || now - v.issuedAt > LICENSE_MAX_AGE_MS) return null;
    const str = (x, n) => (typeof x === "string" && x.trim() ? x.trim().slice(0, n) : null);
    const r = v.reseller && typeof v.reseller === "object" ? v.reseller : null;
    return {
      status: str(v.status, 20),
      until: typeof v.until === "number" ? v.until : null,
      code: str(v.code, 16),
      reseller: r ? { name: str(r.name, 60), whatsapp: str(r.whatsapp, 32), telegram: str(r.telegram, 64) } : null,
      devices: v.devices && typeof v.devices.used === "number" && typeof v.devices.max === "number" ? { used: v.devices.used, max: v.devices.max } : null,
      issuedAt: v.issuedAt,
    };
  } catch { return null; }
}

/** Met à jour les métadonnées d'un appareil (édition, licence) ; n'écrit que si quelque chose change. */
export async function setDeviceInfo(env, acct, deviceId, patch) {
  const d = (acct.devices || []).find((x) => x.id === deviceId);
  if (!d) return false;
  const next = { ...d, ...patch };
  if (JSON.stringify(next) === JSON.stringify(d)) return true;
  acct.devices = acct.devices.map((x) => (x.id === deviceId ? next : x));
  await putAccount(env, acct);
  return true;
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


/** Plafonds des réglages partagés d'une source (une source réelle compte ~1 000 catégories par type). */
export const PREFS_MAX_IDS = 5000;
export const PREFS_KINDS = ["live", "movie", "series"];

/**
 * Réglages d'affichage partagés d'une source, envoyés par un appareil :
 * `{ langs: string[] | null, disabled: { live: string[], movie: string[], series: string[] }, updatedAt: number }`.
 * Les identifiants de catégorie sont ceux du fournisseur (identiques sur tous les appareils).
 */
export function parsePrefs(body, now = Date.now()) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return { error: "body" };
  let langs;
  if (body.langs === null) langs = null;
  else if (Array.isArray(body.langs) && body.langs.length <= 64 && body.langs.every((l) => typeof l === "string" && /^[a-z0-9_-]{1,16}$/i.test(l))) {
    langs = [...new Set(body.langs.map((l) => l.toLowerCase()))];
  } else return { error: "langs" };
  const d = body.disabled;
  if (!d || typeof d !== "object" || Array.isArray(d)) return { error: "disabled" };
  const disabled = {};
  for (const k of PREFS_KINDS) {
    const a = d[k] ?? [];
    if (!Array.isArray(a) || a.length > PREFS_MAX_IDS) return { error: `disabled.${k}` };
    // eslint-disable-next-line no-control-regex
    if (!a.every((x) => typeof x === "string" && x.length > 0 && x.length <= 128 && !/[\u0000-\u001f\u007f]/.test(x))) return { error: `disabled.${k}` };
    disabled[k] = [...new Set(a)];
  }
  const updatedAt = Number(body.updatedAt);
  if (!Number.isFinite(updatedAt) || updatedAt <= 0) return { error: "updatedAt" };
  // Horloge d'appareil en avance : plafonnée, sinon ses réglages gagneraient pour toujours.
  return { prefs: { langs, disabled, updatedAt: Math.min(Math.floor(updatedAt), now + 60_000) } };
}

// ---- état partagé d'une source : favoris, positions de reprise, derniers vus -----------------------

export const STATE_KINDS = new Set(["LIVE", "MOVIE", "EPISODE", "SERIES"]);
export const STATE_MAX_FAV = 3000;
export const STATE_MAX_HIST_PER_PROFILE = 200;
const TOMBSTONE_TTL_MS = 90 * 24 * 3600 * 1000;

const str = (v, max) => (typeof v === "string" && v.length > 0 && v.length <= max && !/[\u0000-\u001f\u007f]/.test(v) ? v : null);
const int = (v) => (Number.isFinite(Number(v)) && Number(v) >= 0 ? Math.floor(Number(v)) : null);

/** Clé d'une entrée : profil (par NOM, partagé entre appareils), type, identifiant du fournisseur. */
export const stateKey = (e) => `${e.p}|${e.k}|${e.r}`;

function parseEntry(e, now, hist) {
  if (!e || typeof e !== "object") return null;
  const p = str(e.p, 40); const k = str(e.k, 16); const r = str(e.r, 128);
  if (!p || !k || !r || !STATE_KINDS.has(k)) return null;
  const at = int(e.at);
  if (!at) return null;
  const base = { p, k, r, at: Math.min(at, now + 60_000) };
  if (!hist) return { ...base, on: e.on !== false };
  const img = typeof e.img === "string" && /^https?:\/\//i.test(e.img) ? str(e.img, 1000) : null;
  return { ...base, t: str(e.t, 200) || "", img, pos: int(e.pos) ?? 0, dur: int(e.dur) ?? 0, par: str(e.par, 128) };
}

/** Corps envoyé par un appareil : entrées invalides ignorées une à une (une mauvaise ligne ne bloque pas les autres). */
export function parseStateBody(body, now = Date.now()) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return { error: "body" };
  const fav = Array.isArray(body.fav) ? body.fav : [];
  const hist = Array.isArray(body.hist) ? body.hist : [];
  if (fav.length > STATE_MAX_FAV || hist.length > STATE_MAX_HIST_PER_PROFILE * 10) return { error: "size" };
  return {
    fav: fav.map((e) => parseEntry(e, now, false)).filter(Boolean),
    hist: hist.map((e) => parseEntry(e, now, true)).filter(Boolean),
  };
}

/** Fusion « le plus récent gagne » par clé, puis élagage (tombes anciennes, historique borné par profil). */
export function mergeState(cur, incoming, now = Date.now()) {
  const fav = new Map((cur?.fav || []).map((e) => [stateKey(e), e]));
  for (const e of incoming.fav) { const o = fav.get(stateKey(e)); if (!o || e.at >= o.at) fav.set(stateKey(e), e); }
  const hist = new Map((cur?.hist || []).map((e) => [stateKey(e), e]));
  for (const e of incoming.hist) { const o = hist.get(stateKey(e)); if (!o || e.at >= o.at) hist.set(stateKey(e), e); }
  const favOut = [...fav.values()].filter((e) => e.on || now - e.at < TOMBSTONE_TTL_MS).sort((a, b) => b.at - a.at).slice(0, STATE_MAX_FAV);
  const byProfile = new Map();
  for (const e of [...hist.values()].sort((a, b) => b.at - a.at)) {
    const l = byProfile.get(e.p) || []; if (l.length < STATE_MAX_HIST_PER_PROFILE) l.push(e); byProfile.set(e.p, l);
  }
  return { fav: favOut, hist: [...byProfile.values()].flat() };
}

const stateKvKey = (login, pid) => `st:${login}:${pid}`;

export async function loadState(env, login, pid) {
  const raw = await env.CONFIG.get(stateKvKey(login, pid));
  if (!raw) return { fav: [], hist: [] };
  try { return await decryptJson(keysFromEnv(env), raw, `${login}:st:${pid}`); } catch { return { fav: [], hist: [] }; }
}

export async function saveState(env, login, pid, state) {
  await env.CONFIG.put(stateKvKey(login, pid), await encryptJson(keysFromEnv(env), state, `${login}:st:${pid}`));
}

export async function deleteState(env, login, pid) { await env.CONFIG.delete(stateKvKey(login, pid)); }
