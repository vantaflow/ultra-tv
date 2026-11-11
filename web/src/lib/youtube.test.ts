import { describe, expect, it } from "vitest";
import { findTmdbTrailer, pickTmdbTrailer, youtubeKey } from "./youtube";

const K = "dQw4w9WgXcQ";

describe("youtubeKey", () => {
  it("formats d'adresse", () => {
    expect(youtubeKey(`https://www.youtube.com/watch?v=${K}`)).toBe(K);
    expect(youtubeKey(`https://www.youtube.com/watch?feature=share&v=${K}&t=10`)).toBe(K);
    expect(youtubeKey(`https://youtu.be/${K}`)).toBe(K);
    expect(youtubeKey(`https://youtu.be/${K}?t=5`)).toBe(K);
    expect(youtubeKey(`https://www.youtube.com/embed/${K}`)).toBe(K);
    expect(youtubeKey(`https://www.youtube-nocookie.com/embed/${K}?rel=0`)).toBe(K);
    expect(youtubeKey(`https://youtube.com/shorts/${K}`)).toBe(K);
    expect(youtubeKey(`https://m.youtube.com/watch?v=${K}`)).toBe(K);
    expect(youtubeKey(`www.youtube.com/watch?v=${K}`)).toBe(K);
    expect(youtubeKey(`//youtu.be/${K}`)).toBe(K);
  });
  it("clé brute, avec espaces", () => {
    expect(youtubeKey(K)).toBe(K);
    expect(youtubeKey(`  ${K}\n`)).toBe(K);
    expect(youtubeKey("a_b-c123456")).toBe("a_b-c123456");
  });
  it("valeurs vides ou absentes", () => {
    for (const v of ["", "   ", "null", "NULL", "None", "0", "N/A", null, undefined, 42, {}]) expect(youtubeKey(v)).toBeNull();
  });
  it("adresses qui ne sont pas YouTube, clés mal formées", () => {
    expect(youtubeKey("https://exemple.test/film.mp4")).toBeNull();
    expect(youtubeKey(`https://exemple.test/embed/${K}`)).toBeNull();
    expect(youtubeKey(`https://evilyoutube.com/watch?v=${K}`)).toBeNull();
    expect(youtubeKey("https://www.youtube.com/watch?v=court")).toBeNull();
    expect(youtubeKey(`https://youtu.be/${K}AAA`)).toBeNull();
    expect(youtubeKey("abcdefghijkl")).toBeNull(); // 12 caractères
    expect(youtubeKey("abc def ghij")).toBeNull();
  });
});

describe("pickTmdbTrailer", () => {
  it("officielle d'abord, puis Trailer, puis la première", () => {
    const a = { site: "YouTube", type: "Teaser", key: "aaaaaaaaaaa" };
    const b = { site: "YouTube", type: "Trailer", key: "bbbbbbbbbbb" };
    const c = { site: "YouTube", type: "Trailer", official: true, key: "ccccccccccc" };
    expect(pickTmdbTrailer([a, b, c])).toBe("ccccccccccc");
    expect(pickTmdbTrailer([a, b])).toBe("bbbbbbbbbbb");
    expect(pickTmdbTrailer([a])).toBe("aaaaaaaaaaa");
  });
  it("ignore les autres sites et les clés invalides", () => {
    expect(pickTmdbTrailer([{ site: "Vimeo", type: "Trailer", key: "aaaaaaaaaaa" }, { site: "YouTube", key: "x" }])).toBeNull();
    expect(pickTmdbTrailer(null)).toBeNull();
    expect(pickTmdbTrailer([])).toBeNull();
  });
});

describe("findTmdbTrailer", () => {
  const vid = (key: string) => ({ videos: { results: [{ site: "YouTube", type: "Trailer", key }] } });
  it("identifiant connu : pas de recherche", async () => {
    const calls: string[] = [];
    const k = await findTmdbTrailer(async (p) => { calls.push(p); return vid("aaaaaaaaaaa"); }, { kind: "movie", tmdb: 603, langs: ["fr-FR"] });
    expect(k).toBe("aaaaaaaaaaa");
    expect(calls).toEqual(["movie/603?append_to_response=videos&language=fr-FR"]);
  });
  it("recherche par titre et année, puis détails", async () => {
    const calls: string[] = [];
    const k = await findTmdbTrailer(async (p) => {
      calls.push(p);
      return p.startsWith("search/") ? { results: [{ id: 7 }] } : vid("bbbbbbbbbbb");
    }, { kind: "tv", title: "Dark", year: 2017, langs: ["fr-FR"] });
    expect(k).toBe("bbbbbbbbbbb");
    expect(calls[0]).toContain("search/tv?");
    expect(calls[0]).toContain("first_air_date_year=2017");
    expect(calls[1]).toBe("tv/7?append_to_response=videos&language=fr-FR");
  });
  it("retente en anglais quand la langue demandée n'a pas de vidéo", async () => {
    const k = await findTmdbTrailer(async (p) => (p.endsWith("language=en-US") ? vid("ddddddddddd") : { videos: { results: [] } }), { kind: "movie", tmdb: 1, langs: ["fr-FR"] });
    expect(k).toBe("ddddddddddd");
  });
  it("titre introuvable, accès indisponible, erreur : null sans exception", async () => {
    expect(await findTmdbTrailer(async () => ({ results: [] }), { kind: "movie", title: "X" })).toBeNull();
    expect(await findTmdbTrailer(async () => null, { kind: "movie", title: "X" })).toBeNull();
    expect(await findTmdbTrailer(async () => { throw new Error("réseau"); }, { kind: "movie", tmdb: 1 })).toBeNull();
    expect(await findTmdbTrailer(async () => ({}), { kind: "movie" })).toBeNull();
  });
});
