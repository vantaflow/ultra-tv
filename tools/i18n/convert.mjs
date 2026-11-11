// Conversion ponctuelle des dictionnaires TypeScript (fr/en/es/ar) vers des JSON au format i18next.
// Usage (depuis la racine, avec les anciens fr.ts/en.ts/es.ts/ar.ts récupérés via git) :
//   node tools/i18n/convert.mjs <dossier_des_anciens_ts> web/src/i18n
// Produit locales/{en,fr,es,ar}.json (clés de pluriel `_one`, `_few`... incluses) et golden.json
// (photo des anciennes valeurs, utilisée par le test de non-régression).
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const [src, out] = process.argv.slice(2);
const load = (l) => {
  const t = readFileSync(join(src, `${l}.ts`), "utf8");
  const body = t.slice(t.indexOf("{", t.indexOf("= ")), t.lastIndexOf("}") + 1);
  return new Function(`return (${body})`)();
};
const old = Object.fromEntries(["fr", "en", "es", "ar"].map((l) => [l, load(l)]));
mkdirSync(join(out, "locales"), { recursive: true });
writeFileSync(join(out, "golden.json"), JSON.stringify(old, null, 1) + "\n");

// Formes de pluriel par clé et par langue (catégories Intl.PluralRules).
const P = {
  "common.seasons": {
    en: { one: "{count} season", other: "{count} seasons" },
    fr: { one: "{count} saison", many: "{count} de saisons", other: "{count} saisons" },
    es: { one: "{count} temporada", many: "{count} de temporadas", other: "{count} temporadas" },
    ar: { zero: "{count} موسم", one: "موسم واحد", two: "موسمان", few: "{count} مواسم", many: "{count} موسمًا", other: "{count} موسم" },
  },
  "common.items": {
    en: { one: "{n} item", other: "{n} items" },
    fr: { one: "{n} élément", many: "{n} d’éléments", other: "{n} éléments" },
    es: { one: "{n} elemento", many: "{n} de elementos", other: "{n} elementos" },
    ar: { zero: "{n} عنصر", one: "عنصر واحد", two: "عنصران", few: "{n} عناصر", many: "{n} عنصرًا", other: "{n} عنصر" },
  },
  "common.channelsCount": {
    en: { one: "{count} channel", other: "{count} channels" },
    fr: { one: "{count} chaîne", many: "{count} de chaînes", other: "{count} chaînes" },
    es: { one: "{count} canal", many: "{count} de canales", other: "{count} canales" },
    ar: { zero: "{count} قناة", one: "قناة واحدة", two: "قناتان", few: "{count} قنوات", many: "{count} قناة", other: "{count} قناة" },
  },
  "common.sections": {
    en: { one: "{count} section", other: "{count} sections" },
    fr: { one: "{count} section", many: "{count} de sections", other: "{count} sections" },
    es: { one: "{count} sección", many: "{count} de secciones", other: "{count} secciones" },
    ar: { zero: "{count} قسم", one: "قسم واحد", two: "قسمان", few: "{count} أقسام", many: "{count} قسمًا", other: "{count} قسم" },
  },
  "set.catsPending": {
    en: { one: "{count} change to apply", other: "{count} changes to apply" },
    fr: { one: "{count} modification à appliquer", many: "{count} de modifications à appliquer", other: "{count} modifications à appliquer" },
    es: { one: "{count} cambio por aplicar", many: "{count} de cambios por aplicar", other: "{count} cambios por aplicar" },
    ar: { zero: "{count} تغيير بانتظار التطبيق", one: "تغيير واحد بانتظار التطبيق", two: "تغييران بانتظار التطبيق", few: "{count} تغييرات بانتظار التطبيق", many: "{count} تغييرًا بانتظار التطبيق", other: "{count} تغيير بانتظار التطبيق" },
  },
  "lang.summary": {
    en: { one: "{count} language · {c} categories", other: "{count} languages · {c} categories" },
    fr: { one: "{count} langue · {c} catégories", many: "{count} de langues · {c} catégories", other: "{count} langues · {c} catégories" },
    es: { one: "{count} idioma · {c} categorías", many: "{count} de idiomas · {c} categorías", other: "{count} idiomas · {c} categorías" },
    ar: { zero: "{count} لغة · {c} فئة", one: "لغة واحدة · {c} فئة", two: "لغتان · {c} فئة", few: "{count} لغات · {c} فئة", many: "{count} لغة · {c} فئة", other: "{count} لغة · {c} فئة" },
  },
  "lang.cats": {
    en: { one: "{count} category", other: "{count} categories" },
    fr: { one: "{count} catégorie", many: "{count} de catégories", other: "{count} catégories" },
    es: { one: "{count} categoría", many: "{count} de categorías", other: "{count} categorías" },
    ar: { zero: "{count} فئة", one: "فئة واحدة", two: "فئتان", few: "{count} فئات", many: "{count} فئة", other: "{count} فئة" },
  },
  "cloud.devicesCount": {
    en: { one: "{count} device on the account", other: "{count} devices on the account" },
    fr: { one: "{count} appareil sur le compte", many: "{count} d’appareils sur le compte", other: "{count} appareils sur le compte" },
    es: { one: "{count} dispositivo en la cuenta", many: "{count} de dispositivos en la cuenta", other: "{count} dispositivos en la cuenta" },
    ar: { zero: "{count} جهاز على الحساب", one: "جهاز واحد على الحساب", two: "جهازان على الحساب", few: "{count} أجهزة على الحساب", many: "{count} جهازًا على الحساب", other: "{count} جهاز على الحساب" },
  },
  "cloud.badgeShared": {
    en: { one: "Cloud · shared with {count} device", other: "Cloud · shared with {count} devices" },
    fr: { one: "Cloud · partagée avec {count} appareil", many: "Cloud · partagée avec {count} d’appareils", other: "Cloud · partagée avec {count} appareils" },
    es: { one: "Nube · compartida con {count} dispositivo", many: "Nube · compartida con {count} de dispositivos", other: "Nube · compartida con {count} dispositivos" },
    ar: { zero: "السحابة · مشارك مع {count} جهاز", one: "السحابة · مشارك مع جهاز واحد", two: "السحابة · مشارك مع جهازين", few: "السحابة · مشارك مع {count} أجهزة", many: "السحابة · مشارك مع {count} جهازًا", other: "السحابة · مشارك مع {count} جهاز" },
  },
};

for (const l of ["en", "fr", "es", "ar"]) {
  const o = {};
  for (const [k] of Object.entries(old.en)) {
    if (P[k]) for (const [cat, s] of Object.entries(P[k][l])) o[`${k}_${cat}`] = s;
    else o[k] = old[l][k];
  }
  writeFileSync(join(out, "locales", `${l}.json`), JSON.stringify(o, null, 2) + "\n");
}
