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
      movies: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+streamId]",
      series: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+seriesId]",
      programs: "++id, [sourceId+epg+start], [sourceId+end]",
      favorites: "&key, [sourceId+kind], addedAt",
      history: "&key, [sourceId+kind], updatedAt",
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

/** Supprime tout le catalogue d'une source (les favoris et l'historique sont conservés). */
export async function clearCatalog(sourceId: number): Promise<void> {
  await db.transaction("rw", [db.categories, db.channels, db.movies, db.series, db.programs], async () => {
    await db.categories.where("[sourceId+kind]").between([sourceId, ""], [sourceId, "￿"]).delete();
    await db.channels.where("[sourceId+ord]").between([sourceId, -1], [sourceId, Infinity]).delete();
    await db.movies.where("[sourceId+ord]").between([sourceId, -1], [sourceId, Infinity]).delete();
    await db.series.where("[sourceId+ord]").between([sourceId, -1], [sourceId, Infinity]).delete();
    await db.programs.where("[sourceId+end]").between([sourceId, 0], [sourceId, Infinity]).delete();
  });
}
