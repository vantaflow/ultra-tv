// Alertes : traduction automatique des langues laissées vides, par Workers AI (quota gratuit quotidien de Cloudflare) :
// modèle de langage Llama 3.3 70B d'abord (formulation naturelle), m2m100 en secours. Jamais bloquant : sans liaison AI,
// ou en cas d'échec, l'alerte garde ses textes saisis et les appareils sans traduction affichent le texte par défaut.

export const AUTO_TR_MODEL = "@cf/meta/m2m100-1.2b";
/** Traduction principale : modèle de langage (même quota gratuit), plus naturel que m2m100 qui reste en secours. */
export const AUTO_TR_LLM = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";

/** Nom anglais des langues, pour la consigne du modèle de langage. */
const LANG_NAME = { en: "English", fr: "French", es: "Spanish", ar: "Arabic", pl: "Polish", pt: "Portuguese", ro: "Romanian", nb: "Norwegian Bokmål", cs: "Czech", fi: "Finnish", da: "Danish", el: "Greek", th: "Thai", vi: "Vietnamese", tr: "Turkish" };

const TR_SCHEMA = { type: "object", properties: { title: { type: "string" }, message: { type: "string" } }, required: ["title", "message"] };

/** Titre et message traduits par le modèle de langage, ou null (réponse absente, illisible ou vide). */
export async function llmTranslate(ai, a, source, target) {
  const from = LANG_NAME[source] || "English", to = LANG_NAME[target];
  if (!to) return null;
  const r = await ai.run(AUTO_TR_LLM, {
    messages: [
      { role: "system", content: `You translate short in-app notifications of the IPTV player "Ultra TV" from ${from} to ${to}. Natural, concise wording a native speaker would use in an app. Keep "Ultra TV", URLs, e-mail addresses and numbers unchanged; translate menu paths (e.g. "Settings › About") as the app would. Reply with JSON only: {"title": "...", "message": "..."}.` },
      { role: "user", content: JSON.stringify({ title: a.title || "", message: a.message }) },
    ],
    response_format: { type: "json_schema", json_schema: TR_SCHEMA },
    max_tokens: 800,
    temperature: 0.2,
  });
  let v = r?.response;
  if (typeof v === "string") { try { v = JSON.parse(v.slice(v.indexOf("{"), v.lastIndexOf("}") + 1)); } catch { return null; } }
  const clean = (t) => String(t || "").replace(/[\u0000-\u001f\u007f]/g, " ").trim();
  const message = clean(v?.message);
  if (!message) return null;
  return { title: a.title ? clean(v?.title) : "", message };
}

/** Code de langue de l'alerte → code attendu par m2m100 (norvégien bokmål = « no »). */
// « en » : cible quand le texte est saisi dans une autre langue (sinon les appareils en anglais recevaient le texte source).
const M2M = { en: "en", fr: "fr", es: "es", ar: "ar", pl: "pl", pt: "pt", ro: "ro", nb: "no", cs: "cs", fi: "fi", da: "da", el: "el", th: "th", vi: "vi", tr: "tr" };

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
      // Modèle de langage d'abord ; en cas d'échec (quota, réponse illisible), m2m100.
      const llm = await llmTranslate(ai, a, source, l).catch(() => null);
      const [title, message] = llm ? [llm.title, llm.message] : await Promise.all([one(a.title, M2M[l]), one(a.message, M2M[l])]);
      if (message) { out[l] = { title: title.slice(0, TITLE_MAX), message: message.slice(0, MESSAGE_MAX), auto: true }; done.push(l); }
    } catch { /* langue ignorée : texte par défaut sur ces appareils */ }
  }));
  return { i18n: Object.keys(out).length ? out : null, done: done.sort() };
}
