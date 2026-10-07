// Scrobble Trakt (via le Worker) : films et épisodes uniquement, jamais le direct ni la rediffusion.
// La logique d'état (`Scrobbler`) est pure et testable ; `resolveIdentity` / `startScrobble` font le lien
// avec le catalogue local et le Worker. Tout est « tire et oublie » : une erreur réseau ne touche jamais la lecture.

import { db } from "@/db/db";
import { traktScrobble, type ScrobbleAction, type ScrobbleBody, type ScrobbleResult } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";
import type { Source } from "@/db/types";
import type { PlayState } from "./engine";
import type { PlayTarget } from "./resolve";

/** Identité du média, sans l'action ni la progression. */
export type ScrobbleItem = Omit<ScrobbleBody, "action" | "progress">;
export type ScrobbleSend = (body: ScrobbleBody) => Promise<ScrobbleResult | null | void>;

/** Progression 0..100 (arrondie à 0,01), ou null si la durée est inconnue / infinie. */
export function progressOf(pos: number, dur: number): number | null {
  if (!Number.isFinite(dur) || dur <= 0 || !Number.isFinite(pos)) return null;
  return Math.round(Math.min(100, Math.max(0, (pos / dur) * 100)) * 100) / 100;
}

/** Lit un identifiant TMDB dans la fiche Xtream (nombre ou chaîne numérique), sinon undefined. */
export function tmdbOf(v: unknown): number | undefined {
  const n = typeof v === "string" ? Number(v.trim()) : typeof v === "number" ? v : NaN;
  return Number.isInteger(n) && n > 0 ? n : undefined;
}

/**
 * Une instance par média lu. Les envois d'un même média sont sérialisés (un seul en vol) ;
 * pas deux actions identiques d'affilée ; `linked:false` coupe les envois pour le reste du média.
 */
export class Scrobbler {
  private last: ScrobbleAction | null = null;
  private chain: Promise<void> = Promise.resolve();
  private off = false;
  private finished = false;
  private seen: { pos: number; dur: number } | null = null;

  /** `item` peut être résolu plus tard (promesse) ; null = média non scrobblable. */
  constructor(private item: Promise<ScrobbleItem | null> | ScrobbleItem | null, private send: ScrobbleSend) {}

  /** Transition d'état du lecteur. « buffering » / « loading » ne sont pas des pauses : ignorés. */
  onState(s: PlayState, pos: number, dur: number) {
    this.note(pos, dur);
    if (s === "playing") this.act("start", pos, dur);
    else if (s === "paused") { if (this.last === "start") this.act("pause", pos, dur); }
    else if (s === "ended") this.close(pos, dur);
  }

  /** Mémorise la dernière position valide (repli quand le <video> n'est plus lisible à la fermeture). */
  note(pos: number, dur: number) { if (progressOf(pos, dur) != null) this.seen = { pos, dur }; }

  /** Fin du média (fin de lecture, changement de cible, fermeture du lecteur). */
  close(pos: number, dur: number) {
    if (progressOf(pos, dur) == null && this.seen) { pos = this.seen.pos; dur = this.seen.dur; }
    if (this.last === "start" || this.last === "pause") this.act("stop", pos, dur);
    this.finished = true;
  }

  private act(action: ScrobbleAction, pos: number, dur: number) {
    if (this.off || this.finished || this.last === action) return;
    const progress = progressOf(pos, dur);
    if (progress == null) return;
    this.last = action;
    this.chain = this.chain.then(async () => {
      if (this.off) return;
      try {
        const item = await this.item;
        if (!item) { this.off = true; return; }
        const r = await this.send({ ...item, action, progress });
        if (r && r.linked === false) this.off = true;
      } catch { /* réseau, 401, 429 : on n'insiste pas */ }
    });
  }
}

/** Identité d'un film / épisode (titre + année du catalogue, TMDB de la fiche en cache si présent). Null = ne pas scrobbler. */
export async function resolveIdentity(t: PlayTarget, source: Source): Promise<ScrobbleItem | null> {
  try {
    if (t.replay || (t.kind !== "movie" && t.kind !== "episode")) return null;
    if (t.kind === "movie") {
      const row = await db.movies.where("[sourceId+streamId]").equals([source.cid, t.refId]).first();
      const det = await db.details.get(`vod:${source.id}:${t.refId}`);
      const tmdb = tmdbOf((det?.json as { info?: { tmdb_id?: unknown } } | undefined)?.info?.tmdb_id);
      return clean({ kind: "movie", title: row?.title || t.title, year: row?.year ?? undefined, tmdb });
    }
    if (!t.season || !t.episode || t.seriesId == null) return null;
    const row = await db.series.where("[sourceId+seriesId]").equals([source.cid, t.seriesId]).first();
    const det = await db.details.get(`series:${source.id}:${t.seriesId}`);
    const tmdb = tmdbOf((det?.json as { info?: { tmdb?: unknown } } | undefined)?.info?.tmdb);
    return clean({ kind: "episode", title: row?.title || t.title, year: row?.year ?? undefined, tmdb, season: t.season, episode: t.episode });
  } catch { return null; }
}

function clean(i: ScrobbleItem): ScrobbleItem | null {
  const title = i.title.trim().slice(0, 200);
  if (!title) return null;
  const year = i.year && i.year >= 1900 && i.year <= 2099 ? i.year : undefined;
  return { ...i, title, year };
}

/** Envoi réel : seulement si l'appareil est appairé ; null sinon (le Scrobbler s'arrête alors pour ce média). */
const sendViaWorker: ScrobbleSend = async (body) => {
  if (!useCloud.getState().paired) return { linked: false };
  const token = await getToken();
  if (!token) return { linked: false };
  return traktScrobble(useCloud.getState().worker, token, body);
};

/** Démarre le suivi d'une cible ; null si elle n'est pas scrobblable (direct, rediffusion, appareil non appairé). */
export function startScrobble(t: PlayTarget, source: Source): Scrobbler | null {
  if (t.kind === "live" || t.replay || !useCloud.getState().paired) return null;
  return new Scrobbler(resolveIdentity(t, source), sendViaWorker);
}
