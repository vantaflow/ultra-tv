// Domain model interfaces — pure data shapes consumed across the data, player
// and app layers. Runtime invariants are enforced at construction sites
// (parsers, repositories) rather than on every read.

export type ContentType = "LIVE" | "MOVIE" | "SERIES" | "SERIES_EPISODE";

export type ProviderType = "XTREAM_CODES" | "M3U" | "STALKER_PORTAL";
export type ProviderEpgSyncMode = "UPFRONT" | "BACKGROUND" | "SKIP";
export type ProviderStatus =
  | "ACTIVE"
  | "PARTIAL"
  | "EXPIRED"
  | "DISABLED"
  | "ERROR"
  | "UNKNOWN";

export interface Provider {
  id: number;
  name: string;
  type: ProviderType;
  serverUrl: string;
  username: string;
  password: string;
  m3uUrl: string;
  epgUrl: string;
  stalkerMacAddress: string;
  stalkerDeviceProfile: string;
  stalkerDeviceTimezone: string;
  stalkerDeviceLocale: string;
  userAgent: string;
  httpReferer: string;
  isActive: boolean;
  maxConnections: number;
  expirationDate: number | null;
  apiVersion: string | null;
  allowedOutputFormats: string[];
  epgSyncMode: ProviderEpgSyncMode;
  xtreamFastSyncEnabled: boolean;
  m3uVodClassificationEnabled: boolean;
  status: ProviderStatus;
  lastSyncedAt: number;
  createdAt: number;
}

export interface Category {
  id: number;
  roomId: number;
  name: string;
  parentId: number | null;
  type: ContentType;
  isVirtual: boolean;
  count: number;
  isAdult: boolean;
  isUserProtected: boolean;
}

export interface ChannelQualityOption {
  label: string;
  height: number | null;
  url: string | null;
}

export interface LiveChannelVariantAttributes {
  resolutionLabel: string | null;
  declaredHeight: number | null;
  qualityTier: number;
  codecLabel: string | null;
  transportLabel: string | null;
  frameRate: number | null;
  isHdr: boolean;
  sourceHint: string | null;
  regionHint: string | null;
  languageHint: string | null;
  rawTags: string[];
}

export interface LiveChannelObservedQuality {
  lastObservedWidth: number;
  lastObservedHeight: number;
  lastObservedBitrate: number;
  lastObservedFrameRate: number;
  successCount: number;
  lastSuccessfulAt: number;
}

export interface LiveChannelVariant {
  rawChannelId: number;
  logicalGroupId: string;
  providerId: number;
  originalName: string;
  canonicalName: string;
  streamUrl: string;
  streamId: number;
  epgChannelId: string | null;
  number: number;
  errorCount: number;
  catchUpSupported: boolean;
  catchUpDays: number;
  catchUpSource: string | null;
  attributes: LiveChannelVariantAttributes;
  observedQuality: LiveChannelObservedQuality;
}

export interface Channel {
  id: number;
  name: string;
  canonicalName: string;
  logoUrl: string | null;
  groupTitle: string | null;
  categoryId: number | null;
  categoryName: string | null;
  streamUrl: string;
  epgChannelId: string | null;
  number: number;
  isFavorite: boolean;
  catchUpSupported: boolean;
  catchUpDays: number;
  catchUpSource: string | null;
  providerId: number;
  currentProgram: Program | null;
  nextProgram: Program | null;
  isAdult: boolean;
  isUserProtected: boolean;
  logicalGroupId: string;
  selectedVariantId: number;
  errorCount: number;
  qualityOptions: ChannelQualityOption[];
  alternativeStreams: string[];
  variants: LiveChannelVariant[];
  streamId: number;
}

export interface Movie {
  id: number;
  name: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  categoryId: number | null;
  categoryName: string | null;
  streamUrl: string;
  containerExtension: string | null;
  plot: string | null;
  cast: string | null;
  director: string | null;
  genre: string | null;
  releaseDate: string | null;
  duration: string | null;
  durationSeconds: number;
  rating: number;
  year: string | null;
  tmdbId: number | null;
  youtubeTrailer: string | null;
  isFavorite: boolean;
  providerId: number;
  watchProgress: number;
  lastWatchedAt: number;
  isAdult: boolean;
  isUserProtected: boolean;
  streamId: number;
  addedAt: number;
}

export interface Episode {
  id: number;
  title: string;
  episodeNumber: number;
  seasonNumber: number;
  streamUrl: string;
  containerExtension: string | null;
  coverUrl: string | null;
  plot: string | null;
  duration: string | null;
  durationSeconds: number;
  rating: number;
  releaseDate: string | null;
  seriesId: number;
  providerId: number;
  watchProgress: number;
  lastWatchedAt: number;
  isAdult: boolean;
  isUserProtected: boolean;
  episodeId: number;
}

export interface Season {
  seasonNumber: number;
  name: string;
  coverUrl: string | null;
  episodes: Episode[];
  airDate: string | null;
  episodeCount: number;
}

export interface Series {
  id: number;
  name: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  categoryId: number | null;
  categoryName: string | null;
  plot: string | null;
  cast: string | null;
  director: string | null;
  genre: string | null;
  releaseDate: string | null;
  rating: number;
  tmdbId: number | null;
  youtubeTrailer: string | null;
  isFavorite: boolean;
  providerId: number;
  seasons: Season[];
  episodeRunTime: string | null;
  lastModified: number;
  isAdult: boolean;
  isUserProtected: boolean;
  seriesId: number;
  providerSeriesId: string | null;
}

export interface Program {
  id: number;
  channelId: string;
  title: string;
  description: string;
  startTime: number;
  endTime: number;
  lang: string;
  rating: string | null;
  imageUrl: string | null;
  genre: string | null;
  category: string | null;
  hasArchive: boolean;
  isNowPlaying: boolean;
  providerId: number;
}

export function programProgress(p: Program, now = Date.now()): number {
  if (p.endTime <= p.startTime || now < p.startTime) return 0;
  if (now >= p.endTime) return 1;
  return (now - p.startTime) / (p.endTime - p.startTime);
}

export interface Favorite {
  id: number;
  providerId: number;
  contentId: number;
  contentType: ContentType;
  position: number;
  groupId: number | null;
  addedAt: number;
}

export interface VirtualGroup {
  id: number;
  providerId: number;
  name: string;
  iconEmoji: string | null;
  position: number;
  createdAt: number;
  contentType: ContentType;
}

export type PlaybackWatchedStatus = "IN_PROGRESS" | "COMPLETED_AUTO" | "COMPLETED_MANUAL";

export interface PlaybackHistory {
  id: number;
  contentId: number;
  contentType: ContentType;
  providerId: number;
  title: string;
  posterUrl: string | null;
  streamUrl: string;
  resumePositionMs: number;
  totalDurationMs: number;
  lastWatchedAt: number;
  watchCount: number;
  watchedStatus: PlaybackWatchedStatus;
  seriesId: number | null;
  seasonNumber: number | null;
  episodeNumber: number | null;
}

export type StreamType = "HLS" | "DASH" | "MPEG_TS" | "PROGRESSIVE" | "RTSP" | "UNKNOWN";
export type DrmScheme = "WIDEVINE" | "PLAYREADY" | "CLEARKEY";

export interface DrmInfo {
  scheme: DrmScheme;
  licenseUrl: string;
  headers: Record<string, string>;
  multiSession: boolean;
  forceDefaultLicenseUrl: boolean;
  playClearContentWithoutKey: boolean;
}

export interface StreamInfo {
  url: string;
  title: string | null;
  headers: Record<string, string>;
  userAgent: string | null;
  streamType: StreamType;
  containerExtension: string | null;
  catchUpUrl: string | null;
  expirationTime: number | null;
  drmInfo: DrmInfo | null;
}

export function streamTypeFromExtension(ext: string | null | undefined): StreamType {
  const e = ext?.trim().replace(/^\./, "").toLowerCase();
  switch (e) {
    case "ts":
      return "MPEG_TS";
    case "m3u8":
      return "HLS";
    case "mpd":
      return "DASH";
    case "mp4":
    case "mkv":
    case "avi":
    case "mov":
    case "mp3":
    case "aac":
    case "m4a":
    case "flv":
    case "webm":
      return "PROGRESSIVE";
    default:
      return "UNKNOWN";
  }
}

// --- Recordings, reminders, EPG sources, parental, search history ---

export type RecordingStatus = "SCHEDULED" | "RECORDING" | "COMPLETED" | "FAILED" | "CANCELLED";

export interface Recording {
  id: number;
  providerId: number;
  channelId: number;
  channelName: string;
  programTitle: string;
  startTime: number;
  endTime: number;
  filePath: string;
  sizeBytes: number;
  status: RecordingStatus;
  errorMessage: string | null;
  createdAt: number;
}

export interface ProgramReminder {
  id: number;
  providerId: number;
  channelId: number;
  programTitle: string;
  startTime: number;
  endTime: number;
  notifyAt: number;
  fired: boolean;
}

export interface EpgSource {
  id: number;
  name: string;
  url: string;
  enabled: boolean;
  priority: number;
  lastFetchedAt: number;
  providerId: number | null;
}

export interface ParentalLock {
  id: number;
  categoryId: number;
  contentType: ContentType;
  providerId: number;
  createdAt: number;
}

export interface SearchHistoryEntry {
  id: number;
  query: string;
  scope: "ALL" | "LIVE" | "MOVIE" | "SERIES";
  timestamp: number;
}

export interface CustomGroup extends VirtualGroup {}

export interface WatchlistEntry {
  id: number;
  providerId: number;
  contentId: number;
  contentType: ContentType;
  addedAt: number;
}

export interface CombinedM3uProfile {
  id: number;
  name: string;
  providerIds: number[];
  createdAt: number;
}

export function streamTypeFromUrl(url: string): StreamType {
  const clean = url.split("?")[0]?.toLowerCase() ?? "";
  if (clean.endsWith(".m3u8")) return "HLS";
  if (clean.endsWith(".mpd")) return "DASH";
  if (clean.endsWith(".ts")) return "MPEG_TS";
  if (clean.startsWith("rtsp://")) return "RTSP";
  const m = clean.match(/\.([a-z0-9]+)$/);
  return streamTypeFromExtension(m?.[1] ?? null);
}
