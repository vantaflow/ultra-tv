import { describe, expect, it } from "vitest";
import { byRating, sortHits } from "./Vod";

describe("byRating", () => {
  it("met les mieux notés d'abord, ordre conservé à note égale", () => {
    const r = byRating([{ id: 1, rating: 6 }, { id: 2, rating: 8 }, { id: 3, rating: 0 }, { id: 4, rating: 8 }]);
    expect(r.map((x) => x.id)).toEqual([2, 4, 1, 3]);
  });
});

describe("sortHits", () => {
  const items = [{ rating: 5, added: 10, ord: 2 }, { rating: 9, added: 5, ord: 0 }, { rating: 7, added: 20, ord: 1 }];
  it("remet les résultats d'index dans l'ordre du tri choisi", () => {
    expect(sortHits(items, "rating").map((i) => i.rating)).toEqual([9, 7, 5]);
    expect(sortHits(items, "recent").map((i) => i.added)).toEqual([20, 10, 5]);
    expect(sortHits(items, "provider").map((i) => i.ord)).toEqual([0, 1, 2]);
  });
});
