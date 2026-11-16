import { describe, expect, it } from "vitest";
import { parseVtt, shiftCues, vttTime } from "./subtitles";

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
});
