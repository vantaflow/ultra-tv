import { describe, expect, it } from "vitest";
import { cleanEpisodeTitle, cleanTitle, prettyCategoryName, presentable, tidyTitle } from "./titleCleaner";

const film = (s: string) => cleanTitle(s);
const tv = (s: string) => cleanTitle(s, true);

// Motifs observés sur un vrai catalogue (~360 titres), réécrits en titres génériques.
describe("films et séries", () => {
  it("préfixe langue et année", () => {
    const c = film("IN-EN - Sample Movie Name (2025)");
    expect(c.title).toBe("Sample Movie Name");
    expect(c.year).toBe(2025);
  });
  it("préfixe qualité-langue", () => expect(film("4K-ES - Sample Movie (2004)").title).toBe("Sample Movie"));
  it("préfixe simple", () => expect(film("IL - Another Film (2023)").title).toBe("Another Film"));
  it("préfixe avec plus", () => expect(film("4K-A+ - Some Show (2021)").title).toBe("Some Show"));
  it("préfixe en trois parties", () => expect(film("AR-ANM-S - Anime Title (2025) (JP)").title).toBe("Anime Title"));
  it("titre traduit après l'année ignoré", () => {
    const c = film("KU - Cartoon Pair in a Movie (2016) زینگو و رینگو");
    expect(c.title).toBe("Cartoon Pair in a Movie");
    expect(c.year).toBe(2016);
  });
  it("suffixe doublage", () => expect(film("IR - Ruthless (2023) بی رحم - دوبله").title).toBe("Ruthless"));
  it("sans année : garde le titre", () => {
    const c = film("IT - Sample Without Year");
    expect(c.title).toBe("Sample Without Year");
    expect(c.year).toBeNull();
  });
  it("pays entre parenthèses", () => {
    const c = film("IN - Sample Series (2024) (US)");
    expect(c.title).toBe("Sample Series");
    expect(c.year).toBe(2024);
  });
  it("pays seul sans année", () => expect(film("NF - Winx Like Saga (US)").title).toBe("Winx Like Saga"));
  it("titre arabe après préfixe", () => expect(film("4K-AR -  داون تاون").title).toBe("داون تاون"));
  it("apostrophe conservée", () => expect(film("DE - That's Amor  (2022)").title).toBe("That's Amor"));
  it("sans préfixe inchangé", () => expect(film("Casablanca (1942)").title).toBe("Casablanca"));
  it("court en majuscules : pas un préfixe", () => expect(film("WWE").title).toBe("WWE"));
  it("vide : retombe sur le brut", () => expect(film("###").title).toBe("###"));
});

describe("direct", () => {
  it("préfixe pays deux-points", () => expect(tv("US: NBC 5 PUEBLO CO (KOAA) HD").title).toBe("NBC 5 PUEBLO CO (KOAA)"));
  it("qualité extraite", () => expect(tv("ES: LA LIGA 1 HD").quality).toBe("HD"));
  it("lettres modificatrices", () => expect(tv("PL VIP: TRAVEL CHANNEL ᴿᴬᵂ").title).toBe("TRAVEL CHANNEL"));
  it("préfixe composé et décorations", () => expect(tv("IL: SAMPLE CHANNEL ᴴᴰ ◉").title).toBe("SAMPLE CHANNEL"));
  it("préfixe pipe", () => expect(tv("FR| Some Channel").title).toBe("Some Channel"));
  it("préfixe barres entourées", () => expect(tv("|AR| Some Channel").title).toBe("Some Channel"));
  it("préfixe crochets", () => expect(tv("[VIP] Some Channel").title).toBe("Some Channel"));
  it("préfixe parenthèses", () => expect(tv("(AU) ESPN PLAY 25 (D)").title).toBe("ESPN PLAY 25"));
  it("décoration dièses", () => expect(tv("### SAMPLE SERIES RAW ###").title).toBe("SAMPLE SERIES"));
  it("préfixe chiffres", () => expect(tv("24/7: SHE'S GOTTA HAVE IT").title).toBe("SHE'S GOTTA HAVE IT"));
  it("ligne d'événement : dernier segment", () =>
    expect(tv("End | Some Grand Prix | Sprint | 2026-07-04 | 10:30 (GMT) | 8K EXCLUSIVE | DK: VIAPLAY PPV 16").title).toBe("VIAPLAY PPV 16"));
  it("arabe et cyrillique", () => {
    expect(tv("AR: الكندوش").title).toBe("الكندوش");
    expect(tv("RU: Спорт ТВ HD").title).toBe("Спорт ТВ");
  });
  it("ne perd jamais le titre", () => expect(tv("US: TV").title).toBe("TV"));
});

describe("tidyTitle / presentable", () => {
  it("ne montre jamais None", () => {
    expect(tidyTitle("PREDICTION.None")).toBe("Prediction");
    expect(tidyTitle("ONGUENNE.None")).toBe("Onguenne");
    expect(tidyTitle("IRRATIONAL.LOVE.None")).toBe("Irrational Love");
    expect(tidyTitle("Sample null")).toBe("Sample");
  });
  it("majuscules -> capitalisé", () => expect(tidyTitle("SUGAR DADDY")).toBe("Sugar Daddy"));
  it("mixte inchangé", () => expect(tidyTitle("That's Amor")).toBe("That's Amor"));
  it("idempotent", () => expect(tidyTitle(tidyTitle("SUGAR DADDY"))).toBe(tidyTitle("SUGAR DADDY")));
  it("préfixe langue et None", () => expect(cleanTitle("AF-FR - PREDICTION.None").title).toBe("Prediction"));
  it("année en points", () => {
    const c = cleanTitle("AF-FR - Some.Movie.2021");
    expect(c.title).toBe("Some Movie");
    expect(c.year).toBe(2021);
  });
  it("masque les absents", () => {
    for (const v of [null, "None", "null", "0", "  "]) expect(presentable(v)).toBeNull();
    expect(presentable(" Drame ")).toBe("Drame");
  });
});

describe("prettyCategoryName", () => {
  it("retire le séparateur final", () => {
    expect(prettyCategoryName("AFRICA /")).toBe("AFRICA");
    expect(prettyCategoryName("CANAL+ /")).toBe("CANAL+");
    expect(prettyCategoryName("AFRICA/")).toBe("AFRICA");
  });
  it("décorations", () => {
    expect(prettyCategoryName("RELAX ☼")).toBe("RELAX");
    expect(prettyCategoryName("☼ RELAX ☼ /")).toBe("RELAX");
  });
  it("inchangé / brut", () => {
    expect(prettyCategoryName("Sport FR")).toBe("Sport FR");
    expect(prettyCategoryName("###")).toBe("###");
  });
});

describe("cleanEpisodeTitle", () => {
  it("retire série et code d'épisode", () => {
    expect(cleanEpisodeTitle("AR-SUBS - Some Show (2024) (US) - S01E01 - Enter Sandman (1)")).toBe("Enter Sandman (1)");
    expect(cleanEpisodeTitle("Épisode 4")).toBe("Épisode 4");
  });
});
