import { describe, expect, it } from "vitest";
import { parseSkip, skipOfferAt, skipUrl } from "./skip";

const EPISODE = {
  tvdb_id: 121361, type: "tv", season: 1, episode: 2,
  intro: [{ start_ms: 62_000, end_ms: 151_000, confidence: 0.9, submission_count: 12 }],
  recap: [{ start_ms: null, end_ms: 45_000 }],
  credits: [{ start_ms: 3_300_000, end_ms: null }],
  preview: [],
};

describe("skip (TheIntroDB)", () => {
  it("construit l'URL avec saison, épisode et durée", () => {
    expect(skipUrl(1399, 1, 2, 3480)).toBe("https://api.theintrodb.org/v4/media?tmdb_id=1399&season=1&episode=2&duration_ms=3480000");
    expect(skipUrl(27205)).toBe("https://api.theintrodb.org/v4/media?tmdb_id=27205");
  });

  it("lit les passages, triés, début absent = 0, en secondes", () => {
    const s = parseSkip(EPISODE);
    expect(s.map((x) => x.kind)).toEqual(["recap", "intro", "credits"]);
    expect(s[0]).toEqual({ kind: "recap", start: 0, end: 45 });
    expect(s[2]).toEqual({ kind: "credits", start: 3300, end: null });
  });

  it("ignore les passages invalides et les réponses illisibles", () => {
    expect(parseSkip(null)).toEqual([]);
    expect(parseSkip("x")).toEqual([]);
    expect(parseSkip({ intro: [{ start_ms: 50_000, end_ms: 10_000 }] })).toEqual([]);
  });

  it("intro et résumé : saut à leur fin, rien entre deux passages ni dans la marge de fin", () => {
    const s = parseSkip(EPISODE);
    expect(skipOfferAt(s, 10, 3480, true, true)).toEqual({ kind: "recap", to: 45, next: false });
    expect(skipOfferAt(s, 70, 3480, true, true)).toEqual({ kind: "intro", to: 151, next: false });
    expect(skipOfferAt(s, 50, 3480, true, true)).toBeNull();
    expect(skipOfferAt(s, 150, 3480, true, true)).toBeNull();
  });

  it("générique : épisode suivant s'il existe, scène post-générique d'un film, sinon rien", () => {
    const s = parseSkip(EPISODE);
    expect(skipOfferAt(s, 3310, 3480, true, true)).toEqual({ kind: "credits", to: null, next: true });
    expect(skipOfferAt(s, 3310, 3480, true, false)).toBeNull();
    expect(skipOfferAt([{ kind: "credits", start: 7000, end: null }], 7100, 7400, false, false)).toBeNull();
    expect(skipOfferAt([{ kind: "credits", start: 7000, end: 7200 }], 7100, 7400, false, false)).toEqual({ kind: "credits", to: 7200, next: false });
  });
});
