// IndexedDB schema (via Dexie). Single source of truth for the local catalog,
// playback history, favourites/watchlist, parental locks, settings and DVR.
// v1: core catalog · v2: DVR/EPG/parental/search · v3: watchlist.

import Dexie, { type Table } from "dexie";
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
  WatchlistEntry,
} from "@domain/model";

export class UltraTvDb extends Dexie {
  providers!: Table<Provider, number>;
  categories!: Table<Category, number>;
  channels!: Table<Channel, number>;
  movies!: Table<Movie, number>;
  series!: Table<Series, number>;
  episodes!: Table<Episode, number>;
  programs!: Table<Program, number>;
  favorites!: Table<Favorite, number>;
  virtualGroups!: Table<VirtualGroup, number>;
  playbackHistory!: Table<PlaybackHistory, number>;
  recordings!: Table<Recording, number>;
  reminders!: Table<ProgramReminder, number>;
  epgSources!: Table<EpgSource, number>;
  parentalLocks!: Table<ParentalLock, number>;
  searchHistory!: Table<SearchHistoryEntry, number>;
  combinedM3uProfiles!: Table<CombinedM3uProfile, number>;
  pinnedCategories!: Table<{ id: number; providerId: number; categoryId: number; contentType: string }, number>;
  settings!: Table<{ key: string; value: unknown }, string>;
  watchlist!: Table<WatchlistEntry, number>;

  constructor() {
    super("ultratv");
    this.version(1).stores({
      providers: "++id, name, type, isActive",
      categories: "++id, providerId, type, parentId, name",
      channels: "++id, providerId, categoryId, name, canonicalName, number, logicalGroupId, epgChannelId",
      movies: "++id, providerId, categoryId, name, lastWatchedAt, addedAt",
      series: "++id, providerId, categoryId, name, lastModified",
      episodes: "++id, seriesId, seasonNumber, episodeNumber, providerId",
      programs: "++id, channelId, providerId, startTime, endTime",
      favorites: "++id, providerId, contentId, contentType, groupId, position",
      virtualGroups: "++id, providerId, contentType, position",
      playbackHistory: "++id, providerId, contentId, contentType, lastWatchedAt",
    });
    this.version(2).stores({
      recordings: "++id, providerId, channelId, status, startTime",
      reminders: "++id, providerId, channelId, startTime, fired",
      epgSources: "++id, providerId, enabled, priority",
      parentalLocks: "++id, providerId, categoryId, contentType",
      searchHistory: "++id, scope, timestamp",
      combinedM3uProfiles: "++id, name",
      pinnedCategories: "++id, providerId, categoryId, contentType",
      settings: "&key",
    });
    this.version(3).stores({
      watchlist: "++id, providerId, contentId, contentType, addedAt",
    });
  }
}

export const db = new UltraTvDb();
