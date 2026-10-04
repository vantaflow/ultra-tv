import { describe, expect, it } from "vitest";
import { fmtDuration } from "./text";

describe("fmtDuration", () => {
  it("français", () => { expect(fmtDuration(65 * 60, "fr")).toMatch(/^1 h 05$|^1\s?h\s?05/); expect(fmtDuration(12 * 60, "fr")).toMatch(/12\s?min/); });
  it("arabe : aucune unité latine", () => { expect(fmtDuration(65 * 60, "ar")).not.toMatch(/[A-Za-z]/); expect(fmtDuration(12 * 60, "ar")).not.toMatch(/[A-Za-z]/); });
  it("vide si invalide", () => expect(fmtDuration(0, "fr")).toBe(""));
});
