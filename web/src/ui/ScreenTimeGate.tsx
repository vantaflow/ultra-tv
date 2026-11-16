// Écran de blocage (hors plage horaire ou limite quotidienne atteinte) par-dessus toute l'application, et avertissement
// 5 minutes avant la fin. Le blocage vaut pour le profil courant : « Changer de profil » mène à « Qui regarde ? ».

import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { localeOf, useT } from "@/i18n";
import { dayName, hhmm, zoneFor } from "@/lib/screenTime";
import { checkParentalCode, hasParentalCode } from "@/lib/parental";
import { usePlayer } from "@/player/store";
import { usePrefs } from "@/state/prefs";
import { useScreenTime } from "@/state/screenTime";
import { CodeModal } from "@/screens/ScreenTimeEditor";
import { Modal } from "./common";

export function ScreenTimeGate() {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const loc = useLocation();
  const nav = useNavigate();
  const v = useScreenTime((s) => s.verdict);
  const grantExtra = useScreenTime((s) => s.grantExtra);
  const grantToday = useScreenTime((s) => s.grantToday);
  const [stage, setStage] = useState<"idle" | "code" | "choose">("idle");
  const [warnShown, setWarnShown] = useState(true);

  // Bloqué : la lecture s'arrête (le lecteur plein écran ou intégré est fermé).
  useEffect(() => { if (v.kind === "blocked") usePlayer.getState().close(); }, [v.kind]);
  useEffect(() => { if (v.kind !== "blocked") setStage("idle"); }, [v.kind]);
  // Avertissement : une fois par épisode, masqué après 8 s, réarmé quand on sort de la zone des 5 minutes.
  const warn = v.kind === "allowed" && v.warn;
  useEffect(() => {
    if (!warn) { setWarnShown(true); return; }
    setWarnShown(true);
    const id = setTimeout(() => setWarnShown(false), 8000);
    return () => clearTimeout(id);
  }, [warn]);

  const onPicker = loc.pathname.startsWith("/profiles") || loc.pathname.startsWith("/welcome");
  if (v.kind === "allowed" && v.warn && warnShown) {
    return <div className="st-warn" role="status">{t("st.warn", { n: Math.max(1, Math.ceil(v.left / 60)) })}</div>;
  }
  if (v.kind !== "blocked" || onPicker) return null;

  const z = zoneFor();
  const when = v.next === null ? null : (() => { const p = z.parts(v.next); return `${dayName(p.dow, localeOf(lang))} ${hhmm(p.minute)}`; })();
  return (
    <div className="st-block" role="alertdialog" aria-modal="true" aria-label={t(v.reason === "outside" ? "st.blockOutside" : "st.blockLimit")}>
      <h1>{t(v.reason === "outside" ? "st.blockOutside" : "st.blockLimit")}</h1>
      <p className="muted">{when ? t("st.blockNext", { when }) : t("st.blockNone")}</p>
      <div className="actions">
        {/* Sans code parental il n'y a pas de dérogation possible : seul le changement de profil reste proposé. */}
        {hasParentalCode() && <button className="btn primary" onClick={() => setStage("code")}>{t("st.unlock")}</button>}
        <button className="btn" onClick={() => nav("/profiles")}>{t("rail.switchProfile")}</button>
      </div>
      {stage === "code" && <CodeModal title={t("st.code")} label={t("st.codeEnter")} onClose={() => setStage("idle")} onSubmit={async (c) => { if (!(await checkParentalCode(c))) return false; setStage("choose"); return true; }} />}
      {stage === "choose" && (
        <Modal title={t("st.unlock")} onClose={() => setStage("idle")} foot={<button className="btn" onClick={() => setStage("idle")}>{t("common.cancel")}</button>}>
          <button className="btn primary" onClick={() => { grantExtra(); setStage("idle"); }}>{t("st.unlockExtra")}</button>
          <button className="btn" onClick={() => { grantToday(); setStage("idle"); }}>{t("st.unlockToday")}</button>
        </Modal>
      )}
    </div>
  );
}
