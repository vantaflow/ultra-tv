import { describe, expect, it } from "vitest";
import type { ChannelRow, MovieRow, SeriesRow } from "@/db/types";
import { sourceBadge } from "./profileSources";
import { mergeSearch, type SourceHits } from "./searchMerge";

const movie = (id: number, cid: number) => ({ id, sourceId: cid, title: `M${id}` }) as unknown as MovieRow;
const chan = (id: number, cid: number) => ({ id, sourceId: cid }) as unknown as ChannelRow;
const hits = (over: Partial<SourceHits>): SourceHits => ({ channels: [], movies: [], series: [], programs: [], fuzzy: false, ...over });

describe("recherche dans toutes les sources", () => {
  it("fusionne les résultats en gardant la génération (source) de chaque ligne", () => {
    const m = mergeSearch([hits({ movies: [movie(1, 10)], channels: [chan(1, 10)] }), hits({ movies: [movie(2, 20)], series: [{ id: 3, sourceId: 20 } as unknown as SeriesRow] })]);
    expect(m.movies.map((x) => x.sourceId)).toEqual([10, 20]);
    expect(m.channels).toHaveLength(1);
    expect(m.series).toHaveLength(1);
    expect(m.fuzzy).toBe(false);
  });
  it("titres proches seulement si aucune source n'a de résultat exact", () => {
    const near = hits({ movies: [movie(2, 20)], fuzzy: true });
    expect(mergeSearch([hits({ movies: [movie(1, 10)] }), near]).movies.map((x) => x.id)).toEqual([1]);
    expect(mergeSearch([hits({}), near]).fuzzy).toBe(true);
    expect(mergeSearch([]).fuzzy).toBe(false);
  });
  it("pastille courte", () => {
    expect(sourceBadge(" IPTV ")).toBe("IPTV");
    expect(sourceBadge("Ma liste de chaînes")).toBe("Ma list…");
    expect(sourceBadge("  ")).toBe("?");
  });
});
