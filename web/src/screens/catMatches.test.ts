import { describe, expect, it } from "vitest";
import { catMatches } from "./Settings";

describe("catMatches", () => {
  it("filtre court = mot entier", () => {
    expect(catMatches("FR| SPORT", "fr")).toBe(true);
    expect(catMatches("AFRICA SPORT", "fr")).toBe(false);
  });
  it("filtre long = sous-chaîne", () => expect(catMatches("AFRICA SPORT", "afric")).toBe(true));
});
