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

import { Guard } from "./guard.js";
import { hashPassword, verifyPassword, randomToken, sha256Hex, timingSafeEqual } from "./crypto.js";
import {
  ConfigError, json, redirect, tooMany, nonce, readJson, readForm, readLimited, sameOrigin, clientIp,
  withSecurityHeaders, readCookie, sessionCookieHeader, clearCookieHeader, COOKIE, signSession, verifySession,
  csrfToken, parseBearer, parseOpsSecret, assertSessionSecret,
} from "./http.js";
import {
  guardStub, normalizeLogin, isMacLogin, getAccount, putAccount, loadProviders, saveProviders, deleteAccount,
  newDeviceToken, registerDevice, authDevice, revokeDevice, rotateDevice, parseProvider, publicProvider, syncProvider, parseDeviceProvider,
  isVisibleTo, assignmentOf, parseAssign, dropDeviceFromAssignments, renameDevice,
  MAX_PROVIDERS, MAX_DEVICES,
} from "./store.js";
import { tmdbProxy } from "./tmdb.js";
import { subtitlesSearch, subtitlesDownload } from "./subtitles.js";
import { sanitizeText } from "./sanitize.js";
import { loginPage, signupPage, dashboardPage, eventsPage, crashesPage } from "./pages.js";

export { Guard };

const PAIR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 32 symboles, sans I/O/0/1
const PAIR_TTL_S = 600;
const MIN_PASSWORD = 10;

const CRASH_TTL_S = 30 * 24 * 3600;
const EVENT_TTL_S = 7 * 24 * 3600;
const EVENT_MAX_BODY = 8 * 1024;
const CRASH_MAX_BODY = 48 * 1024;

// ---- limitation de débit ------------------------------------------------------

/** Renvoie une réponse 429 si la limite est dépassée, sinon null. */
async function limited(env, key, limit, windowSec, asJson = false) {
  const r = await guardStub(env, key).hit(limit, windowSec);
  return r.ok ? null : tooMany(r.retryAfter, asJson);
}

async function lockedOut(env, key, asJson = false) {
  const s = await guardStub(env, key).lockState();
  return s.locked ? tooMany(s.retryAfter, asJson) : null;
}

const LOGIN_LOCK = { threshold: 5, baseSec: 60, capSec: 900 };   // par compte
const IP_LOCK = { threshold: 10, baseSec: 60, capSec: 3600 };    // par IP
const SECRET_LOCK = { threshold: 5, baseSec: 60, capSec: 3600 }; // OPS / ADMIN

// ---- point d'entrée -----------------------------------------------------------

export default {
  async fetch(req, env) {
    try {
      return withSecurityHeaders(await route(req, env));
    } catch (err) {
      console.error("erreur:", err instanceof ConfigError ? `configuration: ${err.message}` : err?.message);
      return withSecurityHeaders(json({ error: err instanceof ConfigError ? "server_misconfigured" : "internal" }, 500));
    }
  },
};

async function route(req, env) {
  const url = new URL(req.url);
  const { pathname: path } = url;
  const m = req.method;

  // Aucune origine tierce n'a besoin d'appeler ce Worker depuis un navigateur : pas de CORS.
  if (m === "OPTIONS") return new Response(null, { status: 204 });

  // ---- API application TV (jeton d'appareil / appairage) ----
  if (path === "/api/pair/start" && m === "POST") return pairStart(req, env);
  if (path === "/api/pair/poll" && m === "POST") return pairPoll(req, env);
  if (path === "/api/config" && m === "GET") return deviceConfig(req, env);
  if (path === "/api/device/rotate" && m === "POST") return deviceRotate(req, env);
  if (path === "/api/device/providers" && (m === "POST" || m === "PUT")) return devicePutProvider(req, env);
  const delProv = path.match(/^\/api\/device\/providers\/([0-9a-f]{8})$/);
  if (delProv && m === "DELETE") return deviceDeleteProvider(req, env, delProv[1]);
  if ((path === "/api/device/self" && m === "POST") || (path === "/api/device" && (m === "PATCH" || m === "POST"))) return deviceRename(req, env);
  if ((path === "/api/subtitles/search" || path === "/api/subtitles/download") && m === "GET") return deviceSubtitles(req, env, path.endsWith("/search"), url);
  if (path.startsWith("/api/tmdb/") && m === "GET") return deviceTmdb(req, env, path.slice("/api/tmdb/".length), url);
  if (path === "/api/event" && m === "POST") return ingest(req, env, "event");
  if (path === "/api/crash" && m === "POST") return ingest(req, env, "crash");
  // Ancienne lecture anonyme par MAC : supprimée (la MAC n'est pas un secret).
  if (/^\/api\/config\//.test(path)) {
    return json({ error: "pairing_required", message: "La lecture par adresse MAC est supprimée. Mets à jour l'application et appaire-la." }, 410);
  }

  // ---- exploitation ----
  if ((path === "/crashes" || path === "/logs") && m === "GET") return opsPage(req, env, path === "/crashes" ? "crash" : "event");
  if (path === "/api/admin/migrate" && m === "POST") return adminMigrate(req, env, url);

  // ---- pages publiques ----
  if (path === "/login" && m === "GET") { const n = nonce(); return loginPage(n, url.searchParams.get("e")); }
  if (path === "/login" && m === "POST") return doLogin(req, env);
  if (path === "/signup" && m === "GET") { const n = nonce(); return signupPage(n, url.searchParams.get("e")); }
  if (path === "/signup" && m === "POST") return doSignup(req, env);

  // ---- tableau de bord (session) ----
  const sess = await currentSession(req, env);
  const dashboardRoute = (path === "/" || path === "/dashboard") && m === "GET";
  const mutating = m === "POST" && ["/pair", "/providers", "/password", "/account/delete", "/logout"].includes(path)
    || (m === "POST" && /^\/(devices\/[0-9a-f]+\/(revoke|rename)|providers\/[0-9a-f]+\/(delete|assign))$/.test(path));
  if (!dashboardRoute && !mutating) return new Response("Not found", { status: 404 });
  if (!sess) return redirect("/login");

  if (dashboardRoute) {
    const n = nonce();
    const providers = await loadProviders(env, sess.acct);
    return dashboardPage(n, { acct: sess.acct, providers, csrf: sess.csrf, err: url.searchParams.get("e"), ok: url.searchParams.get("m") });
  }

  // Mutations : Origin + jeton CSRF + débit par compte.
  if (!sameOrigin(req)) return new Response("Forbidden", { status: 403 });
  const parsed = await readForm(req);
  if (parsed.error) return parsed.error;
  const form = parsed.form;
  if (!timingSafeEqual(form.get("csrf") || "", sess.csrf)) return new Response("Forbidden (csrf)", { status: 403 });
  const rl = await limited(env, `dash:${sess.acct.login}`, 120, 3600);
  if (rl) return rl;

  const acct = sess.acct;
  if (path === "/logout") {
    acct.sessEpoch = (acct.sessEpoch || 0) + 1; // invalide aussi le cookie s'il a fuité
    await putAccount(env, acct);
    return redirect("/login", { "set-cookie": clearCookieHeader() });
  }
  if (path === "/pair") return confirmPairing(env, acct, form);
  if (path === "/providers") return addProvider(env, acct, form);
  if (path === "/password") return changePassword(env, acct, form, req);
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
  const providers = await loadProviders(env, acct);
  await saveProviders(env, acct, providers.filter((p) => p.id !== del[1]));
  await putAccount(env, acct);
  return redirect("/");
}

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
    return redirect("/login?e=pw");
  }
  await guardStub(env, lockKey).lockClear();
  if (verdict.needsRehash) {
    acct.passwordHash = await hashPassword(password);
    delete acct.salt;
    await putAccount(env, acct);
  }
  return startSession(env, acct);
}

async function doSignup(req, env) {
  if (!sameOrigin(req)) return new Response("Forbidden", { status: 403 });
  const rl = await limited(env, `signup:ip:${clientIp(req)}`, 5, 3600);
  if (rl) return rl;
  const parsed = await readForm(req);
  if (parsed.error) return parsed.error;
  const f = parsed.form;
  const login = normalizeLogin(f.get("login"));
  const password = (f.get("password") || "").slice(0, 256);
  if (!login) return redirect("/signup?e=login");
  if (password.length < MIN_PASSWORD) return redirect("/signup?e=short");
  if (password !== (f.get("confirm") || "")) return redirect("/signup?e=mismatch");
  // Un compte hérité (clé = MAC en clair) ne peut pas être « squatté » avant sa migration.
  if (isMacLogin(login) && (await env.CONFIG.get(login)) !== null) return redirect("/signup?e=taken");
  if (!(await guardStub(env, `acct:${login}`).claim())) return redirect("/signup?e=taken");
  try {
    const acct = { login, passwordHash: await hashPassword(password), devices: [], sessEpoch: 0, createdAt: Date.now() };
    await saveProviders(env, acct, []);
    await putAccount(env, acct);
    return await startSession(env, acct);
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
    if (r.ok) return json({ code: formatCode(code), pollSecret, expiresIn: PAIR_TTL_S, interval: 3 });
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
  const lock = await lockedOut(env, `lock:ip:${clientIp(req)}`, true);
  if (lock) return { res: lock };
  const auth = await authDevice(env, token);
  if (!auth) {
    // Un jeton présenté mais faux compte comme un échec (anti-devinette) ; l'absence de jeton non.
    if (token) await guardStub(env, `lock:ip:${clientIp(req)}`).lockFail({ threshold: 20, baseSec: 60, capSec: 3600 });
    return { res: json({ error: "unauthorized" }, 401, { "www-authenticate": 'Bearer realm="ultratv"' }) };
  }
  return { auth };
}

async function deviceConfig(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `cfg:ip:${clientIp(req)}`, 300, 600, true))
    || (await limited(env, `cfg:dev:${auth.device.id}`, 60, 600, true));
  if (rl) return rl;
  const version = auth.acct.cfgVersion || 0;
  const etag = `"v${version}"`;
  // Synchro incrémentale : rien n'a changé depuis la version connue de l'appareil.
  if (req.headers.get("if-none-match") === etag) return new Response(null, { status: 304, headers: { etag, "cache-control": "no-store" } });
  // Un appareil ne reçoit QUE les fournisseurs qui lui sont affectés (ou affectés à tous).
  const providers = (await loadProviders(env, auth.acct)).filter((p) => isVisibleTo(p, auth.device.id));
  const devices = (auth.acct.devices || []).map((d) => ({ id: d.id, name: d.name, model: d.label || "", lastSeen: d.lastSeen || 0, isCurrent: d.id === auth.device.id }));
  return json(
    { version, self: auth.device.id, devices, providers: providers.map(syncProvider) },
    200, { etag, "cache-control": "no-store" },
  );
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
  const providers = await loadProviders(env, auth.acct);
  const wanted = body.value.id;
  if (wanted !== undefined && !(typeof wanted === "string" && /^[0-9a-f]{8}$/.test(wanted))) return json({ error: "invalid", field: "id" }, 400);
  let assign = null;
  if (body.value.shareWith !== undefined) {
    const a = parseAssign(body.value.shareWith, auth.acct);
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
    providers[i] = { ...r.provider, id: old.id, createdAt: old.createdAt, originDeviceId: old.originDeviceId, originName: old.originName, assign: assign ?? assignmentOf(old) };
    status = 200; savedId = old.id;
  } else {
    if (providers.length >= MAX_PROVIDERS) return json({ error: "limit" }, 409);
    // Créée depuis un appareil : privée à cet appareil, sauf partage explicite.
    r.provider.assign = assign ?? [auth.device.id];
    if (Array.isArray(r.provider.assign) && !r.provider.assign.includes(auth.device.id)) r.provider.assign.push(auth.device.id);
    providers.push(r.provider); savedId = r.provider.id;
  }
  await saveProviders(env, auth.acct, providers);
  await putAccount(env, auth.acct);
  return json({ version: auth.acct.cfgVersion, provider: syncProvider(providers.find((p) => p.id === savedId)) }, status);
}

async function deviceDeleteProvider(req, env, id) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `pput:ip:${clientIp(req)}`, 120, 600, true))
    || (await limited(env, `pput:dev:${auth.device.id}`, 30, 600, true));
  if (rl) return rl;
  const providers = await loadProviders(env, auth.acct);
  const p = providers.find((x) => x.id === id);
  if (!p || !isVisibleTo(p, auth.device.id)) return json({ error: "not_found" }, 404);
  // Par défaut l'appareil se RETIRE de l'affectation ; `?all=1` supprime le fournisseur du compte.
  const everywhere = new URL(req.url).searchParams.get("all") === "1";
  const a = assignmentOf(p);
  let next;
  if (everywhere) next = providers.filter((x) => x.id !== id);
  else {
    const rest = a === "all" ? (auth.acct.devices || []).map((d) => d.id).filter((d) => d !== auth.device.id) : a.filter((d) => d !== auth.device.id);
    next = rest.length === 0 ? providers.filter((x) => x.id !== id) : providers.map((x) => (x.id === id ? { ...x, assign: rest } : x));
  }
  await saveProviders(env, auth.acct, next);
  await putAccount(env, auth.acct);
  return json({ version: auth.acct.cfgVersion });
}

/** Renomme CET appareil (étiquette affichée dans le tableau de bord et dans les listes de partage). */
async function deviceRename(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = await limited(env, `pput:dev:${auth.device.id}`, 30, 600, true);
  if (rl) return rl;
  const body = await readJson(req, 1024);
  if (body.error) return body.error;
  if (typeof body.value.name !== "string" || !(await renameDevice(env, auth.acct, auth.device.id, body.value.name))) return json({ error: "invalid", field: "name" }, 400);
  return json({ ok: true });
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

// Sous-titres OpenSubtitles : mêmes garde-fous que TMDB (appareil appairé, débit par appareil et par IP), plus serrés
// car chaque téléchargement consomme le quota de la clé.
async function deviceSubtitles(req, env, isSearch, url) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = (await limited(env, `sub:ip:${clientIp(req)}`, 120, 600, true))
    || (await limited(env, `sub:dev:${auth.device.id}`, 40, 600, true));
  if (rl) return rl;
  return isSearch ? subtitlesSearch(url.searchParams, env) : subtitlesDownload(url.searchParams, env);
}

async function deviceRotate(req, env) {
  const { auth, res } = await deviceAuth(req, env);
  if (res) return res;
  const rl = await limited(env, `rotate:dev:${auth.device.id}`, 5, 3600, true);
  if (rl) return rl;
  const token = await rotateDevice(env, auth.acct, auth.device);
  return json({ token, deviceId: auth.device.id });
}

// ---- ingestion ---------------------------------------------------------------

const LEVELS = ["debug", "info", "warn", "error"];
const int = (v) => (Number.isFinite(v) ? Math.trunc(v) : null);
const rand = (n) => [...crypto.getRandomValues(new Uint8Array(n))].map((b) => b.toString(16).padStart(2, "0")).join("");

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
  };
  const key = `${kind}:${base.ts}:${rand(6)}`;
  const entry = isCrash
    ? { ...base, androidSdk: int(b.androidSdk), stack: sanitizeText(b.stack, 32 * 1024) }
    : { ...base, level: LEVELS.includes(b.level) ? b.level : "info", tag: sanitizeText(b.tag, 32) || "app", message: sanitizeText(b.message, 4000) };
  await env.CONFIG.put(key, JSON.stringify(entry), { expirationTtl: isCrash ? CRASH_TTL_S : EVENT_TTL_S });
  return json({ ok: true });
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

async function opsPage(req, env, kind) {
  const c = await checkSecret(req, env, env.OPS_TOKEN, "ops");
  if (c.res) return c.res;
  const limit = Math.max(10, Math.min(500, parseInt(new URL(req.url).searchParams.get("limit") || "100", 10) || 100));
  const keys = [];
  let cursor;
  do {
    const r = await env.CONFIG.list({ prefix: `${kind}:`, cursor, limit: 1000 });
    keys.push(...r.keys.map((k) => k.name));
    cursor = r.list_complete ? undefined : r.cursor;
  } while (cursor && keys.length < 5000);
  const recent = keys.sort().reverse().slice(0, limit);
  const items = (await Promise.all(recent.map(async (k) => {
    try { const v = JSON.parse(await env.CONFIG.get(k)); return v?.v === 2 ? v : null; } catch { return null; }
  }))).filter(Boolean);
  const n = nonce();
  return kind === "crash" ? crashesPage(n, items) : eventsPage(n, items);
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
  const limit = Math.max(1, Math.min(1000, parseInt(url.searchParams.get("limit") || "500", 10) || 500));
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
