// Colonne des noms de chaînes du guide (application de bureau) : largeur réglable, noms sur deux lignes.
export type GuideCol = "narrow" | "normal" | "wide";
export const GUIDE_COLS: GuideCol[] = ["narrow", "normal", "wide"];

/** Largeur en rem : « Étroite » reprend l'ancienne colonne fixe (14 rem), « Normale » est plus large par défaut. */
const REM: Record<GuideCol, number> = { narrow: 14, normal: 18, wide: 23 };

export function parseGuideCol(v: unknown): GuideCol {
  return GUIDE_COLS.includes(v as GuideCol) ? (v as GuideCol) : "normal";
}

export function guideColRem(v: unknown): number {
  return REM[parseGuideCol(v)];
}

/** Largeur en pixels (1 rem = 16 px), pour le positionnement de la grille. */
export function guideColPx(v: unknown): number {
  return guideColRem(v) * 16;
}

/** Texte de l'en-tête de ligne : numéro puis nom ; logo masqué, le nom garde toute la place. */
export function chanLabel(num: number | undefined | null, display: string): string {
  return num ? `${num} · ${display}` : display;
}
