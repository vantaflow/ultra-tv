import { afterEach, describe, expect, it, vi } from "vitest";
import { call, newAccount, pairDevice, addProvider, bearer } from "./helpers.js";
import { nasUrl, parseProvider, parseDeviceProvider, parsePlexProvider, syncProvider } from "../src/store.js";
import { parsePlexServers } from "../src/plexweb.js";
import { autoTranslate, AUTO_TR_LLM } from "../src/annautotr.js";

const form = (o) => ({ get: (k) => (o[k] === undefined ? null : o[k]) });
const ok = (body) => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });

afterEach(() => vi.restoreAllMocks());

describe("serveurs multimédias : validation", () => {
  it("dossier partagé SMB : formes acceptées, identifiants dans l'adresse refusés", () => {
    expect(nasUrl("smb://192.168.1.10/Films/")).toBe("smb://192.168.1.10/Films");
    expect(nasUrl("\\\\nas\\Vidéos\\Séries")).toBe("smb://nas/Vidéos/Séries");
    expect(nasUrl("nas.local:445/media")).toBe("smb://nas.local:445/media");
    expect(nasUrl("smb://user:pw@nas/films")).toBeNull();
    expect(nasUrl("smb://nas")).toBeNull();
    expect(nasUrl("")).toBeNull();
  });

  it("Jellyfin / Emby : utilisateur ou clé API, clé envoyée aux appareils", () => {
    expect(parseProvider(form({ kind: "JELLYFIN", url: "http://192.168.1.20:8096" })).error).toBe("media_creds");
    const r = parseProvider(form({ kind: "EMBY", url: "http://emby.local:8096", apikey: "k123" }));
    expect(r.provider).toMatchObject({ kind: "EMBY", apiKey: "k123", username: "" });
    expect(syncProvider(r.provider)).toMatchObject({ kind: "EMBY", apiKey: "k123" });
    const j = parseProvider(form({ kind: "JELLYFIN", url: "http://jf:8096", username: "anna", password: "" })).provider;
    expect(j).toMatchObject({ username: "anna", password: "" });
  });

  it("NAS : invité permis, adresse invalide refusée avec son propre code", () => {
    expect(parseProvider(form({ kind: "NAS", url: "smb://nas/films" })).provider).toMatchObject({ kind: "NAS", url: "smb://nas/films", username: "" });
    expect(parseProvider(form({ kind: "NAS", url: "http://nas" })).error).toBe("nas");
  });

  it("Plex : jamais par formulaire libre ; source construite depuis le serveur choisi", () => {
    expect(parseProvider(form({ kind: "PLEX", url: "http://x:32400" })).error).toBe("kind");
    const p = parsePlexProvider({ id: "m1", name: "Salon", owned: true, token: "srv-token", conns: [{ uri: "http://192.168.1.5:32400", local: true, relay: false }] }).provider;
    expect(p).toMatchObject({ kind: "PLEX", name: "Salon", url: "http://192.168.1.5:32400", username: "m1", password: "srv-token" });
    expect(syncProvider(p).plex).toEqual({ owned: true, conns: [{ uri: "http://192.168.1.5:32400", local: true, relay: false }] });
    expect(parsePlexProvider({ id: "m1", token: "t", conns: [] }).error).toBe("plex");
  });

  it("un appareil ne pousse jamais de serveur multimédia", () => {
    for (const kind of ["JELLYFIN", "EMBY", "PLEX", "NAS"]) expect(parseDeviceProvider({ kind, url: "http://x", username: "u" }, null).error).toBe("kind");
  });

  it("serveurs Plex : seulement ceux qui servent, connexions locales d'abord et relais en dernier", () => {
    const s = parsePlexServers([
      { name: "Lecteur", provides: "player", clientIdentifier: "p", accessToken: "t" },
      { name: "Salon", provides: "server", clientIdentifier: "m1", accessToken: "t1", owned: false, connections: [
        { uri: "https://relay.plex.direct:8443", local: false, relay: true },
        { uri: "https://1-2-3-4.plex.direct:32400", local: false, relay: false },
        { uri: "http://192.168.1.5:32400", local: true, relay: false },
      ] },
    ]);
    expect(s).toHaveLength(1);
    expect(s[0]).toMatchObject({ id: "m1", name: "Salon", owned: false, token: "t1" });
    expect(s[0].conns.map((c) => c.uri)).toEqual(["http://192.168.1.5:32400", "https://1-2-3-4.plex.direct:32400", "https://relay.plex.direct:8443"]);
  });
});

describe("serveurs multimédias : tableau de bord → appareil", () => {
  it("Jellyfin ajouté sur le site : reçu par l'appareil avec sa clé API", async () => {
    const acct = await newAccount();
    const dev = await pairDevice(acct);
    const r = await addProvider(acct, { kind: "JELLYFIN", name: "Maison", url: "http://192.168.1.20:8096", apikey: "abc" });
    expect(r.status).toBe(302);
    expect(r.headers.get("location")).toBe("/?m=srvadded&k=JELLYFIN&s=Maison#serveurs");
    const cfg = await (await call("/api/config", { ip: dev.ip, headers: bearer(dev.token) })).json();
    expect(cfg.providers.find((p) => p.kind === "JELLYFIN")).toMatchObject({ name: "Maison", url: "http://192.168.1.20:8096", apiKey: "abc" });
  });

  it("NAS invalide : retour au panneau des serveurs avec le message dédié", async () => {
    const acct = await newAccount();
    const r = await addProvider(acct, { kind: "NAS", url: "ftp://nas/x" });
    expect(r.headers.get("location")).toBe("/?e=nas#serveurs");
  });

  it("Plex : PIN, validation sur plex.tv, choix du serveur ; le jeton ne passe jamais par le navigateur", async () => {
    const acct = await newAccount();
    const dev = await pairDevice(acct);
    let validated = false;
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const u = String(input?.url || input);
      if (u.startsWith("https://plex.tv/api/v2/pins?")) return ok({ id: 42, code: "ABCD" });
      if (u === "https://plex.tv/api/v2/pins/42") return ok({ id: 42, code: "ABCD", authToken: validated ? "acct-token" : null });
      if (u.startsWith("https://plex.tv/api/v2/resources")) return ok([{ name: "Salon", provides: "server", clientIdentifier: "m1", owned: true, accessToken: "srv-token", connections: [{ uri: "http://192.168.1.5:32400", local: true }] }]);
      throw new Error(`inattendu : ${u}`);
    });
    const post = (p) => call(p, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    const pin = await (await post("/plex/pin")).json();
    expect(pin.auth).toMatch(/^https:\/\/app\.plex\.tv\/auth#\?clientID=ultratv-web-/);
    expect((await (await post("/plex/check")).json()).state).toBe("wait");
    validated = true;
    const checked = await (await post("/plex/check")).json();
    expect(checked).toEqual({ state: "ok", servers: [{ id: "m1", name: "Salon", owned: true, conn: "local", added: false }] });
    expect(JSON.stringify(checked)).not.toContain("token");
    const r = await addProvider(acct, { kind: "PLEX", server: "m1" });
    expect(r.headers.get("location")).toBe("/?m=srvadded&k=PLEX&s=Salon#serveurs");
    const cfg = await (await call("/api/config", { ip: dev.ip, headers: bearer(dev.token) })).json();
    expect(cfg.providers.find((p) => p.kind === "PLEX")).toMatchObject({ name: "Salon", username: "m1", password: "srv-token", plex: { owned: true } });
    // PIN consommé : un second ajout échoue proprement.
    expect((await addProvider(acct, { kind: "PLEX", server: "m1" })).headers.get("location")).toBe("/?e=plex#serveurs");
  });

  it("Plex : serveur déjà ajouté refusé en doublon, message et ligne « déjà ajouté »", async () => {
    const acct = await newAccount();
    await pairDevice(acct, "Salon");
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const u = String(input?.url || input);
      if (u.startsWith("https://plex.tv/api/v2/pins?")) return ok({ id: 42, code: "ABCD" });
      if (u === "https://plex.tv/api/v2/pins/42") return ok({ id: 42, code: "ABCD", authToken: "acct-token" });
      if (u.startsWith("https://plex.tv/api/v2/resources")) return ok([{ name: "JDM Plex Server", provides: "server", clientIdentifier: "m1", owned: true, accessToken: "srv-token", connections: [{ uri: "http://192.168.1.5:32400", local: true }] }]);
      throw new Error(`inattendu : ${u}`);
    });
    const post = (p) => call(p, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    await post("/plex/pin");
    expect((await (await post("/plex/check")).json()).servers[0].added).toBe(false);
    const first = await addProvider(acct, { kind: "PLEX", server: "m1" });
    expect(first.headers.get("location")).toBe("/?m=srvadded&k=PLEX&s=JDM%20Plex%20Server#serveurs");
    await post("/plex/pin");
    const again = await (await post("/plex/check")).json();
    expect(again.servers[0].added).toBe(true);
    const dup = await addProvider(acct, { kind: "PLEX", server: "m1" });
    expect(dup.headers.get("location")).toBe("/?m=srvdup&k=PLEX&s=JDM%20Plex%20Server#serveurs");
    const html = await (await call("/?l=fr&m=srvadded&k=PLEX&s=JDM%20Plex%20Server", { ip: acct.ip, cookie: acct.cookie })).text();
    expect(html).toContain("JDM Plex Server ajouté");
  });

  it("liste des serveurs configurés : nom, type, badge, date, suppression et appareils", async () => {
    const acct = await newAccount();
    await pairDevice(acct, "Salon");
    await addProvider(acct, { kind: "JELLYFIN", name: "Maison", url: "http://192.168.1.20:8096", apikey: "abc" });
    await addProvider(acct, { kind: "NAS", name: "Mon NAS", url: "smb://nas/films" });
    const html = await (await call("/?l=fr", { ip: acct.ip, cookie: acct.cookie })).text();
    const list = html.slice(html.indexOf('class="msrv-list"'));
    expect(list).toContain("Maison");
    expect(list).toContain("JELLYFIN");
    expect(list).toContain("Mon NAS");
    expect(list).toContain("Ajouté le");
    expect(list).toContain("/delete");
    expect(list).toContain("Tous les appareils");
    expect(list).toContain("/assign");
    expect(html.indexOf('<ul class="msrv-list"')).toBeLessThan(html.indexOf('name="mtab"'));
  });

  it("Jellyfin, Emby, NAS : doublon refusé (adresse normalisée), message et onglet de retour", async () => {
    const acct = await newAccount();
    const cases = [
      ["JELLYFIN", { url: "http://Jf.local:8096/", username: "Anna", apikey: "" }, { url: "http://jf.local:8096", username: "anna", apikey: "" }, { url: "http://jf.local:8096", username: "bob" }],
      ["EMBY", { url: "http://emby.local:80/", apikey: "k1" }, { url: "http://EMBY.local", apikey: "k1" }, null],
      ["NAS", { url: "smb://NAS/Films/" }, { url: "smb://nas/films", username: "autre" }, { url: "smb://nas/series" }],
    ];
    for (const [kind, first, same, other] of cases) {
      const r1 = await addProvider(acct, { kind, name: `${kind}1`, ...first });
      expect(r1.headers.get("location")).toBe(`/?m=srvadded&k=${kind}&s=${kind}1#serveurs`);
      const r2 = await addProvider(acct, { kind, name: `${kind}2`, ...same });
      expect(r2.headers.get("location")).toBe(`/?m=srvdup&k=${kind}&s=${kind}2#serveurs`);
      if (other) expect((await addProvider(acct, { kind, name: `${kind}3`, ...other })).headers.get("location")).toBe(`/?m=srvadded&k=${kind}&s=${kind}3#serveurs`);
    }
    const html = await (await call("/?l=fr&m=srvdup&k=NAS&s=NAS2", { ip: acct.ip, cookie: acct.cookie })).text();
    expect(html).toContain("NAS2 est déjà ajouté");
    expect(html).toMatch(/id="t-nas" checked/);
    const list = html.slice(html.indexOf('class="msrv-list"'));
    for (const n of ["JELLYFIN1", "EMBY1", "NAS1"]) expect(list).toContain(n);
    expect(list).not.toContain("JELLYFIN2");
  });

  it("le panneau « Serveurs multimédias » est sur le tableau de bord", async () => {
    const acct = await newAccount();
    const html = await (await call("/", { ip: acct.ip, cookie: acct.cookie })).text();
    expect(html).toContain('id="serveurs"');
    expect(html).toContain('id="form-nas"');
    expect(html).toContain('id="plex-go"');
  });
});

describe("alertes : traduction automatique", () => {
  const fakeAi = (fail = []) => ({ run: vi.fn(async (_m, { text, target_lang }) => {
    if (fail.includes(target_lang)) throw new Error("quota");
    return { translated_text: `[${target_lang}] ${text}` };
  }) });

  it("traduit les langues vides, garde celles saisies, ignore les échecs", async () => {
    const ai = fakeAi(["th"]);
    const { i18n, done } = await autoTranslate(ai, { title: "Hi", message: "Maintenance", i18n: { fr: { title: "Salut", message: "Maintenance (FR)" } } }, ["fr", "es", "nb", "th"]);
    expect(i18n.fr).toEqual({ title: "Salut", message: "Maintenance (FR)" });
    expect(i18n.es).toEqual({ title: "[es] Hi", message: "[es] Maintenance", auto: true });
    expect(i18n.nb.message).toBe("[no] Maintenance");
    expect(i18n.th).toBeUndefined();
    expect(done).toEqual(["es", "nb"]);
  });

  it("texte saisi en français : l'anglais est traduit aussi, jamais la langue source", async () => {
    const { i18n, done } = await autoTranslate(fakeAi(), { title: "Soutenir", message: "Merci" }, ["en", "fr", "es"], "fr");
    expect(i18n.en).toEqual({ title: "[en] Soutenir", message: "[en] Merci", auto: true });
    expect(i18n.fr).toBeUndefined();
    expect(done).toEqual(["en", "es"]);
  });

  it("modèle de langage d'abord (objet ou texte JSON), m2m100 seulement en secours", async () => {
    const ai = { run: vi.fn(async (model, inp) => {
      if (model === AUTO_TR_LLM) {
        const to = inp.messages[0].content.match(/ to ([A-Za-z ]+)\./)[1];
        if (to === "Thai") throw new Error("quota");
        if (to === "Spanish") return { response: { title: "Apoya a Ultra TV", message: "¡Gracias!" } };
        return { response: 'Voici : {"title": "Support Ultra TV", "message": "Thanks!"}' };
      }
      return { translated_text: `[m2m ${inp.target_lang}] ${inp.text}` };
    }) };
    const { i18n, done } = await autoTranslate(ai, { title: "Soutenir Ultra TV", message: "Merci !" }, ["en", "es", "th"], "fr");
    expect(i18n.es).toEqual({ title: "Apoya a Ultra TV", message: "¡Gracias!", auto: true });
    expect(i18n.en).toEqual({ title: "Support Ultra TV", message: "Thanks!", auto: true });
    expect(i18n.th).toEqual({ title: "[m2m th] Soutenir Ultra TV", message: "[m2m th] Merci !", auto: true });
    expect(done).toEqual(["en", "es", "th"]);
  });

  it("sans Workers AI : rien ne change", async () => {
    expect(await autoTranslate(undefined, { title: "", message: "x" }, ["fr"])).toEqual({ i18n: null, done: [] });
  });
});
