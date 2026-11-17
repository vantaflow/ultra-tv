import { describe, expect, it } from "vitest";
import { isIgnoredFile, isIgnoredFolder, isVideoName, nasSeriesKey, parseNasName } from "./nasNames";

// Mêmes cas que android-native/.../data/nas/NasNamesTest.kt : les deux implémentations doivent rester identiques.

function movie(name: string, title: string, year: number | null, folders: string[] = []) {
  const p = parseNasName(name, folders);
  expect({ kind: p.kind, title: p.title, year: p.year }, name).toEqual({ kind: "movie", title, year });
}

function episode(name: string, series: string, season: number, ep: number, opts: { title?: string; year?: number | null; folders?: string[] } = {}) {
  const p = parseNasName(name, opts.folders ?? []);
  expect(
    { kind: p.kind, series: p.seriesTitle, season: p.season, ep: p.episode, title: p.title, year: p.year },
    name,
  ).toEqual({ kind: "episode", series, season, ep, title: opts.title ?? "", year: opts.year ?? null });
}

describe("films", () => {
  it("titre et année entre parenthèses", () => movie("Titre (2019).mkv", "Titre", 2019));
  it("points, année et mots de release", () => movie("Titre.2019.1080p.x264.mkv", "Titre", 2019));
  it("mots de release sans année", () => movie("Le.Grand.Film.1080p.BluRay.x265-GRP.mkv", "Le Grand Film", null));
  it("WEB-DL et audio", () => movie("Mon.Film.2021.MULTi.1080p.WEB-DL.DDP5.1.H.264-FOO.mkv", "Mon Film", 2021));
  it("tirets internes conservés", () => movie("Spider-Man.No.Way.Home.2021.2160p.mkv", "Spider-Man No Way Home", 2021));
  it("titre contenant une année", () => movie("Blade Runner 2049 (2017).mp4", "Blade Runner 2049", 2017));
  it("titre qui est une année", () => {
    movie("1917.2019.1080p.mkv", "1917", 2019);
    movie("2012 (2009).avi", "2012", 2009);
    movie("1917.mkv", "1917", null);
  });
  it("crochets retirés", () => movie("[Groupe] Titre (2018) [1080p] [FR].mkv", "Titre", 2018));
  it("tirets bas et espaces", () => movie("Mon_Film_2015_720p.mkv", "Mon Film", 2015));
  it("mot du titre identique à un mot de release", () => {
    movie("The.French.Dispatch.2021.1080p.mkv", "The French Dispatch", 2021);
    movie("Charlotte's Web (2006).mkv", "Charlotte's Web", 2006);
  });
  it("accents conservés", () => movie("Amélie (2001) 1080p.mkv", "Amélie", 2001));
  it("numéro de disque retiré", () => movie("Titre (2005) CD1.avi", "Titre", 2005));
  it("titre dans le dossier quand le nom est générique", () => {
    movie("movie.mkv", "Titre", 2019, ["Films", "Titre (2019)"]);
    movie("video.mp4", "Mon Film", 2014, ["Mon.Film.2014.1080p"]);
  });
  it("année du dossier quand le nom est le même titre", () => movie("Titre.mkv", "Titre", 2016, ["Titre (2016)"]));
  it("année du dossier ignorée si le titre diffère", () => movie("Autre chose.mkv", "Autre chose", null, ["Titre (2016)"]));
  it("sans extension vidéo reconnue", () => movie("Titre (2019)", "Titre", 2019));
  it("majuscules et chiffre final", () => movie("TITRE 2 (2012).mkv", "TITRE 2", 2012));
  it("une résolution n'est pas une marque d'épisode", () => movie("Titre 1920x1080 (2019).mkv", "Titre 1920x1080", 2019));
});

describe("épisodes", () => {
  it("S01E02", () => episode("Série S01E02.mkv", "Série", 1, 2));
  it("points, minuscules et titre", () => episode("Show.Name.s02e10.The.Title.720p.HDTV.x264-GRP.mkv", "Show Name", 2, 10, { title: "The Title" }));
  it("forme 1x02", () => episode("Série 1x02.avi", "Série", 1, 2));
  it("1x02 avec titre", () => episode("Série - 3x07 - Le titre.mkv", "Série", 3, 7, { title: "Le titre" }));
  it("season episode en toutes lettres", () => episode("Série Season 2 Episode 5.mkv", "Série", 2, 5));
  it("saison française", () => episode("Série Saison 1 Episode 12.mkv", "Série", 1, 12));
  it("année de la série", () => episode("Série (2019) S01E02.mkv", "Série", 1, 2, { year: 2019 }));
  it("points, année et marque", () => episode("Show.Name.2019.S03E04.1080p.mkv", "Show Name", 3, 4, { year: 2019 }));
  it("double épisode : le premier", () => episode("Série S01E01E02.mkv", "Série", 1, 1));
  it("sans titre de série : le dossier le donne", () => episode("S01E03.mkv", "Ma Série", 1, 3, { folders: ["Ma Série", "Saison 1"] }));
  it("dossier saison, numéro en tête", () => episode("02 - Le titre.mkv", "Série", 3, 2, { title: "Le titre", folders: ["Série", "Saison 3"] }));
  it("dossier Season avec E05", () => episode("E05.mkv", "Show", 2, 5, { year: 2012, folders: ["Show (2012)", "Season 02"] }));
  it("dossier abrégé S1", () => episode("07.mp4", "Série", 1, 7, { folders: ["Série", "S1"] }));
  it("marque collée", () => episode("serie.s01e02.mkv", "serie", 1, 2));
});

describe("fichiers et dossiers ignorés", () => {
  it("extensions vidéo", () => {
    expect(isVideoName("a.MKV")).toBe(true);
    expect(isVideoName("a.m2ts")).toBe(true);
    expect(isVideoName("a.srt")).toBe(false);
    expect(isVideoName("a.jpg")).toBe(false);
    expect(isVideoName("mkv")).toBe(false);
  });
  it("fichiers ignorés", () => {
    for (const n of ["sample.mkv", "Titre-sample.mkv", "Titre.Sample.mp4", "Film-trailer.mp4", "._Titre.mkv"]) expect(isIgnoredFile(n), n).toBe(true);
    for (const n of ["Samplers (2015).mkv", "Titre (2019).mkv"]) expect(isIgnoredFile(n), n).toBe(false);
  });
  it("dossiers ignorés", () => {
    for (const n of ["@eaDir", "#recycle", "$RECYCLE.BIN", ".Trash", "Extras"]) expect(isIgnoredFolder(n), n).toBe(true);
    expect(isIgnoredFolder("Films")).toBe(false);
  });
  it("clé de série : sans accents, ponctuation ni casse", () => {
    expect(nasSeriesKey("La Casa de Papel")).toBe(nasSeriesKey("la.casa.de.papel"));
    expect(nasSeriesKey("Élite")).toBe(nasSeriesKey("elite"));
  });
});
