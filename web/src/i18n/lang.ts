// Langues de l'interface. Ajouter une langue = un fichier locales/<code>.json + une entrée ici.
export const LANGS = [
  { code: "fr", label: "Français", dir: "ltr", locale: "fr-FR" },
  { code: "en", label: "English", dir: "ltr", locale: "en-GB" },
  { code: "es", label: "Español", dir: "ltr", locale: "es-ES" },
  { code: "ar", label: "العربية", dir: "rtl", locale: "ar-MA" },
  { code: "pl", label: "Polski", dir: "ltr", locale: "pl-PL" },
  { code: "pt-BR", label: "Português (Brasil)", dir: "ltr", locale: "pt-BR" },
  { code: "ro", label: "Română", dir: "ltr", locale: "ro-RO" },
  { code: "nb", label: "Norsk bokmål", dir: "ltr", locale: "nb-NO" },
  { code: "cs", label: "Čeština", dir: "ltr", locale: "cs-CZ" },
  { code: "fi", label: "Suomi", dir: "ltr", locale: "fi-FI" },
  { code: "da", label: "Dansk", dir: "ltr", locale: "da-DK" },
  { code: "el", label: "Ελληνικά", dir: "ltr", locale: "el-GR" },
  { code: "th", label: "ไทย", dir: "ltr", locale: "th-TH" },
  { code: "vi", label: "Tiếng Việt", dir: "ltr", locale: "vi-VN" },
] as const;

export type Lang = (typeof LANGS)[number]["code"];

/** Langues communautaires : traductions incomplètes tolérées (repli sur l'anglais). Polonais, portugais du Brésil : premier jet, à relire via Weblate. */
export const COMMUNITY_LANGS: readonly Lang[] = ["pl", "pt-BR", "ro", "nb", "cs", "fi", "da", "el", "th", "vi"];

/** Langue source : référence des clés, et repli quand une clé manque. */
export const SOURCE_LANG: Lang = "en";

const info = (l: Lang) => LANGS.find((x) => x.code === l) ?? LANGS[0];

export function detectLang(): Lang {
  const raw = (typeof navigator !== "undefined" ? navigator.language : "en").slice(0, 2).toLowerCase();
  const n = raw === "no" || raw === "nn" ? "nb" : raw; // norvégien générique → bokmål
  // « pt-BR » : reconnu par sa langue (pt, pt-PT, pt-BR).
  return LANGS.find((l) => l.code.slice(0, 2).toLowerCase() === n)?.code ?? "en";
}

export const dirOf = (l: Lang): "ltr" | "rtl" => info(l).dir;
export const localeOf = (l: Lang): string => info(l).locale;
