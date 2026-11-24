import { describe, it, expect } from "vitest";
import { call, newAccount, pairDevice, addProvider, bearer, freshIp } from "./helpers.js";
import { editProvider, editableFields, splitXtreamLink } from "../src/store.js";
import { SITE_DICT, SITE_LANGS } from "../src/i18n/site.js";

const xt = { kind: "XTREAM", name: "Démo", url: "http://iptv.example.test", username: "demo", password: "s3cret" };
const m3u = { kind: "M3U", name: "Liste", url: "http://lists.example.test/get.php?username=u&password=p" };
const cfg = async (dev) => (await call("/api/config", { ip: freshIp(), headers: bearer(dev.token) })).json();
const post = (acct, path, form, csrf = acct.csrf) => call(path, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf, ...form } });
const f = (o) => ({ get: (k) => (o[k] === undefined ? null : o[k]) });

async function setup(provider = xt) {
  const acct = await newAccount();
  await addProvider(acct, provider);
  const tv = await pairDevice(acct, "TV");
  const c = await cfg(tv);
  return { acct, tv, id: c.providers[0].id };
}

describe("editProvider : règles", () => {
  const p = { id: "ab12cd34", kind: "XTREAM", name: "A", url: "http://a.test", username: "u", password: "p", updatedAt: 5, assign: ["d1"] };
  it("mêmes valeurs : rien ne change", () => {
    expect(editProvider(p, f({ kind: "XTREAM", name: "A", url: "http://a.test", username: "u", password: "p" }), 100)).toMatchObject({ changed: false, replaced: false });
  });
  it("même type : modifié en place (identifiant gardé)", () => {
    const r = editProvider(p, f({ kind: "XTREAM", name: "B", url: "https://b.test:8080", username: "v", password: "q" }), 100);
    expect(r).toMatchObject({ changed: true, replaced: false });
    expect(r.provider).toMatchObject({ id: "ab12cd34", name: "B", url: "https://b.test:8080", username: "v", password: "q", updatedAt: 100, assign: ["d1"] });
  });
  it("autre type : nouvelle source (nouvel identifiant), nom et affectations repris", () => {
    const r = editProvider({ ...p, kind: "M3U", username: "", password: "" }, f({ kind: "XTREAM", name: "A", url: "http://x.test", username: "u2", password: "p2" }), 100);
    expect(r.replaced).toBe(true);
    expect(r.provider.id).not.toBe("ab12cd34");
    expect(r.provider).toMatchObject({ kind: "XTREAM", name: "A", url: "http://x.test", username: "u2", password: "p2", assign: ["d1"] });
  });
  it("validé comme à l'ajout ; revendeur et serveurs multimédias exclus", () => {
    expect(editProvider(p, f({ kind: "XTREAM", url: "ftp://x", username: "u", password: "p" })).error).toBe("url");
    expect(editProvider(p, f({ kind: "XTREAM", url: "http://x.test", username: "", password: "" })).error).toBe("creds");
    expect(editProvider(p, f({ kind: "STALKER", url: "http://x.test", mac: "zz" })).error).toBe("mac");
    expect(editProvider(p, f({ kind: "PLEX", url: "http://x.test" })).error).toBe("kind");
    expect(editProvider({ ...p, managed: "reseller" }, f({ kind: "XTREAM", url: "http://b.test", username: "u", password: "p" })).error).toBe("edit");
    expect(editProvider({ ...p, kind: "JELLYFIN" }, f({ url: "http://b.test" })).error).toBe("edit");
    expect(editableFields({ ...p, managed: "reseller" })).toBeNull();
  });
});

describe("passage M3U → Xtream : lien découpé", () => {
  it("splitXtreamLink : serveur, identifiant, mot de passe", () => {
    expect(splitXtreamLink("http://cf.example.test:8080/get.php?username=ab&password=cd&type=m3u_plus&output=ts")).toEqual({ url: "http://cf.example.test:8080", username: "ab", password: "cd" });
    expect(splitXtreamLink("https://x.test/sub/player_api.php?username=a&password=b")).toEqual({ url: "https://x.test/sub", username: "a", password: "b" });
    expect(splitXtreamLink("http://lists.test/a.m3u")).toBeNull();
  });
  it("editProvider : lien complet en Xtream, champs vides → identifiants repris du lien", () => {
    const r = editProvider({ id: "a1", kind: "M3U", name: "S", url: "http://x.test/get.php?username=ab&password=cd" }, f({ kind: "XTREAM", name: "S", url: "http://x.test/get.php?username=ab&password=cd&type=m3u_plus", username: "", password: "" }), 100);
    expect(r.provider).toMatchObject({ kind: "XTREAM", url: "http://x.test", username: "ab", password: "cd" });
  });
});

describe("tableau de bord : modifier une source", () => {
  it("valeurs actuelles lues à la demande (POST + CSRF), jamais dans la page", async () => {
    const { acct, id } = await setup(m3u);
    const r = await post(acct, `/providers/${id}/editfields`, {});
    expect(await r.json()).toMatchObject({ kind: "M3U", name: "Liste", url: m3u.url });
    expect((await post(acct, `/providers/${id}/editfields`, {}, "mauvais")).status).toBe(403);
    for (const l of SITE_LANGS) {
      const html = await (await call(`/?l=${l}`, { cookie: acct.cookie, ip: acct.ip })).text();
      expect(html, l).toContain(`action="/providers/${id}/edit"`);
      expect(html, l).toContain(SITE_DICT[l].dash.srcs.editKind);
      expect(html, l).not.toContain("password=p");
      for (const [, code] of html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)) expect(() => new Function(code), l).not.toThrow();
    }
  });
  it("même type : nouvelle URL envoyée aux appareils, même identifiant", async () => {
    const { acct, tv, id } = await setup();
    const r = await post(acct, `/providers/${id}/edit`, { kind: "XTREAM", name: "Démo", url: "http://new.example.test:8080", username: "demo", password: "s3cret" });
    expect(r.headers.get("location")).toContain("m=edited");
    expect((await cfg(tv)).providers[0]).toMatchObject({ id, url: "http://new.example.test:8080", username: "demo" });
  });
  it("M3U → Xtream : l'ancienne est retirée des appareils, la nouvelle ajoutée", async () => {
    const { acct, tv, id } = await setup(m3u);
    await post(acct, `/providers/${id}/edit`, { kind: "XTREAM", name: "Liste", url: "http://xt.example.test", username: "u", password: "p" });
    const c = await cfg(tv);
    expect(c.providers).toHaveLength(1);
    expect(c.providers[0]).toMatchObject({ kind: "XTREAM", url: "http://xt.example.test", username: "u" });
    expect(c.providers[0].id).not.toBe(id);
    expect(JSON.stringify(c.removed || [])).toContain(id);
  });
  it("URL invalide : erreur, source inchangée", async () => {
    const { acct, tv, id } = await setup();
    const r = await post(acct, `/providers/${id}/edit`, { kind: "XTREAM", url: "javascript:alert(1)", username: "demo", password: "s3cret" });
    expect(r.headers.get("location")).toContain("e=url");
    expect((await cfg(tv)).providers[0].url).toBe("http://iptv.example.test");
  });
});
