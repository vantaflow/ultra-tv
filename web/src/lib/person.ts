// Pages acteurs / réalisateurs : lecture des réponses TMDB et rapprochement avec la playlist (même règle que l'app Android,
// PersonLogic.kt). Un titre correspond quand son nom normalisé est celui de l'œuvre (titre ou titre original) et que
// l'année, si les deux sont connues, est à ±1 an.
import { searchTokens } from "./text";

export interface PersonCredit { tv: boolean; title: string; original: string | null; year: number | null }

const key = (s: string) => searchTokens(s).join(" ");

/** Identifiant de la personne dont le nom correspond (premier à nom identique, sinon le premier résultat). */
export function personSearchBest(json: unknown, name: string): number | null {
  const results = (json as { results?: unknown[] } | null)?.results;
  if (!Array.isArray(results)) return null;
  const want = key(name);
  let first: number | null = null;
  for (const r of results) {
    const o = r as { id?: unknown; name?: unknown };
    const id = typeof o.id === "number" && o.id > 0 ? o.id : null;
    if (id == null) continue;
    first ??= id;
    if (typeof o.name === "string" && key(o.name) === want) return id;
  }
  return first;
}

/** Filmographie (rôles et postes confondus), sans doublon, la plus récente d'abord. */
export function personCredits(json: unknown): PersonCredit[] {
  const o = json as { cast?: unknown[]; crew?: unknown[] } | null;
  const out = new Map<string, PersonCredit>();
  for (const arr of [o?.cast, o?.crew]) {
    if (!Array.isArray(arr)) continue;
    for (const raw of arr) {
      const c = raw as Record<string, unknown>;
      const tv = c.media_type === "tv";
      const title = String((tv ? c.name : c.title) ?? "").trim();
      if (!title) continue;
      const orig = String((tv ? c.original_name : c.original_title) ?? "").trim();
      const y = Number(String((tv ? c.first_air_date : c.release_date) ?? "").slice(0, 4));
      const year = Number.isInteger(y) && y > 1800 ? y : null;
      const k = `${tv ? "t" : "m"}|${key(title)}|${year ?? ""}`;
      if (!out.has(k)) out.set(k, { tv, title, original: orig && orig !== title ? orig : null, year });
    }
  }
  return [...out.values()].sort((a, b) => (b.year ?? 0) - (a.year ?? 0));
}

/** Le titre de la playlist est-il cette œuvre ? */
export function creditMatches(c: PersonCredit, title: string, year: number | null): boolean {
  const k = key(title);
  if (!k || (k !== key(c.title) && (c.original == null || k !== key(c.original)))) return false;
  return year == null || c.year == null || Math.abs(year - c.year) <= 1;
}
