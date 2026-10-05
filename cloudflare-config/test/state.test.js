import { describe, expect, it } from "vitest";
import { mergeState, parseStateBody } from "../src/store.js";

const NOW = 1_800_000_000_000;

describe("parseStateBody", () => {
  it("ignore les entrées invalides une à une", () => {
    const r = parseStateBody({ fav: [{ p: "Principal", k: "LIVE", r: "12", at: NOW }, { p: "", k: "LIVE", r: "1", at: NOW }, { p: "P", k: "BAD", r: "1", at: NOW }] }, NOW);
    expect(r.fav).toEqual([{ p: "Principal", k: "LIVE", r: "12", at: NOW, on: true }]);
  });
  it("plafonne une horloge en avance et refuse les affiches non http", () => {
    const r = parseStateBody({ hist: [{ p: "P", k: "MOVIE", r: "7", at: NOW + 10_000_000, img: "javascript:alert(1)", pos: 60000, dur: 7200000 }] }, NOW);
    expect(r.hist[0].at).toBe(NOW + 60_000);
    expect(r.hist[0].img).toBeNull();
  });
  it("refuse un corps non objet", () => expect(parseStateBody([], NOW).error).toBe("body"));
});

describe("mergeState", () => {
  const fav = (r, at, on = true) => ({ p: "P", k: "LIVE", r, at, on });
  it("le plus récent gagne, y compris une suppression", () => {
    const cur = { fav: [fav("1", NOW - 100), fav("2", NOW - 100)], hist: [] };
    const out = mergeState(cur, { fav: [fav("1", NOW, false), fav("2", NOW - 150, false)], hist: [] }, NOW);
    expect(out.fav.find((e) => e.r === "1").on).toBe(false);
    expect(out.fav.find((e) => e.r === "2").on).toBe(true);
  });
  it("élague les tombes anciennes", () => {
    const out = mergeState({ fav: [fav("1", NOW - 100 * 86400000, false)], hist: [] }, { fav: [], hist: [] }, NOW);
    expect(out.fav).toEqual([]);
  });
  it("borne l'historique par profil", () => {
    const hist = Array.from({ length: 250 }, (_, i) => ({ p: "P", k: "MOVIE", r: String(i), at: i + 1, t: "", img: null, pos: 0, dur: 0, par: null }));
    const out = mergeState({ fav: [], hist: [] }, { fav: [], hist }, NOW);
    expect(out.hist.length).toBe(200);
    expect(out.hist[0].r).toBe("249");
  });
});
