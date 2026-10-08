import { describe, expect, it, vi } from "vitest";
import { matchKey, titleMatches, fetchTraktLibrary, enrichTitles, libraryForDevice, TMDB_PER_REFRESH } from "../src/traktlib.js";
import vectors from "./fixtures/trakt-match-vectors.json";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";

const res = (status, body) => new Response(JSON.stringify(body), { status });

describe("matchKey (vecteurs partagés avec les applications)", () => {
  it.each(vectors.keys)("%s → %s", (raw, key) => expect(matchKey(raw)).toBe(key));
  it.each(vectors.matches.map((m) => [m.catalog, m.catalogYear, m]))("rapprochement %s (%s)", (_c, _y, m) =>
    expect(titleMatches(m.catalog, m.catalogYear, m.titles, m.year)).toBe(m.match));
});

describe("bibliothèque Trakt", () => {
  const trakt = {
    "/sync/watchlist/movies": [{ movie: { title: "Alien", year: 1979, ids: { tmdb: 348 } } }],
    "/sync/watchlist/shows": [{ show: { title: "Money Heist", year: 2017, ids: { tmdb: 71446 } } }],
    "/recommendations/movies": [{ title: "Aliens", year: 1986, ids: { tmdb: 679 } }],
    "/recommendations/shows": [],
    "/sync/watched/movies": [{ movie: { title: "Dune", year: 2021, ids: { tmdb: 438631 } } }],
    "/sync/watched/shows": [{ show: { title: "Lost", year: 2004, ids: { tmdb: 4607 } }, seasons: [{ number: 1, episodes: [{ number: 1 }, { number: 2 }] }] }],
  };
  const fetchTrakt = vi.fn(async (u) => { const p = new URL(u).pathname; return res(200, trakt[p] ?? []); });

  it("six listes lues et normalisées", async () => {
    const lib = await fetchTraktLibrary("CID", "TOK", fetchTrakt);
    expect(lib.watchlist).toEqual([{ type: "movie", tmdb: 348, year: 1979, title: "Alien" }, { type: "show", tmdb: 71446, year: 2017, title: "Money Heist" }]);
    expect(lib.recommendations[0]).toMatchObject({ type: "movie", tmdb: 679 });
    expect(lib.watched.shows[0].episodes).toEqual(["1x1", "1x2"]);
    expect(fetchTrakt.mock.calls[0][1].headers.authorization).toBe("Bearer TOK");
  });
  it("accès refusé : remonté ; autre échec : liste vide", async () => {
    await expect(fetchTraktLibrary("C", "T", async () => res(401, {}))).rejects.toMatchObject({ unauthorized: true });
    const lib = await fetchTraktLibrary("C", "T", async (u) => (u.includes("watchlist/movies") ? res(500, {}) : res(200, [])));
    expect(lib.watchlist).toEqual([]);
  });
  it("titres localisés TMDB : bornés par rafraîchissement, priorité à la watchlist, clés pour l'appareil", async () => {
    const lib = await fetchTraktLibrary("CID", "TOK", fetchTrakt);
    const tmdb = vi.fn(async (u) => res(200, u.includes("/tv/71446") ? { name: "La casa de papel", original_name: "La casa de papel" } : { title: "Titre FR", original_title: "Original" }));
    const dict = await enrichTitles({ TMDB_READ_TOKEN: "T" }, lib, {}, "fr", tmdb);
    expect(dict.s71446).toEqual(["La casa de papel", "La casa de papel"]);
    expect(String(tmdb.mock.calls[0][0])).toContain("language=fr");
    const out = libraryForDevice(lib, dict, 1);
    expect(out.watchlist[1].keys).toEqual(["money heist", "casa de papel"]);
    expect(out.watched.shows[0].episodes).toEqual(["1x1", "1x2"]);
    // Déjà connus : aucun nouvel appel.
    const again = vi.fn();
    await enrichTitles({ TMDB_READ_TOKEN: "T" }, lib, dict, "fr", again);
    expect(again).not.toHaveBeenCalled();
  });
  it("au plus TMDB_PER_REFRESH appels TMDB par rafraîchissement", async () => {
    const many = { watchlist: Array.from({ length: 80 }, (_, i) => ({ type: "movie", tmdb: i + 1, year: 2000, title: `T${i}` })), recommendations: [], watched: { movies: [], shows: [] } };
    const tmdb = vi.fn(async () => res(200, { title: "x" }));
    const dict = await enrichTitles({ TMDB_READ_TOKEN: "T" }, many, {}, "fr", tmdb);
    expect(tmdb).toHaveBeenCalledTimes(TMDB_PER_REFRESH);
    expect(Object.keys(dict)).toHaveLength(TMDB_PER_REFRESH);
  });
  it("route appareil : compte non relié → linked:false ; sans jeton → 401", async () => {
    const acct = await newAccount();
    const dev = await pairDevice(acct, "Salon");
    const r = await call("/api/device/trakt/library?lang=fr", { ip: freshIp(), headers: bearer(dev.token) });
    expect(await r.json()).toEqual({ linked: false });
    expect((await call("/api/device/trakt/library", { ip: freshIp() })).status).toBe(401);
  });
});
