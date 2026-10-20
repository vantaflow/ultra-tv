import { afterEach, describe, expect, it, vi } from "vitest";
import { env } from "cloudflare:workers";
import { parseWatchlist, parseHistory, parseRating, traktWriteRequest, sendSync, MAX_HISTORY_EPISODES } from "../src/trakt.js";
import { fetchTraktLibrary, libraryForDevice, enrichTitles, MAX_LISTS } from "../src/traktlib.js";
import { keysFromEnv, encryptJson } from "../src/crypto.js";
import { getAccount, putAccount } from "../src/store.js";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";

const res = (status, body) => new Response(typeof body === "string" ? body : JSON.stringify(body), { status });

describe("trakt : écritures (validation)", () => {
  it("watchlist", () => {
    expect(parseWatchlist({ action: "add", type: "movie", title: " Alien ", year: 1979 })).toEqual({ action: "add", type: "movie", title: "Alien", year: 1979, tmdb: null });
    expect(parseWatchlist({ action: "remove", type: "show", title: "Lost", tmdb: 4607 })).toMatchObject({ type: "show", tmdb: 4607 });
    for (const bad of [null, {}, { action: "toggle", type: "movie", title: "a" }, { action: "add", type: "episode", title: "a" }, { action: "add", type: "movie", title: "" }]) {
      expect(parseWatchlist(bad)).toBeNull();
    }
  });
  it("historique : film, épisodes dédoublonnés, bornes", () => {
    expect(parseHistory({ action: "add", type: "movie", title: "Dune", year: 2021 })).toMatchObject({ type: "movie", title: "Dune" });
    const h = parseHistory({ action: "remove", type: "episodes", title: "Lost", episodes: [{ season: 1, episode: 2 }, { season: 1, episode: 2 }, { season: 2, episode: 1 }] });
    expect(h.episodes).toEqual([{ season: 1, episode: 2 }, { season: 2, episode: 1 }]);
    expect(parseHistory({ action: "add", type: "episodes", title: "Lost", episodes: [] })).toBeNull();
    expect(parseHistory({ action: "add", type: "episodes", title: "Lost", episodes: [{ season: 1 }] })).toBeNull();
    const many = Array.from({ length: MAX_HISTORY_EPISODES + 1 }, (_, i) => ({ season: 1, episode: i + 1 }));
    expect(parseHistory({ action: "add", type: "episodes", title: "Lost", episodes: many })).toBeNull();
  });
  it("note : 0..10 entier", () => {
    expect(parseRating({ type: "movie", title: "Alien", rating: 9 })).toMatchObject({ rating: 9 });
    expect(parseRating({ type: "show", title: "Lost", rating: 0 })).toMatchObject({ rating: 0 });
    for (const r of [11, -1, 7.5, "8", null]) expect(parseRating({ type: "movie", title: "Alien", rating: r })).toBeNull();
  });
  it("requêtes Trakt", () => {
    const now = "2026-10-20T10:00:00.000Z";
    expect(traktWriteRequest("watchlist", { action: "add", type: "movie" }, 348, now)).toEqual({ path: "/sync/watchlist", body: { movies: [{ ids: { tmdb: 348 } }] } });
    expect(traktWriteRequest("watchlist", { action: "remove", type: "show" }, 4607, now)).toEqual({ path: "/sync/watchlist/remove", body: { shows: [{ ids: { tmdb: 4607 } }] } });
    expect(traktWriteRequest("history", { action: "add", type: "movie" }, 348, now)).toEqual({ path: "/sync/history", body: { movies: [{ ids: { tmdb: 348 }, watched_at: now }] } });
    expect(traktWriteRequest("history", { action: "remove", type: "episodes", episodes: [{ season: 2, episode: 1 }, { season: 1, episode: 3 }, { season: 1, episode: 4 }] }, 4607, now)).toEqual({
      path: "/sync/history/remove",
      body: { shows: [{ ids: { tmdb: 4607 }, seasons: [{ number: 1, episodes: [{ number: 3 }, { number: 4 }] }, { number: 2, episodes: [{ number: 1 }] }] }] },
    });
    expect(traktWriteRequest("rating", { type: "movie", rating: 8 }, 348, now)).toEqual({ path: "/sync/ratings", body: { movies: [{ ids: { tmdb: 348 }, rating: 8, rated_at: now }] } });
    expect(traktWriteRequest("rating", { type: "show", rating: 0 }, 4607, now)).toEqual({ path: "/sync/ratings/remove", body: { shows: [{ ids: { tmdb: 4607 } }] } });
  });
  it("réponses /sync traduites", async () => {
    const s = (status, body) => sendSync("C", "A", "/sync/history", {}, async () => res(status, body));
    expect(await s(201, { added: { movies: 1 }, not_found: { movies: [] } })).toBe("ok");
    expect(await s(201, { added: { movies: 0 }, not_found: { movies: [{ ids: { tmdb: 1 } }] } })).toBe("unmatched");
    expect(await s(200, { deleted: { movies: 0 }, not_found: { movies: [] } })).toBe("ok");
    expect(await s(401, {})).toBe("unauthorized");
    expect(await s(500, {})).toBe("upstream");
    expect(await sendSync("C", "A", "/x", {}, async () => { throw new Error(); })).toBe("upstream");
  });
});

describe("bibliothèque : notes, listes, reprises, dernière vue", () => {
  const data = {
    "/sync/watched/shows": [{ show: { title: "Lost", year: 2004, ids: { tmdb: 4607 } }, last_watched_at: "2026-10-01T20:00:00.000Z", seasons: [{ number: 1, episodes: [{ number: 1 }] }] }],
    "/sync/ratings/movies": [{ rating: 9, movie: { title: "Alien", year: 1979, ids: { tmdb: 348 } } }, { rating: 0, movie: { title: "Bad", ids: {} } }],
    "/sync/ratings/shows": [{ rating: 7, show: { title: "Lost", year: 2004, ids: { tmdb: 4607 } } }],
    "/sync/playback": [
      { type: "movie", progress: 42.123, paused_at: "2026-10-02T10:00:00.000Z", movie: { title: "Dune", year: 2021, runtime: 155, ids: { tmdb: 438631 } } },
      { type: "episode", progress: 10, paused_at: "2026-10-03T10:00:00.000Z", episode: { season: 2, number: 3, runtime: 44 }, show: { title: "Lost", year: 2004, ids: { tmdb: 4607 } } },
      { type: "movie", progress: 100, paused_at: "2026-10-01T10:00:00.000Z", movie: { title: "Fin", ids: {} } },
    ],
    "/users/me/lists": Array.from({ length: MAX_LISTS + 2 }, (_, i) => ({ name: `Liste ${i}`, ids: { trakt: 100 + i, slug: `l${i}` } })),
    "/users/me/lists/100/items/movie,show": [{ type: "movie", movie: { title: "Heat", year: 1995, ids: { tmdb: 949 } } }, { type: "person", person: { name: "x" } }, { type: "show", show: { title: "Dark", year: 2017, ids: { tmdb: 70523 } } }],
  };
  const fetchFn = vi.fn(async (u) => { const p = decodeURIComponent(new URL(u).pathname); return res(200, data[p] ?? []); });

  it("lues et normalisées, listes bornées, transmises à l'appareil avec leurs clés", async () => {
    const lib = await fetchTraktLibrary("C", "T", fetchFn);
    expect(lib.watched.shows[0].lastWatchedAt).toBe(Date.parse("2026-10-01T20:00:00.000Z"));
    expect(lib.ratings.movies).toEqual([{ type: "movie", tmdb: 348, year: 1979, title: "Alien", rating: 9 }]);
    expect(lib.ratings.shows[0]).toMatchObject({ title: "Lost", rating: 7 });
    expect(lib.playback.map((x) => x.title)).toEqual(["Lost", "Dune"]);
    expect(lib.playback[0]).toMatchObject({ type: "show", season: 2, episode: 3, runtime: 44, progress: 10 });
    expect(lib.playback[1]).toMatchObject({ type: "movie", progress: 42.12, runtime: 155 });
    expect(lib.lists).toHaveLength(MAX_LISTS);
    expect(lib.lists[0]).toEqual({ id: "100", name: "Liste 0", items: [{ type: "movie", tmdb: 949, year: 1995, title: "Heat" }, { type: "show", tmdb: 70523, year: 2017, title: "Dark" }] });
    const listCalls = fetchFn.mock.calls.filter(([u]) => String(u).includes("/users/me/lists/"));
    expect(listCalls).toHaveLength(MAX_LISTS);
    const out = libraryForDevice(lib, {}, 1);
    expect(out.ratings.movies[0]).toEqual({ type: "movie", tmdb: 348, year: 1979, title: "Alien", keys: ["alien"], rating: 9 });
    expect(out.lists[0].items[0].keys).toEqual(["heat"]);
    expect(out.playback[0]).toMatchObject({ type: "show", keys: ["lost"], season: 2, episode: 3, pausedAt: Date.parse("2026-10-03T10:00:00.000Z") });
    expect(out.playback[1].season).toBeUndefined();
    expect(out.watched.shows[0].lastWatchedAt).toBeGreaterThan(0);
  });
  it("budget TMDB partagé : reprises et listes passent avant les recommandations", async () => {
    const lib = await fetchTraktLibrary("C", "T", fetchFn);
    lib.recommendations = Array.from({ length: 40 }, (_, i) => ({ type: "movie", tmdb: 5000 + i, year: 2000, title: `R${i}` }));
    const tmdb = vi.fn(async () => res(200, { title: "x" }));
    const dict = await enrichTitles({ TMDB_READ_TOKEN: "T" }, lib, {}, "fr", tmdb);
    expect(dict.m438631).toBeDefined();
    expect(dict.m949).toBeDefined();
    expect(dict.s70523).toBeDefined();
  });
  it("une liste en échec reste vide ; accès refusé remonté", async () => {
    const f = async (u) => { const p = decodeURIComponent(new URL(u).pathname); if (p.startsWith("/users/me/lists/")) return res(500, {}); return res(200, data[p] ?? []); };
    const lib = await fetchTraktLibrary("C", "T", f);
    expect(lib.lists[0].items).toEqual([]);
    const g = async (u) => { const p = decodeURIComponent(new URL(u).pathname); if (p.startsWith("/users/me/lists/")) return res(401, {}); return res(200, data[p] ?? []); };
    await expect(fetchTraktLibrary("C", "T", g)).rejects.toMatchObject({ unauthorized: true });
  });
});

describe("trakt : routes d'écriture", () => {
  afterEach(() => vi.unstubAllGlobals());

  /** Compte relié à Trakt (jetons chiffrés comme au retour OAuth) + appareil appairé. */
  async function linked() {
    const acct = await newAccount();
    const dev = await pairDevice(acct, "Salon");
    const a = await getAccount(env, acct.login);
    a.traktEnc = await encryptJson(keysFromEnv(env), { access: "ACC", refresh: "REF", expiresAt: Date.now() + 30 * 86400_000 }, a.login);
    a.trakt = { user: "moi", at: Date.now() };
    await putAccount(env, a);
    return { acct, dev };
  }
  const post = (dev, path, json, headers = bearer(dev.token)) => call(path, { method: "POST", ip: freshIp(), headers, json });

  it("compte non relié → linked:false ; corps invalide → 400 ; sans jeton → 401", async () => {
    const acct = await newAccount();
    const dev = await pairDevice(acct, "Salon");
    for (const [path, body] of [
      ["/api/device/trakt/watchlist", { action: "add", type: "movie", title: "Alien", tmdb: 348 }],
      ["/api/device/trakt/history", { action: "add", type: "movie", title: "Alien", tmdb: 348 }],
      ["/api/device/trakt/rating", { type: "movie", title: "Alien", tmdb: 348, rating: 8 }],
    ]) {
      const r = await post(dev, path, body);
      expect(r.status).toBe(200);
      expect(await r.json()).toEqual({ linked: false });
      expect((await post(dev, path, { action: "nope" })).status).toBe(400);
      expect((await post(dev, path, body, {})).status).toBe(401);
    }
  });

  it("watchlist : écrit chez Trakt avec le jeton du compte et oublie la bibliothèque en cache", async () => {
    const { acct, dev } = await linked();
    await env.CONFIG.put(`tlib:${acct.login}:fr`, "x");
    await env.CONFIG.put(`tdict:${acct.login}:fr`, "{}");
    const calls = [];
    vi.stubGlobal("fetch", vi.fn(async (u, init) => {
      calls.push({ url: String(u), init });
      return res(201, { added: { movies: 1, shows: 0 }, existing: {}, not_found: { movies: [] } });
    }));
    const r = await post(dev, "/api/device/trakt/watchlist", { action: "add", type: "movie", title: "Alien", year: 1979, tmdb: 348 });
    expect(await r.json()).toEqual({ linked: true, matched: true, ok: true, tmdb: 348 });
    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("https://api.trakt.tv/sync/watchlist");
    expect(calls[0].init.headers.authorization).toBe("Bearer ACC");
    expect(JSON.parse(calls[0].init.body)).toEqual({ movies: [{ ids: { tmdb: 348 } }] });
    expect(await env.CONFIG.get(`tlib:${acct.login}:fr`)).toBeNull();
    // Le dictionnaire des titres (coûteux) survit : seule la bibliothèque est périmée.
    expect(await env.CONFIG.get(`tdict:${acct.login}:fr`)).toBe("{}");
  });

  it("historique : épisodes regroupés par saison ; non reconnu → matched:false, cache gardé", async () => {
    const { acct, dev } = await linked();
    await env.CONFIG.put(`tlib:${acct.login}:en`, "x");
    const bodies = [];
    vi.stubGlobal("fetch", vi.fn(async (u, init) => {
      bodies.push([String(u), JSON.parse(init.body)]);
      return res(201, { added: { episodes: 0 }, not_found: { shows: [{ ids: { tmdb: 4607 } }] } });
    }));
    const r = await post(dev, "/api/device/trakt/history", { action: "add", type: "episodes", title: "Lost", tmdb: 4607, episodes: [{ season: 1, episode: 1 }, { season: 1, episode: 2 }] });
    expect(await r.json()).toEqual({ linked: true, matched: false, ok: false, tmdb: 4607 });
    expect(bodies[0][0]).toBe("https://api.trakt.tv/sync/history");
    expect(bodies[0][1].shows[0].seasons).toEqual([{ number: 1, episodes: [{ number: 1, watched_at: expect.any(String) }, { number: 2, watched_at: expect.any(String) }] }]);
    expect(await env.CONFIG.get(`tlib:${acct.login}:en`)).toBe("x");
  });

  it("note : jeton refusé → renouvelé une fois puis réécrit", async () => {
    const { dev } = await linked();
    const seen = [];
    vi.stubGlobal("fetch", vi.fn(async (u, init) => {
      const url = String(u);
      seen.push(url);
      if (url.endsWith("/oauth/token")) return res(200, { access_token: "NEW", refresh_token: "R2", expires_in: 7776000 });
      if (init.headers.authorization === "Bearer ACC") return res(401, {});
      return res(201, { added: { movies: 1 }, not_found: { movies: [] } });
    }));
    const r = await post(dev, "/api/device/trakt/rating", { type: "movie", title: "Alien", tmdb: 348, rating: 0 });
    expect(await r.json()).toMatchObject({ linked: true, ok: true });
    expect(seen).toEqual(["https://api.trakt.tv/sync/ratings/remove", "https://api.trakt.tv/oauth/token", "https://api.trakt.tv/sync/ratings/remove"]);
  });

  it("panne de Trakt → 502 ; débit limité par appareil", async () => {
    const { dev } = await linked();
    vi.stubGlobal("fetch", vi.fn(async () => res(503, {})));
    const r = await post(dev, "/api/device/trakt/watchlist", { action: "remove", type: "show", title: "Lost", tmdb: 4607 });
    expect(r.status).toBe(502);
    let last;
    for (let i = 0; i < 61; i++) last = await post(dev, "/api/device/trakt/watchlist", { action: "nope" });
    expect(last.status).toBe(429);
  });
});
