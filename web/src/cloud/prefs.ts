// Réglages d'affichage partagés (langues + catégories désactivées) : conversions pures, règle « le plus récent gagne »,
// et regroupement des envois (anti-rebond). Aucune E/S ici.

import { OTHER_LANG, canonicalLang, protocolLang } from "@/lib/categoryLang";
import type { CategoryRow, Kind } from "@/db/types";
import type { CloudPrefs } from "./client";

export const MAX_IDS = 5000;
export const MAX_ID_LEN = 128;
export const KINDS: Kind[] = ["live", "movie", "series"];

/** Code local -> code partagé : minuscules, « other », anglais = « en ». */
export function langToWire(code: string): string {
  if (code === OTHER_LANG) return "other";
  if (code === "GB") return "en";
  return protocolLang(code);
}
/** Code partagé -> code local (alias normalisés ; code inconnu conservé en majuscules). */
export function langFromWire(w: string): string {
  const l = w.trim();
  if (l.toLowerCase() === "other") return OTHER_LANG;
  if (l.toLowerCase() === "uk") return "UK_UA"; // ukrainien (ISO 639-1) ; l'anglais circule en « en »
  return canonicalLang(l) ?? l.toUpperCase();
}

type CatLike = Pick<CategoryRow, "kind" | "extId" | "enabled">;

/** Local -> prefs à publier. */
export function toPrefs(langs: string[] | null, cats: CatLike[], updatedAt: number): Omit<CloudPrefs, "by"> {
  const disabled: CloudPrefs["disabled"] = { live: [], movie: [], series: [] };
  for (const c of cats) {
    if (c.enabled || c.extId.length > MAX_ID_LEN || c.extId.length === 0) continue;
    const l = disabled[c.kind];
    if (l.length < MAX_IDS) l.push(c.extId);
  }
  return { langs: langs == null ? null : [...new Set(langs.map(langToWire))].sort(), disabled, updatedAt };
}

export interface ApplyPlan { langs: string[] | null; changes: { id: number; enabled: 0 | 1 }[] }

/** Prefs reçues -> modifications locales (langues de la source + état des catégories de la génération courante). */
export function fromPrefs(prefs: CloudPrefs, cats: (CatLike & { id?: number })[]): ApplyPlan {
  const off = { live: new Set(prefs.disabled?.live ?? []), movie: new Set(prefs.disabled?.movie ?? []), series: new Set(prefs.disabled?.series ?? []) };
  const changes: ApplyPlan["changes"] = [];
  for (const c of cats) {
    const want: 0 | 1 = off[c.kind]?.has(c.extId) ? 0 : 1;
    if (want !== c.enabled && c.id != null) changes.push({ id: c.id, enabled: want });
  }
  const langs = prefs.langs == null ? null : [...new Set(prefs.langs.map(langFromWire))];
  return { langs, changes };
}

/** Le plus récent gagne : on n'applique que ce qui est strictement plus récent que la dernière version publiée/appliquée ici. */
export const shouldApply = (remote: CloudPrefs | null | undefined, localAt: number): remote is CloudPrefs =>
  !!remote && Number.isFinite(remote.updatedAt) && remote.updatedAt > localAt;

export const sameLangs = (a: string[] | null, b: string[] | null) =>
  a == null || b == null ? a === b : a.length === b.length && [...a].sort().every((x, i) => x === [...b].sort()[i]);

/** Regroupe les changements : `fn` reçoit l'ensemble des clés notées pendant la fenêtre, une seule fois. */
export function createBatcher<K>(ms: number, fn: (keys: K[]) => void) {
  const keys = new Set<K>();
  let t: ReturnType<typeof setTimeout> | null = null;
  return {
    note(k: K) {
      keys.add(k);
      if (t) clearTimeout(t);
      t = setTimeout(() => { t = null; const ks = [...keys]; keys.clear(); fn(ks); }, ms);
    },
    cancel() { if (t) clearTimeout(t); t = null; keys.clear(); },
  };
}
