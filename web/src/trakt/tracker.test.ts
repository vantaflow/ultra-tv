import { describe, expect, it } from "vitest";
import { installMemStorage } from "./memstorage";
installMemStorage();
import { normalizeTracking, normalizeTraktLibrary } from "@/cloud/client";
import { forTracker } from "./tracker";

describe("service de suivi", () => {
  it("forTracker : Trakt inchangé, Simkl remplace le nom", () => {
    expect(forTracker("Add to my Trakt watchlist")).toBe("Add to my Trakt watchlist");
    expect(forTracker("Add to my Trakt watchlist", "trakt")).toBe("Add to my Trakt watchlist");
    expect(forTracker("Add to my Trakt watchlist", "simkl")).toBe("Add to my Simkl watchlist");
    expect(forTracker("Mark as watched", "simkl")).toBe("Mark as watched");
  });
  it("état du suivi normalisé (valeurs inconnues écartées)", () => {
    expect(normalizeTracking({ service: "simkl", choice: "simkl", available: ["trakt", "simkl", "x"], linked: { trakt: false, simkl: true } }))
      .toEqual({ service: "simkl", choice: "simkl", available: ["trakt", "simkl"], linked: { trakt: false, simkl: true } });
    expect(normalizeTracking({ service: null, choice: "none", available: ["trakt"] })).toEqual({ service: null, choice: "none", available: ["trakt"], linked: { trakt: false, simkl: false } });
    expect(normalizeTracking({})).toEqual({ service: null, choice: "none", available: [], linked: { trakt: false, simkl: false } });
  });
  it("bibliothèque : service lu dans la réponse, Trakt par défaut", () => {
    expect(normalizeTraktLibrary({ service: "simkl" }).service).toBe("simkl");
    expect(normalizeTraktLibrary({}).service).toBe("trakt");
    expect(normalizeTraktLibrary({ service: "autre" }).service).toBe("trakt");
  });
});
