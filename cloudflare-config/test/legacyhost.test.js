import { describe, expect, it } from "vitest";
import { legacyDecision } from "../src/legacyhost.js";

const OLD = "https://ultratv-config.khalilbenaz.workers.dev";

describe("ancienne adresse workers.dev", () => {
  it("API des applis installées : servie", () => {
    expect(legacyDecision("GET", `${OLD}/api/announcements?audience=tv`).kind).toBe("serve");
    expect(legacyDecision("POST", `${OLD}/api/pair/start`).kind).toBe("serve");
  });
  it("retour Trakt, /pair et /envoyer (QR des applis installées) : poursuivis sur la nouvelle adresse", () => {
    expect(legacyDecision("GET", `${OLD}/trakt/callback?code=a&state=b`)).toEqual({ kind: "redirect", to: "https://tv.weedy.dpdns.org/trakt/callback?code=a&state=b" });
    expect(legacyDecision("GET", `${OLD}/pair?c=ABC`).to).toBe("https://tv.weedy.dpdns.org/pair?c=ABC");
    expect(legacyDecision("GET", `${OLD}/envoyer?c=ABC&l=fr`).to).toBe("https://tv.weedy.dpdns.org/envoyer?c=ABC&l=fr");
  });
  it("site, tableau de bord, administration, téléchargements : plus servis", () => {
    for (const p of ["/", "/login", "/admin", "/apk", "/download/android", "/manifest.webmanifest"]) {
      expect(legacyDecision("GET", `${OLD}${p}`).kind).toBe("gone");
    }
    expect(legacyDecision("POST", `${OLD}/login`).kind).toBe("gone");
  });
  it("nouvelle adresse et tests : servis normalement", () => {
    expect(legacyDecision("GET", "https://tv.weedy.dpdns.org/login").kind).toBe("serve");
    expect(legacyDecision("GET", "https://config.test/login").kind).toBe("serve");
  });
});
