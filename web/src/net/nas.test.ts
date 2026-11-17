// Partage NAS monté par le système : liens internes, indexation (films / séries / épisodes par nom de fichier), adresse de lecture,
// synchronisation dans le catalogue local (faux pont Electron), lecture. Les mêmes cas de nommage que sur Android (lib/nasNames.test.ts).
import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { db } from "@/db/db";
import { emptySource, getSource, saveSource } from "@/db/sources";
import type { Source } from "@/db/types";
import { nasCandidate, nasRelOf } from "@/player/resolve";
import { runSync, testConnection } from "@/sync/core";
import { buildNasCatalog, isNasLink, nasLink, nasRootName, nasSeriesInfo, nasUrl, numericHash, parseNasLink, type NasFile } from "./nas";

const f = (path: string, mtime = 100): NasFile => ({ path, size: 1000, mtime });

describe("liens internes", () => {
  it("aller-retour avec accents, espaces et symboles", () => {
    const rel = "Séries/Ma Série (2019)/Saison 1/S01E02 – Épisode #1.mkv";
    const link = nasLink(rel);
    expect(isNasLink(link)).toBe(true);
    expect(link).toMatch(/^nas:[A-Za-z0-9_-]+$/);
    expect(parseNasLink(link)).toBe(rel);
  });
  it("ni hôte ni chemin en clair", () => {
    const link = nasLink("Films/Secret.mkv");
    expect(link).not.toContain("Secret");
    expect(link).not.toContain("smb");
  });
  it("liens invalides ou remontant l'arborescence rejetés", () => {
    expect(parseNasLink("http://x")).toBeNull();
    expect(parseNasLink("nas:")).toBeNull();
    expect(parseNasLink("nas:@@@")).toBeNull();
    expect(parseNasLink(nasLink("a/../../etc/passwd"))).toBeNull();
    expect(isNasLink(null)).toBe(false);
    expect(isNasLink("media:abc")).toBe(false);
  });
  it("adresse de lecture : proxy local, racine, chemin encodé, extension", () => {
    const u = nasUrl("http://127.0.0.1:5000/TOK/", "0123456789ab", "Films/Titre (2019).mkv");
    expect(u).toMatch(/^http:\/\/127\.0\.0\.1:5000\/TOK\/nas\/0123456789ab\/[A-Za-z0-9_-]+\.mkv$/);
    expect(u).not.toContain("Titre");
  });
  it("nom de racine", () => {
    expect(nasRootName("/Volumes/video/Films")).toBe("Films");
    expect(nasRootName("\\\\NAS\\partage\\HD\\")).toBe("HD");
    expect(nasRootName("/")).toBe("/");
  });
  it("identifiant numérique : stable, sûr pour Dexie, peu de collisions", () => {
    expect(numericHash("a/b.mkv")).toBe(numericHash("a/b.mkv"));
    const ids = new Set<number>();
    for (let i = 0; i < 20000; i++) ids.add(numericHash(`Films/Film numéro ${i} (${1950 + (i % 70)}).mkv`));
    expect(ids.size).toBe(20000);
    for (const id of ids) { expect(Number.isSafeInteger(id)).toBe(true); expect(id).toBeGreaterThanOrEqual(0); }
  });
});

describe("indexation du catalogue", () => {
  const FILES = [
    f("Films/Titre (2019).mkv"), f("Films/Autre.2020.1080p.mp4"), f("Racine (2001).avi"),
    f("Séries/Ma Série/Saison 1/Ma Série S01E02.mkv", 300), f("Séries/Ma Série/Saison 1/Ma Série S01E01.The.Start.mkv", 200), f("Séries/Ma Série/Saison 2/Ma Série S02E01.mkv", 250),
    f("Séries/Autre Show/Autre.Show.1x03.avi"),
  ];
  it("films et séries séparés ; catégories = dossiers de premier niveau", () => {
    const c = buildNasCatalog(FILES, "video");
    expect(c.movies.map((m) => m.name).sort()).toEqual(["Autre", "Racine", "Titre"]);
    expect(c.series.map((s) => s.name).sort()).toEqual(["Autre Show", "Ma Série"]);
    expect(c.movieCats.map((x) => [x.extId, x.name]).sort()).toEqual([["Films", "Films"], ["_root", "video"]]);
    expect(c.seriesCats).toEqual([{ extId: "Séries", name: "Séries" }]);
    const titre = c.movies.find((m) => m.name === "Titre")!;
    expect(titre).toMatchObject({ catExt: "Films", year: 2019, ext: "mkv" });
    expect(parseNasLink(titre.url)).toBe("Films/Titre (2019).mkv");
    expect(c.movies.find((m) => m.name === "Racine")!.catExt).toBe("_root");
  });
  it("épisodes rangés par série, ordonnés ; titre absent = S01E02", () => {
    const c = buildNasCatalog(FILES, "video");
    const s = c.series.find((x) => x.name === "Ma Série")!;
    expect(s.added).toBe(300);
    const eps = c.episodes[s.portalId]!;
    expect(eps.map((e) => [e.season, e.episode])).toEqual([[1, 1], [1, 2], [2, 1]]);
    expect(eps[0]!.title).toBe("The Start");
    expect(eps[1]!.title).toBe("S01E02");
    expect(new Set(eps.map((e) => e.id)).size).toBe(3);
    const info = nasSeriesInfo(eps);
    expect(Object.keys(info.episodes)).toEqual(["1", "2"]);
    expect(info.media).toHaveLength(3);
    expect(info.media.every((m) => parseNasLink(m.url) !== null)).toBe(true);
  });
  it("séries regroupées malgré les écritures différentes", () => {
    const c = buildNasCatalog([f("A/La Casa de Papel/La.Casa.de.Papel.S01E01.mkv"), f("A/La Casa de Papel/la casa de papel S01E02.mkv"), f("A/Élite/Elite.S01E01.mkv"), f("A/Élite/Élite S01E02.mkv")], "r");
    expect(c.series).toHaveLength(2);
    expect(Object.values(c.episodes).map((e) => e.length)).toEqual([2, 2]);
  });
  it("catalogue vide", () => {
    const c = buildNasCatalog([], "r");
    expect(c.movies).toEqual([]); expect(c.series).toEqual([]); expect(c.movieCats).toEqual([]);
  });
});

// ---------------------------------------------------------------- synchro et lecture, avec un faux pont Electron

const ROOT = "/Volumes/video";
let scanned: Array<{ root: string; probe: boolean }>;
let scanFails = false;
const FILES = [f("Films/Titre (2019).mkv"), f("Films/Autre.2020.mp4"), f("Séries/Show/Saison 1/Show.S01E01.mkv"), f("Séries/Show/Saison 1/Show.S01E02.mkv")];
const BRIDGE = {
  isElectron: true, platform: "darwin", proxyBase: "http://127.0.0.1:5000/TOK/",
  nasScan: vi.fn(async (root: string, probe?: boolean) => {
    scanned.push({ root, probe: !!probe });
    if (scanFails) throw new Error("scan-failed");
    return { id: "0123456789ab", files: probe ? [] : FILES, truncated: false };
  }),
  nasRegister: vi.fn(async (root: string) => (root === ROOT ? "0123456789ab" : null)),
};

const base = (o: Partial<Source> = {}): Source => ({ ...emptySource(), id: 1, name: "NAS", type: "nas", server: ROOT, ...o });
const sync = async () => runSync({ source: (await getSource(1))!, transport: { mode: "direct" }, onProgress: () => undefined });

beforeEach(async () => {
  scanned = []; scanFails = false;
  vi.stubGlobal("window", { ultratv: BRIDGE });
  await Promise.all([db.sources, db.categories, db.channels, db.movies, db.series, db.details].map((t) => t.clear()));
  await saveSource(base());
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe("synchro d'un partage NAS", () => {
  it("films et séries indexés comme une source VOD, épisodes rangés dans les fiches", async () => {
    expect(await sync()).toEqual({ live: 0, movie: 2, series: 1 });
    expect(scanned).toEqual([{ root: ROOT, probe: false }]);
    const cid = (await db.sources.get(1))!.cid;
    const cats = await db.categories.where("sourceId").equals(cid).toArray();
    expect(cats.map((c) => [c.kind, c.extId, c.count]).sort()).toEqual([["movie", "Films", 2], ["series", "Séries", 1]]);
    const movies = await db.movies.where("sourceId").equals(cid).sortBy("ord");
    expect(movies.map((m) => m.title)).toEqual(["Autre", "Titre"]);
    expect(movies.find((m) => m.title === "Titre")).toMatchObject({ year: 2019, ext: "mkv", poster: null, catExt: "Films" });
    const series = (await db.series.where("sourceId").equals(cid).toArray())[0]!;
    expect(series).toMatchObject({ name: "Show", catExt: "Séries", poster: null });
    const det = (await db.details.get(`series:1:${series.seriesId}`))!.json as { episodes: Record<string, unknown[]>; media: unknown[] };
    expect(det.episodes["1"]).toHaveLength(2);
    expect(det.media).toHaveLength(2);
  });

  it("aucun chemin d'accès ni identifiant dans les lignes : des liens internes seulement", async () => {
    await sync();
    const dump = JSON.stringify([await db.movies.toArray(), await db.series.toArray(), await db.details.toArray()]);
    expect(dump).not.toContain("Volumes");
    expect(dump).not.toContain("127.0.0.1");
    expect(dump).not.toContain("Titre (2019).mkv");   // chemin en base64 dans le lien, jamais en clair
  });

  it("seconde synchro : rien de dupliqué", async () => {
    await sync();
    expect(await sync()).toEqual({ live: 0, movie: 2, series: 1 });
    expect(await db.movies.count()).toBe(2);
  });

  it("parcours en échec : erreur remontée, catalogue précédent intact", async () => {
    await sync();
    scanFails = true;
    await expect(sync()).rejects.toThrow("scan-failed");
    const cid = (await db.sources.get(1))!.cid;
    expect(await db.movies.where("sourceId").equals(cid).count()).toBe(2);
  });

  it("test de connexion : sonde de la racine seule ; hors application de bureau « nas-unavailable »", async () => {
    await expect(testConnection({ mode: "direct" }, base())).resolves.toMatchObject({ ok: true });
    expect(scanned).toEqual([{ root: ROOT, probe: true }]);
    scanFails = true;
    await expect(testConnection({ mode: "direct" }, base())).rejects.toThrow("scan-failed");
    vi.stubGlobal("window", {});
    await expect(testConnection({ mode: "direct" }, base())).rejects.toThrow("nas-unavailable");
  });
});

describe("lecture d'un partage NAS", () => {
  const target = (o: object) => ({ kind: "movie", cid: 0, sourceId: 1, refId: 0, title: "t", ...o }) as unknown as Parameters<typeof nasCandidate>[1];

  it("film : adresse du proxy local ; épisode : retrouvé dans la fiche de la série", async () => {
    await sync();
    const cid = (await db.sources.get(1))!.cid;
    const source = (await getSource(1))!;
    const m = (await db.movies.where("sourceId").equals(cid).toArray()).find((x) => x.title === "Titre")!;
    const c = await nasCandidate(source, target({ cid, refId: m.streamId }));
    expect(c).toMatchObject({ format: "native", label: "MKV" });
    expect(c!.url).toMatch(/^http:\/\/127\.0\.0\.1:5000\/TOK\/nas\/0123456789ab\/[A-Za-z0-9_-]+\.mkv$/);
    expect(c!.url).not.toContain("Volumes");
    expect(BRIDGE.nasRegister).toHaveBeenCalledWith(ROOT);

    const s = (await db.series.where("sourceId").equals(cid).toArray())[0]!;
    const det = (await db.details.get(`series:1:${s.seriesId}`))!.json as { media: Array<{ id: number; url: string }> };
    const ep = det.media[1]!;
    expect(await nasRelOf(source, target({ kind: "episode", cid, refId: ep.id, seriesId: s.seriesId }))).toBe("Séries/Show/Saison 1/Show.S01E02.mkv");
    // Lien porté par la cible : utilisé tel quel.
    expect(await nasRelOf(source, target({ url: nasLink("Films/Autre.2020.mp4") }))).toBe("Films/Autre.2020.mp4");
  });

  it("racine refusée par le processus principal, ou hors application de bureau : pas de lecture", async () => {
    await sync();
    const cid = (await db.sources.get(1))!.cid;
    const m = (await db.movies.where("sourceId").equals(cid).toArray())[0]!;
    expect(await nasCandidate(base({ server: "/ailleurs" }), target({ cid, refId: m.streamId }))).toBeNull();
    vi.stubGlobal("window", {});
    expect(await nasCandidate(base(), target({ cid, refId: m.streamId }))).toBeNull();
    expect(await nasRelOf(base(), target({ cid, refId: 424242 }))).toBeNull();
  });
});
