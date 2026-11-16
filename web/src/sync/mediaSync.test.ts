// Synchronisation d'un serveur Jellyfin / Emby (faux serveur en mémoire) : connexion, bibliothèques = catégories, films et séries
// indexés dans le catalogue local, jeton absent de tout ce qui est stocké, mode clé API, refus d'identifiants, lecture (adresses, reprise).
import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { db } from "@/db/db";
import { emptySource, getSource, saveSource } from "@/db/sources";
import type { Source } from "@/db/types";
import { numericId } from "@/net/mediaServer";
import { mediaItemIdOf, mediaPlayback } from "@/player/resolve";
import { runSync, testConnection } from "./core";

const BASE = "http://jf.invalid:8096";
const TOKEN = "jeton-secret-xyz";
const G = (n: number) => n.toString(16).padEnd(32, "0");   // identifiants de 32 chiffres hexadécimaux, comme ceux du serveur
const MOVIES = [G(0xa1), G(0xa2), G(0xa3)];
const SERIES = [G(0xb1), G(0xb2)];
const EPS = [G(0xc1), G(0xc2)];

let calls: Array<{ method: string; url: string; body?: string; auth?: string }>;
let wrongPassword = false;

/** Faux serveur : mêmes routes que Jellyfin / Emby ; refuse tout appel sans jeton valide (en-tête ou paramètre api_key). */
const fakeFetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = new URL(String(input));
  const method = init?.method ?? "GET";
  const headers = new Headers(init?.headers);
  calls.push({ method, url: url.toString(), body: typeof init?.body === "string" ? init.body : undefined, auth: headers.get("authorization") ?? undefined });
  const json = (o: unknown, status = 200) => new Response(JSON.stringify(o), { status, headers: { "Content-Type": "application/json" } });
  const path = url.pathname;
  if (path === "/Users/AuthenticateByName") {
    const b = JSON.parse(String(init?.body));
    return b.Pw === "bon" && !wrongPassword ? json({ User: { Id: "u1", Name: b.Username }, AccessToken: TOKEN }) : new Response("", { status: 401 });
  }
  const key = url.searchParams.get("api_key");
  if (path === "/Users") return key === "cle-api" ? json([{ Id: "u1", Name: "admin", Policy: { IsAdministrator: true } }]) : new Response("", { status: 401 });
  if (key !== TOKEN && key !== "cle-api") return new Response("", { status: 401 });
  if (path === "/Users/u1/Views") return json({ Items: [{ Name: "Films", Id: "lib1", CollectionType: "movies" }, { Name: "Séries", Id: "lib2", CollectionType: "tvshows" }, { Name: "Musique", Id: "lib3", CollectionType: "music" }] });
  if (path === "/Users/u1/Items") {
    if (url.searchParams.get("IncludeItemTypes") === "Series") return json({ Items: SERIES.map((id, i) => ({ Id: id, Name: `Série ${i + 1}`, Type: "Series", ProductionYear: 2020, ImageTags: { Primary: "t" } })), TotalRecordCount: SERIES.length });
    return json({ Items: MOVIES.map((id, i) => ({ Id: id, Name: `Film ${i + 1} (2001)`, Type: "Movie", CommunityRating: 7, ImageTags: { Primary: "p" }, MediaSources: [{ Id: id, Container: "mkv" }] })), TotalRecordCount: MOVIES.length });
  }
  if (path === `/Users/u1/Items/${MOVIES[0]}`) return json({ Id: MOVIES[0], Name: "Film 1", MediaSources: [{ Id: "srcA", Container: "mp4" }], UserData: { PlaybackPositionTicks: 6_000_000_000, Played: false } });
  if (path === `/Shows/${SERIES[0]}/Episodes`) return json({ Items: EPS.map((id, i) => ({ Id: id, Name: `Ép ${i + 1}`, Type: "Episode", ParentIndexNumber: 1, IndexNumber: i + 1 })), TotalRecordCount: 2 });
  if (path.startsWith("/Sessions/Playing")) return new Response(null, { status: 204 });
  return new Response("", { status: 404 });
});

const base = (o: Partial<Source> = {}): Source => ({ ...emptySource(), id: 1, name: "NAS", type: "jellyfin", server: "jf.invalid:8096/web/", username: "lilou", password: "bon", ...o });

async function sync(s?: Source) {
  const source = (await getSource(1)) ?? s ?? base();
  return runSync({ source, transport: { mode: "direct" }, onProgress: () => undefined });
}

beforeEach(async () => {
  calls = []; wrongPassword = false;
  vi.stubGlobal("fetch", fakeFetch);
  await Promise.all([db.sources, db.categories, db.channels, db.movies, db.series, db.details].map((t) => t.clear()));
  await saveSource(base());
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe("synchro Jellyfin / Emby", () => {
  it("bibliothèques en catégories, films et séries indexés, ni direct ni musique", async () => {
    const counts = await sync();
    expect(counts).toEqual({ live: 0, movie: 3, series: 2 });
    const cid = (await db.sources.get(1))!.cid;
    const cats = await db.categories.where("sourceId").equals(cid).toArray();
    expect(cats.map((c) => [c.kind, c.extId, c.name, c.count]).sort()).toEqual([["movie", "lib1", "Films", 3], ["series", "lib2", "Séries", 2]]);
    const movies = await db.movies.where("sourceId").equals(cid).sortBy("ord");
    expect(movies.map((m) => m.title)).toEqual(["Film 1", "Film 2", "Film 3"]);
    expect(movies[0]).toMatchObject({ catExt: "lib1", streamId: numericId(MOVIES[0]!), year: 2001, ext: "mkv", rating: 7, url: `media:${MOVIES[0]}`, poster: `${BASE}/Items/${MOVIES[0]}/Images/Primary?maxWidth=480&quality=90&tag=p` });
    const series = await db.series.where("sourceId").equals(cid).sortBy("ord");
    expect(series[0]).toMatchObject({ catExt: "lib2", seriesId: numericId(SERIES[0]!), portalId: SERIES[0], url: `media:${SERIES[0]}` });
    expect(new Set(movies.map((m) => m.streamId)).size).toBe(3);
  });

  it("le jeton n'est stocké nulle part (catalogue, source)", async () => {
    await sync();
    const dump = JSON.stringify([await db.movies.toArray(), await db.series.toArray(), await db.categories.toArray(), await db.details.toArray()]);
    expect(dump).not.toContain(TOKEN);
    expect(dump).not.toContain("api_key");
    const stored = JSON.stringify(await db.sources.toArray());
    expect(stored).not.toContain(TOKEN);
  });

  it("connexion par POST avec l'en-tête d'appareil ; requêtes suivantes avec la clé api_key", async () => {
    await sync();
    const login = calls.find((c) => c.url.endsWith("/Users/AuthenticateByName"))!;
    expect(login.method).toBe("POST");
    expect(JSON.parse(login.body!)).toEqual({ Username: "lilou", Pw: "bon" });
    expect(login.auth).toContain('MediaBrowser Client="Ultra TV"');
    expect(calls.filter((c) => c.url.includes("/Users/u1/")).every((c) => c.url.includes(`api_key=${TOKEN}`))).toBe(true);
    expect(calls.filter((c) => c.url.endsWith("/Users/AuthenticateByName"))).toHaveLength(1);   // une seule connexion pour toute la synchro
  });

  it("seconde synchro : rien de dupliqué", async () => {
    await sync();
    const counts = await sync();
    expect(counts).toEqual({ live: 0, movie: 3, series: 2 });
    const cid = (await db.sources.get(1))!.cid;
    expect(await db.movies.where("sourceId").equals(cid).count()).toBe(3);
    expect(await db.movies.count()).toBe(3);   // l'ancienne génération est purgée
  });

  it("mode clé API : identifiant vide, aucune connexion par mot de passe", async () => {
    await saveSource(base({ username: "", password: "cle-api" }));
    expect(await sync()).toEqual({ live: 0, movie: 3, series: 2 });
    expect(calls.some((c) => c.url.endsWith("/Users/AuthenticateByName"))).toBe(false);
  });

  it("identifiants refusés : erreur « auth », catalogue précédent intact", async () => {
    await sync();
    wrongPassword = true;
    await expect(sync()).rejects.toThrow("auth");
    const cid = (await db.sources.get(1))!.cid;
    expect(await db.movies.where("sourceId").equals(cid).count()).toBe(3);
  });

  it("test de connexion : ok, ou « auth »", async () => {
    await expect(testConnection({ mode: "direct" }, base())).resolves.toMatchObject({ ok: true });
    await expect(testConnection({ mode: "direct" }, base({ password: "mauvais" }))).rejects.toThrow("auth");
    await expect(testConnection({ mode: "direct" }, base({ server: "" }))).rejects.toThrow("auth");
  });
});

describe("lecture d'un titre du serveur", () => {
  it("film : adresse directe puis HLS, jeton en paramètre, reprise lue sur le serveur", async () => {
    await sync();
    const s = (await getSource(1))!;
    const target = { kind: "movie" as const, sourceId: 1, cid: s.cid, refId: numericId(MOVIES[0]!), title: "Film 1", startAt: 30 };
    expect(await mediaItemIdOf(s, target)).toBe(MOVIES[0]);
    const p = (await mediaPlayback(s, target))!;
    expect(p.candidates[0]).toMatchObject({ format: "native", label: "MP4", url: `${BASE}/Videos/${MOVIES[0]}/stream.mp4?static=true&MediaSourceId=srcA&api_key=${TOKEN}` });
    expect(p.candidates[1]).toMatchObject({ format: "hls" });
    expect(p.candidates[1]!.url).toContain("/master.m3u8?");
    expect(p.resumeAt).toBe(600);   // la position du serveur (600 s) prime sur la position locale (30 s)
  });

  it("film sans position serveur : la position locale est conservée", async () => {
    await sync();
    const s = (await getSource(1))!;
    const p = (await mediaPlayback(s, { kind: "movie", sourceId: 1, cid: s.cid, refId: numericId(MOVIES[1]!), title: "Film 2", startAt: 90 }))!;
    // La fiche du film 2 est introuvable (404) : adresse nue, reprise locale.
    expect(p.candidates[0]!.url).toBe(`${BASE}/Videos/${MOVIES[1]}/stream?static=true&api_key=${TOKEN}`);
    expect(p.resumeAt).toBe(90);
  });

  it("épisode : identifiant retrouvé via les épisodes du serveur quand la fiche n'est pas en cache", async () => {
    await sync();
    const s = (await getSource(1))!;
    const t = { kind: "episode" as const, sourceId: 1, cid: s.cid, refId: numericId(EPS[1]!), title: "Série 1", seriesId: numericId(SERIES[0]!) };
    expect(await mediaItemIdOf(s, t)).toBe(EPS[1]);
  });

  it("titre inconnu : pas de lecture", async () => {
    await sync();
    const s = (await getSource(1))!;
    expect(await mediaPlayback(s, { kind: "movie", sourceId: 1, cid: s.cid, refId: 424242, title: "?" })).toBeNull();
  });
});
