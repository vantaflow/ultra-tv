// Synchro et lecture d'un serveur Plex (faux serveur en mémoire) : bibliothèques = catégories, films et séries indexés, jeton absent
// de tout ce qui est stocké, adresses de lecture avec jeton à la volée, reprise lue sur le serveur, rapports de lecture.
import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { db } from "@/db/db";
import { emptySource, getSource, saveSource } from "@/db/sources";
import type { Source } from "@/db/types";
import { PlexAccount, plexClientId } from "@/net/plex";
import { mediaClientFor, mediaItemIdOf, mediaPlayback } from "@/player/resolve";
import { runSync, testConnection } from "./core";

const BASE = "http://plex.invalid:32400";
const TOKEN = "jeton-plex-secret";

let calls: Array<{ method: string; url: string; accept?: string }>;

const movie = (i: number) => ({ ratingKey: String(100 + i), type: "movie", title: `Film ${i} (2001)`, year: 2001, thumb: `/library/metadata/${100 + i}/thumb/1`, duration: 1000, Media: [{ container: "mkv", Part: [{ key: `/library/parts/${100 + i}/1/file.mkv` }] }] });

const fakeFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = new URL(String(input));
  const headers = new Headers(init?.headers);
  calls.push({ method: init?.method ?? "GET", url: url.toString(), accept: headers.get("accept") ?? undefined });
  const json = (o: unknown) => new Response(JSON.stringify(o), { status: 200, headers: { "Content-Type": "application/json" } });
  const p = url.pathname;
  if (url.hostname === "plex.tv") {
    if (p === "/api/v2/pins") return json({ id: 7, code: "ABCD", authToken: null, expiresIn: 900 });
    if (p === "/api/v2/pins/7") return json({ id: 7, code: "ABCD", authToken: "acct-tok" });
    if (p === "/api/v2/resources") return json([{ name: "Salon", provides: "server", clientIdentifier: "mach1", accessToken: TOKEN, owned: true, connections: [{ protocol: "http", uri: BASE, local: true, relay: false }] }]);
    return new Response("", { status: 404 });
  }
  if (url.searchParams.get("X-Plex-Token") !== TOKEN) return new Response("", { status: 401 });
  if (p === "/identity") return json({ MediaContainer: {} });
  if (p === "/library/sections") return json({ MediaContainer: { Directory: [{ key: "1", title: "Films", type: "movie" }, { key: "2", title: "Séries", type: "show" }, { key: "3", title: "Musique", type: "artist" }] } });
  if (p === "/library/sections/1/all") return json({ MediaContainer: { totalSize: 3, Metadata: [movie(1), movie(2), movie(3)] } });
  if (p === "/library/sections/2/all") return json({ MediaContainer: { totalSize: 2, Metadata: [{ ratingKey: "500", type: "show", title: "Série 1", year: 2020, thumb: "/library/metadata/500/thumb/1" }, { ratingKey: "501", type: "show", title: "Série 2" }] } });
  if (p === "/library/metadata/101") return json({ MediaContainer: { Metadata: [{ ...movie(1), viewOffset: 600_000, duration: 5_400_000 }] } });
  if (p === "/library/metadata/102") return json({ MediaContainer: { Metadata: [{ ...movie(2), viewCount: 1 }] } });
  if (p === "/library/metadata/500/allLeaves") return json({ MediaContainer: { Metadata: [{ ratingKey: "600", type: "episode", title: "Ép 1", parentIndex: 1, index: 1 }, { ratingKey: "601", type: "episode", title: "Ép 2", parentIndex: 1, index: 2 }] } });
  if (p === "/:/timeline") return new Response("", { status: 200 });
  return new Response("", { status: 404 });
});

const base = (o: Partial<Source> = {}): Source => ({ ...emptySource(), id: 1, name: "Salon", type: "plex", server: BASE, username: "mach1", password: TOKEN, ...o });

async function sync() {
  const source = (await getSource(1)) ?? base();
  return runSync({ source, transport: { mode: "direct" }, onProgress: () => undefined });
}

beforeEach(async () => {
  calls = [];
  vi.stubGlobal("fetch", fakeFetch);
  await Promise.all([db.sources, db.categories, db.channels, db.movies, db.series, db.details].map((t) => t.clear()));
  await saveSource(base());
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe("synchro Plex", () => {
  it("bibliothèques en catégories, films et séries indexés, ni direct ni musique", async () => {
    expect(await sync()).toEqual({ live: 0, movie: 3, series: 2 });
    const cid = (await db.sources.get(1))!.cid;
    const cats = await db.categories.where("sourceId").equals(cid).toArray();
    expect(cats.map((c) => [c.kind, c.extId, c.name, c.count]).sort()).toEqual([["movie", "1", "Films", 3], ["series", "2", "Séries", 2]]);
    const movies = await db.movies.where("sourceId").equals(cid).sortBy("ord");
    expect(movies[0]).toMatchObject({ title: "Film 1", catExt: "1", streamId: 101, year: 2001, ext: "mkv", url: "media:101" });
    expect(movies[0]!.poster).toContain(`${BASE}/photo/:/transcode?`);
    const series = await db.series.where("sourceId").equals(cid).sortBy("ord");
    expect(series[0]).toMatchObject({ seriesId: 500, portalId: "500", url: "media:500" });
  });

  it("le jeton n'est stocké nulle part (catalogue, affiches) et part en paramètre avec Accept JSON", async () => {
    await sync();
    const dump = JSON.stringify([await db.movies.toArray(), await db.series.toArray(), await db.categories.toArray(), await db.details.toArray()]);
    expect(dump).not.toContain(TOKEN);
    expect(dump).not.toMatch(/X-Plex-Token/i);
    const lib = calls.filter((c) => c.url.startsWith(BASE));
    expect(lib.length).toBeGreaterThanOrEqual(3);
    expect(lib.every((c) => c.url.includes(`X-Plex-Token=${TOKEN}`) && c.url.includes("X-Plex-Client-Identifier=ultratv-") && c.accept === "application/json")).toBe(true);
    expect(await plexClientId()).toMatch(/^ultratv-/);
  });

  it("seconde synchro : rien de dupliqué", async () => {
    await sync();
    expect(await sync()).toEqual({ live: 0, movie: 3, series: 2 });
    expect(await db.movies.count()).toBe(3);
  });

  it("jeton refusé : erreur « auth » ; test de connexion", async () => {
    await expect(testConnection({ mode: "direct" }, base())).resolves.toMatchObject({ ok: true });
    await expect(testConnection({ mode: "direct" }, base({ password: "mauvais" }))).rejects.toThrow("auth");
    await expect(testConnection({ mode: "direct" }, base({ password: "" }))).rejects.toThrow("auth");
  });
});

describe("connexion par code (plex.tv)", () => {
  it("code, jeton du compte, serveurs, meilleure connexion", async () => {
    const acc = new PlexAccount({ mode: "direct" });
    expect(await acc.createPin()).toMatchObject({ id: 7, code: "ABCD" });
    expect(calls[0]).toMatchObject({ method: "POST", accept: "application/json" });
    expect(calls[0]!.url).toContain("strong=false");
    expect(await acc.pollPin(7)).toBe("acct-tok");
    const servers = await acc.servers("acct-tok");
    expect(servers.map((s) => s.name)).toEqual(["Salon"]);
    expect((await acc.bestConnection(servers[0]!))!.uri).toBe(BASE);
    expect(calls.find((c) => c.url.includes("/api/v2/resources"))!.url).toContain("includeHttps=1");
  });
});

describe("lecture d'un titre Plex", () => {
  it("film : lecture directe avec jeton à la volée, reprise du serveur, rapports timeline", async () => {
    await sync();
    const s = (await getSource(1))!;
    const target = { kind: "movie" as const, sourceId: 1, cid: s.cid, refId: 101, title: "Film 1", startAt: 30 };
    expect(await mediaItemIdOf(s, target)).toBe("101");
    const p = (await mediaPlayback(s, target))!;
    expect(p.candidates[0]).toMatchObject({ format: "native", label: "MKV" });
    expect(p.candidates[0]!.url.startsWith(`${BASE}/library/parts/101/1/file.mkv?`)).toBe(true);
    expect(p.candidates[0]!.url).toContain(`X-Plex-Token=${TOKEN}`);
    expect(p.candidates[1]).toMatchObject({ format: "hls" });
    expect(p.candidates[1]!.url).toContain("/video/:/transcode/universal/start.m3u8?");
    expect(p.resumeAt).toBe(600);   // position du serveur (600 s) > position locale (30 s)

    const mc = await mediaClientFor(s);
    await mc.report("progress", "101", 610, false);
    await mc.report("stop", "101", 620, true);
    const t = calls.filter((c) => c.url.includes("/:/timeline")).map((c) => new URL(c.url).searchParams);
    expect(t.map((q) => q.get("state"))).toEqual(["playing", "stopped"]);
    expect(t[0]!.get("time")).toBe("610000");
    expect(t[0]!.get("duration")).toBe("5400000");
  });

  it("film vu en entier : repart du début, pas de la position locale périmée", async () => {
    await sync();
    const s = (await getSource(1))!;
    const p = (await mediaPlayback(s, { kind: "movie", sourceId: 1, cid: s.cid, refId: 102, title: "Film 2", startAt: 90 }))!;
    expect(p.resumeAt).toBeUndefined();
  });

  it("épisode retrouvé via les épisodes du serveur ; titre inconnu : pas de lecture", async () => {
    await sync();
    const s = (await getSource(1))!;
    expect(await mediaItemIdOf(s, { kind: "episode", sourceId: 1, cid: s.cid, refId: 601, title: "Série 1", seriesId: 500 })).toBe("601");
    expect(await mediaPlayback(s, { kind: "movie", sourceId: 1, cid: s.cid, refId: 424242, title: "?" })).toBeNull();
  });
});
