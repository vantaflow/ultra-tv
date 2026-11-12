import { describe, expect, it } from "vitest";
import {
  REC_AFTER_MS, REC_BEFORE_MS, REC_MAX_MS, REMIND_GRACE_MS, canRecord, canRemind, concurrentOn, connectionRisk, dueRecordings, dueReminders,
  fmtBytes, groupRecordings, nextRecordingDelay, nextReminderDelay, progId, recBaseName, recordWindow, type Recording, type Reminder,
} from "./schedule";

const MIN = 60_000;
const now = 1_800_000_000_000;
const ref = { sourceId: 1, streamId: 7, channel: "Stade 1", epg: "e7", title: "Club Nord – Club Sud" };
const rem = (start: number, end: number, firedAt?: number): Reminder => ({ ...ref, id: progId({ ...ref, start }), start, end, createdAt: 0, firedAt });

describe("Rappels", () => {
  it("identifiant stable par source, chaîne et début", () => {
    expect(progId({ sourceId: 1, streamId: 7, start: 42 })).toBe("1:7:42");
  });

  it("proposé seulement pour un programme futur", () => {
    expect(canRemind({ start: now + 1 }, now)).toBe(true);
    expect(canRemind({ start: now }, now)).toBe(false);
  });

  it("notifié une minute avant le début, pas avant", () => {
    const r = rem(now + 61_000, now + 60 * MIN);
    expect(dueReminders([r], now).fire).toEqual([]);
    expect(dueReminders([r], now + 1000).fire).toEqual([r]);
  });

  it("déjà notifié : plus jamais renvoyé, supprimé à la fin du programme", () => {
    const r = rem(now - MIN, now + 30 * MIN, now - 2 * MIN);
    expect(dueReminders([r], now)).toEqual({ fire: [], expired: [] });
    expect(dueReminders([r], now + 30 * MIN).expired).toEqual([r]);
  });

  it("application ouverte en retard : rattrapé jusqu'à 10 min, puis expiré", () => {
    const r = rem(now - 5 * MIN, now + 60 * MIN);
    expect(dueReminders([r], now).fire).toEqual([r]);
    const late = rem(now - REMIND_GRACE_MS, now + 60 * MIN);
    expect(dueReminders([late], now)).toEqual({ fire: [], expired: [late] });
  });

  it("délai jusqu'au prochain rappel", () => {
    expect(nextReminderDelay([], now)).toBeNull();
    expect(nextReminderDelay([rem(now + 10 * MIN, now + 20 * MIN), rem(now + 5 * MIN, now + 9 * MIN)], now)).toBe(4 * MIN);
    expect(nextReminderDelay([rem(now - MIN, now + 9 * MIN)], now)).toBe(0);
    expect(nextReminderDelay([rem(now + MIN * 5, now + 9 * MIN, now)], now)).toBeNull();
  });
});

const rec = (over: Partial<Recording> = {}): Recording => ({
  ...ref, id: "r", start: now, end: now + 90 * MIN, recStart: now - REC_BEFORE_MS, recEnd: now + 90 * MIN + REC_AFTER_MS, state: "scheduled", createdAt: 0, ...over,
});

describe("Créneaux d'enregistrement", () => {
  it("programmé : 2 min avant, 5 min après", () => {
    expect(recordWindow({ start: now + 60 * MIN, end: now + 150 * MIN }, now)).toEqual({ recStart: now + 58 * MIN, recEnd: now + 155 * MIN, immediate: false });
  });

  it("déjà commencé (ou dans la marge) : immédiat", () => {
    expect(recordWindow({ start: now - 30 * MIN, end: now + 30 * MIN }, now)).toEqual({ recStart: now, recEnd: now + 35 * MIN, immediate: true });
    expect(recordWindow({ start: now + MIN, end: now + 30 * MIN }, now)!.immediate).toBe(true);
  });

  it("terminé (marge comprise) ou incohérent : refusé", () => {
    expect(recordWindow({ start: now - 60 * MIN, end: now - 5 * MIN }, now)).toBeNull();
    expect(recordWindow({ start: now - 60 * MIN, end: now - 4 * MIN }, now)).not.toBeNull();
    expect(recordWindow({ start: now + MIN, end: now + MIN }, now)).toBeNull();
    expect(canRecord({ start: now - 60 * MIN, end: now - 10 * MIN }, now)).toBe(false);
  });

  it("borné à 8 h", () => {
    const w = recordWindow({ start: now, end: now + 24 * 60 * MIN }, now)!;
    expect(w.recEnd - w.recStart).toBe(REC_MAX_MS);
  });
});

describe("Planificateur d'enregistrements", () => {
  it("démarre ce qui est dû, marque manqués et interrompus", () => {
    const due = rec({ id: "a" });
    const later = rec({ id: "b", recStart: now + MIN });
    const missed = rec({ id: "c", recEnd: now });
    const running = rec({ id: "d", state: "recording" });
    const orphan = rec({ id: "e", state: "recording" });
    const done = rec({ id: "f", state: "done" });
    const r = dueRecordings([due, later, missed, running, orphan, done], now, new Set(["d"]));
    expect(r.start.map((x) => x.id)).toEqual(["a"]);
    expect(r.missed.map((x) => x.id)).toEqual(["c"]);
    expect(r.interrupted.map((x) => x.id)).toEqual(["e"]);
  });

  it("délai jusqu'à la prochaine programmation", () => {
    expect(nextRecordingDelay([rec({ recStart: now + 3 * MIN }), rec({ recStart: now + MIN, state: "done" })], now)).toBe(3 * MIN);
    expect(nextRecordingDelay([], now)).toBeNull();
  });

  it("connexions simultanées sur la même source", () => {
    const list = [rec({ id: "a" }), rec({ id: "b", sourceId: 2 }), rec({ id: "c", state: "done" }), rec({ id: "d", recStart: now + 200 * MIN, recEnd: now + 300 * MIN })];
    expect(concurrentOn(list, 1, { recStart: now, recEnd: now + 60 * MIN })).toBe(1);
    expect(concurrentOn(list, 1, { recStart: now, recEnd: now + 60 * MIN }, "a")).toBe(0);
  });

  it("limite de connexions : avertissements", () => {
    expect(connectionRisk(0, 0)).toBe("none");
    expect(connectionRisk(1, 0)).toBe("shared");
    expect(connectionRisk(1, 1)).toBe("full");
    expect(connectionRisk(3, 0)).toBe("none");
    expect(connectionRisk(2, 1)).toBe("shared");
  });

  it("regroupement de l'écran", () => {
    const g = groupRecordings([
      rec({ id: "s2", recStart: now + 9 }), rec({ id: "s1", recStart: now + 1 }), rec({ id: "on", state: "recording" }),
      rec({ id: "d1", state: "done", finishedAt: 1 }), rec({ id: "d2", state: "failed", finishedAt: 2 }), rec({ id: "x", state: "cancelled" }),
    ]);
    expect(g.recording.map((r) => r.id)).toEqual(["on"]);
    expect(g.scheduled.map((r) => r.id)).toEqual(["s1", "s2"]);
    expect(g.finished.map((r) => r.id)).toEqual(["d2", "d1"]);
  });
});

describe("Fichiers", () => {
  it("nom de fichier lisible et sûr", () => {
    const start = new Date(2026, 10, 12, 20, 45).getTime();
    expect(recBaseName("Stade 1", "Ligue : Nord / Sud?", start)).toBe("2026-11-12 20h45 - Stade 1 - Ligue Nord Sud");
    expect(recBaseName("", "", start)).toBe("2026-11-12 20h45");
    expect(recBaseName("C".repeat(200), "T", start).length).toBeLessThanOrEqual(120);
    expect(recBaseName("Chaîne..", "Fin. ", start).endsWith(".")).toBe(false);
  });

  it("taille lisible", () => {
    expect(fmtBytes(0, "en")).toBe("0 o");
    expect(fmtBytes(1536 * 1024 * 1024, "en")).toBe("1.5 Go");
  });
});
