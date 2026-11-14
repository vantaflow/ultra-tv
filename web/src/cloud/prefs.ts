// Réglages d'affichage partagés (langues, catégories désactivées et leur ordre, catégories masquées par profil, chaînes masquées, ordre des chaînes) : conversions pures, règle « le plus récent gagne »,
// et regroupement des envois (anti-rebond). Aucune E/S ici.

import { OTHER_LANG, canonicalLang, protocolLang } from "@/lib/categoryLang";
import { mergeCategoryOrder } from "@/lib/categoryOrder";
import type { CategoryRow, Kind } from "@/db/types";
import { pHiddenToWire, type PHiddenLocal, type PHiddenWire } from "@/lib/profileCategories";
import type { CloudPrefs } from "./client";

export const MAX_IDS = 5000;
export const MAX_ID_LEN = 128;
export const MAX_HIDDEN = 3000;
export const MAX_CHORDER = 400;
export const MAX_PROFILE_NAME = 24;
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

type CatLike = Pick<CategoryRow, "kind" | "extId" | "enabled"> & { ord?: number };

export interface HiddenLocal { streamId: number; catExt: string | null; name: string }
export interface ChOrderLocal { profile: string; scope: string; ids: number[] }

const validId = (x: string) => x.length > 0 && x.length <= MAX_ID_LEN;

type HiddenWire = NonNullable<CloudPrefs["hidden"]>[number];
type ChOrderWire = NonNullable<CloudPrefs["chOrder"]>[number];
const isIntId = (x: string) => /^\d+$/.test(x) && Number.isSafeInteger(Number(x));

/** Entrées reçues que le bureau ne sait pas interpréter (identifiant non numérique) : conservées telles quelles et renvoyées (le Worker remplace la liste). */
export const foreignHidden = (list: HiddenWire[]): HiddenWire[] => list.filter((h) => !isIntId(h.i));

/** Chaînes masquées locales -> format partagé (plafonds du Worker respectés). */
export function hiddenToWire(rows: HiddenLocal[], keep: HiddenWire[] = []): NonNullable<CloudPrefs["hidden"]> {
  const out: NonNullable<CloudPrefs["hidden"]> = [];
  const own = new Set(rows.map((r) => String(r.streamId)));
  for (const r of rows) {
    if (out.length >= MAX_HIDDEN) break;
    out.push({ i: String(r.streamId), c: r.catExt && validId(r.catExt) ? r.catExt : null, n: (r.name ?? "").slice(0, 120) });
  }
  for (const h of keep) if (out.length < MAX_HIDDEN && !own.has(h.i)) { own.add(h.i); out.push(h); }
  return out;
}
/** Format partagé -> chaînes masquées locales (identifiants non numériques ignorés : le catalogue local n'a que des identifiants entiers). */
export function hiddenFromWire(list: NonNullable<CloudPrefs["hidden"]>): HiddenLocal[] {
  const seen = new Set<number>();
  const out: HiddenLocal[] = [];
  for (const h of list) {
    if (!isIntId(h.i)) continue;
    const n = Number(h.i);
    if (seen.has(n)) continue;
    seen.add(n);
    out.push({ streamId: n, catExt: h.c ?? null, name: h.n ?? "" });
  }
  return out;
}

/** Ordres personnels locaux (profil = NOM) -> format partagé ; listes vides ignorées, doublons profil+liste : le dernier gagne. */
export function chOrderToWire(rows: ChOrderLocal[], keep: ChOrderWire[] = []): NonNullable<CloudPrefs["chOrder"]> {
  const m = new Map<string, { p: string; s: string; k: string[] }>();
  for (const r of rows) {
    const p = r.profile.trim().slice(0, MAX_PROFILE_NAME);
    if (!p || !validId(r.scope) || !r.ids.length) continue;
    m.set(`${p.toLowerCase()}|${r.scope}`, { p, s: r.scope, k: r.ids.slice(0, MAX_IDS).map(String) });
  }
  // Entrées reçues non interprétées (profil inconnu ici, identifiants non numériques) : renvoyées telles quelles, sauf si l'utilisateur a une liste locale pour la même clé.
  for (const k of keep) { const key = `${k.p.toLowerCase()}|${k.s}`; if (!m.has(key)) m.set(key, k); }
  return [...m.values()].slice(0, MAX_CHORDER);
}

/**
 * Ordres reçus -> modifications locales. [profiles] : profils locaux { id, name } (name = nom partagé).
 * Profil inconnu ici : ignoré. Listes locales absentes de la réception : effacées (le compte remplace l'ordre).
 */
export function planChOrder(
  received: NonNullable<CloudPrefs["chOrder"]>,
  profiles: { id: string; name: string }[],
  existing: { profile: string; scope: string }[],
): { put: { profile: string; scope: string; ids: number[] }[]; remove: { profile: string; scope: string }[]; foreign: ChOrderWire[] } {
  const low = (n: string) => n.trim().toLowerCase();
  const idOf = (name: string) => profiles.find((p) => low(p.name) === low(name))?.id;
  const put = new Map<string, { profile: string; scope: string; ids: number[] }>();
  const foreign: ChOrderWire[] = [];
  const foreignKeys = new Set<string>();
  for (const e of received) {
    const profile = idOf(e.p);
    if (!profile || e.k.some((x) => !isIntId(x))) { foreign.push(e); foreignKeys.add(`${profile ?? low(e.p)}\u0000${e.s}`); continue; }
    const ids = [...new Set(e.k.map(Number))];
    if (ids.length) put.set(`${profile}\u0000${e.s}`, { profile, scope: e.s, ids });
  }
  // Seules les listes des profils connus absentes de la réception sont effacées (ni reçues, ni gardées telles quelles).
  const remove = existing.filter((x) => !put.has(`${x.profile}\u0000${x.scope}`) && !foreignKeys.has(`${x.profile}\u0000${x.scope}`));
  return { put: [...put.values()], remove, foreign };
}

export interface PrefsExtra { hidden?: HiddenLocal[]; chOrder?: ChOrderLocal[]; pHidden?: PHiddenLocal[]; keepHidden?: HiddenWire[]; keepChOrder?: ChOrderWire[]; keepPHidden?: PHiddenWire[] }

/** Local -> prefs à publier. `order` : catégories de chaque type triées comme affichées (`ord`). */
export function toPrefs(langs: string[] | null, cats: CatLike[], updatedAt: number, extra: PrefsExtra = {}): Omit<CloudPrefs, "by"> {
  const disabled: CloudPrefs["disabled"] = { live: [], movie: [], series: [] };
  const order: NonNullable<CloudPrefs["order"]> = { live: [], movie: [], series: [] };
  for (const c of cats) {
    if (c.enabled || !validId(c.extId)) continue;
    const l = disabled[c.kind];
    if (l.length < MAX_IDS) l.push(c.extId);
  }
  for (const k of KINDS) {
    order[k] = cats.map((c, i) => ({ c, i })).filter((x) => x.c.kind === k && validId(x.c.extId))
      .sort((a, b) => (a.c.ord ?? 0) - (b.c.ord ?? 0) || a.i - b.i).map((x) => x.c.extId).slice(0, MAX_IDS);
  }
  const out: Omit<CloudPrefs, "by"> = { langs: langs == null ? null : [...new Set(langs.map(langToWire))].sort(), disabled, order, updatedAt };
  if (extra.hidden) out.hidden = hiddenToWire(extra.hidden, extra.keepHidden);
  if (extra.chOrder) out.chOrder = chOrderToWire(extra.chOrder, extra.keepChOrder);
  if (extra.pHidden) out.pHidden = pHiddenToWire(extra.pHidden, extra.keepPHidden, MAX_CHORDER, MAX_IDS, MAX_PROFILE_NAME);
  return out;
}

export interface ApplyPlan {
  langs: string[] | null;
  changes: { id: number; enabled: 0 | 1 }[];
  /** Nouvelle position (`ord`) des catégories dont la place change. */
  ords: { id: number; ord: number }[];
}

/** Prefs reçues -> modifications locales (langues de la source, état et ordre des catégories de la génération courante). */
export function fromPrefs(prefs: CloudPrefs, cats: (CatLike & { id?: number })[]): ApplyPlan {
  const off = { live: new Set(prefs.disabled?.live ?? []), movie: new Set(prefs.disabled?.movie ?? []), series: new Set(prefs.disabled?.series ?? []) };
  const changes: ApplyPlan["changes"] = [];
  for (const c of cats) {
    const want: 0 | 1 = off[c.kind]?.has(c.extId) ? 0 : 1;
    if (want !== c.enabled && c.id != null) changes.push({ id: c.id, enabled: want });
  }
  const ords: ApplyPlan["ords"] = [];
  if (prefs.order) {
    for (const k of KINDS) {
      const recv = prefs.order[k];
      if (!recv?.length) continue;
      const mine = cats.map((c, i) => ({ c, i })).filter((x) => x.c.kind === k).sort((a, b) => (a.c.ord ?? 0) - (b.c.ord ?? 0) || a.i - b.i).map((x) => x.c);
      const byExt = new Map(mine.map((c) => [c.extId, c]));
      mergeCategoryOrder(mine.map((c) => c.extId), recv).forEach((ext, i) => {
        const c = byExt.get(ext)!;
        if (c.id != null && c.ord !== i) ords.push({ id: c.id, ord: i });
      });
    }
  }
  const langs = prefs.langs == null ? null : [...new Set(prefs.langs.map(langFromWire))];
  return { langs, changes, ords };
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
