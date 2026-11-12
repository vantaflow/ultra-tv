// Actions d'un programme du guide (Guide, Sport) : « Me rappeler » (programme futur) et « Enregistrer » (bureau).
import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useState } from "react";
import { db } from "@/db/db";
import type { Source } from "@/db/types";
import { useT } from "@/i18n";
import { canRecord, canRemind, progId, type ProgRef } from "@/lib/schedule";
import { bridge } from "@/net/transport";
import { recordPlan, recordingAvailable, scheduleRecording, toggleReminder } from "@/state/schedule";
import { usePrefs } from "@/state/prefs";
import { useUi } from "@/state/ui";
import { Modal, hhmm } from "./common";
import { Icon } from "./Icon";

/** Identifiants des rappels posés. */
export function useReminderIds(): Set<string> {
  const ids = useLiveQuery(() => db.reminders.toCollection().primaryKeys(), []);
  return new Set(ids ?? []);
}

/** Identifiants des enregistrements programmés ou en cours. */
export function useRecordingIds(): Set<string> {
  const ids = useLiveQuery(() => db.recordings.filter((r) => r.state === "scheduled" || r.state === "recording").primaryKeys(), []);
  return new Set(ids ?? []);
}

/** Dossier d'enregistrement (processus principal). */
export function useRecDir(): [string | null, () => Promise<void>] {
  const [dir, setDir] = useState<string | null>(null);
  useEffect(() => { void bridge()?.recDir?.().then(setDir).catch(() => {}); }, []);
  const choose = async () => { const d = await bridge()?.recChooseDir?.().catch(() => null); if (d) setDir(d); };
  return [dir, choose];
}

/** Confirmation d'un enregistrement : créneau (marges comprises), dossier, avertissements. */
export function RecordDialog({ source, prog, onClose }: { source: Source; prog: ProgRef; onClose: () => void }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const [dir, chooseDir] = useRecDir();
  const plan = useLiveQuery(() => recordPlan(source, prog), [source.id, source.maxConnections, prog.sourceId, prog.streamId, prog.start]);
  const [busy, setBusy] = useState(false);
  const confirm = async () => {
    setBusy(true);
    const r = await scheduleRecording(prog);
    setBusy(false);
    if (!r.ok) useUi.getState().toast(t(r.reason === "exists" ? "rec.already" : r.reason === "ended" ? "rec.ended" : "rec.desktopOnly"));
    onClose();
  };
  return (
    <Modal title={t("rec.confirmTitle", { title: prog.title })} onClose={onClose} foot={
      <>
        <button className="btn" onClick={onClose}>{t("common.cancel")}</button>
        <button className="btn primary" disabled={busy || !plan || plan.exists} onClick={() => void confirm()}>
          <Icon name="rec" size={16} fill />{t(plan?.immediate ? "rec.now" : "rec.schedule")}
        </button>
      </>}>
      {plan === null ? <div className="alert err" role="alert"><Icon name="alert" size={20} />{t("rec.ended")}</div> : plan && (
        <>
          <p className="rec-when">{t("rec.window", { channel: prog.channel, from: hhmm(plan.recStart, lang), to: hhmm(plan.recEnd, lang) })}</p>
          {plan.exists && <div className="alert" role="status"><Icon name="info" size={20} />{t("rec.already")}</div>}
          {plan.risk !== "none" && <div className="alert err" role="alert"><Icon name="alert" size={20} />{t(plan.risk === "full" ? "rec.warnFull" : "rec.warnShared")}</div>}
        </>
      )}
      <div className="pref">
        <div><div>{t("rec.folder")}</div><div className="d rec-dir">{dir ?? "…"}</div></div>
        {bridge()?.recChooseDir && <button className="btn sm" onClick={() => void chooseDir()}><Icon name="folder" size={16} />{t("rec.change")}</button>}
      </div>
      <p className="muted rec-note">{t("rec.keepOpen")} {t("rec.format")}</p>
    </Modal>
  );
}

/**
 * Boutons d'un programme : rappel (programme futur) et enregistrement (application de bureau, programme pas encore fini).
 * `size` : taille des boutons (« lg » sur l'en-tête du guide, « sm » dans les listes).
 */
export function ProgActions({ source, prog, now, size = "sm", reminded, recorded }: { source: Source; prog: ProgRef; now: number; size?: "sm" | "lg"; reminded: boolean; recorded: boolean }) {
  const t = useT();
  const [dialog, setDialog] = useState(false);
  const showRemind = canRemind(prog, now);
  const showRecord = recordingAvailable() && canRecord(prog, now);
  if (!showRemind && !showRecord) return null;
  return (
    <>
      {showRemind && (
        <button className={`btn ${size}${reminded ? " on" : ""}`} aria-pressed={reminded} onClick={(e) => { e.stopPropagation(); void toggleReminder(prog); }}>
          <Icon name={reminded ? "check" : "bell"} size={size === "lg" ? 18 : 15} />{t(reminded ? "rem.on" : "rem.remind")}
        </button>
      )}
      {showRecord && (
        <button className={`btn ${size}${recorded ? " on" : ""}`} disabled={recorded} onClick={(e) => { e.stopPropagation(); setDialog(true); }}>
          <Icon name="rec" size={size === "lg" ? 16 : 13} fill />{t(recorded ? "rec.scheduled" : "rec.record")}
        </button>
      )}
      {dialog && <RecordDialog source={source} prog={prog} onClose={() => setDialog(false)} />}
    </>
  );
}

export { progId };
