import { describe, expect, it } from "vitest";
import { ratingLabels } from "./ratings";

describe("ratingLabels", () => {
  it("IMDb puis RT ; sans IMDb, la note de la source", () => {
    expect(ratingLabels({ imdb: 8.8, rt: 87 }, 7.1)).toEqual(["IMDb 8.8", "RT 87%"]);
    expect(ratingLabels({ rt: 40 }, 6.25)).toEqual(["★ 6.3", "RT 40%"]);
    expect(ratingLabels(null, 0)).toEqual([]);
  });
});
