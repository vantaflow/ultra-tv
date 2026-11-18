// Chaînes masquées PAR PROFIL (même modèle qu'Android : ChannelHidingRules / ProfileChannelHiding). Logique pure, sans E/S.
//
//  - Une chaîne masquée par UN profil reste dans le catalogue : les autres profils la voient, le profil courant la filtre à la lecture.
//  - Une chaîne masquée par TOUS les profils est supprimée du catalogue et ignorée à la synchro : c'est la table `hiddenChannels`,
//    qui garde donc son sens historique « masquées par tous » (champ `hidden` du compte cloud).
//  - Numéros : le numéro affiché d'une chaîne = son numéro (`num`) moins les chaînes de la même catégorie, masquées par le profil
//    courant, qui ont un numéro plus petit (pas de trou dans la numérotation de ce profil).

import type { HiddenLocal } from "@/cloud/prefs";

export type PerProfile = ReadonlyMap<string, ReadonlySet<number>>;

/** Chaînes masquées par TOUS les profils (intersection) ; aucun profil = rien. Ce sont les seules supprimées du catalogue. */
export function hiddenByAll(perProfile: PerProfile): Set<number> {
  const it = perProfile.values();
  const first = it.next();
  if (first.done) return new Set();
  const acc = new Set(first.value);
  for (const s of it) {
    if (!acc.size) break;
    for (const id of acc) if (!s.has(id)) acc.delete(id);
  }
  return acc;
}

/** Chaînes masquées par ce seul profil (et pas par tous) : celles que le cloud transporte par NOM de profil (`pHiddenCh`). */
export const partial = (mine: ReadonlySet<number>, all: ReadonlySet<number>): Set<number> => new Set([...mine].filter((x) => !all.has(x)));

/**
 * Réglages reçus du cloud -> chaînes masquées de chaque profil local (mêmes règles que ChannelHidingRules.mergeRemote d'Android).
 *  - [remoteAll] : champ historique `hidden` = masquées par TOUS les profils (ancien appareil : sa liste globale) ;
 *  - [remotePartial] : champ `pHiddenCh` (clé de profil -> ses masquées propres), `null` s'il n'a pas été transmis (appareil plus
 *    ancien) : les choix propres de chaque profil local sont alors CONSERVÉS.
 * Résultat d'un profil = (ses choix propres) ∪ [remoteAll]. Les clés de [local] sont celles du résultat (identifiants de profil ici).
 */
export function mergeRemote(local: PerProfile, remoteAll: ReadonlySet<number>, remotePartial: PerProfile | null): Map<string, Set<number>> {
  const localAll = hiddenByAll(local);
  const out = new Map<string, Set<number>>();
  for (const [key, mine] of local) {
    const own = remotePartial == null ? partial(mine, localAll) : new Set(remotePartial.get(key) ?? []);
    for (const id of remoteAll) own.add(id);
    out.set(key, own);
  }
  return out;
}

// ---------------- Numérotation par profil ----------------

/** Pour chaque catégorie, numéros (triés) des chaînes masquées par le profil courant. */
export type NumShift = ReadonlyMap<string, readonly number[]>;
export const NO_SHIFT: NumShift = new Map();

/** Construit le décalage à partir des chaînes masquées (catégorie + numéro). */
export function buildShift(hidden: readonly { catExt: string; num: number }[]): NumShift {
  const m = new Map<string, number[]>();
  for (const h of hidden) { const l = m.get(h.catExt); if (l) l.push(h.num); else m.set(h.catExt, [h.num]); }
  for (const l of m.values()) l.sort((a, b) => a - b);
  return m;
}

/** Nombre d'éléments strictement inférieurs à [x] dans [sorted] (recherche dichotomique). */
function below(sorted: readonly number[], x: number): number {
  let lo = 0; let hi = sorted.length;
  while (lo < hi) { const mid = (lo + hi) >> 1; if (sorted[mid]! < x) lo = mid + 1; else hi = mid; }
  return lo;
}

/** Numéro affiché d'une chaîne pour le profil : `num` moins les chaînes masquées de sa catégorie qui la précèdent. */
export function shownNum(shift: NumShift, c: { catExt: string; num: number }): number {
  const l = shift.get(c.catExt);
  return l ? c.num - below(l, c.num) : c.num;
}

// ---------------- Cloud : `pHiddenCh` ----------------

export interface PHiddenChWire { p: string; ch: { i: string; c: string | null; n: string }[] }
export interface PHiddenChLocal { profile: string; items: HiddenLocal[] }

// ---------------- Migration ----------------

export interface ProfileHiddenChannel { key: string; profile: string; sourceId: number; streamId: number; catExt: string | null; name: string }
export const profileHiddenChKey = (profile: string, sourceId: number, streamId: number) => `${profile}:${sourceId}:${streamId}`;

/** Identifiants des profils d'après les préférences enregistrées (JSON brut) ; le profil d'origine « main » à défaut. */
export function profileIdsFromPrefs(raw: string | null | undefined): string[] {
  try {
    const ids = ((JSON.parse(raw ?? "null") as { profiles?: { id?: unknown }[] } | null)?.profiles ?? []).map((p) => p.id).filter((x): x is string => typeof x === "string" && !!x);
    return ids.length ? [...new Set(ids)] : ["main"];
  } catch { return ["main"]; }
}

/** Migration : les chaînes masquées globales deviennent masquées par TOUS les profils (rien ne réapparaît). */
export function globalToProfiles(rows: readonly { sourceId: number; streamId: number; catExt: string | null; name: string }[], profileIds: readonly string[]): ProfileHiddenChannel[] {
  const out: ProfileHiddenChannel[] = [];
  for (const profile of profileIds) for (const r of rows) out.push({ key: profileHiddenChKey(profile, r.sourceId, r.streamId), profile, sourceId: r.sourceId, streamId: r.streamId, catExt: r.catExt, name: r.name });
  return out;
}
