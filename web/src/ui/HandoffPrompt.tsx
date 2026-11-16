import { useEffect } from "react";
import { useT } from "@/i18n";
import { answerHandoff, HANDOFF_TTL_MS, useHandoff } from "@/cloud/handoff";
import { Icon } from "./Icon";

/**
 * « Reprendre sur cet appareil ? » : demande envoyée par un autre appareil du compte (« Envoyer vers… »). Rien ne démarre
 * seul : la lecture ne part que sur « Oui ». Une demande sans réponse disparaît d'elle-même après 2 minutes.
 */
export function HandoffPrompt() {
  const t = useT();
  const pending = useHandoff((s) => s.pending);
  useEffect(() => {
    if (!pending.length) return;
    const next = Math.min(...pending.map((p) => p.seenAt + HANDOFF_TTL_MS)) - Date.now();
    const id = setTimeout(() => useHandoff.setState((s) => ({ pending: s.pending.filter((p) => Date.now() - p.seenAt < HANDOFF_TTL_MS) })), Math.max(1000, next));
    return () => clearTimeout(id);
  }, [pending]);
  if (!pending.length) return null;
  const r = pending[0]!;
  return (
    <div className="rem-banners" role="alertdialog" aria-live="assertive" aria-label={t("handoff.title")}>
      <div className="rem-banner">
        <span className="ico"><Icon name="play" size={20} fill /></span>
        <div className="tx">
          <strong className="ellipsis">{t("handoff.title")}</strong>
          <span className="sub ellipsis">{t("handoff.body", { from: r.f, title: r.n })}</span>
        </div>
        <button className="btn primary sm" autoFocus onClick={() => void answerHandoff(r.i, true)}>{t("handoff.yes")}</button>
        <button className="btn sm" onClick={() => void answerHandoff(r.i, false)}>{t("handoff.no")}</button>
      </div>
    </div>
  );
}
