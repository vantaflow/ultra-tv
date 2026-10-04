import { describe, expect, it } from "vitest";
import { emptySource } from "@/db/sources";
import { convertToXtream, missingRequired, nonStandardHttpStatus, parseXtreamUrl } from "./xtreamUrl";

// Hôtes et identifiants fictifs uniquement.
describe("parseXtreamUrl", () => {
  it("get.php avec port : serveur, identifiant, mot de passe", () => {
    expect(parseXtreamUrl("http://iptv.example.test:8080/get.php?username=alice&password=secret&type=m3u_plus&output=ts"))
      .toEqual({ server: "http://iptv.example.test:8080", username: "alice", password: "secret" });
  });
  it("https et sans port", () => {
    expect(parseXtreamUrl("HTTPS://iptv.example.test/get.php?username=a&password=b")!.server).toBe("https://iptv.example.test");
  });
  it("paramètres dans un autre ordre, player_api.php et xmltv.php", () => {
    const o = (u: string) => { const c = parseXtreamUrl(u)!; return [c.username, c.password]; };
    expect(o("http://h.example.test/get.php?type=m3u&password=p1&output=ts&username=u1")).toEqual(["u1", "p1"]);
    expect(o("http://h.example.test/player_api.php?password=p2&username=u2")).toEqual(["u2", "p2"]);
    expect(o("http://h.example.test/xmltv.php?username=u3&password=p3")).toEqual(["u3", "p3"]);
  });
  it("encodage pourcent décodé, « + » conservé", () => {
    const c = parseXtreamUrl("http://h.example.test/get.php?username=al%40ice&password=pa%24%24+w%C3%A9")!;
    expect(c.username).toBe("al@ice");
    expect(c.password).toBe("pa$$+wé");
  });
  it("préfixe de chemin conservé, espaces autour ignorés", () => {
    expect(parseXtreamUrl("  http://h.example.test:8000/panel/get.php?username=a&password=b ")!.server).toBe("http://h.example.test:8000/panel");
  });
  it("sans mot de passe ou sans identifiant : pas de conversion", () => {
    expect(parseXtreamUrl("http://h.example.test/get.php?username=a")).toBeNull();
    expect(parseXtreamUrl("http://h.example.test/get.php?username=a&password=")).toBeNull();
    expect(parseXtreamUrl("http://h.example.test/get.php?username=&password=b")).toBeNull();
  });
  it("get.php sans identifiants, lien M3U ordinaire, autre schéma : reste M3U", () => {
    expect(parseXtreamUrl("http://h.example.test/get.php")).toBeNull();
    expect(parseXtreamUrl("https://h.example.test/liste.m3u")).toBeNull();
    expect(parseXtreamUrl("http://h.example.test/autre.php?username=a&password=b")).toBeNull();
    expect(parseXtreamUrl("ftp://h.example.test/get.php?username=a&password=b")).toBeNull();
    expect(parseXtreamUrl("")).toBeNull();
  });
});

describe("convertToXtream", () => {
  const m3u = () => ({
    ...emptySource(), id: 7, name: "Ma liste", type: "m3u" as const, cid: 3, lastSyncAt: 99, state: "error" as const, error: "HTTP 884",
    m3uUrl: "http://iptv.example.test:8080/get.php?username=alice&password=secret&type=m3u_plus&output=ts",
  });
  it("devient une source Xtream, repart de zéro, garde id et nom", () => {
    const x = convertToXtream(m3u())!;
    expect(x).toMatchObject({ id: 7, name: "Ma liste", type: "xtream", server: "http://iptv.example.test:8080", username: "alice", password: "secret", m3uUrl: "", lastSyncAt: 0, state: "new", error: undefined });
  });
  it("laisse les autres sources inchangées", () => {
    expect(convertToXtream({ ...m3u(), m3uUrl: "https://h.example.test/liste.m3u" })).toBeNull();
    expect(convertToXtream({ ...m3u(), type: "xtream" })).toBeNull();
  });
});

describe("nonStandardHttpStatus", () => {
  it("reconnaît les codes maison, pas les standards", () => {
    expect(nonStandardHttpStatus("HTTP 884")).toBe(884);
    expect(nonStandardHttpStatus("HTTP 403")).toBeNull();
    expect(nonStandardHttpStatus("HTTP 503")).toBeNull();
    expect(nonStandardHttpStatus("auth")).toBeNull();
  });
});

describe("missingRequired", () => {
  const link = { ...emptySource(), type: "m3u" as const, name: "STRONG", m3uUrl: "http://h.example.test:8080/get.php?username=alice&password=secret&type=m3u_plus&output=ts" };
  it("get.php en M3U : la source convertie est complète (régression « Renseignez tous les champs »)", () => {
    const conv = convertToXtream(link)!;
    expect(conv.m3uUrl).toBe("");
    expect(missingRequired("m3u-link", conv)).toBe(false);
  });
  it("lien M3U vide : champ manquant", () => expect(missingRequired("m3u-link", { ...link, m3uUrl: " " })).toBe(true));
  it("lien M3U ordinaire : complet", () => expect(missingRequired("m3u-link", { ...link, m3uUrl: "http://h.example.test/l.m3u" })).toBe(false));
  it("Xtream sans mot de passe : champ manquant", () => expect(missingRequired("xtream", { ...emptySource(), server: "http://h", username: "u" })).toBe(true));
});
