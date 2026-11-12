// Rappels et enregistrements programmés : logique pure (planification, créneaux, conflits de connexion, noms de fichier).
// La persistance (Dexie) et les minuteries vivent dans state/schedule.ts ; le téléchargement dans electron/recorder.cjs.
// Aucune URL de flux ici : elle n'est reconstruite qu'au moment de démarrer un enregistrement, puis oubliée.

const MIN = 60_000;

/** Programme visé (chaîne de la source + horaires du guide). */
export interface ProgRef {
  sourceId: number;
  streamId: number;
  /** Nom affiché de la chaîne. */
  channel: string;
  epg: string | null;
  title: string;
  start: number;
  end: number;
}

// ───────────────────────── Rappels ─────────────────────────

export interface Reminder extends ProgRef {
  id: string;
  createdAt: number;
  /** Notification déjà montrée. */
  firedAt?: number;
}

/** La notification part une minute avant le début. */
export const REMIND_BEFORE_MS = MIN;
/** Application ouverte en retard : le rappel part encore jusqu'à 10 min après le début (programme toujours en cours). */
export const REMIND_GRACE_MS = 10 * MIN;

export const progId = (p: Pick<ProgRef, "sourceId" | "streamId" | "start">) => `${p.sourceId}:${p.streamId}:${p.start}`;

/** « Me rappeler » n'est proposé que pour un programme qui n'a pas commencé. */
export const canRemind = (p: Pick<ProgRef, "start">, now: number) => p.start > now;

export const fireAt = (r: Pick<Reminder, "start">) => r.start - REMIND_BEFORE_MS;

/**
 * À chaque battement : rappels à notifier maintenant, rappels périmés à supprimer (programme fini, ou manqué de plus de
 * 10 min parce que l'application était fermée).
 */
export function dueReminders<R extends Reminder>(list: R[], now: number): { fire: R[]; expired: R[] } {
  const fire: R[] = [];
  const expired: R[] = [];
  for (const r of list) {
    if (now >= r.end || (!r.firedAt && now >= r.start + REMIND_GRACE_MS)) { expired.push(r); continue; }
    if (!r.firedAt && now >= fireAt(r)) fire.push(r);
  }
  return { fire, expired };
}

/** Délai avant le prochain rappel à notifier (ms, ≥ 0) ; null s'il n'y en a pas. */
export function nextReminderDelay(list: Reminder[], now: number): number | null {
  let best: number | null = null;
  for (const r of list) {
    if (r.firedAt || now >= r.end) continue;
    const d = Math.max(0, fireAt(r) - now);
    if (best === null || d < best) best = d;
  }
  return best;
}

// ───────────────────────── Enregistrements ─────────────────────────

export type RecState = "scheduled" | "recording" | "done" | "failed" | "cancelled";

export interface Recording extends ProgRef {
  id: string;
  createdAt: number;
  /** Créneau réellement enregistré (marges comprises). */
  recStart: number;
  recEnd: number;
  state: RecState;
  /** Fichier écrit (chemin local, jamais l'adresse du flux). */
  path?: string;
  bytes?: number;
  /** Code d'échec court (« missed », « interrupted », « network », « disk »…). */
  error?: string;
  finishedAt?: number;
}

/** Marges : 2 min avant le début annoncé, 5 min après la fin. */
export const REC_BEFORE_MS = 2 * MIN;
export const REC_AFTER_MS = 5 * MIN;
/** Garde-fou : un enregistrement ne dépasse jamais 8 h. */
export const REC_MAX_MS = 8 * 60 * MIN;

/**
 * Créneau d'enregistrement d'un programme : début - 2 min (ou maintenant s'il est déjà passé), fin + 5 min.
 * null si le programme (marge comprise) est déjà terminé ou si ses horaires sont incohérents.
 */
export function recordWindow(p: Pick<ProgRef, "start" | "end">, now: number): { recStart: number; recEnd: number; immediate: boolean } | null {
  if (!(p.end > p.start)) return null;
  const from = p.start - REC_BEFORE_MS;
  const recEnd = Math.min(p.end + REC_AFTER_MS, Math.max(from, now) + REC_MAX_MS);
  if (recEnd <= now) return null;
  const immediate = from <= now;
  return { recStart: immediate ? now : from, recEnd, immediate };
}

/** « Enregistrer » est proposé tant que le programme n'est pas terminé. */
export const canRecord = (p: Pick<ProgRef, "start" | "end">, now: number) => recordWindow(p, now) !== null;

/**
 * Battement du planificateur : programmations à démarrer, programmations manquées (créneau passé, application fermée),
 * enregistrements « en cours » que le processus principal ne connaît plus (application relancée) : interrompus.
 */
export function dueRecordings<R extends Recording>(list: R[], now: number, active: ReadonlySet<string>): { start: R[]; missed: R[]; interrupted: R[] } {
  const start: R[] = [];
  const missed: R[] = [];
  const interrupted: R[] = [];
  for (const r of list) {
    if (r.state === "scheduled") {
      if (now >= r.recEnd) missed.push(r);
      else if (now >= r.recStart) start.push(r);
    } else if (r.state === "recording" && !active.has(r.id)) interrupted.push(r);
  }
  return { start, missed, interrupted };
}

/** Délai avant la prochaine programmation à démarrer (ms, ≥ 0) ; null s'il n'y en a pas. */
export function nextRecordingDelay(list: Recording[], now: number): number | null {
  let best: number | null = null;
  for (const r of list) {
    if (r.state !== "scheduled" || now >= r.recEnd) continue;
    const d = Math.max(0, r.recStart - now);
    if (best === null || d < best) best = d;
  }
  return best;
}

const overlaps = (a: { recStart: number; recEnd: number }, b: { recStart: number; recEnd: number }) => a.recStart < b.recEnd && b.recStart < a.recEnd;

/** Enregistrements (programmés ou en cours) de la même source qui chevauchent ce créneau. */
export function concurrentOn(list: Recording[], sourceId: number, win: { recStart: number; recEnd: number }, exceptId?: string): number {
  return list.filter((r) => r.id !== exceptId && r.sourceId === sourceId && (r.state === "scheduled" || r.state === "recording") && overlaps(r, win)).length;
}

/**
 * Limite de connexions du fournisseur : l'enregistrement occupe une connexion pendant tout son créneau.
 *  - "none" : rien à signaler (limite inconnue ou suffisante) ;
 *  - "shared" : l'enregistrement prend la dernière connexion libre (une lecture sur cette source coupera l'un ou l'autre) ;
 *  - "full" : la limite est déjà atteinte par d'autres enregistrements à cette heure-là (celui-ci échouera probablement).
 */
export function connectionRisk(maxConnections: number, concurrent: number): "none" | "shared" | "full" {
  if (!(maxConnections > 0)) return "none";
  if (concurrent + 1 > maxConnections) return "full";
  return concurrent + 1 === maxConnections ? "shared" : "none";
}

const pad = (n: number) => String(n).padStart(2, "0");

/** Nom de fichier (sans extension) : « 2026-11-12 20h45 - Chaîne - Titre », caractères interdits retirés, 120 caractères au plus. */
export function recBaseName(channel: string, title: string, start: number): string {
  const d = new Date(start);
  const stamp = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}h${pad(d.getMinutes())}`;
  const clean = (s: string) => s.normalize("NFC").replace(/[\u0000-\u001f<>:"/\\|?*]+/g, " ").replace(/\s+/g, " ").trim().replace(/[. ]+$/, "");
  const name = [stamp, clean(channel), clean(title)].filter(Boolean).join(" - ");
  return name.slice(0, 120).trim().replace(/[. ]+$/, "") || stamp;
}

/** Taille lisible (octets → « 1,2 Go »), séparateur décimal de la langue. */
export function fmtBytes(n: number, locale?: string): string {
  const units = ["o", "Ko", "Mo", "Go", "To"];
  let v = Math.max(0, n);
  let i = 0;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return `${v.toLocaleString(locale, { maximumFractionDigits: i >= 2 ? 1 : 0 })} ${units[i]}`;
}

/** Tri de l'écran : en cours, puis programmés (plus proche d'abord), puis terminés (plus récent d'abord). */
export function groupRecordings<R extends Recording>(list: R[]): { recording: R[]; scheduled: R[]; finished: R[] } {
  return {
    recording: list.filter((r) => r.state === "recording").sort((a, b) => a.recStart - b.recStart),
    scheduled: list.filter((r) => r.state === "scheduled").sort((a, b) => a.recStart - b.recStart),
    finished: list.filter((r) => r.state === "done" || r.state === "failed").sort((a, b) => (b.finishedAt ?? b.recEnd) - (a.finishedAt ?? a.recEnd)),
  };
}
