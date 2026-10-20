// Construction des URL de lecture à partir de la source (identifiants déchiffrés) et de l'élément choisi.
import type { Source } from "@/db/types";
import { credsOf, stalkerOf } from "@/sync/core";
import { db } from "@/db/db";
import { isStalkerLink, parseStalkerLink, stalkerLink, type StalkerClient } from "@/net/stalker";
import { currentTransport } from "@/net/transport";
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
  // Portail Stalker : le lien n'existe qu'au moment de jouer (voir stalkerCandidate, asynchrone).
  if (source.type === "stalker") return [];
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
  const vod = (ext: string) => (t.kind === "movie" ? movieUrl(c, t.refId, ext) : episodeUrl(c, t.refId, ext));
  for (const ext of exts) add(vod(ext), "native", ext.toUpperCase());
  // Dernier recours : beaucoup de panneaux Xtream servent aussi le VOD en HLS (remux/transcodage audio AAC),
  // seule voie quand le conteneur natif joue sans son (AC-3/DTS non décodables par Chromium).
  add(vod("m3u8"), "hls", "M3U8");
  return out;
}

// ---------------------------------------------------------------- portail Stalker

/** Adresse interne `stalker:…` de l'élément à jouer : celle de la cible, sinon relue dans le catalogue (reprise, favoris). */
export async function stalkerLinkOf(t: PlayTarget): Promise<string | null> {
  if (isStalkerLink(t.url)) return t.url;
  if (t.kind === "live") return (await db.channels.where("[sourceId+streamId]").equals([t.cid, t.refId]).first())?.url ?? null;
  if (t.kind === "movie") return (await db.movies.where("[sourceId+streamId]").equals([t.cid, t.refId]).first())?.url ?? null;
  if (t.seriesId == null) return null;
  // Épisode : liste mise en cache par la fiche série, sinon commande de la série + numéro d'épisode.
  const info = (await db.details.get(`series:${t.sourceId}:${t.seriesId}`))?.json as { stalker?: Array<{ id: number; url: string }> } | undefined;
  const hit = info?.stalker?.find((e) => e.id === t.refId)?.url;
  if (hit) return hit;
  const row = await db.series.where("[sourceId+seriesId]").equals([t.cid, t.seriesId]).first();
  const base = row?.url ? parseStalkerLink(row.url) : null;
  return base && t.episode != null ? stalkerLink("vod", base.cmd, t.episode) : null;
}

const clients = new Map<string, StalkerClient>();

/** Client gardé par source (le jeton du portail est réutilisé d'une chaîne à l'autre). */
export async function stalkerClientFor(source: Source): Promise<StalkerClient> {
  const key = `${source.id}|${source.server}|${source.username}|${source.password}`;
  let c = clients.get(key);
  if (!c) { c = stalkerOf(await currentTransport(), source); clients.set(key, c); }
  return c;
}

/** Lien de lecture demandé au portail (create_link) juste avant d'ouvrir le flux ; format deviné d'après l'adresse. */
export async function stalkerCandidate(source: Source, t: PlayTarget): Promise<Candidate | null> {
  const link = await stalkerLinkOf(t);
  const p = link ? parseStalkerLink(link) : null;
  if (!p) return null;
  const url = await (await stalkerClientFor(source)).createLink(p.type, p.cmd, p.series);
  return { url, format: stalkerFormat(url, t.kind), label: "Stalker" };
}

export function stalkerFormat(url: string, kind: PlayKind): Format {
  const path = url.split("?")[0]!.toLowerCase();
  if (path.endsWith(".m3u8") || /[?&]extension=m3u8/i.test(url)) return "hls";
  if (path.endsWith(".ts") || /[?&]extension=ts/i.test(url)) return "ts";
  return kind === "live" ? "ts" : "native";
}
