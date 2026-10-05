import "fake-indexeddb/auto";
import { describe, expect, it } from "vitest";
import { db } from "./db";
import { favoritesOf, toggleFavorite } from "./queries";

describe("favoritesOf", () => {
  it("renvoie les favoris d'un type donné (et tous sans type)", async () => {
    await db.favorites.clear();
    await toggleFavorite({ profile: "main", sourceId: 1, kind: "series", refId: 39825, name: "S", image: null });
    await toggleFavorite({ profile: "main", sourceId: 1, kind: "movie", refId: 7, name: "M", image: null });
    expect((await favoritesOf("main", 1, "series")).map((f) => f.refId)).toEqual([39825]);
    expect((await favoritesOf("main", 1, "movie")).map((f) => f.refId)).toEqual([7]);
    expect(await favoritesOf("main", 1, "live")).toEqual([]);
    expect((await favoritesOf("main", 1)).length).toBe(2);
  });
});
