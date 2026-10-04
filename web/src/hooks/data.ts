import { useLiveQuery } from "dexie-react-hooks";
import { useMemo } from "react";
import { categoriesOf, favoritesOf } from "@/db/queries";
import type { CategoryRow, FavoriteRow, Kind, Source } from "@/db/types";
import { OTHER_LANG } from "@/sync/core";
import { useT } from "@/i18n";
import { usePrefs } from "@/state/prefs";

/** Catégories activées d'un type, triées dans l'ordre du fournisseur. `undefined` pendant le premier chargement. */
export function useCategories(source: Source | undefined, kind: Kind): CategoryRow[] | undefined {
  return useLiveQuery(async () => (source?.cid ? (await categoriesOf(source.cid, kind)).filter((c) => c.enabled && c.count > 0) : []), [source?.cid, kind]);
}

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
