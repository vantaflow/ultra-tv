import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/net/secrets", () => ({ encryptSecret: async (s: string) => s, decryptSecret: async (s: string) => s }));
vi.mock("@/cloud/service", async () => {
  const { create } = await import("zustand");
  return { getToken: async () => "tok", useCloud: create(() => ({ paired: true, worker: "https://w.example" })) };
});

import { installMemStorage } from "./memstorage";
installMemStorage();
import { normalizeTraktLibrary, setCloudHttp, traktWrite, type TraktPlayback } from "@/cloud/client";
import { db } from "@/db/db";
import type { Source } from "@/db/types";
import { Scrobbler } from "@/player/trakt";
import { rate, setEpisodesWatched, setWatchlist } from "./actions";
import { addToIndex } from "./availability";
import type { CatalogIndex } from "./catalog";
import { computeUpNext, importPlayback, IMPORTED_KEY } from "./hooks";
import { useTraktLibrary } from "./library";
import { loadTraktPrefs, MAX_LIST_ROWS, TRAKT_PREFS_KEY, useTraktPrefs, visibleLists } from "./prefs";
import { inWatchlist, nextEpisode, ratingFor, resumeDecision, upNextCandidates, withEpisodes, withMovieWatched, withRating, withWatchlist, type Linked } from "./progress";
import { resetEpisodeRequests } from "./episodes";

const RAW = {
  linked: true, updatedAt: 5,
  watchlist: [{ type: "show", tmdb: 4607, year: 2004, title: "Lost", keys: ["lost"] }],
  recommendations: [], trending: [], popular: [],
  watched: {
    movies: [{ type: "movie", tmdb: null, year: 1979, title: "Alien", keys: ["alien"] }],
    shows: [
      { type: "show", tmdb: null, year: 2004, title: "Lost", keys: ["lost"], episodes: ["1x1", "1x2"], lastWatchedAt: 200 },
      { type: "show", tmdb: null, year: 2017, title: "Dark", keys: ["dark"], episodes: ["1x1"], lastWatchedAt: 900 },
    ],
  },
  ratings: { movies: [{ type: "movie", tmdb: 348, year: 1979, title: "Alien", keys: ["alien"], rating: 9 }], shows: [{ type: "show", title: "Lost", keys: ["lost"], rating: 0 }] },
  lists: [{ id: "100", name: "Soirée", items: [{ type: "movie", tmdb: 949, year: 1995, title: "Heat", keys: ["heat"] }] }, { id: "", name: "x", items: [] }],
  playback: [
    { type: "movie", tmdb: 438631, year: 2021, title: "Dune", keys: ["dune"], progress: 50, pausedAt: 1000, runtime: 100 },
    { type: "show", tmdb: 4607, year: 2004, title: "Lost", keys: ["lost"], progress: 25, pausedAt: 2000, runtime: null, season: 1, episode: 3 },
    { type: "show", title: "Bad", keys: ["bad"], progress: 10, pausedAt: 1 },
  ],
};
const lib = (): Linked => normalizeTraktLibrary(RAW as unknown as Record<string, unknown>);

describe("bibliothèque étendue", () => {
  it("normalisée : notes valides, listes nommées, reprises complètes", () => {
    const l = lib();
    expect(l.ratings.movies.map((r) => r.rating)).toEqual([9]);
    expect(l.ratings.shows).toEqual([]);
    expect(l.lists.map((x) => x.id)).toEqual(["100"]);
    expect(l.playback.map((p) => p.title)).toEqual(["Dune", "Lost"]);
    expect(l.watched.shows[1]!.lastWatchedAt).toBe(900);
    // Ancien format (sans notes / listes / reprises) : champs vides.
    const old = normalizeTraktLibrary({ linked: true, watchlist: [], recommendations: [], watched: { movies: [], shows: [] } });
    expect(old.lists).toEqual([]);
    expect(old.ratings).toEqual({ movies: [], shows: [] });
  });
  it("watchlist, note et corrections locales", () => {
    const l = lib();
    expect(inWatchlist(l, "show", "|FR| Lost", 2004)).toBe(true);
    expect(inWatchlist(l, "movie", "Lost", 2004)).toBe(false);
    expect(ratingFor(l, "movie", "FR - Alien (1979) 4K", 1979)).toBe(9);
    expect(inWatchlist(withWatchlist(l, "movie", "Heat", 1995, true), "movie", "Heat", 1995)).toBe(true);
    expect(inWatchlist(withWatchlist(l, "show", "Lost", 2004, false), "show", "Lost", 2004)).toBe(false);
    expect(withMovieWatched(l, "Alien", 1979, false).watched.movies).toEqual([]);
    expect(ratingFor(withRating(l, "movie", "Alien", 1979, 0), "movie", "Alien", 1979)).toBeNull();
    expect(ratingFor(withRating(l, "show", "Lost", 2004, 6), "show", "Lost", 2004)).toBe(6);
    const e = withEpisodes(l, "Lost", 2004, ["1x3"], true, 5000);
    expect(e.watched.shows[0]).toMatchObject({ episodes: ["1x1", "1x2", "1x3"], lastWatchedAt: 5000 });
    expect(withEpisodes(e, "Lost", 2004, ["1x1", "1x2", "1x3"], false).watched.shows.map((s) => s.title)).toEqual(["Dark"]);
    expect(withEpisodes(l, "Fargo", 2014, ["2x1"], true).watched.shows.at(-1)).toMatchObject({ title: "Fargo", episodes: ["2x1"] });
  });
});

describe("À suivre", () => {
  const r = (...k: string[]) => k.map((x) => { const [s, e] = x.split("x").map(Number); return { season: s!, episode: e! }; });
  it("suivant disponible après le plus avancé", () => {
    expect(nextEpisode(["1x1", "1x2"], r("1x1", "1x2", "1x3", "2x1"))).toEqual({ season: 1, episode: 3 });
    expect(nextEpisode(["1x1", "1x2"], r("2x1", "1x2"))).toEqual({ season: 2, episode: 1 });
    expect(nextEpisode(["1x1", "3x1"], r("1x2", "3x2"))).toEqual({ season: 3, episode: 2 });
    expect(nextEpisode(["0x5", "1x1"], r("0x6", "1x2"))).toEqual({ season: 1, episode: 2 });
  });
  it("à jour, rien vu ou suite absente : rien", () => {
    expect(nextEpisode(["1x1", "1x2"], r("1x1", "1x2"))).toBeNull();
    expect(nextEpisode([], r("1x1"))).toBeNull();
    expect(nextEpisode(["1x1"], [])).toBeNull();
  });
  it("candidats : plus récemment vus d'abord", () => {
    expect(upNextCandidates(lib()).map((s) => s.title)).toEqual(["Dark", "Lost"]);
    expect(upNextCandidates(null)).toEqual([]);
  });
});

describe("reprises : décision d'import", () => {
  it("sans historique : importée à l'heure de la pause", () => {
    expect(resumeDecision(undefined, 50, 1000, 6000, 5000)).toEqual({ pos: 3000, dur: 6000, updatedAt: 1000 });
  });
  it("historique local plus récent : jamais écrasé ; plus ancien : remplacé avec sa durée", () => {
    expect(resumeDecision({ updatedAt: 1000, dur: 0 }, 50, 1000, 6000)).toBeNull();
    expect(resumeDecision({ updatedAt: 500, dur: 4000 }, 25, 1000, 6000, 5000)).toEqual({ pos: 1000, dur: 4000, updatedAt: 1000 });
  });
  it("durée inconnue, trop tôt ou presque fini : ignorée", () => {
    expect(resumeDecision(undefined, 50, 1000, 0)).toBeNull();
    expect(resumeDecision(undefined, 0.1, 1000, 6000)).toBeNull();
    expect(resumeDecision(undefined, 99, 1000, 6000)).toBeNull();
  });
});

describe("réglages Trakt", () => {
  beforeEach(() => localStorage.clear());
  it("listes visibles : masquées retirées, au plus 5", () => {
    const lists = Array.from({ length: 7 }, (_, i) => ({ id: String(i) }));
    expect(visibleLists(lists, ["1"]).map((l) => l.id)).toEqual(["0", "2", "3", "4", "5"]);
    expect(visibleLists(lists, [])).toHaveLength(MAX_LIST_ROWS);
  });
  it("persistés, valeurs par défaut si illisibles", () => {
    useTraktPrefs.getState().setAutoWatched(false);
    useTraktPrefs.getState().setListVisible("100", false);
    expect(loadTraktPrefs()).toEqual({ autoWatched: false, hiddenLists: ["100"] });
    useTraktPrefs.getState().setListVisible("100", true);
    expect(loadTraktPrefs().hiddenLists).toEqual([]);
    localStorage.setItem(TRAKT_PREFS_KEY, "{bad");
    expect(loadTraktPrefs()).toEqual({ autoWatched: true, hiddenLists: [] });
    useTraktPrefs.getState().setAutoWatched(true);
  });
});

describe("écritures via le Worker", () => {
  let calls: { url: string; method?: string; body?: string }[] = [];
  let reply: { status: number; body: unknown } = { status: 200, body: { linked: true, matched: true, ok: true } };
  beforeEach(() => {
    calls = [];
    reply = { status: 200, body: { linked: true, matched: true, ok: true } };
    setCloudHttp(async (r) => {
      calls.push(r);
      const body = r.url.includes("/library") ? RAW : reply.body;
      return { status: r.url.includes("/library") ? 200 : reply.status, text: JSON.stringify(body), retryAfter: "", etag: "" };
    });
    useTraktLibrary.setState({ lib: lib(), loadedAt: Date.now(), key: "https://w.example|fr" });
  });

  it("réponses traduites", async () => {
    expect(await traktWrite("https://w", "t", "watchlist", {})).toBe("ok");
    reply = { status: 200, body: { linked: false } };
    expect(await traktWrite("https://w", "t", "watchlist", {})).toBe("not-linked");
    reply = { status: 200, body: { linked: true, matched: false, ok: false } };
    expect(await traktWrite("https://w", "t", "history", {})).toBe("unmatched");
    reply = { status: 502, body: { linked: true } };
    expect(await traktWrite("https://w", "t", "rating", {})).toBe("failed");
  });
  it("succès : corrige tout de suite puis relit la bibliothèque (forcé)", async () => {
    expect(await setWatchlist({ type: "movie", title: "Heat", year: 1995, tmdb: 949 }, true)).toBe("ok");
    expect(calls[0]!.url).toBe("https://w.example/api/device/trakt/watchlist");
    expect(JSON.parse(calls[0]!.body!)).toEqual({ title: "Heat", year: 1995, tmdb: 949, action: "add", type: "movie" });
    expect(inWatchlist(useTraktLibrary.getState().lib, "movie", "Heat", 1995)).toBe(true);
    await vi.waitFor(() => expect(calls.some((c) => c.url.includes("/api/device/trakt/library"))).toBe(true));
  });
  it("échec : rien ne change ; épisodes dédoublonnés", async () => {
    reply = { status: 200, body: { linked: true, matched: false, ok: false } };
    expect(await rate({ type: "movie", title: "Alien", year: 1979 }, 3)).toBe("unmatched");
    expect(ratingFor(useTraktLibrary.getState().lib, "movie", "Alien", 1979)).toBe(9);
    reply = { status: 200, body: { linked: true, matched: true, ok: true } };
    calls = [];
    await setEpisodesWatched({ type: "show", title: "Lost", year: 2004 }, [{ season: 1, episode: 3 }, { season: 1, episode: 3 }], true);
    expect(JSON.parse(calls[0]!.body!)).toMatchObject({ type: "episodes", episodes: [{ season: 1, episode: 3 }] });
    calls = [];
    expect(await setEpisodesWatched({ type: "show", title: "Lost", year: 2004 }, [], true)).toBe("ok");
    expect(calls).toEqual([]);
  });
});

describe("catalogue : À suivre et import des reprises", () => {
  const source = { id: 3, cid: 30, type: "m3u" } as Source;
  const index = (): CatalogIndex => {
    const movies = new Map(); const series = new Map();
    addToIndex(movies, ["FR - Dune (2021)"], 11, 2021);
    addToIndex(series, ["Lost"], 21, 2004);
    addToIndex(series, ["Dark"], 22, 2017);
    return { movies, series };
  };
  const eps = (list: [number, number, number][]) => ({ episodes: Object.fromEntries([...new Set(list.map((x) => x[0]))].map((s) => [String(s), list.filter((x) => x[0] === s).map(([, n, id]) => ({ id, episode_num: n, container_extension: "mkv", info: { duration_secs: 2400 } }))])) });

  beforeEach(async () => {
    localStorage.removeItem(IMPORTED_KEY);
    resetEpisodeRequests();
    await Promise.all([db.movies.clear(), db.series.clear(), db.details.clear(), db.history.clear()]);
    await db.movies.add({ sourceId: 30, catExt: "1", ord: 1, streamId: 11, name: "FR - Dune (2021)", title: "Dune", norm: "dune", year: 2021, poster: "p", rating: 0, ext: "mkv", added: 1 });
    await db.series.bulkAdd([
      { sourceId: 30, catExt: "1", ord: 1, seriesId: 21, name: "Lost", title: "Lost", norm: "lost", year: 2004, poster: "pl", backdrop: null, plot: null, rating: 0, added: 1 },
      { sourceId: 30, catExt: "1", ord: 2, seriesId: 22, name: "Dark", title: "Dark", norm: "dark", year: 2017, poster: "pd", backdrop: null, plot: null, rating: 0, added: 1 },
    ]);
    await db.details.put({ key: "series:3:21", json: eps([[1, 1, 101], [1, 2, 102], [1, 3, 103]]), fetchedAt: 1 });
    await db.details.put({ key: "series:3:22", json: eps([[1, 1, 201]]), fetchedAt: 1 });
  });

  it("À suivre : seulement les séries dont la suite est dans la playlist", async () => {
    const up = await computeUpNext(source, lib(), index());
    expect(up.map((u) => [u.ref, u.season, u.episode])).toEqual([[21, 1, 3]]);
  });

  it("reprises : film et épisode importés une fois, position locale plus récente gardée", async () => {
    await db.history.put({ key: "main:3:movie:11", profile: "main", sourceId: 3, kind: "movie", refId: 11, title: "Dune", image: null, pos: 100, dur: 6000, updatedAt: 5000 });
    const n = await importPlayback(source, "main", lib().playback as TraktPlayback[], index(), { left: 0 }, 10_000);
    expect(n).toBe(1);
    expect((await db.history.get("main:3:movie:11"))!.pos).toBe(100);
    const ep = (await db.history.get("main:3:series:21:103"))!;
    expect(ep).toMatchObject({ kind: "series", epId: 103, season: 1, episode: 3, dur: 2400, pos: 600, updatedAt: 2000 });
    await db.history.delete("main:3:series:21:103");
    expect(await importPlayback(source, "main", lib().playback as TraktPlayback[], index(), { left: 0 }, 10_000)).toBe(0);
    expect(await db.history.get("main:3:series:21:103")).toBeUndefined();
  });
});

describe("scrobble : marquer vu à la fin", () => {
  const item = { kind: "movie" as const, title: "Alien", year: 1979 };
  it("désactivé : la fin part en pause", async () => {
    const sent: string[] = [];
    const s = new Scrobbler(item, async (b) => { sent.push(b.action); return { linked: true }; }, () => false);
    s.onState("playing", 10, 100);
    s.onState("ended", 95, 100);
    await vi.waitFor(() => expect(sent).toEqual(["start", "pause"]));
  });
  it("actif (par défaut) : stop", async () => {
    const sent: string[] = [];
    const s = new Scrobbler(item, async (b) => { sent.push(b.action); return { linked: true }; });
    s.onState("playing", 10, 100);
    s.onState("ended", 95, 100);
    await vi.waitFor(() => expect(sent).toEqual(["start", "stop"]));
  });
});
