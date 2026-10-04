import { describe, it, expect, vi } from "vitest";
import { tmdbTarget, tmdbProxy, cacheKey } from "../src/tmdb.js";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";

const sp = (s) => new URL("https://x.test/?" + s).searchParams;

describe("tmdbTarget : liste blanche", () => {
  it.each([
    ["search/movie", "query=Alien&year=1979"],
    ["search/tv", "query=Dark&first_air_date_year=2017&language=fr-FR"],
    ["movie/603", "append_to_response=videos,credits&language=fr-FR"],
    ["tv/1396", ""],
    ["movie/603/images", "include_image_language=fr,en,null"],
    ["tv/1396/videos", ""],
    ["movie/603/credits", ""],
  ])("chemin_%s_autorise", (p, q) => expect(tmdbTarget(p, sp(q))).not.toBeNull());

  it.each([
    "account", "authentication/token/new", "movie/popular", "movie/abc", "movie/603/reviews", "movie/603/../account",
    "search/person", "search/multi", "movie/0", "movie/12345678901", "tv/1/season/1", "../3/account", "movie/603/",
  ])("chemin_%s_refuse", (p) => expect(tmdbTarget(p, sp(""))).toBeNull());

  it("recherche_sansQuery_ouVide_refusee", () => {
    expect(tmdbTarget("search/movie", sp(""))).toBeNull();
    expect(tmdbTarget("search/movie", sp("query=%20"))).toBeNull();
  });
  it("query_horsRecherche_refusee", () => expect(tmdbTarget("movie/603", sp("query=x"))).toBeNull());
  it("parametresInconnus_ignores_dontApiKey", () => {
    const t = tmdbTarget("movie/603", sp("api_key=EVIL&session_id=1&language=fr-FR"));
    expect([...t.params.keys()]).toEqual(["language"]);
  });
  it("append_valeurInterdite_refusee", () => expect(tmdbTarget("movie/603", sp("append_to_response=account_states"))).toBeNull());
  it("langue_ou_annee_invalide_refusee", () => {
    expect(tmdbTarget("movie/603", sp("language=fr%0d%0a"))).toBeNull();
    expect(tmdbTarget("search/movie", sp("query=a&year=abcd"))).toBeNull();
  });
  it("parametres_tries_cleDeCacheStable", () => {
    const a = cacheKey(tmdbTarget("search/movie", sp("year=1979&query=Alien"))).url;
    const b = cacheKey(tmdbTarget("search/movie", sp("query=Alien&year=1979"))).url;
    expect(a).toBe(b);
    expect(a).not.toContain("api_key");
  });
});

describe("tmdbProxy", () => {
  const mkCache = () => { const m = new Map(); return { match: async (k) => m.get(k.url)?.clone(), put: async (k, r) => { m.set(k.url, r); } }; };

  it("cleSecrete_envoyeeEnAmont_jamaisDansLaReponse", async () => {
    const fetchFn = vi.fn(async () => new Response('{"id":603}', { status: 200 }));
    const r = await tmdbProxy("movie/603", sp(""), { TMDB_API_KEY: "SECRET" }, { fetchFn, cache: mkCache() });
    expect(r.status).toBe(200);
    expect(fetchFn.mock.calls[0][0]).toContain("api_key=SECRET");
    expect(fetchFn.mock.calls[0][1].redirect).toBe("manual");
    expect(JSON.stringify([...r.headers])).not.toContain("SECRET");
  });
  it("deuxiemeAppel_serviDuCache_sansAppelAmont", async () => {
    const fetchFn = vi.fn(async () => new Response('{"id":603}', { status: 200 }));
    const cache = mkCache();
    await tmdbProxy("movie/603", sp("language=fr-FR"), { TMDB_API_KEY: "k" }, { fetchFn, cache });
    const r2 = await tmdbProxy("movie/603", sp("language=fr-FR"), { TMDB_API_KEY: "k" }, { fetchFn, cache });
    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(await r2.json()).toEqual({ id: 603 });
  });
  it("erreurAmont_pasMiseEnCache_etMessageGenerique", async () => {
    const fetchFn = vi.fn(async () => new Response('{"status_message":"Invalid API key: api_key=k"}', { status: 401 }));
    const cache = mkCache();
    const r = await tmdbProxy("movie/603", sp(""), { TMDB_API_KEY: "k" }, { fetchFn, cache });
    expect(r.status).toBe(502);
    expect(await r.text()).not.toContain("api_key");
    await tmdbProxy("movie/603", sp(""), { TMDB_API_KEY: "k" }, { fetchFn, cache });
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });
  it("sansSecret_503", async () => {
    expect((await tmdbProxy("movie/603", sp(""), {}, { fetchFn: vi.fn(), cache: mkCache() })).status).toBe(503);
  });
  it("cheminHorsListe_404_sansAppelAmont", async () => {
    const fetchFn = vi.fn();
    expect((await tmdbProxy("account", sp(""), { TMDB_API_KEY: "k" }, { fetchFn })).status).toBe(404);
    expect(fetchFn).not.toHaveBeenCalled();
  });
});

describe("/api/tmdb/* : authentification et débit", () => {
  it("sansJeton_401", async () => {
    expect((await call("/api/tmdb/movie/603", { ip: freshIp() })).status).toBe(401);
  });
  it("jetonFaux_401", async () => {
    expect((await call("/api/tmdb/movie/603", { ip: freshIp(), headers: bearer("utv_faux") })).status).toBe(401);
  });
  it("appareilAppaire_cheminHorsListe_404", async () => {
    const acct = await newAccount();
    const dev = await pairDevice(acct);
    const r = await call("/api/tmdb/account", { ip: dev.ip, headers: bearer(dev.token) });
    expect(r.status).toBe(404);
  });
  it("appareilAppaire_limiteDeDebit_429", async () => {
    const acct = await newAccount();
    const dev = await pairDevice(acct);
    let last;
    for (let i = 0; i < 125; i++) last = await call("/api/tmdb/account", { ip: dev.ip, headers: bearer(dev.token) });
    expect(last.status).toBe(429);
  });
});
