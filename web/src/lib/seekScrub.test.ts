import { describe, expect, it } from "vitest";
import { clampTarget, fmtDelta, press, release, shouldCommit, stepSeconds, type ScrubState } from "./seekScrub";

const D = 7200; // 2 h : plafond 2 % = 144 s, ne gêne pas

describe("seekScrub : pas", () => {
  it("accélère avec la durée d'appui", () => {
    expect([0, 999, 1000, 2499, 2500, 3999, 4000, 9000].map((h) => stepSeconds(h, D))).toEqual([10, 10, 30, 30, 60, 60, 120, 120]);
  });
  it("plafonné à 2 % de la durée, au moins 10 s", () => {
    expect(stepSeconds(5000, 3000)).toBe(60);
    expect(stepSeconds(5000, 2000)).toBe(40);
    expect(stepSeconds(5000, 300)).toBe(10);
    expect(stepSeconds(5000, 0)).toBe(120);
  });
});

describe("seekScrub : cible", () => {
  it("un appui = ±10 s depuis la position", () => {
    expect(press(null, 1, 0, 100, D, false).target).toBe(110);
    expect(press(null, -1, 0, 100, D, false).target).toBe(90);
  });
  it("bornes [0, durée − 1]", () => {
    expect(press(null, -1, 0, 4, D, false).target).toBe(0);
    expect(press(null, 1, 0, D - 3, D, false).target).toBe(D - 1);
    expect(clampTarget(99999, D)).toBe(D - 1);
  });
  it("appuis rapides (< 600 ms) : cumul ; au-delà : nouveau défilement depuis la position", () => {
    let s = press(null, 1, 0, 100, D, false);
    s = press(s, 1, 500, 100, D, false);
    s = press(s, 1, 1000, 100, D, false);
    expect(s.target).toBe(130);
    expect(s.origin).toBe(100);
    const n = press(s, 1, 1700, 105, D, false);
    expect(n.origin).toBe(105);
    expect(n.target).toBe(115);
  });
  it("touche maintenue : le pas grandit, à cadence limitée", () => {
    let s = press(null, 1, 0, 0, D, false);
    expect(s.target).toBe(10);
    for (let t = 33; t <= 4500; t += 33) s = press(s, 1, t, 0, D, true);
    expect(s.pressStartAt).toBe(0);
    expect(s.target).toBeGreaterThan(400);
    // au-delà de 4 s d'appui : un pas de 120 s
    const before = s.target;
    s = press(s, 1, s.lastStepAt + 250, 0, D, true);
    expect(s.target - before).toBe(120);
  });
  it("répétition plus rapide que le tick : cible inchangée, délai de validation prolongé", () => {
    const s = press(null, 1, 0, 0, D, false);
    const r = press(s, 1, 100, 0, D, true);
    expect(r.target).toBe(s.target);
    expect(r.lastPressAt).toBe(100);
  });
});

describe("seekScrub : validation et annulation", () => {
  it("valide 700 ms après le dernier appui ou relâchement", () => {
    const s = press(null, 1, 0, 0, D, false);
    expect(shouldCommit(s, 699)).toBe(false);
    expect(shouldCommit(s, 700)).toBe(true);
    const r: ScrubState = release(s, 3000);
    expect(shouldCommit(r, 3500)).toBe(false);
    expect(shouldCommit(r, 3700)).toBe(true);
  });
  it("annulation : l'origine reste la position d'avant le défilement", () => {
    let s = press(null, 1, 0, 250, D, false);
    s = press(s, 1, 100, 250, D, false);
    expect(s.origin).toBe(250);
    expect(s.target).toBe(270);
  });
});

describe("seekScrub : écart affiché", () => {
  it("signe et format", () => {
    expect(fmtDelta(150)).toBe("+2:30");
    expect(fmtDelta(-40)).toBe("−0:40");
    expect(fmtDelta(3725)).toBe("+1:02:05");
    expect(fmtDelta(-3600)).toBe("−1:00:00");
    expect(fmtDelta(0)).toBe("+0:00");
  });
});
