// Filtres de la grille Films / Séries : note minimale et période (décennie). Même découpage que l'app Android.
export type Period = "all" | "2020s" | "2010s" | "2000s" | "1990s" | "older";
export interface VodFilter { minRating: number; period: Period }
export const NO_FILTER: VodFilter = { minRating: 0, period: "all" };
export const RATINGS = [0, 6, 7, 8] as const;
export const PERIODS: Period[] = ["all", "2020s", "2010s", "2000s", "1990s", "older"];

const BOUNDS: Record<Period, [number, number]> = {
  all: [0, Infinity], "2020s": [2020, Infinity], "2010s": [2010, 2019], "2000s": [2000, 2009], "1990s": [1990, 1999], older: [0, 1989],
};

export const filterActive = (f: VodFilter) => f.minRating > 0 || f.period !== "all";

/** La ligne passe-t-elle le filtre ? Année inconnue : exclue dès qu'une période est choisie. */
export function matchesFilter(r: { rating: number; year: number | null }, f: VodFilter): boolean {
  if (f.minRating > 0 && !(r.rating >= f.minRating && r.rating <= 10)) return false;
  if (f.period !== "all") {
    if (r.year == null) return false;
    const [a, b] = BOUNDS[f.period];
    if (r.year < a || r.year > b) return false;
  }
  return true;
}
