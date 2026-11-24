// Modèle de données (KV `CONFIG`) :
//   acct:<login>      compte : hash du mot de passe, fournisseurs CHIFFRÉS, liste d'appareils
//   dev:<sha256(jeton)>  appareil appairé (le jeton lui-même n'est jamais stocké)
//   crash:*, event:*  télémétrie (TTL)
// L'unicité d'un login est garantie par le Durable Object (claim), pas par KV.

import { docs } from "./docs.js";
import { touchDeviceSeen, forgetDeviceSeen, stateBlobGet, stateBlobPut, stateBlobDelete, forgetAccountD1, forgetDeviceActivity } from "./telemetry.js";
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
  const raw = await docs.get(env, `acct:${login}`);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

export async function putAccount(env, acct) {
  await docs.put(env, `acct:${acct.login}`, JSON.stringify(acct));
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

// Pierres tombales : identifiants des sources supprimées du compte, renvoyés aux appareils (`removed`) pour qu'ils
// retirent aussi leur copie locale. Bornées (nombre et âge) : le document du compte reste petit.
export const SRC_TOMB_MAX = 50;
export const SRC_TOMB_TTL_MS = 30 * 86_400_000;

/** Ajoute les sources supprimées aux pierres tombales du compte (en place), en écartant les plus anciennes. */
export function addTombstones(acct, ids, now = Date.now()) {
  const keep = (acct.removedProviders || []).filter((t) => now - t.at < SRC_TOMB_TTL_MS && !ids.includes(t.id));
  acct.removedProviders = [...keep, ...ids.map((id) => ({ id, at: now }))].slice(-SRC_TOMB_MAX);
}

/** Identifiants supprimés encore dans leur fenêtre de rétention. */
export function liveTombstones(acct, now = Date.now()) {
  return (acct.removedProviders || []).filter((t) => now - t.at < SRC_TOMB_TTL_MS).map((t) => t.id);
}

export async function deleteAccount(env, acct) {
  await docs.deleteMany(env, (acct.devices || []).map((d) => `dev:${d.hash}`));
  // État partagé (favoris, reprises) de chaque source du compte.
  const st = await env.CONFIG.list({ prefix: `st:${acct.login}:` });
  if (typeof env.CONFIG.deleteMany === "function") await env.CONFIG.deleteMany(st.keys.map((k) => k.name));
  else await Promise.all(st.keys.map((k) => env.CONFIG.delete(k.name))); // anciens états KV (rares, déjà migrés vers D1)
  await forgetAccountD1(env, acct.login, (acct.devices || []).map((d) => d.id));
  await docs.delete(env, `ostok:${acct.login}`);
  // Bibliothèque et titres Trakt en cache.
  const tl = await docs.keys(env, `tlib:${acct.login}:`);
  const td = await docs.keys(env, `tdict:${acct.login}:`);
  await docs.deleteMany(env, [...tl, ...td]);
  await docs.delete(env, `acct:${acct.login}`);
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
  await docs.put(env, `dev:${hash}`, JSON.stringify({ login: acct.login, deviceId, createdAt: now, seen: now }));
  acct.devices = [...(acct.devices || []), { id: deviceId, hash, name, label, createdAt: now }];
  await putAccount(env, acct);
}

/** Authentifie un jeton d'appareil. Renvoie {acct, device, hash} ou null. */
export async function authDevice(env, token) {
  if (!token || !token.startsWith(DEVICE_TOKEN_PREFIX) || token.length > 128) return null;
  const hash = await sha256Hex(token);
  const raw = await docs.get(env, `dev:${hash}`);
  if (!raw) return null;
  let rec; try { rec = JSON.parse(raw); } catch { return null; }
  const acct = await getAccount(env, rec.login);
  const device = acct?.devices?.find((d) => d.id === rec.deviceId && timingSafeEqual(d.hash, hash));
  if (!device) return null; // révoqué : l'entrée du compte fait foi
  // « Vu » en D1 (une écriture KV par heure et par appareil consommait le quota gratuit) ; `rec.seen` n'est plus réécrit.
  await touchDeviceSeen(env, hash);
  return { acct, device, hash };
}

export async function revokeDevice(env, acct, deviceId) {
  const d = (acct.devices || []).find((x) => x.id === deviceId);
  if (!d) return false;
  acct.devices = acct.devices.filter((x) => x.id !== deviceId);
  await putAccount(env, acct);
  await docs.delete(env, `dev:${d.hash}`);
  await forgetDeviceSeen(env, d.hash);
  await forgetDeviceActivity(env, d.id);
  return true;
}

export async function rotateDevice(env, acct, device) {
  const token = newDeviceToken();
  const hash = await sha256Hex(token);
  await docs.put(env, `dev:${hash}`, JSON.stringify({ login: acct.login, deviceId: device.id, createdAt: Date.now(), seen: Date.now() }));
  acct.devices = acct.devices.map((d) => (d.id === device.id ? { ...d, hash, rotatedAt: Date.now() } : d));
  await putAccount(env, acct);
  await docs.delete(env, `dev:${device.hash}`);
  await forgetDeviceSeen(env, device.hash);
  return token;
}

// ---- validation des fournisseurs -------------------------------------------

// Portail Stalker / Ministra : `url` = portail, `mac` = MAC du boîtier (obligatoire), `username` / `password` facultatifs.
const KINDS = ["XTREAM", "M3U", "STALKER", "JELLYFIN", "EMBY", "PLEX", "NAS"];
/**
 * Serveurs multimédias : ajoutés depuis le tableau de bord seulement (un appareil n'en pousse jamais : il n'en garde
 * que des jetons de session). L'appareil s'y connecte lui-même à la réception ; le Worker ne les contacte jamais.
 */
export const MEDIA_KINDS = ["JELLYFIN", "EMBY", "PLEX", "NAS"];

/**
 * Dossier partagé SMB : « smb://hôte[:port]/partage[/dossier] », « \\\\hôte\\partage » ou « hôte/partage » → forme
 * « smb://hôte[:port]/partage/dossier » sans identifiants ni « / » final ; null si invalide.
 */
export function nasUrl(raw) {
  let s = cleanLine(raw, 1024).replace(/\\/g, "/");
  if (!s) return null;
  // Autre protocole (http, ftp…) : ce n'est pas un partage SMB.
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(s) && !/^smb:\/\//i.test(s)) return null;
  if (s.startsWith("//")) s = `smb:${s}`;
  else if (!/^smb:\/\//i.test(s)) s = `smb://${s.replace(/^\/+/, "")}`;
  try {
    const u = new URL(s);
    const path = decodeURIComponent(u.pathname).replace(/\/+$/, "");
    if (!u.hostname || u.username || u.password || path.length < 2) return null;
    return `smb://${u.host}${path}`;
  } catch { return null; }
}

/** MAC « 00:1A:79:XX:XX:XX » (séparateurs « : » ou « - » ou aucun) → forme normalisée en majuscules ; null sinon. */
export function normalizeMac(raw) {
  const hex = String(raw ?? "").replace(/[^0-9a-f]/gi, "").toUpperCase();
  if (hex.length !== 12 || !/^[0-9A-F:\-\s]+$/i.test(String(raw ?? "").trim())) return null;
  return hex.match(/.{2}/g).join(":");
}

/** MAC affichable sans la divulguer (comme un mot de passe) : seuls les deux derniers octets restent lisibles. */
export function maskMac(mac) {
  const n = normalizeMac(mac);
  return n ? `••:••:••:••:${n.slice(-5)}` : "••:••:••:••:••:••";
}

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
  // Plex : seulement par le parcours de liaison du compte (parsePlexProvider), jamais par un formulaire libre.
  if (kind === "PLEX") return { error: "kind" };
  const url = kind === "NAS" ? nasUrl(form.get("url")) : validUrl(form.get("url"));
  if (!url) return { error: kind === "NAS" ? "nas" : "url" };
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
  if (kind === "JELLYFIN" || kind === "EMBY") {
    // Utilisateur (+ mot de passe, vide permis) ou clé API (+ utilisateur facultatif pour choisir le profil).
    p.username = cleanLine(form.get("username"), 256);
    p.password = cleanLine(form.get("password"), 256);
    p.apiKey = cleanLine(form.get("apikey"), 256);
    if (!p.username && !p.apiKey) return { error: "media_creds" };
  }
  if (kind === "NAS") {
    // Vide : accès invité.
    p.username = cleanLine(form.get("username"), 256);
    p.password = cleanLine(form.get("password"), 256);
  }
  if (kind === "STALKER") {
    p.mac = normalizeMac(form.get("mac"));
    if (!p.mac) return { error: "mac" };
    // Identifiant / mot de passe du portail : facultatifs (certains portails les demandent en plus de la MAC).
    p.username = cleanLine(form.get("username"), 256);
    p.password = cleanLine(form.get("password"), 256);
  }
  return { provider: p };
}

/**
 * Modification d'une source IPTV depuis le tableau de bord : nom, adresse et, pour Xtream, identifiant et mot de passe.
 * Un champ vide garde la valeur actuelle (l'adresse et le mot de passe ne sont jamais renvoyés dans la page). Les sources
 * gérées par un revendeur et les serveurs multimédias ne se modifient pas ici. Renvoie { provider, changed } ou { error }.
 */
export function editProvider(p, form, now = Date.now()) {
  if (!p || p.managed === "reseller" || !["XTREAM", "M3U", "STALKER"].includes(p.kind)) return { error: "edit" };
  const next = { ...p };
  const name = cleanLine(form.get("name"), 64);
  if (name) next.name = name;
  const rawUrl = String(form.get("url") || "").trim();
  if (rawUrl) {
    const url = validUrl(rawUrl);
    if (!url) return { error: "url" };
    next.url = url;
  }
  if (p.kind === "XTREAM") {
    const u = cleanLine(form.get("username"), 256), pw = cleanLine(form.get("password"), 256);
    if (u) next.username = u;
    if (pw) next.password = pw;
  }
  const changed = ["name", "url", "username", "password"].some((k) => next[k] !== p[k]);
  if (changed) next.updatedAt = Math.max(now, (p.updatedAt || 0) + 1);
  return { provider: next, changed };
}

/**
 * Source Plex à partir d'un serveur choisi après liaison du compte (voir plexweb.js) : `username` = identifiant de la
 * machine, `password` = jeton du SERVEUR (pas celui du compte), `url` = meilleure connexion ; toutes les connexions
 * suivent pour que l'appareil choisisse celle qui répond chez lui.
 */
/** Clé d'identité d'un serveur multimédia (adresse normalisée + utilisateur) : sert à refuser les doublons. */
export function mediaKey(p) {
  let u = String(p.url || "").trim().toLowerCase();
  try {
    const x = new URL(u);
    const port = x.port && !((x.protocol === "http:" && x.port === "80") || (x.protocol === "https:" && x.port === "443")) ? `:${x.port}` : "";
    u = `${x.protocol}//${x.hostname}${port}${x.pathname.replace(/\/+$/, "")}`;
  } catch { u = u.replace(/\/+$/, ""); }
  return `${p.kind}|${u}|${p.kind === "NAS" ? "" : String(p.username || "").trim().toLowerCase()}`;
}

export function parsePlexProvider(server, name = "") {
  const conns = Array.isArray(server?.conns) ? server.conns.filter((c) => c && validUrl(c.uri)) : [];
  if (!server?.id || !server?.token || !conns.length) return { error: "plex" };
  return { provider: {
    id: [...crypto.getRandomValues(new Uint8Array(4))].map((b) => b.toString(16).padStart(2, "0")).join(""),
    kind: "PLEX", name: cleanLine(name, 64) || cleanLine(server.name, 64) || "Plex", url: conns[0].uri,
    username: cleanLine(server.id, 128), password: String(server.token).slice(0, 512),
    plex: { owned: server.owned === true, conns: conns.map((c) => ({ uri: c.uri, local: c.local === true, relay: c.relay === true })) },
    createdAt: Date.now(), updatedAt: Date.now(), originDeviceId: "", originName: "dashboard",
  } };
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
    // MAC du portail Stalker : envoyée aux appareils comme un identifiant (jamais réaffichée sur le tableau de bord).
    ...(p.kind === "STALKER" ? { mac: p.mac || "" } : {}),
    // Jellyfin / Emby : clé API (à la place du mot de passe) ; Plex : toutes les connexions du serveur.
    ...(p.kind === "JELLYFIN" || p.kind === "EMBY" ? { apiKey: p.apiKey || "" } : {}),
    ...(p.kind === "PLEX" ? { plex: { owned: p.plex?.owned === true, conns: p.plex?.conns || [] } } : {}),
    originDeviceId: p.originDeviceId || "", originName: p.originName || "", createdAt: p.createdAt || 0, updatedAt: p.updatedAt || p.createdAt || 0,
  };
}

/**
 * Corps JSON d'un appareil → validation identique au tableau de bord. Les champs doivent être des chaînes
 * (un objet ou un tableau serait converti en « [object Object] » et passerait la validation).
 */
export function parseDeviceProvider(body, origin) {
  const FIELDS = ["kind", "name", "url", "username", "password", "mac"]; // `mac` : portail Stalker seulement
  for (const k of FIELDS) {
    if (body[k] !== undefined && typeof body[k] !== "string") return { error: k };
  }
  // Serveurs multimédias : tableau de bord seulement (l'appareil n'a que des jetons de session, pas les identifiants).
  if (MEDIA_KINDS.includes(String(body.kind || "").toUpperCase())) return { error: "kind" };
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
  // Portail Stalker : aucun lien autonome (le portail exige la MAC en cookie, côté appareil).
  if (p.kind === "STALKER") return "";
  if (p.kind !== "XTREAM") return p.url || "";
  try {
    const u = new URL(p.url);
    const base = `${u.protocol}//${u.host}${u.pathname.replace(/\/+$/, "")}`;
    const q = new URLSearchParams({ username: p.username || "", password: p.password || "", type: "m3u_plus", output: "ts" });
    return `${base}/get.php?${q}`;
  } catch { return ""; }
}

/** Ports HTTP(S) qu'un Worker Cloudflare peut joindre (les autres échouent : on le dit plutôt que d'attendre). */
/** Identités essayées tour à tour quand le serveur refuse (HTTP 401/403) : la nôtre, puis des lecteurs IPTV courants. */
const XTREAM_USER_AGENTS = ["UltraTV/1.0", "IPTVSmartersPro", "okhttp/4.12.0", "VLC/3.0.20 LibVLC/3.0.20"];

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
 * fetch() passant par le relais d'abonnement (relay/, hébergé hors Cloudflare) : même interface qu'un fetch sans
 * redirection automatique, pour réutiliser la logique de sauts et de contrôle. Erreur du relais → exception `relay`.
 */
export function relayFetch(relay, fetchImpl = fetch, timeoutMs = 8000) {
  return async (url, init) => {
    let res;
    try {
      res = await fetchImpl(relay.url, {
        method: "POST",
        headers: { "content-type": "application/json", "x-relay-key": relay.key },
        body: JSON.stringify({ url, ua: init && init.headers ? init.headers["user-agent"] : undefined }),
        signal: AbortSignal.timeout(timeoutMs + 4000),
      });
    } catch (e) {
      if (e && (e.name === "TimeoutError" || e.name === "AbortError")) throw e;
      throw Object.assign(new Error("relay"), { relay: true });
    }
    let j = null;
    try { j = res.ok ? await res.json() : null; } catch { /* réponse invalide */ }
    if (!j || typeof j !== "object") throw Object.assign(new Error("relay"), { relay: true });
    if (j.error === "timeout") throw Object.assign(new Error("timeout"), { name: "TimeoutError" });
    if (j.error) throw new Error(String(j.error));
    const status = Number(j.status);
    if (!Number.isInteger(status) || status < 200 || status > 599) throw Object.assign(new Error("relay"), { relay: true });
    const nullBody = status === 204 || status === 205 || status === 304;
    return new Response(nullBody ? null : String(j.body ?? ""), { status, headers: j.location ? { location: String(j.location) } : {} });
  };
}

/**
 * Abonnement d'un fournisseur Xtream (player_api.php → user_info), interrogé PAR LE WORKER : les identifiants ne
 * quittent jamais le serveur. Renvoie un objet normalisé, ou { error, detail? } : "unsupported" (M3U sans identifiants
 * Xtream), "port" (port non joignable depuis Cloudflare, y compris après redirection), "denied" (identifiants refusés),
 * "unreachable" (réseau, délai, réponse invalide ; `detail` dit pourquoi).
 */
export async function xtreamAccount(p, fetchImpl = fetch, timeoutMs = 8000, relay = null) {
  const c = xtreamCredsOf(p);
  if (!c) return { error: p.kind === "XTREAM" ? "unreachable" : "unsupported", ...(p.kind === "XTREAM" ? { detail: "adresse invalide" } : {}) };
  if (c.u.protocol !== "http:" && c.u.protocol !== "https:") return { error: "unreachable", detail: "adresse invalide" };
  const q = new URLSearchParams({ username: c.username, password: c.password });
  const first = `${c.base}/player_api.php?${q}`;
  // Une interrogation complète avec une identité (user-agent) donnée : { body } ou { error, detail?, port?, http? }.
  const probe = async (ua, f, viaRelay) => {
    let url = first;
    if (!viaRelay && !WORKER_PORTS.has(c.u.port)) return { error: "port", port: c.u.port };
    try {
      // Redirections suivies À LA MAIN : beaucoup de serveurs renvoient vers un autre hôte ou port ; un port que Cloudflare
      // ne peut pas joindre est signalé comme tel (sinon : échec muet « ne répond pas »).
      for (let hop = 0; ; hop++) {
        const res = await f(url, {
          headers: { "user-agent": ua, accept: "*/*" },
          signal: AbortSignal.timeout(timeoutMs),
          redirect: "manual",
        });
        if (res.status >= 300 && res.status < 400 && res.headers.get("location")) {
          if (hop >= 3) return { error: "unreachable", detail: "trop de redirections" };
          const next = new URL(res.headers.get("location"), url);
          if (next.protocol !== "http:" && next.protocol !== "https:") return { error: "unreachable", detail: "redirection invalide" };
          if (!viaRelay && !WORKER_PORTS.has(next.port)) return { error: "port", port: next.port };
          url = next.toString();
          continue;
        }
        // 401/403 en HTTP : souvent un pare-feu (adresses Cloudflare ou user-agent refusés), pas les identifiants (le vrai
        // refus d'identifiants arrive en JSON, auth = 0).
        if (res.status === 401 || res.status === 403) return { error: "unreachable", http: res.status, detail: viaRelay ? `accès refusé par le serveur (HTTP ${res.status}), même hors Cloudflare` : `accès refusé par le serveur (HTTP ${res.status}), qui bloque sans doute les serveurs Cloudflare` };
        if (!res.ok) return { error: "unreachable", detail: `HTTP ${res.status}` };
        const text = await res.text();
        try { return { body: JSON.parse(text) }; } catch { return { error: "unreachable", detail: "réponse non reconnue (pas une API Xtream)" }; }
      }
    } catch (e) {
      if (e && e.relay) return { error: "unreachable", detail: "relais d'abonnement indisponible" };
      return { error: "unreachable", detail: e && (e.name === "TimeoutError" || e.name === "AbortError") ? "délai dépassé (8 s)" : viaRelay ? "connexion impossible" : "connexion impossible depuis Cloudflare" };
    }
  };
  // Beaucoup de panneaux ne laissent passer que les lecteurs IPTV connus : refus HTTP → on réessaie sous leur identité.
  const attempt = async (f, viaRelay) => {
    let r;
    for (const ua of XTREAM_USER_AGENTS) {
      r = await probe(ua, f, viaRelay);
      if (!r.http) break;
    }
    return r;
  };
  let r = await attempt(fetchImpl, false);
  // Bloqué depuis Cloudflare (refus HTTP, port fermé, connexion coupée) : nouvel essai par le relais hors Cloudflare.
  if (relay && relay.url && relay.key && (r.http || r.error === "port" || r.detail === "connexion impossible depuis Cloudflare")) {
    r = await attempt(relayFetch(relay, fetchImpl, timeoutMs), true);
  }
  if (!r.body) { const { http: _h, ...err } = r; return err; }
  const body = r.body;
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

/**
 * Catégories d'une source Xtream (live, films, séries), interrogées PAR LE WORKER (les identifiants ne quittent pas le
 * serveur) : { live: [{ id, name }], movie: [...], series: [...] } ou { error: "unsupported" | "unreachable" }.
 */
export async function xtreamCategories(p, fetchImpl = fetch, timeoutMs = 8000, relay = null) {
  const c = xtreamCredsOf(p);
  if (!c || (c.u.protocol !== "http:" && c.u.protocol !== "https:")) return { error: "unsupported" };
  const actions = { live: "get_live_categories", movie: "get_vod_categories", series: "get_series_categories" };
  const one = async (action, f, viaRelay) => {
    if (!viaRelay && !WORKER_PORTS.has(c.u.port)) return null;
    const url = `${c.base}/player_api.php?${new URLSearchParams({ username: c.username, password: c.password, action })}`;
    for (const ua of XTREAM_USER_AGENTS.slice(0, 2)) {
      try {
        const res = await f(url, { headers: { "user-agent": ua, accept: "*/*" }, signal: AbortSignal.timeout(timeoutMs), redirect: "follow" });
        if (res.status === 401 || res.status === 403) continue;
        if (!res.ok) return null;
        const j = JSON.parse(await res.text());
        if (!Array.isArray(j)) return null;
        const seen = new Set();
        const list = [];
        for (const e of j) {
          const id = e && typeof e === "object" && (typeof e.category_id === "string" || typeof e.category_id === "number") ? cleanId(String(e.category_id)) : null;
          if (!id || seen.has(id)) continue;
          seen.add(id);
          list.push({ id, name: typeof e.category_name === "string" ? cleanLine(e.category_name, 120) || id : id });
        }
        return list;
      } catch { return null; }
    }
    return null;
  };
  const run = async (f, viaRelay) => {
    const r = await Promise.all(Object.values(actions).map((a) => one(a, f, viaRelay)));
    return r.every((x) => x === null) ? null : { live: r[0] || [], movie: r[1] || [], series: r[2] || [] };
  };
  let out = await run(fetchImpl, false);
  if (!out && relay && relay.url && relay.key) out = await run(relayFetch(relay, fetchImpl, timeoutMs), true);
  return out || { error: "unreachable" };
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
export const PREFS_MAX_HIDDEN = 3000;
export const PREFS_MAX_CHORDER = 400;
// eslint-disable-next-line no-control-regex
const cleanId = (x) => (typeof x === "string" && x.length > 0 && x.length <= 128 && !/[\u0000-\u001f\u007f]/.test(x) ? x : null);

/** Catégories favorites { live, movie, series } (listes d'identifiants du fournisseur, sans doublon) ; null si invalide. */
export function parseFavCats(f) {
  if (!f || typeof f !== "object" || Array.isArray(f)) return null;
  const out = {};
  for (const k of PREFS_KINDS) {
    const a = f[k] ?? [];
    if (!Array.isArray(a) || a.length > PREFS_MAX_IDS || !a.every((x) => cleanId(x))) return null;
    out[k] = [...new Set(a)];
  }
  return out;
}

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
  // Ordre des catégories par type (facultatif : absent = un appareil plus ancien, l'ordre actuel est gardé).
  let order;
  if (body.order !== undefined && body.order !== null) {
    const o = body.order;
    if (typeof o !== "object" || Array.isArray(o)) return { error: "order" };
    order = {};
    for (const k of PREFS_KINDS) {
      const a = o[k] ?? [];
      // eslint-disable-next-line no-control-regex
      if (!Array.isArray(a) || a.length > PREFS_MAX_IDS || !a.every((x) => typeof x === "string" && x.length > 0 && x.length <= 128 && !/[\u0000-\u001f\u007f]/.test(x))) return { error: `order.${k}` };
      order[k] = [...new Set(a)];
    }
  }
  // Chaînes masquées (facultatif) : { i: identifiant du fournisseur, c: catégorie, n: nom }.
  let hidden;
  if (body.hidden !== undefined && body.hidden !== null) {
    if (!Array.isArray(body.hidden) || body.hidden.length > PREFS_MAX_HIDDEN) return { error: "hidden" };
    hidden = [];
    const seen = new Set();
    for (const h of body.hidden) {
      const i = h && typeof h === "object" ? cleanId(h.i) : null;
      if (!i) return { error: "hidden" };
      if (seen.has(i)) continue;
      seen.add(i);
      hidden.push({ i, c: h.c == null ? null : cleanId(h.c), n: typeof h.n === "string" ? cleanLine(h.n, 120) : "" });
    }
  }
  // Ordre personnel des chaînes (facultatif) : [{ p: nom du profil, s: liste (catégorie ou favoris), k: [identifiants] }].
  let chOrder;
  if (body.chOrder !== undefined && body.chOrder !== null) {
    if (!Array.isArray(body.chOrder) || body.chOrder.length > PREFS_MAX_CHORDER) return { error: "chOrder" };
    chOrder = [];
    for (const c of body.chOrder) {
      const p = c && typeof c === "object" && typeof c.p === "string" ? cleanLine(c.p, 24) : "";
      const sc = c && cleanId(c.s);
      if (!p || !sc || !Array.isArray(c.k) || c.k.length > PREFS_MAX_IDS || !c.k.every((x) => cleanId(x))) return { error: "chOrder" };
      chOrder.push({ p, s: sc, k: [...new Set(c.k)] });
    }
  }
  // Catégories masquées par profil (facultatif) : [{ p: nom du profil, k: live|movie|series, ids: [...] }].
  let pHidden;
  if (body.pHidden !== undefined && body.pHidden !== null) {
    if (!Array.isArray(body.pHidden) || body.pHidden.length > PREFS_MAX_CHORDER) return { error: "pHidden" };
    pHidden = [];
    for (const c of body.pHidden) {
      const p = c && typeof c === "object" && typeof c.p === "string" ? cleanLine(c.p, 24) : "";
      if (!p || !PREFS_KINDS.includes(c.k) || !Array.isArray(c.ids) || c.ids.length > PREFS_MAX_IDS || !c.ids.every((x) => cleanId(x))) return { error: "pHidden" };
      pHidden.push({ p, k: c.k, ids: [...new Set(c.ids)] });
    }
  }
  // Chaînes masquées PAR PROFIL (facultatif) : [{ p: nom du profil, ch: [{ i, c, n }] }]. Seulement les chaînes que ce profil est
  // seul à masquer ; celles que TOUS masquent restent dans `hidden` (champ historique, lu par les appareils plus anciens).
  let pHiddenCh;
  if (body.pHiddenCh !== undefined && body.pHiddenCh !== null) {
    if (!Array.isArray(body.pHiddenCh) || body.pHiddenCh.length > PREFS_MAX_CHORDER) return { error: "pHiddenCh" };
    pHiddenCh = [];
    for (const e of body.pHiddenCh) {
      const p = e && typeof e === "object" && typeof e.p === "string" ? cleanLine(e.p, 24) : "";
      if (!p || !Array.isArray(e.ch) || e.ch.length > PREFS_MAX_HIDDEN) return { error: "pHiddenCh" };
      const seen = new Set();
      const ch = [];
      for (const h of e.ch) {
        const i = h && typeof h === "object" ? cleanId(h.i) : null;
        if (!i) return { error: "pHiddenCh" };
        if (seen.has(i)) continue;
        seen.add(i);
        ch.push({ i, c: h.c == null ? null : cleanId(h.c), n: typeof h.n === "string" ? cleanLine(h.n, 120) : "" });
      }
      pHiddenCh.push({ p, ch });
    }
  }
  // Ordre des catégories PAR PROFIL (facultatif) : [{ p: nom du profil, k: live|movie|series, ids: [...] dans l'ordre }].
  let pOrder;
  if (body.pOrder !== undefined && body.pOrder !== null) {
    if (!Array.isArray(body.pOrder) || body.pOrder.length > PREFS_MAX_CHORDER) return { error: "pOrder" };
    pOrder = [];
    for (const c of body.pOrder) {
      const p = c && typeof c === "object" && typeof c.p === "string" ? cleanLine(c.p, 24) : "";
      if (!p || !PREFS_KINDS.includes(c.k) || !Array.isArray(c.ids) || c.ids.length > PREFS_MAX_IDS || !c.ids.every((x) => cleanId(x))) return { error: "pOrder" };
      pOrder.push({ p, k: c.k, ids: [...new Set(c.ids)] });
    }
  }
  // Catégories FAVORITES par type (facultatif : absent = appareil plus ancien, les favorites du compte sont gardées).
  let favCats;
  if (body.favCats !== undefined && body.favCats !== null) {
    const f = parseFavCats(body.favCats);
    if (!f) return { error: "favCats" };
    favCats = f;
  }
  const updatedAt = Number(body.updatedAt);
  if (!Number.isFinite(updatedAt) || updatedAt <= 0) return { error: "updatedAt" };
  // Horloge d'appareil en avance : plafonnée, sinon ses réglages gagneraient pour toujours.
  const prefs = { langs, disabled, updatedAt: Math.min(Math.floor(updatedAt), now + 60_000) };
  if (order) prefs.order = order;
  if (hidden) prefs.hidden = hidden;
  if (chOrder) prefs.chOrder = chOrder;
  if (pHidden) prefs.pHidden = pHidden;
  if (pHiddenCh) prefs.pHiddenCh = pHiddenCh;
  if (pOrder) prefs.pOrder = pOrder;
  if (favCats) prefs.favCats = favCats;
  return { prefs };
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
  // D1 d'abord ; l'ancienne valeur KV reste lue tant que la source n'a pas été réécrite (migration au fil de l'eau).
  const k = stateKvKey(login, pid);
  const raw = (await stateBlobGet(env, k)) ?? (await env.CONFIG.get(k));
  if (!raw) return { fav: [], hist: [] };
  try { return await decryptJson(keysFromEnv(env), raw, `${login}:st:${pid}`); } catch { return { fav: [], hist: [] }; }
}

/** Écrit dans D1 (une écriture KV par synchro épuisait le quota gratuit) ; KV seulement si D1 est indisponible. */
export async function saveState(env, login, pid, state) {
  const k = stateKvKey(login, pid);
  const blob = await encryptJson(keysFromEnv(env), state, `${login}:st:${pid}`);
  if (!(await stateBlobPut(env, k, blob))) await env.CONFIG.put(k, blob);
}

export async function deleteState(env, login, pid) {
  const k = stateKvKey(login, pid);
  await stateBlobDelete(env, k);
  await env.CONFIG.delete(k);
}

// ---- profils partagés entre les appareils du compte (nom, couleur, profil Enfants ; jamais de code PIN) ------------

export const PROFILES_MAX = 40;
const PROFILE_TOMB_TTL_MS = 180 * 24 * 3600 * 1000;

/**
 * Horaires de visionnage / temps d'écran d'un profil (champ facultatif `t`) : « L90;1=480-1200,1260-1380;2=;… »
 * (L = limite quotidienne en minutes ; « jour=début-fin,… » en minutes depuis minuit, jour 1 = lundi … 7 = dimanche ;
 * jour sans plage = interdit toute la journée). Renvoie le texte nettoyé (ré-encodé), « » = règles retirées, null = invalide.
 */
export function cleanScreenTime(t) {
  if (typeof t !== "string" || t.length > 400) return null;
  if (!t.trim()) return "";
  let limit = null;
  const days = new Map();
  for (const seg of t.split(";")) {
    const s = seg.trim();
    if (s.startsWith("L")) {
      const n = /^L(\d{1,4})$/.exec(s);
      if (!n) return null;
      const v = Number(n[1]);
      if (v >= 1 && v <= 1440) limit = v;
      continue;
    }
    const m = /^([1-7])=(.*)$/.exec(s);
    if (!m) return null;
    const wins = [];
    for (const w of m[2].split(",")) {
      if (!w) continue;
      const p = /^(\d{1,4})-(\d{1,4})$/.exec(w);
      if (!p) return null;
      const a = Number(p[1]), b = Number(p[2]);
      if (a >= 0 && a <= 1439 && b >= 1 && b <= 1440 && b > a) wins.push([a, b]);
    }
    wins.sort((x, y) => x[0] - y[0]);
    const merged = [];
    for (const w of wins) {
      const last = merged[merged.length - 1];
      if (last && w[0] <= last[1]) last[1] = Math.max(last[1], w[1]); else merged.push([w[0], w[1]]);
    }
    days.set(Number(m[1]), merged.slice(0, 4));
  }
  const parts = [];
  if (limit !== null) parts.push(`L${limit}`);
  for (let d = 1; d <= 7; d++) if (days.has(d)) parts.push(`${d}=${days.get(d).map((w) => `${w[0]}-${w[1]}`).join(",")}`);
  return parts.join(";");
}

/** Entrées de profils envoyées par un appareil : { n: nom, c: couleur ARGB, k: enfants, at: ms, on: false = supprimé, s: noms des sources visibles, t: temps d'écran }. */
export function parseProfiles(body, now = Date.now()) {
  if (!body || typeof body !== "object" || !Array.isArray(body.profiles) || body.profiles.length > PROFILES_MAX) return { error: "profiles" };
  const out = [];
  for (const e of body.profiles) {
    if (!e || typeof e !== "object") return { error: "profiles" };
    const n = typeof e.n === "string" ? cleanLine(e.n, 24) : "";
    const at = Number(e.at);
    if (!n || !Number.isFinite(at) || at <= 0) return { error: "profiles" };
    const c = Number.isInteger(e.c) && e.c >= -2147483648 && e.c <= 4294967295 ? e.c : 0;
    const f = typeof e.f === "string" ? cleanLine(e.f, 24) : "";
    // Langue de l'interface du profil (code AppLang : « fr », « pt-BR », « system »…).
    const l = typeof e.l === "string" && /^(system|[a-z]{2}(-[A-Z]{2})?)$/.test(e.l) ? e.l : "";
    // Sources visibles du profil (noms de sources, 12 au plus) : facultatif, absent = toutes les sources. Les anciennes versions l'ignorent.
    const s = Array.isArray(e.s) ? [...new Set(e.s.filter((x) => typeof x === "string").map((x) => cleanLine(x, 40)).filter(Boolean))].slice(0, 12) : [];
    // Temps d'écran : absent = ancienne version (on ne touche à rien) ; « » = règles retirées ; sinon texte nettoyé.
    const t = e.t === undefined ? null : cleanScreenTime(e.t);
    if (e.t !== undefined && t === null) return { error: "profiles" };
    out.push({ n, c, k: e.k === true, at: Math.min(Math.floor(at), now + 60_000), on: e.on !== false, ...(f ? { f } : {}), ...(l ? { l } : {}), ...(s.length ? { s } : {}), ...(t !== null ? { t } : {}) });
  }
  return { profiles: out };
}

/** Fusion par nom (sans casse) : la modification la plus récente gagne ; tombes anciennes oubliées. */
export function mergeProfiles(cur, incoming, now = Date.now()) {
  const byKey = new Map((cur || []).map((p) => [p.n.toLowerCase(), p]));
  for (const p of incoming) {
    const k = p.n.toLowerCase();
    const o = byKey.get(k);
    if (!o || p.at > o.at) {
      // Une ancienne version n'envoie pas `t` : elle ne doit pas effacer les règles de temps d'écran déjà partagées.
      byKey.set(k, p.t === undefined && o && o.t !== undefined && p.on ? { ...p, t: o.t } : p);
    }
  }
  return [...byKey.values()].filter((p) => p.on || now - p.at < PROFILE_TOMB_TTL_MS).sort((a, b) => a.at - b.at).slice(-PROFILES_MAX * 2);
}

/**
 * Sources visibles d'un profil, modifiées depuis le tableau de bord : `names` = liste de noms de sources, ou null
 * pour « toutes » (champ `s` retiré). L'horodatage `at` avance pour que la fusion avec les appareils retienne le
 * changement. Renvoie la nouvelle liste de profils, ou null si le profil n'existe pas (ou est supprimé).
 */
export function setProfileSources(profiles, name, names, now = Date.now()) {
  const list = profiles || [];
  const i = list.findIndex((p) => p.on !== false && p.n.toLowerCase() === String(name || "").toLowerCase());
  if (i < 0) return null;
  const clean = names === null ? [] : [...new Set(names.map((x) => cleanLine(x, 40)).filter(Boolean))].slice(0, 12);
  const { s: _old, ...rest } = list[i];
  const next = { ...rest, at: Math.max(now, (list[i].at || 0) + 1), ...(clean.length ? { s: clean } : {}) };
  return list.map((p, j) => (j === i ? next : p));
}

// ---- reprise de la lecture d'un appareil à l'autre (« Envoyer vers… ») -----------------------------------------
// File COURTE par appareil cible (3 demandes, 2 min), écrite par l'émetteur, lue par la cible pendant sa synchro
// (en-tête de GET /api/config : aucune interrogation de plus). Jamais d'URL de flux ni d'identifiants d'abonnement :
// seulement des références (source du compte, type, identifiant dans le catalogue, position) que la cible résout
// dans SON catalogue.

export const HANDOFF_TTL_MS = 120_000;
export const HANDOFF_MAX = 3;
export const HANDOFF_KINDS = new Set(["live", "movie", "episode"]);
const handoffKey = (login, deviceId) => `ho:${login}:${deviceId}`;

/** Corps de POST /api/device/handoff : { to, ref: { src, t, id, par?, pos, n } } → demande nettoyée, ou { error }. */
export function parseHandoff(body, now = Date.now()) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return { error: "body" };
  const to = typeof body.to === "string" && /^[0-9a-f]{8,64}$/.test(body.to) ? body.to : null;
  if (!to) return { error: "to" };
  const r = body.ref;
  if (!r || typeof r !== "object" || Array.isArray(r)) return { error: "ref" };
  const src = typeof r.src === "string" && /^[0-9a-f]{8}$/.test(r.src) ? r.src : null;
  if (!src) return { error: "src" };
  if (typeof r.t !== "string" || !HANDOFF_KINDS.has(r.t)) return { error: "t" };
  const id = str(r.id, 128);
  if (!id) return { error: "id" };
  const par = r.par === undefined || r.par === null ? null : str(r.par, 128);
  if (r.par !== undefined && r.par !== null && !par) return { error: "par" };
  const pos = r.t === "live" ? 0 : (int(r.pos) ?? 0);
  const n = typeof r.n === "string" ? r.n.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 100) : "";
  return { to, item: { i: randomToken(6), s: src, t: r.t, r: id, ...(par ? { p: par } : {}), o: Math.min(pos, 48 * 3600_000), n, a: now } };
}

/** Demandes encore valables (non expirées), les plus récentes d'abord, au plus HANDOFF_MAX. */
export function liveHandoffs(list, now = Date.now()) {
  return (Array.isArray(list) ? list : []).filter((x) => x && Number.isFinite(x.a) && now - x.a < HANDOFF_TTL_MS && now - x.a >= -60_000)
    .sort((a, b) => b.a - a.a).slice(0, HANDOFF_MAX);
}

export async function loadHandoffs(env, login, deviceId, now = Date.now()) {
  const raw = await env.CONFIG.get(handoffKey(login, deviceId));
  if (!raw) return [];
  try { return liveHandoffs(JSON.parse(raw), now); } catch { return []; }
}

/** Ajoute une demande à la file de l'appareil cible (2 sous-requêtes de stockage : lecture + écriture, TTL 2 min). */
export async function pushHandoff(env, login, to, item, now = Date.now()) {
  const next = liveHandoffs([item, ...(await loadHandoffs(env, login, to, now))], now);
  await env.CONFIG.put(handoffKey(login, to), JSON.stringify(next), { expirationTtl: Math.ceil(HANDOFF_TTL_MS / 1000) });
  return next.length;
}

/** En-tête x-utv-handoff : JSON UTF-8 en base64url (les titres peuvent contenir des accents). Vide = rien à reprendre. */
export function handoffHeader(items, fromNames = {}) {
  if (!items.length) return "";
  const json = JSON.stringify(items.map((x) => ({ ...x, f: fromNames[x.f] || x.f })));
  let bin = "";
  for (const b of new TextEncoder().encode(json)) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
