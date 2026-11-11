import { describe, it, expect, vi } from "vitest";
import { ratingsTarget, parseOmdb, ratingsProxy } from "../src/ratings.js";
import { call, freshIp } from "./helpers.js";

const sp = (s) => new URL("https://x.test/?" + s).searchParams;
const memCache = () => { const m = new Map(); return { match: async (k) => m.get(k.url)?.clone(), put: async (k, r) => { m.set(k.url, r); } }; };
const OMDB = { Response: "True", imdbRating: "8.8", imdbVotes: "2,512,331", Metascore: "74",
  Ratings: [{ Source: "Internet Movie Database", Value: "8.8/10" }, { Source: "Rotten Tomatoes", Value: "87%" }, { Source: "Metacritic", Value: "74/100" }] };

describe("ratingsTarget", () => {
  it("accepte tmdb ou titre + année", () => {
    expect(ratingsTarget(sp("kind=movie&tmdb=27205"))).toEqual({ kind: "movie", tmdb: 27205, title: "", year: null });
    expect(ratingsTarget(sp("kind=tv&title=Dark&year=2017"))).toEqual({ kind: "tv", tmdb: null, title: "Dark", year: 2017 });
  });
  it.each(["kind=person&tmdb=1", "kind=movie", "kind=movie&tmdb=0", "kind=movie&tmdb=abc", "kind=movie&title=x&year=12"])("refuse %s", (q) =>
    expect(ratingsTarget(sp(q))).toBeNull());
});

describe("parseOmdb", () => {
  it("lit IMDb, votes, Rotten Tomatoes et Metacritic", () =>
    expect(parseOmdb(OMDB)).toEqual({ imdb: 8.8, imdbVotes: 2512331, rt: 87, mc: 74 }));
  it("N/A ou introuvable : null", () => {
    expect(parseOmdb({ Response: "False", Error: "Incorrect IMDb ID." })).toBeNull();
    expect(parseOmdb({ Response: "True", imdbRating: "N/A", Ratings: [] })).toBeNull();
  });
});

describe("ratingsProxy", () => {
  const env = { OMDB_API_KEY: "k", TMDB_READ_TOKEN: "t" };
  it("tmdb → external_ids → OMDb, puis cache", async () => {
    const fetchFn = vi.fn(async (u) => new Response(JSON.stringify(u.includes("themoviedb") ? { imdb_id: "tt1375666" } : OMDB), { status: 200 }));
    const cache = memCache();
    const r = await ratingsProxy(sp("kind=movie&tmdb=27205"), env, { fetchFn, cache });
    expect(await r.json()).toEqual({ ratings: { imdb: 8.8, imdbVotes: 2512331, rt: 87, mc: 74, imdbId: "tt1375666" } });
    expect(fetchFn.mock.calls[1][0]).toContain("i=tt1375666");
    await ratingsProxy(sp("kind=movie&tmdb=27205"), env, { fetchFn, cache });
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });
  it("sans clé OMDb : 503", async () =>
    expect((await ratingsProxy(sp("kind=movie&tmdb=1"), { TMDB_READ_TOKEN: "t" }, { fetchFn: vi.fn(), cache: memCache() })).status).toBe(503));
  it("quota OMDb dépassé : 502, rien en cache", async () => {
    const fetchFn = vi.fn(async (u) => new Response(JSON.stringify(u.includes("themoviedb") ? { imdb_id: "tt1375666" } : { Response: "False", Error: "Request limit reached!" }), { status: 200 }));
    const cache = memCache();
    expect((await ratingsProxy(sp("kind=movie&tmdb=27205"), env, { fetchFn, cache })).status).toBe(502);
    expect(await cache.match(new Request("https://ratings-cache.invalid/movie?tmdb=27205&title=&year="))).toBeUndefined();
  });
});

describe("/api/ratings : authentification", () => {
  it("sans jeton : 401", async () => expect((await call("/api/ratings?kind=movie&tmdb=1", { ip: freshIp() })).status).toBe(401));
});
