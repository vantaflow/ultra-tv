import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/net/secrets", () => ({ encryptSecret: async (s: string) => s, decryptSecret: async (s: string) => s }));
vi.mock("@/cloud/service", async () => {
  const { create } = await import("zustand");
  return { getToken: async () => "tok", useCloud: create(() => ({ paired: true, worker: "https://w.example" })) };
});
vi.mock("@/cloud/client", async (orig) => ({ ...(await orig<typeof import("@/cloud/client")>()), cloudAvailable: () => true }));

import { setCloudHttp } from "@/cloud/client";
import { useCloud } from "@/cloud/service";
import { db } from "@/db/db";
import type { FavoriteRow, HistoryRow, Source } from "@/db/types";
import { DAY_MS } from "@/lib/extmeta";
import { _resetExtCache, loadFanart, loadNewEpisodes, loadTvShow } from "./extmeta";

let calls: string[] = [];
let routes: Record<string, { status: number; body: unknown }> = {};
beforeEach(async () => {
  calls = [];
  routes = {};
  _resetExtCache();
  await db.details.clear();
  await db.series.clear();
  useCloud.setState({ paired: true, worker: "https://w.example" });
  setCloudHttp(async (r) => {
    const path = r.url.replace("https://w.example", "");
    calls.push(path);
    expect(r.headers?.authorization).toBe("Bearer tok");
    const hit = Object.entries(routes).find(([k]) => path.startsWith(k))?.[1] ?? { status: 404, body: { error: "not_found" } };
    return { status: hit.status, text: JSON.stringify(hit.body), retryAfter: "", etag: "" };
  });
});

describe("loadFanart", () => {
  it("film : par identifiant TMDB, mis en cache", async () => {
    routes["/api/fanart/movie/603"] = { status: 200, body: { logo: "https://assets.fanart.tv/l.png", background: "https://assets.fanart.tv/b.jpg", thumb: null } };
    const a = await loadFanart({ kind: "movie", tmdb: 603, title: "X" }, "fr");
    expect(a.logo).toBe("https://assets.fanart.tv/l.png");
    expect(calls).toEqual(["/api/fanart/movie/603?lang=fr"]);
    await loadFanart({ kind: "movie", tmdb: 603 }, "fr");
    expect(calls).toHaveLength(1);
  });
  it("série : recherche TMDB, external_ids (tvdb), puis Fanart par TheTVDB", async () => {
    routes["/api/tmdb/search/tv"] = { status: 200, body: { results: [{ id: 1399 }] } };
    routes["/api/tmdb/tv/1399?append_to_response=external_ids"] = { status: 200, body: { id: 1399, external_ids: { imdb_id: "tt0944947", tvdb_id: 121361 } } };
    routes["/api/fanart/tv/121361"] = { status: 200, body: { logo: "https://assets.fanart.tv/t.png", background: null, thumb: null } };
    const a = await loadFanart({ kind: "tv", title: "Trônes", year: 2011 }, "fr");
    expect(a.logo).toBe("https://assets.fanart.tv/t.png");
    expect(calls[0]).toMatch(/^\/api\/tmdb\/search\/tv\?query=Tr%C3%B4nes&language=fr-FR&first_air_date_year=2011$/);
    expect(calls.slice(1)).toEqual(["/api/tmdb/tv/1399?append_to_response=external_ids", "/api/fanart/tv/121361?lang=fr"]);
  });
  it("Fanart non configuré (503) : rien", async () => {
    routes["/api/fanart/movie/7"] = { status: 503, body: { error: "fanart_not_configured" } };
    expect(await loadFanart({ kind: "movie", tmdb: 7 }, "en")).toEqual({ logo: null, background: null, thumb: null });
  });
  it("non appairé : aucun appel", async () => {
    useCloud.setState({ paired: false });
    expect((await loadFanart({ kind: "movie", tmdb: 603 }, "fr")).logo).toBeNull();
    expect(calls).toEqual([]);
  });
});

describe("loadTvShow", () => {
  it("identifiant TMDB lu dans la fiche Xtream en cache, puis TVmaze par IMDb", async () => {
    await db.details.put({ key: "series:1:5", json: { info: { tmdb: "1399" } }, fetchedAt: Date.now() });
    routes["/api/tmdb/tv/1399"] = { status: 200, body: { external_ids: { imdb_id: "tt0944947", tvdb_id: 121361 } } };
    routes["/api/tvmaze/show"] = { status: 200, body: { id: 82, status: "Ended", network: "N", previous: { season: 8, number: 6, name: "Fin", airAt: 1, image: null, summary: null }, next: null } };
    const s = await loadTvShow({ kind: "tv", title: "X", detailKey: "series:1:5" }, "fr");
    expect(s?.status).toBe("Ended");
    expect(calls).toEqual(["/api/tmdb/tv/1399?append_to_response=external_ids", "/api/tvmaze/show?imdb=tt0944947"]);
  });
  it("erreur réseau (429) : remonte, rien n'est mis en cache", async () => {
    routes["/api/tmdb/tv/5"] = { status: 429, body: {} };
    await expect(loadTvShow({ kind: "tv", tmdb: 5 }, "fr")).rejects.toThrow();
    expect(await db.details.get("ext:ids:tv:5")).toBeUndefined();
  });
});

describe("loadNewEpisodes", () => {
  it("séries en cours / favorites présentes dans la playlist, fenêtre de 7 jours", async () => {
    const now = Date.now();
    const source = { id: 1, cid: 3 } as Source;
    await db.series.bulkAdd([10, 11, 12].map((sid) => ({ sourceId: 3, catExt: "a", ord: sid, seriesId: sid, name: `T${sid}`, title: `T${sid}`, norm: `t${sid}`, words: [], year: 2020, poster: null, backdrop: null, plot: null, rating: 0, added: 0 })));
    for (const [sid, tmdb] of [[10, 100], [11, 110], [12, 120]] as const) {
      await db.details.put({ key: `series:1:${sid}`, json: { info: { tmdb } }, fetchedAt: now });
      routes[`/api/tmdb/tv/${tmdb}?`] = { status: 200, body: { external_ids: { tvdb_id: tmdb * 10 } } };
    }
    routes["/api/tvmaze/show?tvdb=1000"] = { status: 200, body: { id: 1, status: "Running", previous: { season: 1, number: 2, airAt: now - DAY_MS }, next: null } };
    routes["/api/tvmaze/show?tvdb=1100"] = { status: 200, body: { id: 2, status: "Running", previous: null, next: { season: 3, number: 1, airAt: now + 2 * DAY_MS } } };
    routes["/api/tvmaze/show?tvdb=1200"] = { status: 200, body: { id: 3, status: "Ended", previous: { season: 1, number: 9, airAt: now - 90 * DAY_MS }, next: null } };
    const hist = [{ kind: "series", seriesId: 10, updatedAt: 5, title: "T10" }] as HistoryRow[];
    const favs = [11, 12, 99].map((refId) => ({ kind: "series", refId, name: `T${refId}`, addedAt: refId })) as FavoriteRow[];
    const out = await loadNewEpisodes(source, hist, favs, "fr", now);
    expect(out.map((x) => [x.series.seriesId, x.ep.season, x.ep.number, x.aired])).toEqual([[10, 1, 2, true], [11, 3, 1, false]]);
    expect(calls.filter((c) => c.includes("/99"))).toEqual([]); // série 99 absente de la playlist : pas interrogée
  });
});
