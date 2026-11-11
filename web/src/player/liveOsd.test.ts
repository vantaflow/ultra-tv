import { describe, expect, it } from "vitest";
import { appendDigit, backEffect, decide, keyOf, MAX_RECENTS, navigate, pushRecent, recentsToShow, type RecentChannel } from "./liveOsd";

const closed = (key: Parameters<typeof decide>[0], o: { hidden?: boolean; ts?: boolean } = {}) => decide(key, false, o.hidden ?? false, o.ts ?? false);
const ch = (id: number): RecentChannel => ({ streamId: id, num: id, name: `C${id}`, logo: null, ord: id, catExt: null, epg: null, archive: false, q: 0 });

describe("liveOsd : touches avec l'OSD fermé", () => {
  it("OK ouvre la liste des chaînes", () => expect(closed("ok")).toEqual({ type: "openChannels" }));
  it("Haut, Bas, Menu, Info ouvrent l'OSD complet", () => {
    for (const k of ["up", "down", "menu", "info"] as const) expect(closed(k)).toEqual({ type: "openOsd" });
  });
  it("Gauche / Droite : chaîne précédente / suivante, OSD non refermé", () => {
    expect(closed("left")).toEqual({ type: "zap", forward: false, closeOsd: false });
    expect(closed("right")).toEqual({ type: "zap", forward: true, closeOsd: false });
  });
  it("différé actif et surcouche masquée : ±30 s", () => {
    expect(closed("left", { ts: true, hidden: true })).toEqual({ type: "timeshiftJump", seconds: -30 });
    expect(closed("right", { ts: true, hidden: true })).toEqual({ type: "timeshiftJump", seconds: 30 });
  });
  it("différé actif avec bandeau visible : ouvre l'OSD", () => {
    expect(closed("left", { ts: true })).toEqual({ type: "openOsd" });
    expect(closed("right", { ts: true })).toEqual({ type: "openOsd" });
  });
  it("autre touche : absorbée", () => expect(closed("other")).toEqual({ type: "consume" }));
});

describe("liveOsd : touches valables partout", () => {
  it("chaîne +/- ferme l'OSD en zappant, même OSD ouvert", () => {
    expect(decide("channelUp", true, false, false)).toEqual({ type: "zap", forward: false, closeOsd: true });
    expect(decide("channelDown", false, true, false)).toEqual({ type: "zap", forward: true, closeOsd: true });
  });
  it("guide ouvre les chaînes, dernière chaîne rappelle la précédente", () => {
    expect(decide("guide", true, false, false)).toEqual({ type: "openChannels" });
    expect(decide("lastChannel", false, false, false)).toEqual({ type: "recallPrevious" });
  });
  it("OSD ouvert : les flèches et OK passent à la navigation du focus", () => {
    for (const k of ["ok", "up", "down", "left", "right"] as const) expect(decide(k, true, false, false)).toEqual({ type: "pass" });
  });
  it("panneau d'erreur : tout passe, même Chaîne +", () => {
    expect(decide("channelUp", false, false, false, true)).toEqual({ type: "pass" });
    expect(decide("ok", false, true, false, true)).toEqual({ type: "pass" });
  });
});

describe("liveOsd : keyOf", () => {
  it("convertit les touches du clavier", () => {
    expect(keyOf("Enter")).toBe("ok");
    expect(keyOf("ArrowLeft")).toBe("left");
    expect(keyOf("PageUp")).toBe("channelUp");
    expect(keyOf("PageDown")).toBe("channelDown");
    expect(keyOf("Backspace")).toBe("lastChannel");
    expect(keyOf("x")).toBe("other");
  });
});

describe("liveOsd : navigation dans l'OSD ouvert", () => {
  it("sans focus dans l'OSD : la barre prend le focus", () => {
    expect(navigate("right", null, true)).toEqual({ type: "focusStage", stage: "controls" });
  });
  it("Haut depuis la barre : récents s'il y en a, sinon rien", () => {
    expect(navigate("up", "controls", true)).toEqual({ type: "focusStage", stage: "recents" });
    expect(navigate("up", "controls", false)).toEqual({ type: "none" });
  });
  it("Bas depuis les récents : barre ; Bas depuis la barre : rien", () => {
    expect(navigate("down", "recents", true)).toEqual({ type: "focusStage", stage: "controls" });
    expect(navigate("down", "controls", true)).toEqual({ type: "none" });
  });
  it("Gauche / Droite : bouton précédent / suivant, inversé en RTL", () => {
    expect(navigate("right", "controls", false)).toEqual({ type: "step", delta: 1 });
    expect(navigate("left", "recents", true)).toEqual({ type: "step", delta: -1 });
    expect(navigate("right", "controls", false, true)).toEqual({ type: "step", delta: -1 });
  });
  it("touche non directionnelle : rien", () => expect(navigate("ok", "controls", true)).toEqual({ type: "none" }));
});

describe("liveOsd : Échap", () => {
  it("récents → barre → fermé → quitter", () => {
    expect(backEffect(true, "recents", true)).toBe("focusControls");
    expect(backEffect(true, "controls", true)).toBe("closeOsd");
    expect(backEffect(false, "controls", true)).toBe("notOsd");
  });
  it("sans image (ne joue pas) : quitte directement", () => expect(backEffect(true, "controls", false)).toBe("notOsd"));
});

describe("liveOsd : chaînes récentes", () => {
  it("pushRecent : remonte sans doublon et borne la liste", () => {
    let l: RecentChannel[] = [];
    for (let i = 1; i <= 15; i++) l = pushRecent(l, ch(i));
    expect(l).toHaveLength(MAX_RECENTS + 1);
    expect(l[0]!.streamId).toBe(15);
    l = pushRecent(l, ch(10));
    expect(l.map((c) => c.streamId).slice(0, 3)).toEqual([10, 15, 14]);
    expect(l.filter((c) => c.streamId === 10)).toHaveLength(1);
  });
  it("recentsToShow : sans la chaîne en cours, au plus 10", () => {
    let l: RecentChannel[] = [];
    for (let i = 1; i <= 15; i++) l = pushRecent(l, ch(i));
    const r = recentsToShow(l, 15);
    expect(r).toHaveLength(MAX_RECENTS);
    expect(r[0]!.streamId).toBe(14);
    expect(r.some((c) => c.streamId === 15)).toBe(false);
  });
});

describe("liveOsd : saisie d'un numéro", () => {
  it("chiffres seulement, plafonné à 5", () => {
    expect(appendDigit("", "4")).toBe("4");
    expect(appendDigit("4", "2")).toBe("42");
    expect(appendDigit("12345", "6")).toBe("23456");
    expect(appendDigit("4", "a")).toBeNull();
    expect(appendDigit("4", "12")).toBeNull();
  });
});
