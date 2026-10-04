import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useT } from "@/i18n";
import { PROFILE_COLORS, usePrefs } from "@/state/prefs";
import { Modal } from "@/ui/common";
import { AppMark } from "@/ui/Icon";

export function Profiles() {
  const t = useT();
  const nav = useNavigate();
  const { profiles, set } = usePrefs();
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  return (
    <div className="stage">
      <header><div className="brand"><AppMark size={40} className="ic" /><span>ULTRA <span>TV</span></span></div><span /></header>
      <main style={{ alignItems: "center", textAlign: "center" }}>
        <h1>{t("profile.who")}</h1>
        <div className="profiles">
          {profiles.map((p, i) => (
            <button key={p.id} className="profile" onClick={() => { set({ profileId: p.id }); nav("/", { replace: true }); }}>
              <span className="av" style={{ background: p.color }}>{(p.name || t("profile.main")).slice(0, 1).toUpperCase()}</span>
              <b>{p.name || t("profile.main")}</b>
              <span className="muted" style={{ fontSize: "0.8125rem" }}>{i === 0 ? t("profile.main") : ""}</span>
            </button>
          ))}
          <button className="profile" onClick={() => setAdding(true)}>
            <span className="av" style={{ background: "var(--surface-3)", color: "var(--text-2)" }}>+</span>
            <b>{t("profile.add")}</b>
          </button>
        </div>
        <button className="btn" onClick={() => nav("/settings/profiles")}>{t("profile.manage")}</button>
      </main>
      {adding && (
        <Modal title={t("set.addProfile")} onClose={() => setAdding(false)} foot={
          <>
            <button className="btn" onClick={() => setAdding(false)}>{t("common.cancel")}</button>
            <button className="btn primary" disabled={!name.trim()} onClick={() => {
              const id = `p${Date.now()}`;
              set({ profiles: [...profiles, { id, name: name.trim(), color: PROFILE_COLORS[profiles.length % PROFILE_COLORS.length]! }], profileId: id });
              nav("/", { replace: true });
            }}>{t("common.save")}</button>
          </>}>
          <div className="field"><label htmlFor="pn">{t("set.profileName")}</label><input id="pn" className="input" value={name} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter" && name.trim()) (e.currentTarget.closest(".modal")?.querySelector(".btn.primary") as HTMLElement | null)?.click(); }} /></div>
        </Modal>
      )}
    </div>
  );
}
