// Client Xtream Codes (player_api.php). Utilisable depuis le thread principal et le worker.

import { asArray, parseTolerant } from "./json";
import { transportFetch, type ReqOptions, type Transport } from "./transport";

export interface XtreamCreds {
  server: string;
  username: string;
  password: string;
  userAgent?: string | null;
  referer?: string | null;
}

export interface XtreamCategory { category_id: string; category_name: string; parent_id?: number }
export interface XtreamLive {
  num: number; name: string; stream_id: number; stream_icon?: string; epg_channel_id?: string | null;
  category_id: string; tv_archive?: number; added?: string;
}
export interface XtreamVod {
  num: number; name: string; stream_id: number; stream_icon?: string; rating?: string; rating_5based?: number | string;
  added?: string; category_id: string; container_extension?: string;
}
export interface XtreamSeries {
  num: number; name: string; series_id: number; cover?: string; plot?: string; backdrop_path?: string[] | string;
  rating?: string; rating_5based?: number | string; last_modified?: string; category_id: string; releaseDate?: string; release_date?: string;
}

export interface XtreamHandshake {
  user_info?: {
    status?: string; exp_date?: string | null; max_connections?: string | number; active_cons?: string | number;
    auth?: number; allowed_output_formats?: string[]; message?: string;
  };
  server_info?: { url?: string; port?: string; server_protocol?: string; timezone?: string };
}

export const normalizeBase = (s: string) => {
  let t = s.trim();
  if (!/^https?:\/\//i.test(t)) t = "http://" + t;
  return t.endsWith("/") ? t : t + "/";
};

export function apiUrl(c: XtreamCreds, params: Record<string, string | number> = {}): string {
  const u = new URL("player_api.php", normalizeBase(c.server));
  u.searchParams.set("username", c.username);
  u.searchParams.set("password", c.password);
  for (const [k, v] of Object.entries(params)) u.searchParams.set(k, String(v));
  return u.toString();
}

const opts = (c: XtreamCreds, signal?: AbortSignal): ReqOptions => ({ signal, userAgent: c.userAgent, referer: c.referer });

export async function xtreamJson<T>(t: Transport, c: XtreamCreds, params: Record<string, string | number>, signal?: AbortSignal): Promise<T> {
  const res = await transportFetch(t, apiUrl(c, params), opts(c, signal));
  return parseTolerant<T>(await res.text());
}

export const xtreamArray = async <T>(t: Transport, c: XtreamCreds, action: string, extra: Record<string, string | number> = {}, signal?: AbortSignal) =>
  asArray<T>(await xtreamJson<unknown>(t, c, { action, ...extra }, signal));

export const handshake = (t: Transport, c: XtreamCreds, signal?: AbortSignal) => xtreamJson<XtreamHandshake>(t, c, {}, signal);

/** Réponse brute à streamer (grand catalogue). */
export const xtreamStream = (t: Transport, c: XtreamCreds, action: string, extra: Record<string, string | number> = {}, signal?: AbortSignal) =>
  transportFetch(t, apiUrl(c, { action, ...extra }), opts(c, signal));

// --- URL de flux : reconstruites à la lecture, jamais stockées -------------------------------------

export const liveUrl = (c: XtreamCreds, streamId: number, ext = "m3u8") =>
  `${normalizeBase(c.server)}live/${enc(c.username)}/${enc(c.password)}/${streamId}.${ext}`;
export const movieUrl = (c: XtreamCreds, streamId: number, ext = "mp4") =>
  `${normalizeBase(c.server)}movie/${enc(c.username)}/${enc(c.password)}/${streamId}.${ext || "mp4"}`;
export const episodeUrl = (c: XtreamCreds, episodeId: number, ext = "mp4") =>
  `${normalizeBase(c.server)}series/${enc(c.username)}/${enc(c.password)}/${episodeId}.${ext || "mp4"}`;
export const timeshiftUrl = (c: XtreamCreds, streamId: number, startMs: number, minutes: number) => {
  const d = new Date(startMs);
  const p = (n: number) => String(n).padStart(2, "0");
  const stamp = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}:${p(d.getHours())}-${p(d.getMinutes())}`;
  return `${normalizeBase(c.server)}streaming/timeshift.php?username=${enc(c.username)}&password=${enc(c.password)}&stream=${streamId}&start=${stamp}&duration=${minutes}`;
};
const enc = encodeURIComponent;

export const xmltvUrl = (c: XtreamCreds) =>
  `${normalizeBase(c.server)}xmltv.php?username=${enc(c.username)}&password=${enc(c.password)}`;

export interface VodInfo {
  info?: {
    name?: string; o_name?: string; cover_big?: string; movie_image?: string; releasedate?: string; release_date?: string;
    youtube_trailer?: string; director?: string; actors?: string; cast?: string; description?: string; plot?: string;
    genre?: string; country?: string; duration?: string; duration_secs?: number; rating?: string; age?: string;
    backdrop_path?: string[] | string; episode_run_time?: string;
  };
  movie_data?: { stream_id?: number; container_extension?: string };
}

export interface SeriesInfo {
  info?: {
    name?: string; cover?: string; plot?: string; cast?: string; director?: string; genre?: string; releaseDate?: string;
    release_date?: string; rating?: string; backdrop_path?: string[] | string; episode_run_time?: string; youtube_trailer?: string;
  };
  seasons?: Array<{ season_number?: number; name?: string; cover?: string; episode_count?: number | string }>;
  episodes?: Record<string, Array<{
    id: string | number; title?: string; episode_num?: number | string; container_extension?: string;
    info?: { duration_secs?: number; duration?: string; plot?: string; movie_image?: string; rating?: number | string };
  }>> | Array<Array<never>>;
}

export const vodInfo = (t: Transport, c: XtreamCreds, id: number, signal?: AbortSignal) =>
  xtreamJson<VodInfo>(t, c, { action: "get_vod_info", vod_id: id }, signal);
export const seriesInfo = (t: Transport, c: XtreamCreds, id: number, signal?: AbortSignal) =>
  xtreamJson<SeriesInfo>(t, c, { action: "get_series_info", series_id: id }, signal);
export const shortEpg = (t: Transport, c: XtreamCreds, streamId: number, limit = 6, signal?: AbortSignal) =>
  xtreamJson<{ epg_listings?: Array<{ title?: string; description?: string; start_timestamp?: string; stop_timestamp?: string; start?: string; end?: string }> }>(
    t, c, { action: "get_short_epg", stream_id: streamId, limit }, signal);

/** Les titres du guide Xtream sont encodés en base64. */
export function b64text(s: string | undefined): string {
  if (!s) return "";
  try {
    const bin = atob(s);
    const bytes = Uint8Array.from(bin, (ch) => ch.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  } catch {
    return s;
  }
}
