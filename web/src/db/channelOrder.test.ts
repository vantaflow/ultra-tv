import "fake-indexeddb/auto";
import { describe, expect, it } from "vitest";
import { db } from "./db";
import { neighborOf } from "@/lib/channelOrder";
import { getOrder, orderedList, resetOrder, saveOrder } from "./channelOrder";
import { toggleFavorite } from "./queries";
import type { ChannelRow } from "./types";

describe("ordre personnel des chaînes (Dexie)", () => {
  it("enregistre, relit, isole par profil / source / liste, puis rétablit", async () => {
    await db.channelOrder.clear();
    expect(await getOrder("main", 1, "__fav__")).toBeNull();
    await saveOrder("main", 1, "__fav__", [3, 1, 2]);
    await saveOrder("main", 1, "cat7", [9, 8]);
    expect(await getOrder("main", 1, "__fav__")).toEqual([3, 1, 2]);
    expect(await getOrder("main", 1, "cat7")).toEqual([9, 8]);
    expect(await getOrder("kids", 1, "__fav__")).toBeNull();
    expect(await getOrder("main", 2, "__fav__")).toBeNull();
    await saveOrder("main", 1, "__fav__", [1, 2, 3]);
    expect(await getOrder("main", 1, "__fav__")).toEqual([1, 2, 3]);
    await resetOrder("main", 1, "__fav__");
    expect(await getOrder("main", 1, "__fav__")).toBeNull();
    expect(await getOrder("main", 1, "cat7")).toEqual([9, 8]);
  });
  it("une liste vide revient à l'ordre de la source", async () => {
    await saveOrder("main", 1, "x", [1]);
    await saveOrder("main", 1, "x", []);
    expect(await getOrder("main", 1, "x")).toBeNull();
  });
  it("file du lecteur : catégorie réordonnée et favoris suivent l'ordre affiché", async () => {
    await db.channels.clear(); await db.favorites.clear(); await db.channelOrder.clear();
    const ch = (streamId: number, ord: number, catExt: string, sep = 0) => ({ sourceId: 5, catExt, ord, streamId, num: ord, name: `c${streamId}`, display: `c${streamId}`, norm: `c${streamId}`, sep } as unknown as ChannelRow);
    await db.channels.bulkAdd([ch(1, 1, "a"), ch(2, 2, "a"), ch(3, 3, "a", 1), ch(4, 4, "a"), ch(9, 5, "b")]);
    // Sans ordre : catégorie = null (le lecteur garde sa requête par index).
    expect(await orderedList("main", 1, 5, "a")).toBeNull();
    await saveOrder("main", 1, "a", [4, 1]);
    const q = (await orderedList("main", 1, 5, "a"))!;
    expect(q.map((c) => c.streamId)).toEqual([4, 1, 2]); // séparateur exclu, chaîne non placée à la suite
    expect(neighborOf(q, 4, 1, (c) => c.streamId)?.streamId).toBe(1);
    // Favoris : ordre des favoris, puis ordre choisi.
    for (const r of [2, 9, 1]) await toggleFavorite({ profile: "main", sourceId: 1, kind: "live", refId: r, name: "x", image: null });
    const natural = (await orderedList("main", 1, 5, "__fav__"))!.map((c) => c.streamId);
    expect([...natural].sort()).toEqual([1, 2, 9]);
    await saveOrder("main", 1, "__fav__", [9, 2, 1]);
    expect((await orderedList("main", 1, 5, "__fav__"))!.map((c) => c.streamId)).toEqual([9, 2, 1]);
  });
});
