import { describe, expect, it } from "vitest";
import { chooseSrc, optimizeImageUrl, PROXY_TIMEOUT_MS } from "./imageProxy";

const bg = "https://assets.fanart.tv/fanart/movies/603/moviebackground/matrix.jpg";
const logo = "https://assets.fanart.tv/fanart/movies/603/hdmovielogo/matrix.png";
const thumb = "https://assets.fanart.tv/fanart/movies/603/moviethumb/matrix.jpg";
const tvmaze = "https://static.tvmaze.com/uploads/images/original_untouched/1/2.jpg";
const on = { enabled: true };

describe("optimizeImageUrl : hôtes", () => {
  it("réécrit Fanart.tv et TVmaze", () => {
    expect(optimizeImageUrl(bg, on)).toMatch(/^https:\/\/wsrv\.nl\/\?url=/);
    expect(optimizeImageUrl(tvmaze, on)).toMatch(/^https:\/\/wsrv\.nl\/\?url=/);
  });
  it("ne touche ni TMDB ni les autres hôtes ni une URL déjà proxifiée", () => {
    expect(optimizeImageUrl("https://image.tmdb.org/t/p/original/a.jpg", on)).toBeNull();
    expect(optimizeImageUrl("https://exemple.org/a.jpg", on)).toBeNull();
    expect(optimizeImageUrl("https://fanart.tv.evil.org/a.jpg", on)).toBeNull();
    expect(optimizeImageUrl("https://evil.org/?u=assets.fanart.tv/a.jpg", on)).toBeNull();
    expect(optimizeImageUrl("https://wsrv.nl/?url=assets.fanart.tv%2Fa.jpg&w=500", on)).toBeNull();
    expect(optimizeImageUrl(null, on)).toBeNull();
    expect(optimizeImageUrl("data:image/png;base64,xx", on)).toBeNull();
  });
});

describe("optimizeImageUrl : encodage", () => {
  it("retire le schéma et encode le reste", () => {
    expect(optimizeImageUrl("https://static.tvmaze.com/a b/c.jpg?x=1&y=2", on)).toBe(
      "https://wsrv.nl/?url=static.tvmaze.com%2Fa%20b%2Fc.jpg%3Fx%3D1%26y%3D2&w=640&output=webp&q=75");
  });
});

describe("optimizeImageUrl : largeurs", () => {
  it("fond 1280, 960 en mémoire faible", () => {
    expect(optimizeImageUrl(bg, on)).toContain("&w=1280&output=webp&q=75");
    expect(optimizeImageUrl(bg, { enabled: true, lowMem: true })).toContain("&w=960&");
  });
  it("logo 500 et vignette 640, quel que soit le mode", () => {
    expect(optimizeImageUrl(logo, on)).toContain("&w=500&");
    expect(optimizeImageUrl(logo, { enabled: true, lowMem: true })).toContain("&w=500&");
    expect(optimizeImageUrl(thumb, on)).toContain("&w=640&");
    expect(optimizeImageUrl(tvmaze, on)).toContain("&w=640&");
  });
  it("l'indication de l'appelant sert quand le chemin ne dit rien", () => {
    expect(optimizeImageUrl(tvmaze, { enabled: true, hint: "background" })).toContain("&w=1280&");
    expect(optimizeImageUrl(tvmaze, { enabled: true, hint: "logo" })).toContain("&w=500&");
  });
});

describe("préférence désactivée", () => {
  it("renvoie null : l'URL d'origine est conservée", () => {
    expect(optimizeImageUrl(bg, { enabled: false })).toBeNull();
    expect(optimizeImageUrl(tvmaze, { enabled: false })).toBeNull();
  });
});

describe("repli", () => {
  it("délai de 4 s", () => expect(PROXY_TIMEOUT_MS).toBe(4000));
  it("wsrv.nl par défaut, l'originale après échec ou dépassement", () => {
    const p = optimizeImageUrl(bg, on);
    expect(chooseSrc(bg, p, null)).toBe(p);
    expect(chooseSrc(bg, p, bg)).toBe(bg);
    expect(chooseSrc(bg, null, null)).toBe(bg);
    expect(chooseSrc(undefined, null, null)).toBeUndefined();
  });
});
