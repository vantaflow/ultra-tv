import { describe, expect, it } from "vitest";
import { OTHER_LANG, canonicalLang, categoryLang, filterCategories, languageStats } from "./categoryLang";

// Noms FICTIFS imitant les motifs courants des fournisseurs.
describe("categoryLang", () => {
  it.each([
    ["FR| CINEMA", "FR"], ["|FR| SERIES", "FR"], ["FR - SPORT", "FR"], ["fr | docs", "FR"], ["[FR] Enfants", "FR"],
    ["FRA | Films 4K", "FR"], ["French Movies", "FR"], ["FRENCH | NEWS", "FR"], ["🇫🇷 Cinema", "FR"], ["Films VF", "FR"], ["Séries VOSTFR", "FR"],
    ["UK | Sports", "UK"], ["GB| News", "UK"], ["ENG | Kids", "UK"], ["English Series", "UK"], ["🇬🇧 Docs", "UK"],
    ["ES| Deportes", "ES"], ["SPA | Cine", "ES"], ["DE| Filme", "DE"], ["GER | Kino", "DE"], ["AR | Channels", "AR"], ["ARABIC | Series", "AR"],
    ["MULTI | Cinema", "MULTI"], ["Cartoons", OTHER_LANG], ["24/7 Marathons", OTHER_LANG], ["", OTHER_LANG],
  ])("%s -> %s", (n, c) => expect(categoryLang(n)).toBe(c));
  it("alias d'une même langue : un seul code", () => {
    const set = new Set(["FR|A", "|FR|B", "FR - C", "FRA | D", "French E", "🇫🇷 F", "VF G"].map(categoryLang));
    expect([...set]).toEqual(["FR"]);
  });
  it("canonicalLang", () => {
    expect(canonicalLang("français")).toBe("FR");
    expect(canonicalLang("xx")).toBeNull();
  });
});

const row = (name: string, kind = "live", enabled: 0 | 1 = 1) => ({ name, kind, enabled });
describe("languageStats / filterCategories", () => {
  const rows = [row("FR| A"), row("|FR| B", "movie", 0), row("French C", "series"), row("UK | D"), row("Zz"), row("Yy")];
  it("union dédoublonnée avec compteurs, OTHER en dernier, aucune coupe", () => {
    expect(languageStats(rows)).toEqual([
      { code: "FR", total: 3, on: 2 }, { code: "UK", total: 1, on: 1 }, { code: OTHER_LANG, total: 2, on: 2 },
    ]);
    const many = "FR UK ES DE IT PT NL TR AR BE CH CA US MA DZ TN SE NO PL RO GR".split(" ").map((c) => row(`${c}| x`));
    expect(languageStats(many)).toHaveLength(21);
  });
  it("filtre langue + type + recherche", () => {
    expect(filterCategories(rows, { lang: "FR", kind: null })).toHaveLength(3);
    expect(filterCategories(rows, { lang: "FR", kind: "movie" })).toHaveLength(1);
    expect(filterCategories(rows, { lang: null, kind: "live", match: (r) => r.name.includes("D") })).toHaveLength(1);
  });
});
