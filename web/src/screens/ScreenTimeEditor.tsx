// Réglages › Profils : horaires de visionnage et temps d'écran quotidien par profil, et code parental du bureau.
// Le code parental (s'il existe) est exigé avant de modifier les règles.

import { useState } from "react";
import { useT, localeOf } from "@/i18n";
import {
  LIMIT_CHOICES, decode, dayName, emptyRules, encode, hhmm, isEmpty, parseHhmm, type Rules,
} from "@/lib/screenTime";
import { checkParentalCode, clearParentalCode, hasParentalCode, setParentalCode, validCode } from "@/lib/parental";
import { usePrefs, type Profile } from "@/state/prefs";
import { Modal } from "@/ui/common";

type Mode = "free" | "blocked" | "hours";

/** Saisie d'un code à 4 chiffres, validée par `onSubmit` (renvoie faux = code refusé). */
export function CodeModal({ title, label, onSubmit, onClose }: { title: string; label: string; onSubmit: (code: string) => Promise<boolean>; onClose: () => void }) {
  const t = useT();
  const [code, setCode] = useState("");
  const [bad, setBad] = useState(false);
  const go = async () => { if (!validCode(code)) { setBad(true); return; } if (await onSubmit(code)) return; setBad(true); setCode(""); };
  return (
    <Modal title={title} onClose={onClose} foot={
      <>
        <button className="btn" onClick={onClose}>{t("common.cancel")}</button>
        <button className="btn primary" disabled={code.length !== 4} onClick={() => void go()}>{t("common.save")}</button>
      </>}>
      <div className="field">
        <label htmlFor="pcode">{label}</label>
        <input id="pcode" className="input" type="password" inputMode="numeric" autoComplete="off" maxLength={4} value={code} aria-invalid={bad}
          onChange={(e) => { setBad(false); setCode(e.target.value.replace(/\D/g, "").slice(0, 4)); }}
          onKeyDown={(e) => { if (e.key === "Enter") void go(); }} />
        {bad && <div className="meta" role="alert">{t("st.codeWrong")}</div>}
      </div>
    </Modal>
  );
}

/** Ligne « Code parental » : définir, changer, retirer (le code actuel est demandé). */
export function ParentalCodeRow() {
  const t = useT();
  const [has, setHas] = useState(hasParentalCode());
  const [ask, setAsk] = useState<"current" | "new" | "remove" | null>(null);
  const [next, setNext] = useState(false);
  const close = () => { setAsk(null); setNext(false); };
  return (
    <div className="src-card">
      <span className="grow"><b>{t("st.code")}</b><div className="meta">{t(has ? "st.codeChange" : "st.codeSet")}</div></span>
      <button className="btn sm" onClick={() => setAsk(has ? "current" : "new")}>{t(has ? "st.codeChange" : "st.codeSet")}</button>
      {has && <button className="btn sm danger" onClick={() => setAsk("remove")}>{t("st.codeRemove")}</button>}
      {ask === "current" && !next && <CodeModal title={t("st.code")} label={t("st.codeCurrent")} onClose={close} onSubmit={async (c) => { if (!(await checkParentalCode(c))) return false; setNext(true); return true; }} />}
      {ask === "remove" && <CodeModal title={t("st.codeRemove")} label={t("st.codeCurrent")} onClose={close} onSubmit={async (c) => { if (!(await checkParentalCode(c))) return false; clearParentalCode(); setHas(false); close(); return true; }} />}
      {(ask === "new" || (ask === "current" && next)) && <CodeModal title={t(has ? "st.codeChange" : "st.codeSet")} label={t("st.codeNew")} onClose={close} onSubmit={async (c) => { if (!(await setParentalCode(c))) return false; setHas(true); close(); return true; }} />}
    </div>
  );
}

/** Bouton « Temps d'écran » d'un profil : code parental (s'il existe) puis éditeur. */
export function ScreenTimeButton({ profile }: { profile: Profile }) {
  const t = useT();
  const [step, setStep] = useState<"idle" | "code" | "edit">("idle");
  const rules = decode(profile.screenTime);
  const open = () => setStep(hasParentalCode() ? "code" : "edit");
  return (
    <>
      <button className="btn sm" onClick={open}>{t("st.title")}{!isEmpty(rules) ? ` · ${summary(rules, t)}` : ""}</button>
      {step === "code" && <CodeModal title={t("st.code")} label={t("st.codeEnter")} onClose={() => setStep("idle")} onSubmit={async (c) => { if (!(await checkParentalCode(c))) return false; setStep("edit"); return true; }} />}
      {step === "edit" && <Editor profile={profile} onClose={() => setStep("idle")} />}
    </>
  );
}

function summary(r: Rules, t: ReturnType<typeof useT>): string {
  return [r.limit !== null ? t("st.summaryLimit", { n: r.limit }) : "", Object.keys(r.days).length ? t("st.summaryHours") : ""].filter(Boolean).join(" · ");
}

const modeOf = (r: Rules, d: number): Mode => (r.days[d] === undefined ? "free" : r.days[d]!.length === 0 ? "blocked" : "hours");

function Editor({ profile, onClose }: { profile: Profile; onClose: () => void }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const set = usePrefs((s) => s.set);
  const [r, setR] = useState<Rules>(() => decode(profile.screenTime));
  const setDay = (d: number, w: Rules["days"][number] | undefined) => setR((x) => { const days = { ...x.days }; if (w === undefined) delete days[d]; else days[d] = w; return { ...x, days }; });
  const save = (rules: Rules) => {
    const enc = encode(rules);
    // Le profil est relu dans le magasin : le changement alimente le journal de synchro (diffLog) comme tout autre réglage.
    const profiles = usePrefs.getState().profiles.map((p) => { if (p.id !== profile.id) return p; const n = { ...p }; if (enc) n.screenTime = enc; else delete n.screenTime; return n; });
    set({ profiles });
    onClose();
  };
  return (
    <Modal wide title={`${t("st.title")} · ${profile.name || t("profile.main")}`} onClose={onClose} foot={
      <>
        {!isEmpty(r) && <button className="btn danger" onClick={() => save(emptyRules())}>{t("st.clear")}</button>}
        <button className="btn" onClick={onClose}>{t("common.cancel")}</button>
        <button className="btn primary" onClick={() => save(r)}>{t("common.save")}</button>
      </>}>
      <p className="lead">{t("st.sub")}</p>
      {!hasParentalCode() && <p className="meta">{t("st.needCode")}</p>}
      <div className="field">
        <label htmlFor="st-limit">{t("st.limit")}</label>
        <select id="st-limit" className="input" value={r.limit ?? 0} onChange={(e) => setR({ ...r, limit: Number(e.target.value) || null })}>
          <option value={0}>{t("st.limitNone")}</option>
          {LIMIT_CHOICES.map((m) => <option key={m} value={m}>{t("st.minutes", { n: m })}</option>)}
        </select>
      </div>
      {[1, 2, 3, 4, 5, 6, 7].map((d) => {
        const mode = modeOf(r, d);
        const w = r.days[d]?.[0];
        return (
          <div key={d} className="field" style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <label htmlFor={`st-d${d}`} style={{ minWidth: "7rem" }}>{dayName(d, localeOf(lang))}</label>
            <select id={`st-d${d}`} className="input" style={{ maxWidth: "14rem" }} value={mode}
              onChange={(e) => { const m = e.target.value as Mode; setDay(d, m === "free" ? undefined : m === "blocked" ? [] : [{ start: 480, end: 1200 }]); }}>
              <option value="free">{t("st.free")}</option>
              <option value="blocked">{t("st.blockedDay")}</option>
              <option value="hours">{t("st.pickHours")}</option>
            </select>
            {mode === "hours" && w && (
              <>
                <input className="input" style={{ maxWidth: "8rem" }} type="time" aria-label={t("st.start")} value={hhmm(w.start)}
                  onChange={(e) => { const v = parseHhmm(e.target.value); if (v !== null && v < w.end) setDay(d, [{ start: v, end: w.end }]); }} />
                <span aria-hidden>–</span>
                {/* « 24:00 » n'existe pas dans un champ d'heure : minuit de fin de journée s'écrit 00:00. */}
                <input className="input" style={{ maxWidth: "8rem" }} type="time" aria-label={t("st.end")} value={hhmm(w.end % 1440)}
                  onChange={(e) => { const v = parseHhmm(e.target.value); const end = v === 0 ? 1440 : v; if (end !== null && end > w.start) setDay(d, [{ start: w.start, end }]); }} />
              </>
            )}
          </div>
        );
      })}
    </Modal>
  );
}
