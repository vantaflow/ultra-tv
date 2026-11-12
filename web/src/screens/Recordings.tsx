// Écran « Enregistrements » : en cours (arrêter), programmés (annuler), terminés (lire, dossier, supprimer), et la liste
// des rappels (retirer). L'application doit rester ouverte pendant un enregistrement : c'est dit en tête d'écran.
import { useLiveQuery } from "dexie-react-hooks";
import { useState } from "react";
import { db } from "@/db/db";
import { useNow } from "@/hooks/misc";
import { useT, type Key } from "@/i18n";
import { fmtBytes, groupRecordings, type Recording, type Reminder } from "@/lib/schedule";
import { bridge } from "@/net/transport";
import { usePrefs } from "@/state/prefs";
import { cancelRecording, deleteRecording, openProgChannel, recordingAvailable, removeReminder, useSchedule } from "@/state/schedule";
import { Icon } from "@/ui/Icon";
import { useRecDir } from "@/ui/ProgActions";
import { hhmm } from "@/ui/common";

const ERR: Record<string, Key> = {
  missed: "rec.err.missed", interrupted: "rec.err.interrupted", limit: "rec.err.limit", disk: "rec.err.disk", stream: "rec.err.stream", partial: "rec.partial",
};

function useDay() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  return (ms: number, now: number) => {
    const a = new Date(ms); a.setHours(0, 0, 0, 0);
    const b = new Date(now); b.setHours(0, 0, 0, 0);
    const d = Math.round((a.getTime() - b.getTime()) / 86400_000);
    return d === 0 ? t("guide.today") : d === 1 ? t("guide.tomorrow") : new Date(ms).toLocaleDateString(lang, { weekday: "long", day: "numeric", month: "long" });
  };
}

function RecRow({ r, now }: { r: Recording; now: number }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const day = useDay();
  const bytes = useSchedule((s) => s.progress[r.id]) ?? r.bytes ?? 0;
  const b = bridge();
  const ratio = r.state === "recording" ? Math.min(1, Math.max(0, (now - r.recStart) / Math.max(1, r.recEnd - r.recStart))) : 0;
  let meta: string;
  if (r.state === "recording") meta = `${t("rec.until", { time: hhmm(r.recEnd, lang) })} · ${t("rec.size", { size: fmtBytes(bytes, lang) })}`;
  else if (r.state === "scheduled") meta = t("rec.startsAt", { day: day(r.recStart, now), time: hhmm(r.recStart, lang) });
  else meta = [day(r.start, now), `${hhmm(r.start, lang)} – ${hhmm(r.end, lang)}`, bytes ? fmtBytes(bytes, lang) : null, r.error ? t(ERR[r.error] ?? "rec.err.generic") : null].filter(Boolean).join(" · ");
  return (
    <div className={`rec-row ${r.state}`} role="listitem">
      <span className="rec-ico" aria-hidden="true"><Icon name={r.state === "failed" ? "alert" : r.state === "done" ? "check" : "rec"} size={18} fill={r.state === "recording"} /></span>
      <span className="rec-tx">
        <b className="ellipsis">{r.title}</b>
        <span className="ellipsis">{r.channel} · {meta}</span>
        {r.state === "recording" && <span className="lc-trk"><i style={{ width: `${ratio * 100}%` }} /></span>}
      </span>
      <span className="rec-acts">
        {r.state === "recording" && <button className="btn sm" onClick={() => void cancelRecording(r)}><Icon name="pause" size={14} fill />{t("rec.stop")}</button>}
        {r.state === "scheduled" && <button className="btn sm" onClick={() => void cancelRecording(r)}><Icon name="x" size={14} />{t("common.cancel")}</button>}
        {(r.state === "done" || r.state === "failed") && r.path && b?.recOpen && (
          <button className="btn sm primary" title={t("rec.openHint")} onClick={() => void b.recOpen!(r.path!)}><Icon name="play" size={14} fill />{t("rec.open")}</button>
        )}
        {(r.state === "done" || r.state === "failed") && r.path && b?.recReveal && (
          <button className="btn sm" onClick={() => void b.recReveal!(r.path!)}><Icon name="folder" size={14} />{t("rec.reveal")}</button>
        )}
        {(r.state === "done" || r.state === "failed") && (
          <button className="btn sm danger icon" aria-label={t("common.delete")} title={t("common.delete")}
            onClick={() => { if (confirm(t("rec.deleteConfirm", { title: r.title }))) void deleteRecording(r); }}><Icon name="trash" size={16} /></button>
        )}
      </span>
    </div>
  );
}

function ReminderRow({ r, now }: { r: Reminder; now: number }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const day = useDay();
  const live = r.start <= now && r.end > now;
  return (
    <div className="rec-row" role="listitem">
      <span className="rec-ico" aria-hidden="true"><Icon name="bell" size={18} /></span>
      <span className="rec-tx">
        <b className="ellipsis">{r.title}</b>
        <span className="ellipsis">{r.channel} · {t("rem.when", { day: day(r.start, now), time: hhmm(r.start, lang) })}</span>
      </span>
      <span className="rec-acts">
        {live && <button className="btn sm primary" onClick={() => void openProgChannel(r)}><Icon name="play" size={14} fill />{t("common.watch")}</button>}
        <button className="btn sm" onClick={() => void removeReminder(r.id)}><Icon name="x" size={14} />{t("rem.cancel")}</button>
      </span>
    </div>
  );
}

export function Recordings() {
  const t = useT();
  const now = useNow(15_000);
  const recs = useLiveQuery(() => db.recordings.toArray(), []) ?? [];
  const rems = (useLiveQuery(() => db.reminders.orderBy("start").toArray(), []) ?? []).filter((r) => r.end > now);
  const [tab, setTab] = useState<"rec" | "rem">("rec");
  const [dir, chooseDir] = useRecDir();
  const g = groupRecordings(recs);
  const canRec = recordingAvailable();
  const b = bridge();
  return (
    <div className="page recordings">
      <header className="page-head">
        <div>
          <h1>{t("nav.recordings")}</h1>
          <div className="sub">{canRec ? `${t("rec.keepOpen")} ${t("rec.format")}` : t("rec.desktopOnly")}</div>
        </div>
      </header>
      <div className="chips" role="tablist">
        <button className="chip" role="tab" aria-selected={tab === "rec"} onClick={() => setTab("rec")}>{t("nav.recordings")}<span className="n">{g.recording.length + g.scheduled.length + g.finished.length}</span></button>
        <button className="chip" role="tab" aria-selected={tab === "rem"} onClick={() => setTab("rem")}>{t("rem.title")}<span className="n">{rems.length}</span></button>
      </div>

      {tab === "rec" ? (
        <>
          {canRec && (
            <div className="pref rec-folder">
              <div><div>{t("rec.folder")}</div><div className="d rec-dir">{dir ?? "…"}</div></div>
              <span className="rec-acts">
                {b?.recOpenDir && <button className="btn sm" onClick={() => void b.recOpenDir!()}><Icon name="folder" size={16} />{t("rec.openDir")}</button>}
                {b?.recChooseDir && <button className="btn sm" onClick={() => void chooseDir()}>{t("rec.change")}</button>}
              </span>
            </div>
          )}
          {g.recording.length + g.scheduled.length + g.finished.length === 0 && <p className="sp-empty">{t("rec.empty")}</p>}
          {([["recording", "rec.inProgress"], ["scheduled", "rec.scheduledH"], ["finished", "rec.finished"]] as const).map(([k, label]) => g[k].length > 0 && (
            <section key={k} aria-label={t(label)}>
              <h2 className="section-title">{t(label)}</h2>
              <div className="rec-list" role="list">{g[k].map((r) => <RecRow key={r.id} r={r} now={now} />)}</div>
            </section>
          ))}
        </>
      ) : (
        <section aria-label={t("rem.title")}>
          <p className="muted">{t("rem.note")}</p>
          {rems.length === 0 ? <p className="sp-empty">{t("rem.empty")}</p> : (
            <div className="rec-list" role="list">{rems.map((r) => <ReminderRow key={r.id} r={r} now={now} />)}</div>
          )}
        </section>
      )}
    </div>
  );
}
