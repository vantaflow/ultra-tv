// Taille de l'interface (Réglages › Affichage) : zoom de toute la fenêtre de l'application de bureau. Le zoom de la page
// (comme Ctrl/Cmd +) agrandit texte et éléments ET réduit la largeur utile en pixels CSS : les mises en page adaptatives
// (rail replié, colonnes, grilles) se recalculent, rien ne déborde.

export const UI_SCALES = [0.9, 1, 1.1, 1.25, 1.5] as const;
export const DEFAULT_UI_SCALE = 1;

/** Valeur lue des préférences : une des tailles proposées, sinon 100 %. */
export function parseUiScale(v: unknown): number {
  const n = typeof v === "number" ? v : typeof v === "string" ? Number(v) : Number.NaN;
  return (UI_SCALES as readonly number[]).find((s) => Math.abs(s - n) < 0.001) ?? DEFAULT_UI_SCALE;
}

/** Libellé « 125 % » (espace insécable fine selon la langue). */
export const uiScaleLabel = (s: number, locale: string): string => new Intl.NumberFormat(locale, { style: "percent", maximumFractionDigits: 0 }).format(s);
