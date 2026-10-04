import { describe, expect, it } from "vitest";
import { iptvLink } from "../src/store.js";

describe("iptvLink", () => {
  it("Xtream : playlist get.php avec identifiants encodés", () => {
    expect(iptvLink({ kind: "XTREAM", url: "http://iptv.example.test:8080/", username: "alice", password: "p&ss" }))
      .toBe("http://iptv.example.test:8080/get.php?username=alice&password=p%26ss&type=m3u_plus&output=ts");
  });
  it("M3U : URL telle qu'enregistrée", () => {
    expect(iptvLink({ kind: "M3U", url: "https://h.example.test/liste.m3u" })).toBe("https://h.example.test/liste.m3u");
  });
  it("URL invalide : vide", () => expect(iptvLink({ kind: "XTREAM", url: "pas une url" })).toBe(""));
});
