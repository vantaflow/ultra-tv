import Dexie, { type Table } from "dexie";
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
