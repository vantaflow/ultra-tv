import { describe, expect, it } from "vitest";
import { accountServer } from "./accountServer";

describe("accountServer : adresse saisie par l'utilisateur", () => {
  it("Xtream : l'adresse saisie, telle quelle", () => {
    expect(accountServer({ type: "xtream", server: "http://mon-iptv.example:8080" })).toBe("http://mon-iptv.example:8080");
    expect(accountServer({ type: "xtream", server: " https://a.example/ " })).toBe("https://a.example");
  });
  it("ni identifiants ni paramètres", () => {
    expect(accountServer({ type: "xtream", server: "http://user:pass@a.example:80/path?username=u&password=p" })).toBe("http://a.example:80/path");
  });
  it("Stalker : adresse du portail ; M3U et autres : rien", () => {
    expect(accountServer({ type: "stalker", server: "http://portal.example/c/" })).toBe("http://portal.example/c");
    expect(accountServer({ type: "m3u", server: "" })).toBeNull();
    expect(accountServer({ type: "xtream", server: "" })).toBeNull();
  });
});
