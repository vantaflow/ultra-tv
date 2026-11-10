import { describe, expect, it } from "vitest";
import { clampSeek, txClock, txUrl } from "./transcode";

describe("conversion audio (bureau)", () => {
  it("URL /tx avec la position", () => {
    const u = txUrl("http://127.0.0.1:5000/tok/", "http://h/movie/u/p/1.mkv", 754.5);
    expect(u.startsWith("http://127.0.0.1:5000/tok/tx/")).toBe(true);
    expect(u.endsWith("?ss=754.5")).toBe(true);
    const b64 = u.slice("http://127.0.0.1:5000/tok/tx/".length).split("?")[0]!;
    expect(atob(b64.replace(/-/g, "+").replace(/_/g, "/"))).toBe("http://h/movie/u/p/1.mkv");
    expect(txUrl("http://x/t/", "http://h/a.ts", 0)).not.toContain("?ss");
  });
  it("position affichée = départ + lecture", () => {
    expect(txClock({ orig: "", offset: 600, duration: 5400 }, 12, Infinity)).toEqual({ t: 612, d: 5400 });
    expect(txClock(null, 12, 100)).toEqual({ t: 12, d: 100 });
    expect(txClock(null, 12, Infinity)).toEqual({ t: 12, d: 0 });
  });
  it("saut borné", () => {
    expect(clampSeek(-5, 100)).toBe(0);
    expect(clampSeek(500, 100)).toBe(99);
    expect(clampSeek(500, 0)).toBe(500);
  });
});
