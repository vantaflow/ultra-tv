import { describe, expect, it } from "vitest";
import type { Library } from "@/net/mediaServer";
import { connKindOfAddress, copyDate, countdownParts, isLibrarySource, libraryWanted, libStatus, libsToSave, plexConnKind, progressRatio } from "./mediaWizard";

const conn = (uri: string, p: { local?: boolean; relay?: boolean } = {}) => ({ uri, local: !!p.local, relay: !!p.relay, https: uri.startsWith("https"), ipv6: false });
const libs: Library[] = [{ id: "1", name: "Films", type: "movies" }, { id: "2", name: "Séries", type: "tvshows" }, { id: "3", name: "Enfants", type: "movies" }];

describe("connexion Plex retenue", () => {
  it("locale quand une connexion locale existe, même listée après", () => {
    expect(plexConnKind({ connections: [conn("https://a.plex.direct:8443", { relay: true }), conn("http://192.168.1.4:32400", { local: true })] })).toBe("local");
  });
  it("distante sans locale, relais en dernier recours, distante par défaut", () => {
    expect(plexConnKind({ connections: [conn("https://x:32400"), conn("https://r:8443", { relay: true })] })).toBe("remote");
    expect(plexConnKind({ connections: [conn("https://r:8443", { relay: true })] })).toBe("relay");
    expect(plexConnKind({ connections: [] })).toBe("remote");
  });
  it("d'après l'adresse enregistrée", () => {
    expect(connKindOfAddress("http://192.168.1.40:32400")).toBe("local");
    expect(connKindOfAddress("http://10.0.0.2:32400")).toBe("local");
    expect(connKindOfAddress("http://172.20.1.1:32400")).toBe("local");
    expect(connKindOfAddress("http://nas.local:32400")).toBe("local");
    expect(connKindOfAddress("https://1-2-3-4.abcdef.plex.direct:8443")).toBe("relay");
    expect(connKindOfAddress("https://192-168-1-40.abcdef.plex.direct:32400")).toBe("local");
    expect(connKindOfAddress("https://88-12-3-4.abcdef.plex.direct:32400")).toBe("remote");
    expect(connKindOfAddress("http://203.0.113.9:8096")).toBe("remote");
    expect(connKindOfAddress("n'importe quoi")).toBe("remote");
  });
});

describe("choix des bibliothèques", () => {
  it("tout coché = null (toutes, y compris les futures)", () => {
    expect(libsToSave(libs, new Set(["1", "2", "3"]))).toBeNull();
  });
  it("sinon les cochées, dans l'ordre du serveur", () => {
    expect(libsToSave(libs, new Set(["3", "1"]))).toEqual(["1", "3"]);
    expect(libsToSave(libs, new Set())).toEqual([]);
  });
  it("une bibliothèque est importée sans liste ou si elle y figure", () => {
    expect(libraryWanted(undefined, "1")).toBe(true);
    expect(libraryWanted(null, "1")).toBe(true);
    expect(libraryWanted(["2"], "1")).toBe(false);
    expect(libraryWanted(["2"], "2")).toBe(true);
  });
});

describe("code Plex et progression", () => {
  it("compte à rebours : minutes puis secondes, jamais négatif", () => {
    expect(countdownParts(14 * 60_000)).toEqual({ unit: "min", n: 14 });
    expect(countdownParts(13 * 60_000 + 1)).toEqual({ unit: "min", n: 14 });
    expect(countdownParts(60_000)).toEqual({ unit: "s", n: 60 });
    expect(countdownParts(1)).toEqual({ unit: "s", n: 1 });
    expect(countdownParts(-5)).toEqual({ unit: "s", n: 0 });
  });
  it("barre de progression : rien si non commencé ou presque fini", () => {
    expect(progressRatio(0, 100)).toBeNull();
    expect(progressRatio(50, 0)).toBeNull();
    expect(progressRatio(99, 100)).toBeNull();
    expect(progressRatio(55, 100)).toBeCloseTo(0.55);
  });
});

describe("état d'un serveur", () => {
  it("vérification en cours, hors ligne, en ligne", () => {
    expect(libStatus({ type: "jellyfin", server: "http://192.168.1.2:8096" }, null)).toBe("checking");
    expect(libStatus({ type: "jellyfin", server: "http://192.168.1.2:8096" }, false)).toBe("offline");
    expect(libStatus({ type: "jellyfin", server: "http://192.168.1.2:8096" }, true)).toBe("online");
  });
  it("relais Plex seulement pour Plex", () => {
    expect(libStatus({ type: "plex", server: "https://1-2-3-4.abc.plex.direct:8443" }, true)).toBe("relay");
    expect(libStatus({ type: "plex", server: "http://192.168.1.40:32400" }, true)).toBe("online");
    expect(libStatus({ type: "emby", server: "https://1-2-3-4.abc.plex.direct:8443" }, true)).toBe("online");
  });
  it("types de bibliothèque personnelle et date de copie", () => {
    expect(["jellyfin", "emby", "plex", "nas"].every(isLibrarySource)).toBe(true);
    expect(["xtream", "m3u", "stalker", undefined].some(isLibrarySource)).toBe(false);
    expect(copyDate(0, "fr")).toBe("—");
    expect(copyDate(Date.UTC(2026, 10, 12, 12), "en-US")).toBe("11/12/2026");
  });
});
