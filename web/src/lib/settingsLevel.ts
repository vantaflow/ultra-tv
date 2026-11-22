// Niveaux de réglages (Essentiel / Standard / Expert) : chaque réglage a un palier ; le niveau choisi masque les paliers
// au-dessus de lui. Rien n'est supprimé, et un réglage déjà modifié (valeur différente du défaut) reste TOUJOURS affiché :
// aucun utilisateur ne perd l'accès à un réglage qu'il a changé.

export type SettingsLevel = "essential" | "standard" | "expert";
export const SETTINGS_LEVELS: readonly SettingsLevel[] = ["essential", "standard", "expert"];
export const DEFAULT_SETTINGS_LEVEL: SettingsLevel = "standard";

/** Palier d'un réglage : « basic » (toujours), « tech » (masqué en Essentiel), « expert » (affiché en Expert seulement). */
export type SettingTier = "basic" | "tech" | "expert";

export function parseSettingsLevel(v: unknown): SettingsLevel {
  return (SETTINGS_LEVELS as readonly unknown[]).includes(v) ? (v as SettingsLevel) : DEFAULT_SETTINGS_LEVEL;
}

/**
 * Niveau au démarrage. Choix enregistré : toujours conservé. Jamais choisi : Expert si l'appli avait déjà des préférences
 * enregistrées (utilisateur existant : rien ne disparaît à la mise à jour), Standard pour une installation neuve.
 */
export function initialSettingsLevel(stored: unknown, hasSavedPrefs: boolean): SettingsLevel {
  if ((SETTINGS_LEVELS as readonly unknown[]).includes(stored)) return stored as SettingsLevel;
  return hasSavedPrefs ? "expert" : DEFAULT_SETTINGS_LEVEL;
}

export function levelShows(level: SettingsLevel, tier: SettingTier, modified = false): boolean {
  if (modified || tier === "basic") return true;
  if (tier === "tech") return level !== "essential";
  return level === "expert";
}
