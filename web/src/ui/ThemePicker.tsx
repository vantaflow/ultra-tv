// Thème et couleur d'accent : en tête de Réglages › Affichage et dans l'assistant de première source (faciles à trouver).

import { useT, type Key } from "@/i18n";
import { ACCENT_DEFS } from "@/lib/themes";
import { usePrefs, type Theme } from "@/state/prefs";
import { Seg } from "./common";

export function ThemePicker() {
  const t = useT();
  const p = usePrefs();
  return (
    <>
      <div className="pref">
        <div><div>{t("set.theme")}</div></div>
        <Seg<Theme> label={t("set.theme")} value={p.theme} onChange={(theme) => p.set({ theme })} options={[{ v: "auto", label: t("set.themeAuto") }, { v: "dark", label: t("set.themeDark") }, { v: "light", label: t("set.themeLight") }, { v: "oled", label: t("set.themeOled") }]} />
      </div>
      <div className="pref">
        <div><div>{t("set.accent")}</div></div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(6, 1.75rem)", gap: 10 }} role="radiogroup" aria-label={t("set.accent")}>
          {ACCENT_DEFS.map((a) => { const name = t(`set.accent.${a.id}` as Key); return <button key={a.id} className="swatch" role="radio" aria-checked={p.accent === a.id} aria-label={name} title={name} style={{ background: a.hex }} onClick={() => p.set({ accent: a.id })} />; })}
        </div>
      </div>
    </>
  );
}
