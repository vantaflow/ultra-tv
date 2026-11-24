import { describe, it, expect } from "vitest";
import { call, newAccount, pairDevice, addProvider, bearer, freshIp } from "./helpers.js";
import { parsePrefs, parseFavCats, xtreamCategories } from "../src/store.js";
import { SITE_DICT, SITE_LANGS } from "../src/i18n/site.js";

const xt = { kind: "XTREAM", name: "Démo", url: "http://iptv.example.test", username: "demo", password: "s3cret" };
const m3u = { kind: "M3U", name: "Liste", url: "http://lists.example.test/a.m3u" };
const cfg = async (dev) => (await call("/api/config", { ip: freshIp(), headers: bearer(dev.token) })).json();
const putPrefs = (dev, id, json) => call(`/api/device/providers/${id}/prefs`, { method: "PUT", ip: freshIp(), headers: bearer(dev.token), json });
const prefs = (t, extra = {}) => ({ langs: ["fr"], disabled: { live: ["12"], movie: [], series: [] }, updatedAt: t, ...extra });
const postFav = (acct, id, form, csrf = acct.csrf) => call(`/providers/${id}/favcats`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf, ...form } });

async function setup(provider = xt) {
  const acct = await newAccount();
  await addProvider(acct, provider);
  const tv = await pairDevice(acct, "TV");
  const id = (await cfg(tv)).providers[0].id;
  return { acct, tv, id };
}

describe("parsePrefs : catégories favorites", () => {
  it("favCats_absent_resteAbsent", () => {
    expect(parsePrefs(prefs(1000)).prefs.favCats).toBeUndefined();
  });
  it("favCats_valide_dedoublonne_et_complete", () => {
    const r = parsePrefs(prefs(1000, { favCats: { live: ["1", "1", "2"] } }));
    expect(r.prefs.favCats).toEqual({ live: ["1", "2"], movie: [], series: [] });
  });
  it("favCats_invalide_refuse", () => {
    expect(parsePrefs(prefs(1000, { favCats: [] })).error).toBe("favCats");
    expect(parsePrefs(prefs(1000, { favCats: { live: "1" } })).error).toBe("favCats");
    expect(parsePrefs(prefs(1000, { favCats: { live: [""] } })).error).toBe("favCats");
    expect(parseFavCats({ movie: [1] })).toBeNull();
  });
});

describe("favorites : appareil et tableau de bord", () => {
  it("appareilAncien_sansFavCats_conserveLesFavorites", async () => {
    const { acct, tv, id } = await setup();
    await putPrefs(tv, id, prefs(1000));
    await postFav(acct, id, { live: "5" });
    const r = await putPrefs(tv, id, prefs(Date.now() + 5000)); // sans favCats
    expect(r.status).toBe(200);
    expect((await cfg(tv)).providers[0].prefs.favCats.live).toEqual(["5"]);
  });
  it("appareilQuiEnvoieFavCats_lesRemplace", async () => {
    const { tv, id } = await setup();
    await putPrefs(tv, id, prefs(1000, { favCats: { live: ["1"] } }));
    await putPrefs(tv, id, prefs(2000, { favCats: { live: ["2"], series: ["9"] } }));
    expect((await cfg(tv)).providers[0].prefs.favCats).toEqual({ live: ["2"], movie: [], series: ["9"] });
  });
  it("enregistrement_depuisLeTableauDeBord_visibleParLAppareil_autresReglagesIntacts", async () => {
    const { acct, tv, id } = await setup();
    await putPrefs(tv, id, prefs(1000));
    const v1 = (await cfg(tv)).version;
    const r = await call(`/providers/${id}/favcats`, { method: "POST", ip: acct.ip, cookie: acct.cookie, body: `csrf=${encodeURIComponent(acct.csrf)}&live=3&live=4&series=8`, headers: { "content-type": "application/x-www-form-urlencoded" } });
    expect(r.status).toBe(302);
    expect(r.headers.get("location")).toContain("m=favcats_saved");
    const c = await cfg(tv);
    const p = c.providers[0].prefs;
    expect(p.favCats).toEqual({ live: ["3", "4"], movie: [], series: ["8"] });
    expect(p.langs).toEqual(["fr"]);
    expect(p.disabled.live).toEqual(["12"]);
    expect(p.updatedAt).toBeGreaterThan(1000);
    expect(p.by).toBe("dashboard");
    expect(c.version).toBeGreaterThan(v1);
  });
  it("demarquerTout_viderLesFavorites", async () => {
    const { acct, tv, id } = await setup();
    await putPrefs(tv, id, prefs(1000, { favCats: { live: ["1"] } }));
    await postFav(acct, id, {});
    expect((await cfg(tv)).providers[0].prefs.favCats).toEqual({ live: [], movie: [], series: [] });
  });
  it("sansReglagesSynchronises_refuse_etNeCreePasDeReglages", async () => {
    const { acct, tv, id } = await setup();
    const r = await postFav(acct, id, { live: "1" });
    expect(r.headers.get("location")).toContain("e=favcats_noprefs");
    expect((await cfg(tv)).providers[0].prefs).toBeNull();
  });
  it("csrfAbsent_403", async () => {
    const { acct, id } = await setup();
    expect((await postFav(acct, id, {}, "mauvais")).status).toBe(403);
  });
  it("sourceDUnAutreCompte_inchangee", async () => {
    const { tv, id } = await setup();
    await putPrefs(tv, id, prefs(1000));
    const other = await newAccount();
    const r = await postFav(other, id, { live: "1" });
    expect(r.status).toBe(302);
    expect((await cfg(tv)).providers[0].prefs.favCats).toBeUndefined();
  });
  it("identifiantInvalide_refuse", async () => {
    const { acct, tv, id } = await setup();
    await putPrefs(tv, id, prefs(1000));
    const r = await postFav(acct, id, { live: "x".repeat(200) });
    expect(r.headers.get("location")).toContain("e=favcats");
    expect((await cfg(tv)).providers[0].prefs.favCats).toBeUndefined();
  });
});

describe("liste des catégories", () => {
  it("routeCategories_sourceM3U_unsupported_avecFavorites", async () => {
    const { acct, tv, id } = await setup(m3u);
    await putPrefs(tv, id, prefs(1000, { favCats: { live: ["7"] } }));
    const r = await call(`/providers/${id}/categories`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(r.status).toBe(200);
    const j = await r.json();
    expect(j.error).toBe("unsupported");
    expect(j.fav.live).toEqual(["7"]);
    expect(j.hasPrefs).toBe(true);
  });
  it("routeCategories_autreCompte_404_et_csrf", async () => {
    const { acct, id } = await setup();
    const other = await newAccount();
    expect((await call(`/providers/${id}/categories`, { method: "POST", ip: other.ip, cookie: other.cookie, form: { csrf: other.csrf } })).status).toBe(404);
    expect((await call(`/providers/${id}/categories`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: "x" } })).status).toBe(403);
  });
  it("xtreamCategories_litLesTroisTypes", async () => {
    const calls = [];
    const f = async (url) => {
      const a = new URL(url).searchParams.get("action");
      calls.push(a);
      const rows = { get_live_categories: [{ category_id: "1", category_name: "Sport" }, { category_id: 2, category_name: "News" }, { category_id: "1", category_name: "Doublon" }], get_vod_categories: [{ category_id: "9", category_name: "Action" }], get_series_categories: [] };
      return new Response(JSON.stringify(rows[a]));
    };
    const r = await xtreamCategories({ kind: "XTREAM", ...xt }, f);
    expect(calls.sort()).toEqual(["get_live_categories", "get_series_categories", "get_vod_categories"]);
    expect(r.live).toEqual([{ id: "1", name: "Sport" }, { id: "2", name: "News" }]);
    expect(r.movie).toEqual([{ id: "9", name: "Action" }]);
    expect(r.series).toEqual([]);
  });
  it("xtreamCategories_erreurs", async () => {
    expect((await xtreamCategories({ kind: "M3U", url: "http://a.test/x.m3u" })).error).toBe("unsupported");
    expect((await xtreamCategories({ kind: "XTREAM", ...xt }, async () => new Response("nope", { status: 500 }))).error).toBe("unreachable");
  });
});

describe("page du tableau de bord", () => {
  it("sourceXtream_afficheLeVoletFavoritesEtLeCsrf_danslesCinqLangues", async () => {
    const { acct, id } = await setup();
    for (const l of SITE_LANGS) {
      const html = await (await call(`/?l=${l}`, { cookie: acct.cookie, ip: acct.ip })).text();
      const s = SITE_DICT[l].dash.srcs;
      expect(html, l).toContain(`action="/providers/${id}/favcats"`);
      expect(html, l).toContain(s.favCats);
      expect(html, l).toContain(`/categories`);
      // Recherche et onglets (#249) : textes traduits dans le script de la page.
      const c = SITE_DICT[l].dash.clientJs;
      for (const k of ["favAll", "favSearch", "favNoMatch"]) expect(c[k], `${l} ${k}`).toBeTruthy();
      expect(html, l).toContain(JSON.stringify(c.favSearch).slice(1, -1));
      expect(html, l).toContain("favcats-seg");
      // Le script inline de la page reste du JavaScript valide (échappements du gabarit compris).
      for (const [, code] of html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)) expect(() => new Function(code), l).not.toThrow();
    }
  });
  it("sourceM3U_sansVoletFavorites", async () => {
    const { acct, id } = await setup(m3u);
    const html = await (await call("/?l=fr", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(html).not.toContain(`/providers/${id}/favcats`);
  });
  it("messages_traduits", async () => {
    const { acct } = await setup();
    for (const l of SITE_LANGS) {
      const html = await (await call(`/?l=${l}&m=favcats_saved`, { cookie: acct.cookie, ip: acct.ip })).text();
      expect(html, l).toContain(SITE_DICT[l].dashMsg.ok.favcats_saved);
    }
  });
});
