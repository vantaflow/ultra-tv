import "fake-indexeddb/auto";
import Dexie from "dexie";
import { beforeEach, describe, expect, it } from "vitest";
import { wordKeys } from "@/lib/text";
import { UltraTvDb, db, purgeOrphanGenerations, setGenerationPending } from "./db";
import { moviesCol, searchByWords } from "./queries";
import type { ChannelRow, MovieRow } from "./types";

const movie = (sourceId: number, ord: number, norm: string, extra: Partial<MovieRow> = {}): MovieRow => ({
  sourceId, catExt: "1", ord, streamId: ord, name: norm, title: norm, norm, words: wordKeys(sourceId, norm),
  year: null, poster: null, rating: 0, ext: "mp4", added: 0, ...extra,
});

describe("wordKeys", () => {
  it("clés génération|mot, mots distincts, plafonnées", () => {
    expect(wordKeys(7, "le grand bleu le")).toEqual(["7|le", "7|grand", "7|bleu"]);
    expect(wordKeys(7, "", 12)).toEqual([]);
    expect(wordKeys(7, "a b c d", 2)).toEqual(["7|a", "7|b"]);
  });
});

describe("searchByWords", () => {
  beforeEach(async () => { await db.movies.clear(); });

  it("préfixe du mot le plus long, tous les mots exigés, limité à la génération", async () => {
    await db.movies.bulkAdd([
      movie(1, 0, "le grand bleu"), movie(1, 1, "grand hotel budapest"), movie(1, 2, "bleu nuit"), movie(2, 0, "le grand bleu"),
    ]);
    expect((await searchByWords(db.movies, 1, "gran ble")).map((m) => m.ord)).toEqual([0]);
    expect((await searchByWords(db.movies, 1, "GRAND")).map((m) => m.ord).sort()).toEqual([0, 1]);
    expect(await searchByWords(db.movies, 1, "zzz")).toEqual([]);
    expect(await searchByWords(db.movies, 1, "   ")).toEqual([]);
  });

  it("un mot présent deux fois par préfixe n'est rendu qu'une fois ; limite respectée", async () => {
    await db.movies.bulkAdd([movie(1, 0, "star stars starship"), ...Array.from({ length: 10 }, (_, i) => movie(1, i + 1, `star ${i}`))]);
    const all = await searchByWords(db.movies, 1, "sta", 200);
    expect(all.length).toBe(11);
    expect((await searchByWords(db.movies, 1, "sta", 4)).length).toBe(4);
  });

  it("repli : lignes sans champ words (avant la migration) retrouvées par balayage de norm", async () => {
    await db.movies.bulkAdd([movie(1, 0, "ancien film", { words: undefined }), movie(1, 1, "autre chose", { words: undefined })]);
    expect((await searchByWords(db.movies, 1, "cien")).map((m) => m.ord)).toEqual([0]);
  });

  it("tri « note » par catégorie servi par l'index [sourceId+catExt+rating]", async () => {
    await db.movies.bulkAdd([
      movie(1, 0, "a", { rating: 5 }), movie(1, 1, "b", { rating: 9 }), movie(1, 2, "c", { rating: 7 }), movie(1, 3, "d", { rating: 9.5, catExt: "2" }),
    ]);
    const top = await moviesCol(1, "1", "rating").limit(2).toArray();
    expect(top.map((m) => m.ord)).toEqual([1, 2]);
  });
});

describe("migration du schéma v1 -> v2", () => {
  it("calcule words pour les lignes existantes (séparateurs vides)", async () => {
    const name = "ultratv-migr-test";
    const v1 = new Dexie(name);
    v1.version(1).stores({
      sources: "++id", categories: "++id, [sourceId+kind], [sourceId+kind+extId]",
      channels: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+epg], [sourceId+streamId]",
      movies: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+streamId]",
      series: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+seriesId]",
      programs: "++id, [sourceId+epg+start], [sourceId+end]", favorites: "&key, [profile+sourceId+kind], addedAt",
      history: "&key, [profile+sourceId], updatedAt", details: "&key", settings: "&key",
    });
    await v1.table("movies").add({ sourceId: 3, catExt: "1", ord: 0, streamId: 1, norm: "le grand bleu", rating: 8, added: 0 });
    await v1.table("channels").bulkAdd([
      { sourceId: 3, catExt: "1", ord: 0, streamId: 1, norm: "bbc news", sep: 0 },
      { sourceId: 3, catExt: "1", ord: 1, streamId: 2, norm: "fr sport", sep: 1 },
    ]);
    v1.close();
    const v2 = new UltraTvDb(name);
    await v2.open();
    expect((await v2.movies.toArray())[0]!.words).toEqual(["3|le", "3|grand", "3|bleu"]);
    const ch = await v2.channels.orderBy("[sourceId+ord]").toArray() as ChannelRow[];
    expect(ch.map((c) => c.words)).toEqual([["3|bbc", "3|news"], []]);
    expect((await v2.movies.where("words").startsWith("3|gra").toArray()).length).toBe(1);
    v2.close();
    await Dexie.delete(name);
  });
});

describe("purgeOrphanGenerations", () => {
  beforeEach(async () => {
    for (const t of [db.sources, db.movies, db.channels, db.categories, db.series, db.programs, db.settings]) await t.clear();
    await db.settings.put({ key: "cid.counter", value: 5 });
  });

  it("supprime les générations qui ne sont la cid d'aucune source, garde celle d'une source", async () => {
    await db.sources.put({ id: 1, cid: 4 } as never);
    await db.movies.bulkAdd([movie(2, 0, "orphelin"), movie(4, 0, "courant"), movie(3, 0, "autre orphelin")]);
    await db.categories.add({ sourceId: 3, kind: "movie", extId: "1", name: "x", label: "x", badge: null, count: 0, enabled: 1, adult: 0, ord: 0 });
    const purged = await purgeOrphanGenerations({ startup: true });
    expect(purged.sort()).toEqual([2, 3]);
    expect((await db.movies.toArray()).map((m) => m.sourceId)).toEqual([4]);
    expect(await db.categories.count()).toBe(0);
  });

  it("garde une génération en cours d'écriture, sauf au démarrage", async () => {
    await db.movies.bulkAdd([movie(5, 0, "en cours")]);
    await setGenerationPending(5, true);
    expect(await purgeOrphanGenerations()).toEqual([]);
    expect(await db.movies.count()).toBe(1);
    expect(await purgeOrphanGenerations({ startup: true })).toEqual([5]);
    expect(await db.movies.count()).toBe(0);
    expect((await db.settings.get("cid.pending"))?.value).toEqual([]);
  });
});
