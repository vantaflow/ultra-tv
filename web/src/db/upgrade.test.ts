// Mise à jour depuis le bureau 1.2.38 : une base écrite par l'ancienne version (schéma recopié du tag desktop-v1.2.38)
// doit s'ouvrir avec le code actuel sans perdre ni catégories, ni éléments, ni préférences, puis rester complète
// après la première synchro (catégories Séries du module type=series préfixées « s: » comprises).
import "fake-indexeddb/auto";
import Dexie from "dexie";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { db } from "./db";
import { getSource } from "./sources";
import { FAKE_API, FAKE_MAC, FAKE_PORTAL, FakeVodPortal } from "@/net/fakeVodPortal";
import { runSync } from "@/sync/core";
import { catalogNeedsRebuild, claimRebuild, resetRebuildAttempts } from "@/sync/rebuild";
import type { CategoryRow, Source } from "./types";

const LEGACY_V1 = {
  sources: "++id",
  categories: "++id, [sourceId+kind], [sourceId+kind+extId]",
  channels: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+epg], [sourceId+streamId]",
  movies: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+streamId]",
  series: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+seriesId]",
  programs: "++id, [sourceId+epg+start], [sourceId+end]",
  favorites: "&key, [profile+sourceId+kind], addedAt",
  history: "&key, [profile+sourceId], updatedAt",
  details: "&key",
  settings: "&key",
};

const cat = (kind: CategoryRow["kind"], extId: string, name: string, enabled: 0 | 1, count: number, ord: number): CategoryRow =>
  ({ sourceId: 1, kind, extId, name, label: name, badge: null, count, enabled, adult: 0, ord });

const SOURCE: Source = {
  name: "Portail", type: "stalker", server: FAKE_PORTAL, username: FAKE_MAC, password: "", m3uUrl: "", stalkerApi: FAKE_API, epgUrl: "", userAgent: "", referer: "",
  cid: 1, langs: ["FR"], createdAt: 1, lastSyncAt: 1_000_000, counts: { live: 2, movie: 3, series: 2 }, expDate: null, maxConnections: 1, state: "ready",
};

/** Base telle que l'écrivait le bureau 1.2.38 : une source synchronisée (génération 1), catégories dont une masquée, préférences. */
async function writeLegacyDb(): Promise<void> {
  const old = new Dexie("ultratv-desktop");
  old.version(1).stores(LEGACY_V1);
  await old.open();
  await old.table("sources").add({ ...SOURCE, id: 1 });
  await old.table("categories").bulkAdd([
    cat("live", "5", "FR | News", 1, 2, 0),
    cat("movie", "7", "FR | Films", 1, 3, 0),
    cat("movie", "8", "FR | Autres", 0, 0, 1), // masquée à la main
    cat("series", "7", "Séries", 1, 2, 0), // activée à la main (nom sans code de langue)
  ]);
  await old.table("channels").bulkAdd([
    { sourceId: 1, catExt: "5", ord: 0, streamId: 1, num: 1, name: "FR: A", display: "A", norm: "a", country: "FR", q: "", flags: [], sep: 0, logo: null, epg: null, archive: 0, url: "stalker:itv::x" },
    { sourceId: 1, catExt: "5", ord: 1, streamId: 2, num: 2, name: "FR: B", display: "B", norm: "b", country: "FR", q: "", flags: [], sep: 0, logo: null, epg: null, archive: 0, url: "stalker:itv::y" },
  ]);
  await old.table("movies").bulkAdd([0, 1, 2].map((i) => ({ sourceId: 1, catExt: "7", ord: i, streamId: i, name: `Film ${i}`, title: `Film ${i}`, norm: `film ${i}`, year: 2020, poster: null, rating: 0, ext: "mp4", added: i })));
  await old.table("series").bulkAdd([0, 1].map((i) => ({ sourceId: 1, catExt: "7", ord: i, seriesId: i, name: `Série ${i}`, title: `Série ${i}`, norm: `serie ${i}`, year: null, poster: null, backdrop: null, plot: null, rating: 0, added: i })));
  await old.table("settings").bulkAdd([{ key: "cid.counter", value: 1 }, { key: "migr.catLabels.v1", value: 1 }, { key: "epg.extra", value: "https://g.test/epg.xml" }]);
  await old.table("favorites").add({ key: "main:1:live:1", profile: "main", sourceId: 1, kind: "live", refId: 1, addedAt: 1 });
  old.close();
}

beforeAll(async () => { await writeLegacyDb(); });

describe("ouverture d'une base du bureau 1.2.38", () => {
  it("schéma, catégories, éléments, favoris et préférences intacts (mise à niveau v1 -> actuelle)", async () => {
    const s = (await getSource(1))!;
    expect(s).toMatchObject({ cid: 1, state: "ready", langs: ["FR"], lastSyncAt: 1_000_000 });
    const cats = await db.categories.where("[sourceId+kind]").between([1, ""], [1, "￿"]).toArray();
    expect(cats.map((c) => [c.kind, c.extId, c.enabled, c.count])).toEqual([["live", "5", 1, 2], ["movie", "7", 1, 3], ["movie", "8", 0, 0], ["series", "7", 1, 2]]);
    expect(await db.channels.count()).toBe(2);
    expect(await db.movies.count()).toBe(3);
    expect(await db.series.count()).toBe(2);
    expect(await db.favorites.count()).toBe(1);
    // Tables ajoutées par les versions 4 et 5 du schéma : présentes et vides après la mise à niveau.
    expect(await db.channelOrder.count()).toBe(0);
    expect(await db.hiddenChannels.count()).toBe(0);
    expect((await db.settings.get("epg.extra"))?.value).toBe("https://g.test/epg.xml");
    // Index de recherche ajouté par la mise à niveau.
    expect((await db.movies.toArray()).every((m) => Array.isArray(m.words))).toBe(true);
  });

  it("catalogue complet : aucune récupération déclenchée", async () => {
    expect(await catalogNeedsRebuild((await getSource(1))!)).toBe(false);
  });
});

describe("première synchro après la mise à jour (portail avec module type=series)", () => {
  it("les catégories Séries activées à la main restent visibles malgré le préfixe « s: » ; les choix masqués sont gardés", async () => {
    const fake = new FakeVodPortal();
    fake.vodCategories = { "7": ["FR | Films", 3], "8": ["FR | Autres", 4] };
    fake.seriesCategories = { "1": ["Séries", 30], "2": ["Drames", 5] }; // aucun code de langue : filtré par langues sans la préférence
    vi.stubGlobal("fetch", fake.fetch);
    try {
      const counts = await runSync({ source: (await getSource(1))!, transport: { mode: "direct" }, onProgress: () => {}, preserveFlags: true });
      expect(counts).toEqual({ live: 1, movie: 3, series: 30 });
      const cid = (await getSource(1))!.cid;
      const cats = await db.categories.where("[sourceId+kind]").between([cid, ""], [cid, "￿"]).toArray();
      const visible = (k: string) => cats.filter((c) => c.kind === k && c.enabled && c.count > 0).map((c) => c.extId);
      expect(visible("live")).toEqual(["5"]);
      expect(visible("movie")).toEqual(["7"]); // la catégorie 8 reste masquée
      expect(visible("series")).toEqual(["s:1"]); // « Drames », inconnue avant, suit le filtre de langues;
      expect(await db.series.where("sourceId").equals(cid).count()).toBe(30);
    } finally { vi.unstubAllGlobals(); }
  });
});

describe("récupération au démarrage", () => {
  const base = { lastSyncAt: 5, state: "ready" as const, cid: 9, counts: { live: 0, movie: 0, series: 0 } };
  it("jamais synchronisée, ou en cours : rien à reconstruire", async () => {
    expect(await catalogNeedsRebuild({ ...base, lastSyncAt: 0 })).toBe(false);
    expect(await catalogNeedsRebuild({ ...base, state: "new" })).toBe(false);
    expect(await catalogNeedsRebuild({ ...base, state: "syncing" })).toBe(false);
  });
  it("synchronisée mais sans génération ou sans aucune catégorie : reconstruire", async () => {
    expect(await catalogNeedsRebuild({ ...base, cid: 0 })).toBe(true);
    expect(await catalogNeedsRebuild({ ...base, cid: 42 })).toBe(true);
  });
  it("une partie qui avait des éléments n'en a plus : reconstruire", async () => {
    const s = (await getSource(1))!;
    expect(await catalogNeedsRebuild({ ...s, counts: { live: 1, movie: 1, series: 1 } })).toBe(false);
    await db.movies.where("sourceId").equals(s.cid).delete();
    expect(await catalogNeedsRebuild({ ...s, counts: { live: 1, movie: 1, series: 1 } })).toBe(true);
    expect(await catalogNeedsRebuild({ ...s, counts: { live: 1, movie: 0, series: 1 } })).toBe(false);
  });
  it("une seule tentative par démarrage et par source (pas de boucle sur une source vide)", () => {
    resetRebuildAttempts();
    expect(claimRebuild(3)).toBe(true);
    expect(claimRebuild(3)).toBe(false);
    expect(claimRebuild(4)).toBe(true);
  });
});

describe("applyStalkerFlags : changement d'identifiants des séries", () => {
  const mk = (extId: string, name: string, enabled: 0 | 1): CategoryRow => cat("series", extId, name, enabled, 0, 0);
  it("repli par nom, sinon réactivation si l'utilisateur avait des séries et que le filtre de langues les masque toutes", async () => {
    const { applyStalkerFlags } = await import("@/sync/core");
    const flags = new Map<string, 0 | 1>([["series:7", 1], ["series#Séries", 1], ["series:9", 0], ["series#Anciennes", 0]]);
    const byName = [mk("s:1", "Séries", 0), mk("s:2", "Drames", 0)];
    applyStalkerFlags(byName, flags);
    expect(byName.map((c) => c.enabled)).toEqual([1, 0]);
    const renamed = [mk("s:1", "Nouveautés", 0), mk("s:2", "Drames", 0)];
    applyStalkerFlags(renamed, flags);
    expect(renamed.map((c) => c.enabled)).toEqual([1, 1]);
    // Aucune série activée avant : on respecte le filtre.
    const none = [mk("s:1", "Nouveautés", 0)];
    applyStalkerFlags(none, new Map([["series:9", 0]]));
    expect(none[0]!.enabled).toBe(0);
  });
});
