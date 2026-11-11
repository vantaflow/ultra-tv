// Synchronisation complète d'un portail Stalker (faux portail en mémoire) : module `type=series`, catégorie en erreur
// sautée sans faire échouer la synchro (contenu précédent gardé), jeton expiré en route, liste de catégories illisible.
import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { db } from "@/db/db";
import { emptySource, getSource, saveSource } from "@/db/sources";
import type { Source, SyncProgress } from "@/db/types";
import { translate } from "@/i18n";
import { FAKE_API, FAKE_MAC, FAKE_PORTAL, FakeVodPortal } from "@/net/fakeVodPortal";
import { parseStalkerLink, retryDefaults } from "@/net/stalker";
import { runSync } from "./core";

let fake: FakeVodPortal;
let infos: string[];

const base = (): Source => ({ ...emptySource(), id: 1, name: "Portail", type: "stalker", server: FAKE_PORTAL, username: FAKE_MAC, stalkerApi: FAKE_API });

/** Une synchro, comme le fait le magasin : la source est relue (sa génération courante) avant chaque exécution. */
async function sync(): Promise<{ counts: { live: number; movie: number; series: number }; last: SyncProgress }> {
  const source = (await getSource(1)) ?? base();
  let last!: SyncProgress;
  const counts = await runSync({ source, transport: { mode: "direct" }, onProgress: (p) => { last = p; } });
  return { counts, last };
}
const count = async (table: "movies" | "series" | "channels") => { const cid = (await db.sources.get(1))!.cid; return db[table].where("[sourceId+ord]").between([cid, -1], [cid, Infinity]).count(); };

beforeEach(async () => {
  fake = new FakeVodPortal();
  retryDefaults.delayMs = 1;
  vi.stubGlobal("fetch", fake.fetch);
  infos = [];
  vi.spyOn(console, "info").mockImplementation((m: unknown) => { infos.push(String(m)); });
  await Promise.all([db.sources, db.categories, db.channels, db.movies, db.series].map((t) => t.clear()));
  await saveSource(base());
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); retryDefaults.delayMs = 700; });

describe("synchro Stalker : séries du module type=series", () => {
  it("séries uniquement en type=series (la VOD n'a aucune catégorie)", async () => {
    const { counts, last } = await sync();
    expect(counts).toEqual({ live: 1, movie: 0, series: 39 });
    expect(last.phase).toBe("done");
    expect(last.skipped).toBeUndefined();
    const cid = (await db.sources.get(1))!.cid;
    const cats = await db.categories.where("[sourceId+kind]").equals([cid, "series"]).toArray();
    expect(cats.map((c) => [c.extId, c.count])).toEqual([["s:1", 30], ["s:2", 5], ["s:3", 4]]);
    const rows = await db.series.where("sourceId").equals(cid).toArray();
    expect(rows).toHaveLength(39);
    expect(new Set(rows.map((r) => r.seriesId)).size).toBe(39);
    expect(rows[0]).toMatchObject({ catExt: "s:1", portalId: "1-0" });
    expect(parseStalkerLink(rows[0]!.url!)).toEqual({ type: "series", cmd: "", series: null });
  });

  it("portail sans module : séries tirées de la VOD, comme avant", async () => {
    fake.seriesCategories = {};
    fake.vodCategories = { "7": ["FR Films", 20] };
    fake.vodSeriesEvery = 5;
    const { counts } = await sync();
    expect(counts).toEqual({ live: 1, movie: 16, series: 4 });
    const cid = (await db.sources.get(1))!.cid;
    const s = (await db.series.where("sourceId").equals(cid).toArray())[0]!;
    expect(parseStalkerLink(s.url!)!.type).toBe("vod");
    expect(s.eps).toEqual([1, 2, 3]);
    expect(s.portalId).toBeUndefined();
  });
});

describe("synchro Stalker : tolérante", () => {
  it("catégorie qui répond toujours 500 : sautée, synchro réussie, « 1 catégorie ignorée », ancien contenu gardé", async () => {
    fake.failingCategories.add("3");
    const first = await sync();
    expect(first.counts.series).toBe(35); // 30 + 5, la catégorie 3 est sautée
    expect(first.last.skipped).toBe(1);
    expect(translate("fr", "sync.doneSkipped", { count: first.last.skipped! })).toBe("Synchronisation terminée · 1 catégorie ignorée");
    expect((await db.sources.get(1))!.state).toBe("ready");
    // Exactement 3 tentatives sur la liste de la catégorie en panne.
    expect(fake.urls.filter((u) => u.searchParams.get("category") === "3")).toHaveLength(3);

    // Tout répond : 39 séries. Puis la catégorie 2 tombe : ses 5 séries restent.
    fake.failingCategories.clear();
    expect((await sync()).counts.series).toBe(39);
    fake.failingCategories.add("2");
    const again = await sync();
    expect(again.counts.series).toBe(39);
    expect(again.last.skipped).toBe(1);
    const cid = (await db.sources.get(1))!.cid;
    expect(await db.series.where("[sourceId+catExt+ord]").between([cid, "s:2", -1], [cid, "s:2", Infinity]).count()).toBe(5);
    expect((await db.categories.where("[sourceId+kind+extId]").equals([cid, "series", "s:2"]).first())!.count).toBe(5);
    // Un seul catalogue vivant : l'ancienne génération est nettoyée.
    expect(await db.series.count()).toBe(39);
  });

  it("films : catégorie VOD en panne gardée, comptée une seule fois même en Films puis en Séries", async () => {
    fake.seriesCategories = {};
    fake.vodCategories = { "7": ["FR Films", 20], "8": ["FR Autres", 10] };
    fake.vodSeriesEvery = 5;
    const ok = await sync();
    expect(ok.counts).toEqual({ live: 1, movie: 16 + 8, series: 4 + 2 });
    fake.failingCategories.add("7");
    const r = await sync();
    expect(r.counts).toEqual({ live: 1, movie: 24, series: 6 });
    expect(r.last.skipped).toBe(1); // catégorie 7 sautée à la fois pour les films et pour les séries
    const cid = (await db.sources.get(1))!.cid;
    const m = await db.movies.where("sourceId").equals(cid).toArray();
    expect(m).toHaveLength(24);
    // Les mots de recherche sont recalculés pour la nouvelle génération.
    expect(m.every((x) => (x.words ?? []).every((w) => w.startsWith(`${cid}|`)))).toBe(true);
  });

  it("jeton qui expire après N requêtes de liste : tout est chargé, nouvelle poignée de main", async () => {
    fake.seriesCategories = { "1": ["FR Séries", 100], "2": ["FR Drames", 100] };
    fake.expireAfterListRequests = 5;
    const { counts, last } = await sync();
    expect(counts.series).toBe(200);
    expect(last.skipped).toBeUndefined();
    expect(fake.handshakes).toBeGreaterThanOrEqual(2);
  });

  it("liste des catégories VOD en 503 : partie sautée, direct et séries présents", async () => {
    fake.vodCategoriesStatus = 503;
    const { counts, last } = await sync();
    expect(counts).toEqual({ live: 1, movie: 0, series: 39 });
    expect(last.skipped).toBe(1);
    expect(await count("channels")).toBe(1);
    expect(await count("series")).toBe(39);
    expect((await db.sources.get(1))!.state).toBe("ready");
  });

  it("liste des catégories VOD en 503 à la 2e synchro : les films précédents restent", async () => {
    fake.seriesCategories = {};
    fake.vodCategories = { "7": ["FR Films", 20] };
    expect((await sync()).counts.movie).toBe(20);
    fake.vodCategoriesStatus = 503;
    const r = await sync();
    expect(r.counts.movie).toBe(20);
    expect(r.last.skipped).toBeGreaterThanOrEqual(1);
    expect(await count("movies")).toBe(20);
    expect(await db.movies.count()).toBe(20);
    expect(await db.categories.where("[sourceId+kind]").equals([(await db.sources.get(1))!.cid, "movie"]).count()).toBe(1);
  });

  it("journaux : jamais d'adresse, de MAC ni de nom de catégorie", async () => {
    fake.failingCategories.add("3");
    await sync();
    const text = infos.join("\n");
    expect(text).toContain("stalker sync skip step=list type=series http=500 error=http");
    expect(text).toContain("stalker sync done type=series");
    expect(text).not.toMatch(/portal\.test|00:1A|FR Docs|FR S/);
  });
});
