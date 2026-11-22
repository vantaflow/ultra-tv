// Groupes de chaînes personnalisés : menu contextuel d'une chaîne (ajouter / retirer d'un groupe, déplacer) et fenêtre du nom.

import { useEffect, useRef, useState } from "react";
import { addGroup, addToChannelGroup, removeFromChannelGroup } from "@/db/channelGroups";
import type { ChannelRow } from "@/db/types";
import { useT } from "@/i18n";
import { MAX_GROUP_NAME, cleanGroupName, type ChannelGroup } from "@/lib/channelGroups";
import { useUi } from "@/state/ui";
import { Modal } from "./common";
import { Icon } from "./Icon";

/** Saisie du nom d'un groupe (création ou renommage). */
export function GroupNameModal({ title, initial = "", onSubmit, onClose }: { title: string; initial?: string; onSubmit: (name: string) => void | Promise<void>; onClose: () => void }) {
  const t = useT();
  const [name, setName] = useState(initial);
  const ok = !!cleanGroupName(name);
  const go = async () => { if (!ok) return; await onSubmit(cleanGroupName(name)); onClose(); };
  return (
    <Modal title={title} onClose={onClose} foot={<><button className="btn" onClick={onClose}>{t("common.cancel")}</button><button className="btn primary" disabled={!ok} onClick={() => void go()}>{t("common.save")}</button></>}>
      <div className="field">
        <label htmlFor="grp-name">{t("grp.name")}</label>
        <input id="grp-name" className="input" value={name} maxLength={MAX_GROUP_NAME} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") void go(); }} />
      </div>
    </Modal>
  );
}

export interface MenuAt { x: number; y: number; c: ChannelRow }

/**
 * Menu d'une chaîne du Direct (clic droit, ou bouton « Groupes ») : une case par groupe (cochée = la chaîne y est),
 * « Nouveau groupe… » (créé avec cette chaîne), « Déplacer » quand la liste peut être réordonnée.
 */
export function ChannelMenu({ at, profile, sourceId, groups, onMove, onClose }: { at: MenuAt; profile: string; sourceId: number; groups: ChannelGroup[]; onMove?: () => void; onClose: () => void }) {
  const t = useT();
  const ref = useRef<HTMLDivElement>(null);
  const [naming, setNaming] = useState(false);
  const toast = useUi.getState().toast;
  const sid = at.c.streamId;
  useEffect(() => {
    ref.current?.querySelector<HTMLElement>("button")?.focus();
    const k = (e: KeyboardEvent) => { if (e.key === "Escape") { e.stopPropagation(); onClose(); } };
    window.addEventListener("keydown", k, true);
    return () => window.removeEventListener("keydown", k, true);
  }, [onClose]);
  // Reste dans la fenêtre (bord droit / bas), y compris en arabe.
  const style = { left: Math.max(8, Math.min(at.x, window.innerWidth - 280)), top: Math.max(8, Math.min(at.y, window.innerHeight - 60 - 40 * (groups.length + 3))) };
  const toggle = async (g: ChannelGroup) => {
    if (g.ids.includes(sid)) { await removeFromChannelGroup(profile, sourceId, g.id, [sid]); toast(t("grp.removed", { name: g.name })); }
    else { await addToChannelGroup(profile, sourceId, g.id, [sid]); toast(t("grp.added", { name: g.name })); }
    onClose();
  };
  if (naming) return <GroupNameModal title={t("grp.new")} onClose={onClose} onSubmit={async (name) => { if (await addGroup(profile, sourceId, name, [sid])) toast(t("grp.added", { name })); }} />;
  return (
    <div className="scrim clear" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }} onContextMenu={(e) => { e.preventDefault(); onClose(); }}>
      <div ref={ref} className="ctx-menu" role="menu" aria-label={t("grp.menu")} style={style}>
        <div className="ctx-title ellipsis">{at.c.display}</div>
        {onMove && <button role="menuitem" onClick={() => { onClose(); onMove(); }}><Icon name="list" size={16} />{t("cor.move")}</button>}
        <div className="ctx-sep" role="presentation">{t("grp.addTo")}</div>
        {groups.map((g) => (
          <button key={g.id} role="menuitemcheckbox" aria-checked={g.ids.includes(sid)} onClick={() => void toggle(g)}>
            <span className="ctx-check" aria-hidden="true">{g.ids.includes(sid) && <Icon name="check" size={14} />}</span>
            <span className="ellipsis">{g.name}</span>
          </button>
        ))}
        <button role="menuitem" onClick={() => setNaming(true)}><Icon name="plus" size={16} />{t("grp.newEllipsis")}</button>
      </div>
    </div>
  );
}
