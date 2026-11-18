import { useState } from "react";
import { addManual, deleteManual, isValidLogoUrl, isValidStreamUrl, MANUAL_CAT, updateManual } from "@/db/manual";
import type { CategoryRow, ManualChannelRow } from "@/db/types";
import { useT } from "@/i18n";
import { Modal } from "@/ui/common";

/**
 * Ajout / modification d'une chaîne manuelle de la source [sourceId]. [existing] absent = ajout. [categories] : catégories
 * du Direct de la source ; « Mes chaînes » est toujours proposée en dernier choix. Les adresses ne sont jamais journalisées.
 */
export function ManualChannelModal({ sourceId, categories, existing, defaultCat, onClose }: {
  sourceId: number; categories: CategoryRow[]; existing?: ManualChannelRow; defaultCat?: string; onClose: () => void;
}) {
  const t = useT();
  const choices = [...categories.filter((c) => c.extId !== MANUAL_CAT).map((c) => ({ ext: c.extId, label: c.label || c.name })), { ext: MANUAL_CAT, label: t("mc.catCustom") }];
  const first = existing?.catExt ?? defaultCat;
  const [name, setName] = useState(existing?.name ?? "");
  const [url, setUrl] = useState(existing?.url ?? "");
  const [logo, setLogo] = useState(existing?.logo ?? "");
  const [epg, setEpg] = useState(existing?.epg ?? "");
  const [cat, setCat] = useState(choices.some((c) => c.ext === first) ? first! : MANUAL_CAT);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [busy, setBusy] = useState(false);
  const urlOk = url.trim() === "" || isValidStreamUrl(url);
  const logoOk = isValidLogoUrl(logo);
  const canSave = name.trim() !== "" && isValidStreamUrl(url) && logoOk && !busy;
  const save = async () => {
    if (!canSave) return;
    setBusy(true);
    const v = { name, url, logo, epg, catExt: cat, catName: t("mc.catCustom") };
    try { if (existing) await updateManual(sourceId, existing.ref, v); else await addManual(sourceId, v); onClose(); } finally { setBusy(false); }
  };
  const remove = async () => {
    if (!existing) return;
    if (!confirmDelete) { setConfirmDelete(true); return; }
    setBusy(true);
    try { await deleteManual(sourceId, existing.ref); onClose(); } finally { setBusy(false); }
  };
  return (
    <Modal
      title={existing ? t("mc.editTitle") : t("mc.add")} onClose={onClose}
      foot={<>
        {existing && <button className="btn" disabled={busy} onClick={() => void remove()}>{confirmDelete ? t("mc.deleteSure") : t("common.delete")}</button>}
        <button className="btn" onClick={onClose}>{t("common.cancel")}</button>
        <button className="btn primary" disabled={!canSave} onClick={() => void save()}>{t("common.save")}</button>
      </>}
    >
      <p className="muted">{t("mc.sub")}</p>
      <form onSubmit={(e) => { e.preventDefault(); void save(); }}>
        <div className="field full">
          <label htmlFor="mc-name">{t("mc.name")}</label>
          <input id="mc-name" className="input" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="field full">
          <label htmlFor="mc-url">{t("mc.url")}</label>
          <input id="mc-url" className="input" value={url} placeholder="http://hote.tld/flux.m3u8" autoCapitalize="off" autoComplete="off" spellCheck={false} onChange={(e) => setUrl(e.target.value)} aria-invalid={!urlOk} />
          {!urlOk && <span className="muted" role="alert">{t("mc.badUrl")}</span>}
        </div>
        <div className="field full">
          <label htmlFor="mc-logo">{t("mc.logo")}</label>
          <input id="mc-logo" className="input" value={logo} placeholder="https://hote.tld/logo.png" autoCapitalize="off" spellCheck={false} onChange={(e) => setLogo(e.target.value)} aria-invalid={!logoOk} />
          {!logoOk && <span className="muted" role="alert">{t("mc.badUrl")}</span>}
        </div>
        <div className="field full">
          <label htmlFor="mc-epg">{t("mc.epg")}</label>
          <input id="mc-epg" className="input" value={epg} autoCapitalize="off" spellCheck={false} onChange={(e) => setEpg(e.target.value)} />
        </div>
        <div className="field full">
          <label htmlFor="mc-cat">{t("mc.category")}</label>
          <select id="mc-cat" className="input" value={cat} onChange={(e) => setCat(e.target.value)}>
            {choices.map((c) => <option key={c.ext} value={c.ext}>{c.label}</option>)}
          </select>
        </div>
        <input type="submit" hidden />
      </form>
    </Modal>
  );
}
