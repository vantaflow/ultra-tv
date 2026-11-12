import { describe, expect, it } from "vitest";
import {
  DAY_MS, airState, firstSearchId, fmtAirDay, fmtEpisodeLine, parseExternalIds, parseFanart, parseTvShow, pickCandidates, pickNewEpisodes,
  type TvEpisode, type TvShow,
} from "./extmeta";

const ep = (season: number, number: number, airAt: number | null, name: string | null = "Titre"): TvEpisode => ({ season, number, name, airAt, image: null, summary: null });
const show = (p: Partial<TvShow>): TvShow => ({ id: 1, status: "Running", network: null, previous: null, next: null, ...p });

describe("parseExternalIds", () => {
  it("série : imdb et tvdb dans external_ids", () => {
    expect(parseExternalIds({ id: 1399, external_ids: { imdb_id: "tt0944947", tvdb_id: 121361 } })).toEqual({ imdbId: "tt0944947", tvdbId: 121361 });
  });
  it("film : imdb_id à la racine si absent de external_ids", () => {
    expect(parseExternalIds({ imdb_id: "tt0133093", external_ids: { imdb_id: null } })).toEqual({ imdbId: "tt0133093", tvdbId: null });
  });
  it("valeurs invalides ignorées", () => {
    expect(parseExternalIds({ external_ids: { imdb_id: "nm123", tvdb_id: -4 } })).toEqual({ imdbId: null, tvdbId: null });
    expect(parseExternalIds({ external_ids: { tvdb_id: "81189" } }).tvdbId).toBe(81189);
    expect(parseExternalIds(null)).toEqual({ imdbId: null, tvdbId: null });
  });
});

describe("firstSearchId", () => {
  it("premier identifiant valide", () => {
    expect(firstSearchId({ results: [{ id: 0 }, { name: "x" }, { id: 42 }] })).toBe(42);
    expect(firstSearchId({ results: [] })).toBeNull();
    expect(firstSearchId("nope")).toBeNull();
  });
});

describe("parseFanart", () => {
  it("garde les URL HTTPS seulement", () => {
    expect(parseFanart({ logo: "https://assets.fanart.tv/a.png", background: "http://x/b.jpg", thumb: null })).toEqual({ logo: "https://assets.fanart.tv/a.png", background: null, thumb: null });
    expect(parseFanart({ error: "fanart_not_configured" })).toEqual({ logo: null, background: null, thumb: null });
    expect(parseFanart(undefined)).toEqual({ logo: null, background: null, thumb: null });
  });
});

describe("parseTvShow", () => {
  it("réponse réduite du Worker", () => {
    const s = parseTvShow({ id: 82, status: "Running", network: "Chaîne", previous: { season: 2, number: 4, name: "A", airAt: 1000, image: "https://i/x.jpg", summary: "Résumé" }, next: null });
    expect(s).toEqual({ id: 82, status: "Running", network: "Chaîne", previous: { season: 2, number: 4, name: "A", airAt: 1000, image: "https://i/x.jpg", summary: "Résumé" }, next: null });
  });
  it("champs invalides neutralisés", () => {
    const s = parseTvShow({ id: null, status: 3, previous: { season: "2", number: 4.5, airAt: "hier", image: "javascript:x" }, next: "x" });
    expect(s).toEqual({ id: null, status: null, network: null, previous: { season: null, number: null, name: null, airAt: null, image: null, summary: null }, next: null });
    expect(parseTvShow(null)).toBeNull();
  });
});

describe("airState", () => {
  const now = 10 * DAY_MS;
  it("prochain épisode daté en priorité", () => {
    expect(airState(show({ next: ep(2, 5, now + DAY_MS), previous: ep(2, 4, now - DAY_MS) }), now)).toEqual({ kind: "next", ep: ep(2, 5, now + DAY_MS) });
  });
  it("série terminée", () => {
    expect(airState(show({ status: "Ended", previous: ep(5, 10, now - 300 * DAY_MS) }), now)?.kind).toBe("ended");
  });
  it("sinon dernier épisode diffusé, sinon rien", () => {
    expect(airState(show({ previous: ep(1, 3, now - 2 * DAY_MS) }), now)?.kind).toBe("last");
    expect(airState(show({}), now)).toBeNull();
    expect(airState(null, now)).toBeNull();
  });
});

describe("pickCandidates", () => {
  const h = (seriesId: number, updatedAt: number, kind = "series") => ({ kind, seriesId, updatedAt, title: `S${seriesId}` });
  const f = (refId: number, addedAt: number, kind = "series") => ({ kind, refId, name: `F${refId}`, addedAt });
  it("en cours d'abord (plus récent en tête), puis favoris, sans doublon", () => {
    const c = pickCandidates([h(1, 10), h(2, 30), h(1, 50), h(9, 99, "movie")], [f(2, 5), f(3, 1), f(4, 9), f(8, 9, "live")]);
    expect(c.map((x) => x.seriesId)).toEqual([1, 2, 4, 3]);
  });
  it("au plus `max`", () => {
    const many = Array.from({ length: 30 }, (_, i) => f(i + 1, i));
    expect(pickCandidates([], many, 20)).toHaveLength(20);
  });
});

describe("pickNewEpisodes", () => {
  const now = 100 * DAY_MS;
  const c = (seriesId: number) => ({ seriesId, title: `S${seriesId}` });
  it("fenêtre de ±7 jours, épisodes sortis puis à venir, triés", () => {
    const out = pickNewEpisodes([
      { series: c(1), show: show({ previous: ep(1, 1, now - 3 * DAY_MS), next: ep(1, 2, now + 4 * DAY_MS) }) }, // sorti il y a 3 j (prioritaire)
      { series: c(2), show: show({ previous: ep(1, 1, now - 20 * DAY_MS), next: ep(1, 2, now + 2 * DAY_MS) }) }, // à venir dans 2 j
      { series: c(3), show: show({ previous: ep(1, 1, now - DAY_MS) }) }, // sorti hier
      { series: c(4), show: show({ next: ep(1, 1, now + 6 * DAY_MS) }) }, // à venir dans 6 j
      { series: c(5), show: show({ previous: ep(1, 1, now - 8 * DAY_MS), next: ep(1, 2, now + 9 * DAY_MS) }) }, // hors fenêtre
      { series: c(6), show: null },
      { series: c(7), show: show({ next: ep(1, 1, null) }) },
    ], now);
    expect(out.map((x) => [x.series.seriesId, x.aired])).toEqual([[3, true], [1, true], [2, false], [4, false]]);
  });
  it("vide si rien dans la fenêtre", () => {
    expect(pickNewEpisodes([{ series: c(1), show: show({ status: "Ended", previous: ep(1, 1, now - 400 * DAY_MS) }) }], now)).toEqual([]);
  });
});

describe("formatage", () => {
  const now = new Date(2026, 10, 12, 15, 0).getTime();
  it("jours relatifs puis date courte", () => {
    expect(fmtAirDay(new Date(2026, 10, 13, 21, 0).getTime(), now, "fr-FR")).toBe("demain");
    expect(fmtAirDay(new Date(2026, 10, 12, 21, 0).getTime(), now, "en-US")).toBe("today");
    expect(fmtAirDay(new Date(2026, 10, 11, 21, 0).getTime(), now, "en-US")).toBe("yesterday");
    expect(fmtAirDay(new Date(2026, 10, 18, 21, 0).getTime(), now, "en-US")).toBe("Wed, Nov 18");
    expect(fmtAirDay(new Date(2027, 0, 5, 21, 0).getTime(), now, "en-US")).toContain("2027");
  });
  it("ligne d'épisode", () => {
    const code = (s: number, e: number) => `S${s} É${e}`;
    const e = ep(2, 5, new Date(2026, 10, 13, 21, 0).getTime(), "La relève");
    expect(fmtEpisodeLine(e, now, "fr-FR", code)).toBe("S2 É5 · La relève · demain");
    expect(fmtEpisodeLine(e, now, "fr-FR", code, false)).toBe("S2 É5 · demain");
    expect(fmtEpisodeLine(ep(1, 1, null, null), now, "fr-FR", code)).toBe("S1 É1");
  });
});
