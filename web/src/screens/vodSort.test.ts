import { describe, expect, it } from "vitest";
import { byRating } from "./Vod";

describe("byRating", () => {
  it("met les mieux notés d'abord, ordre conservé à note égale", () => {
    const r = byRating([{ id: 1, rating: 6 }, { id: 2, rating: 8 }, { id: 3, rating: 0 }, { id: 4, rating: 8 }]);
    expect(r.map((x) => x.id)).toEqual([2, 4, 1, 3]);
  });
});
