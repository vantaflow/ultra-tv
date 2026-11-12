// Rappels et enregistrements : persistance (Dexie), planificateur (un battement recalculé au prochain événement, au plus
// toutes les 30 s), notifications (système via Electron, Web Notification en navigateur, sinon bandeau dans l'appli) et
// pilotage de l'enregistreur du processus principal. Logique de décision : lib/schedule.ts (testée).
import { create } from "zustand";
import { db } from "@/db/db";
import type { ChannelRow, ProgramRow, Source } from "@/db/types";
import { translate, type Key } from "@/i18n";
import {
  canRecord, concurrentOn, connectionRisk, dueRecordings, dueReminders, nextRecordingDelay, nextReminderDelay, progId, recBaseName, recordWindow,
  type ProgRef, type Recording, type Reminder,
} from "@/lib/schedule";
import { STALKER_UA } from "@/net/stalker";
import { bridge, type RecEvent } from "@/net/transport";
import { candidates, stalkerCandidate } from "@/player/resolve";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { usePrefs } from "./prefs";
import { useSources } from "./sources";
import { useUi } from "./ui";

const tr = (k: Key, vars?: Record<string, string | number>) => translate(usePrefs.getState().lang, k, vars);
const hm = (ms: number) => new Date(ms).toLocaleTimeString(usePrefs.getState().lang, { hour: "2-digit", minute: "2-digit" });

interface ScheduleStore {
  /** Rappels notifiés dans l'appli (aucune notification système possible). */
  banners: Reminder[];
  /** Octets reçus par enregistrement en cours (événements du processus principal). */
  progress: Record<string, number>;
  dismiss: (id: string) => void;
}
export const useSchedule = create<ScheduleStore>((set) => ({
  banners: [],
  progress: {},
  dismiss: (id) => set((s) => ({ banners: s.banners.filter((b) => b.id !== id) })),
}));

/** Enregistrement possible ici (application de bureau avec l'enregistreur). */
export const recordingAvailable = () => !!bridge()?.recStart;

/** Programme du guide sur une chaîne de la source → référence planifiable. */
export const progRef = (source: Source, ch: ChannelRow, p: Pick<ProgramRow, "title" | "start" | "end">): ProgRef => ({
  sourceId: source.id!, streamId: ch.streamId, channel: ch.display, epg: ch.epg, title: p.title, start: p.start, end: p.end,
});

// ───────────────────────── Rappels ─────────────────────────

export async function toggleReminder(p: ProgRef): Promise<boolean> {
  const id = progId(p);
  if (await db.reminders.get(id)) {
    await db.reminders.delete(id);
    useUi.getState().toast(tr("rem.removed"));
    kick();
    return false;
  }
  await db.reminders.put({ ...p, id, createdAt: Date.now() });
  // Navigateur : la permission se demande sur le geste de l'utilisateur ; refusée, le bandeau de l'appli prend le relais.
  if (!bridge()?.notify && typeof Notification !== "undefined" && Notification.permission === "default") {
    void Notification.requestPermission().catch(() => "denied");
  }
  useUi.getState().toast(tr("rem.added"));
  kick();
  return true;
}

export const removeReminder = async (id: string) => { await db.reminders.delete(id); kick(); };

/** Ouvre la chaîne d'un rappel (clic sur la notification ou sur le bandeau). */
export async function openProgChannel(r: ProgRef): Promise<boolean> {
  const source = useSources.getState().list.find((s) => s.id === r.sourceId);
  if (!source?.cid) return false;
  const ch = await db.channels.where("[sourceId+streamId]").equals([source.cid, r.streamId]).first();
  if (!ch) return false;
  if (usePrefs.getState().activeSourceId !== source.id) usePrefs.getState().set({ activeSourceId: source.id! });
  usePlayer.getState().open(channelTarget(source, ch), "full", null);
  return true;
}

async function notifyReminder(r: Reminder): Promise<void> {
  const title = tr("rem.notifTitle", { title: r.title });
  const body = tr("rem.notifBody", { time: hm(r.start), channel: r.channel });
  const b = bridge();
  if (b?.notify) {
    if (await b.notify({ id: r.id, title, body }).catch(() => false)) return;
  } else if (typeof Notification !== "undefined" && Notification.permission === "granted") {
    try {
      const n = new Notification(title, { body, tag: r.id });
      n.onclick = () => { window.focus(); void openProgChannel(r); n.close(); };
      return;
    } catch { /* notifications indisponibles : bandeau */ }
  }
  useSchedule.setState((s) => ({ banners: [...s.banners.filter((x) => x.id !== r.id), r] }));
}

// ───────────────────────── Enregistrements ─────────────────────────

export type RecordResult = { ok: true; immediate: boolean } | { ok: false; reason: "ended" | "exists" | "unavailable" };

/** Vérifications avant confirmation : créneau et risque de connexion (abonnement à connexions limitées). */
export async function recordPlan(source: Source, p: ProgRef, now = Date.now()) {
  const win = recordWindow(p, now);
  if (!win) return null;
  const list = await db.recordings.toArray();
  const exists = list.some((r) => r.id === progId(p) && (r.state === "scheduled" || r.state === "recording"));
  return { ...win, exists, risk: connectionRisk(source.maxConnections, concurrentOn(list, p.sourceId, win, progId(p))) };
}

export async function scheduleRecording(p: ProgRef): Promise<RecordResult> {
  if (!recordingAvailable()) return { ok: false, reason: "unavailable" };
  const now = Date.now();
  const win = recordWindow(p, now);
  if (!win) return { ok: false, reason: "ended" };
  const id = progId(p);
  const prev = await db.recordings.get(id);
  if (prev && (prev.state === "scheduled" || prev.state === "recording")) return { ok: false, reason: "exists" };
  await db.recordings.put({ ...p, id, createdAt: now, recStart: win.recStart, recEnd: win.recEnd, state: "scheduled" });
  useUi.getState().toast(tr(win.immediate ? "rec.starting" : "rec.scheduled"));
  kick();
  return { ok: true, immediate: win.immediate };
}

/** Annule une programmation, ou arrête un enregistrement en cours (le fichier déjà écrit est gardé). */
export async function cancelRecording(r: Recording): Promise<void> {
  if (r.state === "recording") { await bridge()?.recStop?.(r.id); return; }
  await db.recordings.delete(r.id);
  kick();
}

/** Supprime un enregistrement terminé (et son fichier). */
export async function deleteRecording(r: Recording): Promise<void> {
  if (r.path) await bridge()?.recDelete?.(r.path).catch(() => false);
  await db.recordings.delete(r.id);
}

const errorCode = (code: string): string => (/^HTTP_(403|429|458|509)$/.test(code) ? "limit" : code === "DISK" || code === "dir" ? "disk" : /^HTTP_|HLS|EMPTY|NETWORK|ETIMEDOUT|ECONN|ENOTFOUND|EAI_/.test(code) ? "stream" : code);

async function startRecording(r: Recording): Promise<void> {
  const b = bridge();
  const fail = (error: string) => db.recordings.update(r.id, { state: "failed", error, finishedAt: Date.now() });
  if (!b?.recStart) { await fail("unavailable"); return; }
  const source = useSources.getState().list.find((s) => s.id === r.sourceId);
  const ch = source?.cid ? await db.channels.where("[sourceId+streamId]").equals([source.cid, r.streamId]).first() : undefined;
  if (!source || !ch) { await fail("stream"); return; }
  const target = channelTarget(source, ch);
  let url: string | null = null;
  try {
    // Flux continu MPEG-TS de préférence (copie octet pour octet) ; HLS accepté, ses segments sont ajoutés au fichier.
    url = source.type === "stalker" ? (await stalkerCandidate(source, target))?.url ?? null : candidates(source, target, { liveFormat: "ts", preferMp4: false })[0]?.url ?? null;
  } catch { url = null; }
  if (!url) { await fail("stream"); return; }
  // Marqué « en cours » avant l'appel : un événement de fin très rapide trouve la bonne ligne.
  await db.recordings.update(r.id, { state: "recording" });
  const res = await b.recStart({
    id: r.id, url, endAt: r.recEnd, name: recBaseName(r.channel, r.title, r.start),
    userAgent: source.userAgent || (source.type === "stalker" ? STALKER_UA : null), referer: source.referer || null,
  }).catch(() => ({ ok: false as const, error: "unavailable" }));
  url = null;
  if (!res.ok) { await fail(errorCode(res.error ?? "unavailable")); useUi.getState().toast(tr("rec.failedToast", { title: r.title })); return; }
  await db.recordings.update(r.id, { path: res.path });
  // Une seule connexion autorisée et une lecture en cours sur cette source : l'un des deux risque d'être coupé.
  const playing = usePlayer.getState().target;
  const busy = source.maxConnections === 1 && playing && playing.sourceId === source.id && usePlayer.getState().mode !== "closed";
  useUi.getState().toast(tr(busy ? "rec.startedBusy" : "rec.started"));
}

async function onRecEvent(e: RecEvent): Promise<void> {
  if (e.type === "progress") { useSchedule.setState((s) => ({ progress: { ...s.progress, [e.id]: e.bytes } })); return; }
  const r = await db.recordings.get(e.id);
  useSchedule.setState((s) => { const p = { ...s.progress }; delete p[e.id]; return { progress: p }; });
  if (!r || (r.state !== "recording" && r.state !== "scheduled")) return;
  if (e.type === "done") {
    await db.recordings.update(e.id, { state: "done", path: e.path ?? r.path, bytes: e.bytes, finishedAt: Date.now(), error: e.reason === "stopped" && Date.now() < r.recEnd - 60_000 ? "partial" : undefined });
    useUi.getState().toast(tr("rec.doneToast", { title: r.title }));
  } else {
    await db.recordings.update(e.id, { state: "failed", path: e.path, bytes: e.bytes, error: errorCode(e.code), finishedAt: Date.now() });
    useUi.getState().toast(tr("rec.failedToast", { title: r.title }));
  }
}

// ───────────────────────── Planificateur ─────────────────────────

let timer: ReturnType<typeof setTimeout> | undefined;
let running = false;
let again = false;
let started = false;

async function tick(): Promise<void> {
  if (running) { again = true; return; }
  running = true;
  try {
    const now = Date.now();
    const rems = await db.reminders.toArray();
    const due = dueReminders(rems, now);
    if (due.expired.length) await db.reminders.bulkDelete(due.expired.map((r) => r.id));
    for (const r of due.fire) {
      await db.reminders.update(r.id, { firedAt: now });
      await notifyReminder(r);
    }
    // Bandeaux de rappels dont le programme est fini : retirés.
    useSchedule.setState((s) => (s.banners.some((b) => b.end <= now) ? { banners: s.banners.filter((b) => b.end > now) } : s));

    const recs = await db.recordings.toArray();
    const b = bridge();
    const known = b?.recList ? await b.recList().catch(() => ({ active: [], finished: [] })) : { active: [], finished: [] };
    // Fins d'enregistrement survenues pendant un rechargement de l'interface : rejouées.
    for (const ev of known.finished) if (recs.some((r) => r.id === ev.id && r.state === "recording")) await onRecEvent(ev);
    const fresh = known.finished.length ? await db.recordings.toArray() : recs;
    const plan = dueRecordings(fresh, now, new Set(known.active.map((a) => a.id)));
    for (const r of plan.missed) await db.recordings.update(r.id, { state: "failed", error: "missed", finishedAt: now });
    for (const r of plan.interrupted) await db.recordings.update(r.id, { state: "failed", error: "interrupted", finishedAt: now });
    // Au lancement, les sources (identifiants déchiffrés) arrivent un peu après le premier battement : on attend.
    const sourcesReady = useSources.getState().ready;
    if (sourcesReady) for (const r of plan.start) await startRecording(r);

    const next = Math.min(
      plan.start.length && !sourcesReady ? 1000 : 30_000,
      nextReminderDelay((await db.reminders.toArray()), Date.now()) ?? 30_000,
      nextRecordingDelay((await db.recordings.toArray()), Date.now()) ?? 30_000,
      30_000,
    );
    clearTimeout(timer);
    timer = setTimeout(() => void tick(), Math.max(1000, next + 50));
  } catch {
    clearTimeout(timer);
    timer = setTimeout(() => void tick(), 30_000);
  } finally {
    running = false;
    if (again) { again = false; void tick(); }
  }
}

/** Relance le planificateur tout de suite (ajout, suppression). */
export function kick() { if (started) void tick(); }

/** Démarré une fois par l'application : reprogramme rappels et enregistrements au lancement, écoute l'enregistreur. */
export function startScheduler(): () => void {
  if (started) return () => {};
  started = true;
  const b = bridge();
  const offRec = b?.onRecEvent?.((e) => void onRecEvent(e));
  const offClick = b?.onNotifyClick?.((id) => {
    void db.reminders.get(id).then((r) => { if (r) void openProgChannel(r); });
  });
  void tick();
  return () => {
    started = false;
    clearTimeout(timer);
    if (typeof offRec === "function") offRec();
    if (typeof offClick === "function") offClick();
  };
}

/** Textes de la confirmation de fermeture (enregistrement en cours), dans la langue de l'interface. */
export function pushCloseLabels() {
  void bridge()?.recSetLabels?.({ title: tr("rec.closeTitle"), message: tr("rec.closeMessage"), quit: tr("rec.quit"), cancel: tr("common.cancel") }).catch(() => false);
}

export { canRecord };
