import { describe, expect, it } from "vitest";
import { syncPercent } from "./Rail";

describe("syncPercent", () => {
  it("avance de phase en phase", () => {
    const p = (phase: "categories" | "live" | "movie" | "series" | "epg" | "done", ratio: number) => ({ phase, ratio, counts: { live: 0, movie: 0, series: 0 } });
    expect(syncPercent(null)).toBeNull();
    expect(syncPercent(p("categories", 0))).toBe(0);
    expect(syncPercent(p("movie", 0.5))).toBe(50);
    expect(syncPercent(p("epg", 1))).toBe(100);
    expect(syncPercent(p("done", 0))).toBe(100);
    expect(syncPercent(p("live", 7))).toBe(40);
  });
});
