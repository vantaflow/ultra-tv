import { afterEach, describe, expect, it, vi } from "vitest";
import { discoverServers, discoveryAvailable, displayAddress, isFound, mergeFound, type FoundServer } from "./discovery";

const jf: FoundServer = { kind: "jellyfin", name: "Salon", address: "http://192.168.1.20:8096", id: "a" };
const plex: FoundServer = { kind: "plex", name: "NAS-Plex", address: "http://192.168.1.40:32400", id: "m1" };

afterEach(() => { vi.unstubAllGlobals(); });

describe("détection des serveurs", () => {
  it("indisponible hors application de bureau : liste vide, jamais d'erreur", async () => {
    expect(discoveryAvailable()).toBe(false);
    expect(await discoverServers()).toEqual([]);
  });

  it("utilise le pont du bureau et écarte les entrées invalides", async () => {
    vi.stubGlobal("window", { ultratv: { discoverServers: async () => [jf, { kind: "inconnu", name: "x", address: "http://a", id: "1" }, { kind: "emby", name: "E", address: "pas-une-adresse", id: "2" }, plex] } });
    expect(discoveryAvailable()).toBe(true);
    expect(await discoverServers()).toEqual([jf, plex]);
  });

  it("une erreur du pont donne une liste vide", async () => {
    vi.stubGlobal("window", { ultratv: { discoverServers: async () => { throw new Error("boom"); } } });
    expect(await discoverServers()).toEqual([]);
  });

  it("isFound vérifie type, nom, adresse http(s) et identifiant", () => {
    expect(isFound(jf)).toBe(true);
    expect(isFound(null)).toBe(false);
    expect(isFound({ ...jf, address: "ftp://x" })).toBe(false);
    expect(isFound({ ...jf, id: 3 })).toBe(false);
  });
});

describe("relance de la recherche", () => {
  it("garde l'ordre d'arrivée et ne duplique pas", () => {
    const again = { ...jf, address: "http://autre-interface:8096" };
    expect(mergeFound([jf], [plex, again])).toEqual([jf, plex]);
    expect(mergeFound([], [])).toEqual([]);
  });
  it("même identifiant mais type différent : deux serveurs", () => {
    expect(mergeFound([jf], [{ ...jf, kind: "emby" }])).toHaveLength(2);
  });
  it("adresse affichée sans schéma", () => {
    expect(displayAddress("http://192.168.1.20:8096/")).toBe("192.168.1.20:8096");
    expect(displayAddress("https://nas.local")).toBe("nas.local");
  });
});
