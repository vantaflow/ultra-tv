import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { db } from "./db";
import { addManual, deleteManual, injectManual, isManualStreamId, isValidLogoUrl, isValidStreamUrl, MANUAL_CAT, manualStreamId, planManual, updateManual } from "./manual";
import type { CategoryRow, ChannelRow, ManualChannelRow, Source } from "./types";

const cat = (extId: string, o: Partial<CategoryRow> = {}): CategoryRow => ({ sourceId: 7, kind: "live", extId, name: `Cat ${extId}`, label: `Cat ${extId}`, badge: null, count: 0, enabled: 1, adult: 0, ord: 0, ...o });
const man = (id: string, catExt: string, o: Partial<ManualChannelRow> = {}): ManualChannelRow =>
  ({ key: `1:custom:${id}`, sourceId: 1, ref: `custom:${id}-0000-4000-8000-000000000000`, name: `M${id}`, url: `http://x.invalid/${id}`, logo: null, epg: null, catExt, catName: null, createdAt: 0, ...o });
const base = { cid: 7, startOrd: 100, startCatOrd: 5, defaultCatName: "Mes chaînes", maxNum: new Map<string, number>() };

describe("planManual (logique pure)", () => {
  it("numéros à la suite de ceux du fournisseur, ordre de création", () => {
    const p = planManual({ ...base, rows: [man("aaaaaaaa", "9", { createdAt: 2 }), man("bbbbbbbb", "9", { createdAt: 1 })], cats: [cat("9")], maxNum: new Map([["9", 40]]) });
    expect(p.channels.map((c) => c.name)).toEqual(["Mbbbbbbbb", "Maaaaaaaa"]);
    expect(p.channels.map((c) => c.num)).toEqual([41, 42]);
    expect(p.channels.map((c) => c.ord)).toEqual([100, 101]);
    expect(p.perCat.get("9")).toBe(2);
    expect(p.customCat).toBeNull();
  });
  it("« Mes chaînes » créée avec le libellé enregistré ; catégorie disparue ⇒ bascule dedans", () => {
    const p = planManual({ ...base, rows: [man("aaaaaaaa", "inconnue", { catName: "Mes chaînes" }), man("bbbbbbbb", MANUAL_CAT, { createdAt: 1 })], cats: [cat("9")] });
    expect(p.channels.every((c) => c.catExt === MANUAL_CAT)).toBe(true);
    expect(p.customCat).toMatchObject({ extId: MANUAL_CAT, name: "Mes chaînes", count: 2, ord: 5, enabled: 1 });
  });
  it("catégorie désactivée : chaîne sautée ; nom numérique ou séparateur : reste une chaîne", () => {
    expect(planManual({ ...base, rows: [man("aaaaaaaa", "9")], cats: [cat("9", { enabled: 0 })] }).channels).toEqual([]);
    const p = planManual({ ...base, rows: [man("aaaaaaaa", "9", { name: "2" }), man("bbbbbbbb", "9", { name: "##### X #####", createdAt: 1 })], cats: [cat("9")] });
    expect(p.channels.map((c) => c.sep)).toEqual([0, 0]);
  });
  it("identifiant de flux stable, ≥ 2^52, dérivé de l'UUID", () => {
    const ref = "custom:123e4567-e89b-42d3-a456-426614174000";
    expect(manualStreamId(ref)).toBe(manualStreamId(ref));
    expect(isManualStreamId(manualStreamId(ref))).toBe(true);
    expect(isManualStreamId(123456)).toBe(false);
    expect(Number.isSafeInteger(manualStreamId(ref))).toBe(true);
    expect(manualStreamId("custom:autre")).not.toBe(manualStreamId("custom:autre2"));
  });
  it("validation des adresses", () => {
    expect(isValidStreamUrl("http://h.tld:80/live/u/p/1.ts")).toBe(true);
    expect(isValidStreamUrl(" rtmp://h.tld/app/x ")).toBe(true);
    expect(isValidStreamUrl("h.tld/x")).toBe(false);
    expect(isValidStreamUrl("http://")).toBe(false);
    expect(isValidStreamUrl("javascript://x")).toBe(false);
    expect(isValidLogoUrl("")).toBe(true);
    expect(isValidLogoUrl("ftp://h/l.png")).toBe(false);
  });
});

const SRC: Source = { name: "S", type: "xtream", server: "http://s.invalid", username: "u", password: "p", m3uUrl: "", epgUrl: "", userAgent: "", referer: "", cid: 7, langs: null, createdAt: 1, lastSyncAt: 1, counts: { live: 3, movie: 0, series: 0 }, expDate: null, maxConnections: 1, state: "ready" } as Source;
const chan = (streamId: number, ord: number, num: number): ChannelRow => ({ sourceId: 7, catExt: "9", ord, streamId, num, name: `c${streamId}`, display: `c${streamId}`, norm: `c${streamId}`, country: null, q: 0, flags: 0, sep: 0, logo: null, epg: null, archive: 0 });
const input = (name: string, catExt: string) => ({ name, url: "http://perso.invalid/x.m3u8", logo: "", epg: "ma.tvg", catExt, catName: "Mes chaînes" });

describe("chaînes manuelles et synchro (Dexie)", () => {
  beforeEach(async () => {
    await Promise.all([db.sources, db.categories, db.channels, db.manualChannels].map((t) => t.clear()));
    await db.sources.add({ ...SRC, id: 1 });
    await db.categories.add(cat("9", { count: 3 }));
    await db.channels.bulkAdd([chan(1, 0, 1), chan(2, 1, 2), chan(3, 2, 3)]);
  });

  it("ajout : injectée à la suite, effectifs et totaux à jour ; modification ; suppression", async () => {
    const ref = await addManual(1, input("Ma chaîne", "9"));
    const ref2 = await addManual(1, input("Autre", MANUAL_CAT));
    expect(ref.startsWith("custom:")).toBe(true);
    let row = (await db.channels.where("[sourceId+streamId]").equals([7, manualStreamId(ref)]).first())!;
    expect(row).toMatchObject({ catExt: "9", num: 4, epg: "ma.tvg", url: "http://perso.invalid/x.m3u8", ord: 3 });
    expect((await db.categories.where("[sourceId+kind+extId]").equals([7, "live", "9"]).first())!.count).toBe(4);
    expect((await db.categories.where("[sourceId+kind+extId]").equals([7, "live", MANUAL_CAT]).first())!.count).toBe(1);
    expect((await db.sources.get(1))!.counts.live).toBe(5);

    await updateManual(1, ref, { ...input("Renommée", MANUAL_CAT) });
    row = (await db.channels.where("[sourceId+streamId]").equals([7, manualStreamId(ref)]).first())!;
    expect(row).toMatchObject({ catExt: MANUAL_CAT, name: "Renommée" });
    expect((await db.categories.where("[sourceId+kind+extId]").equals([7, "live", "9"]).first())!.count).toBe(3);
    expect((await db.categories.where("[sourceId+kind+extId]").equals([7, "live", MANUAL_CAT]).first())!.count).toBe(2);
    expect((await db.sources.get(1))!.counts.live).toBe(5);

    await deleteManual(1, ref);
    await deleteManual(1, ref2);
    expect(await db.channels.count()).toBe(3);
    expect(await db.categories.where("[sourceId+kind+extId]").equals([7, "live", MANUAL_CAT]).count()).toBe(0); // catégorie vide retirée
    expect((await db.sources.get(1))!.counts.live).toBe(3);
  });

  it("survit à la synchro : nouvelle génération sans elles ⇒ réinjectées, ancienne génération jetée", async () => {
    const ref = await addManual(1, input("Ma chaîne", "9"));
    const ref2 = await addManual(1, input("Autre", MANUAL_CAT));
    // « Synchro » : génération 8 écrite par le fournisseur seul (4 chaînes), puis réinjection comme runSync.
    await db.categories.add(cat("9", { sourceId: 8, count: 4 }));
    await db.channels.bulkAdd([1, 2, 3, 4].map((i) => ({ ...chan(i, i - 1, i), sourceId: 8 })));
    const delta = await injectManual(8, 1);
    expect(delta).toBe(2);
    const rows = await db.channels.where("[sourceId+ord]").between([8, -1], [8, Infinity]).toArray();
    expect(rows).toHaveLength(6);
    expect(rows.find((r) => r.streamId === manualStreamId(ref))).toMatchObject({ num: 5, catExt: "9" }); // après les 4 du fournisseur
    expect(rows.find((r) => r.streamId === manualStreamId(ref2))!.catExt).toBe(MANUAL_CAT);
    expect((await db.categories.where("[sourceId+kind+extId]").equals([8, "live", "9"]).first())!.count).toBe(5);
    expect((await db.categories.where("[sourceId+kind+extId]").equals([8, "live", MANUAL_CAT]).first())!.count).toBe(1);
    // Idempotent : rejouer ne duplique rien.
    expect(await injectManual(8, 1)).toBe(0);
    expect(await db.channels.where("[sourceId+ord]").between([8, -1], [8, Infinity]).count()).toBe(6);
  });
});
