import { useCallback } from "react";
import { ar } from "./ar";
import { en } from "./en";
import { es } from "./es";
import { fr, type Dict, type Key } from "./fr";
import { usePrefs } from "@/state/prefs";

export type Lang = "fr" | "en" | "es" | "ar";
export const LANGS: { code: Lang; label: string }[] = [
  { code: "fr", label: "Français" },
  { code: "en", label: "English" },
  { code: "es", label: "Español" },
  { code: "ar", label: "العربية" },
];
const dicts: Record<Lang, Dict> = { fr, en, es, ar };

export type { Key };
export type TFn = (key: Key, vars?: Record<string, string | number>) => string;

export function translate(lang: Lang, key: Key, vars?: Record<string, string | number>): string {
  let s = dicts[lang][key] ?? fr[key] ?? key;
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.split(`{${k}}`).join(String(v));
  return s;
}

export function detectLang(): Lang {
  const n = (typeof navigator !== "undefined" ? navigator.language : "fr").slice(0, 2).toLowerCase();
  return (["fr", "en", "es", "ar"] as const).find((l) => l === n) ?? "en";
}

export function useT(): TFn {
  const lang = usePrefs((s) => s.lang);
  return useCallback((key, vars) => translate(lang, key, vars), [lang]);
}

export const dirOf = (l: Lang) => (l === "ar" ? "rtl" : "ltr");
export const localeOf = (l: Lang) => ({ fr: "fr-FR", en: "en-GB", es: "es-ES", ar: "ar-MA" })[l];
