import { describe, expect, it } from "vitest";
import golden from "./golden.json";
import { COMMUNITY_LANGS, LANGS, PLURAL_SUFFIXES, resources, translate, type Key, type Lang } from "./index";

const SOURCE = resources.en!;
const SUFFIX = new RegExp(`_(${PLURAL_SUFFIXES.join("|")})$`);
const baseOf = (k: string) => k.replace(SUFFIX, "");
// Clés de pluriel : celles dont l'anglais a une forme `_other`.
const PLURAL_BASES = new Set(Object.keys(SOURCE).filter((k) => k.endsWith("_other")).map(baseOf));

const varsOf = (s: string) => [...new Set(s.match(/\{\w+\}/g) ?? [])].sort().join();
// `count` sert parfois seulement à choisir la forme : on l'ignore dans la comparaison des variables.
const varsNoCount = (s: string) => varsOf(s).split(",").filter((v) => v && v !== "{count}").join();
/** Variables d'une clé de base = union des variables de toutes ses formes. */
function baseVars(d: Record<string, string>, base: string): string {
  const forms = Object.keys(d).filter((k) => k === base || (SUFFIX.test(k) && baseOf(k) === base));
  return [...new Set(forms.flatMap((k) => varsNoCount(d[k]!).split(",").filter(Boolean)))].sort().join();
}
const bases = (d: Record<string, string>) => [...new Set(Object.keys(d).map((k) => (PLURAL_BASES.has(baseOf(k)) ? baseOf(k) : k)))];

const OFFICIAL = LANGS.map((l) => l.code).filter((c) => !COMMUNITY_LANGS.includes(c));
const forms = (lang: string) => new Intl.PluralRules(LANGS.find((l) => l.code === lang)!.locale).resolvedOptions().pluralCategories as string[];

describe("i18n : ressources", () => {
  it("chaque langue de LANGS a son JSON", () => {
    for (const l of LANGS) expect(resources[l.code], l.code).toBeDefined();
  });
  it("langues officielles : 100 % des clés de l'anglais", () => {
    for (const lang of OFFICIAL) {
      const d = resources[lang]!;
      const missing = bases(SOURCE).filter((b) => (PLURAL_BASES.has(b) ? !(`${b}_other` in d) : !(b in d)));
      expect(missing, lang).toEqual([]);
    }
  });
  it("aucune clé orpheline (absente de l'anglais), toutes langues", () => {
    const src = new Set(bases(SOURCE));
    for (const l of LANGS) expect(bases(resources[l.code]!).filter((b) => !src.has(b)), l.code).toEqual([]);
  });
  it("mêmes variables {x} que l'anglais, toutes langues", () => {
    for (const l of LANGS) {
      const d = resources[l.code]!;
      for (const b of bases(d)) if (SOURCE[b] !== undefined || PLURAL_BASES.has(b)) expect(baseVars(d, b), `${l.code}:${b}`).toBe(baseVars(SOURCE, b));
    }
  });
  it("pluriels : toutes les formes de la langue sont présentes", () => {
    for (const l of OFFICIAL) {
      const d = resources[l]!;
      for (const b of PLURAL_BASES) for (const c of forms(l)) expect(d[`${b}_${c}`], `${l}:${b}_${c}`).toBeTypeOf("string");
    }
  });
  it("langues communautaires : couverture affichée, échec seulement sur variable ou clé orpheline", () => {
    for (const l of COMMUNITY_LANGS) {
      const d = resources[l]!;
      const total = bases(SOURCE).length;
      const done = bases(d).filter((b) => b in SOURCE || PLURAL_BASES.has(b)).length;
      console.info(`i18n ${l} : ${done}/${total} clés (${Math.round((100 * done) / total)} %)`);
    }
    expect(true).toBe(true);
  });
});

describe("i18n : traduction", () => {
  it("interpole", () => {
    expect(translate("fr", "common.resumeAt", { t: "1:12:40" })).toBe("Reprendre à 1:12:40");
    expect(translate("en", "common.season", { n: 2 })).toBe("Season 2");
  });
  it("choisit la forme de pluriel via count", () => {
    expect(translate("en", "common.seasons", { count: 1 })).toBe("1 season");
    expect(translate("en", "common.seasons", { count: 3 })).toBe("3 seasons");
    expect(translate("fr", "common.seasons", { count: 0 })).toBe("0 saison");
    expect(translate("fr", "common.seasons", { count: 2 })).toBe("2 saisons");
    expect(translate("ar", "common.seasons", { count: 2 })).toBe("موسمان");
    expect(translate("ar", "common.seasons", { count: 5 })).toBe("5 مواسم");
    expect(translate("ar", "common.seasons", { count: 11 })).toBe("11 موسمًا");
  });
  it("repli sur l'anglais puis sur la clé", () => {
    const es = resources.es!;
    const saved = es["nav.home"];
    delete es["nav.home"];
    try {
      expect(translate("es", "nav.home")).toBe(SOURCE["nav.home"]);
    } finally {
      es["nav.home"] = saved!;
    }
    expect(translate("fr", "clé.inconnue" as Key)).toBe("clé.inconnue");
  });
  it("repli sur l'anglais pour une forme de pluriel manquante", () => {
    const fr = resources.fr!;
    const saved = fr["common.seasons_one"];
    delete fr["common.seasons_one"];
    delete fr["common.seasons_other"];
    delete fr["common.seasons_many"];
    try {
      expect(translate("fr", "common.seasons", { count: 1 })).toBe("1 season");
    } finally {
      fr["common.seasons_one"] = saved!;
      fr["common.seasons_other"] = "{count} saisons";
      fr["common.seasons_many"] = "{count} de saisons";
    }
  });
});

// Non-régression : photo (golden.json) des anciens dictionnaires TypeScript, avant la conversion en JSON.
// Les clés de pluriel sont comparées à count = 11 (forme « other »/« many »), sauf pluriels corrigés listés ici.
const CORRECTED = new Set([
  // « (s) » remplacés par de vrais singuliers/pluriels
  ...["en", "fr", "es"].flatMap((l) => ["set.catsPending", "lang.summary", "cloud.devicesCount"].map((k) => `${l}:${k}`)),
  // arabe : formes duel, pauci et accusatif singulier remplacent la forme unique
  ...["common.seasons", "common.items", "common.sections", "set.catsPending", "cloud.devicesCount", "cloud.badgeShared"].map((k) => `ar:${k}`),
]);
describe("i18n : non-régression (golden)", () => {
  const old = golden as Record<string, Record<string, string>>;
  it.each(LANGS.map((l) => l.code))("%s : mêmes textes qu'avant la conversion", (lang: Lang) => {
    const diffs: string[] = [];
    for (const [k, v] of Object.entries(old[lang]!)) {
      if (PLURAL_BASES.has(k)) {
        const got = translate(lang, k as Key, { count: 11, n: 11, c: 7 });
        const want = v.split("{n}").join("11").split("{c}").join("7");
        if (got !== want && !CORRECTED.has(`${lang}:${k}`)) diffs.push(`${k}: «${got}» ≠ «${want}»`);
      } else if (translate(lang, k as Key) !== v) diffs.push(`${k}: «${translate(lang, k as Key)}» ≠ «${v}»`);
    }
    expect(diffs).toEqual([]);
  });
  it("le golden couvre toutes les clés des langues officielles", () => {
    for (const l of OFFICIAL) expect(Object.keys(old[l]!).length).toBeGreaterThan(400);
  });
});

// Complétude : une valeur copiée telle quelle de l'anglais reste non traduite.
const ARABIC = /[؀-ۿ]/;
// Noms propres, formats et valeurs techniques identiques dans toutes les langues.
const SAME_AS_EN_OK = new Set(["app.name", "src.xtream", "src.serverPh", "src.userAgent", "src.referer", "sync.percent"]);
describe("i18n : complétude arabe", () => {
  const ar = resources.ar!;
  const fr = resources.fr!;
  it("aucune valeur arabe sans caractère arabe, hors liste blanche", () => {
    const bad = Object.entries(ar).filter(([k, v]) => !ARABIC.test(v) && !SAME_AS_EN_OK.has(k)).map(([k]) => k);
    expect(bad).toEqual([]);
  });
  it("aucune valeur arabe identique à l'anglais ou au français, hors liste blanche", () => {
    const bad = Object.keys(ar).filter((k) => !SAME_AS_EN_OK.has(k) && (ar[k] === SOURCE[k] || ar[k] === fr[k]));
    expect(bad).toEqual([]);
  });
  it("aucun texte français ou anglais d'interface ne reste en dur dans les composants", async () => {
    const files = import.meta.glob(["../screens/*.tsx", "../ui/*.tsx", "../player/*.tsx", "../state/sync.ts"], { query: "?raw", import: "default", eager: true }) as Record<string, string>;
    const hard = /(aria-label|title|placeholder|alt)="(?!https?:)[A-Za-zÀ-ÿ]{3}|toast\("|<span className="kbd">[A-Za-zÀ-ÿ]{2,}</;
    const found = Object.entries(files).filter(([f, src]) => !f.includes(".test.") && hard.test(src)).map(([f]) => f);
    expect(found).toEqual([]);
  });
});
