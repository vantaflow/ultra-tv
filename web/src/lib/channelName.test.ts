import { describe, expect, it } from "vitest";
import {
  F_BACKUP, F_HEVC, F_RAW, Q_4K, Q_FHD, Q_HD, Q_NONE, Q_SD,
  parseCategoryName, parseChannelName as p,
} from "./channelName";

// Motifs mesurés sur un vrai catalogue (~54 000 chaînes), réécrits en exemples synthétiques.
describe("séparateurs", () => {
  it("dièses, 4K et exposants normalisés", () => {
    const r = p("##### 4K ᵁᴴᴰ ³⁸⁴⁰ᴾ #####");
    expect(r.isSeparator).toBe(true);
    expect(r.displayName).toBe("4K UHD 3840P");
  });
  it("pays en majuscules", () => {
    const r = p("###### AZERBAIJAN ######");
    expect(r.isSeparator).toBe(true);
    expect(r.displayName).toBe("AZERBAIJAN");
  });
  it("news hevc", () => {
    const r = p("### UK NEWS HEVC/HD ###");
    expect(r.isSeparator).toBe(true);
    expect(r.displayName.startsWith("UK NEWS")).toBe(true);
  });
  it.each(["-----", "=====", "★★★", "▬▬▬▬", "·····", "• • •"])("%s est un séparateur", (s) => {
    expect(p(s).isSeparator).toBe(true);
  });
  it.each(["BBC One", "US: CNN HD", "13EME RUE", "24/7 CARTOON", "Canal- Plus"])("%s n'est pas un séparateur", (s) => {
    expect(p(s).isSeparator).toBe(false);
  });
});

describe("pays", () => {
  it("préfixe deux-points", () => {
    const r = p("US: NBC 5 HD");
    expect(r.country).toBe("US");
    expect(r.displayName).toBe("NBC 5");
  });
  it("préfixe barre", () => {
    const r = p("AR | Channel Name");
    expect(r.country).toBe("AR");
    expect(r.displayName).toBe("Channel Name");
  });
  it("composé BE-VIP", () => {
    const r = p("BE-VIP: Sport Channel");
    expect(r.country).toBe("BE");
    expect(r.displayName).toBe("Sport Channel");
  });
  it("alias UK", () => expect(p("UK: Sky Example").country).toBe("UK"));
  it.each(["TV: Some Channel", "GOLD: Some Channel", "VIP: Some Channel", "4K: Some Channel"])("%s n'a pas de pays", (s) => {
    expect(p(s).country).toBeNull();
  });
  it("étiquette retirée du nom", () => {
    expect(p("GOLD: Some Channel").displayName).toBe("Some Channel");
    expect(p("PRIME: Some Channel").displayName).toBe("Some Channel");
  });
  it("parenthèses", () => {
    const r = p("(AU) ESPN PLAY");
    expect(r.country).toBe("AU");
    expect(r.displayName).toBe("ESPN PLAY");
  });
  it("crochets et préfixes multiples", () => {
    const r = p("[VIP] FR| Channel One");
    expect(r.country).toBe("FR");
    expect(r.displayName).toBe("Channel One");
  });
  it("sans préfixe : inchangé", () => {
    const r = p("Canal Plus Sport");
    expect(r.country).toBeNull();
    expect(r.displayName).toBe("Canal Plus Sport");
  });
  it("un titre avec deux-points n'est pas un préfixe", () => {
    expect(p("Match du jour: Lyon contre Nice").displayName).toBe("Match du jour: Lyon contre Nice");
  });
});

describe("qualité", () => {
  it.each(["Chan 4K", "Chan UHD", "Chan 2160p", "Chan 3840P"])("%s -> 4K", (s) => expect(p(s).quality).toBe(Q_4K));
  it("FHD / 1080", () => {
    expect(p("Chan FHD").quality).toBe(Q_FHD);
    expect(p("Chan 1080p").quality).toBe(Q_FHD);
  });
  it("HD / 720", () => {
    expect(p("Chan HD").quality).toBe(Q_HD);
    expect(p("Chan 720P").quality).toBe(Q_HD);
  });
  it("SD / LQ", () => {
    expect(p("Chan SD").quality).toBe(Q_SD);
    expect(p("Chan LQ").quality).toBe(Q_SD);
  });
  it("aucune", () => expect(p("Plain Channel").quality).toBe(Q_NONE));
  it("8K est une étiquette, pas une définition", () => {
    const r = p("8K: Some Sports");
    expect(r.quality).toBe(Q_NONE);
    expect(r.displayName).toBe("Some Sports");
  });
  it("la plus haute gagne", () => expect(p("Chan HD UHD").quality).toBe(Q_4K));
  it("exposants normalisés", () => expect(p("Some Channel ᴴᴰ").quality).toBe(Q_HD));
});

describe("drapeaux et noms", () => {
  it("drapeaux", () => {
    const r = p("Chan RAW HEVC BACKUP");
    expect(r.flags & F_RAW).not.toBe(0);
    expect(r.flags & F_HEVC).not.toBe(0);
    expect(r.flags & F_BACKUP).not.toBe(0);
    expect(r.displayName).toBe("Chan");
  });
  it("drapeaux en exposants", () => expect(p("Chan ᴿᴬᵂ").flags & F_RAW).not.toBe(0));
  it("arabe et cyrillique inchangés", () => {
    expect(p("AR: الكندوش").displayName).toBe("الكندوش");
    expect(p("RU: Спорт ТВ HD").displayName).toBe("Спорт ТВ");
  });
  it("jamais vide", () => expect(p("US: HD").displayName.trim()).not.toBe(""));
  it("chiffres conservés", () => expect(p("UK: SKY SPORTS 1 FHD").displayName).toBe("SKY SPORTS 1"));
});

describe("catégories", () => {
  it("préfixe pays -> badge", () => {
    const c = parseCategoryName("FR | SPORT HD");
    expect(c.badge).toBe("FR");
    expect(c.label).toBe("SPORT");
  });
  it("région -> badge", () => {
    const c = parseCategoryName("AFRI SPORT");
    expect(c.badge).toBe("AFR");
    expect(c.label).toBe("SPORT");
  });
});
