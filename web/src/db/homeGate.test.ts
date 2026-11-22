import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { db } from "./db";
import { homeAllowedHistory, homeAllowedTrakt } from "./homeGate";
import type { HistoryRow, MovieRow, SeriesRow } from "./types";
import type { TraktRow } from "@/trakt/hooks";

const CID = 9;
const movie = (streamId: number, catExt: string) => ({ sourceId: CID, catExt, ord: 0, streamId, name: `m${streamId}`, title: `m${streamId}`, norm: "", year: null, poster: null, rating: 0, ext: "mp4", added: 0 }) as MovieRow;
const series = (seriesId: number, catExt: string) => ({ sourceId: CID, catExt, ord: 0, seriesId, name: `s${seriesId}`, title: `s${seriesId}`, norm: "", year: null, poster: null, rating: 0, added: 0 }) as unknown as SeriesRow;
const hist = (kind: "movie" | "series" | "live", refId: number, seriesId?: number): HistoryRow => ({ key: `${kind}${refId}`, profile: "main", sourceId: 1, kind, refId, title: "", image: null, pos: 10, dur: 100, updatedAt: 0, seriesId });

beforeEach(async () => {
  await db.movies.clear(); await db.series.clear();
  await db.movies.bulkAdd([movie(1, "ok"), movie(2, "adult")]);
  await db.series.bulkAdd([series(10, "ok"), series(11, "locked")]);
});

describe("accueil : reprises et rangées Trakt sans catégorie écartée", () => {
  it("reprises : film adulte et série verrouillée écartés, le reste (y compris inconnu) gardé", async () => {
    const list = [hist("movie", 1), hist("movie", 2), hist("series", 500, 10), hist("series", 501, 11), hist("movie", 99), hist("live", 3)];
    const out = await homeAllowedHistory(CID, list, new Set(["adult"]), new Set(["locked"]));
    expect(out.map((h) => h.key)).toEqual(["movie1", "series500", "movie99", "live3"]);
  });
  it("rien d'écarté : liste inchangée", async () => {
    const list = [hist("movie", 2)];
    expect(await homeAllowedHistory(CID, list, new Set(), new Set())).toBe(list);
  });
  it("Trakt : titres des catégories écartées retirés de toutes les rangées", () => {
    const m = (id: number, c: string): TraktRow => ({ kind: "movie", ref: id, row: movie(id, c) });
    const s = (id: number, c: string): TraktRow => ({ kind: "show", ref: id, row: series(id, c) });
    const rows = { watchlist: [m(1, "ok"), m(2, "adult")], recommendations: [s(10, "ok"), s(11, "locked")], trending: [m(2, "adult")], popular: [], recent: [s(11, "locked")], upNext: [{ ref: 11, row: series(11, "locked"), season: 1, episode: 2 }], lists: [{ id: "a", name: "A", rows: [m(2, "adult"), m(1, "ok")] }] };
    const out = homeAllowedTrakt(rows, new Set(["adult"]), new Set(["locked"]));
    expect(out.watchlist.map((r) => r.ref)).toEqual([1]);
    expect(out.recommendations.map((r) => r.ref)).toEqual([10]);
    expect(out.trending).toEqual([]);
    expect(out.recent).toEqual([]);
    expect(out.upNext).toEqual([]);
    expect(out.lists[0]!.rows.map((r) => r.ref)).toEqual([1]);
  });
});
