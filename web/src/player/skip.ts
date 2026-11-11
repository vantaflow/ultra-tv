// Passer l'intro / le résumé / le générique, d'après TheIntroDB (base communautaire, sans clé).
// Seuls l'identifiant TMDB, la saison, l'épisode et la durée partent vers TheIntroDB : jamais d'identifiant ni
// d'adresse IPTV. Logique pure (URL, lecture de la réponse, passage en cours) testée à part ; même règle que l'app Android.

import { currentTransport, HttpError, transportFetch } from "@/net/transport";
import type { Source } from "@/db/types";
import type { PlayTarget } from "./resolve";
import { resolveIdentity } from "./trakt";

export type SkipKind = "intro" | "recap" | "credits";
/** `end` null = jusqu'à la fin du média ; début absent côté API = depuis le tout début (0). Secondes. */
export interface SkipSegment { kind: SkipKind; start: number; end: number | null }
/** Action proposée : sauter jusqu'à `to` (secondes), ou passer à l'épisode suivant. */
export interface SkipOffer { kind: SkipKind; to: number | null; next: boolean }

export const SKIP_BASE = "https://api.theintrodb.org/v4/media";
/** Le bouton disparaît un peu avant la fin du passage (sauter 1 s ne sert à rien). */
export const END_MARGIN = 1.5;

export function skipUrl(tmdb: number, season?: number, episode?: number, durationSec?: number): string {
  let u = `${SKIP_BASE}?tmdb_id=${tmdb}`;
  if (season != null && episode != null) u += `&season=${season}&episode=${episode}`;
  if (durationSec && Number.isFinite(durationSec) && durationSec > 0) u += `&duration_ms=${Math.round(durationSec * 1000)}`;
  return u;
}

/** Réponse `/media` → passages valides triés par début (secondes). Réponse illisible : liste vide. */
export function parseSkip(json: unknown): SkipSegment[] {
  if (!json || typeof json !== "object") return [];
  const o = json as Record<string, unknown>;
  const out: SkipSegment[] = [];
  for (const kind of ["intro", "recap", "credits"] as const) {
    const arr = o[kind];
    if (!Array.isArray(arr)) continue;
    for (const s of arr) {
      if (!s || typeof s !== "object") continue;
      const r = s as { start_ms?: unknown; end_ms?: unknown };
      const start = r.start_ms == null ? 0 : typeof r.start_ms === "number" ? r.start_ms : NaN;
      const end = r.end_ms == null ? null : typeof r.end_ms === "number" ? r.end_ms : NaN;
      if (!Number.isFinite(start) || start < 0 || (end != null && (!Number.isFinite(end) || end <= start))) continue;
      out.push({ kind, start: start / 1000, end: end == null ? null : end / 1000 });
    }
  }
  return out.sort((a, b) => a.start - b.start);
}

/**
 * Passage en cours à `pos` et action : intro / résumé → leur fin ; générique d'épisode → épisode suivant s'il existe ;
 * générique suivi d'une scène → sa fin ; générique jusqu'au bout d'un film → rien (sauter reviendrait à quitter).
 */
export function skipOfferAt(segs: SkipSegment[], pos: number, dur: number, episode: boolean, hasNext: boolean): SkipOffer | null {
  if (!(pos >= 0)) return null;
  const d = Number.isFinite(dur) && dur > 0 ? dur : null;
  for (const s of segs) {
    const end = s.end ?? d;
    if (pos < s.start) continue;
    if (end != null && pos >= end - END_MARGIN) continue;
    if (end == null && s.kind !== "credits") continue;
    if (s.kind !== "credits") return { kind: s.kind, to: end, next: false };
    if (episode && hasNext) return { kind: s.kind, to: null, next: true };
    if (s.end != null && d != null && s.end < d - 5) return { kind: s.kind, to: s.end, next: false };
    return null;
  }
  return null;
}

const cache = new Map<string, SkipSegment[]>();

/** Passages de la cible (film / épisode). Inconnu, sans TMDB, réseau absent : liste vide, jamais d'exception. */
export async function loadSkips(t: PlayTarget, source: Source, durationSec: number): Promise<SkipSegment[]> {
  if (t.replay || (t.kind !== "movie" && t.kind !== "episode")) return [];
  const key = `${source.id}:${t.kind}:${t.refId}`;
  const hit = cache.get(key);
  if (hit) return hit;
  const id = await resolveIdentity(t, source);
  if (!id?.tmdb || (t.kind === "episode" && (id.season == null || id.episode == null))) return [];
  try {
    const res = await transportFetch(await currentTransport(), skipUrl(id.tmdb, id.season, id.episode, durationSec), { signal: AbortSignal.timeout(8_000) });
    const segs = parseSkip(await res.json());
    cache.set(key, segs);
    return segs;
  } catch (e) {
    if (e instanceof HttpError && e.status === 404) { cache.set(key, []); }
    return [];
  }
}
