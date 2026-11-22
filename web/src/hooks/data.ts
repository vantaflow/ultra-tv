import { useLiveQuery } from "dexie-react-hooks";
import { useCallback, useMemo } from "react";
import { favoritesOf, visibleCategories } from "@/db/queries";
import { blockedCategoryIds, categoryNeedsCode, codeCategoryIds, ensureCategoryAccess, unlistedCategoryIds } from "@/db/contentGate";
import type { GateContext } from "@/lib/contentGate";
import { askParentalCode, useUnlocks } from "@/state/unlocks";
import type { CategoryRow, FavoriteRow, Kind, Source } from "@/db/types";
import { OTHER_LANG } from "@/sync/core";
import { useT } from "@/i18n";
import { usePrefs } from "@/state/prefs";

/** Profil courant, son statut Enfants et la version des déverrouillages : dépendances des requêtes de la porte des contenus. */
function useGateDeps() {
  const profile = usePrefs((s) => s.profileId);
  const kids = usePrefs((s) => !!s.profiles.find((p) => p.id === s.profileId)?.kids);
  const v = useUnlocks((s) => s.v);
  return { profile, kids, v };
}

/**
 * Catégories affichées pour le profil courant (téléchargées, non masquées par lui, et pas adultes pour un profil Enfants), triées dans
 * l'ordre du fournisseur. Une catégorie verrouillée y reste (cadenas, voir useCodeCats). `undefined` pendant le premier chargement.
 */
export function useCategories(source: Source | undefined, kind: Kind): CategoryRow[] | undefined {
  const { profile, kids, v } = useGateDeps();
  return useLiveQuery(async () => visibleCategories(source?.cid ?? 0, kind, source?.id ? await unlistedCategoryIds(profile, { id: source.id, cid: source.cid }, kind) : undefined), [source?.cid, source?.id, kind, profile, kids, v]);
}

/**
 * Catégories dont le contenu est écarté pour le profil courant : masquées, verrouillées (tant que le code n'a pas été saisi), adultes
 * pour un profil Enfants ; sur l'accueil (`home`), verrouillées et adultes toujours. Ensemble vide tant que non chargé.
 */
export function useBlockedCats(source: Source | undefined, kind: Kind, ctx: GateContext = "list"): ReadonlySet<string> {
  const { profile, kids, v } = useGateDeps();
  return useLiveQuery(() => (source?.id ? blockedCategoryIds(profile, { id: source.id, cid: source.cid }, kind, ctx) : EMPTY), [source?.id, source?.cid, kind, profile, kids, v, ctx]) ?? EMPTY;
}

/**
 * Ouverture d'une catégorie (liste, guide, fiche, lecture) : vrai tout de suite si elle est libre ; sinon la fenêtre du code
 * parental s'ouvre et le code accepté la déverrouille pour la session.
 */
export function useCatOpener(source: Source | undefined, kind: Kind): (catExt: string | null | undefined, label: string) => Promise<boolean> {
  const profile = usePrefs((s) => s.profileId);
  return useCallback(async (catExt, label) => (source?.id == null ? true : ensureCategoryAccess(profile, { id: source.id, cid: source.cid }, kind, catExt, label, askParentalCode)), [source?.id, source?.cid, kind, profile]);
}

/** Fiche d'un film / d'une série : la catégorie [catExt] exige-t-elle le code parental ? `undefined` pendant le chargement. */
export function useNeedsCode(source: Source | undefined, kind: Kind, catExt: string | null | undefined): boolean | undefined {
  const { profile, kids, v } = useGateDeps();
  // Réponse étiquetée par la catégorie demandée : une réponse d'avant (autre catégorie) ne laisse jamais passer la fiche.
  const r = useLiveQuery(async () => ({ catExt, need: source?.id ? await categoryNeedsCode(profile, { id: source.id, cid: source.cid }, kind, catExt) : false }), [source?.id, source?.cid, kind, catExt, profile, kids, v]);
  return r && r.catExt === catExt ? r.need : undefined;
}

/** Catégories affichées avec un cadenas (code parental exigé pour les ouvrir). */
export function useCodeCats(source: Source | undefined, kind: Kind): ReadonlySet<string> {
  const { profile, kids, v } = useGateDeps();
  return useLiveQuery(() => (source?.id ? codeCategoryIds(profile, { id: source.id, cid: source.cid }, kind) : EMPTY), [source?.id, source?.cid, kind, profile, kids, v]) ?? EMPTY;
}
const EMPTY: ReadonlySet<string> = new Set();

export function useFavorites(source: Source | undefined, kind?: Kind): FavoriteRow[] {
  const profile = usePrefs((s) => s.profileId);
  return useLiveQuery(() => (source?.id ? favoritesOf(profile, source.id, kind) : []), [source?.id, profile, kind]) ?? [];
}

export function useLangSummary(source: Source | undefined): string {
  const t = useT();
  return useMemo(() => {
    if (!source?.langs) return t("common.langsAll");
    const l = source.langs.map((c) => (c === OTHER_LANG ? t("lang.other") : c));
    return l.length > 3 ? `${l.slice(0, 3).join(" · ")} +${l.length - 3}` : l.join(" · ");
  }, [source?.langs, t]);
}
