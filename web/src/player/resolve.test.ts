import { describe, expect, it } from "vitest";
import { emptySource } from "@/db/sources";
import { candidates, type PlayTarget } from "./resolve";
import { formatFromUrl } from "./engine";

const source = { ...emptySource(), id: 1, server: "http://h.example:8080", username: "u", password: "p w" };
const base = { sourceId: 1, cid: 1, title: "x" };

describe("candidates", () => {
  it("direct : HLS puis MPEG-TS", () => {
    const t: PlayTarget = { ...base, kind: "live", refId: 42 };
    const c = candidates(source, t, { liveFormat: "m3u8", preferMp4: true });
    expect(c.map((x) => x.format)).toEqual(["hls", "ts"]);
    expect(c[0]!.url).toBe("http://h.example:8080/live/u/p%20w/42.m3u8");
  });
  it("direct : préférence MPEG-TS", () => {
    const c = candidates(source, { ...base, kind: "live", refId: 1 }, { liveFormat: "ts", preferMp4: true });
    expect(c[0]!.format).toBe("ts");
  });
  it("film mkv : essaie mp4 d'abord", () => {
    const c = candidates(source, { ...base, kind: "movie", refId: 7, ext: "mkv" }, { liveFormat: "m3u8", preferMp4: true });
    expect(c.map((x) => x.url.split(".").pop())).toEqual(["mp4", "mkv"]);
    expect(c[0]!.url).toContain("/movie/u/p%20w/7.");
  });
  it("film : conteneur annoncé seul si mp4 non préféré", () => {
    const c = candidates(source, { ...base, kind: "movie", refId: 7, ext: "avi" }, { liveFormat: "m3u8", preferMp4: false });
    expect(c[0]!.url.endsWith(".avi")).toBe(true);
  });
  it("épisode", () => {
    const c = candidates(source, { ...base, kind: "episode", refId: 9, ext: "mp4" }, { liveFormat: "m3u8", preferMp4: true });
    expect(c[0]!.url).toContain("/series/u/p%20w/9.mp4");
  });
  it("rediffusion : timeshift", () => {
    const c = candidates(source, { ...base, kind: "live", refId: 5, replay: { start: Date.UTC(2026, 0, 1, 20), minutes: 60 } }, { liveFormat: "m3u8", preferMp4: true });
    expect(c).toHaveLength(1);
    expect(c[0]!.url).toContain("timeshift.php");
    expect(c[0]!.url).toContain("duration=60");
  });
  it("M3U : URL de la playlist", () => {
    const c = candidates({ ...source, type: "m3u" }, { ...base, kind: "live", refId: 1, url: "http://x/a.m3u8" }, { liveFormat: "ts", preferMp4: true });
    expect(c).toEqual([{ url: "http://x/a.m3u8", format: "hls", label: "M3U" }]);
  });
});

describe("formatFromUrl", () => {
  it("détecte HLS, TS et natif", () => {
    expect(formatFromUrl("http://a/b.m3u8?x=1")).toBe("hls");
    expect(formatFromUrl("http://a/b.ts")).toBe("ts");
    expect(formatFromUrl("http://a/b.mp4")).toBe("native");
  });
});
