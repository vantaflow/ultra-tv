// Porte des contenus : quelles catégories un écran doit écarter pour le profil courant. Logique pure, testée.
//
// * masquée par le profil (Réglages › Catégories) : écartée partout ;
// * verrouillée par code parental : écartée partout tant qu'elle n'a pas été déverrouillée pendant la session,
//   et TOUJOURS écartée des rangées de l'accueil (on ne pousse pas un contenu verrouillé en vitrine) ;
// * adulte (nom de catégorie ou indicateur du fournisseur) : écartée partout pour un profil Enfants (déverrouillable
//   avec le code parental seulement), et toujours écartée des rangées de l'accueil.

/** Même heuristique que l'appli Android (AdultHeuristic) : mot entier pour « ero » (« Hero », « Zero » ne comptent pas). */
const ADULT_RE = /xxx|adult|18\+|\+18|porn|(?<![\p{L}\p{N}])[eé]ro(tic|tica|tique|tik|tico)?(?![\p{L}\p{N}])|للكبار/iu;

export function isAdultCategory(c: { name: string; adult?: 0 | 1 }): boolean {
  return c.adult === 1 || ADULT_RE.test(c.name);
}

/** Contexte d'affichage : « list » = listes, guide, recherche, fiches, lecteur ; « home » = rangées de l'accueil. */
export type GateContext = "list" | "home";

export interface GateInput {
  hidden: ReadonlySet<string>;
  locked: ReadonlySet<string>;
  adult: ReadonlySet<string>;
  /** Catégories déverrouillées pendant la session (code saisi). */
  unlocked: ReadonlySet<string>;
  kids: boolean;
}

/** Identifiants de catégorie à écarter. */
export function blockedCategories(g: GateInput, ctx: GateContext): Set<string> {
  const out = new Set(g.hidden);
  for (const id of g.locked) if (ctx === "home" || !g.unlocked.has(id)) out.add(id);
  for (const id of g.adult) if (ctx === "home" || (g.kids && !g.unlocked.has(id))) out.add(id);
  return out;
}

/** Une catégorie exige-t-elle le code avant d'être ouverte (listes, fiche, lecture) ? */
export function needsCode(id: string, g: Omit<GateInput, "hidden">): boolean {
  if (g.unlocked.has(id)) return false;
  return g.locked.has(id) || (g.kids && g.adult.has(id));
}

/** Clé d'une catégorie déverrouillée pendant la session. */
export const unlockKey = (sourceId: number, kind: string, extId: string) => `${sourceId}:${kind}:${extId}`;
/** Clé de stockage des verrous d'une source et d'un type. */
export const lockKey = (sourceId: number, kind: string) => `${sourceId}:${kind}`;

/** Verrous (clé source:type -> identifiants) après pose ou retrait d'un verrou ; listes vides retirées. */
export function toggleLock(locks: Record<string, string[]>, sourceId: number, kind: string, extId: string, on: boolean): Record<string, string[]> {
  const k = lockKey(sourceId, kind);
  const cur = new Set(locks[k] ?? []);
  if (on) cur.add(extId); else cur.delete(extId);
  const next = { ...locks };
  if (cur.size) next[k] = [...cur]; else delete next[k];
  return next;
}

/** Verrous lus du stockage : toute valeur inattendue est ignorée (jamais d'exception). */
export function parseLocks(v: unknown): Record<string, string[]> {
  if (!v || typeof v !== "object" || Array.isArray(v)) return {};
  const out: Record<string, string[]> = {};
  for (const [k, ids] of Object.entries(v as Record<string, unknown>)) {
    if (!/^\d+:(live|movie|series)$/.test(k) || !Array.isArray(ids)) continue;
    const ok = ids.filter((x): x is string => typeof x === "string" && x.length > 0);
    if (ok.length) out[k] = ok;
  }
  return out;
}
