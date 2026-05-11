// Thin repository facades over the Dexie tables.

import type {
  Category,
  Channel,
  CombinedM3uProfile,
  Episode,
  EpgSource,
  Favorite,
  Movie,
  ParentalLock,
  PlaybackHistory,
  Program,
  ProgramReminder,
  Provider,
  Recording,
  SearchHistoryEntry,
  Series,
  VirtualGroup,
} from "@domain/model";
import { db } from "./database";

export const providerRepo = {
  list: () => db.providers.orderBy("name").toArray(),
  get: (id: number) => db.providers.get(id),
  upsert: (p: Provider) => db.providers.put(p),
  delete: (id: number) => db.providers.delete(id),
  active: () => db.providers.filter((p) => p.isActive).toArray(),
};

export const channelRepo = {
  forProvider: (providerId: number) =>
    db.channels.where("providerId").equals(providerId).toArray(),
  byCategory: (providerId: number, categoryId: number) =>
    db.channels.where({ providerId, categoryId }).toArray(),
  byEpgId: (epgChannelId: string) =>
    db.channels.where("epgChannelId").equals(epgChannelId).toArray(),
  get: (id: number) => db.channels.get(id),
  bulkAdd: (channels: Channel[]) => db.channels.bulkPut(channels),
  clearProvider: (providerId: number) =>
    db.channels.where("providerId").equals(providerId).delete(),
};

export const movieRepo = {
  forProvider: (providerId: number) =>
    db.movies.where("providerId").equals(providerId).toArray(),
  get: (id: number) => db.movies.get(id),
  bulkAdd: (movies: Movie[]) => db.movies.bulkPut(movies),
  put: (m: Movie) => db.movies.put(m),
  clearProvider: (providerId: number) =>
    db.movies.where("providerId").equals(providerId).delete(),
};

export const seriesRepo = {
  forProvider: (providerId: number) =>
    db.series.where("providerId").equals(providerId).toArray(),
  get: (id: number) => db.series.get(id),
  bulkAdd: (series: Series[]) => db.series.bulkPut(series),
  put: (s: Series) => db.series.put(s),
  clearProvider: (providerId: number) =>
    db.series.where("providerId").equals(providerId).delete(),
};

export const episodeRepo = {
  forSeries: (seriesId: number) =>
    db.episodes.where("seriesId").equals(seriesId).toArray(),
  bulkAdd: (rows: Episode[]) => db.episodes.bulkPut(rows),
  clearSeries: (seriesId: number) =>
    db.episodes.where("seriesId").equals(seriesId).delete(),
};

export const categoryRepo = {
  forProvider: (providerId: number) =>
    db.categories.filter((c) => (c as Category & { providerId?: number }).providerId === providerId).toArray(),
  bulkAdd: (rows: (Category & { providerId: number })[]) =>
    db.categories.bulkPut(rows as unknown as Category[]),
  clearProvider: (providerId: number) =>
    db.categories.filter((c) => (c as Category & { providerId?: number }).providerId === providerId).delete(),
};

export const programRepo = {
  forChannel: (channelId: string) =>
    db.programs.where("channelId").equals(channelId).toArray(),
  inWindow: (fromMs: number, toMs: number) =>
    db.programs.where("startTime").between(fromMs, toMs, true, true).toArray(),
  bulkAdd: (rows: Program[]) => db.programs.bulkPut(rows),
  clearProvider: (providerId: number) =>
    db.programs.where("providerId").equals(providerId).delete(),
};

export const favoriteRepo = {
  list: (providerId: number) =>
    db.favorites.where("providerId").equals(providerId).toArray(),
  byContent: (providerId: number, contentId: number) =>
    db.favorites.where({ providerId, contentId }).first(),
  add: (f: Omit<Favorite, "id">) => db.favorites.add({ ...f, id: 0 } as Favorite),
  remove: (id: number) => db.favorites.delete(id),
  clearProvider: (providerId: number) =>
    db.favorites.where("providerId").equals(providerId).delete(),
};

export const virtualGroupRepo = {
  list: (providerId: number) =>
    db.virtualGroups.where("providerId").equals(providerId).toArray(),
  add: (g: Omit<VirtualGroup, "id">) =>
    db.virtualGroups.add({ ...g, id: 0 } as VirtualGroup),
  remove: (id: number) => db.virtualGroups.delete(id),
};

export const historyRepo = {
  list: (providerId: number) =>
    db.playbackHistory.where("providerId").equals(providerId).reverse().sortBy("lastWatchedAt"),
  byContent: (providerId: number, contentId: number) =>
    db.playbackHistory.where({ providerId, contentId }).first(),
  upsert: (h: PlaybackHistory) => db.playbackHistory.put(h),
  remove: (id: number) => db.playbackHistory.delete(id),
};

export const recordingRepo = {
  list: () => db.recordings.orderBy("startTime").reverse().toArray(),
  upsert: (r: Recording) => db.recordings.put(r),
  delete: (id: number) => db.recordings.delete(id),
};

export const reminderRepo = {
  pending: () => db.reminders.filter((r) => !r.fired).toArray(),
  add: (r: Omit<ProgramReminder, "id">) =>
    db.reminders.add({ ...r, id: 0 } as ProgramReminder),
  delete: (id: number) => db.reminders.delete(id),
};

export const epgSourceRepo = {
  list: () => db.epgSources.orderBy("priority").toArray(),
  upsert: (s: EpgSource) => db.epgSources.put(s),
  delete: (id: number) => db.epgSources.delete(id),
};

export const parentalLockRepo = {
  list: (providerId: number) =>
    db.parentalLocks.where("providerId").equals(providerId).toArray(),
  add: (l: Omit<ParentalLock, "id">) =>
    db.parentalLocks.add({ ...l, id: 0 } as ParentalLock),
  delete: (id: number) => db.parentalLocks.delete(id),
  has: async (providerId: number, categoryId: number) =>
    (await db.parentalLocks.where({ providerId, categoryId }).count()) > 0,
};

export const searchHistoryRepo = {
  recent: (scope: SearchHistoryEntry["scope"], limit = 20) =>
    db.searchHistory.where("scope").equals(scope).reverse().limit(limit).sortBy("timestamp"),
  add: (e: Omit<SearchHistoryEntry, "id">) =>
    db.searchHistory.add({ ...e, id: 0 } as SearchHistoryEntry),
  clear: () => db.searchHistory.clear(),
};

export const combinedM3uRepo = {
  list: () => db.combinedM3uProfiles.toArray(),
  upsert: (p: CombinedM3uProfile) => db.combinedM3uProfiles.put(p),
  delete: (id: number) => db.combinedM3uProfiles.delete(id),
};

export const pinnedCategoryRepo = {
  list: (providerId: number, contentType: string) =>
    db.pinnedCategories.filter((p) => p.providerId === providerId && p.contentType === contentType).toArray(),
  toggle: async (providerId: number, categoryId: number, contentType: string) => {
    const existing = await db.pinnedCategories
      .filter((p) => p.providerId === providerId && p.categoryId === categoryId && p.contentType === contentType)
      .first();
    if (existing) await db.pinnedCategories.delete(existing.id);
    else await db.pinnedCategories.add({ id: 0, providerId, categoryId, contentType } as never);
  },
};

export const watchlistRepo = {
  list: (providerId: number) =>
    db.watchlist.where("providerId").equals(providerId).reverse().sortBy("addedAt"),
  byContent: (providerId: number, contentId: number) =>
    db.watchlist.where({ providerId, contentId }).first(),
  add: (e: Omit<import("@domain/model").WatchlistEntry, "id">) =>
    db.watchlist.add({ ...e, id: 0 } as never),
  remove: (id: number) => db.watchlist.delete(id),
  toggle: async (providerId: number, contentId: number, contentType: "MOVIE" | "SERIES" | "LIVE") => {
    const existing = await db.watchlist.where({ providerId, contentId }).first();
    if (existing) { await db.watchlist.delete(existing.id); return false; }
    await db.watchlist.add({ id: 0, providerId, contentId, contentType, addedAt: Date.now() } as never);
    return true;
  },
};

export const settingsRepo = {
  get: async <T>(key: string): Promise<T | null> => {
    const row = await db.settings.get(key);
    return (row?.value as T | undefined) ?? null;
  },
  set: <T>(key: string, value: T) => db.settings.put({ key, value }),
};
