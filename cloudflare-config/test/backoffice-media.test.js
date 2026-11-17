import { afterEach, describe, expect, it, vi } from "vitest";
import { call, newAccount, pairDevice, addProvider, bearer } from "./helpers.js";
import { nasUrl, parseProvider, parseDeviceProvider, parsePlexProvider, syncProvider } from "../src/store.js";
import { parsePlexServers } from "../src/plexweb.js";
import { autoTranslate } from "../src/annautotr.js";

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
    expect(r.headers.get("location")).toBe("/?m=added");
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
    expect(checked).toEqual({ state: "ok", servers: [{ id: "m1", name: "Salon", owned: true, conn: "local" }] });
    expect(JSON.stringify(checked)).not.toContain("token");
    const r = await addProvider(acct, { kind: "PLEX", server: "m1" });
    expect(r.headers.get("location")).toBe("/?m=added");
    const cfg = await (await call("/api/config", { ip: dev.ip, headers: bearer(dev.token) })).json();
    expect(cfg.providers.find((p) => p.kind === "PLEX")).toMatchObject({ name: "Salon", username: "m1", password: "srv-token", plex: { owned: true } });
    // PIN consommé : un second ajout échoue proprement.
    expect((await addProvider(acct, { kind: "PLEX", server: "m1" })).headers.get("location")).toBe("/?e=plex#serveurs");
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

  it("sans Workers AI : rien ne change", async () => {
    expect(await autoTranslate(undefined, { title: "", message: "x" }, ["fr"])).toEqual({ i18n: null, done: [] });
  });
});
