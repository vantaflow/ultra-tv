import { describe, it, expect } from "vitest";
import { env } from "cloudflare:workers";
import { call, newAccount, pairDevice, bearer, freshIp, addProvider } from "./helpers.js";
import { getAccount, putAccount, setProfileSources } from "../src/store.js";
import { trackingPanel, profilesPanel } from "../src/pages/dashboard.js";

const csrf = '<input type="hidden" name="csrf" value="C"/>';
const radio = (html, v) => html.match(new RegExp(`<input type="radio" name="service" value="${v}"( checked)?/>`));

describe("carte « Service de suivi »", () => {
  it("sans choix ni service relié : Aucun, boutons Connecter", () => {
    const h = trackingPanel({}, csrf, "fr", true, true);
    expect(h).toContain("Service actuel :");
    expect(h).toMatch(/Service actuel : <strong>Aucun<\/strong>/);
    expect(h).toContain("Connecter Trakt");
    expect(h).toContain("Connecter Simkl");
    expect(radio(h, "none")[1]).toBeTruthy();
    expect(h).toContain("Trakt · à relier");
    expect(h.match(/<section/g).length).toBe(1);
  });
  it("Trakt relié sans choix explicite : actif par défaut, avec l'utilisateur", () => {
    const h = trackingPanel({ traktEnc: "x", trakt: { user: "bob<" } }, csrf, "fr", true, true);
    expect(h).toContain("<strong>Trakt</strong> (relié, utilisateur bob&lt;)");
    expect(radio(h, "trakt")[1]).toBeTruthy();
    expect(h).toContain("Déconnecter Trakt");
    expect(h).toContain("Aucun choix enregistré");
  });
  it("Simkl seul relié : Simkl par défaut ; choix explicite non relié : mention à relier", () => {
    expect(trackingPanel({ simklEnc: "y" }, csrf, "en", true, true)).toContain("<strong>Simkl</strong> (linked)");
    const h = trackingPanel({ traktEnc: "x", tracking: "simkl" }, csrf, "fr", true, true);
    expect(h).toContain("<strong>Simkl</strong> (non relié)");
    expect(h).toContain("pas encore relié");
    expect(radio(h, "simkl")[1]).toBeTruthy();
    expect(h).toContain("Simkl · à relier");
  });
  it("choix « none » explicite ; Simkl absent du site : une seule ligne", () => {
    const h = trackingPanel({ traktEnc: "x", tracking: "none" }, csrf, "fr", false, true);
    expect(h).toMatch(/Service actuel : <strong>Aucun<\/strong>/);
    expect(radio(h, "simkl")).toBeNull();
    expect(radio(h, "none")[1]).toBeTruthy();
  });
  it("rendue dans le tableau de bord, une seule carte de suivi", async () => {
    const acct = await newAccount();
    const html = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(html.match(/id="h-track"/g).length).toBe(1);
    expect(html).not.toContain('id="h-trakt"');
  });
});

describe("sources visibles par profil", () => {
  const providers = [{ name: "Alpha" }, { name: "Bêta" }];
  it("liste les profils avec cases cochées selon `s`", () => {
    const h = profilesPanel({ profiles: [{ n: "Papa", on: true, at: 1 }, { n: "Kids", k: true, s: ["Alpha"], on: true, at: 1 }, { n: "Vieux", on: false, at: 1 }] }, providers, csrf, "fr");
    expect(h).toContain("Papa");
    expect(h).not.toContain("Vieux");
    const kids = h.split("<article")[2];
    expect(kids).toMatch(/value="Alpha" checked/);
    expect(kids).not.toMatch(/value="Bêta" checked/);
    expect(h.split("<article")[1]).toMatch(/value="Bêta" checked/);
    expect(profilesPanel({ profiles: [] }, providers, csrf, "fr")).toBe("");
  });
  it("setProfileSources : s posé, at avancé, null = toutes", () => {
    const base = [{ n: "Kids", c: 0, k: true, at: 500, on: true }];
    const a = setProfileSources(base, "kids", ["Alpha", "Alpha"], 100);
    expect(a[0]).toMatchObject({ s: ["Alpha"], at: 501 });
    const b = setProfileSources(a, "Kids", null, 9000);
    expect(b[0].s).toBeUndefined();
    expect(b[0].at).toBe(9000);
    expect(setProfileSources(base, "inconnu", null)).toBeNull();
  });
  it("enregistrement depuis le tableau de bord puis synchro d'un appareil", async () => {
    const acct = await newAccount();
    await addProvider(acct, { kind: "M3U", name: "Alpha", url: "http://a.test/p.m3u" });
    await addProvider(acct, { kind: "M3U", name: "Bêta", url: "http://b.test/p.m3u" });
    const tv = await pairDevice(acct, "TV");
    const sync = (profiles) => call("/api/device/profiles", { method: "POST", ip: freshIp(), headers: bearer(tv.token), json: { profiles } });
    const t0 = Date.now() - 60_000;
    await sync([{ n: "Enfants", c: 1, k: true, at: t0 }]);
    const post = (form) => call("/profiles/sources", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, ...form } });
    const r = await post({ name: "Enfants", src: "Alpha" });
    expect(r.headers.get("location")).toContain("m=profile_src_saved");
    expect((await getAccount(env, acct.login)).profiles[0]).toMatchObject({ n: "Enfants", s: ["Alpha"] });
    // L'appareil renvoie son ancienne version : la modification du tableau de bord (plus récente) gagne et lui revient.
    const got = await (await sync([{ n: "Enfants", c: 1, k: true, at: t0 }])).json();
    expect(got.profiles[0].s).toEqual(["Alpha"]);
    // Aucune case : refusé ; « Toutes » : champ retiré.
    expect((await post({ name: "Enfants" })).headers.get("location")).toContain("e=profile_src");
    expect((await post({ name: "Fantôme", src: "Alpha" })).headers.get("location")).toContain("e=profile_src");
    await post({ name: "Enfants", all: "1" });
    expect((await getAccount(env, acct.login)).profiles[0].s).toBeUndefined();
    const again = await (await sync([])).json();
    expect(again.profiles[0].s).toBeUndefined();
  });
});
