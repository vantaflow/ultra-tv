import { describe, expect, it } from "vitest";
import { flattenOverlaps, fmtShiftMs, nudgeShift, parseVtt, shiftCues, vttTime } from "./subtitles";

describe("sous-titres WebVTT (bureau)", () => {
  it("temps avec et sans heures", () => {
    expect(vttTime("00:01:02.500")).toBe(62.5);
    expect(vttTime("01:02.5")).toBe(62.5);
    expect(Number.isNaN(vttTime("abc"))).toBe(true);
  });
  it("repères, identifiants et réglages de ligne ignorés", () => {
    const v = "WEBVTT\n\n1\n00:00:01.000 --> 00:00:03.000 align:start\nBonjour\nle monde\n\n00:00:05.000 --> 00:00:04.000\nignoré\n\nNOTE x\n\n00:01:00.000 --> 00:01:02.000\n<i>Fin</i>\n";
    expect(parseVtt(v)).toEqual([{ start: 1, end: 3, text: "Bonjour\nle monde" }, { start: 60, end: 62, text: "<i>Fin</i>" }]);
  });
  it("décalage du départ de la conversion", () => {
    const c = [{ start: 1, end: 3, text: "a" }, { start: 60, end: 62, text: "b" }];
    expect(shiftCues(c, 0)).toEqual(c);
    expect(shiftCues(c, 59)).toEqual([{ start: 1, end: 3, text: "b" }]);
  });

  it("aplatit les chevauchements de repères", () => {
    const chevauche = [{ start: 10, end: 13, text: "première" }, { start: 12, end: 14, text: "deuxième" }];
    expect(flattenOverlaps(chevauche)).toEqual([{ start: 10, end: 11.975, text: "première" }, { start: 12, end: 14, text: "deuxième" }]);
    // Un repère avalé au suivant est raccourci avant lui, pas empilé.
    expect(flattenOverlaps([{ start: 10, end: 11.5, text: "avalée" }, { start: 10.2, end: 12, text: "garde" }, { start: 20, end: 21, text: "après" }]))
      .toEqual([{ start: 10, end: 10.2 - 0.025, text: "avalée" }, { start: 10.2, end: 12, text: "garde" }, { start: 20, end: 21, text: "après" }]);
    // Sans chevauchement important : inchangé (le léger battement ne coupe rien).
    const ok = [{ start: 1, end: 2, text: "a" }, { start: 2, end: 3, text: "b" }];
    expect(flattenOverlaps(ok)).toEqual(ok);
  });

  it("borde le décalage manuel (±10 s, pas de 250 ms)", () => {
    expect(nudgeShift(0, 0.25)).toBe(0.25);
    expect(nudgeShift(0, -0.25)).toBe(-0.25);
    expect(nudgeShift(0.1, 0.25)).toBe(0.25);
    expect(nudgeShift(10, 0.25)).toBe(10);
    expect(nudgeShift(-10, -0.25)).toBe(-10);
    expect(fmtShiftMs(0)).toBe("0 ms");
    expect(fmtShiftMs(0.25)).toBe("+250 ms");
    expect(fmtShiftMs(-0.5)).toBe("-500 ms");
  });
});
