// Langues de l'interface. Ajouter une langue = un fichier locales/<code>.json + une entrée ici.
export const LANGS = [
  { code: "fr", label: "Français", dir: "ltr", locale: "fr-FR" },
  { code: "en", label: "English", dir: "ltr", locale: "en-GB" },
  { code: "es", label: "Español", dir: "ltr", locale: "es-ES" },
  { code: "ar", label: "العربية", dir: "rtl", locale: "ar-MA" },
  { code: "pl", label: "Polski", dir: "ltr", locale: "pl-PL" },
] as const;

export type Lang = (typeof LANGS)[number]["code"];

/** Langues communautaires : traductions incomplètes tolérées (repli sur l'anglais). Polonais : premier jet, à relire via Weblate. */
export const COMMUNITY_LANGS: readonly Lang[] = ["pl"];

/** Langue source : référence des clés, et repli quand une clé manque. */
export const SOURCE_LANG: Lang = "en";

const info = (l: Lang) => LANGS.find((x) => x.code === l) ?? LANGS[0];

export function detectLang(): Lang {
  const n = (typeof navigator !== "undefined" ? navigator.language : "en").slice(0, 2).toLowerCase();
  return LANGS.find((l) => l.code === n)?.code ?? "en";
}

export const dirOf = (l: Lang): "ltr" | "rtl" => info(l).dir;
export const localeOf = (l: Lang): string => info(l).locale;
