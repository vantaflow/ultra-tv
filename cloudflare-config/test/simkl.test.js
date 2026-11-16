import { afterEach, describe, expect, it, vi } from "vitest";
import { env } from "cloudflare:workers";
import {
  simklEnabled, simklAuthorizeUrl, simklExchangeCode, simklRefresh, simklRevoke, simklApiUrl, fetchSimklLibrary, simklWriteRequest, simklScrobbleBody, simklSend,
} from "../src/simkl.js";
import { activeTracker, trackingStatus } from "../src/tracking.js";
import { keysFromEnv, encryptJson } from "../src/crypto.js";
import { getAccount, putAccount } from "../src/store.js";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";

const res = (status, body) => new Response(typeof body === "string" ? body : JSON.stringify(body), { status });

describe("simkl : identifiants et choix du service", () => {
  it("masqué sans identifiant ET secret", () => {
    expect(simklEnabled({})).toBe(false);
    expect(simklEnabled({ SIMKL_CLIENT_ID: "a" })).toBe(false);
    expect(simklEnabled({ SIMKL_CLIENT_ID: "a", SIMKL_CLIENT_SECRET: "b" })).toBe(true);
  });
  it("service actif : choix explicite, sinon Trakt puis Simkl", () => {
    const e = { TRAKT_CLIENT_ID: "t", SIMKL_CLIENT_ID: "a", SIMKL_CLIENT_SECRET: "b" };
    expect(activeTracker(e, {})).toBeNull();
    expect(activeTracker(e, { traktEnc: "x" })).toBe("trakt");
    expect(activeTracker(e, { simklEnc: "x" })).toBe("simkl");
    expect(activeTracker(e, { traktEnc: "x", simklEnc: "y" })).toBe("trakt");
    expect(activeTracker(e, { traktEnc: "x", simklEnc: "y", tracking: "simkl" })).toBe("simkl");
    expect(activeTracker(e, { traktEnc: "x", simklEnc: "y", tracking: "none" })).toBeNull();
    expect(activeTracker(e, { traktEnc: "x", tracking: "simkl" })).toBeNull();
    // Sans identifiants Simkl, un compte relié à Simkl n'a plus de service actif.
    expect(activeTracker({ TRAKT_CLIENT_ID: "t" }, { simklEnc: "y", tracking: "simkl" })).toBeNull();
    expect(trackingStatus({ TRAKT_CLIENT_ID: "t" }, {}).available).toEqual(["trakt"]);
    expect(trackingStatus(e, { simklEnc: "y" })).toMatchObject({ service: "simkl", choice: "simkl", available: ["trakt", "simkl"], linked: { trakt: false, simkl: true } });
  });
});

describe("simkl : API", () => {
  it("adresse d'autorisation et échange du code (secret dans le corps, jamais dans l'adresse)", async () => {
    const u = new URL(simklAuthorizeUrl("CID", "https://x.test/simkl/callback", "ST"));
    expect(u.origin + u.pathname).toBe("https://simkl.com/oauth2/authorize");

    expect(u.searchParams.get("client_id")).toBe("CID");
    expect(u.search).not.toContain("SECRET");
    expect(u.searchParams.get("scope")).toBe("media:read media:write");
    const f = vi.fn(async () => res(200, { access_token: "simkl_at_TOK", refresh_token: "simkl_rt_R", expires_in: 604800, token_type: "bearer", scope: "media:read media:write" }));
    const t = await simklExchangeCode("CID", "SECRET", "https://x.test/simkl/callback", "CODE", f, "VER");
    expect(t).toMatchObject({ access: "simkl_at_TOK", refresh: "simkl_rt_R" });
    expect(Math.abs(t.exp - (Date.now() + 604800_000))).toBeLessThan(5000);
    const [url, init] = f.mock.calls[0];
    expect(url).toBe("https://api.simkl.com/oauth2/token");
    expect(init.headers["content-type"]).toBe("application/x-www-form-urlencoded");
    const form = new URLSearchParams(init.body);
    expect(Object.fromEntries(form)).toEqual({ grant_type: "authorization_code", client_id: "CID", client_secret: "SECRET", code: "CODE", redirect_uri: "https://x.test/simkl/callback", code_verifier: "VER" });
    expect(await simklExchangeCode("C", "S", "r", "c", async () => res(400, {}))).toEqual({ error: "denied" });
    expect(await simklExchangeCode("C", "S", "r", "c", async () => res(500, {}))).toEqual({ error: "upstream" });
    expect(await simklExchangeCode("C", "S", "r", "c", async () => res(200, {}))).toEqual({ error: "upstream" });
  });

  const data = {
    "/sync/all-items/movies/plantowatch": { movies: [{ status: "plantowatch", movie: { title: "Alien", year: 1979, ids: { simkl: 1, tmdb: "348" } } }] },
    "/sync/all-items/shows/plantowatch": { shows: [{ show: { title: "Lost", year: 2004, ids: { tmdb: 4607 } }, user_rating: 7 }] },
    "/sync/all-items/movies/completed": { movies: [{ movie: { title: "Dune", year: 2021, ids: { tmdb: 438631 } }, user_rating: 9 }] },
    "/sync/all-items/shows/watching": { shows: [{ show: { title: "Dark", year: 2017, ids: { tmdb: 70523 } }, last_watched_at: "2026-10-01T20:00:00Z", seasons: [{ number: 1, episodes: [{ number: 1 }, { number: 2 }] }] }] },
  };
  const fetchFn = vi.fn(async (u) => res(200, data[new URL(u).pathname] ?? {}));

  it("bibliothèque au format commun : watchlist, vus, notes", async () => {
    const lib = await fetchSimklLibrary("CID", "ACC", fetchFn);
    expect(lib.watchlist).toEqual([
      { type: "movie", tmdb: 348, year: 1979, title: "Alien" },
      { type: "show", tmdb: 4607, year: 2004, title: "Lost" },
    ]);
    expect(lib.watched.movies[0]).toMatchObject({ title: "Dune", tmdb: 438631 });
    expect(lib.watched.shows[0]).toMatchObject({ title: "Dark", episodes: ["1x1", "1x2"], lastWatchedAt: Date.parse("2026-10-01T20:00:00Z") });
    expect(lib.ratings.movies).toEqual([{ type: "movie", tmdb: 438631, year: 2021, title: "Dune", rating: 9 }]);
    expect(lib.ratings.shows[0]).toMatchObject({ title: "Lost", rating: 7 });
    expect(lib.recommendations).toEqual([]);
    // Nombre d'appels borné (une sous-requête de stockage chacune côté Worker : ici, appels réseau en parallèle).
    expect(fetchFn.mock.calls.length).toBe(6);
    const u0 = new URL(fetchFn.mock.calls[0][0]);
    expect(u0.searchParams.get("client_id")).toBe("CID");
    expect(u0.searchParams.get("app-name")).toBe("ultra-tv");
    expect(u0.searchParams.get("app-version")).toBe("1.0");
    expect(fetchFn.mock.calls[0][1].headers["user-agent"]).toBe("ultra-tv/1.0");
    expect(fetchFn.mock.calls[0][1].headers.authorization).toBe("Bearer ACC");
  });
  it("accès refusé remonté ; tout en échec : erreur", async () => {
    await expect(fetchSimklLibrary("C", "A", async () => res(401, {}))).rejects.toMatchObject({ unauthorized: true });
    await expect(fetchSimklLibrary("C", "A", async () => res(503, {}))).rejects.toMatchObject({ upstream: true });
  });

  it("requêtes d'écriture", () => {
    const now = "2026-10-20T10:00:00.000Z";
    expect(simklWriteRequest("watchlist", { action: "add", type: "movie" }, 348, now)).toEqual({ path: "/sync/add-to-list", body: { movies: [{ to: "plantowatch", ids: { tmdb: 348 } }] } });
    expect(simklWriteRequest("watchlist", { action: "remove", type: "show" }, 4607, now)).toBeNull();
    expect(simklWriteRequest("history", { action: "add", type: "movie" }, 348, now)).toEqual({ path: "/sync/history", body: { movies: [{ ids: { tmdb: 348 }, watched_at: now }] } });
    expect(simklWriteRequest("history", { action: "remove", type: "episodes", episodes: [{ season: 1, episode: 3 }] }, 4607, now)).toEqual({
      path: "/sync/history/remove", body: { shows: [{ ids: { tmdb: 4607 }, seasons: [{ number: 1, episodes: [{ number: 3 }] }] }] },
    });
    expect(simklWriteRequest("rating", { type: "movie", rating: 8 }, 348, now)).toEqual({ path: "/sync/ratings", body: { movies: [{ ids: { tmdb: 348 }, rating: 8, rated_at: now }] } });
    expect(simklScrobbleBody({ kind: "episode", season: 1, episode: 2, progress: 50 }, 4607)).toEqual({ show: { ids: { tmdb: 4607 } }, episode: { season: 1, number: 2 }, progress: 50 });
    expect(simklScrobbleBody({ kind: "movie", progress: 5 }, null)).toBeNull();
  });
  it("réponses traduites", async () => {
    const s = (status, body) => simklSend("C", "A", "/sync/history", {}, async () => res(status, body));
    expect(await s(201, { added: { movies: 1 }, not_found: { movies: [] } })).toBe("ok");
    expect(await s(201, { added: { movies: 0 }, not_found: { movies: [{ ids: {} }] } })).toBe("unmatched");
    expect(await s(401, {})).toBe("unauthorized");
    expect(await s(500, {})).toBe("upstream");
  });
});

describe("simkl : routes appareil", () => {
  afterEach(() => vi.unstubAllGlobals());

  async function linkedSimkl(tracking, tok = { access: "SACC", refresh: "simkl_rt_R", exp: Date.now() + 5 * 86_400_000 }) {
    const acct = await newAccount();
    const dev = await pairDevice(acct, "Salon");
    const a = await getAccount(env, acct.login);
    a.simklEnc = await encryptJson(keysFromEnv(env), tok, a.login);
    a.simkl = { user: "moi", at: Date.now() };
    if (tracking) a.tracking = tracking;
    await putAccount(env, a);
    return { acct, dev };
  }
  const post = (dev, path, json) => call(path, { method: "POST", ip: freshIp(), headers: bearer(dev.token), json });
  const get = (dev, path) => call(path, { ip: freshIp(), headers: bearer(dev.token) });

  it("état et changement du service depuis l'appareil", async () => {
    const { acct, dev } = await linkedSimkl();
    const st = await (await get(dev, "/api/device/tracking")).json();
    expect(st).toMatchObject({ service: "simkl", available: ["trakt", "simkl"], linked: { trakt: false, simkl: true } });
    const r = await post(dev, "/api/device/tracking", { service: "none" });
    expect(await r.json()).toMatchObject({ service: null, choice: "none" });
    expect((await getAccount(env, acct.login)).tracking).toBe("none");
    expect((await post(dev, "/api/device/tracking", { service: "autre" })).status).toBe(400);
    expect((await call("/api/device/tracking", { ip: freshIp() })).status).toBe(401);
    // « none » : plus rien n'est envoyé à aucun service.
    expect(await (await post(dev, "/api/device/trakt/watchlist", { action: "add", type: "movie", title: "Alien", tmdb: 348 })).json()).toEqual({ linked: false });
  });

  it("écriture via l'interface commune : envoyée à Simkl avec son jeton", async () => {
    const { dev } = await linkedSimkl();
    const calls = [];
    vi.stubGlobal("fetch", vi.fn(async (u, init) => { calls.push({ url: String(u), init }); return res(201, { added: { movies: 1 }, not_found: { movies: [] } }); }));
    const r = await post(dev, "/api/device/tracking/watchlist", { action: "add", type: "movie", title: "Alien", tmdb: 348 });
    expect(await r.json()).toEqual({ linked: true, matched: true, ok: true, tmdb: 348 });
    expect(calls[0].url).toMatch(/^https:\/\/api\.simkl\.com\/sync\/add-to-list\?/);
    expect(calls[0].init.headers.authorization).toBe("Bearer SACC");
    const cu = new URL(calls[0].url);
    expect(cu.searchParams.get("client_id")).toBe("test-simkl-id-not-real");
    expect(cu.searchParams.get("app-name")).toBe("ultra-tv");
    expect(cu.searchParams.get("app-version")).toBe("1.0");
    expect(calls[0].init.headers["user-agent"]).toBe("ultra-tv/1.0");
    expect(calls[0].init.headers["content-type"]).toBe("application/json");
    const rm = await post(dev, "/api/device/trakt/watchlist", { action: "remove", type: "movie", title: "Alien", tmdb: 348 });
    expect(await rm.json()).toMatchObject({ linked: true, ok: false, error: "unsupported" });
    expect(calls).toHaveLength(1);
  });

  it("bibliothèque : format commun, marquée du service, cache propre à chaque service", async () => {
    const { acct, dev } = await linkedSimkl();
    vi.stubGlobal("fetch", vi.fn(async (u) => res(200, { shows: [], movies: [{ movie: { title: "Alien", year: 1979, ids: { tmdb: 348 } } }] })));
    const lib = await (await get(dev, "/api/device/tracking/library?lang=fr")).json();
    expect(lib.service).toBe("simkl");
    expect(lib.watchlist[0]).toMatchObject({ title: "Alien", keys: ["alien"] });
    // Changer de service oublie la bibliothèque en cache.
    await post(dev, "/api/device/tracking", { service: "none" });
    expect(await env.CONFIG.get(`tlib:${acct.login}:fr`)).toBeNull();
  });

  it("refus du jeton et échec du renouvellement : jeton oublié, compte marqué révoqué", async () => {
    const { acct, dev } = await linkedSimkl();
    vi.stubGlobal("fetch", vi.fn(async (u) => (String(u).includes("/oauth2/token") ? res(400, { error: "invalid_grant" }) : res(401, {}))));
    const r = await post(dev, "/api/device/trakt/history", { action: "add", type: "movie", title: "Alien", tmdb: 348 });
    expect(await r.json()).toEqual({ linked: false });
    const a = await getAccount(env, acct.login);
    expect(a.simklEnc).toBeUndefined();
    expect(a.simkl.revoked).toBe(true);
  });
});

describe("simkl : tableau de bord", () => {
  it("le panneau Simkl et le choix apparaissent avec les identifiants ; le choix est enregistré", async () => {
    const acct = await newAccount();
    const html = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(html).toContain('action="/simkl/connect"');
    expect(html).toContain('name="service"');
    const r = await call("/tracking/service", { method: "POST", cookie: acct.cookie, ip: acct.ip, form: { csrf: acct.csrf, service: "none" } });
    expect(r.status).toBe(302);
    expect((await getAccount(env, acct.login)).tracking).toBe("none");
    const bad = await call("/tracking/service", { method: "POST", cookie: acct.cookie, ip: acct.ip, form: { csrf: acct.csrf, service: "x" } });
    expect(bad.headers.get("location")).toContain("e=tracking");
  });
  it("connexion : redirige vers Simkl avec état et cookie lié au navigateur", async () => {
    const acct = await newAccount();
    const r = await call("/simkl/connect", { method: "POST", cookie: acct.cookie, ip: acct.ip, form: { csrf: acct.csrf } });
    expect(r.status).toBe(302);
    expect(r.headers.get("location")).toMatch(/^https:\/\/simkl\.com\/oauth2\/authorize\?/);
    expect(r.headers.get("set-cookie")).toContain("utv_simkl=");
    // Retour sans cookie : refus, rien d'enregistré.
    const back = await call("/simkl/callback?code=abc&state=" + "a".repeat(24), { ip: acct.ip });
    expect(await back.text()).toContain("e=simkl");
  });
});

describe("simkl : PKCE", () => {
  it("l'adresse d'autorisation porte le défi S256 quand il est fourni", () => {
    const u = new URL(simklAuthorizeUrl("CID", "https://x.test/simkl/callback", "ST", "CHAL"));
    expect(u.searchParams.get("code_challenge")).toBe("CHAL");
    expect(u.searchParams.get("code_challenge_method")).toBe("S256");
  });
  it("l'échange du code envoie le vérificateur", async () => {
    let body = null;
    const fetchFn = async (_u, init) => { body = new URLSearchParams(init.body); return new Response(JSON.stringify({ access_token: "tok", refresh_token: "rt", expires_in: 100 }), { status: 200 }); };
    const r = await simklExchangeCode("CID", "SEC", "https://x.test/simkl/callback", "CODE", fetchFn, "VER");
    expect(r).toMatchObject({ access: "tok", refresh: "rt" });
    expect(body.get("code_verifier")).toBe("VER");
  });
});

describe("simkl : renouvellement des jetons (Auth V2)", () => {
  afterEach(() => vi.unstubAllGlobals());
  const post = (dev, path, json) => call(path, { method: "POST", ip: freshIp(), headers: bearer(dev.token), json });
  async function linked(tok) {
    const acct = await newAccount();
    const dev = await pairDevice(acct, "Salon");
    const a = await getAccount(env, acct.login);
    a.simklEnc = await encryptJson(keysFromEnv(env), tok, a.login);
    a.simkl = { user: "moi", at: Date.now() };
    await putAccount(env, a);
    return { acct, dev };
  }
  const stored = async (acct) => (await import("../src/crypto.js")).decryptJson(keysFromEnv(env), (await getAccount(env, acct.login)).simklEnc, acct.login);
  const ok = () => res(201, { added: { movies: 1 }, not_found: { movies: [] } });
  const addBody = { action: "add", type: "movie", title: "Alien", tmdb: 348 };

  it("simklRefresh : form-urlencoded, même jeton de renouvellement conservé", async () => {
    const f = vi.fn(async () => res(200, { access_token: "NEW", expires_in: 604800 }));
    const t = await simklRefresh("CID", "SEC", "simkl_rt_R", f);
    expect(t).toMatchObject({ access: "NEW", refresh: "simkl_rt_R" });
    const [url, init] = f.mock.calls[0];
    expect(url).toBe("https://api.simkl.com/oauth2/token");
    expect(init.headers["content-type"]).toBe("application/x-www-form-urlencoded");
    expect(Object.fromEntries(new URLSearchParams(init.body))).toEqual({ grant_type: "refresh_token", client_id: "CID", client_secret: "SEC", refresh_token: "simkl_rt_R" });
    expect(await simklRefresh("C", "S", "r", async () => res(401, {}))).toEqual({ error: "revoked" });
    expect(await simklRefresh("C", "S", "r", async () => res(500, {}))).toEqual({ error: "upstream" });
  });

  it("simklRevoke : form-urlencoded, sans lever en cas d'échec", async () => {
    const f = vi.fn(async () => res(200, {}));
    expect(await simklRevoke("CID", "TOK", f)).toBe(true);
    expect(f.mock.calls[0][0]).toBe("https://api.simkl.com/oauth2/revoke");
    expect(Object.fromEntries(new URLSearchParams(f.mock.calls[0][1].body))).toEqual({ client_id: "CID", token: "TOK" });
    expect(await simklRevoke("C", "T", async () => { throw new Error("réseau"); })).toBe(false);
  });

  it("paramètres d'URL et version de l'appli", () => {
    const u = new URL(simklApiUrl("/sync/history", "CID", "1.3.36"));
    expect(Object.fromEntries(u.searchParams)).toEqual({ client_id: "CID", "app-name": "ultra-tv", "app-version": "1.3.36" });
  });

  it("renouvellement proactif : jeton proche de l'échéance renouvelé avant l'appel", async () => {
    const { acct, dev } = await linked({ access: "OLD", refresh: "simkl_rt_R", exp: Date.now() + 3600_000 });
    const calls = [];
    vi.stubGlobal("fetch", vi.fn(async (u, init) => {
      calls.push({ url: String(u), init });
      return String(u).includes("/oauth2/token") ? res(200, { access_token: "NEW", refresh_token: "simkl_rt_R", expires_in: 604800 }) : ok();
    }));
    expect((await (await post(dev, "/api/device/tracking/watchlist", addBody)).json()).ok).toBe(true);
    expect(calls[0].url).toBe("https://api.simkl.com/oauth2/token");
    expect(calls[1].init.headers.authorization).toBe("Bearer NEW");
    const t = await stored(acct);
    expect(t).toMatchObject({ access: "NEW", refresh: "simkl_rt_R" });
    expect(t.exp).toBeGreaterThan(Date.now() + 6 * 86_400_000);
  });

  it("jeton valide : aucun renouvellement", async () => {
    const { dev } = await linked({ access: "OK1", refresh: "simkl_rt_R", exp: Date.now() + 5 * 86_400_000 });
    const f = vi.fn(async () => ok());
    vi.stubGlobal("fetch", f);
    await post(dev, "/api/device/tracking/watchlist", addBody);
    expect(f).toHaveBeenCalledTimes(1);
  });

  it("401 : renouvellement puis une seule nouvelle tentative", async () => {
    const { acct, dev } = await linked({ access: "OLD", refresh: "simkl_rt_R", exp: Date.now() + 5 * 86_400_000 });
    const seen = [];
    vi.stubGlobal("fetch", vi.fn(async (u, init) => {
      if (String(u).includes("/oauth2/token")) { seen.push("refresh"); return res(200, { access_token: "NEW", expires_in: 604800 }); }
      seen.push(init.headers.authorization);
      return init.headers.authorization === "Bearer NEW" ? ok() : res(401, {});
    }));
    const r = await post(dev, "/api/device/tracking/watchlist", addBody);
    expect(await r.json()).toMatchObject({ linked: true, ok: true });
    expect(seen).toEqual(["Bearer OLD", "refresh", "Bearer NEW"]);
    expect((await stored(acct)).access).toBe("NEW");
  });

  it("401 persistant après renouvellement : une seule nouvelle tentative, jeton oublié", async () => {
    const { acct, dev } = await linked({ access: "OLD", refresh: "simkl_rt_R", exp: Date.now() + 5 * 86_400_000 });
    const f = vi.fn(async (u) => (String(u).includes("/oauth2/token") ? res(200, { access_token: "NEW", expires_in: 604800 }) : res(401, {})));
    vi.stubGlobal("fetch", f);
    expect(await (await post(dev, "/api/device/tracking/watchlist", addBody)).json()).toEqual({ linked: false });
    expect(f).toHaveBeenCalledTimes(3);
    expect((await getAccount(env, acct.login)).simkl.revoked).toBe(true);
  });

  it("ancien enregistrement { access } : reconnexion forcée proprement", async () => {
    const { acct, dev } = await linked({ access: "VIEUX" });
    const f = vi.fn(async () => ok());
    vi.stubGlobal("fetch", f);
    expect(await (await post(dev, "/api/device/tracking/watchlist", addBody)).json()).toEqual({ linked: false });
    expect(f).not.toHaveBeenCalled();
    const a = await getAccount(env, acct.login);
    expect(a.simklEnc).toBeUndefined();
    expect(a.simkl.revoked).toBe(true);
  });

  it("déconnexion : révocation chez Simkl, sans bloquer si elle échoue", async () => {
    const { acct } = await linked({ access: "simkl_at_X", refresh: "simkl_rt_R", exp: Date.now() + 5 * 86_400_000 });
    const calls = [];
    vi.stubGlobal("fetch", vi.fn(async (u, init) => { calls.push({ url: String(u), init }); throw new Error("réseau"); }));
    const r = await call("/simkl/disconnect", { method: "POST", cookie: acct.cookie, ip: acct.ip, form: { csrf: acct.csrf } });
    expect(r.status).toBe(302);
    expect(calls[0].url).toBe("https://api.simkl.com/oauth2/revoke");
    expect(Object.fromEntries(new URLSearchParams(calls[0].init.body))).toMatchObject({ token: "simkl_at_X" });
    expect((await getAccount(env, acct.login)).simklEnc).toBeUndefined();
  });
});
