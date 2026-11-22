// Porte du code parental branchée sur le lecteur (store.setOpenGate) : la catégorie de la cible est retrouvée dans le
// catalogue local (chaîne, film, série de l'épisode), puis db/contentGate décide (et demande le code si besoin).

import { db } from "@/db/db";
import { blockedCategoryIds, ensureCategoryAccess } from "@/db/contentGate";
import type { Kind } from "@/db/types";
import { usePrefs } from "@/state/prefs";
import { askParentalCode } from "@/state/unlocks";
import type { PlayTarget } from "./resolve";
import type { OpenGate } from "./store";

/** Type de catalogue et catégorie d'une cible ; catégorie inconnue = null (titre absent du catalogue local). */
export async function categoryOfTarget(t: PlayTarget): Promise<{ kind: Kind; catExt: string | null } | null> {
  if (t.kind === "live") {
    const c = t.channel?.catExt ?? (await db.channels.where("[sourceId+streamId]").equals([t.cid, t.refId]).first())?.catExt;
    return { kind: "live", catExt: c ?? null };
  }
  if (t.kind === "movie") return { kind: "movie", catExt: (await db.movies.where("[sourceId+streamId]").equals([t.cid, t.refId]).first())?.catExt ?? null };
  if (t.kind === "episode" && t.seriesId != null) return { kind: "series", catExt: (await db.series.where("[sourceId+seriesId]").equals([t.cid, t.seriesId]).first())?.catExt ?? null };
  return null;
}

export function makeOpenGate(ask: (label: string) => Promise<boolean> = askParentalCode): OpenGate {
  return {
    async allow(t) {
      const c = await categoryOfTarget(t).catch(() => null);
      if (!c) return true;
      return ensureCategoryAccess(usePrefs.getState().profileId, { id: t.sourceId, cid: t.cid }, c.kind, c.catExt, t.title, ask);
    },
    blockedLive: (sourceId, cid) => blockedCategoryIds(usePrefs.getState().profileId, { id: sourceId, cid }, "live"),
  };
}
