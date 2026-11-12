import { describe, expect, it } from "vitest";
import { legacyRedirectTarget } from "../src/legacyhost.js";

const OLD = "https://ultratv-config.khalilbenaz.workers.dev";
const HTML = "text/html,application/xhtml+xml,*/*;q=0.8";

describe("ancienne adresse workers.dev", () => {
  it("pages du navigateur → nouvelle adresse (chemin et paramètres gardés)", () => {
    expect(legacyRedirectTarget("GET", `${OLD}/login?e=pw`, HTML)).toBe("https://tv.weedy.dpdns.org/login?e=pw");
    expect(legacyRedirectTarget("GET", `${OLD}/envoyer?c=ABC&l=fr`, HTML)).toBe("https://tv.weedy.dpdns.org/envoyer?c=ABC&l=fr");
  });
  it("API des applis installées : jamais redirigée", () => {
    expect(legacyRedirectTarget("GET", `${OLD}/api/announcements?audience=tv`, "*/*")).toBeNull();
    expect(legacyRedirectTarget("GET", `${OLD}/api/config`, HTML)).toBeNull();
    expect(legacyRedirectTarget("POST", `${OLD}/api/pair/start`, "*/*")).toBeNull();
  });
  it("formulaires (POST) et retour Trakt : servis sur place", () => {
    expect(legacyRedirectTarget("POST", `${OLD}/login`, HTML)).toBeNull();
    expect(legacyRedirectTarget("GET", `${OLD}/trakt/callback?code=x`, HTML)).toBeNull();
  });
  it("téléchargements (Downloader, sans text/html) → nouvelle adresse", () => {
    expect(legacyRedirectTarget("GET", `${OLD}/download/android-32`, "*/*")).toBe("https://tv.weedy.dpdns.org/download/android-32");
    expect(legacyRedirectTarget("GET", `${OLD}/apk`, "")).toBe("https://tv.weedy.dpdns.org/apk");
  });
  it("nouvelle adresse : rien à faire", () => {
    expect(legacyRedirectTarget("GET", "https://tv.weedy.dpdns.org/login", HTML)).toBeNull();
  });
});
