// Logique pure de Plex sur des réponses simulées (forme réelle de plex.tv et d'un serveur) : PIN, serveurs, connexions,
// bibliothèques, éléments, adresses sans jeton, jeton ajouté à l'affichage seulement, rapports, attente du code.
import "fake-indexeddb/auto";
import { describe, expect, it } from "vitest";
import { emptySource } from "@/db/sources";
import { resumeSeconds, seriesInfoOf, vodInfoOf } from "./mediaServer";
import {
  awaitPlexToken, directUrl, hlsUrl, imageUrl, isPlexImage, itemsUrl, orderConnections, parseItem, parseItems, parsePin, parseSections, parseServers,
  PIN_POLL_MS, PIN_TTL_MS, registerPlexTokens, timelineState, timelineUrl, withImageToken, withPlexParams, type PlexConnection,
} from "./plex";
import { HttpError } from "./transport";

const RESOURCES = JSON.stringify([
  { name: "Salon", provides: "server", clientIdentifier: "mach1", accessToken: "srv-tok", owned: true, connections: [
    { protocol: "https", uri: "https://1-2-3-4.abc.plex.direct:32400", local: false, relay: false, IPv6: false },
    { protocol: "https", uri: "https://relay.plex.direct:8443", local: false, relay: true, IPv6: false },
    { protocol: "http", uri: "http://192.168.1.5:32400", local: true, relay: false, IPv6: false },
    { protocol: "https", uri: "https://192-168-1-5.abc.plex.direct:32400", local: true, relay: false, IPv6: false },
  ] },
  { name: "Téléphone", provides: "client,player", clientIdentifier: "phone", accessToken: "x", connections: [] },
  { name: "Ami", provides: "server", clientIdentifier: "mach2", accessToken: "ami-tok", owned: false, connections: [{ protocol: "http", uri: "http://9.9.9.9:32400/", local: false, relay: false }] },
  { name: "SansJeton", provides: "server", clientIdentifier: "mach3", connections: [{ uri: "http://x:1" }] },
]);

const MOVIES = JSON.stringify({ MediaContainer: { size: 1, totalSize: 650, Metadata: [
  { ratingKey: "101", type: "movie", title: "Le Film", year: 1999, audienceRating: 8.2, summary: "Résumé", thumb: "/library/metadata/101/thumb/17", art: "/library/metadata/101/art/17",
    duration: 5_400_000, addedAt: 1_700_000_000, viewOffset: 120_000, Genre: [{ tag: "Drame" }], Role: [{ tag: "A" }], Director: [{ tag: "Réal" }],
    Media: [{ container: "mkv", Part: [{ id: 9, key: "/library/parts/9/1700/file.mkv", container: "mkv" }] }] },
] } });

describe("code PIN", () => {
  it("analyse la réponse de plex.tv", () => {
    expect(parsePin('{"id":12345,"code":"ABCD","authToken":null,"expiresIn":1800}')).toEqual({ id: 12345, code: "ABCD", authToken: null, expiresIn: 1800 });
    expect(parsePin('{"id":1,"code":"Z","authToken":"tok"}')!.authToken).toBe("tok");
    expect(parsePin("pas du json")).toBeNull();
    expect(parsePin('{"id":1}')).toBeNull();
  });

  const pin = (expiresIn: number | null = null) => ({ id: 7, code: "WXYZ", authToken: null, expiresIn });

  it("jeton reçu après quelques sondes ; le code est affiché", async () => {
    let polls = 0, shown = "";
    const waits: number[] = [];
    const r = await awaitPlexToken({ create: async () => pin(), poll: async (id) => { expect(id).toBe(7); return ++polls < 3 ? null : "acct-tok"; }, onCode: (c) => { shown = c; }, wait: async (ms) => { waits.push(ms); } });
    expect(r).toEqual({ token: "acct-tok" });
    expect(shown).toBe("WXYZ");
    expect(waits).toEqual([PIN_POLL_MS, PIN_POLL_MS, PIN_POLL_MS]);
  });

  it("expire après 15 minutes sans jeton, ou selon le serveur", async () => {
    let polls = 0;
    expect(await awaitPlexToken({ create: async () => pin(), poll: async () => { polls++; return null; }, onCode: () => undefined, wait: async () => undefined })).toEqual({ expired: true });
    expect(polls).toBe(PIN_TTL_MS / PIN_POLL_MS);
    polls = 0;
    await awaitPlexToken({ create: async () => pin(10), poll: async () => { polls++; return null; }, onCode: () => undefined, wait: async () => undefined });
    expect(polls).toBe(4);
  });

  it("404 = expiré ; erreur réseau passagère ignorée ; annulation propagée", async () => {
    let n = 0;
    const r = await awaitPlexToken({ create: async () => pin(), poll: async () => { if (++n === 1) throw new TypeError("réseau"); throw new HttpError(404); }, onCode: () => undefined, wait: async () => undefined });
    expect(r).toEqual({ expired: true });
    expect(n).toBe(2);
    const ctrl = new AbortController();
    ctrl.abort();
    await expect(awaitPlexToken({ create: async () => pin(), poll: async () => null, onCode: () => undefined, wait: async () => undefined, signal: ctrl.signal })).rejects.toThrow();
  });
});

describe("serveurs et connexions", () => {
  it("seulement les serveurs avec jeton, propriétaire d'abord, connexions classées", () => {
    const s = parseServers(RESOURCES);
    expect(s.map((x) => x.name)).toEqual(["Salon", "Ami"]);
    expect(s[0]).toMatchObject({ machineId: "mach1", accessToken: "srv-tok", owned: true });
    expect(s[0]!.connections.map((c) => c.uri)).toEqual([
      "https://192-168-1-5.abc.plex.direct:32400", "http://192.168.1.5:32400", "https://1-2-3-4.abc.plex.direct:32400", "https://relay.plex.direct:8443",
    ]);
    expect(s[1]!.connections[0]!.uri).toBe("http://9.9.9.9:32400");
    expect(parseServers("pas du json")).toEqual([]);
  });

  it("IPv6 après IPv4 à égalité, doublons retirés", () => {
    const c = (uri: string, p: Partial<PlexConnection> = {}): PlexConnection => ({ uri, local: false, relay: false, https: true, ipv6: false, ...p });
    expect(orderConnections([c("a", { ipv6: true }), c("b"), c("b")]).map((x) => x.uri)).toEqual(["b", "a"]);
  });
});

describe("bibliothèques et éléments", () => {
  it("films et séries seulement", () => {
    const l = parseSections(JSON.stringify({ MediaContainer: { Directory: [{ key: "1", title: "Films", type: "movie" }, { key: "2", title: "Séries", type: "show" }, { key: "3", title: "Musique", type: "artist" }] } }));
    expect(l).toEqual([{ id: "1", name: "Films", type: "movies" }, { id: "2", name: "Séries", type: "tvshows" }]);
  });

  it("élément ramené au modèle commun (ticks, chemins d'image)", () => {
    const page = parseItems(MOVIES);
    expect(page.total).toBe(650);
    expect(page.items[0]).toMatchObject({
      id: "101", name: "Le Film", year: 1999, rating: 8.2, container: "mkv", mediaSourceId: "/library/parts/9/1700/file.mkv",
      genres: ["Drame"], cast: ["A"], directors: ["Réal"], added: 1_700_000_000_000, played: false,
      primaryTag: "/library/metadata/101/thumb/17", backdropTag: "/library/metadata/101/art/17",
    });
    expect(vodInfoOf(page.items[0]!)).toMatchObject({ resumePos: 120, info: { duration_secs: 5400 } });
    expect(parseItems("n'importe quoi")).toEqual({ total: 0, items: [] });
    expect(parseItem('{"MediaContainer":{"Metadata":[]}}')).toBeNull();
  });

  it("reprise : serveur d'abord ; vu en entier repart du début", () => {
    const it = (off: number, views: number) => parseItem(JSON.stringify({ MediaContainer: { Metadata: [{ ratingKey: "1", title: "t", viewOffset: off, viewCount: views }] } }));
    expect(resumeSeconds(it(60_000, 0), 5)).toBe(60);
    expect(resumeSeconds(it(0, 1), 5)).toBeUndefined();
    expect(resumeSeconds(it(0, 0), 5)).toBe(5);
  });

  it("épisodes présentés par saison avec image fournie par le client", () => {
    const eps = parseItems(JSON.stringify({ MediaContainer: { Metadata: [
      { ratingKey: "201", type: "episode", title: "Pilote", parentIndex: 1, index: 1, thumb: "/t/1" }, { ratingKey: "202", type: "episode", title: "Deux", parentIndex: 2, index: 1 },
    ] } })).items;
    const info = seriesInfoOf(eps, (e) => (e.primaryTag ? `img:${e.primaryTag}` : undefined));
    expect(Object.keys(info.episodes)).toEqual(["1", "2"]);
    expect(info.episodes["1"]![0]!.info.movie_image).toBe("img:/t/1");
    expect(info.media).toEqual([{ id: 201, url: "media:201" }, { id: 202, url: "media:202" }]);
  });
});

describe("adresses : jamais de jeton stocké", () => {
  const BASE = "http://192.168.1.5:32400";

  it("adresses stockées sans jeton ; paramètres ajoutés à la volée", () => {
    expect(itemsUrl(BASE, "2", 2, 300)).toBe(`${BASE}/library/sections/2/all?type=2&X-Plex-Container-Start=300&X-Plex-Container-Size=300`);
    expect(directUrl(BASE, "/library/parts/9/1700/file.mkv")).toBe(`${BASE}/library/parts/9/1700/file.mkv`);
    expect(directUrl(BASE, null)).toBeNull();
    expect(directUrl(BASE, "pas-un-chemin")).toBeNull();
    const img = imageUrl(BASE, "/library/metadata/101/thumb/17");
    expect(img).toContain("/photo/:/transcode?");
    expect(img).toContain("url=%2Flibrary%2Fmetadata%2F101%2Fthumb%2F17");
    for (const u of [img, itemsUrl(BASE, "1", 1, 0), hlsUrl(BASE, "101", "sess")]) expect(u).not.toMatch(/token/i);
    const full = withPlexParams(`${BASE}/library/parts/9/1/file.mkv`, "inst-1", "tok");
    expect(full).toContain("X-Plex-Client-Identifier=inst-1");
    expect(full).toContain("X-Plex-Product=Ultra%20TV");
    expect(full).toContain("X-Plex-Token=tok");
    expect(withPlexParams(`${BASE}/x`, "i")).not.toContain("Token");
  });

  it("jeton d'affiche : seulement pour un serveur Plex connu, jamais un autre hôte", () => {
    registerPlexTokens([{ ...emptySource(), type: "plex", server: BASE, password: "img-tok" }, { ...emptySource(), type: "xtream", server: "http://autre:1", password: "xtream-pw" }]);
    const img = imageUrl(BASE, "/library/metadata/1/thumb/2");
    expect(isPlexImage(img)).toBe(true);
    expect(withImageToken(img)).toBe(`${img}&X-Plex-Token=img-tok`);
    expect(withImageToken(imageUrl("http://inconnu:32400", "/x"))).not.toContain("Token");
    expect(withImageToken("http://autre:1/photo/other.jpg")).toBe("http://autre:1/photo/other.jpg");
    registerPlexTokens([]);
    expect(withImageToken(img)).toBe(img);
  });

  it("rapports /:/timeline", () => {
    expect(timelineState("start", false)).toBe("playing");
    expect(timelineState("progress", true)).toBe("paused");
    expect(timelineState("stop", false)).toBe("stopped");
    const u = timelineUrl(BASE, "101", "stopped", 4000, 5_400_000);
    expect(u).toContain("/:/timeline?ratingKey=101&key=%2Flibrary%2Fmetadata%2F101");
    expect(u).toContain("state=stopped&time=4000&duration=5400000");
  });
});
