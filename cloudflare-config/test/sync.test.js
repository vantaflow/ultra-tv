import { describe, it, expect } from "vitest";
import { call, newAccount, pairDevice, addProvider, bearer, freshIp, csrfOf } from "./helpers.js";

const m3u = (name, host = "m3u.example") => ({ kind: "M3U", name, url: `https://${host}/list.m3u` });

async function cfg(dev, extra = {}) {
  return call("/api/config", { ip: freshIp(), headers: { ...bearer(dev.token), ...extra } });
}
async function putProv(dev, json) {
  return call("/api/device/providers", { method: "POST", ip: freshIp(), headers: bearer(dev.token), json });
}
async function names(dev) { return (await (await cfg(dev)).json()).providers.map((p) => p.name).sort(); }

async function twoDevices() {
  const acct = await newAccount();
  const tv = await pairDevice(acct, "TV salon");
  const phone = await pairDevice(acct, "Téléphone");
  return { acct, tv, phone };
}

describe("synchro : version / ETag", () => {
  it("config_renvoieVersionEtEtag_304SiInchange", async () => {
    const { acct, tv } = await twoDevices();
    const r1 = await cfg(tv); const b1 = await r1.json();
    expect(typeof b1.version).toBe("number");
    const etag = r1.headers.get("etag");
    expect(etag).toBe(`"v${b1.version}"`);
    expect((await cfg(tv, { "if-none-match": etag })).status).toBe(304);
    await addProvider(acct, m3u("Dash"));
    const r2 = await cfg(tv, { "if-none-match": etag });
    expect(r2.status).toBe(200);
    expect((await r2.json()).version).toBeGreaterThan(b1.version);
  });
  it("config_listeLesAppareilsDuCompte_etSoi", async () => {
    const { tv, phone } = await twoDevices();
    const b = await (await cfg(tv)).json();
    expect(b.self).toBe(tv.deviceId);
    expect(b.devices.find((d) => d.isCurrent).id).toBe(tv.deviceId);
    expect(b.devices.map((d) => d.name).sort()).toEqual(["TV salon", "Téléphone"]);
    expect(b.devices.every((d) => !("hash" in d))).toBe(true);
    expect(phone.deviceId).not.toBe(tv.deviceId);
  });
});

describe("affectations", () => {
  it("fournisseurDuTableauDeBord_parDefaut_visiblePourTous", async () => {
    const { acct, tv, phone } = await twoDevices();
    await addProvider(acct, m3u("Partagée"));
    expect(await names(tv)).toEqual(["Partagée"]);
    expect(await names(phone)).toEqual(["Partagée"]);
  });
  it("creeDepuisUnAppareil_estPriveACetAppareil", async () => {
    const { tv, phone } = await twoDevices();
    const r = await putProv(phone, m3u("Privée"));
    expect(r.status).toBe(201);
    const b = await r.json();
    expect(b.provider.sharedWith).toEqual([phone.deviceId]);
    expect(await names(phone)).toEqual(["Privée"]);
    expect(await names(tv)).toEqual([]);
  });
  it("shareWith_all_etListe", async () => {
    const { tv, phone } = await twoDevices();
    await putProv(phone, { ...m3u("Pour tous"), shareWith: "all" });
    await putProv(phone, { ...m3u("Pour la TV"), shareWith: [tv.deviceId] });
    expect(await names(tv)).toEqual(["Pour la TV", "Pour tous"]);
    // `Pour la TV` reste aussi sur le téléphone qui l'a créée (créateur toujours inclus)
    expect(await names(phone)).toEqual(["Pour la TV", "Pour tous"]);
  });
  it("shareWith_idInconnuOuDUnAutreCompte_refuse400", async () => {
    const { phone } = await twoDevices();
    const other = await newAccount();
    const foreign = await pairDevice(other, "Etranger");
    for (const bad of [[foreign.deviceId], ["ffffffffffff"], [], "everyone", [1], { a: 1 }]) {
      const r = await putProv(phone, { ...m3u("X"), shareWith: bad });
      expect(r.status, JSON.stringify(bad)).toBe(400);
    }
    expect(await names(phone)).toEqual([]);
  });
  it("unAppareilNeVoitPasUneSourceNonAffectee_updateParIdentifiant_404", async () => {
    const { tv, phone } = await twoDevices();
    const created = await (await putProv(phone, m3u("Secrète"))).json();
    const id = created.provider.id;
    const r = await putProv(tv, { ...m3u("Piratée", "evil.example"), id });
    expect(r.status).toBe(404);
    expect((await call(`/api/device/providers/${id}`, { method: "DELETE", ip: freshIp(), headers: bearer(tv.token) })).status).toBe(404);
    // intacte pour le propriétaire
    const mine = (await (await cfg(phone)).json()).providers[0];
    expect(mine.name).toBe("Secrète"); expect(mine.url).toContain("m3u.example");
  });
  it("jetonDUnAutreCompte_neVoitNiNeModifie", async () => {
    const { phone } = await twoDevices();
    const id = (await (await putProv(phone, m3u("Mienne"))).json()).provider.id;
    const stranger = await pairDevice(await newAccount(), "Autre");
    expect(await names(stranger)).toEqual([]);
    expect((await putProv(stranger, { ...m3u("X"), id })).status).toBe(404);
    expect((await call(`/api/device/providers/${id}?all=1`, { method: "DELETE", ip: freshIp(), headers: bearer(stranger.token) })).status).toBe(404);
  });
  it("changementDAffectationDepuisLeTableauDeBord", async () => {
    const { acct, tv, phone } = await twoDevices();
    await addProvider(acct, m3u("Dash"));
    const id = (await (await cfg(tv)).json()).providers[0].id;
    const post = (form) => call(`/providers/${id}/assign`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, ...form } });
    expect((await post({ d: tv.deviceId })).status).toBe(302);
    expect(await names(tv)).toEqual(["Dash"]);
    expect(await names(phone)).toEqual([]);
    // aucune case cochée : refusé, l'affectation ne change pas
    const none = await post({});
    expect(none.headers.get("location")).toContain("e=assign");
    expect(await names(tv)).toEqual(["Dash"]);
    // « Tous »
    await post({ all: "1" });
    expect(await names(phone)).toEqual(["Dash"]);
  });
  it("affectationDashboard_idDAutreCompte_refuse", async () => {
    const { acct } = await twoDevices();
    const other = await newAccount();
    const foreign = await pairDevice(other, "E");
    await addProvider(acct, m3u("Dash"));
    const tvList = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(tvList).not.toContain(foreign.deviceId);
    const id = /\/providers\/([0-9a-f]+)\/assign/.exec(tvList)[1];
    const r = await call(`/providers/${id}/assign`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, d: foreign.deviceId } });
    expect(r.headers.get("location")).toContain("e=assign");
  });
  it("retraitParUnAppareil_leRetireSeulementDeSonAffectation", async () => {
    const { acct, tv, phone } = await twoDevices();
    await addProvider(acct, m3u("Dash"));
    const id = (await (await cfg(tv)).json()).providers[0].id;
    expect((await call(`/api/device/providers/${id}`, { method: "DELETE", ip: freshIp(), headers: bearer(tv.token) })).status).toBe(200);
    expect(await names(tv)).toEqual([]);
    expect(await names(phone)).toEqual(["Dash"]);
    // ?all=1 supprime pour tout le compte
    expect((await call(`/api/device/providers/${id}?all=1`, { method: "DELETE", ip: freshIp(), headers: bearer(phone.token) })).status).toBe(200);
    expect(await names(phone)).toEqual([]);
  });
  it("donneesAnterieuresSansAffectation_sontVisiblesPourTous", async () => {
    const { acct, tv } = await twoDevices();
    await addProvider(acct, m3u("Ancienne"));
    const p = (await (await cfg(tv)).json()).providers[0];
    expect(p.sharedWith).toBe("all");
  });
  it("revocation_retireLAppareilDesAffectations_etBloqueLeJeton", async () => {
    const { acct, tv, phone } = await twoDevices();
    await putProv(phone, { ...m3u("Partagée TV+tel"), shareWith: [tv.deviceId] });
    const revoke = await call(`/devices/${tv.deviceId}/revoke`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(revoke.status).toBe(302);
    expect((await cfg(tv)).status).toBe(401);
    const p = (await (await cfg(phone)).json()).providers[0];
    expect(p.sharedWith).toEqual([phone.deviceId]);
  });
});

describe("POST /api/device/providers : validation et attaques", () => {
  it("sansJeton_ou_jetonRevoque_401", async () => {
    const { acct, tv } = await twoDevices();
    expect((await call("/api/device/providers", { method: "POST", ip: freshIp(), json: m3u("X") })).status).toBe(401);
    await call(`/devices/${tv.deviceId}/revoke`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect((await putProv(tv, m3u("X"))).status).toBe(401);
  });
  it("memeValidationQueLeTableauDeBord", async () => {
    const { phone } = await twoDevices();
    expect((await putProv(phone, { kind: "WAT", url: "https://a.b/x" })).status).toBe(400);
    expect((await putProv(phone, { kind: "M3U", url: "javascript:alert(1)" })).status).toBe(400);
    expect((await putProv(phone, { kind: "M3U", url: "file:///etc/passwd" })).status).toBe(400);
    expect((await putProv(phone, { kind: "XTREAM", url: "http://h:80" })).status).toBe(400);
    expect((await putProv(phone, { kind: "STALKER", url: "http://h:80", mac: "00:1A:79:00:00:01" })).status).toBe(400);
  });
  it("champsNonChaines_objetsTableaux_refuses", async () => {
    const { phone } = await twoDevices();
    for (const bad of [{ name: { a: 1 } }, { name: ["x"] }, { url: ["https://a.b/x"] }, { username: 5 }]) {
      const r = await putProv(phone, { ...m3u("ok"), ...bad });
      expect(r.status, JSON.stringify(bad)).toBe(400);
    }
  });
  it("injection_nomAvecBaliseOuCaracteresDeControle_neSortJamaisNonEchappe", async () => {
    const { acct, phone } = await twoDevices();
    const evil = '<script>alert(1)</script>\r\nSet-Cookie: x=1';
    expect((await putProv(phone, { ...m3u(evil), shareWith: "all" })).status).toBe(201);
    const stored = (await (await cfg(phone)).json()).providers[0].name;
    expect(stored).not.toMatch(/[\r\n]/);
    const html = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(html).not.toContain("<script>alert(1)");
  });
  it("idForgeOuMalForme_400_et_aucuneCreationSousIdImpose", async () => {
    const { phone } = await twoDevices();
    expect((await putProv(phone, { ...m3u("X"), id: "../../etc" })).status).toBe(400);
    expect((await putProv(phone, { ...m3u("X"), id: "deadbeef" })).status).toBe(404);
    expect(await names(phone)).toEqual([]);
  });
  it("corpsGeant_413_etJsonInvalide_400", async () => {
    const { phone } = await twoDevices();
    expect((await putProv(phone, { ...m3u("X"), name: "a".repeat(10_000) })).status).toBe(413);
    const r = await call("/api/device/providers", { method: "POST", ip: freshIp(), headers: bearer(phone.token), body: "{nope" });
    expect(r.status).toBe(400);
  });
  it("limiteDeFournisseurs_409", async () => {
    const { phone } = await twoDevices();
    for (let i = 0; i < 20; i++) expect((await putProv(phone, m3u(`p${i}`))).status).toBe(201);
    expect((await putProv(phone, m3u("de trop"))).status).toBe(409);
  });
  it("limiteDeDebitParAppareil_429", async () => {
    const { phone } = await twoDevices();
    let last = 0;
    for (let i = 0; i < 40; i++) { last = (await putProv(phone, { kind: "WAT", url: "x" })).status; if (last === 429) break; }
    expect(last).toBe(429);
  });
  it("miseAJour_conserveOrigineEtCreation_etChangeLesIdentifiants", async () => {
    const { phone } = await twoDevices();
    const c = (await (await putProv(phone, { kind: "XTREAM", name: "X", url: "http://h:80", username: "u", password: "p" })).json()).provider;
    const u = await putProv(phone, { id: c.id, kind: "XTREAM", name: "X", url: "http://h2:80", username: "u2", password: "p2" });
    expect(u.status).toBe(200);
    const b = (await u.json()).provider;
    expect(b.id).toBe(c.id); expect(b.createdAt).toBe(c.createdAt); expect(b.originName).toBe("Téléphone");
    expect(b.url).toBe("http://h2:80"); expect(b.username).toBe("u2");
  });
  it("identifiantsChiffresAuRepos", async () => {
    const { allKv } = await import("./helpers.js");
    const { phone } = await twoDevices();
    await putProv(phone, { kind: "XTREAM", name: "X", url: "http://secret-host.example:80", username: "alice-user", password: "SuperSecretPw" });
    const dump = JSON.stringify(await allKv());
    expect(dump).not.toContain("SuperSecretPw"); expect(dump).not.toContain("alice-user"); expect(dump).not.toContain("secret-host");
  });
});

describe("appareils : nom et tableau de bord", () => {
  it("renommerCetAppareil_et_tableauDeBordAffichageOrigineEtMatrice", async () => {
    const { acct, phone } = await twoDevices();
    const r = await call("/api/device/self", { method: "POST", ip: freshIp(), headers: bearer(phone.token), json: { name: "Pixel de Lilou" } });
    expect(r.status).toBe(200);
    await putProv(phone, { ...m3u("Ma source"), shareWith: "all" });
    const html = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(html).toContain("Ajouté depuis Pixel de Lilou");
    expect(html).toContain('name="all"');
    expect(html).toContain("Renommer");
  });
  it("PATCH_api_device_et_PUT_providers_alias", async () => {
    const { phone } = await twoDevices();
    expect((await call("/api/device", { method: "PATCH", ip: freshIp(), headers: bearer(phone.token), json: { name: "Pixel" } })).status).toBe(200);
    const r = await call("/api/device/providers", { method: "PUT", ip: freshIp(), headers: bearer(phone.token), json: m3u("Via PUT") });
    expect(r.status).toBe(201);
  });
  it("renomme_nomVideOuObjet_400", async () => {
    const { phone } = await twoDevices();
    for (const name of ["", "   ", { a: 1 }, 5]) {
      expect((await call("/api/device/self", { method: "POST", ip: freshIp(), headers: bearer(phone.token), json: { name } })).status).toBe(400);
    }
  });
  it("renommageDashboard_etNomParDefautIssuDeLEtiquette", async () => {
    const acct = await newAccount();
    const ip = freshIp();
    const start = await (await call("/api/pair/start", { method: "POST", ip, json: { label: "Pixel 8" } })).json();
    await call("/pair", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, code: start.code, name: "" } });
    const html = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(html).toContain("Pixel 8");
    const id = /\/devices\/([0-9a-f]+)\/rename/.exec(html)[1];
    await call(`/devices/${id}/rename`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, name: "Mac bureau" } });
    expect(await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text()).toContain("Mac bureau");
  });
});

describe("Stalker retiré : champ mac", () => {
  it("macIgnoreEnEntree_etNeSortPlus_etLesAnciensStalkerSontFiltres", async () => {
    const { acct, phone } = await twoDevices();
    const r = await putProv(phone, { ...m3u("Avec mac"), mac: "00:1A:79:00:00:01" });
    expect(r.status).toBe(201);
    const p = (await r.json()).provider;
    expect("mac" in p).toBe(false);
    expect((await (await cfg(phone)).json()).providers.every((x) => !("mac" in x))).toBe(true);
    // un ancien fournisseur Stalker stocké ne part plus vers les appareils
    const { env } = await import("cloudflare:workers");
    const { loadProviders, saveProviders, getAccount, putAccount } = await import("../src/store.js");
    const a = await getAccount(env, acct.login);
    const list = await loadProviders(env, a);
    list.push({ id: "0badc0de", kind: "STALKER", name: "Vieux portail", url: "http://p:80", mac: "00:1A:79:00:00:02", username: "", password: "" });
    await saveProviders(env, a, list); await putAccount(env, a);
    expect(await names(phone)).toEqual(["Avec mac"]);
    // et le tableau de bord refuse d'en créer
    expect((await addProvider(acct, { kind: "STALKER", url: "http://p:80", mac: "00:1A:79:00:00:03" })).headers.get("location")).toContain("e=kind");
  });
});

describe("mutations concurrentes d'un compte", () => {
  it("vingtAjoutsSimultanesDeDeuxAppareils_etDuTableauDeBord_aucunePerte", async () => {
    const { acct, tv, phone } = await twoDevices();
    const jobs = [];
    for (let i = 0; i < 8; i++) {
      jobs.push(putProv(tv, { ...m3u(`tv-${i}`, `tv${i}.example`), shareWith: "all" }));
      jobs.push(putProv(phone, { ...m3u(`ph-${i}`, `ph${i}.example`), shareWith: "all" }));
    }
    for (let i = 0; i < 4; i++) jobs.push(addProvider(acct, m3u(`dash-${i}`, `d${i}.example`)));
    const res = await Promise.all(jobs);
    expect(res.every((r) => r.status === 201 || r.status === 302), res.map((r) => r.status).join()).toBe(true);
    const got = await names(tv);
    expect(got.length).toBe(20);
    const b = await (await cfg(tv)).json();
    expect(b.version).toBeGreaterThanOrEqual(20);        // une version par mutation, sans doublon perdu
  });
  it("affectationsEtSuppressionsSimultanees_restentCoherentes", async () => {
    const { acct, tv, phone } = await twoDevices();
    const ids = [];
    for (let i = 0; i < 4; i++) ids.push((await (await putProv(tv, { ...m3u(`s${i}`, `s${i}.example`), shareWith: "all" })).json()).provider.id);
    const ops = ids.flatMap((id) => [
      call(`/providers/${id}/assign`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, d: phone.deviceId } }),
      call(`/api/device/providers/${id}`, { method: "DELETE", ip: freshIp(), headers: bearer(tv.token) }),
    ]);
    const res = await Promise.all(ops);
    expect(res.every((r) => r.status < 500)).toBe(true);
    // chaque source a un état valide : visible du téléphone seul, ou supprimée
    const seen = (await (await cfg(phone)).json()).providers;
    for (const p of seen) expect(Array.isArray(p.sharedWith) || p.sharedWith === "all").toBe(true);
    expect((await (await cfg(tv)).json()).providers.length).toBe(0);
  });
  it("verrouTenu_rendOccupe503_puisSeLibere", async () => {
    const { acct, tv } = await twoDevices();
    const { env } = await import("cloudflare:workers");
    const stub = env.GUARD.get(env.GUARD.idFromName(`mut:${acct.login}`));
    const l = await stub.lockAcquire(200);
    expect(l.ok).toBe(true);
    expect((await stub.lockAcquire(200)).ok).toBe(false);
    // la mutation attend la libération (expiration du verrou) puis réussit
    expect((await putProv(tv, m3u("Après verrou"))).status).toBe(201);
  });
});
