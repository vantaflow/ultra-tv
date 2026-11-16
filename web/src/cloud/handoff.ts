// Reprise de la lecture d'un appareil à l'autre (« Envoyer vers… » / « Reprendre sur cet appareil ? »).
// Envoi : une référence (source du compte, type, identifiant chez le fournisseur, position), jamais d'URL de flux ni
// d'identifiants. Réception : l'en-tête de GET /api/config (donc aucune interrogation de plus) ; la demande est résolue
// dans le catalogue de CET appareil et ne démarre jamais seule : l'utilisateur répond Oui / Non.

import { create } from "zustand";
import { db } from "@/db/db";
import type { Source } from "@/db/types";
import { channelTarget, movieTarget } from "@/player/targets";
import type { PlayTarget } from "@/player/resolve";
import { usePlayer } from "@/player/store";
import { useSources } from "@/state/sources";
import { useUi } from "@/state/ui";
import { usePrefs } from "@/state/prefs";
import { translate } from "@/i18n";
import { parseHandoffHeader, sendHandoff, type HandoffRef, type HandoffRequest } from "./client";

/** Durée de validité d'une demande (la même que la file du Worker) : au-delà, la question n'a plus de sens. */
export const HANDOFF_TTL_MS = 120_000;

interface HandoffState { pending: Array<HandoffRequest & { seenAt: number }> }
export const useHandoff = create<HandoffState>(() => ({ pending: [] }));

/** Demandes déjà traitées (ou refusées) : l'en-tête reste présent 2 min, on ne repose pas la question. */
const handled = new Set<string>();
const remember = (id: string) => { handled.add(id); if (handled.size > 60) handled.delete(handled.values().next().value as string); };

/** Réception : appelée avec l'en-tête de chaque réponse de /api/config (200 ou 304). */
export function noteHandoffHeader(header: string | null | undefined, now = Date.now()): void {
  const items = parseHandoffHeader(header);
  const cur = useHandoff.getState().pending.filter((p) => now - p.seenAt < HANDOFF_TTL_MS);
  const fresh = items.filter((x) => !handled.has(x.i) && !cur.some((p) => p.i === x.i)).map((x) => ({ ...x, seenAt: now }));
  if (fresh.length || cur.length !== useHandoff.getState().pending.length) useHandoff.setState({ pending: [...fresh, ...cur].slice(0, 3) });
}

const tr = (key: Parameters<typeof translate>[1], vars?: Record<string, string | number>) => translate(usePrefs.getState().lang, key, vars);

/** Réponse de l'utilisateur : « Non » oublie la demande, « Oui » la résout dans le catalogue local et lance la lecture. */
export async function answerHandoff(id: string, accept: boolean): Promise<void> {
  const req = useHandoff.getState().pending.find((p) => p.i === id);
  remember(id);
  useHandoff.setState((s) => ({ pending: s.pending.filter((p) => p.i !== id) }));
  if (!req || !accept) return;
  const target = await resolveHandoff(req, useSources.getState().list);
  if (!target) { useUi.getState().toast(tr("handoff.notFound")); return; }
  usePlayer.getState().open(target, "full");
}

/** Cible de lecture dans le catalogue local, ou null (source absente de cet appareil, élément retiré du catalogue). */
export async function resolveHandoff(r: Pick<HandoffRequest, "s" | "t" | "r" | "p" | "o" | "n">, sources: Source[]): Promise<PlayTarget | null> {
  const src = sources.find((s) => s.cloudId === r.s && s.cid);
  const id = Number(r.r);
  if (!src || !Number.isFinite(id)) return null;
  const startAt = r.o > 1000 ? Math.floor(r.o / 1000) : undefined;
  if (r.t === "live") {
    const c = await db.channels.where("[sourceId+streamId]").equals([src.cid, id]).first();
    return c ? channelTarget(src, c) : null;
  }
  if (r.t === "movie") {
    const m = await db.movies.where("[sourceId+streamId]").equals([src.cid, id]).first();
    return m ? movieTarget(src, m, startAt) : null;
  }
  const sid = Number(r.p);
  if (!Number.isFinite(sid)) return null;
  const s = await db.series.where("[sourceId+seriesId]").equals([src.cid, sid]).first();
  if (!s) return null;
  return { kind: "episode", sourceId: src.id!, cid: src.cid, refId: id, title: s.title, subtitle: r.n, image: s.poster, seriesId: sid, startAt };
}

/** Référence de ce qui est lu ici, ou null si la source n'est pas liée au compte (rien que l'autre appareil puisse retrouver). */
export function handoffRefOf(t: PlayTarget, sources: Source[], positionSec: number): HandoffRef | null {
  const cloud = sources.find((s) => s.id === t.sourceId)?.cloudId;
  if (!cloud || t.protectedStream) return null;
  const n = t.kind === "episode" && t.subtitle ? `${t.title} · ${t.subtitle}` : t.title;
  return {
    src: cloud, t: t.kind, id: String(t.refId), ...(t.kind === "episode" && t.seriesId != null ? { par: String(t.seriesId) } : {}),
    pos: t.kind === "live" ? 0 : Math.max(0, Math.floor(positionSec * 1000)), n: n.slice(0, 100),
  };
}

export type SendOutcome = "sent" | "noSource" | "failed";

/** « Envoyer vers… » : dépose la demande pour l'appareil `to` (la file du compte la garde 2 min). */
export async function sendToDevice(worker: string, token: string, to: string, t: PlayTarget, positionSec: number): Promise<SendOutcome> {
  const ref = handoffRefOf(t, useSources.getState().list, positionSec);
  if (!ref) return "noSource";
  try { await sendHandoff(worker, token, to, ref); return "sent"; } catch { return "failed"; }
}
