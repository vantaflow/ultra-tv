import { describe, expect, it } from "vitest";
import { classifyM3uEntry, episodeOf, seriesTitleOf, urlExtension } from "./m3uKind";

const e = (name: string, url: string, groupTitle: string | null = null, rawAttributes: Record<string, string> = {}) => ({ name, url, groupTitle, rawAttributes });

describe("classement des entrées M3U", () => {
  it("fichiers vidéo : film, même rangés dans un groupe « Drama »", () => {
    expect(classifyM3uEntry(e("Burning Days", "http://h/files/burning.days.2022.mkv", "Drama"))).toBe("movie");
    expect(classifyM3uEntry(e("Sea of Time", "http://h:80/x/y/sea.mp4?token=1", "Drama"))).toBe("movie");
    for (const x of ["avi", "mov", "m4v"]) expect(classifyM3uEntry(e("F", `http://h/a.${x}`))).toBe("movie");
  });
  it("chemins Xtream /movie/ et /series/", () => {
    expect(classifyM3uEntry(e("X", "http://h/movie/u/p/123.mkv"))).toBe("movie");
    expect(classifyM3uEntry(e("X", "http://h/movie/u/p/123"))).toBe("movie");
    expect(classifyM3uEntry(e("Show S01E02", "http://h/series/u/p/456.mp4"))).toBe("series");
    expect(classifyM3uEntry(e("Autre", "http://h/live/u/p/789.ts"))).toBe("live");
    expect(classifyM3uEntry(e("Autre", "http://h/live/u/p/789.m3u8"))).toBe("live");
  });
  it("tvg-type prime, group-title en dernier recours", () => {
    expect(classifyM3uEntry(e("X", "http://h/stream/1", null, { "tvg-type": "movie" }))).toBe("movie");
    expect(classifyM3uEntry(e("X", "http://h/stream/1", null, { "tvg-type": "series" }))).toBe("series");
    expect(classifyM3uEntry(e("X", "http://h/a.mp4", null, { "tvg-type": "live" }))).toBe("live");
    expect(classifyM3uEntry(e("X", "http://h/stream/1", "Films 2024"))).toBe("movie");
    expect(classifyM3uEntry(e("X", "http://h/stream/1", "Séries Netflix"))).toBe("series");
  });
  it("direct : chaînes .ts, .m3u8 et sans extension", () => {
    expect(classifyM3uEntry(e("TF1", "http://h/live/1.ts", "France"))).toBe("live");
    expect(classifyM3uEntry(e("CNN", "http://h/stream/cnn.m3u8", "News"))).toBe("live");
    expect(classifyM3uEntry(e("FOX News", "http://h:8080/u/p/2", "USA"))).toBe("live");
    expect(classifyM3uEntry(e("Film HLS", "http://h/vod/x.m3u8", "VOD Films"))).toBe("movie");
  });
  it("épisodes : S01E02, 1x02, Saison/Épisode", () => {
    expect(classifyM3uEntry(e("Dark S01E03", "http://h/a/b.mkv"))).toBe("series");
    expect(classifyM3uEntry(e("Dark 1x03", "http://h/a/b.mp4"))).toBe("series");
    expect(episodeOf("Dark S02 E10 - Titre")).toEqual({ season: 2, episode: 10 });
    expect(episodeOf("Dark 1x03")).toEqual({ season: 1, episode: 3 });
    expect(episodeOf("Saison 3 Épisode 4")).toEqual({ season: 3, episode: 4 });
    expect(episodeOf("Burning Days")).toBeNull();
    expect(seriesTitleOf("Dark S01E03 - Secrets")).toBe("Dark");
  });
  it("extension de l'adresse", () => {
    expect(urlExtension("http://h/a/b.MKV?x=1")).toBe("mkv");
    expect(urlExtension("http://h/stream/12")).toBe("");
  });
});
