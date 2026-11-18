import { describe, expect, it } from "vitest";
import type { ChannelRow } from "@/db/types";
import { canMixFavorites, concatBySource, dropHidden, hiddenKey, mixActive, mixedNeighbor, type MixedChan } from "./favoritesMix";

const chan = (sourceId: number, streamId: number, catExt = "1"): MixedChan => ({ sourceId, cid: sourceId * 10, name: `S${sourceId}`, c: { streamId, catExt } as ChannelRow });

describe("canMixFavorites / mixActive", () => {
  it("exige au moins deux sources visibles", () => {
    expect(canMixFavorites(0)).toBe(false);
    expect(canMixFavorites(1)).toBe(false);
    expect(canMixFavorites(2)).toBe(true);
  });
  it("le choix du profil seul ne suffit pas", () => {
    expect(mixActive(true, 1)).toBe(false);
    expect(mixActive(undefined, 3)).toBe(false);
    expect(mixActive(true, 3)).toBe(true);
  });
});

describe("dropHidden", () => {
  it("retire les catégories masquées, par source", () => {
    const l = [chan(1, 1, "a"), chan(2, 2, "a"), chan(2, 3, "b")];
    expect(dropHidden(l, new Set([hiddenKey(2, "a")])).map((x) => x.c.streamId)).toEqual([1, 3]);
  });
  it("rien de masqué : liste inchangée", () => {
    const l = [chan(1, 1)];
    expect(dropHidden(l, new Set())).toBe(l);
  });
});

describe("concatBySource", () => {
  it("garde l'ordre des sources puis celui de chacune", () => {
    const out = concatBySource([{ sourceId: 2, items: [chan(2, 9), chan(2, 8)] }, { sourceId: 1, items: [chan(1, 5)] }]);
    expect(out.map((x) => `${x.sourceId}:${x.c.streamId}`)).toEqual(["2:9", "2:8", "1:5"]);
  });
  it("même identifiant dans deux sources : deux chaînes ; vraie répétition : une seule", () => {
    const out = concatBySource([{ sourceId: 1, items: [chan(1, 1), chan(1, 1)] }, { sourceId: 2, items: [chan(2, 1)] }]);
    expect(out.length).toBe(2);
  });
});

describe("mixedNeighbor", () => {
  const l = [chan(1, 1), chan(2, 1), chan(2, 2)];
  it("passe d'une source à l'autre", () => {
    expect(mixedNeighbor(l, { sourceId: 1, streamId: 1 }, 1)?.sourceId).toBe(2);
    expect(mixedNeighbor(l, { sourceId: 2, streamId: 2 }, -1)?.c.streamId).toBe(1);
  });
  it("null aux bornes ou si inconnue", () => {
    expect(mixedNeighbor(l, { sourceId: 1, streamId: 1 }, -1)).toBeNull();
    expect(mixedNeighbor(l, { sourceId: 2, streamId: 2 }, 1)).toBeNull();
    expect(mixedNeighbor(l, { sourceId: 9, streamId: 9 }, 1)).toBeNull();
  });
});
