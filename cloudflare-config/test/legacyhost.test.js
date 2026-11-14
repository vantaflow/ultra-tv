import { describe, expect, it } from "vitest";
import { legacyDecision } from "../src/legacyhost.js";

const OLD = "https://ultratv-config.khalilbenaz.workers.dev";
const NEW = "https://ultratv.dpdns.org";
const API = "https://tv.weedy.dpdns.org";

describe("ancienne adresse workers.dev", () => {
  it("API des applis installées : servie", () => {
    expect(legacyDecision("GET", `${OLD}/api/announcements?audience=tv`).kind).toBe("serve");
    expect(legacyDecision("POST", `${OLD}/api/pair/start`).kind).toBe("serve");
  });
  it("toute page (site, connexion, admin, téléchargements, Trakt, QR) : redirigée vers la même page de la nouvelle adresse", () => {
    for (const p of ["/", "/login", "/login?e=pw", "/admin", "/apk", "/download/android", "/manifest.webmanifest", "/pair?c=ABC", "/envoyer?c=ABC&l=fr", "/trakt/callback?code=a&state=b"]) {
      expect(legacyDecision("GET", `${OLD}${p}`)).toEqual({ kind: "redirect", to: `${NEW}${p}`, status: 302 });
    }
  });
  it("formulaire envoyé à l'ancienne adresse : 307, garde la méthode", () => {
    expect(legacyDecision("POST", `${OLD}/login`)).toEqual({ kind: "redirect", to: `${NEW}/login`, status: 307 });
  });
  it("nouvelle adresse et tests : servis normalement", () => {
    expect(legacyDecision("GET", `${NEW}/login`).kind).toBe("serve");
    expect(legacyDecision("GET", "https://config.test/login").kind).toBe("serve");
  });
});

describe("tv.weedy.dpdns.org : adresse API, pages vers le site principal", () => {
  it("API servie (applis installées), pages redirigées", () => {
    expect(legacyDecision("POST", `${API}/api/pair/start`).kind).toBe("serve");
    expect(legacyDecision("GET", `${API}/api/config`).kind).toBe("serve");
    expect(legacyDecision("GET", `${API}/donate?l=fr`)).toEqual({ kind: "redirect", to: `${NEW}/donate?l=fr`, status: 302 });
    expect(legacyDecision("GET", `${API}/pair?code=ABCD2345`)).toEqual({ kind: "redirect", to: `${NEW}/pair?code=ABCD2345`, status: 302 });
  });
});
