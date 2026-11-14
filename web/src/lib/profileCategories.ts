// Catégories PAR PROFIL (même modèle qu'Android) : chaque profil a sa liste de catégories masquées ; le téléchargement
// (`enabled`, global à la source) suit : une catégorie est téléchargée tant qu'au moins un profil l'affiche. Logique pure, sans E/S.

import type { Kind } from "@/db/types";

export type HiddenMap = Map<string, Set<string>>; // profil -> extId masqués (pour un type donné)

/** Affichée pour le profil = téléchargée ET non masquée par ce profil. */
export const isShown = (c: { enabled: 0 | 1; extId: string }, hidden: ReadonlySet<string>): boolean => !!c.enabled && !hidden.has(c.extId);

export interface VisibilityPlan {
  /** Pour chaque profil : identifiants à masquer / à ne plus masquer. */
  hide: Map<string, string[]>;
  unhide: Map<string, string[]>;
  /** Catégories à télécharger / à ne plus télécharger (champ `enabled`). */
  enable: string[];
  disable: string[];
}

/**
 * Afficher / masquer [ids] pour le profil [me].
 * - Afficher une catégorie non téléchargée : on la télécharge ET on la masque pour tous les AUTRES profils (ils ne la voient pas apparaître d'un coup).
 * - Masquer une catégorie téléchargée que tous les autres profils masquent déjà : elle n'est plus téléchargée.
 * [enabledNow] : catégories téléchargées ; [hidden] : masquées par profil ; [profiles] : tous les profils locaux.
 */
export function planVisibility(me: string, ids: string[], visible: boolean, enabledNow: ReadonlySet<string>, hidden: HiddenMap, profiles: string[]): VisibilityPlan {
  const others = profiles.filter((p) => p !== me);
  const plan: VisibilityPlan = { hide: new Map(), unhide: new Map(), enable: [], disable: [] };
  const push = (m: Map<string, string[]>, p: string, id: string) => { const l = m.get(p); if (l) l.push(id); else m.set(p, [id]); };
  for (const id of new Set(ids)) {
    if (visible) {
      push(plan.unhide, me, id);
      if (!enabledNow.has(id)) {
        plan.enable.push(id);
        for (const o of others) push(plan.hide, o, id);
      }
    } else {
      push(plan.hide, me, id);
      if (enabledNow.has(id) && others.every((o) => hidden.get(o)?.has(id))) plan.disable.push(id);
    }
  }
  return plan;
}

// ---------------- Partage (champ `pHidden` des réglages de la source) ----------------

export interface PHiddenWire { p: string; k: Kind; ids: string[] }
export interface PHiddenLocal { profile: string; kind: Kind; ids: string[] }

const KIND_SET = new Set<string>(["live", "movie", "series"]);
const low = (n: string) => n.trim().toLowerCase();

/** Catégories masquées locales (profil = NOM partagé) -> format partagé. Une entrée par profil et par type ayant des masquées ; [keep] : entrées étrangères renvoyées telles quelles. */
export function pHiddenToWire(rows: PHiddenLocal[], keep: PHiddenWire[] = [], maxEntries = 400, maxIds = 5000, maxName = 24): PHiddenWire[] {
  const m = new Map<string, PHiddenWire>();
  for (const r of rows) {
    const p = r.profile.trim().slice(0, maxName);
    const ids = [...new Set(r.ids)].slice(0, maxIds);
    if (!p || !ids.length || !KIND_SET.has(r.kind)) continue;
    const key = `${low(p)}|${r.kind}`;
    const o = m.get(key);
    m.set(key, o ? { ...o, ids: [...new Set([...o.ids, ...ids])].slice(0, maxIds) } : { p, k: r.kind, ids });
  }
  for (const k of keep) { const key = `${low(k.p)}|${k.k}`; if (!m.has(key)) m.set(key, k); }
  return [...m.values()].slice(0, maxEntries);
}

/**
 * Entrées reçues -> catégories masquées de chaque profil LOCAL (rattaché par nom, sans casse). Un profil sans entrée reçue n'a
 * aucune catégorie masquée. Entrées pour un profil inconnu ici : [foreign], à renvoyer telles quelles.
 */
export function planPHidden(received: PHiddenWire[], profiles: { id: string; name: string }[]): { byProfile: Map<string, Record<Kind, string[]>>; foreign: PHiddenWire[] } {
  const byProfile = new Map<string, Record<Kind, string[]>>();
  for (const p of profiles) byProfile.set(p.id, { live: [], movie: [], series: [] });
  const foreign: PHiddenWire[] = [];
  for (const e of received) {
    const mine = profiles.filter((p) => low(p.name) === low(e.p));
    if (!mine.length) { foreign.push(e); continue; }
    if (!KIND_SET.has(e.k)) continue;
    for (const p of mine) { const r = byProfile.get(p.id)!; r[e.k] = [...new Set([...r[e.k], ...e.ids])]; }
  }
  return { byProfile, foreign };
}
