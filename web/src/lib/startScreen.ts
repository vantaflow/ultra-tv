// Écran ouvert au lancement (Réglages › Affichage › Ouvrir sur) : mêmes choix qu'Android, plus « Dernier écran ».

export type StartScreen = "home" | "live" | "movies" | "series" | "guide" | "last";
export const START_SCREENS: StartScreen[] = ["home", "live", "movies", "series", "guide", "last"];

const PATHS: Record<Exclude<StartScreen, "last">, string> = { home: "/", live: "/live", movies: "/movies", series: "/series", guide: "/guide" };

/** Rubriques que « Dernier écran » peut rouvrir : jamais une fiche, la recherche, les réglages ni l'accueil de configuration. */
const REMEMBERABLE = new Set(["/", "/live", "/guide", "/movies", "/series", "/sport", "/favorites", "/recordings"]);

export function rememberablePath(path: string): string | null {
  return REMEMBERABLE.has(path) ? path : null;
}

export function parseStartScreen(v: unknown): StartScreen {
  return START_SCREENS.includes(v as StartScreen) ? (v as StartScreen) : "home";
}

/** Chemin à ouvrir au lancement, ou null pour rester sur l'accueil. */
export function startPath(screen: StartScreen, last: string | null | undefined): string | null {
  const p = screen === "last" ? (last ? rememberablePath(last) : null) : PATHS[screen];
  return p && p !== "/" ? p : null;
}
