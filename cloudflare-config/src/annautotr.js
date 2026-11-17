// Alertes : traduction automatique des langues laissées vides, par Workers AI (modèle m2m100, inclus dans le quota
// gratuit quotidien de Cloudflare). Jamais bloquant : sans liaison AI, ou en cas d'échec, l'alerte garde ses textes
// saisis et les appareils sans traduction affichent le texte par défaut (anglais).

export const AUTO_TR_MODEL = "@cf/meta/m2m100-1.2b";

/** Code de langue de l'alerte → code attendu par m2m100 (norvégien bokmål = « no »). */
const M2M = { fr: "fr", es: "es", ar: "ar", pl: "pl", pt: "pt", ro: "ro", nb: "no", cs: "cs", fi: "fi", da: "da", el: "el", th: "th", vi: "vi", tr: "tr" };

const TITLE_MAX = 80;
const MESSAGE_MAX = 600;

/**
 * Complète `a.i18n` pour chaque langue de [langs] sans traduction saisie. Retourne { i18n, done } : la carte complète
 * (ou null) et les langues traduites automatiquement (marquées `auto: true`).
 */
export async function autoTranslate(ai, a, langs, source = "en") {
  const out = { ...(a.i18n || {}) };
  const done = [];
  if (!ai || typeof ai.run !== "function" || !a.message) return { i18n: Object.keys(out).length ? out : null, done };
  const one = async (text, target) => {
    if (!text) return "";
    const r = await ai.run(AUTO_TR_MODEL, { text, source_lang: source, target_lang: target });
    return String(r?.translated_text || "").replace(/[\u0000-\u001f\u007f]/g, " ").trim();
  };
  await Promise.all(langs.filter((l) => !out[l] && M2M[l] && M2M[l] !== source).map(async (l) => {
    try {
      const [title, message] = await Promise.all([one(a.title, M2M[l]), one(a.message, M2M[l])]);
      if (message) { out[l] = { title: title.slice(0, TITLE_MAX), message: message.slice(0, MESSAGE_MAX), auto: true }; done.push(l); }
    } catch { /* langue ignorée : texte par défaut sur ces appareils */ }
  }));
  return { i18n: Object.keys(out).length ? out : null, done: done.sort() };
}
