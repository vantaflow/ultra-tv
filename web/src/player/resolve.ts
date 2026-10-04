// Construction des URL de lecture à partir de la source (identifiants déchiffrés) et de l'élément choisi.
import type { Source } from "@/db/types";
import { credsOf } from "@/sync/core";
import { episodeUrl, liveUrl, movieUrl, timeshiftUrl } from "@/net/xtream";
import type { Format } from "./engine";

export type PlayKind = "live" | "movie" | "episode";

export interface PlayTarget {
  kind: PlayKind;
  sourceId: number;
  /** Génération de catalogue au moment de l'ouverture (pour zapper). */
  cid: number;
  /** stream_id (direct, film) ou identifiant d'épisode. */
  refId: number;
  title: string;
  subtitle?: string;
  image?: string | null;
  ext?: string;
  /** Direct : position dans le catalogue et métadonnées. */
  channel?: { ord: number; catExt: string | null; num: number; epg: string | null; archive: boolean; q: number; logo: string | null };
  /** M3U : URL de flux de la playlist. */
  url?: string;
  seriesId?: number;
  season?: number;
  episode?: number;
  startAt?: number;
  /** Rediffusion (catch-up). */
  replay?: { start: number; minutes: number };
}

export interface Candidate { url: string; format: Format; label: string }

export function candidates(source: Source, t: PlayTarget, opts: { liveFormat: "m3u8" | "ts"; preferMp4: boolean }): Candidate[] {
  const c = credsOf(source);
  const out: Candidate[] = [];
  const add = (url: string, format: Format, label: string) => { if (!out.some((o) => o.url === url)) out.push({ url, format, label }); };
  if (t.url) {
    const f = t.url.split("?")[0]!.toLowerCase();
    add(t.url, f.endsWith(".m3u8") ? "hls" : f.endsWith(".ts") ? "ts" : t.kind === "live" ? "hls" : "native", "M3U");
    return out;
  }
  if (t.kind === "live") {
    if (t.replay) { add(timeshiftUrl(c, t.refId, t.replay.start, t.replay.minutes), "ts", "TS"); return out; }
    const order: Array<["m3u8" | "ts", Format]> = opts.liveFormat === "ts" ? [["ts", "ts"], ["m3u8", "hls"]] : [["m3u8", "hls"], ["ts", "ts"]];
    for (const [ext, fmt] of order) add(liveUrl(c, t.refId, ext), fmt, ext.toUpperCase());
    return out;
  }
  const exts = [t.ext || "mp4"];
  if (opts.preferMp4 && exts[0] !== "mp4") exts.unshift("mp4");
  if (!exts.includes("mkv")) exts.push("mkv");
  for (const ext of exts) add(t.kind === "movie" ? movieUrl(c, t.refId, ext) : episodeUrl(c, t.refId, ext), "native", ext.toUpperCase());
  return out;
}
