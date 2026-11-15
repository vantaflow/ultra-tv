import { describe, expect, it } from "vitest";
import { keepEvent, INFO_SAMPLE } from "../src/telemetry.js";

describe("tri de la télémétrie", () => {
  const e = (o) => ({ deviceId: "d" + Math.random(), tag: "t", message: "m", ...o });
  it("garde toujours plantages, avertissements et erreurs", () => {
    expect(keepEvent("crash", e({}), () => 0.99)).toBe(true);
    expect(keepEvent("event", e({ level: "warn" }), () => 0.99)).toBe(true);
    expect(keepEvent("event", e({ level: "error" }), () => 0.99)).toBe(true);
  });
  it("échantillonne les lignes d'info (1 sur INFO_SAMPLE)", () => {
    expect(keepEvent("event", e({ level: "info" }), () => 0.99)).toBe(false);
    expect(keepEvent("event", e({ level: "info" }), () => 0.5 / INFO_SAMPLE)).toBe(true);
  });
  it("une même ligne d'un même appareil n'est gardée qu'une fois par 30 min", () => {
    const x = { deviceId: "dup-1", tag: "play", message: "boom", level: "error" };
    expect(keepEvent("event", x, () => 0, 1_000_000)).toBe(true);
    expect(keepEvent("event", x, () => 0, 1_000_000 + 60_000)).toBe(false);
    expect(keepEvent("event", x, () => 0, 1_000_000 + 31 * 60_000)).toBe(true);
  });
});
