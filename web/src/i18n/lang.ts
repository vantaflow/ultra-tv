export type Lang = "fr" | "en" | "es" | "ar";
export const LANGS: { code: Lang; label: string }[] = [
  { code: "fr", label: "Français" },
  { code: "en", label: "English" },
  { code: "es", label: "Español" },
  { code: "ar", label: "العربية" },
];

export function detectLang(): Lang {
  const n = (typeof navigator !== "undefined" ? navigator.language : "fr").slice(0, 2).toLowerCase();
  return (["fr", "en", "es", "ar"] as const).find((l) => l === n) ?? "en";
}

export const dirOf = (l: Lang) => (l === "ar" ? "rtl" : "ltr");
export const localeOf = (l: Lang) => ({ fr: "fr-FR", en: "en-GB", es: "es-ES", ar: "ar-MA" })[l];
