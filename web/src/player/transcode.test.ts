import { describe, expect, it } from "vitest";
import { clampSeek, parseTxInfo, txClock, txCutShort, txInfoUrl, txSubUrl, txUrl } from "./transcode";

describe("conversion audio (bureau)", () => {
  it("URL /tx avec la position", () => {
    const u = txUrl("http://127.0.0.1:5000/tok/", "http://h/movie/u/p/1.mkv", 754.5);
    expect(u.startsWith("http://127.0.0.1:5000/tok/tx/")).toBe(true);
    expect(u.endsWith("?ss=754.5")).toBe(true);
    const b64 = u.slice("http://127.0.0.1:5000/tok/tx/".length).split("?")[0]!;
    expect(atob(b64.replace(/-/g, "+").replace(/_/g, "/"))).toBe("http://h/movie/u/p/1.mkv");
    expect(txUrl("http://x/t/", "http://h/a.ts", 0)).not.toContain("?ss");
  });
  it("piste audio dans l'URL, routes pistes et sous-titres", () => {
    expect(txUrl("http://x/t/", "http://h/a.mkv", 10, false, 2)).toContain("ss=10&a=2");
    expect(txUrl("http://x/t/", "http://h/a.mkv", 0, false, 0)).not.toContain("a=");
    expect(txInfoUrl("http://x/t/", "http://h/a.mkv").startsWith("http://x/t/txi/")).toBe(true);
    expect(txSubUrl("http://x/t/", "http://h/a.mkv", 3).endsWith("?s=3")).toBe(true);
    expect(txSubUrl("http://x/t/", "http://h/a.mkv", 3, 1346).endsWith("&ss=1346")).toBe(true);
    expect(txSubUrl("http://x/t/", "http://h/a.mkv", 3, 0)).not.toContain("ss=");
  });
  it("réponse /txi validée", () => {
    expect(parseTxInfo(null)).toBeNull();
    expect(parseTxInfo({ audio: [], subs: [] })).toBeNull();
    expect(parseTxInfo({ audio: [{ i: 1, lang: "fre", title: "VFF", codec: "ac3" }, { i: "x" }], subs: "?" })).toEqual({ audio: [{ i: 1, lang: "fre", title: "VFF", codec: "ac3" }], subs: [] });
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

describe("coupure de la conversion (#295)", () => {
  const tx = { orig: "http://x/1.mkv", offset: 29, duration: 3549 };
  it("une fin loin de la durée connue est une coupure, pas la fin de l'épisode", () => {
    expect(txCutShort(tx, 49)).toBe(true);
  });
  it("une fin dans les 30 dernières secondes est une vraie fin", () => {
    expect(txCutShort(tx, 3549 - 29 - 10)).toBe(false);
  });
  it("sans conversion ou sans durée connue : rien à reprendre", () => {
    expect(txCutShort(null, 10)).toBe(false);
    expect(txCutShort({ ...tx, duration: 0 }, 10)).toBe(false);
  });
});
