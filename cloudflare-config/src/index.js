/**
 * Ultra TV — Worker de configuration à distance (v2, appairage par jeton d'appareil).
 *
 * Modèle d'authentification
 *   - Tableau de bord : compte (identifiant + mot de passe PBKDF2), cookie de
 *     session signé HMAC, jeton CSRF lié à la session, contrôle d'Origin.
 *   - Application TV  : appairage par code court (affiché sur la TV, saisi dans
 *     le tableau de bord) → jeton d'appareil aléatoire de 256 bits, stocké haché,
 *     révocable. La MAC n'est plus qu'une étiquette d'affichage.
 *   - Crashs / journaux : l'ingestion exige un jeton d'appareil ; la lecture
 *     exige OPS_TOKEN (secret serveur, jamais dans l'APK).
 *
 * Voir cloudflare-config/README.md pour le détail des routes et de la migration.
 */

import { docs } from "./docs.js";
import { JSQR_SOURCE } from "./jsqr-bundle.js";
import { Guard } from "./guard.js";
import { hashPassword, verifyPassword, randomToken, sha256Hex, timingSafeEqual, keysFromEnv, encryptJson, decryptJson } from "./crypto.js";
import {
  ConfigError, escapeHtml, json, redirect, tooMany, nonce, readJson, readForm, readLimited, sameOrigin, clientIp,
  withSecurityHeaders, readCookie, sessionCookieHeader, clearCookieHeader, COOKIE, signSession, verifySession,
  csrfToken, parseBearer, parseOpsSecret, assertSessionSecret, hmac,
} from "./http.js";
import {
  guardStub, normalizeLogin, isMacLogin, getAccount, putAccount, loadProviders, saveProviders, deleteAccount,
  newDeviceToken, registerDevice, authDevice, revokeDevice, rotateDevice, parseProvider, publicProvider, syncProvider, parseDeviceProvider, parsePrefs,
  isVisibleTo, assignmentOf, addTombstones, liveTombstones, isSupportedKind, parseAssign, dropDeviceFromAssignments, renameDevice,
  MAX_PROVIDERS, MAX_DEVICES, iptvLink, xtreamAccount, verifyProLicense, setDeviceInfo, parseStateBody, mergeState, loadState, saveState, deleteState, parseProfiles, mergeProfiles,
} from "./store.js";
import { parseHandoff, pushHandoff, loadHandoffs, handoffHeader } from "./store.js";
import { tmdbProxy } from "./tmdb.js";
import { downloadRedirect } from "./download.js";
import { ratingsProxy } from "./ratings.js";
import { fanartProxy, tvmazeProxy, sportsTeamProxy } from "./extmeta.js";
import { subtitlesSearch, subtitlesDownload, osLogin } from "./subtitles.js";
import { sanitizeText } from "./sanitize.js";
import { recordEvent, recordEvents, anonTouch as anonTouchD1, purgeTelemetry, touchDeviceActivity, deviceActivity, withActivity, platformOf, modelOf, ANON_PLATFORMS } from "./telemetry.js";
import { anonQuery, eventsQuery, parseListParams, paginate, sortRows, filterAccounts, filterDevices, flattenDevices, countBy, ANON_SORTS, EVENT_SORTS, ACCOUNT_SORTS, DEVICE_SORTS, DAY_MS } from "./admin.js";
import { queryAnon, anonFacets, anonOverview, countEvents, queryEvents, eventFacets } from "./telemetryq.js";
import { overviewPage, accountsPage, anonPage, devicesPage, eventsAdminPage, alertsPage } from "./adminui.js";
import {
  TRAKT_STATE_TTL_S, pkcePair, authorizeUrl, exchangeCode, refreshTokens, traktUsername, revokeToken,
  parseScrobble, resolveTmdb, traktScrobbleBody, sendScrobble,
  parseWatchlist, parseHistory, parseRating, traktWriteRequest, sendSync,
} from "./trakt.js";
import { LIB_TTL_S, LANGS, fetchTraktLibrary, enrichTitles, libraryForDevice } from "./traktlib.js";
import {
  SIMKL_STATE_TTL_S, SIMKL_LIB_TTL_S, simklEnabled, simklAuthorizeUrl, simklExchangeCode, simklUsername, fetchSimklLibrary,
  simklWriteRequest, simklScrobbleBody, simklSend, simklRefresh, simklRevoke, SIMKL_REFRESH_MARGIN_MS,
} from "./simkl.js";
import { TRACKING_CHOICES, activeTracker, trackingStatus } from "./tracking.js";
import { fontResponse } from "./fonts.js";
import { loadAnnouncements, saveAnnouncements, activeAnnouncements, parseAnnouncementForm, ANN_AUDIENCES, ANN_STAT_KINDS } from "./announcements.js";
import { pickEnvoyerLang, langFromCookie, fromAcceptLanguage } from "./i18n/envoyer.js";
import { pickSiteLang, TOO_MANY_TEXT } from "./i18n/site.js";
import { apkPage, envoyerPage, loginPage, signupPage, pairPage, dashboardPage, offlinePage, donatePage } from "./pages.js";
import { pwaResponse } from "./pwa.js";
import { legacyDecision, PRIMARY_HOST, LEGACY_HOST, LEGACY_HOSTS, API_HOST } from "./legacyhost.js";

import { withStorage } from "./turso.js";
export { Guard };

const PAIR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 32 symboles, sans I/O/0/1
const PAIR_TTL_S = 600;
const MIN_PASSWORD = 10;

const EVENT_MAX_BODY = 8 * 1024;
/** Lot de journaux (`/api/events`) : 50 entrées et 32 Ko au plus. */
const EVENTS_BATCH_MAX = 50;
const EVENTS_BATCH_MAX_BODY = 32 * 1024;
const CRASH_MAX_BODY = 48 * 1024;

// ---- limitation de débit ------------------------------------------------------

/** Renvoie une réponse 429 si la limite est dépassée, sinon null. */
async function limited(env, key, limit, windowSec, asJson = false) {
  // Routes à fort trafic (fenêtre ≤ 10 min : appareils, synchro, TMDB…) : limiteur natif de Cloudflare, sans Durable
  // Object (le palier gratuit des DO plafonne à 100 000 requêtes par jour, et chaque appel d'appareil en coûtait deux).
  const native = nativeLimiter(env, limit, windowSec, asJson);
  if (native) {
    try {
      const { success } = await native.limit({ key });
      return success ? null : tooMany(60, asJson);
    } catch { return null; } // limiteur indisponible : on laisse passer plutôt que de couper le service
  }
  try {
    const r = await guardStub(env, key).hit(limit, windowSec);
    return r.ok ? null : tooMany(r.retryAfter, asJson);
  } catch (e) {
    console.error("limiteur DO indisponible", e?.message);
    return null; // quota DO épuisé ou panne : le service reste disponible
  }
}

/**
 * Limiteur natif pour une limite « limit par windowSec », ramenée à la minute (période imposée : 60 s).
 * Paliers RL_10 / RL_30 / RL_60 / RL_120 (wrangler.toml) : le plus petit qui couvre la limite. Routes des appareils (JSON)
 * et fenêtres ≤ 10 min seulement ; connexion et inscription du site (rares, sensibles) : null → Durable Object, compteur exact.
 */
export function nativeLimiter(env, limit, windowSec, asJson = false) {
  if (windowSec > 600 && !asJson) return null;
  // Marge ×4 : la période de 60 s est bien plus courte que la fenêtre d'origine (10 min, 1 h) ; les rafales légitimes
  // (recherche TMDB en série, ouverture de l'appli) doivent passer comme avec l'ancien compteur sur fenêtre longue.
  const perMin = (4 * limit * 60) / windowSec;
  const tier = perMin <= 10 ? env.RL_10 : perMin <= 30 ? env.RL_30 : perMin <= 60 ? env.RL_60 : env.RL_120;
  return tier && typeof tier.limit === "function" ? tier : null;
}

async function lockedOut(env, key, asJson = false) {
  try {
    const s = await guardStub(env, key).lockState();
    return s.locked ? tooMany(s.retryAfter, asJson) : null;
  } catch (e) {
    console.error("verrou DO indisponible", e?.message);
    return null;
  }
}

const LOGIN_LOCK = { threshold: 5, baseSec: 60, capSec: 900 };   // par compte
const IP_LOCK = { threshold: 10, baseSec: 60, capSec: 3600 };    // par IP
const SECRET_LOCK = { threshold: 5, baseSec: 60, capSec: 3600 }; // OPS / ADMIN

// ---- sérialisation des mutations d'un compte ----------------------------------------

/**
 * Toute mutation d'un compte (fournisseurs, affectations, appareils, mot de passe) passe par ici : un verrou par compte dans
 * un Durable Object. `fn` reçoit le compte relu SOUS le verrou (jamais une copie lue avant l'attente), puis la version
 * (`cfgVersion`) n'est incrémentée que dans ce critique : aucune écriture perdue entre deux appareils et le tableau de bord.
 */
async function withAccountLock(env, login, fn) {
  const stub = guardStub(env, `mut:${login}`);
  let token = null;
  let unlocked = false;
  for (let i = 0; i < 60 && !token && !unlocked; i++) {
    let r;
    try { r = await stub.lockAcquire(10_000); } catch (e) {
      // Durable Object indisponible (quota gratuit épuisé, panne) : on écrit sans verrou plutôt que de couper la synchro.
      // Seul risque : deux écritures du même compte à la même milliseconde, l'une écrase l'autre.
      console.error("verrou de compte indisponible", e?.message);
      unlocked = true; break;
    }
    if (r.ok) token = r.token; else await new Promise((res) => setTimeout(res, 40 + Math.random() * 80));
  }
  if (!token && !unlocked) return json({ error: "busy" }, 503, { "retry-after": "2" });
  try {
    const acct = await getAccount(env, login);
    if (!acct) return json({ error: "gone" }, 404);
    return await fn(acct);
  } finally {
    if (token) await stub.lockRelease(token).catch(() => undefined);
  }
}

/**
 * Abonnement d'un fournisseur, gardé 10 min en mémoire de l'isolat (au mieux : aucune écriture KV). Clé liée au
 * compte, au fournisseur et à ses identifiants : un changement d'identifiants relit tout de suite.
 */
const accountCache = new Map();
async function cachedAccount(env, login, p, now = Date.now()) {
  const key = `${login}|${p.id}|${p.url}|${p.username || ""}|${p.password || ""}`;
  const hit = accountCache.get(key);
  if (hit && now - hit.at < 600_000) return hit.value;
  // Relais hors Cloudflare (facultatif) pour les fournisseurs qui bloquent les serveurs Cloudflare.
  const relay = env.ACCOUNT_RELAY_URL && env.ACCOUNT_RELAY_KEY ? { url: env.ACCOUNT_RELAY_URL, key: env.ACCOUNT_RELAY_KEY } : null;
  const value = await xtreamAccount(p, fetch, 8000, relay);
  if (accountCache.size > 500) accountCache.clear();
  // Les erreurs passagères ne sont gardées qu'1 min.
  accountCache.set(key, { at: value.error === "unreachable" ? now - 540_000 : now, value });
  return value;
}

// ---- point d'entrée -----------------------------------------------------------

/** Langue du site pour une requête : ?l=, puis cookie utv-lang, puis Accept-Language, sinon anglais. */
const siteLangOf = (req, url) => pickSiteLang({ param: url.searchParams.get("l"), cookie: langFromCookie(req.headers.get("cookie")), acceptLanguage: req.headers.get("accept-language") });

/** Les 429 en texte brut (tooMany) sont produits sans requête : on les traduit ici selon la langue de la requête. */
function localize429(req, res) {
  if (res.status !== 429 || !(res.headers.get("content-type") || "").startsWith("text/plain")) return res;
  let url;
  try { url = new URL(req.url); } catch { return res; }
  const text = TOO_MANY_TEXT[siteLangOf(req, url)] || TOO_MANY_TEXT.en;
  const headers = new Headers(res.headers);
  headers.append("vary", "Cookie, Accept-Language");
  return new Response(text, { status: 429, headers });
}

export default {
  async fetch(req, rawEnv, ctx) {
    const env = withStorage(rawEnv);
    try {
      const res = withSecurityHeaders(localize429(req, await route(req, env, ctx)));
      // Alerte avant le plafond de 50 sous-requêtes du Worker gratuit (chaque requête Turso en est une).
      const q = env.DB?.queries ?? env.DB?.m?.queries ?? 0;
      if (q > 30) console.warn("requêtes Turso nombreuses", q, new URL(req.url).pathname);
      return res;
    } catch (err) {
      console.error("erreur:", err instanceof ConfigError ? `configuration: ${err.message}` : err?.message);
      return withSecurityHeaders(json({ error: err instanceof ConfigError ? "server_misconfigured" : "internal" }, 500));
    }
  },
  // Cron quotidien : purge des lignes de télémétrie D1 expirées (équivalent des anciens TTL de KV).
  async scheduled(_event, rawEnv, ctx) {
    const env = withStorage(rawEnv);
    ctx.waitUntil(Promise.all([purgeTelemetry(env), docs.purgeExpired(env), typeof env.CONFIG?.purgeExpired === "function" ? env.CONFIG.purgeExpired() : null]));
  },
};

async function route(req, env, ctx) {
  const url = new URL(req.url);
  const { pathname: path } = url;
  const m = req.method;

  // Ancienne adresse : les pages ouvertes dans un navigateur partent vers la nouvelle (l'API reste servie ici).
  { const d = legacyDecision(m, req.url, env.SITE_HOST || API_HOST);
    if (d.kind === "redirect") return new Response(null, { status: d.status, headers: { location: d.to, "cache-control": "no-store" } }); }

  // Aucune origine tierce n'a besoin d'appeler ce Worker depuis un navigateur : pas de CORS.
  if (m === "OPTIONS") return new Response(null, { status: 204 });

  // ---- API application TV (jeton d'appareil / appairage) ----
  if (path === "/api/pair/start" && m === "POST") return pairStart(req, env);
  if (path === "/api/pair/poll" && m === "POST") return pairPoll(req, env);
  // Envoi d'une source depuis un téléphone / un ordinateur, sans compte : code + secret remis à la TV.
  if (path === "/api/envoi/start" && m === "POST") return envoiStart(req, env);
  if (path === "/api/envoi/send" && m === "POST") return envoiSend(req, env);
  if (path === "/api/envoi/poll" && m === "POST") return envoiPoll(req, env);
  // Relevé anonyme d'installation (appareils sans compte) : identifiant aléatoire, version, pays.
  if (path === "/api/anon/ping" && m === "POST") return anonPing(req, env);
  if (path === "/api/config" && m === "GET") return deviceConfig(req, env);
  if (path === "/api/device/rotate" && m === "POST") return deviceRotate(req, env);
  if (path === "/api/device/providers" && (m === "POST" || m === "PUT")) return devicePutProvider(req, env);
  const delProv = path.match(/^\/api\/device\/providers\/([0-9a-f]{8})$/);
  if (delProv && m === "DELETE") return deviceDeleteProvider(req, env, delProv[1]);
  const prefsProv = path.match(/^\/api\/device\/providers\/([0-9a-f]{8})\/prefs$/);
  if (prefsProv && m === "PUT") return devicePutPrefs(req, env, prefsProv[1]);
  const assignProv = path.match(/^\/api\/device\/providers\/([0-9a-f]{8})\/assign$/);
  if (assignProv && (m === "PUT" || m === "POST")) return deviceAssignProvider(req, env, assignProv[1]);
  if (path === "/api/device/profiles" && m === "POST") return deviceSyncProfiles(req, env);
  const stateProv = path.match(/^\/api\/device\/providers\/([0-9a-f]{8})\/state$/);
  if (stateProv && m === "POST") return deviceSyncState(req, env, stateProv[1]);
  if (path === "/api/device/handoff" && m === "POST") return deviceHandoff(req, env);
  if (path === "/api/device/license" && m === "POST") return deviceLicense(req, env);
  // Suivi (Trakt ou Simkl, au choix du compte) : /api/device/tracking/* est l'interface commune, /api/device/trakt/*
  // reste servi à l'identique pour les applications existantes.
  const trk = path.replace(/^\/api\/device\/tracking\//, "/api/device/trakt/");
  if (path === "/api/device/tracking" && (m === "GET" || m === "POST")) return deviceTracking(req, env, m);
  if (trk === "/api/device/trakt/scrobble" && m === "POST") return deviceTraktScrobble(req, env);
  if (trk === "/api/device/trakt/library" && m === "GET") return deviceTraktLibrary(req, env, url, ctx);
  if (trk === "/api/device/trakt/watchlist" && m === "POST") return deviceTraktWrite(req, env, "watchlist");
  if (trk === "/api/device/trakt/history" && m === "POST") return deviceTraktWrite(req, env, "history");
  if (trk === "/api/device/trakt/rating" && m === "POST") return deviceTraktWrite(req, env, "rating");
  if ((path === "/api/device/self" && m === "POST") || (path === "/api/device" && (m === "PATCH" || m === "POST"))) return deviceRename(req, env);
  if ((path === "/api/subtitles/search" || path === "/api/subtitles/download") && m === "GET") return deviceSubtitles(req, env, path.endsWith("/search"), url);
  if (path.startsWith("/api/tmdb/") && m === "GET") return deviceTmdb(req, env, path.slice("/api/tmdb/".length), url);
  if (path === "/api/ratings" && m === "GET") return deviceRatings(req, env, url);
  if (path.startsWith("/api/fanart/") && m === "GET") return deviceExtmeta(req, env, "fanart", () => fanartProxy(path.slice("/api/fanart/".length), url.searchParams, env));
  if (path === "/api/tvmaze/show" && m === "GET") return deviceExtmeta(req, env, "tvmaze", () => tvmazeProxy(url.searchParams, env));
  if (path === "/api/sports/team" && m === "GET") return deviceExtmeta(req, env, "sports", () => sportsTeamProxy(url.searchParams, env));
  // Alertes programmées par l'administrateur : lecture publique (un appareil pas encore appairé doit aussi les voir).
  if (path === "/api/announcements" && m === "GET") {
    const aud = url.searchParams.get("audience");
    const items = activeAnnouncements(await loadAnnouncements(env), Date.now(), ANN_AUDIENCES.includes(aud) && aud !== "all" ? aud : null, url.searchParams.get("version"), url.searchParams.get("lang") || fromAcceptLanguage(req.headers.get("accept-language")));
    return json({ items }, 200, { "cache-control": "public, max-age=60" });
  }
  { const sm = path.match(/^\/api\/announcements\/([0-9a-f]{8,32})\/stat$/); if (sm && m === "POST") return announcementStat(req, env, sm[1]); }
  if (path === "/api/event" && m === "POST") return ingest(req, env, "event");
  if (path === "/api/events" && m === "POST") return ingestBatch(req, env);
  if (path === "/api/crash" && m === "POST") return ingest(req, env, "crash");
  // Ancienne lecture anonyme par MAC : supprimée (la MAC n'est pas un secret).
  if (/^\/api\/config\//.test(path)) {
    return json({ error: "pairing_required", message: "La lecture par adresse MAC est supprimée. Mets à jour l'application et appaire-la." }, 410);
  }

  // ---- exploitation ----
  // Journaux et plantages : session administrateur, ou secret d'exploitation (Basic). `/logs` = ancien nom de `/events`.
  if ((path === "/crashes" || path === "/logs" || path === "/events") && m === "GET") return opsPage(req, env, url, path === "/crashes" ? "crash" : "event");
  if (path === "/api/admin/migrate" && m === "POST") return adminMigrate(req, env, url);

  // ---- polices auto-hébergées (publiques, immuables) ----
  const font = path.match(/^\/assets\/([a-z]+\.woff2)$/);
  if (font && m === "GET") return fontResponse(font[1]) || new Response("Not found", { status: 404 });

  // ---- lecteur de QR vendorisé (jsQR, Apache-2.0) : chargé seulement à l'ouverture du scanner ----
  if (path === "/assets/jsqr.js" && m === "GET") return new Response(JSQR_SOURCE, { headers: { "content-type": "text/javascript; charset=utf-8", "cache-control": "public, max-age=31536000, immutable", "x-content-type-options": "nosniff" } });

  // ---- pages publiques ----
  { const dm = path.match(/^\/download\/(android|android-arm64|android-32|android-armv7|android-universal|windows|mac|linux|linux-deb|linux-rpm)$/); if (dm && (m === "GET" || m === "HEAD")) return downloadRedirect(dm[1]); }
  if ((path === "/apk" || path === "/download") && (m === "GET" || m === "HEAD")) return apkPage(nonce(), { lang: siteLangOf(req, url), url: req.url });
  // Application web installable (manifeste, service worker, icônes, page hors ligne).
  if (m === "GET" || m === "HEAD") { const pw = pwaResponse(path, () => offlinePage(nonce(), { lang: siteLangOf(req, url), url: path })); if (pw) return pw; }
  if (path === "/envoyer" && m === "GET") {
    // Langue : paramètre `l`, puis cookie de préférence, puis Accept-Language, sinon anglais.
    const lang = pickEnvoyerLang({ param: url.searchParams.get("l"), cookie: langFromCookie(req.headers.get("cookie")), acceptLanguage: req.headers.get("accept-language") });
    return envoyerPage(nonce(), { code: normalizeEnvoiCode(url.searchParams.get("c")), lang });
  }
  // Dons : page publique (liens Ko-fi et Liberapay). /donate/merci ne sert plus : 302 vers /donate pour les vieux liens.
  if (path === "/donate/merci" && (m === "GET" || m === "HEAD")) return redirect("/donate");
  if (path === "/donate" && (m === "GET" || m === "HEAD")) {
    const lang = pickEnvoyerLang({ param: url.searchParams.get("l"), cookie: langFromCookie(req.headers.get("cookie")), acceptLanguage: req.headers.get("accept-language") });
    return donatePage(nonce(), { lang });
  }
  if (path === "/login" && m === "GET") { const n = nonce(); return loginPage(n, url.searchParams.get("e"), safeNext(url.searchParams.get("next")), { lang: siteLangOf(req, url), url: req.url }); }
  if (path === "/login" && m === "POST") return doLogin(req, env);
  if (path === "/signup" && m === "GET") { const n = nonce(); return signupPage(n, url.searchParams.get("e"), safeNext(url.searchParams.get("next")), { lang: siteLangOf(req, url), url: req.url }); }
  if (path === "/signup" && m === "POST") return doSignup(req, env);

  // Retour de Trakt : AVANT la session. Le cookie de session est SameSite=Strict, donc absent d'une navigation venue
  // de trakt.tv ; le retour est authentifié par l'état à usage unique ET le cookie SameSite=Lax posé au départ.
  if (path === "/trakt/callback" && m === "GET") return traktFinish(req, env, url);
  if (path === "/simkl/callback" && m === "GET") return simklFinish(req, env, url);

  // ---- tableau de bord (session) ----
  const sess = await currentSession(req, env);
  const dashboardRoute = (path === "/" || path === "/dashboard") && m === "GET";
  const adminRoute = /^\/admin(\/(accounts|anon|devices|alerts|comptes\.csv))?$/.test(path) && m === "GET";
  const pairRoute = path === "/pair" && m === "GET";
  const mutating = m === "POST" && ["/pair", "/providers", "/password", "/account/delete", "/logout", "/subtitles/link", "/subtitles/unlink", "/trakt/connect", "/trakt/disconnect", "/simkl/connect", "/simkl/disconnect", "/tracking/service"].includes(path)
    || (m === "POST" && /^\/(devices\/[0-9a-f]+\/(revoke|rename)|providers\/[0-9a-f]+\/(delete|assign|link|account)|admin\/announcements(\/[0-9a-f]+\/(end|delete))?)$/.test(path));
  if (!dashboardRoute && !pairRoute && !adminRoute && !mutating) return new Response("Not found", { status: 404 });
  if (!sess) {
    const c = pairRoute ? normalizeCode(url.searchParams.get("code")) : null;
    return redirect(c ? `/login?next=${encodeURIComponent(`/pair?code=${c}`)}` : "/login");
  }

  // Lien/QR affiché par la TV : on confirme avec un POST explicite, le GET n'appaire jamais.
  if (pairRoute) {
    const raw = url.searchParams.get("code");
    const c = normalizeCode(raw);
    return pairPage(nonce(), { acct: sess.acct, csrf: sess.csrf, code: c, invalid: Boolean(raw) && !c, admin: isAdmin(env, sess.acct), lang: siteLangOf(req, url), url: req.url });
  }

  if (adminRoute) {
    // Réservé aux identifiants listés dans ADMIN_LOGINS ; tout autre compte reçoit un 404 (l'existence n'est pas révélée).
    if (!isAdmin(env, sess.acct)) return new Response("Not found", { status: 404 });
    if (!path.endsWith(".csv")) return adminView(env, url, path, sess);
    return new Response(adminCsv(await adminAccounts(env)), { headers: {
      "content-type": "text/csv; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff",
      "content-disposition": `attachment; filename="ultratv-comptes-${new Date().toISOString().slice(0, 10)}.csv"`,
    } });
  }

  if (dashboardRoute) {
    const n = nonce();
    const providers = await loadProviders(env, sess.acct);
    const admin = isAdmin(env, sess.acct);
    return dashboardPage(n, { acct: sess.acct, providers, csrf: sess.csrf, err: url.searchParams.get("e"), ok: url.searchParams.get("m"), admin, lang: siteLangOf(req, url), url: req.url, simkl: simklEnabled(env), traktOn: !!env.TRAKT_CLIENT_ID });
  }

  // Mutations : Origin + jeton CSRF + débit par compte.
  if (!sameOrigin(req)) return new Response("Forbidden", { status: 403 });
  const parsed = await readForm(req);
  if (parsed.error) return parsed.error;
  const form = parsed.form;
  if (!timingSafeEqual(form.get("csrf") || "", sess.csrf)) return new Response("Forbidden (csrf)", { status: 403 });
  if (path.startsWith("/admin/announcements")) return adminAnnouncement(env, sess.acct, path, form);
  // Abonnements : une requête par fournisseur Xtream à chaque ouverture du tableau de bord (jusqu'à 20) — limite
  // PROPRE, sinon quelques rechargements épuisaient celle des actions (appairage, partage, suppression).
  const acc = path.match(/^\/providers\/([0-9a-f]+)\/account$/);
  if (acc) {
    const rla = await limited(env, `acctinfo:${sess.acct.login}`, 600, 3600);
    if (rla) return rla;
    const p = (await loadProviders(env, sess.acct)).find((x) => x.id === acc[1]);
    if (!p) return new Response("Not found", { status: 404 });
    return new Response(JSON.stringify(await cachedAccount(env, sess.acct.login, p)), { headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" } });
  }

  const rl = await limited(env, `dash:${sess.acct.login}`, 120, 3600);
  if (rl) return rl;

  // Lien IPTV complet, à la demande (jamais dans la page elle-même) : réponse non mise en cache.
  const lnk = path.match(/^\/providers\/([0-9a-f]+)\/link$/);
  if (lnk) {
    const p = (await loadProviders(env, sess.acct)).find((x) => x.id === lnk[1]);
    if (!p) return new Response("Not found", { status: 404 });
    // Source du revendeur : ses identifiants ne sont pas ceux du client, ils ne sont jamais révélés.
    if (p.managed === "reseller") return new Response(JSON.stringify({ error: "managed" }), { status: 403, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });
    return new Response(JSON.stringify({ link: iptvLink(p) }), { headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" } });
  }

  return withAccountLock(env, sess.acct.login, async (acct) => {
  if (path === "/logout") {
    acct.sessEpoch = (acct.sessEpoch || 0) + 1; // invalide aussi le cookie s'il a fuité
    await putAccount(env, acct);
    return redirect("/login", { "set-cookie": clearCookieHeader() });
  }
  if (path === "/pair") return confirmPairing(env, acct, form);
  if (path === "/providers") return addProvider(env, acct, form);
  if (path === "/password") return changePassword(env, acct, form, req);
  if (path === "/subtitles/link") return linkOpenSubtitles(env, acct, form);
  if (path === "/trakt/connect") return traktStart(env, acct, url);
  if (path === "/simkl/connect") return simklStart(env, acct, url);
  if (path === "/simkl/disconnect") {
    // Révocation chez Simkl (un seul appel, jamais bloquante : l'échec est ignoré).
    if (acct.simklEnc && simklEnabled(env)) {
      const t = await decryptJson(keysFromEnv(env), acct.simklEnc, acct.login).catch(() => null);
      if (t && typeof t.access === "string") await simklRevoke(env.SIMKL_CLIENT_ID, t.access, fetch, appVersionOf(req.headers.get("x-ultra-version"))).catch(() => {});
    }
    delete acct.simklEnc;
    delete acct.simkl;
    await putAccount(env, acct);
    await forgetTraktLibrary(env, acct.login);
    return redirect("/?m=simkl_off#compte");
  }
  if (path === "/tracking/service") {
    const choice = String(form.get("service") || "");
    if (!TRACKING_CHOICES.includes(choice) || (choice === "simkl" && !simklEnabled(env))) return redirect("/?e=tracking#compte");
    acct.tracking = choice;
    await putAccount(env, acct);
    await forgetTraktLibrary(env, acct.login);
    return redirect("/?m=tracking_saved#compte");
  }
  if (path === "/trakt/disconnect") {
    if (acct.traktEnc && env.TRAKT_CLIENT_ID) {
      const t = await decryptJson(keysFromEnv(env), acct.traktEnc, acct.login).catch(() => null);
      if (t) await revokeToken(env.TRAKT_CLIENT_ID, t.access);
    }
    delete acct.traktEnc;
    delete acct.trakt;
    await putAccount(env, acct);
    await forgetTraktLibrary(env, acct.login, true);
    return redirect("/?m=trakt_off#compte");
  }
  if (path === "/subtitles/unlink") {
    delete acct.osEnc;
    delete acct.os;
    await putAccount(env, acct);
    await docs.delete(env, osTokenKey(acct.login));
    return redirect("/?m=os_off#compte");
  }
  if (path === "/account/delete") return removeAccount(env, acct, form);
  const rev = path.match(/^\/devices\/([0-9a-f]+)\/revoke$/);
  if (rev) {
    if (await revokeDevice(env, acct, rev[1])) {
      // Un appareil révoqué sort de toutes les affectations.
      await saveProviders(env, acct, dropDeviceFromAssignments(await loadProviders(env, acct), rev[1]));
      await putAccount(env, acct);
    }
    return redirect("/?m=revoked");
  }
  const ren = path.match(/^\/devices\/([0-9a-f]+)\/rename$/);
  if (ren) {
    await renameDevice(env, acct, ren[1], form.get("name") || "");
    return redirect("/?m=renamed");
  }
  const asg = path.match(/^\/providers\/([0-9a-f]+)\/assign$/);
  if (asg) {
    const all = form.get("all") === "1";
    const r = parseAssign(all ? "all" : form.getAll("d"), acct);
    if (r.error) return redirect("/?e=assign");
    const providers = await loadProviders(env, acct);
    if (!providers.some((p) => p.id === asg[1])) return redirect("/");
    await saveProviders(env, acct, providers.map((p) => (p.id === asg[1] ? { ...p, assign: r.assign, updatedAt: Date.now() } : p)));
    await putAccount(env, acct);
    return redirect("/?m=assigned");
  }
  const del = path.match(/^\/providers\/([0-9a-f]+)\/delete$/);
  if (!del) return new Response("Not found", { status: 404 });
  const providers = await loadProviders(env, acct);
  await saveProviders(env, acct, providers.filter((p) => p.id !== del[1]));
  // Suppression propagée : les appareils retirent leur copie à leur prochaine synchro (cfgVersion a changé).
  if (providers.some((p) => p.id === del[1])) addTombstones(acct, [del[1]]);
  await deleteState(env, acct.login, del[1]);
  await putAccount(env, acct);
  return redirect("/");
  });
}

// Retour après connexion : uniquement /pair?code=XXXXXXXX (chemin relatif strict, jamais d'open redirect).
const NEXT_RE = /^\/pair\?code=[A-Z2-9]{8}$/;
const safeNext = (v) => (typeof v === "string" && NEXT_RE.test(v) ? v : "/");
const nextQuery = (to) => (to === "/" ? "" : `&next=${encodeURIComponent(to)}`);

// ---- sessions -------------------------------------------------------------------

async function currentSession(req, env) {
  assertSessionSecret(env.SESSION_SECRET); // échec fermé, même sans cookie
  const s = await verifySession(env.SESSION_SECRET, readCookie(req, COOKIE));
  if (!s) return null;
  const acct = await getAccount(env, s.login);
  if (!acct || (acct.sessEpoch || 0) !== s.epoch) return null;
  return { acct, csrf: await csrfToken(env.SESSION_SECRET, s.sid) };
}

async function startSession(env, acct, to = "/") {
  const value = await signSession(env.SESSION_SECRET, { login: acct.login, epoch: acct.sessEpoch || 0 });
  return redirect(to, { "set-cookie": sessionCookieHeader(value) });
}

// ---- connexion / inscription -----------------------------------------------------

async function doLogin(req, env) {
  if (!sameOrigin(req)) return new Response("Forbidden", { status: 403 });
  const ip = clientIp(req);
  const rl = await limited(env, `login:ip:${ip}`, 20, 900);
  if (rl) return rl;
  const locked = await lockedOut(env, `lock:ip:${ip}`);
  if (locked) return locked;
  const parsed = await readForm(req);
  if (parsed.error) return parsed.error;
  const to = safeNext(parsed.form.get("next"));
  const rawLogin = (parsed.form.get("login") || "").slice(0, 128);
  const password = (parsed.form.get("password") || "").slice(0, 256);
  const login = normalizeLogin(rawLogin) || `invalid:${await sha256Hex(rawLogin)}`;

  const lockKey = `lock:acct:${login}`;
  const acctLocked = await lockedOut(env, lockKey);
  if (acctLocked) return acctLocked;

  const acct = login.startsWith("invalid:") ? null : await getAccount(env, login);
  const verdict = await verifyPassword(acct, password); // coût identique si le compte n'existe pas
  if (!acct || !verdict.ok) {
    const [a, b] = await Promise.all([
      guardStub(env, lockKey).lockFail(LOGIN_LOCK),
      guardStub(env, `lock:ip:${ip}`).lockFail(IP_LOCK),
    ]);
    if (a.locked || b.locked) return tooMany(Math.max(a.retryAfter, b.retryAfter));
    return redirect(`/login?e=pw${nextQuery(to)}`);
  }
  await guardStub(env, lockKey).lockClear();
  if (verdict.needsRehash) {
    acct.passwordHash = await hashPassword(password);
    delete acct.salt;
    await putAccount(env, acct);
  }
  // Dernière connexion et pays (code Cloudflare, jamais l'adresse IP) : vue d'administration.
  const country = countryOf(req);
  if (Date.now() - (acct.lastLoginAt || 0) > 3600_000 || (country && acct.country !== country)) {
    await withAccountLock(env, acct.login, async (a) => {
      a.lastLoginAt = Date.now();
      if (country) a.country = country;
      await putAccount(env, a);
    });
  }
  return startSession(env, acct, to);
}

/** Version d'application déclarée (« 1.2.65 », « 1.2.35-desktop »…), ou null si absente / invalide. */
function appVersionOf(raw) {
  const v = String(raw ?? "").trim();
  return /^[0-9][0-9A-Za-z.+-]{0,31}$/.test(v) ? v : null;
}

/** Pays de la requête (code ISO à 2 lettres fourni par Cloudflare), ou null. L'adresse IP n'est jamais gardée. */
function countryOf(req) {
  const c = req.cf && req.cf.country;
  return typeof c === "string" && /^[A-Z]{2}$/.test(c) ? c : null;
}

async function doSignup(req, env) {
  if (!sameOrigin(req)) return new Response("Forbidden", { status: 403 });
  const rl = await limited(env, `signup:ip:${clientIp(req)}`, 5, 3600);
  if (rl) return rl;
  const parsed = await readForm(req);
  if (parsed.error) return parsed.error;
  const f = parsed.form;
  const to = safeNext(f.get("next"));
  const login = normalizeLogin(f.get("login"));
  const password = (f.get("password") || "").slice(0, 256);
  if (!login) return redirect(`/signup?e=login${nextQuery(to)}`);
  if (password.length < MIN_PASSWORD) return redirect(`/signup?e=short${nextQuery(to)}`);
  if (password !== (f.get("confirm") || "")) return redirect(`/signup?e=mismatch${nextQuery(to)}`);
  // Un compte hérité (clé = MAC en clair) ne peut pas être « squatté » avant sa migration.
  if (isMacLogin(login) && (await env.CONFIG.get(login)) !== null) return redirect(`/signup?e=taken${nextQuery(to)}`);
  if (!(await guardStub(env, `acct:${login}`).claim())) return redirect(`/signup?e=taken${nextQuery(to)}`);
  try {
    const acct = { login, passwordHash: await hashPassword(password), devices: [], sessEpoch: 0, createdAt: Date.now(), country: countryOf(req) };
    await saveProviders(env, acct, []);
    await putAccount(env, acct);
    return await startSession(env, acct, to);
  } catch (err) {
    await guardStub(env, `acct:${login}`).release();
    throw err;
  }
}

// ---- appairage ------------------------------------------------------------------

function newPairCode() {
  return [...crypto.getRandomValues(new Uint8Array(8))].map((b) => PAIR_ALPHABET[b & 31]).join("");
}
const formatCode = (c) => `${c.slice(0, 4)}-${c.slice(4)}`;
const normalizeCode = (raw) => {
  const c = String(raw ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  return c.length === 8 && [...c].every((ch) => PAIR_ALPHABET.includes(ch)) ? c : null;
};

async function pairStart(req, env) {
  const rl = await limited(env, `pairstart:ip:${clientIp(req)}`, 10, 3600, true);
  if (rl) return rl;
  const body = await readJson(req, 2048);
  if (body.error) return body.error;
  const label = sanitizeText(typeof body.value.label === "string" ? body.value.label : "", 64);
  const pollSecret = randomToken(32);
  const secretHash = await sha256Hex(pollSecret);
  for (let i = 0; i < 5; i++) {
    const code = newPairCode();
    const r = await guardStub(env, `pair:${code}`).pairInit({ secretHash, label, ttlSec: PAIR_TTL_S });
    if (r.ok) return json({ code: formatCode(code), pollSecret, expiresIn: PAIR_TTL_S, interval: 5 });
  }
  return json({ error: "unavailable" }, 503);
}

async function pairPoll(req, env) {
  const rl = await limited(env, `pairpoll:ip:${clientIp(req)}`, 300, 600, true);
  if (rl) return rl;
  const body = await readJson(req, 2048);
  if (body.error) return body.error;
  const code = normalizeCode(body.value.code);
  const secret = typeof body.value.pollSecret === "string" ? body.value.pollSecret.slice(0, 128) : "";
  if (!code || !secret) return json({ error: "not_found" }, 404);
  const r = await guardStub(env, `pair:${code}`).pairPoll(await sha256Hex(secret));
  if (r.status === "pending") return json({ status: "pending" }, 202);
  if (r.status === "ready") return json({ token: r.token, deviceId: r.deviceId });
  return json({ error: "not_found" }, 404);
}

// ---- envoi d'une source depuis un téléphone / un ordinateur (sans compte) ----------------
// 1. La TV demande un code (6 caractères) et un secret ; elle affiche le code et un QR vers /envoyer?c=CODE.
// 2. Le téléphone envoie la source (page publique) ; elle est chiffrée, gardée 15 min au plus dans le Durable Object du code.
// 3. La TV l'interroge (code + secret) : la source lui est remise UNE fois puis supprimée.
// Les identifiants de la source ne sont jamais journalisés (ni console, ni événements).

const ENVOI_TTL_S = 900;
const ENVOI_LOCK = { threshold: 8, baseSec: 60, capSec: 3600 }; // par IP : codes devinés
const ENVOI_KINDS = ["XTREAM", "M3U", "STALKER", "GUIDE"];

/** Adresse de guide XMLTV : http(s), hôte, chemin finissant par .xml ou .xml.gz (query string tolérée). null si invalide. */
function parseGuideUrl(raw) {
  if (typeof raw !== "string" || raw.length > 2048) return null;
  let u;
  try { u = new URL(raw.trim()); } catch { return null; }
  if ((u.protocol !== "http:" && u.protocol !== "https:") || !u.hostname) return null;
  const path = u.pathname.toLowerCase();
  return path.endsWith(".xml") || path.endsWith(".xml.gz") ? u.href : null;
}

function newEnvoiCode() {
  return [...crypto.getRandomValues(new Uint8Array(6))].map((b) => PAIR_ALPHABET[b & 31]).join("");
}
/** « k7q-2m9 » → « K7Q2M9 » ; null si la longueur ou l'alphabet ne conviennent pas. */
function normalizeEnvoiCode(raw) {
  const c = String(raw ?? "").toUpperCase().replace(/[^A-Z0-9]/g, "");
  return c.length === 6 && [...c].every((ch) => PAIR_ALPHABET.includes(ch)) ? c : null;
}

// Identifiant d'installation aléatoire de l'appli (UUID) : seul son empreinte est gardée.
const validInstall = (v) => (typeof v === "string" && /^[A-Za-z0-9-]{16,64}$/.test(v) ? v : null);
const anonKey = async (install) => `anon:${(await sha256Hex(`anon:${install}`)).slice(0, 32)}`;

async function envoiStart(req, env) {
  const rl = await limited(env, `envoistart:ip:${clientIp(req)}`, 20, 3600, true);
  if (rl) return rl;
  const body = await readJson(req, 2048);
  if (body.error) return body.error;
  const install = validInstall(body.value.install);
  const secret = randomToken(32);
  const secretHash = await sha256Hex(secret);
  const installHash = install ? await anonKey(install) : "";
  for (let i = 0; i < 5; i++) {
    const code = newEnvoiCode();
    const r = await guardStub(env, `envoi:${code}`).envoiInit({ secretHash, installHash, ttlSec: ENVOI_TTL_S });
    if (r.ok) return json({ code, secret, expiresIn: ENVOI_TTL_S, interval: 5 });
  }
  return json({ error: "unavailable" }, 503);
}

async function envoiSend(req, env) {
  if (!sameOrigin(req)) return json({ error: "forbidden" }, 403);
  const ip = clientIp(req);
  const lock = await lockedOut(env, `lock:envoi:${ip}`, true);
  if (lock) return lock;
  const rl = await limited(env, `envoisend:ip:${ip}`, 30, 3600, true);
  if (rl) return rl;
  const body = await readJson(req, 8 * 1024);
  if (body.error) return body.error;
  const b = body.value;
  const code = normalizeEnvoiCode(b.code);
  // Même validation que le tableau de bord et les appareils (adresse http/https, MAC, identifiants).
  const kind = typeof b.kind === "string" ? b.kind.toUpperCase() : "";
  if (!ENVOI_KINDS.includes(kind)) return json({ error: "kind" }, 400);
  // GUIDE : adresse d'un guide XMLTV pour le guide complémentaire de la TV (pas une source ; la TV la vérifie avant de l'enregistrer).
  const guideUrl = kind === "GUIDE" ? parseGuideUrl(b.url) : null;
  if (kind === "GUIDE" && !guideUrl) return json({ error: "url" }, 400);
  const r = kind === "GUIDE" ? { provider: { kind, name: "", url: guideUrl } } : parseDeviceProvider({ kind, name: b.name, url: b.url, username: b.username, password: b.password, mac: b.mac }, null);
  if (r.error) return json({ error: r.error }, 400);
  const fail = async () => {
    const f = await guardStub(env, `lock:envoi:${ip}`).lockFail(ENVOI_LOCK);
    return f.locked ? tooMany(f.retryAfter, true) : json({ error: "code" }, 404);
  };
  if (!code) return fail();
  const p = r.provider;
  const named = typeof b.name === "string" && sanitizeText(b.name, 64).trim() ? p.name : "";
  const blob = await encryptJson(keysFromEnv(env), {
    kind: p.kind, name: named, url: p.url, username: p.username || "", password: p.password || "", ...(p.kind === "STALKER" ? { mac: p.mac } : {}),
  }, `envoi:${code}`);
  const put = await guardStub(env, `envoi:${code}`).envoiPut(blob);
  if (put.status === "unknown") return fail();
  if (put.status === "used") return json({ error: "used" }, 409);
  return json({ ok: true });
}

async function envoiPoll(req, env) {
  const rl = await limited(env, `envoipoll:ip:${clientIp(req)}`, 300, 600, true);
  if (rl) return rl;
  const body = await readJson(req, 2048);
  if (body.error) return body.error;
  const code = normalizeEnvoiCode(body.value.code);
  const secret = typeof body.value.secret === "string" ? body.value.secret.slice(0, 128) : "";
  if (!code || !secret) return json({ error: "not_found" }, 404);
  const r = await guardStub(env, `envoi:${code}`).envoiTake(await sha256Hex(secret));
  if (r.status === "pending") return json({ status: "pending" }, 202);
  if (r.status !== "ready") return json({ error: "not_found" }, 404);
  let provider;
  try { provider = await decryptJson(keysFromEnv(env), r.blob, `envoi:${code}`); } catch { return json({ error: "unreadable" }, 410); }
  // Compte l'envoi dans les statistiques de l'installation (si la TV a communiqué son identifiant, télémétrie active).
  if (r.installHash) await anonTouch(env, r.installHash, req, { web: 1 }).catch(() => {});
  return json({ status: "ready", provider });
}

// ---- appareils sans compte : relevé anonyme -------------------------------------------------
// Table D1 `anon_pings` (empreinte, premier / dernier relevé, version, pays, plateforme, modèle, sources envoyées par le web), 180 jours.
// Plus aucune écriture KV ici : voir telemetry.js.

/** Met à jour (ou crée) l'installation [key] ; `web` : sources envoyées en plus. Ne lève jamais d'erreur. */
async function anonTouch(env, key, req, { version = null, web = 0, platform = null, model = null } = {}) {
  await anonTouchD1(env, key, {
    version, web, platform, model, country: countryOf(req),
    // Transition : reprend premier relevé / version / pays / compteur de l'ancienne ligne KV (lecture seule).
    legacy: async () => (await env.CONFIG.getWithMetadata(key, "json")).metadata,
  });
}

async function anonPing(req, env) {
  const rl = await limited(env, `anonping:ip:${clientIp(req)}`, 30, 3600, true);
  if (rl) return rl;
  const body = await readJson(req, 1024);
  if (body.error) return body.error;
  const install = validInstall(body.value.install);
  if (!install) return json({ error: "install" }, 400);
  // Plateforme : liste blanche (une valeur inconnue est refusée) ; modèle : texte court nettoyé. Jamais d'IP.
  const rawPlatform = body.value.platform;
  const platform = platformOf(rawPlatform);
  if (rawPlatform != null && rawPlatform !== "" && !platform) return json({ error: "platform" }, 400);
  await anonTouch(env, await anonKey(install), req, { version: appVersionOf(body.value.version), platform, model: modelOf(body.value.model) });
  return json({ ok: true });
}

async function confirmPairing(env, acct, form) {
  const lock = await lockedOut(env, `lock:pair:${acct.login}`);
  if (lock) return lock;
  const rl = await limited(env, `pairconfirm:${acct.login}`, 30, 3600);
  if (rl) return rl;
  if ((acct.devices || []).length >= MAX_DEVICES) return redirect("/?e=limit");
  const code = normalizeCode(form.get("code"));
  const token = newDeviceToken();
  const deviceId = [...crypto.getRandomValues(new Uint8Array(6))].map((b) => b.toString(16).padStart(2, "0")).join("");
  const r = code ? await guardStub(env, `pair:${code}`).pairConfirm({ login: acct.login, deviceId, token }) : { ok: false };
  if (!r.ok) {
    const f = await guardStub(env, `lock:pair:${acct.login}`).lockFail(LOGIN_LOCK);
    return f.locked ? tooMany(f.retryAfter) : redirect("/?e=code");
  }
  const name = sanitizeText(form.get("name") || "", 40) || sanitizeText(r.label || "", 40) || "Appareil";
  await registerDevice(env, acct, { token, deviceId, name, label: r.label });
  return redirect("/?m=paired");
}

// ---- opérations du tableau de bord ---------------------------------------------

async function addProvider(env, acct, form) {
  const providers = await loadProviders(env, acct);
  if (providers.length >= MAX_PROVIDERS) return redirect("/?e=limit");
  const r = parseProvider(form, { deviceId: "", name: "dashboard" });
  if (r.error) return redirect(`/?e=${r.error}`);
  providers.push(r.provider);
  await saveProviders(env, acct, providers);
  await putAccount(env, acct);
  return redirect("/?m=added");
}

async function changePassword(env, acct, form, req) {
  const lock = await lockedOut(env, `lock:acct:${acct.login}`);
  if (lock) return lock;
  const verdict = await verifyPassword(acct, (form.get("current") || "").slice(0, 256));
  if (!verdict.ok) {
    const f = await guardStub(env, `lock:acct:${acct.login}`).lockFail(LOGIN_LOCK);
    return f.locked ? tooMany(f.retryAfter) : redirect("/?e=pw");
  }
  const next = (form.get("password") || "").slice(0, 256);
  if (next.length < MIN_PASSWORD) return redirect("/?e=short");
  acct.passwordHash = await hashPassword(next);
  delete acct.salt;
  acct.sessEpoch = (acct.sessEpoch || 0) + 1; // toutes les anciennes sessions meurent
  await putAccount(env, acct);
  return startSession(env, acct, "/?m=pw");
}

// ---- administration ---------------------------------------------------------------

function isAdmin(env, acct) {
  const list = String(env.ADMIN_LOGINS || "").split(",").map((x) => x.trim()).filter(Boolean);
  return Boolean(acct && list.includes(acct.login));
}

/**
 * Alertes (administrateur seulement, 404 pour tout autre compte) : POST /admin/announcements crée,
 * …/{id}/end arrête la diffusion maintenant, …/{id}/delete supprime. Retour sur la section Administration.
 */
async function adminAnnouncement(env, acct, path, form) {
  if (!isAdmin(env, acct)) return new Response("Not found", { status: 404 });
  const now = Date.now();
  const list = await loadAnnouncements(env);
  const m = path.match(/^\/admin\/announcements\/([0-9a-f]+)\/(end|delete)$/);
  if (m) {
    const i = list.findIndex((a) => a.id === m[1]);
    if (i < 0) return redirect("/admin/alerts?e=ann_missing");
    if (m[2] === "delete") {
      list.splice(i, 1);
      await annStatsStub(env, m[1]).annReset().catch(() => {});
    }
    else list[i] = { ...list[i], endAt: now, startAt: Math.min(list[i].startAt || now, now) };
    await saveAnnouncements(env, list, now);
    return redirect(`/admin/alerts?m=${m[2] === "delete" ? "ann_deleted" : "ann_ended"}`);
  }
  const parsed = parseAnnouncementForm(form, now);
  if (parsed.error) return redirect(`/admin/alerts?e=${parsed.error}`);
  await saveAnnouncements(env, [...list, { ...parsed.value, by: acct.login }], now);
  return redirect("/admin/alerts?m=ann_added");
}

/** Instance du Durable Object qui compte les statistiques d'une alerte. */
const annStatsStub = (env, id) => guardStub(env, `ann:${id}`);

/**
 * Statistiques d'une alerte, envoyées par l'application : { kind: shown|dismissed|action, device: identifiant
 * d'installation aléatoire }. Route publique comme la lecture (aucun appairage exigé) ; l'identifiant n'est gardé
 * que haché, chaque appareil n'est compté qu'une fois par type, et l'alerte doit exister.
 */
async function announcementStat(req, env, id) {
  const rl = await limited(env, `annstat:ip:${clientIp(req)}`, 120, 3600, true);
  if (rl) return rl;
  const body = await readJson(req, 1024);
  if (body.error) return body.error;
  const kind = body.value?.kind;
  const device = typeof body.value?.device === "string" ? body.value.device.trim() : "";
  if (!ANN_STAT_KINDS.includes(kind) || device.length < 8 || device.length > 64) return json({ error: "bad_request" }, 400);
  const a = (await loadAnnouncements(env)).find((x) => x.id === id);
  if (!a) return json({ error: "not_found" }, 404);
  const expire = (a.endAt || Date.now() + 365 * 86400_000) + 31 * 86400_000;
  await annStatsStub(env, id).annMark(kind, (await sha256Hex(`ann:${device}`)).slice(0, 32), expire);
  return json({ ok: true });
}

/** Statistiques de chaque alerte de la liste (vue d'administration), en parallèle ; zéros si une lecture échoue. */
async function announcementStats(env, list) {
  const zero = { shown: 0, dismissed: 0, action: 0 };
  const rows = await Promise.all(list.map((a) => annStatsStub(env, a.id).annStats().catch(() => zero)));
  return Object.fromEntries(list.map((a, i) => [a.id, rows[i]]));
}

const LEGACY_LOGIN = /^([0-9a-f]{2}:){5}[0-9a-f]{2}$/;

/**
 * Vue d'administration : métadonnées des comptes, JAMAIS de secret (empreinte de mot de passe, sources chiffrées,
 * jetons Trakt / OpenSubtitles exclus). Lecture de tous les comptes (au plus 2000), 50 en parallèle.
 */
async function adminAccounts(env) {
  const names = [];
  names.push(...(await docs.keys(env, "acct:", 2000)));
  const act = await deviceActivity(env);
  const rows = [];
  for (let i = 0; i < names.length; i += 50) {
    const part = names.slice(i, i + 50);
    const many = await docs.getMany(env, part);
    const got = part.map((n) => many.get(n) ?? null);
    for (const raw of got) {
      let a;
      try { a = JSON.parse(raw); } catch { continue; }
      if (!a || !a.login) continue;
      const devices = (a.devices || []).map((d) => withActivity(d, act)).map((d) => ({
        name: d.name || "", model: d.label || "", edition: d.edition || "standard", lastSeen: d.lastSeen || 0, version: d.version || "",
        country: d.country || null, createdAt: d.createdAt || 0,
      }));
      // Nombre et type des sources : déchiffrées pour être COMPTÉES seulement ; rien d'autre n'en sort.
      let sourcesN = null, sourcesXtream = 0, sourcesM3u = 0;
      if (a.providersEnc) {
        const provs = await loadProviders(env, a).catch(() => null);
        if (provs) { sourcesN = provs.length; for (const p of provs) { if (p.kind === "XTREAM") sourcesXtream++; else sourcesM3u++; } }
      } else sourcesN = 0;
      rows.push({
        login: a.login, sourcesN, sourcesXtream, sourcesM3u,
        legacy: LEGACY_LOGIN.test(a.login),
        createdAt: a.createdAt || 0,
        lastLoginAt: a.lastLoginAt || 0,
        country: a.country || null,
        devices,
        lastSeen: Math.max(0, ...devices.map((d) => d.lastSeen)),
        sources: Boolean(a.providersEnc),
        trakt: a.traktEnc ? (a.trakt && a.trakt.user) || "oui" : a.simklEnc ? `simkl:${(a.simkl && a.simkl.user) || "oui"}` : "",
        opensubtitles: (a.os && a.os.user) || "",
      });
    }
  }
  rows.sort((x, y) => (x.legacy - y.legacy) || (y.createdAt - x.createdAt));
  return rows;
}

function adminCsv(rows) {
  const d = (t) => (t ? new Date(t).toISOString().slice(0, 16).replace("T", " ") : "");
  // Cellules neutralisées contre l'injection de formules (=, +, -, @ en tête) et échappées.
  const cell = (v) => { let s = String(v ?? ""); if (/^[=+\-@\t\r]/.test(s)) s = "'" + s; return `"${s.replace(/"/g, '""')}"`; };
  rows = rows.filter((r) => !r.legacy);
  const head = ["compte", "email", "cree_le", "derniere_connexion", "pays_compte", "nb_appareils", "appareils", "derniere_activite", "pays_appareils", "nb_sources", "sources_xtream", "sources_m3u", "trakt", "opensubtitles"];
  const lines = rows.map((r) => [
    r.login, r.login.includes("@") ? r.login : "", d(r.createdAt), d(r.lastLoginAt), r.country || "",
    r.devices.length, r.devices.map((x) => `${x.name} (${x.model}${x.version ? `, v${x.version}` : ""}${x.lastSeen ? `, vu ${d(x.lastSeen)}` : ""})`).join(" | "),
    d(r.lastSeen), [...new Set(r.devices.map((x) => x.country).filter(Boolean))].join(" "), r.sourcesN ?? "?", r.sourcesXtream, r.sourcesM3u, r.trakt, r.opensubtitles,
  ].map(cell).join(";"));
  return "\ufeff" + [head.join(";"), ...lines].join("\r\n") + "\r\n";
}

// ---- Trakt (facultatif) ---------------------------------------------------------
// Connexion depuis l'espace client : OAuth 2 + PKCE (client public, sans secret). Jetons chiffrés dans le compte,
// renouvelés par le Worker ; les appareils n'envoient que « ce que je lis » (POST /api/device/trakt/scrobble).

// Adresse de retour déclarée chez Trakt : l'ancienne adresse workers.dev (l'application Trakt n'autorise qu'elle).
// Sur le domaine public comme sur l'ancien, Trakt revient donc sur l'ancienne adresse, qui renvoie aussitôt vers le
// domaine public (voir la route /trakt/callback) où vit le cookie de liaison au navigateur. Ailleurs (tests, dev) :
// l'origine de la requête.
const traktRedirect = (url) => (url.hostname === PRIMARY_HOST || LEGACY_HOSTS.includes(url.hostname))
  ? `https://${LEGACY_HOST}/trakt/callback`
  : `${url.origin}/trakt/callback`;
const traktStateKey = (state) => `traktst:${state}`;

async function traktStart(env, acct, url) {
  if (!env.TRAKT_CLIENT_ID) return redirect("/?e=trakt_na#compte");
  const rl = await limited(env, `traktlink:${acct.login}`, 10, 3600);
  if (rl) return rl;
  const state = randomToken(24);
  const { verifier, challenge } = await pkcePair();
  // Le vérificateur ne quitte jamais le Worker ; l'état lie le retour de Trakt à CE compte (anti-CSRF du retour).
  await docs.put(env, traktStateKey(state), JSON.stringify({ login: acct.login, verifier }), { expirationTtl: TRAKT_STATE_TTL_S });
  // Cookie lié au navigateur (Lax : renvoyé par la navigation de retour depuis trakt.tv) : un lien d'autorisation
  // fabriqué sur le compte d'un tiers et ouvert par la victime ne relie pas son Trakt au compte du tiers.
  return redirect(authorizeUrl(env.TRAKT_CLIENT_ID, traktRedirect(url), state, challenge), {
    "set-cookie": `${TRAKT_COOKIE}=${state}; Path=/trakt; HttpOnly; Secure; SameSite=Lax; Max-Age=${TRAKT_STATE_TTL_S}`,
  });
}

const TRAKT_COOKIE = "utv_trakt";

/**
 * Fin du parcours Trakt : page relais qui renvoie au tableau de bord par une navigation INTERNE au site (le cookie
 * de session Strict est alors renvoyé ; une redirection 302 dans la chaîne venue de trakt.tv ne le serait pas).
 */
function traktDone(query, cookie = TRAKT_COOKIE, cpath = "/trakt") {
  const to = `/?${query}#compte`;
  return new Response(`<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=${to}"><title>Ultra TV</title><p><a href="${to}">Retour au tableau de bord</a></p>`, {
    headers: {
      "content-type": "text/html; charset=utf-8", "cache-control": "no-store",
      "content-security-policy": "default-src 'none'; base-uri 'none'; frame-ancestors 'none'",
      "x-content-type-options": "nosniff", "referrer-policy": "no-referrer",
      "set-cookie": `${cookie}=; Path=${cpath}; HttpOnly; Secure; SameSite=Lax; Max-Age=0`,
    },
  });
}

async function traktFinish(req, env, url) {
  const state = url.searchParams.get("state") || "";
  const code = url.searchParams.get("code") || "";
  if (!/^[A-Za-z0-9_-]{16,64}$/.test(state)) return traktDone("e=trakt");
  // Même navigateur que celui qui a lancé la connexion.
  if (!timingSafeEqual(readCookie(req, TRAKT_COOKIE) || "", state)) return traktDone("e=trakt");
  const raw = await docs.get(env, traktStateKey(state));
  if (raw) await docs.delete(env, traktStateKey(state));
  let st = null;
  try { st = raw ? JSON.parse(raw) : null; } catch { st = null; }
  // État expiré ou déjà utilisé, refus chez Trakt : rien n'est enregistré.
  if (!st || typeof st.login !== "string" || !code || code.length > 512 || !env.TRAKT_CLIENT_ID) return traktDone("e=trakt");
  const t = await exchangeCode(env.TRAKT_CLIENT_ID, traktRedirect(url), code, st.verifier);
  if (t.error) return traktDone("e=trakt");
  const user = await traktUsername(env.TRAKT_CLIENT_ID, t.access);
  return withAccountLock(env, st.login, async (acct) => {
    if (!acct) return traktDone("e=trakt");
    acct.traktEnc = await encryptJson(keysFromEnv(env), t, acct.login);
    acct.trakt = { user, at: Date.now() };
    await putAccount(env, acct);
    return traktDone("m=trakt");
  });
}

// ---- Simkl (facultatif, au choix avec Trakt) ------------------------------------
// Même parcours que Trakt (état à usage unique + cookie Lax lié au navigateur), mais sans PKCE : Simkl exige le secret
// client, gardé dans le Worker. Auth V2 : jetons { access, refresh, exp } chiffrés dans le compte, renouvelés sous verrou.
const SIMKL_COOKIE = "utv_simkl";
const simklStateKey = (state) => `simklst:${state}`;
const simklRedirect = (url) => (url.hostname === PRIMARY_HOST || LEGACY_HOSTS.includes(url.hostname))
  ? `https://${PRIMARY_HOST}/simkl/callback`
  : `${url.origin}/simkl/callback`;
const simklDone = (query) => traktDone(query, SIMKL_COOKIE, "/simkl");

async function simklStart(env, acct, url) {
  if (!simklEnabled(env)) return redirect("/?e=simkl_na#compte");
  const rl = await limited(env, `simklink:${acct.login}`, 10, 3600);
  if (rl) return rl;
  const state = randomToken(24);
  // PKCE (S256) exigé par Simkl : le vérificateur reste côté serveur, avec l'état.
  const { verifier, challenge } = await pkcePair();
  await docs.put(env, simklStateKey(state), JSON.stringify({ login: acct.login, verifier }), { expirationTtl: SIMKL_STATE_TTL_S });
  return redirect(simklAuthorizeUrl(env.SIMKL_CLIENT_ID, simklRedirect(url), state, challenge), {
    "set-cookie": `${SIMKL_COOKIE}=${state}; Path=/simkl; HttpOnly; Secure; SameSite=Lax; Max-Age=${SIMKL_STATE_TTL_S}`,
  });
}

async function simklFinish(req, env, url) {
  const state = url.searchParams.get("state") || "";
  const code = url.searchParams.get("code") || "";
  if (!/^[A-Za-z0-9_-]{16,64}$/.test(state)) return simklDone("e=simkl");
  if (!timingSafeEqual(readCookie(req, SIMKL_COOKIE) || "", state)) return simklDone("e=simkl");
  const raw = await docs.get(env, simklStateKey(state));
  if (raw) await docs.delete(env, simklStateKey(state));
  let st = null;
  try { st = raw ? JSON.parse(raw) : null; } catch { st = null; }
  if (!st || typeof st.login !== "string" || !code || code.length > 512 || !simklEnabled(env)) return simklDone("e=simkl");
  const t = await simklExchangeCode(env.SIMKL_CLIENT_ID, env.SIMKL_CLIENT_SECRET, simklRedirect(url), code, fetch, st?.verifier || null);
  if (t.error) return simklDone("e=simkl");
  const user = await simklUsername(env.SIMKL_CLIENT_ID, t.access);
  return withAccountLock(env, st.login, async (acct) => {
    if (!acct) return simklDone("e=simkl");
    acct.simklEnc = await encryptJson(keysFromEnv(env), { access: t.access, refresh: t.refresh, exp: t.exp }, acct.login);
    acct.simkl = { user, at: Date.now() };
    // Simkl vient d'être relié : si rien n'était choisi, c'est lui qui devient le service de suivi.
    if (!acct.tracking && !acct.traktEnc) acct.tracking = "simkl";
    await putAccount(env, acct);
    await forgetTraktLibrary(env, acct.login);
    return simklDone("m=simkl");
  });
}

/**
 * Jeton d'accès Simkl valide pour le compte (renouvelé sous verrou s'il expire dans moins d'un jour), ou null (non relié,
 * révoqué, ancien enregistrement sans jeton de renouvellement). force : renouvellement immédiat (après un 401).
 */
async function simklAccess(env, login, force = false) {
  const keys = keysFromEnv(env);
  const acct0 = await getAccount(env, login);
  if (!acct0 || !acct0.simklEnc) return null;
  const t0 = await decryptJson(keys, acct0.simklEnc, login).catch(() => null);
  if (!t0 || typeof t0.access !== "string") return null;
  const fresh = (t) => typeof t.exp === "number" && t.exp - Date.now() > SIMKL_REFRESH_MARGIN_MS;
  if (!force && fresh(t0)) return t0.access;
  return withAccountLock(env, login, async (acct) => {
    if (!acct.simklEnc) return null;
    const cur = await decryptJson(keys, acct.simklEnc, login).catch(() => null);
    if (!cur || typeof cur.access !== "string") return null;
    // Un autre appareil l'a déjà renouvelé pendant l'attente du verrou.
    if (cur.access !== t0.access && fresh(cur)) return cur.access;
    // Ancien format { access } (avant Auth V2) : pas de renouvellement possible, on oublie pour forcer la reconnexion.
    if (!cur.refresh) {
      delete acct.simklEnc;
      acct.simkl = { ...(acct.simkl || {}), revoked: true };
      await putAccount(env, acct);
      return null;
    }
    const nt = await simklRefresh(env.SIMKL_CLIENT_ID, env.SIMKL_CLIENT_SECRET, cur.refresh);
    if (nt.error === "revoked") {
      delete acct.simklEnc;
      acct.simkl = { ...(acct.simkl || {}), revoked: true };
      await putAccount(env, acct);
      return null;
    }
    // Panne passagère : le jeton actuel sert encore s'il n'est pas expiré (sauf renouvellement forcé après un 401).
    if (nt.error) return !force && typeof cur.exp === "number" && cur.exp > Date.now() ? cur.access : null;
    acct.simklEnc = await encryptJson(keys, { access: nt.access, refresh: nt.refresh, exp: nt.exp }, login);
    await putAccount(env, acct);
    return nt.access;
  });
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Appel Simkl avec jeton : fn(access) renvoie le résultat, ou "unauthorized" (ou lève { unauthorized }).
 * Sur un 401 : un renouvellement puis une seule nouvelle tentative (espacée d'1 s, limite de 1 POST/s par utilisateur) ;
 * si le renouvellement échoue ou si le refus persiste, le jeton est oublié. → { linked: false } ou { r }.
 */
async function simklAuthed(env, login, fn, { post = false } = {}) {
  const run = async (access) => { try { return await fn(access); } catch (e) { if (e && e.unauthorized) return "unauthorized"; throw e; } };
  let access = await simklAccess(env, login);
  if (!access) return { linked: false };
  let r = await run(access);
  if (r === "unauthorized") {
    access = await simklAccess(env, login, true);
    if (access) {
      if (post) await sleep(1000);
      r = await run(access);
    }
    if (!access || r === "unauthorized") { await simklRevoked(env, login); return { linked: false }; }
  }
  return { r };
}

/** Simkl refuse le jeton (révoqué chez Simkl) : on l'oublie, l'espace client propose de reconnecter. */
async function simklRevoked(env, login) {
  await withAccountLock(env, login, async (acct) => {
    if (!acct || !acct.simklEnc) return;
    delete acct.simklEnc;
    acct.simkl = { ...(acct.simkl || {}), revoked: true };
    await putAccount(env, acct);
  });
}

/**
 * GET/POST /api/device/tracking : service de suivi choisi, services proposés et reliés. POST {service: trakt|simkl|none}
 * change le choix (la liaison elle-même se fait depuis le tableau de bord, par OAuth).
 */
async function deviceTracking(req, env, method) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = await limited(env, `tracking:dev:${auth.device.id}`, 60, 600, true);
  if (rl) return rl;
  if (method === "GET") return json(trackingStatus(env, auth.acct));
  const body = await readJson(req, 1024);
  if (body.error) return body.error;
  const choice = body.value && body.value.service;
  if (!TRACKING_CHOICES.includes(choice) || (choice === "simkl" && !simklEnabled(env))) return json({ error: "invalid", field: "service" }, 400);
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!acct.devices?.some((d) => d.id === auth.device.id)) return json({ error: "unauthorized" }, 401);
    acct.tracking = choice;
    await putAccount(env, acct);
    await forgetTraktLibrary(env, acct.login);
    return json(trackingStatus(env, acct));
  });
}

/** Jeton d'accès Trakt valide pour le compte (renouvelé sous verrou si besoin), ou null (non relié / révoqué). */
async function traktAccess(env, login, redirectUri, force = false) {
  const keys = keysFromEnv(env);
  const acct0 = await getAccount(env, login);
  if (!acct0 || !acct0.traktEnc) return null;
  const t0 = await decryptJson(keys, acct0.traktEnc, login).catch(() => null);
  if (!t0) return null;
  if (!force && t0.expiresAt - Date.now() > 86_400_000) return t0.access;
  return withAccountLock(env, login, async (acct) => {
    if (!acct.traktEnc) return null;
    const cur = await decryptJson(keys, acct.traktEnc, login).catch(() => null);
    if (!cur) return null;
    // Un autre appareil l'a déjà renouvelé pendant l'attente du verrou.
    if (cur.access !== t0.access && cur.expiresAt - Date.now() > 86_400_000) return cur.access;
    const nt = await refreshTokens(env.TRAKT_CLIENT_ID, redirectUri, cur.refresh);
    if (nt.error === "revoked") {
      // Accès retiré chez Trakt : on oublie les jetons, l'espace client propose de reconnecter.
      delete acct.traktEnc;
      acct.trakt = { ...(acct.trakt || {}), revoked: true };
      await putAccount(env, acct);
      return null;
    }
    if (nt.error) return force ? null : cur.access;
    acct.traktEnc = await encryptJson(keys, nt, login);
    await putAccount(env, acct);
    return nt.access;
  });
}

async function deviceTraktScrobble(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = await limited(env, `trakt:dev:${auth.device.id}`, 120, 600, true);
  if (rl) return rl;
  const body = await readJson(req, 4 * 1024);
  if (body.error) return body.error;
  const s = parseScrobble(body.value);
  if (!s) return json({ error: "invalid" }, 400);
  const svc = activeTracker(env, auth.acct);
  if (!svc) return json({ linked: false });
  const tmdb = s.tmdb || (await resolveTmdb(env, s.kind === "movie" ? "movie" : "tv", s.title, s.year));
  if (svc === "simkl") {
    const payload = simklScrobbleBody(s, tmdb);
    if (!payload) return json({ linked: true, matched: false });
    const ver = appVersionOf(req.headers.get("x-ultra-version"));
    const sent = await simklAuthed(env, auth.acct.login, (access) => simklSend(env.SIMKL_CLIENT_ID, access, `/scrobble/${s.action}`, payload, fetch, ver), { post: true });
    if (!("r" in sent)) return json({ linked: false });
    const r = sent.r;
    if (r === "ok" && s.action === "stop" && s.progress >= 80) await forgetTraktLibrary(env, auth.acct.login);
    return json({ linked: true, matched: r !== "unmatched", ok: r === "ok" });
  }
  const payload = traktScrobbleBody(s, tmdb);
  if (!payload) { console.log(JSON.stringify({ trakt: "scrobble", unmatched: s.kind })); return json({ linked: true, matched: false }); }
  const redirectUri = traktRedirect(new URL(req.url));
  let access = await traktAccess(env, auth.acct.login, redirectUri);
  if (!access) return json({ linked: false });
  let r = await sendScrobble(env.TRAKT_CLIENT_ID, access, s.action, payload);
  if (r === "unauthorized") {
    access = await traktAccess(env, auth.acct.login, redirectUri, true);
    if (!access) return json({ linked: false });
    r = await sendScrobble(env.TRAKT_CLIENT_ID, access, s.action, payload);
  }
  // Vu (Trakt marque « vu » au-delà de 80 %) : la bibliothèque en cache est périmée.
  if (r === "ok" && s.action === "stop" && s.progress >= 80) await forgetTraktLibrary(env, auth.acct.login);
  return json({ linked: true, matched: r !== "unmatched", ok: r === "ok" });
}

const TRAKT_PARSERS = { watchlist: parseWatchlist, history: parseHistory, rating: parseRating };

/**
 * Écriture Trakt demandée par un appareil (watchlist, vu / non vu, note). L'appareil décrit l'œuvre ; le Worker
 * l'identifie (TMDB), écrit avec les jetons du compte (renouvelés si besoin) puis oublie la bibliothèque en cache :
 * la prochaine lecture de /api/device/trakt/library reflète le changement.
 */
async function deviceTraktWrite(req, env, kind) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  // Gestes manuels : 60 écritures / 10 min / appareil (Trakt limite lui-même les POST à environ 1 par seconde).
  const rl = await limited(env, `traktw:dev:${auth.device.id}`, 60, 600, true);
  if (rl) return rl;
  const body = await readJson(req, 8 * 1024);
  if (body.error) return body.error;
  const p = TRAKT_PARSERS[kind](body.value);
  if (!p) return json({ error: "invalid" }, 400);
  const svc = activeTracker(env, auth.acct);
  if (!svc) return json({ linked: false });
  const tmdb = p.tmdb || (await resolveTmdb(env, p.type === "movie" ? "movie" : "tv", p.title, p.year));
  if (!tmdb) { console.log(JSON.stringify({ trakt: kind, unmatched: p.type })); return json({ linked: true, matched: false, ok: false }); }
  if (svc === "simkl") {
    const req2 = simklWriteRequest(kind, p, tmdb);
    // Simkl n'a pas de retrait de la watchlist : réponse explicite plutôt qu'un faux succès.
    if (!req2) return json({ linked: true, matched: true, ok: false, error: "unsupported", tmdb });
    const ver = appVersionOf(req.headers.get("x-ultra-version"));
    const sent = await simklAuthed(env, auth.acct.login, (access) => simklSend(env.SIMKL_CLIENT_ID, access, req2.path, req2.body, fetch, ver), { post: true });
    if (!("r" in sent)) return json({ linked: false });
    const r = sent.r;
    if (r === "ok") await forgetTraktLibrary(env, auth.acct.login);
    if (r === "upstream") return json({ linked: true, matched: true, ok: false, error: "upstream" }, 502);
    return json({ linked: true, matched: r !== "unmatched", ok: r === "ok", tmdb });
  }
  const { path, body: payload } = traktWriteRequest(kind, p, tmdb);
  const redirectUri = traktRedirect(new URL(req.url));
  let access = await traktAccess(env, auth.acct.login, redirectUri);
  if (!access) return json({ linked: false });
  let r = await sendSync(env.TRAKT_CLIENT_ID, access, path, payload);
  if (r === "unauthorized") {
    access = await traktAccess(env, auth.acct.login, redirectUri, true);
    if (!access) return json({ linked: false });
    r = await sendSync(env.TRAKT_CLIENT_ID, access, path, payload);
  }
  if (r === "ok") await forgetTraktLibrary(env, auth.acct.login);
  if (r === "upstream") return json({ linked: true, matched: true, ok: false, error: "upstream" }, 502);
  return json({ linked: true, matched: r !== "unmatched", ok: r === "ok", tmdb });
}

const traktLibKey = (login, lang) => `tlib:${login}:${lang}`;
const traktDictKey = (login, lang) => `tdict:${login}:${lang}`;

/** Oublie la bibliothèque en cache (et, au déliage, le dictionnaire des titres). */
async function forgetTraktLibrary(env, login, all = false) {
  await Promise.all([...LANGS].flatMap((l) => [docs.delete(env, traktLibKey(login, l)), ...(all ? [docs.delete(env, traktDictKey(login, l))] : [])]));
}

// Bibliothèque Trakt pour l'appareil, gardée chiffrée en KV. Au-delà de 15 min, la version gardée est servie TOUT DE
// SUITE et rafraîchie en arrière-plan (l'appareil n'attend pas les 10 appels Trakt) ; au-delà de 24 h, relue d'abord.
const TRAKT_LIB_KEEP_S = 24 * 3600;

async function deviceTraktLibrary(req, env, url, ctx) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = await limited(env, `traktlib:dev:${auth.device.id}`, 30, 600, true);
  if (rl) return rl;
  const svc = activeTracker(env, auth.acct);
  if (!svc) return json({ linked: false });
  const login = auth.acct.login;
  const lang = LANGS.has(url.searchParams.get("lang")) ? url.searchParams.get("lang") : "en";
  const cached = await docs.get(env, traktLibKey(login, lang));
  const v = cached ? await decryptJson(keysFromEnv(env), cached, login).catch(() => null) : null;
  // Ancien format (données seules) : traité comme périmé.
  const at = v && typeof v.at === "number" ? v.at : 0;
  // Un cache fait pour l'autre service (changement de choix) est ignoré.
  const data = v && v.data && (v.svc || "trakt") === svc ? v.data : null;
  // Simkl : 500 requêtes par jour et par utilisateur (offre gratuite), 6 par rafraîchissement → cache de 2 h (Trakt : 15 min).
  // Les écritures faites depuis l'appli vident ce cache : elles restent visibles tout de suite.
  const ttlS = svc === "simkl" ? SIMKL_LIB_TTL_S : LIB_TTL_S;
  if (data && Date.now() - at < ttlS * 1000) return json(data);
  if (data && ctx && typeof ctx.waitUntil === "function") {
    ctx.waitUntil(buildTraktLibrary(env, login, lang, traktRedirect(url), svc, appVersionOf(req.headers.get("x-ultra-version"))).catch(() => {}));
    return json(data);
  }
  const fresh = await buildTraktLibrary(env, login, lang, traktRedirect(url), svc, appVersionOf(req.headers.get("x-ultra-version")));
  return json(fresh.body, fresh.status);
}

/** Relit Trakt, complète les titres, garde le résultat (chiffré) → { status, body }. */
async function buildTraktLibrary(env, login, lang, redirectUri, svc = "trakt", ver = null) {
  const keys = keysFromEnv(env);
  let lib;
  if (svc === "simkl") {
    try {
      const got = await simklAuthed(env, login, (access) => fetchSimklLibrary(env.SIMKL_CLIENT_ID, access, fetch, ver));
      if (!("r" in got)) return { status: 200, body: { linked: false } };
      lib = got.r;
    } catch { return { status: 502, body: { error: "upstream" } }; }
  } else {
    let access = await traktAccess(env, login, redirectUri);
    if (!access) return { status: 200, body: { linked: false } };
    try {
      lib = await fetchTraktLibrary(env.TRAKT_CLIENT_ID, access);
    } catch (e) {
      if (!e.unauthorized) return { status: 502, body: { error: "upstream" } };
      access = await traktAccess(env, login, redirectUri, true);
      if (!access) return { status: 200, body: { linked: false } };
      try { lib = await fetchTraktLibrary(env.TRAKT_CLIENT_ID, access); } catch { return { status: 502, body: { error: "upstream" } }; }
    }
  }
  let known = {};
  try { known = JSON.parse((await docs.get(env, traktDictKey(login, lang))) || "{}") || {}; } catch { known = {}; }
  const dict = await enrichTitles(env, lib, known, lang);
  if (Object.keys(dict).length !== Object.keys(known).length) {
    await docs.put(env, traktDictKey(login, lang), JSON.stringify(dict), { expirationTtl: 90 * 86400 });
  }
  const data = { ...libraryForDevice(lib, dict, Date.now()), service: svc };
  await docs.put(env, traktLibKey(login, lang), await encryptJson(keys, { at: Date.now(), data, svc }, login), { expirationTtl: TRAKT_LIB_KEEP_S });
  return { status: 200, body: data };
}

// ---- compte OpenSubtitles du client (facultatif) ------------------------------
// Lié depuis l'espace client : identifiants chiffrés dans le compte (AES-GCM, lié au login), jeton de session gardé
// 20 h en KV (chiffré lui aussi). Les appareils n'en savent rien : le Worker télécharge au nom du client.

const OS_TOKEN_TTL_S = 20 * 3600;
const osTokenKey = (login) => `ostok:${login}`;

async function linkOpenSubtitles(env, acct, form) {
  const username = (form.get("os_user") || "").trim().slice(0, 100);
  const password = (form.get("os_pass") || "").slice(0, 200);
  if (!username || !password) return redirect("/?e=os_denied#compte");
  const rl = await limited(env, `oslink:${acct.login}`, 10, 3600);
  if (rl) return rl;
  const r = await osLogin(env, username, password);
  if (r.error) return redirect(`/?e=${r.error === "denied" ? "os_denied" : r.error === "busy" ? "os_busy" : "os_upstream"}#compte`);
  const keys = keysFromEnv(env);
  acct.osEnc = await encryptJson(keys, { username, password }, acct.login);
  acct.os = { user: username, level: r.level, allowed: r.allowed, vip: r.vip, at: Date.now() };
  await putAccount(env, acct);
  await docs.put(env, osTokenKey(acct.login), await encryptJson(keys, { token: r.token, base: r.base }, acct.login), { expirationTtl: OS_TOKEN_TTL_S });
  return redirect("/?m=os#compte");
}

/** Session OpenSubtitles d'un compte pour subtitlesDownload, ou null si aucun compte n'est lié. */
function osSession(env, acct) {
  if (!acct.osEnc) return null;
  const keys = keysFromEnv(env);
  return {
    async get(force) {
      if (!force) {
        const blob = await docs.get(env, osTokenKey(acct.login));
        if (blob) return decryptJson(keys, blob, acct.login);
      }
      // Reconnexion bornée : un mot de passe changé chez OpenSubtitles ne déclenche pas une connexion par téléchargement.
      if (await limited(env, `oslogin:${acct.login}`, 6, 3600, true)) return null;
      const { username, password } = await decryptJson(keys, acct.osEnc, acct.login);
      const r = await osLogin(env, username, password);
      if (r.error) return null;
      await docs.put(env, osTokenKey(acct.login), await encryptJson(keys, { token: r.token, base: r.base }, acct.login), { expirationTtl: OS_TOKEN_TTL_S });
      return { token: r.token, base: r.base };
    },
  };
}

async function removeAccount(env, acct, form) {
  const lock = await lockedOut(env, `lock:acct:${acct.login}`);
  if (lock) return lock;
  const verdict = await verifyPassword(acct, (form.get("password") || "").slice(0, 256));
  if (!verdict.ok) {
    const f = await guardStub(env, `lock:acct:${acct.login}`).lockFail(LOGIN_LOCK);
    return f.locked ? tooMany(f.retryAfter) : redirect("/?e=pw");
  }
  await deleteAccount(env, acct);
  return redirect("/login", { "set-cookie": clearCookieHeader() });
}

// ---- API appareil ------------------------------------------------------------

async function deviceAuth(req, env) {
  const token = parseBearer(req);
  // Pas de Durable Object sur le chemin normal (un appel par requête d'appareil épuisait le quota gratuit) : un jeton de
  // 256 bits ne se devine pas ; seuls les jetons faux sont comptés, par le limiteur natif (20 par minute et par IP).
  const auth = await authDevice(env, token);
  if (!auth) {
    if (token) {
      const rl = await limited(env, `authfail:ip:${clientIp(req)}`, 20, 60, true);
      if (rl) return { res: rl };
    }
    return { res: json({ error: "unauthorized" }, 401, { "www-authenticate": 'Bearer realm="ultratv"' }) };
  }
  return { auth };
}

const EDITIONS = new Set(["standard", "pro"]);

/** Édition de l'appli (en-tête X-Ultra-Edition) : enregistrée seulement quand elle change (écriture rare). */
async function noteEdition(req, env, auth) {
  // Édition, dernière activité (à l'heure près : une écriture au plus par heure) et pays de l'appareil.
  const ed = (req.headers.get("x-ultra-edition") || "").trim().toLowerCase();
  const patch = {};
  if (EDITIONS.has(ed) && auth.device.edition !== ed) patch.edition = ed;
  // Dernière activité et pays : D1 (une réécriture du compte KV par heure et par appareil épuisait le quota gratuit
  // d'écritures, et l'appairage échouait ensuite pour tout le monde).
  await touchDeviceActivity(env, auth.device.id, countryOf(req));
  const version = appVersionOf(req.headers.get("x-ultra-version"));
  if (version && auth.device.version !== version) patch.version = version;
  if (!Object.keys(patch).length) return;
  // Information secondaire : un échec ici ne doit jamais empêcher l'appareil de recevoir sa configuration.
  try { await withAccountLock(env, auth.acct.login, (acct) => setDeviceInfo(env, acct, auth.device.id, patch)); }
  catch (e) { console.error("noteEdition", e?.message); }
}

/**
 * Édition Pro : l'appareil transmet son statut de licence SIGNÉ par le panneau revendeur. Vérifié (signature,
 * fraîcheur) puis gardé avec l'appareil pour le tableau de bord ; l'appareil devient « pro ».
 */
async function deviceLicense(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = await limited(env, `lic:dev:${auth.device.id}`, 20, 3600, true);
  if (rl) return rl;
  const body = await readJson(req, 6 * 1024);
  if (body.error) return body.error;
  const lic = await verifyProLicense(body.value.payload, body.value.sig);
  if (!lic) return json({ error: "invalid", field: "license" }, 400);
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!(await setDeviceInfo(env, acct, auth.device.id, { edition: "pro", license: lic }))) return json({ error: "unauthorized" }, 401);
    return json({ ok: true });
  });
}

/**
 * If-None-Match correspond-il à l'ETag ? Forme faible acceptée : en compressant la réponse (Brotli), Cloudflare
 * transforme "v2" en W/"v2", que l'appareil renvoie tel quel (sans cela, jamais de 304 : configuration complète
 * retéléchargée à chaque synchro).
 */
export function etagMatches(header, etag) {
  if (!header) return false;
  const norm = (t) => t.trim().replace(/^W\//, "");
  return header.split(",").some((t) => t.trim() === "*" || norm(t) === norm(etag));
}

async function deviceConfig(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  await noteEdition(req, env, auth);
  const rl = (await limited(env, `cfg:ip:${clientIp(req)}`, 300, 600, true))
    || (await limited(env, `cfg:dev:${auth.device.id}`, 60, 600, true));
  if (rl) return rl;
  const version = auth.acct.cfgVersion || 0;
  const etag = `"v${version}"`;
  // Demandes « Reprendre sur cet appareil » en attente (file de 2 min) : en-tête de la réponse, y compris du 304, donc
  // aucune requête de plus pour la cible. Une seule lecture de stockage ; un échec ne gêne jamais la synchro.
  let ho = "";
  try {
    const names = Object.fromEntries((auth.acct.devices || []).map((d) => [d.id, d.name]));
    ho = handoffHeader(await loadHandoffs(env, auth.acct.login, auth.device.id), names);
  } catch { ho = ""; }
  const hoHeaders = ho ? { "x-utv-handoff": ho, "access-control-expose-headers": "x-utv-handoff" } : {};
  // Synchro incrémentale : rien n'a changé depuis la version connue de l'appareil.
  if (etagMatches(req.headers.get("if-none-match"), etag)) return new Response(null, { status: 304, headers: { etag, "cache-control": "no-store", ...hoHeaders } });
  // Un appareil ne reçoit QUE les fournisseurs qui lui sont affectés (ou affectés à tous).
  const providers = (await loadProviders(env, auth.acct)).filter((p) => isSupportedKind(p) && isVisibleTo(p, auth.device.id));
  const act = await deviceActivity(env, (auth.acct.devices || []).map((d) => d.id));
  const devices = (auth.acct.devices || []).map((d) => withActivity(d, act))
    .map((d) => ({ id: d.id, name: d.name, model: d.label || "", lastSeen: d.lastSeen || 0, isCurrent: d.id === auth.device.id }));
  return json(
    { version, self: auth.device.id, devices, providers: providers.map(syncProvider), removed: liveTombstones(auth.acct) },
    200, { etag, "cache-control": "no-store", ...hoHeaders },
  );
}

/**
 * POST /api/device/handoff { to, ref: { src, t: live|movie|episode, id, par?, pos, n } } : « Envoyer vers… ». Dépose une
 * demande dans la file courte (2 min) de l'appareil cible, qui la lit à sa prochaine synchro. Ni URL de flux ni
 * identifiants : une référence que la cible résout dans son propre catalogue. Cible = autre appareil du même compte.
 */
async function deviceHandoff(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `ho:ip:${clientIp(req)}`, 60, 600, true))
    || (await limited(env, `ho:dev:${auth.device.id}`, 20, 600, true));
  if (rl) return rl;
  const body = await readJson(req, 2048);
  if (body.error) return body.error;
  const r = parseHandoff(body.value);
  if (r.error) return json({ error: "invalid", field: r.error }, 400);
  if (r.to === auth.device.id || !(auth.acct.devices || []).some((d) => d.id === r.to)) return json({ error: "invalid", field: "to" }, 400);
  await pushHandoff(env, auth.acct.login, r.to, { ...r.item, f: auth.device.id });
  return json({ ok: true, expiresIn: 120 });
}

const DEV_BODY_MAX = 4 * 1024;

/** Ajoute ou met à jour (si `id` connu de CE compte) un fournisseur, depuis un appareil appairé. */
async function devicePutProvider(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `pput:ip:${clientIp(req)}`, 120, 600, true))
    || (await limited(env, `pput:dev:${auth.device.id}`, 30, 600, true));
  if (rl) return rl;
  const body = await readJson(req, DEV_BODY_MAX);
  if (body.error) return body.error;
  const origin = { deviceId: auth.device.id, name: auth.device.name };
  const r = parseDeviceProvider(body.value, origin);
  if (r.error) return json({ error: "invalid", field: r.error }, 400);
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!acct.devices?.some((d) => d.id === auth.device.id)) return json({ error: "unauthorized" }, 401);
  const providers = await loadProviders(env, acct);
  const wanted = body.value.id;
  if (wanted !== undefined && !(typeof wanted === "string" && /^[0-9a-f]{8}$/.test(wanted))) return json({ error: "invalid", field: "id" }, 400);
  let assign = null;
  if (body.value.shareWith !== undefined) {
    const a = parseAssign(body.value.shareWith, acct);
    if (a.error) return json({ error: "invalid", field: "shareWith" }, 400);
    assign = a.assign;
  }
  let status = 201;
  let savedId;
  if (wanted) {
    const i = providers.findIndex((p) => p.id === wanted);
    // Inconnu OU non affecté à cet appareil : même réponse (on ne révèle pas l'existence d'une source d'un autre appareil).
    if (i < 0 || !isVisibleTo(providers[i], auth.device.id)) return json({ error: "not_found" }, 404);
    const old = providers[i];
    providers[i] = { ...r.provider, id: old.id, createdAt: old.createdAt, originDeviceId: old.originDeviceId, originName: old.originName, assign: assign ?? assignmentOf(old), ...(old.prefs ? { prefs: old.prefs } : {}) };
    status = 200; savedId = old.id;
  } else {
    if (providers.length >= MAX_PROVIDERS) return json({ error: "limit" }, 409);
    // Créée depuis un appareil : privée à cet appareil, sauf partage explicite.
    r.provider.assign = assign ?? [auth.device.id];
    if (Array.isArray(r.provider.assign) && !r.provider.assign.includes(auth.device.id)) r.provider.assign.push(auth.device.id);
    providers.push(r.provider); savedId = r.provider.id;
  }
  await saveProviders(env, acct, providers);
  await putAccount(env, acct);
  return json({ version: acct.cfgVersion, provider: syncProvider(providers.find((p) => p.id === savedId)) }, status);
  });
}

/**
 * Change l'affectation d'une source (« all » ou liste d'appareils du compte) depuis un appareil, sans renvoyer ses
 * identifiants : le reste de la source (dont le marquage revendeur) est conservé tel quel.
 */
async function deviceAssignProvider(req, env, id) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `pput:ip:${clientIp(req)}`, 120, 600, true))
    || (await limited(env, `pput:dev:${auth.device.id}`, 30, 600, true));
  if (rl) return rl;
  const body = await readJson(req, 2 * 1024);
  if (body.error) return body.error;
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!acct.devices?.some((d) => d.id === auth.device.id)) return json({ error: "unauthorized" }, 401);
    const a = parseAssign(body.value.shareWith, acct);
    if (a.error) return json({ error: "invalid", field: "shareWith" }, 400);
    const providers = await loadProviders(env, acct);
    const i = providers.findIndex((p) => p.id === id);
    if (i < 0 || !isVisibleTo(providers[i], auth.device.id)) return json({ error: "not_found" }, 404);
    providers[i] = { ...providers[i], assign: a.assign, updatedAt: Date.now() };
    await saveProviders(env, acct, providers);
    await putAccount(env, acct);
    return json({ version: acct.cfgVersion, provider: syncProvider(providers[i]) });
  });
}

const PREFS_BODY_MAX = 512 * 1024;

/**
 * Publie les réglages d'affichage partagés d'une source (langues, catégories désactivées).
 * Le plus récent gagne : un envoi plus ancien que la version stockée renvoie 409 avec la version gagnante.
 */
async function devicePutPrefs(req, env, id) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `pprefs:ip:${clientIp(req)}`, 240, 600, true))
    || (await limited(env, `pprefs:dev:${auth.device.id}`, 60, 600, true));
  if (rl) return rl;
  const body = await readJson(req, PREFS_BODY_MAX);
  if (body.error) return body.error;
  const r = parsePrefs(body.value);
  if (r.error) return json({ error: "invalid", field: r.error }, 400);
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!acct.devices?.some((d) => d.id === auth.device.id)) return json({ error: "unauthorized" }, 401);
    const providers = await loadProviders(env, acct);
    const i = providers.findIndex((p) => p.id === id);
    if (i < 0 || !isVisibleTo(providers[i], auth.device.id)) return json({ error: "not_found" }, 404);
    const cur = providers[i].prefs;
    if (cur && cur.updatedAt > r.prefs.updatedAt) return json({ error: "stale", prefs: cur }, 409);
    // Ordre et chaînes masquées non envoyés (appareil d'une version plus ancienne) : on garde ceux du compte.
    const next = { ...r.prefs, by: auth.device.id };
    if (next.order === undefined && cur?.order) next.order = cur.order;
    if (next.hidden === undefined && cur?.hidden) next.hidden = cur.hidden;
    if (next.chOrder === undefined && cur?.chOrder) next.chOrder = cur.chOrder;
    if (next.pHidden === undefined && cur?.pHidden) next.pHidden = cur.pHidden;
    providers[i] = { ...providers[i], prefs: next };
    await saveProviders(env, acct, providers);
    await putAccount(env, acct);
    return json({ version: acct.cfgVersion, prefs: providers[i].prefs });
  });
}

const STATE_BODY_MAX = 512 * 1024;

/** Profils du compte : l'appareil envoie les siens (et ses suppressions), reçoit la liste fusionnée. */
async function deviceSyncProfiles(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `pprof:ip:${clientIp(req)}`, 240, 600, true))
    || (await limited(env, `pprof:dev:${auth.device.id}`, 60, 600, true));
  if (rl) return rl;
  const body = await readJson(req, 32 * 1024);
  if (body.error) return body.error;
  const r = parseProfiles(body.value);
  if (r.error) return json({ error: "invalid", field: r.error }, 400);
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!acct.devices?.some((d) => d.id === auth.device.id)) return json({ error: "unauthorized" }, 401);
    const merged = mergeProfiles(acct.profiles, r.profiles);
    // Écriture seulement si quelque chose a changé (quota d'écritures KV).
    if (JSON.stringify(merged) !== JSON.stringify(acct.profiles || [])) { acct.profiles = merged; await putAccount(env, acct); }
    return json({ profiles: merged });
  });
}

/**
 * Favoris, positions de reprise et derniers vus d'une source, partagés entre les appareils du compte.
 * Un seul aller-retour : l'appareil envoie ses changements, reçoit l'état fusionné (le plus récent gagne, par entrée).
 */
async function deviceSyncState(req, env, id) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `pstate:ip:${clientIp(req)}`, 240, 600, true))
    || (await limited(env, `pstate:dev:${auth.device.id}`, 120, 600, true));
  if (rl) return rl;
  const body = await readJson(req, STATE_BODY_MAX);
  if (body.error) return body.error;
  const incoming = parseStateBody(body.value);
  if (incoming.error) return json({ error: "invalid", field: incoming.error }, 400);
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!acct.devices?.some((d) => d.id === auth.device.id)) return json({ error: "unauthorized" }, 401);
    const providers = await loadProviders(env, acct);
    const p = providers.find((x) => x.id === id);
    if (!p || !isVisibleTo(p, auth.device.id)) return json({ error: "not_found" }, 404);
    const merged = mergeState(await loadState(env, acct.login, id), incoming);
    if (incoming.fav.length || incoming.hist.length) await saveState(env, acct.login, id, merged);
    return json(merged);
  });
}

async function deviceDeleteProvider(req, env, id) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `pput:ip:${clientIp(req)}`, 120, 600, true))
    || (await limited(env, `pput:dev:${auth.device.id}`, 30, 600, true));
  if (rl) return rl;
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!acct.devices?.some((d) => d.id === auth.device.id)) return json({ error: "unauthorized" }, 401);
  const providers = await loadProviders(env, acct);
  const p = providers.find((x) => x.id === id);
  if (!p || !isVisibleTo(p, auth.device.id)) return json({ error: "not_found" }, 404);
  // Par défaut l'appareil se RETIRE de l'affectation ; `?all=1` supprime le fournisseur du compte.
  const everywhere = new URL(req.url).searchParams.get("all") === "1";
  const a = assignmentOf(p);
  let next;
  if (everywhere) next = providers.filter((x) => x.id !== id);
  else {
    const rest = a === "all" ? (acct.devices || []).map((d) => d.id).filter((d) => d !== auth.device.id) : a.filter((d) => d !== auth.device.id);
    next = rest.length === 0 ? providers.filter((x) => x.id !== id) : providers.map((x) => (x.id === id ? { ...x, assign: rest } : x));
  }
  await saveProviders(env, acct, next);
  // Supprimée du compte (et plus seulement retirée de cet appareil) : pierre tombale pour les autres appareils.
  const gone = !next.some((x) => x.id === id);
  if (gone) addTombstones(acct, [id]);
  await putAccount(env, acct);
  if (gone) await deleteState(env, acct.login, id);
  return json({ version: acct.cfgVersion });
  });
}

/** Renomme CET appareil (étiquette affichée dans le tableau de bord et dans les listes de partage). */
async function deviceRename(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = await limited(env, `pput:dev:${auth.device.id}`, 30, 600, true);
  if (rl) return rl;
  const body = await readJson(req, 1024);
  if (body.error) return body.error;
  if (typeof body.value.name !== "string") return json({ error: "invalid", field: "name" }, 400);
  return withAccountLock(env, auth.acct.login, async (acct) => {
    if (!(await renameDevice(env, acct, auth.device.id, body.value.name))) return json({ error: "invalid", field: "name" }, 400);
    return json({ ok: true });
  });
}

// Métadonnées TMDB : réservé aux appareils appairés, débit limité par appareil et par IP.
async function deviceTmdb(req, env, rest, url) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `tmdb:ip:${clientIp(req)}`, 300, 600, true))
    || (await limited(env, `tmdb:dev:${auth.device.id}`, 120, 600, true));
  if (rl) return rl;
  return tmdbProxy(rest, url.searchParams, env);
}

// Fanart.tv, TVmaze, TheSportsDB (voir extmeta.js) : appareils appairés, débit limité comme TMDB.
async function deviceExtmeta(req, env, name, run) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `${name}:ip:${clientIp(req)}`, 300, 600, true))
    || (await limited(env, `${name}:dev:${auth.device.id}`, 120, 600, true));
  if (rl) return rl;
  return run();
}

// Notes IMDb / Rotten Tomatoes (OMDb) : appareils appairés, débit limité comme TMDB (le cache absorbe l'essentiel).
async function deviceRatings(req, env, url) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `ratings:ip:${clientIp(req)}`, 300, 600, true))
    || (await limited(env, `ratings:dev:${auth.device.id}`, 120, 600, true));
  if (rl) return rl;
  return ratingsProxy(url.searchParams, env);
}

// Sous-titres OpenSubtitles : mêmes garde-fous que TMDB (appareil appairé, débit par appareil et par IP), plus serrés
// car chaque téléchargement consomme le quota de la clé.
async function deviceSubtitles(req, env, isSearch, url) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `sub:ip:${clientIp(req)}`, 120, 600, true))
    || (await limited(env, `sub:dev:${auth.device.id}`, 40, 600, true));
  if (rl) return rl;
  return isSearch ? subtitlesSearch(url.searchParams, env) : subtitlesDownload(url.searchParams, env, { session: osSession(env, auth.acct) });
}

async function deviceRotate(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = await limited(env, `rotate:dev:${auth.device.id}`, 5, 3600, true);
  if (rl) return rl;
  return withAccountLock(env, auth.acct.login, async (acct) => {
    const dev = acct.devices?.find((d) => d.id === auth.device.id);
    if (!dev) return json({ error: "unauthorized" }, 401);
    const token = await rotateDevice(env, acct, dev);
    return json({ token, deviceId: auth.device.id });
  });
}

// ---- ingestion ---------------------------------------------------------------

const LEVELS = ["debug", "info", "warn", "error"];
const int = (v) => (Number.isFinite(v) ? Math.trunc(v) : null);

/** Empreinte courte et non réversible d'un compte pour les journaux et plantages (corrélation seulement). */
export async function opsAccountId(env, login) {
  return (await hmac(env.SESSION_SECRET, `ops-acct:${login}`)).slice(0, 12);
}

async function ingest(req, env, kind) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const isCrash = kind === "crash";
  const rl = (await limited(env, `ing:ip:${clientIp(req)}`, 300, 3600, true))
    || (await limited(env, `ing:${kind}:${auth.device.id}`, isCrash ? 20 : 60, 3600, true));
  if (rl) return rl;
  const body = await readJson(req, isCrash ? CRASH_MAX_BODY : EVENT_MAX_BODY);
  if (body.error) return body.error;
  const b = body.value;
  const base = {
    v: 2, ts: Date.now(), deviceId: auth.device.id,
    mac: sanitizeText(b.mac, 32) || null, // étiquette fournie par l'appareil, non fiable
    version: sanitizeText(b.version, 32) || null, versionCode: int(b.versionCode),
    device: sanitizeText(b.device, 128) || null,
    // Compte et pays : le compte n'est jamais stocké en clair (l'identifiant peut être une adresse e-mail) mais sous la
    // forme d'une empreinte HMAC courte, non réversible sans SESSION_SECRET ; jamais l'adresse IP.
    acct: await opsAccountId(env, auth.acct.login), country: countryOf(req),
  };
  // Version de l'appli de l'appareil (déjà présente dans chaque relevé) : gardée sur l'appareil quand elle change.
  const ver = appVersionOf(b.version);
  if (ver && auth.device.version !== ver) {
    await withAccountLock(env, auth.acct.login, (acct) => setDeviceInfo(env, acct, auth.device.id, { version: ver })).catch(() => {});
  }
  const entry = isCrash
    ? { ...base, androidSdk: int(b.androidSdk), stack: sanitizeText(b.stack, 32 * 1024) }
    : cleanEvent(base, b);
  // D1 (pas KV : une écriture par événement épuisait le quota gratuit) ; conservation 7 jours, 30 jours pour les crashs.
  await recordEvent(env, kind, entry);
  return json({ ok: true });
}

/** Journal nettoyé (mêmes bornes que `/api/event`) à partir d'un objet reçu. */
const cleanEvent = (base, b) => ({
  ...base, level: LEVELS.includes(b.level) ? b.level : "info", tag: sanitizeText(b.tag, 32) || "app", message: sanitizeText(b.message, 4000),
});

/**
 * Lot de journaux : `POST /api/events` { events: [{ level, tag, message, ts? }, …] } (50 entrées, 32 Ko).
 * Même authentification, même limitation, même nettoyage et même tri (`keepEvent`) que `/api/event`, mais une seule
 * requête HTTP et une seule écriture groupée (un batch = une sous-requête de stockage, plafond de 50 par appel).
 */
async function ingestBatch(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `ing:ip:${clientIp(req)}`, 300, 3600, true))
    || (await limited(env, `ing:events:${auth.device.id}`, 120, 3600, true));
  if (rl) return rl;
  const body = await readJson(req, EVENTS_BATCH_MAX_BODY);
  if (body.error) return body.error;
  const b = body.value;
  if (!b || !Array.isArray(b.events)) return json({ error: "invalid" }, 400);
  if (b.events.length > EVENTS_BATCH_MAX) return json({ error: "too_many" }, 413);
  const now = Date.now();
  const base = {
    v: 2, ts: now, deviceId: auth.device.id,
    mac: sanitizeText(b.mac, 32) || null,
    version: sanitizeText(b.version, 32) || null, versionCode: int(b.versionCode),
    device: sanitizeText(b.device, 128) || null,
    acct: await opsAccountId(env, auth.acct.login), country: countryOf(req),
  };
  const ver = appVersionOf(b.version);
  if (ver && auth.device.version !== ver) {
    await withAccountLock(env, auth.acct.login, (acct) => setDeviceInfo(env, acct, auth.device.id, { version: ver })).catch(() => {});
  }
  const entries = b.events.filter((e) => e && typeof e === "object").map((e) => {
    // Horodatage de la ligne côté appareil accepté s'il est plausible (ni futur, ni plus vieux d'une heure).
    const ts = Number.isFinite(e.ts) && e.ts <= now && e.ts > now - 3600_000 ? Math.trunc(e.ts) : now;
    return { ...cleanEvent({ ...base, ts }, e), sampled: e.sampled === true };
  });
  await recordEvents(env, "event", entries);
  return json({ ok: true, accepted: entries.length });
}

// ---- tableaux de bord crashs / journaux (secret serveur) -----------------------

async function checkSecret(req, env, secret, lockName) {
  if (!secret || secret.length < 32) throw new ConfigError(`${lockName} absent ou trop court`);
  const ip = clientIp(req);
  const lock = await lockedOut(env, `lock:${lockName}:${ip}`);
  if (lock) return { res: lock };
  const given = parseOpsSecret(req);
  if (given && timingSafeEqual(given, secret)) return { ok: true };
  if (given) {
    const f = await guardStub(env, `lock:${lockName}:${ip}`).lockFail(SECRET_LOCK);
    if (f.locked) return { res: tooMany(f.retryAfter) };
  }
  return { res: new Response("Unauthorized", { status: 401, headers: { "www-authenticate": 'Basic realm="ultratv-ops", charset="UTF-8"' } }) };
}

async function opsPage(req, env, url, kind) {
  const sess = await currentSession(req, env);
  const admin = Boolean(sess && isAdmin(env, sess.acct));
  // Session ordinaire sans secret : 404 (l'existence n'est pas révélée) ; sinon secret d'exploitation comme avant.
  if (sess && !admin && !req.headers.get("authorization")) return new Response("Not found", { status: 404 });
  if (!admin) {
    const c = await checkSecret(req, env, env.OPS_TOKEN, "ops");
    if (c.res) return c.res;
  }
  const params = parseListParams(url.searchParams, { sorts: EVENT_SORTS, sort: "ts", dir: "desc", filters: kind === "crash" ? ["version", "country", "fixed"] : ["version", "country", "level"] });
  const now = Date.now();
  const [{ pg, rows }, facets] = await Promise.all([
    queryEvents(env, kind, eventsQuery(kind, params, now), params.page, params.size),
    eventFacets(env, kind),
  ]);
  const csrfInput = admin ? csrfField(sess.csrf) : "";
  return eventsAdminPage(nonce(), kind, { rows, pg, params, facets, now, me: admin ? sess.acct.login : "", csrfInput, nav: admin });
}

const csrfField = (csrf) => `<input type="hidden" name="csrf" value="${escapeHtml(csrf)}"/>`;

// ---- vues d'administration (un onglet = une URL) --------------------------------------------

const ACCT_CACHE_MS = 120_000;
let acctCache = { at: 0, rows: null };

/**
 * Comptes (KV) en cache 2 minutes par isolat : un parcours de l'administration ne relit pas tous les comptes à
 * chaque page (KV gratuit : 1 000 listes par jour). `?refresh=1` force la relecture.
 */
async function cachedAccounts(env, refresh) {
  if (!refresh && acctCache.rows && Date.now() - acctCache.at < ACCT_CACHE_MS) return acctCache;
  acctCache = { at: Date.now(), rows: await adminAccounts(env) };
  return acctCache;
}

async function adminView(env, url, path, sess) {
  const n = nonce();
  const now = Date.now();
  const sp = url.searchParams;
  const me = sess.acct.login;
  const csrfInput = csrfField(sess.csrf);
  const refresh = sp.get("refresh") === "1";
  if (path === "/admin") {
    const [acc, anon, crashes24] = await Promise.all([cachedAccounts(env, refresh), anonOverview(env, now), countEvents(env, "crash", now - DAY_MS)]);
    return overviewPage(n, { accounts: acc.rows, anon, crashes24, asOf: acc.at, now, me, csrfInput });
  }
  if (path === "/admin/accounts") {
    const params = parseListParams(sp, { sorts: ACCOUNT_SORTS, sort: "created", dir: "desc", filters: ["country", "version"] });
    const acc = await cachedAccounts(env, refresh);
    const all = acc.rows.filter((r) => !r.legacy);
    const filtered = sortRows(filterAccounts(all, params, now), params.sort, params.dir, ACCOUNT_SORTS);
    const pg = paginate(filtered.length, params.page, params.size);
    const facets = {
      countries: countBy(all.flatMap((r) => [r.country, ...r.devices.map((d) => d.country)])).map(([k]) => k),
      versions: countBy(all.flatMap((r) => r.devices.map((d) => d.version))).map(([k]) => k),
    };
    return accountsPage(n, { rows: filtered.slice(pg.offset, pg.offset + pg.size), pg, params, facets, asOf: acc.at, now, me, csrfInput });
  }
  if (path === "/admin/devices") {
    const params = parseListParams(sp, { sorts: DEVICE_SORTS, sort: "seen", dir: "desc", filters: ["country", "version"] });
    const acc = await cachedAccounts(env, refresh);
    const all = flattenDevices(acc.rows);
    const filtered = sortRows(filterDevices(all, params, now), params.sort, params.dir, DEVICE_SORTS);
    const pg = paginate(filtered.length, params.page, params.size);
    const facets = { countries: countBy(all.map((d) => d.country)).map(([k]) => k), versions: countBy(all.map((d) => d.version)).map(([k]) => k) };
    return devicesPage(n, { rows: filtered.slice(pg.offset, pg.offset + pg.size), pg, params, facets, asOf: acc.at, now, me, csrfInput });
  }
  if (path === "/admin/anon") {
    const params = parseListParams(sp, { sorts: ANON_SORTS, sort: "last", dir: "desc", filters: ["platform", "version", "country"] });
    const [{ pg, rows }, facets] = await Promise.all([queryAnon(env, anonQuery(params, now), params.page, params.size), anonFacets(env)]);
    return anonPage(n, { rows, pg, params, facets, platforms: ANON_PLATFORMS, now, me, csrfInput });
  }
  // /admin/alerts
  const announcements = await loadAnnouncements(env);
  const annStats = await announcementStats(env, announcements);
  return alertsPage(n, { announcements, annStats, ok: sp.get("m"), err: sp.get("e"), now, me, csrfInput });
}

// ---- migration depuis l'ancien format ------------------------------------------

const LEGACY_MAC = /^[0-9a-f]{2}(:[0-9a-f]{2}){5}$/;

/**
 * POST /api/admin/migrate  (Authorization: Bearer <ADMIN_TOKEN>)
 *  - comptes protégés par un mot de passe : réécrits au nouveau format (fournisseurs
 *    chiffrés), l'ancien mot de passe reste valable, login = ancienne MAC ;
 *  - entrées SANS mot de passe : supprimées (elles étaient lisibles par n'importe qui) ;
 *  - crashs/journaux hérités (non nettoyés) et compteurs `lk:*` : supprimés.
 * Idempotent et paginé (?cursor=…&limit=…).
 */
async function adminMigrate(req, env, url) {
  if (!env.ADMIN_TOKEN) return new Response("Not found", { status: 404 });
  const c = await checkSecret(req, env, env.ADMIN_TOKEN, "admin");
  if (c.res) return c.res;
  // Turso : chaque clé coûte 1 à 3 sous-requêtes (plafond 50 par appel du Worker gratuit) → pages de 15 par défaut.
  const def = env.STORAGE === "turso" ? 15 : 500;
  const limit = Math.max(1, Math.min(env.STORAGE === "turso" ? 15 : 1000, parseInt(url.searchParams.get("limit") || String(def), 10) || def));
  const page = await env.CONFIG.list({ cursor: url.searchParams.get("cursor") || undefined, limit });
  const rep = { migrated: 0, purgedUnprotected: 0, purgedLogs: 0, purgedLocks: 0, conflicts: 0, skipped: 0 };
  for (const { name } of page.keys) {
    if (LEGACY_MAC.test(name)) await migrateLegacy(env, name, rep);
    else if (name.startsWith("lk:")) { await env.CONFIG.delete(name); rep.purgedLocks++; }
    else if (name.startsWith("crash:") || name.startsWith("event:")) {
      let v = null;
      try { v = JSON.parse(await env.CONFIG.get(name)); } catch { /* corrompu : supprimé */ }
      if (v?.v === 2) rep.skipped++; else { await env.CONFIG.delete(name); rep.purgedLogs++; }
    } else rep.skipped++;
  }
  return json({ ...rep, done: page.list_complete, cursor: page.list_complete ? null : page.cursor });
}

async function migrateLegacy(env, mac, rep) {
  let old;
  try { old = JSON.parse(await env.CONFIG.get(mac)); } catch { old = null; }
  if (!old?.passwordHash) { await env.CONFIG.delete(mac); rep.purgedUnprotected++; return; }
  if (!(await guardStub(env, `acct:${mac}`).claim())) { await env.CONFIG.delete(mac); rep.conflicts++; return; }
  const acct = { login: mac, passwordHash: old.passwordHash, salt: old.salt, devices: [], sessEpoch: 0, createdAt: Date.now(), migrated: true };
  const providers = (Array.isArray(old.providers) ? old.providers : []).slice(0, MAX_PROVIDERS).flatMap((p) => {
    const fd = new URLSearchParams({ kind: p?.kind ?? "", name: p?.name ?? "", url: p?.url ?? "", username: p?.username ?? "", password: p?.password ?? "", mac: p?.mac ?? "" });
    const r = parseProvider(fd);
    return r.provider ? [r.provider] : [];
  });
  await saveProviders(env, acct, providers);
  await putAccount(env, acct);
  await env.CONFIG.delete(mac); // le clair disparaît seulement une fois la copie chiffrée écrite
  rep.migrated++;
}
