import { afterEach, describe, expect, it } from "vitest";
import { clockText, dayPeriods, formatHm, formatRange, parseTimeFormat, systemUses24, use24 } from "./clock";
import { dateTimeLabel, timeLabel, usePrefs } from "@/state/prefs";

const at = (h: number, m: number) => new Date(2024, 0, 1, h, m).getTime();

describe("formatHm : logique pure", () => {
  it("24 h", () => {
    expect(formatHm(16, 30, true)).toBe("16:30");
    expect(formatHm(0, 5, true)).toBe("00:05");
    expect(formatHm(9, 0, true)).toBe("09:00");
    expect(formatHm(23, 59, true)).toBe("23:59");
  });
  it("12 h : « 4:30 PM », minuit « 12:00 AM », midi « 12:00 PM »", () => {
    expect(formatHm(16, 30, false)).toBe("4:30 PM");
    expect(formatHm(0, 0, false)).toBe("12:00 AM");
    expect(formatHm(12, 0, false)).toBe("12:00 PM");
    expect(formatHm(0, 15, false)).toBe("12:15 AM");
    expect(formatHm(1, 5, false)).toBe("1:05 AM");
    expect(formatHm(23, 59, false)).toBe("11:59 PM");
  });
  it("mentions de la langue, chiffres latins", () => {
    expect(formatHm(16, 30, false, "ص", "م")).toBe("4:30 م");
    expect(formatHm(9, 5, false, "ص", "م")).toBe("9:05 ص");
    expect(formatHm(16, 30, true, "ص", "م")).toBe("16:30");
  });
  it("plages isolées en LTR", () => {
    expect(formatRange("16:00", "17:30")).toBe("⁦16:00 – 17:30⁩");
  });
});

describe("réglage", () => {
  it("valeur inconnue ou absente = Automatique", () => {
    for (const v of [undefined, null, "", "36", 12, {}, "AUTO"]) expect(parseTimeFormat(v)).toBe("auto");
    expect(parseTimeFormat("12")).toBe("12");
    expect(parseTimeFormat(" 24 ")).toBe("24");
  });
  it("Automatique suit la langue ; 12 / 24 l'emportent", () => {
    expect(systemUses24("fr-FR")).toBe(true);
    expect(systemUses24("en-US")).toBe(false);
    expect(use24("auto", "fr-FR")).toBe(true);
    expect(use24("auto", "en-US")).toBe(false);
    expect(use24("12", "fr-FR")).toBe(false);
    expect(use24("24", "en-US")).toBe(true);
  });
});

describe("clockText", () => {
  it("Automatique en français : inchangé (24 h)", () => {
    expect(clockText(at(16, 30), "auto", "fr-FR")).toBe("16:30");
    expect(clockText(at(9, 5), "auto", "fr-FR")).toBe("09:05");
  });
  it("12 h en anglais et en français", () => {
    expect(clockText(at(16, 30), "12", "en-US")).toBe("4:30 PM");
    expect(clockText(at(0, 0), "12", "en-US")).toBe("12:00 AM");
    expect(clockText(at(12, 0), "12", "en-US")).toBe("12:00 PM");
    expect(clockText(at(16, 30), "12", "fr-FR")).toMatch(/^4:30 \S+$/);
  });
  it("arabe : chiffres latins conservés, mention arabe en 12 h", () => {
    const s = clockText(at(16, 30), "12", "ar");
    expect(s).toMatch(/^4:30 /);
    expect(s).toMatch(/[؀-ۿ]/);
    expect(clockText(at(16, 30), "24", "ar")).toBe("16:30");
    expect(dayPeriods("ar")[0]).not.toBe("AM");
  });
});

describe("timeLabel (réglage de l'appareil)", () => {
  const before = usePrefs.getState().timeFormat;
  afterEach(() => usePrefs.getState().set({ timeFormat: before }));
  it("suit le réglage 12 / 24", () => {
    usePrefs.getState().set({ timeFormat: "24" });
    expect(timeLabel(at(16, 30), "en")).toBe("16:30");
    usePrefs.getState().set({ timeFormat: "12" });
    expect(timeLabel(at(16, 30), "en")).toBe("4:30 PM");
    expect(dateTimeLabel(at(16, 30), "en")).toMatch(/, 4:30 PM$/);
  });
});
