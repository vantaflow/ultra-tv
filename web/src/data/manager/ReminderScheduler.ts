// Browser-based reminder scheduler. Replaces the Android WorkManager-backed
// ProgramReminderManagerImpl. Notifications fire ~2 min before the program starts.
//
// Limitations: only runs while a tab is open. A service-worker push-style scheduler
// would need a backend; this is best-effort.

import { reminderRepo } from "@data/db/repositories";
import type { ProgramReminder } from "@domain/model";

const LEAD_MS = 2 * 60 * 1000;
const POLL_INTERVAL_MS = 60 * 1000;

let pollTimer: ReturnType<typeof setInterval> | null = null;

async function requestPermissionIfNeeded() {
  if (!("Notification" in window)) return false;
  if (Notification.permission === "granted") return true;
  if (Notification.permission === "denied") return false;
  const res = await Notification.requestPermission();
  return res === "granted";
}

async function fire(r: ProgramReminder) {
  const can = await requestPermissionIfNeeded();
  if (!can) return;
  new Notification(`Now starting: ${r.programTitle}`, {
    body: `Channel ID ${r.channelId} · ${new Date(r.startTime).toLocaleTimeString()}`,
    tag: `reminder-${r.id}`,
  });
  // Mark fired so we don't re-notify
  // We update the row in place via a tiny repo helper (defined inline to avoid touching everything).
  try {
    const all = await reminderRepo.pending();
    const existing = all.find((x) => x.id === r.id);
    if (existing) {
      // Re-add as fired
      const { db } = await import("@data/db/database");
      await db.reminders.update(r.id, { fired: true });
    }
  } catch (e) {
    console.warn("Failed to mark reminder fired:", e);
  }
}

async function tick() {
  const pending = await reminderRepo.pending();
  const now = Date.now();
  for (const r of pending) {
    if (r.notifyAt <= now + LEAD_MS) {
      void fire(r);
    }
  }
}

export function startReminderScheduler() {
  if (pollTimer) return;
  void tick();
  pollTimer = setInterval(tick, POLL_INTERVAL_MS);
}

export function stopReminderScheduler() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = null;
}

export async function scheduleReminder(opts: Omit<ProgramReminder, "id" | "fired" | "notifyAt">) {
  const notifyAt = Math.max(0, opts.startTime - LEAD_MS);
  await reminderRepo.add({
    providerId: opts.providerId,
    channelId: opts.channelId,
    programTitle: opts.programTitle,
    startTime: opts.startTime,
    endTime: opts.endTime,
    notifyAt,
    fired: false,
  });
  // Ask for permission proactively the first time the user schedules something.
  void requestPermissionIfNeeded();
}
