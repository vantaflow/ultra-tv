import { describe, expect, it } from "vitest";
import { call, newAccount } from "./helpers.js";
import { SITE_DICT, SITE_LANGS, SITE_DIR, TOO_MANY_TEXT, pickSiteLang } from "../src/i18n/site.js";

const htmlTag = (html) => html.match(/<html lang="([^"]+)" dir="([^"]+)">/);

/** Aplatit un dictionnaire : chemin -> type (string/function/array), pour comparer les clés entre langues. */
function shape(o, pre = "", out = {}) {
  for (const [k, v] of Object.entries(o)) {
    const key = pre + k;
    if (v && typeof v === "object" && !Array.isArray(v)) shape(v, key + ".", out);
    else out[key] = Array.isArray(v) ? "array" : typeof v;
  }
  return out;
}
const strings = (o, pre = "", out = {}) => {
  for (const [k, v] of Object.entries(o)) {
    if (v && typeof v === "object" && !Array.isArray(v)) strings(v, pre + k + ".", out);
    else if (typeof v === "string") out[pre + k] = v;
  }
  return out;
};
const vars = (s) => (s.match(/\{[a-zA-Z0-9_]+\}/g) || []).sort().join(",");

describe("dictionnaire du site", () => {
  it("5 langues, mêmes clés et mêmes types, aucune valeur vide", () => {
    expect(Object.keys(SITE_DICT).sort()).toEqual([...SITE_LANGS].sort());
    const ref = shape(SITE_DICT.en);
    for (const l of SITE_LANGS) {
      expect(shape(SITE_DICT[l])).toEqual(ref);
      for (const [k, v] of Object.entries(strings(SITE_DICT[l]))) expect(v.trim(), `${l}.${k}`).not.toBe("");
    }
  });

  it("variables {…} identiques dans toutes les langues", () => {
    const ref = strings(SITE_DICT.en);
    for (const l of SITE_LANGS) {
      const s = strings(SITE_DICT[l]);
      for (const k of Object.keys(ref)) expect(vars(s[k]), `${l}.${k}`).toBe(vars(ref[k]));
    }
  });

  it("arabe : traduit pour de bon (pas une copie de l'anglais) et en RTL", () => {
    expect(SITE_DIR.ar).toBe("rtl");
    for (const l of ["fr", "es", "ar", "pl"]) expect(SITE_DICT[l].login.submit).not.toBe(SITE_DICT.en.login.submit);
    expect(TOO_MANY_TEXT.ar).toMatch(/[؀-ۿ]/);
  });

  it("résolution : ?l= > cookie > Accept-Language > anglais", () => {
    expect(pickSiteLang({ param: "pl", cookie: "fr", acceptLanguage: "es" })).toBe("pl");
    expect(pickSiteLang({ cookie: "fr", acceptLanguage: "es" })).toBe("fr");
    expect(pickSiteLang({ acceptLanguage: "es-ES,en;q=0.5" })).toBe("es");
    expect(pickSiteLang({ param: "xx" })).toBe("en");
    expect(pickSiteLang({})).toBe("en");
  });
});

describe("pages publiques traduites", () => {
  it.each(SITE_LANGS)("/login et /signup, /apk, /offline en %s : lang, dir, textes", async (l) => {
    for (const [path, key] of [["/login", "login"], ["/signup", "signup"], ["/apk", "apk"], ["/offline", "offline"]]) {
      const res = await call(`${path}?l=${l}`);
      expect(res.status).toBe(200);
      const html = await res.text();
      expect(htmlTag(html).slice(1), path).toEqual([l, SITE_DIR[l]]);
      expect(html).toContain(SITE_DICT[l][key].h1.replace(/&/g, "&amp;"));
      // sélecteur de langue dans le pied de page : 5 liens, langue courante marquée
      for (const x of SITE_LANGS) expect(html).toContain(`href="${path}?l=${x}"`);
      expect(html).toMatch(new RegExp(`data-lang="${l}"[^>]*aria-current="true"`));
      expect(html).toContain("utv-lang");
    }
  });

  it("langue par défaut anglais, Accept-Language et cookie respectés", async () => {
    expect(htmlTag(await (await call("/login")).text()).slice(1)).toEqual(["en", "ltr"]);
    expect(htmlTag(await (await call("/login", { headers: { "accept-language": "pl-PL,pl;q=0.9" } })).text())[1]).toBe("pl");
    expect(htmlTag(await (await call("/login", { headers: { "accept-language": "pl" }, cookie: "utv-lang=ar" })).text())[1]).toBe("ar");
    expect(htmlTag(await (await call("/login?l=es", { cookie: "utv-lang=ar" })).text())[1]).toBe("es");
  });

  it("menu du haut traduit (arabe) et message d'erreur de connexion traduit", async () => {
    const html = await (await call("/login?l=ar&e=pw")).text();
    expect(html).toContain(SITE_DICT.ar.nav.public[2][1]);
    expect(html).toContain(SITE_DICT.ar.authErr.pw);
    const en = await (await call("/login?e=pw")).text();
    expect(en).toContain(SITE_DICT.en.authErr.pw);
  });

  it("le lien « créer un compte » garde la langue choisie", async () => {
    const html = await (await call("/login?l=es")).text();
    expect(html).toContain('href="/signup?l=es"');
  });

  it("CSP à nonce conservée : tout <script> porte le nonce, aucun gestionnaire en ligne", async () => {
    const res = await call("/login?l=pl");
    const html = await res.text();
    const nonce = res.headers.get("content-security-policy").match(/script-src 'nonce-([^']+)'/)[1];
    for (const s of html.match(/<script[^>]*>/g)) expect(s).toContain(`nonce="${nonce}"`);
    expect(html).not.toMatch(/\son[a-z]+=/);
  });

  it("le paramètre l ne s'injecte pas dans la page", async () => {
    const html = await (await call(`/login?l=%22%3E%3Cscript%3Ealert(1)%3C/script%3E`)).text();
    expect(html).not.toContain("<script>alert(1)");
    expect(htmlTag(html)[1]).toBe("en");
  });
});

describe("espace connecté traduit", () => {
  it.each(SITE_LANGS)("tableau de bord et /pair en %s", async (l) => {
    const acct = await newAccount();
    const dash = await (await call(`/?l=${l}`, { cookie: acct.cookie, ip: acct.ip })).text();
    expect(htmlTag(dash).slice(1)).toEqual([l, SITE_DIR[l]]);
    expect(dash).toContain(SITE_DICT[l].dash.devs.emptyTitle);
    expect(dash).toContain(SITE_DICT[l].dash.add.title);
    expect(dash).toContain(SITE_DICT[l].nav.user[1][1]);
    for (const x of SITE_LANGS) expect(dash).toContain(`href="/?l=${x}"`);
    expect(dash).toContain(SITE_DICT[l].dash.clientJs.linkFail);
    const pair = await (await call(`/pair?l=${l}`, { cookie: acct.cookie, ip: acct.ip })).text();
    expect(htmlTag(pair).slice(1)).toEqual([l, SITE_DIR[l]]);
    expect(pair).toContain(SITE_DICT[l].pair.h1New);
    expect(pair).toContain(SITE_DICT[l].pair.scanBtn);
  });

  it("noms de champs de formulaire inchangés", async () => {
    const acct = await newAccount();
    const dash = await (await call("/?l=ar", { cookie: acct.cookie, ip: acct.ip })).text();
    for (const n of ['name="csrf"', 'name="kind"', 'name="url"', 'name="username"', 'name="password"', 'name="mac"', 'name="current"', 'action="/providers"', 'action="/password"', 'action="/account/delete"']) expect(dash).toContain(n);
  });
});
