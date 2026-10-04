import { liveQuery } from "dexie";
import { db } from "./db";
import type { CategoryRow, ChannelRow, FavoriteRow, HistoryRow, Kind, MovieRow, ProgramRow, SeriesRow } from "./types";

const MAXK = Infinity;

export const channelsCol = (cid: number, cat: string | null) =>
  cat == null
    ? db.channels.where("[sourceId+ord]").between([cid, -1], [cid, MAXK])
    : db.channels.where("[sourceId+catExt+ord]").between([cid, cat, -1], [cid, cat, MAXK]);

export type VodSort = "recent" | "provider" | "rating";

function vodCol<T>(table: "movies" | "series", cid: number, cat: string | null, sort: VodSort) {
  const t = db[table] as unknown as import("dexie").Table<T, number>;
  if (cat != null) {
    if (sort === "recent") return t.where("[sourceId+catExt+added]").between([cid, cat, -1], [cid, cat, MAXK]).reverse();
    return t.where("[sourceId+catExt+ord]").between([cid, cat, -1], [cid, cat, MAXK]);
  }
  if (sort === "recent") return t.where("[sourceId+added]").between([cid, -1], [cid, MAXK]).reverse();
  if (sort === "rating") return t.where("[sourceId+rating]").between([cid, -1], [cid, MAXK]).reverse();
  return t.where("[sourceId+ord]").between([cid, -1], [cid, MAXK]);
}
export const moviesCol = (cid: number, cat: string | null, sort: VodSort) => vodCol<MovieRow>("movies", cid, cat, sort);
export const seriesCol = (cid: number, cat: string | null, sort: VodSort) => vodCol<SeriesRow>("series", cid, cat, sort);

export const categoriesOf = (cid: number, kind: Kind): Promise<CategoryRow[]> =>
  db.categories.where("[sourceId+kind]").equals([cid, kind]).sortBy("ord");

export const live$ = <T>(fn: () => Promise<T> | T) => liveQuery(fn);

/** Programmes (maintenant, suivant) pour une liste d'identifiants de guide. */
export async function nowNext(cid: number, epgIds: string[], now = Date.now()): Promise<Map<string, { now?: ProgramRow; next?: ProgramRow }>> {
  const out = new Map<string, { now?: ProgramRow; next?: ProgramRow }>();
  await Promise.all([...new Set(epgIds)].map(async (id) => {
    const rows = await db.programs.where("[sourceId+epg+start]").between([cid, id, now - 6 * 3600_000], [cid, id, now + 6 * 3600_000]).toArray();
    const cur = rows.find((p) => p.start <= now && p.end > now);
    const next = rows.find((p) => p.start >= (cur?.end ?? now));
    out.set(id, { now: cur, next });
  }));
  return out;
}

export const programsFor = (cid: number, epg: string, from: number, to: number) =>
  db.programs.where("[sourceId+epg+start]").between([cid, epg, from - 6 * 3600_000], [cid, epg, to]).filter((p) => p.end > from).toArray();

// --- Favoris et historique -----------------------------------------------------------------------

export const favKey = (profile: string, sourceId: number, kind: Kind, refId: number) => `${profile}:${sourceId}:${kind}:${refId}`;

export const favoritesOf = (profile: string, sourceId: number, kind?: Kind): Promise<FavoriteRow[]> =>
  db.favorites.where("[profile+sourceId+kind]").between([profile, sourceId, kind ?? ""], [profile, sourceId, kind ?? "￿"]).reverse().sortBy("addedAt");

export async function toggleFavorite(row: Omit<FavoriteRow, "key" | "addedAt">): Promise<boolean> {
  const key = favKey(row.profile, row.sourceId, row.kind, row.refId);
  if (await db.favorites.get(key)) { await db.favorites.delete(key); return false; }
  await db.favorites.put({ ...row, key, addedAt: Date.now() });
  return true;
}

export const historyOf = (profile: string, sourceId: number): Promise<HistoryRow[]> =>
  db.history.where("[profile+sourceId]").equals([profile, sourceId]).reverse().sortBy("updatedAt");

export const histKey = (profile: string, sourceId: number, kind: Kind, refId: number) => `${profile}:${sourceId}:${kind}:${refId}`;
