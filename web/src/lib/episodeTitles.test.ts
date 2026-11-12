import { describe, expect, it } from "vitest";
import { TMDB_STILL, isGenericEpisodeTitle, mergeEpisode, mergeEpisodes, parseTmdbSeason } from "./episodeTitles";

describe("isGenericEpisodeTitle", () => {
  it.each([
    "", "   ", "Episode 3", "episode 03", "Épisode 3", "ÉPISODE 03", "Ep. 3", "Ep 3", "EP3", "E03", "e3", "S01E03", "s1 e3",
    "S01 E03", "1x03", "Season 1 Episode 3", "Saison 1 - Épisode 3", "Capítulo 3", "Capitulo 03", "Episodio 3",
    "Temporada 1 Capítulo 3", "Odcinek 3", "Odc. 3", "Sezon 2 Odcinek 3", "Folge 3", "3", "03", "#3", "- 3 -",
    "حلقة 3", "الحلقة 3", "الحلقه ٣", "الحلقة رقم 12", "الموسم 1 الحلقة 3", "Episode 3 ", "(Episode 3)",
  ])("générique : %j", (title) => expect(isGenericEpisodeTitle(title, "Brume", 3)).toBe(true));

  it.each([
    "Pilot", "Le col", "The Rains of Castamere", "Ozymandias", "Cap sur l'île", "Episode final", "Epilogue", "La relève",
    "Le dernier épisode", "حلقة الوداع", "Ostatni odcinek", "Capítulo final", "3 Days", "E.T. revient",
  ])("spécifique : %j", (title) => expect(isGenericEpisodeTitle(title, "Brume", 3)).toBe(false));

  it("nom de la série, seul ou suivi d'un numéro / code", () => {
    expect(isGenericEpisodeTitle("Brume", "Brume", 3)).toBe(true);
    expect(isGenericEpisodeTitle("brume", "Brume", 3)).toBe(true);
    expect(isGenericEpisodeTitle("Brume 3", "Brume", 3)).toBe(true);
    expect(isGenericEpisodeTitle("Brume - S01E03", "Brume", 3)).toBe(true);
    expect(isGenericEpisodeTitle("Brume : Épisode 3", "Brume", 3)).toBe(true);
    expect(isGenericEpisodeTitle("Brume - Le col", "Brume", 3)).toBe(false);
    expect(isGenericEpisodeTitle("Les Héritiers", "Les Heritiers", 1)).toBe(true);
  });

  it("sans nom de série ni numéro", () => {
    expect(isGenericEpisodeTitle(null)).toBe(true);
    expect(isGenericEpisodeTitle("Episode 7")).toBe(true);
    expect(isGenericEpisodeTitle("Pilot")).toBe(false);
  });
});

describe("parseTmdbSeason", () => {
  it("lit les épisodes et ignore les entrées invalides", () => {
    const eps = parseTmdbSeason({ episodes: [
      { episode_number: 1, name: "Pilote", overview: "Résumé", still_path: "/a1.jpg", air_date: "2020-01-01", runtime: 42 },
      { episode_number: 2, name: " ", overview: "", still_path: null },
      { episode_number: "3", name: "X" },
      { episode_number: 4, name: "Y", still_path: "javascript:alert(1)" },
      null,
    ] });
    expect(eps).toEqual([
      { number: 1, name: "Pilote", overview: "Résumé", still: `${TMDB_STILL}/a1.jpg` },
      { number: 2, name: null, overview: null, still: null },
      { number: 4, name: "Y", overview: null, still: null },
    ]);
  });
  it("corps inattendu : liste vide", () => {
    expect(parseTmdbSeason(null)).toEqual([]);
    expect(parseTmdbSeason({ episodes: "x" })).toEqual([]);
  });
});

describe("mergeEpisode(s)", () => {
  const tm = { number: 3, name: "Le col", overview: "Ils passent le col.", still: `${TMDB_STILL}/s.jpg` };
  const ep = (title: string, plot: string | null = null, img: string | null = null) => ({ id: 30, num: 3, title, plot, img });

  it("remplace un titre générique et complète résumé et vignette absents", () => {
    expect(mergeEpisode(ep("Episode 3"), tm, "Brume")).toEqual({ id: 30, num: 3, title: "Le col", plot: "Ils passent le col.", img: `${TMDB_STILL}/s.jpg` });
  });
  it("garde le titre, le résumé et la vignette de la source s'ils existent", () => {
    const e = ep("Titre de la source", "Résumé source", "https://x/img.jpg");
    expect(mergeEpisode(e, tm, "Brume")).toBe(e);
  });
  it("garde le titre de la source si celui de TMDB est lui aussi générique", () => {
    expect(mergeEpisode(ep("E03"), { ...tm, name: "Épisode 3" }, "Brume").title).toBe("E03");
  });
  it("sans épisode TMDB : inchangé", () => {
    const e = ep("Episode 3");
    expect(mergeEpisode(e, undefined)).toBe(e);
  });
  it("liste : par numéro, même référence si rien ne change", () => {
    const list = [{ id: 1, num: 1, title: "Pilot", plot: "p", img: "i" }, ep("S01E03")];
    const out = mergeEpisodes(list, [tm], "Brume");
    expect(out[0]).toBe(list[0]);
    expect(out[1]!.title).toBe("Le col");
    expect(mergeEpisodes(list, null)).toBe(list);
    expect(mergeEpisodes([list[0]!], [tm])).toEqual([list[0]]);
  });
});
