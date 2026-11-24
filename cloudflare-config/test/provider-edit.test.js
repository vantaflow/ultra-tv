import { describe, it, expect } from "vitest";
import { call, newAccount, pairDevice, addProvider, bearer, freshIp } from "./helpers.js";
import { editProvider } from "../src/store.js";
import { SITE_DICT, SITE_LANGS } from "../src/i18n/site.js";

const xt = { kind: "XTREAM", name: "Démo", url: "http://iptv.example.test", username: "demo", password: "s3cret" };
const m3u = { kind: "M3U", name: "Liste", url: "http://lists.example.test/get.php?username=u&password=p" };
const cfg = async (dev) => (await call("/api/config", { ip: freshIp(), headers: bearer(dev.token) })).json();
const postEdit = (acct, id, form, csrf = acct.csrf) => call(`/providers/${id}/edit`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf, ...form } });
const f = (o) => ({ get: (k) => (o[k] === undefined ? null : o[k]) });

async function setup(provider = xt) {
  const acct = await newAccount();
  await addProvider(acct, provider);
  const tv = await pairDevice(acct, "TV");
  const c = await cfg(tv);
  return { acct, tv, id: c.providers[0].id };
}

describe("editProvider : règles", () => {
  const p = { id: "ab12cd34", kind: "XTREAM", name: "A", url: "http://a.test", username: "u", password: "p", updatedAt: 5 };
  it("champs vides : rien ne change", () => {
    const r = editProvider(p, f({ name: "", url: "", username: "", password: "" }), 100);
    expect(r).toEqual({ provider: p, changed: false });
  });
  it("nouvelle adresse et identifiants Xtream, horodatage avancé", () => {
    const r = editProvider(p, f({ url: "https://b.test:8080", username: "v", password: "q" }), 100);
    expect(r.changed).toBe(true);
    expect(r.provider).toMatchObject({ id: "ab12cd34", url: "https://b.test:8080", username: "v", password: "q", name: "A", updatedAt: 100 });
  });
  it("adresse invalide refusée ; revendeur et serveurs multimédias exclus", () => {
    expect(editProvider(p, f({ url: "ftp://x" })).error).toBe("url");
    expect(editProvider({ ...p, managed: "reseller" }, f({ url: "http://b.test" })).error).toBe("edit");
    expect(editProvider({ ...p, kind: "JELLYFIN" }, f({ url: "http://b.test" })).error).toBe("edit");
  });
  it("M3U : les champs Xtream sont ignorés", () => {
    const r = editProvider({ ...p, kind: "M3U", username: "", password: "" }, f({ username: "x", password: "y" }));
    expect(r.changed).toBe(false);
  });
});

describe("tableau de bord : modifier une source", () => {
  it("nouvelle URL envoyée aux appareils, identifiant et réglages gardés", async () => {
    const { acct, tv, id } = await setup();
    const v1 = (await cfg(tv)).version;
    const r = await postEdit(acct, id, { name: "", url: "http://new.example.test:8080", username: "", password: "" });
    expect(r.headers.get("location")).toContain("m=edited");
    const c = await cfg(tv);
    expect(c.providers[0]).toMatchObject({ id, url: "http://new.example.test:8080", username: "demo", name: "Démo" });
    if (v1 !== undefined) expect(c.version).not.toBe(v1);
  });
  it("URL invalide : message d'erreur, source inchangée", async () => {
    const { acct, tv, id } = await setup();
    const r = await postEdit(acct, id, { url: "javascript:alert(1)" });
    expect(r.headers.get("location")).toContain("e=url");
    expect((await cfg(tv)).providers[0].url).toBe("http://iptv.example.test");
  });
  it("sans jeton CSRF : refusé", async () => {
    const { acct, tv, id } = await setup();
    await postEdit(acct, id, { url: "http://evil.test" }, "mauvais");
    expect((await cfg(tv)).providers[0].url).toBe("http://iptv.example.test");
  });
  it("la page n'écrit jamais l'URL complète d'une M3U (identifiants) dans le formulaire", async () => {
    const { acct, id } = await setup(m3u);
    for (const l of SITE_LANGS) {
      const html = await (await call(`/?l=${l}`, { cookie: acct.cookie, ip: acct.ip })).text();
      expect(html, l).toContain(`action="/providers/${id}/edit"`);
      expect(html, l).toContain(SITE_DICT[l].dash.srcs.editUrl);
      expect(html, l).not.toContain("password=p");
    }
  });
});
