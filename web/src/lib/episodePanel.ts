// Panneau « Épisodes » du lecteur : saisons et épisodes de la série en cours, avec progression et « Vu ». Logique pure.

export interface PanelEpisode { id: number; title: string; num: number; season: number; ext: string; secs: number; img: string | null }
export interface PanelSeason<E extends PanelEpisode = PanelEpisode> { n: number; eps: E[] }

export interface PanelItem<E extends PanelEpisode = PanelEpisode> {
  ep: E;
  /** Part regardée (historique local), 0..1. */
  ratio: number;
  /** Vu : terminé localement (≥ 95 %) ou marqué vu sur Trakt. */
  watched: boolean;
  /** Épisode en cours de lecture. */
  current: boolean;
  /** Position de reprise (s) si l'épisode a été commencé et pas terminé. */
  resumeAt?: number;
}

export interface PanelData<E extends PanelEpisode = PanelEpisode> { seasons: { n: number; items: PanelItem<E>[] }[]; currentSeason: number | null }

/**
 * Construit le panneau : `history` = progression locale par identifiant d'épisode, `seen` = épisodes vus sur Trakt
 * (clés « saisonxépisode »), `currentId` = épisode joué. La saison ouverte par défaut est celle de l'épisode joué.
 */
export function episodePanel<E extends PanelEpisode>(
  seasons: PanelSeason<E>[],
  opts: { currentId: number | null; history: ReadonlyArray<{ epId?: number; pos: number; dur: number }>; seen?: ReadonlySet<string> | null },
): PanelData<E> {
  const prog = new Map<number, { pos: number; dur: number }>();
  for (const h of opts.history) if (h.epId != null && h.dur > 0) prog.set(h.epId, h);
  let currentSeason: number | null = null;
  const out = seasons.filter((s) => s.eps.length).map((s) => ({
    n: s.n,
    items: s.eps.map((ep): PanelItem<E> => {
      const h = prog.get(ep.id);
      const ratio = h ? Math.min(1, Math.max(0, h.pos / h.dur)) : 0;
      const current = ep.id === opts.currentId;
      if (current) currentSeason = s.n;
      const watched = ratio >= 0.95 || !!opts.seen?.has(`${ep.season}x${ep.num}`);
      return { ep, ratio, watched, current, resumeAt: h && h.pos > 20 && ratio < 0.95 ? h.pos : undefined };
    }),
  }));
  return { seasons: out, currentSeason: currentSeason ?? out[0]?.n ?? null };
}

/** Déplacement au clavier dans une liste de boutons (↑ ↓, Début, Fin) : index suivant, borné. */
export function stepIndex(i: number, len: number, key: string): number {
  if (len <= 0) return -1;
  if (key === "Home") return 0;
  if (key === "End") return len - 1;
  const d = key === "ArrowDown" ? 1 : key === "ArrowUp" ? -1 : 0;
  return i < 0 ? 0 : Math.max(0, Math.min(len - 1, i + d));
}
