// Traduction des identifiants du bureau (clés numériques locales) vers ceux du compte cloud, et retour.
// Le format du compte est celui de l'appli Android (remoteId texte) : le bureau garde son schéma et ses clés numériques,
// et ne traduit qu'à la frontière (favoris, historique, réglages d'affichage). Fonctions pures, aucune E/S.

import type { Kind } from "@/db/types";

export interface IdMapper {
  /** Identifiant local (direct, film, série) -> identifiant du compte. */
  out(kind: Kind, id: number): string;
  /** Identifiant du compte -> identifiant local ; null si ce bureau ne sait pas l'interpréter (entrée « étrangère »). */
  inn(kind: Kind, wire: string): number | null;
  /** Catégorie locale (`extId`) -> identifiant du compte, et retour. */
  catOut(kind: Kind, ext: string): string;
  catIn(kind: Kind, wire: string): string;
  /** Épisode (identifiant local + série parente) -> identifiant du compte, et retour. */
  epOut(seriesId: number, epId: number): string;
  epIn(wire: string): number | null;
  /** Série parente d'un épisode (champ `par` de l'historique), et retour. */
  parentOut(seriesId: number): string;
  parentIn(wire: string): number | null;
  /** Vrai si la traduction change quelque chose (sinon : identité, comportement historique). */
  readonly active: boolean;
}

const INT_RE = /^\d+$/;
export const toInt = (x: string): number | null => (INT_RE.test(x) && Number.isSafeInteger(Number(x)) ? Number(x) : null);

/** Identité : Xtream, Jellyfin, NAS… (les identifiants sont déjà ceux du fournisseur, numériques des deux côtés). */
export const identityMapper: IdMapper = {
  out: (_k, id) => String(id),
  inn: (_k, w) => toInt(w),
  catOut: (_k, e) => e,
  catIn: (_k, w) => w,
  epOut: (_s, id) => String(id),
  epIn: toInt,
  parentOut: (id) => String(id),
  parentIn: toInt,
  active: false,
};

// ---------------------------------------------------------------- M3U

/**
 * Identifiant Android de chaque entrée d'une playlist M3U, dans l'ordre du fichier (mêmes règles que `M3uParser.kt`) :
 * le tvg-id ; s'il est déjà pris par une entrée précédente, « tvgId#N » ; sans tvg-id, « m3u-N ». Le compteur N est partagé
 * par ces deux derniers cas et compte TOUTES les entrées (direct, films et épisodes : Android ne les distingue pas).
 * [tvgIds] : tvg-id brut de chaque entrée ; vide ou blanc = absent.
 */
export function m3uWireIds(tvgIds: readonly (string | null | undefined)[]): string[] {
  const used = new Set<string>();
  let seq = 0;
  return tvgIds.map((raw) => {
    const tvg = raw && raw.trim() ? raw : null;
    if (tvg === null) return `m3u-${seq++}`;
    if (!used.has(tvg)) { used.add(tvg); return tvg; }
    return `${tvg}#${seq++}`;
  });
}

/** Catégorie M3U côté compte : « g:<groupe> » (Android). */
export const M3U_CAT_PREFIX = "g:";

/**
 * Traducteur d'une source M3U. [tvgIds] : tvg-id de chaque entrée (indice = `streamId - 1`, comme à la synchro du bureau).
 * Direct, films et épisodes partagent la même numérotation ; les séries regroupées par le bureau n'existent pas sur Android
 * (identifiants du bureau conservés tels quels).
 */
export function m3uMapper(tvgIds: readonly (string | null | undefined)[]): IdMapper {
  const wire = m3uWireIds(tvgIds);
  const byWire = new Map<string, number>();
  wire.forEach((w, i) => byWire.set(w, i + 1));
  const entry = (id: number) => (Number.isInteger(id) && id >= 1 && id <= wire.length ? wire[id - 1]! : String(id));
  const lookup = (w: string): number | null => {
    const hit = byWire.get(w);
    if (hit != null) return hit;
    // Ancienne version du bureau : numéro d'entrée brut.
    const n = toInt(w);
    return n != null && n >= 1 && n <= wire.length ? n : null;
  };
  return {
    out: (k, id) => (k === "series" ? String(id) : entry(id)),
    inn: (k, w) => (k === "series" ? toInt(w) : lookup(w)),
    catOut: (_k, e) => M3U_CAT_PREFIX + e,
    catIn: (_k, w) => (w.startsWith(M3U_CAT_PREFIX) ? w.slice(M3U_CAT_PREFIX.length) : w),
    epOut: (_s, id) => entry(id),
    epIn: lookup,
    parentOut: (id) => String(id),
    parentIn: toInt,
    active: true,
  };
}

// ---------------------------------------------------------------- Stalker

/** Préfixe Android des séries du module `type=series` (StalkerClient.SERIES_PREFIX). */
export const STALKER_SERIES_PREFIX = "s:";
/** Le bureau numérote les épisodes `saison * 10000 + numéro` ; Android : « <série>:<saison>:<numéro> ». */
const EP_BASE = 10_000;

/**
 * Traducteur d'une source Stalker. [portalOf] : identifiant du portail des séries du module `type=series`
 * (clé = identifiant local, souvent un condensé) ; les autres séries (VOD) ont l'identifiant du portail en clair.
 * Les catégories ont déjà le même identifiant des deux côtés.
 */
export function stalkerMapper(portalOf: ReadonlyMap<number, string>): IdMapper {
  const byPortal = new Map<string, number>();
  for (const [id, p] of portalOf) byPortal.set(p, id);
  const seriesOut = (id: number) => { const p = portalOf.get(id); return p != null ? STALKER_SERIES_PREFIX + p : String(id); };
  const seriesIn = (w: string): number | null => (w.startsWith(STALKER_SERIES_PREFIX) ? byPortal.get(w.slice(STALKER_SERIES_PREFIX.length)) ?? null : toInt(w));
  return {
    out: (k, id) => (k === "series" ? seriesOut(id) : String(id)),
    inn: (k, w) => (k === "series" ? seriesIn(w) : toInt(w)),
    catOut: (_k, e) => e,
    catIn: (_k, w) => w,
    epOut: (s, id) => `${seriesOut(s)}:${Math.floor(id / EP_BASE)}:${id % EP_BASE}`,
    epIn: (w) => {
      const m = /^(.*):(\d+):(\d+)$/.exec(w);
      if (!m) return toInt(w);
      const n = Number(m[3]);
      return n < EP_BASE ? Number(m[2]) * EP_BASE + n : null;
    },
    parentOut: seriesOut,
    parentIn: seriesIn,
    active: true,
  };
}
