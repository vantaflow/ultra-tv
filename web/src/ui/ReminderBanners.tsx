import { useT } from "@/i18n";
import { openProgChannel, useSchedule } from "@/state/schedule";
import { usePrefs } from "@/state/prefs";
import { hhmm } from "./common";
import { Icon } from "./Icon";

/** Rappels notifiés dans l'appli quand aucune notification système n'est possible (navigateur sans permission). */
export function ReminderBanners() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const banners = useSchedule((s) => s.banners);
  const dismiss = useSchedule((s) => s.dismiss);
  if (!banners.length) return null;
  return (
    <div className="rem-banners" role="status" aria-live="polite">
      {banners.map((r) => (
        <div key={r.id} className="rem-banner">
          <span className="ico"><Icon name="bell" size={20} /></span>
          <div className="tx">
            <strong className="ellipsis">{t("rem.notifTitle", { title: r.title })}</strong>
            <span className="sub ellipsis">{t("rem.notifBody", { time: hhmm(r.start, lang), channel: r.channel })}</span>
          </div>
          <button className="btn primary sm" onClick={() => { dismiss(r.id); void openProgChannel(r); }}><Icon name="play" size={14} fill />{t("common.watch")}</button>
          <button className="btn sm icon" aria-label={t("common.close")} onClick={() => dismiss(r.id)}><Icon name="x" size={16} /></button>
        </div>
      ))}
    </div>
  );
}
