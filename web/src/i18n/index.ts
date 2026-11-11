import { useCallback } from "react";
import type en from "./locales/en.json";
import { LANGS, COMMUNITY_LANGS, SOURCE_LANG, dirOf, detectLang, localeOf, type Lang } from "./lang";
import { usePrefs } from "@/state/prefs";

export { LANGS, COMMUNITY_LANGS, dirOf, detectLang, localeOf };
export type { Lang };

// Toutes les langues sont chargées depuis locales/<code>.json : l'anglais est la source des clés.
type Dict = Record<string, string>;
export const resources: Record<string, Dict> = Object.fromEntries(
  Object.entries(import.meta.glob<Dict>("./locales/*.json", { eager: true, import: "default" })).map(([p, d]) => [p.replace(/^.*\/(.+)\.json$/, "$1"), d]),
);

/** Suffixes de pluriel i18next (catégories d'Intl.PluralRules). */
export const PLURAL_SUFFIXES = ["zero", "one", "two", "few", "many", "other"] as const;
type PluralSuffix = (typeof PLURAL_SUFFIXES)[number];

// Clés utilisables dans le code : celles de en.json, formes de pluriel ramenées à leur clé de base.
type BaseKey<K> = K extends `${infer B}_${PluralSuffix}` ? B : K;
export type Key = BaseKey<keyof typeof en>;
export type TFn = (key: Key, vars?: Record<string, string | number>) => string;

const rules = new Map<Lang, Intl.PluralRules>();
function pluralCategory(lang: Lang, n: number): PluralSuffix {
  let r = rules.get(lang);
  if (!r) rules.set(lang, (r = new Intl.PluralRules(localeOf(lang))));
  return r.select(n) as PluralSuffix;
}

function lookup(lang: Lang, key: string, count: number | null): string | undefined {
  const d = resources[lang];
  if (!d) return undefined;
  if (count !== null) return d[`${key}_${pluralCategory(lang, count)}`] ?? d[`${key}_other`] ?? d[key];
  return d[key];
}

/** Repli : langue choisie, puis anglais, puis la clé. Une variable numérique `count` choisit la forme de pluriel. */
export function translate(lang: Lang, key: Key, vars?: Record<string, string | number>): string {
  const count = typeof vars?.count === "number" && Number.isFinite(vars.count) ? vars.count : null;
  let s = lookup(lang, key, count) ?? (lang === SOURCE_LANG ? undefined : lookup(SOURCE_LANG, key, count)) ?? key;
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.split(`{${k}}`).join(String(v));
  return s;
}

export function useT(): TFn {
  const lang = usePrefs((s) => s.lang);
  return useCallback((key, vars) => translate(lang, key, vars), [lang]);
}
