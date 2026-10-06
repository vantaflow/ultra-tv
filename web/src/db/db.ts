import Dexie, { type Table } from "dexie";
import { wordKeys } from "@/lib/text";
import type {
  CategoryRow, ChannelRow, DetailRow, FavoriteRow, HistoryRow, MovieRow,
  ProgramRow, SeriesRow, SettingRow, Source,
} from "./types";

export class UltraTvDb extends Dexie {
  sources!: Table<Source, number>;
  categories!: Table<CategoryRow, number>;
  channels!: Table<ChannelRow, number>;
  movies!: Table<MovieRow, number>;
  series!: Table<SeriesRow, number>;
  programs!: Table<ProgramRow, number>;
  favorites!: Table<FavoriteRow, string>;
  history!: Table<HistoryRow, string>;
  details!: Table<DetailRow, string>;
  settings!: Table<SettingRow, string>;

  constructor(name = "ultratv-desktop") {
    super(name);
    this.version(1).stores({
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
    });
    // v2 : recherche texte par index (mot -> lignes) au lieu d'un filter() sur tout le catalogue, et tri « note » par catégorie.
    // `*words` est multiEntry ; les lignes existantes reçoivent leur champ ici (une seule fois, à l'ouverture).
    this.version(2).stores({
      channels: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+epg], [sourceId+streamId], *words",
      movies: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+catExt+rating], [sourceId+streamId], *words",
      series: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+catExt+rating], [sourceId+seriesId], *words",
    }).upgrade(async (tx) => {
      for (const name of ["channels", "movies", "series"] as const) {
        await tx.table(name).toCollection().modify((r: { sourceId: number; norm?: string; sep?: number; words?: string[] }) => {
          r.words = r.sep ? [] : wordKeys(r.sourceId, r.norm ?? "");
        });
      }
    });
  }
}

export const db = new UltraTvDb();

export async function getSetting<T>(key: string, fallback: T): Promise<T> {
  const row = await db.settings.get(key);
  return (row?.value as T | undefined) ?? fallback;
}
export const setSetting = (key: string, value: unknown) => db.settings.put({ key, value });

const R = (cid: number): [[number, number], [number, number]] => [[cid, -1], [cid, Infinity]];

/** Supprime une génération de catalogue (les favoris et l'historique sont conservés). */
export async function clearCatalog(cid: number): Promise<void> {
  if (!cid) return;
  await db.categories.where("[sourceId+kind]").between([cid, ""], [cid, "\uffff"]).delete();
  await db.channels.where("[sourceId+ord]").between(...R(cid)).delete();
  await db.movies.where("[sourceId+ord]").between(...R(cid)).delete();
  await db.series.where("[sourceId+ord]").between(...R(cid)).delete();
  await db.programs.where("[sourceId+end]").between([cid, 0], [cid, Infinity]).delete();
}

/** Prochain numéro de génération de catalogue (compteur global). */
export async function nextCid(): Promise<number> {
  return db.transaction("rw", db.settings, async () => {
    const n = ((await db.settings.get("cid.counter"))?.value as number | undefined) ?? 0;
    await db.settings.put({ key: "cid.counter", value: n + 1 });
    return n + 1;
  });
}

/**
 * Supprime les générations de catalogue orphelines : une synchro interrompue (fermeture, plantage) laisse sa génération
 * (jusqu'à 180 000 films) dans IndexedDB pour toujours. Une génération est conservée si elle est la `cid` d'une source ou,
 * hors démarrage, si une synchro en cours l'écrit (`cid.pending`). Au démarrage aucune synchro ne tourne : tout pending est périmé.
 */
export async function purgeOrphanGenerations(opts: { startup?: boolean } = {}): Promise<number[]> {
  const keep = new Set<number>((await db.sources.toArray()).map((s) => s.cid).filter(Boolean));
  const pending = ((await db.settings.get("cid.pending"))?.value as number[] | undefined) ?? [];
  if (!opts.startup) for (const c of pending) keep.add(c);
  const counter = ((await db.settings.get("cid.counter"))?.value as number | undefined) ?? 0;
  const purged: number[] = [];
  for (let c = 1; c <= counter; c++) {
    if (keep.has(c)) continue;
    const any = (await db.categories.where("[sourceId+kind]").between([c, ""], [c, "\uffff"]).limit(1).count())
      || (await db.channels.where("[sourceId+ord]").between(...R(c)).limit(1).count())
      || (await db.movies.where("[sourceId+ord]").between(...R(c)).limit(1).count())
      || (await db.series.where("[sourceId+ord]").between(...R(c)).limit(1).count())
      || (await db.programs.where("[sourceId+end]").between([c, 0], [c, Infinity]).limit(1).count());
    if (!any) continue;
    await clearCatalog(c);
    purged.push(c);
  }
  if (opts.startup && pending.length) await db.settings.put({ key: "cid.pending", value: [] });
  return purged;
}

/** Marque (ou retire) une génération comme « en cours d'écriture » pour que la purge ne la prenne pas pour une orpheline. */
export async function setGenerationPending(cid: number, on: boolean): Promise<void> {
  await db.transaction("rw", db.settings, async () => {
    const cur = ((await db.settings.get("cid.pending"))?.value as number[] | undefined) ?? [];
    const next = on ? [...new Set([...cur, cid])] : cur.filter((c) => c !== cid);
    await db.settings.put({ key: "cid.pending", value: next });
  });
}
