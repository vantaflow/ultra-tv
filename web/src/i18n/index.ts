import { useCallback } from "react";
import { ar } from "./ar";
import { en } from "./en";
import { es } from "./es";
import { fr, type Dict, type Key } from "./fr";
import { LANGS, dirOf, detectLang, localeOf, type Lang } from "./lang";
import { usePrefs } from "@/state/prefs";

export { LANGS, dirOf, detectLang, localeOf };
export type { Lang };
const dicts: Record<Lang, Dict> = { fr, en, es, ar };

export type { Key };
export type TFn = (key: Key, vars?: Record<string, string | number>) => string;

export function translate(lang: Lang, key: Key, vars?: Record<string, string | number>): string {
  let s = dicts[lang][key] ?? fr[key] ?? key;
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.split(`{${k}}`).join(String(v));
  return s;
}

export function useT(): TFn {
  const lang = usePrefs((s) => s.lang);
  return useCallback((key, vars) => translate(lang, key, vars), [lang]);
}
