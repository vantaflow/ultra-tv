// Vue du profil courant sur les chaînes de chaque source : chaînes qu'il masque (Set en mémoire, pas de lecture par ligne) et numéros
// affichés. Une seule surveillance pour toutes les sources, relancée au changement de profil ou de génération de catalogue.
import { liveQuery, type Subscription } from "dexie";
import { create } from "zustand";
import { loadChannelView, NO_VIEW, type ChannelView } from "@/db/profileChannels";
import type { Source } from "@/db/types";
import { useSources } from "./sources";
import { usePrefs } from "./prefs";

interface ViewStore { views: ReadonlyMap<number, ChannelView> }
export const useChannelViews = create<ViewStore>(() => ({ views: new Map() }));

/** Vue d'une source pour le profil courant (lecture synchrone : zapping, saisie d'un numéro…). Vide tant que non chargée. */
export const viewOf = (sourceId: number | undefined): ChannelView => (sourceId == null ? NO_VIEW : useChannelViews.getState().views.get(sourceId) ?? NO_VIEW);

/** Vue réactive d'une source (rendu des listes). */
export function useChannelView(source: Pick<Source, "id"> | undefined): ChannelView {
  return useChannelViews((s) => (source?.id == null ? NO_VIEW : s.views.get(source.id) ?? NO_VIEW));
}

let sub: Subscription | null = null;
let sig = "";

/** (Re)branche la surveillance si le profil ou la liste des générations de catalogue a changé. */
function refresh() {
  const profile = usePrefs.getState().profileId;
  const list = useSources.getState().list;
  const next = `${profile}|${list.map((s) => `${s.id}:${s.cid}`).join(",")}`;
  if (next === sig) return;
  sig = next;
  sub?.unsubscribe();
  sub = liveQuery(async () => {
    const out = new Map<number, ChannelView>();
    for (const s of list) if (s.id != null) out.set(s.id, await loadChannelView(profile, s));
    return out;
  }).subscribe({ next: (views) => useChannelViews.setState({ views }), error: () => undefined });
}

let started = false;
export function startChannelViewWatcher() {
  if (started) return;
  started = true;
  usePrefs.subscribe((s, p) => { if (s.profileId !== p.profileId) refresh(); });
  useSources.subscribe(refresh);
  refresh();
}
