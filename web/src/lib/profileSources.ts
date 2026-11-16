// Sources visibles par profil : règles pures (testées à part). Les sources sont désignées par leur NOM (stable entre appareils).
// Même logique qu'Android (ProfileSources.kt) : absent = toutes les sources.

export interface NamedSource { id?: number; name: string }

const key = (n: string) => n.trim().toLowerCase();

/** Liste de noms nettoyée (sans vide ni doublon, 12 au plus : limite du Worker) ; undefined = toutes. */
export function cleanNames(names: readonly string[] | undefined | null): string[] | undefined {
  const out: string[] = [];
  for (const n of names ?? []) { if (typeof n !== "string") continue; const t = n.trim().slice(0, 40); if (t && !out.some((o) => key(o) === key(t))) out.push(t); }
  return out.length ? out.slice(0, 12) : undefined;
}

/** Sources visibles : toutes sans choix, ou si aucun nom ne correspond à une source de cet appareil (jamais un catalogue vide). */
export function visibleSources<T extends NamedSource>(list: readonly T[], names: readonly string[] | undefined): T[] {
  if (!names?.length) return [...list];
  const want = new Set(names.map(key));
  const v = list.filter((s) => want.has(key(s.name)));
  return v.length ? v : [...list];
}

/** Source à rendre active : null si l'active est déjà visible (ou s'il n'y a aucune source). */
export function activeToSet<T extends NamedSource>(list: readonly T[], names: readonly string[] | undefined, activeId: number | null): number | null {
  const v = visibleSources(list, names);
  if (!v.length) return null;
  return v.some((s) => s.id === activeId) ? null : v[0]!.id ?? null;
}

/** Nouveau choix après avoir coché ou décoché une source ; tout coché ou plus rien de coché = toutes (undefined). */
export function toggleSource(allNames: readonly string[], current: readonly string[] | undefined, name: string): string[] | undefined {
  const base = [...(current?.length ? current : allNames)];
  const i = base.findIndex((n) => key(n) === key(name));
  if (i >= 0) base.splice(i, 1); else base.push(name);
  const have = new Set(base.map(key));
  if (!base.length || allNames.every((n) => have.has(key(n)))) return undefined;
  return base;
}

/** Pastille courte d'une source (mode « Toutes les sources ») : son nom tronqué à 8 caractères. */
export function sourceBadge(name: string): string {
  const n = name.trim() || "?";
  return n.length <= 8 ? n : `${n.slice(0, 7).trimEnd()}…`;
}

export const sameNames = (a: readonly string[] | undefined, b: readonly string[] | undefined) =>
  (a ?? []).length === (b ?? []).length && (a ?? []).every((x, i) => x === (b ?? [])[i]);
