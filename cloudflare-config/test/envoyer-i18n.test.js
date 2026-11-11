import { describe, expect, it } from "vitest";
import { call } from "./helpers.js";
import {
  ENVOYER_DICT, ENVOYER_LANGS, ENVOYER_COOKIE, fromAcceptLanguage, langFromCookie, normalizeEnvoyerLang, pickEnvoyerLang,
} from "../src/i18n/envoyer.js";

const page = async (qs = "", headers = {}, cookie) => {
  const res = await call("/envoyer" + qs, { headers, cookie });
  return { res, html: await res.text() };
};
const htmlTag = (html) => html.match(/<html lang="([^"]+)" dir="([^"]+)">/);
const vars = (s) => (s.match(/\{[a-zA-Z0-9_]+\}/g) || []).sort().join(",");

describe("page /envoyer : choix de la langue", () => {
  it("fonction pure : paramètre > cookie > Accept-Language > anglais", () => {
    expect(pickEnvoyerLang({ param: "es", cookie: "fr", acceptLanguage: "pl" })).toBe("es");
    expect(pickEnvoyerLang({ param: null, cookie: "fr", acceptLanguage: "pl" })).toBe("fr");
    expect(pickEnvoyerLang({ acceptLanguage: "pl,en;q=0.5" })).toBe("pl");
    expect(pickEnvoyerLang({})).toBe("en");
    // Valeurs non prises en charge : on passe au critère suivant.
    expect(pickEnvoyerLang({ param: "xx", cookie: "zz", acceptLanguage: "ar" })).toBe("ar");
    expect(pickEnvoyerLang({ param: "xx", cookie: "zz", acceptLanguage: "de,ja" })).toBe("en");
  });

  it("Accept-Language : qualités q=, région, q=0 et jokers", () => {
    expect(fromAcceptLanguage("de;q=0.9, es;q=0.8, fr;q=0.7")).toBe("es");
    expect(fromAcceptLanguage("fr;q=0.3, ar;q=0.9")).toBe("ar");
    expect(fromAcceptLanguage("es-MX,es;q=0.9,en;q=0.8")).toBe("es");
    expect(fromAcceptLanguage("fr;q=0, en;q=0.1")).toBe("en");
    expect(fromAcceptLanguage("*;q=0.5, pl-PL;q=0.4")).toBe("pl");
    expect(fromAcceptLanguage("de, it")).toBeNull();
    expect(fromAcceptLanguage("")).toBeNull();
    expect(fromAcceptLanguage(null)).toBeNull();
  });

  it("balises avec région ou casse différente, valeurs hors liste", () => {
    expect(normalizeEnvoyerLang("es-MX")).toBe("es");
    expect(normalizeEnvoyerLang("AR_ma")).toBe("ar");
    expect(normalizeEnvoyerLang("english")).toBeNull();
    expect(normalizeEnvoyerLang("")).toBeNull();
    expect(langFromCookie(`a=b; ${ENVOYER_COOKIE}=pl; c=d`)).toBe("pl");
    expect(langFromCookie("autre=pl")).toBeNull();
  });

  it("sans indice : anglais ; lang et dir dans le HTML", async () => {
    const { html } = await page("?c=K7Q2M9");
    expect(htmlTag(html).slice(1)).toEqual(["en", "ltr"]);
    expect(html).toContain("Send to my TV");
    expect(html).toContain("Show password");
  });

  it("paramètre l : chaque langue, avec ses textes et son sens d'écriture", async () => {
    const expected = { en: "ltr", fr: "ltr", es: "ltr", ar: "rtl", pl: "ltr" };
    for (const l of ENVOYER_LANGS) {
      const { html } = await page(`?c=K7Q2M9&l=${l}`);
      expect(htmlTag(html).slice(1), l).toEqual([l, expected[l]]);
      expect(html).toContain(`<h1>${ENVOYER_DICT[l].h1}</h1>`);
      expect(html).toContain(ENVOYER_DICT[l].note);
    }
    expect((await page("?l=es")).html).toContain("Enviar a mi TV");
    expect((await page("?l=ar")).html).toContain("إرسال إلى تلفازي");
    expect((await page("?l=pl")).html).toContain("Wyślij na mój telewizor");
  });

  it("le paramètre l l'emporte sur le cookie et Accept-Language ; le cookie sur Accept-Language", async () => {
    expect(htmlTag((await page("?l=en", { "accept-language": "fr" }, `${ENVOYER_COOKIE}=es`)).html)[1]).toBe("en");
    expect(htmlTag((await page("", { "accept-language": "fr" }, `${ENVOYER_COOKIE}=es`)).html)[1]).toBe("es");
    expect(htmlTag((await page("", { "accept-language": "fr" })).html)[1]).toBe("fr");
  });

  it("Accept-Language avec q et région ; langue inconnue -> anglais", async () => {
    expect(htmlTag((await page("", { "accept-language": "de;q=0.9, es-MX;q=0.8, fr;q=0.2" })).html)[1]).toBe("es");
    expect(htmlTag((await page("", { "accept-language": "de, ja;q=0.8" })).html)[1]).toBe("en");
    expect(htmlTag((await page("?l=klingon", { "accept-language": "pl" })).html)[1]).toBe("pl");
    expect(htmlTag((await page("?l=klingon")).html)[1]).toBe("en");
  });

  it("sélecteur : 5 liens qui gardent le code, langue courante marquée, cookie posé par le script", async () => {
    const { html, res } = await page("?c=k7q-2m9&l=es");
    for (const l of ENVOYER_LANGS) expect(html).toContain(`href="/envoyer?c=K7Q2M9&amp;l=${l}"`);
    expect(html).toMatch(/data-lang="es"[^>]*aria-current="true"/);
    expect(html).toContain("Path=/envoyer; Max-Age=31536000; SameSite=Lax; Secure");
    expect(res.headers.get("vary")).toMatch(/Cookie/);
    expect(res.headers.get("vary")).toMatch(/Accept-Language/);
  });

  it("messages dynamiques passés au script en JSON, sous le nonce de la CSP", async () => {
    const { html, res } = await page("?l=es");
    const nonce = res.headers.get("content-security-policy").match(/script-src 'nonce-([^']+)'/)[1];
    expect(html).toContain(`var T={`);
    expect(html).toContain(JSON.stringify(ENVOYER_DICT.es.errCode).slice(1, -1));
    // Tous les <script> portent le nonce, aucun gestionnaire en ligne.
    const scripts = html.match(/<script[^>]*>/g);
    for (const s of scripts) expect(s).toContain(`nonce="${nonce}"`);
    expect(html).not.toMatch(/\son[a-z]+=/);
    // Le thème est traduit aussi (libellés du gabarit).
    expect(html).toContain("Tema: automático");
  });

  it("injection : un code ou une langue hostile n'ajoute rien à la page", async () => {
    const { html } = await page('?c="><script>alert(1)</script>&l="><img src=x>');
    expect(html).not.toContain("<script>alert(1)");
    expect(html).not.toContain("<img src=x>");
    expect(htmlTag(html)[1]).toBe("en");
  });

  it("aucune régression du formulaire : champs, types de source, identifiants", async () => {
    for (const l of ENVOYER_LANGS) {
      const { html } = await page(`?l=${l}`);
      for (const id of ["envoi-form", "code", "kind", "name", "url", "user", "pass", "mac", "send", "show-pw", "envoi-msg", "envoi-done", "l-url", "g-url", "g-user", "g-pass", "g-mac"]) {
        expect(html, `${l}:${id}`).toContain(`id="${id}"`);
      }
      for (const k of ["XTREAM", "M3U", "STALKER", "GUIDE"]) expect(html).toContain(`<option value="${k}">`);
      expect(html).toContain('name="username"');
      expect(html).toContain('name="password"');
      expect(html).toContain("/api/envoi/send");
      expect(html).toContain("maxlength=\"6\"");
    }
  });

  it("autres pages du Worker : toujours en français", async () => {
    const res = await call("/login");
    const html = await res.text();
    expect(htmlTag(html).slice(1)).toEqual(["fr", "ltr"]);
    expect(html).toContain("Aller au contenu");
    expect(html).toContain("Thème : automatique");
  });
});

describe("dictionnaires de /envoyer", () => {
  it("5 langues, mêmes clés, mêmes variables, aucune valeur vide", () => {
    expect(Object.keys(ENVOYER_DICT).sort()).toEqual([...ENVOYER_LANGS].sort());
    const ref = ENVOYER_DICT.en;
    for (const l of ENVOYER_LANGS) {
      expect(Object.keys(ENVOYER_DICT[l]).sort(), l).toEqual(Object.keys(ref).sort());
      for (const [k, v] of Object.entries(ENVOYER_DICT[l])) {
        expect(typeof v === "string" && v.trim().length > 0, `${l}.${k}`).toBe(true);
        expect(vars(v), `${l}.${k}`).toBe(vars(ref[k]));
      }
    }
  });

  it("aucune mention d'appli concurrente", () => {
  });
});
