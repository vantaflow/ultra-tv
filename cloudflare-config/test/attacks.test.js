// Rejeu des attaques identifiées à l'audit : chacune doit être bloquée.
import { describe, it, expect } from "vitest";
import { env } from "cloudflare:workers";
import { call, freshIp, newAccount, pairDevice, addProvider, allKv, bearer, basic, cookieFrom, OPS, strongPw } from "./helpers.js";

describe("lecture de configuration (ex-anonyme)", () => {
  it("GET_apiConfig_sansJeton_401", async () => {
    const r = await call("/api/config", { ip: freshIp() });
    expect(r.status).toBe(401);
  });
  it("GET_apiConfig_jetonDevine_401", async () => {
    const r = await call("/api/config", { ip: freshIp(), headers: bearer("utv_" + "A".repeat(43)) });
    expect(r.status).toBe(401);
  });
  it("GET_apiConfigParMac_ancienneRoute_410_sansFournisseurs", async () => {
    const acct = await newAccount("aa:bb:cc:dd:ee:ff");
    await addProvider(acct, { kind: "XTREAM", name: "X", url: "http://h.tv:80", username: "bob", password: "hunter2" });
    const r = await call("/api/config/aa:bb:cc:dd:ee:ff", { ip: freshIp() });
    expect(r.status).toBe(410);
    expect(await r.text()).not.toContain("hunter2");
  });
  it("GET_apiConfig_motDePasseDansLUrl_ignore", async () => {
    const acct = await newAccount();
    const r = await call(`/api/config?password=${strongPw}&mac=aa:bb:cc:dd:ee:ff`, { ip: freshIp() });
    expect(r.status).toBe(401);
    expect(acct.login).toBeTruthy();
  });
  it("GET_apiConfig_jetonRevoque_401", async () => {
    const acct = await newAccount();
    const dev = await pairDevice(acct);
    expect((await call("/api/config", { ip: dev.ip, headers: bearer(dev.token) })).status).toBe(200);
    const revoke = await call(`/devices/${dev.deviceId}/revoke`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(revoke.status).toBe(302);
    expect((await call("/api/config", { ip: dev.ip, headers: bearer(dev.token) })).status).toBe(401);
  });
  it("GET_apiConfig_jetonDUnAutreCompte_necontientPasMesFournisseurs", async () => {
    const a = await newAccount(); const b = await newAccount();
    await addProvider(a, { kind: "M3U", name: "secretA", url: "http://a.tv/l.m3u" });
    const devB = await pairDevice(b);
    const r = await (await call("/api/config", { ip: devB.ip, headers: bearer(devB.token) })).json();
    expect(JSON.stringify(r)).not.toContain("secretA");
  });
});

describe("force brute", () => {
  it("POST_login_cinqEchecs_compteVerrouille_memeAvecLeBonMotDePasse", async () => {
    const acct = await newAccount();
    for (let i = 0; i < 5; i++) {
      const r = await call("/login", { method: "POST", ip: freshIp(), form: { login: acct.login, password: "wrong-password-" + i } });
      expect([302, 401, 429]).toContain(r.status);
    }
    const r = await call("/login", { method: "POST", ip: freshIp(), form: { login: acct.login, password: strongPw } });
    expect(r.status).toBe(429);
    expect(Number(r.headers.get("retry-after"))).toBeGreaterThan(0);
  });
  it("POST_login_unAttaquantParIp_estLimiteSurTousLesComptes", async () => {
    const ip = freshIp();
    let blocked = false;
    for (let i = 0; i < 30 && !blocked; i++) {
      const r = await call("/login", { method: "POST", ip, form: { login: `nobody-${i}`, password: "wrong-password-x" } });
      blocked = r.status === 429;
    }
    expect(blocked).toBe(true);
  });
  it("POST_login_compteInconnuEtMauvaisMotDePasse_memeReponse", async () => {
    const acct = await newAccount();
    const a = await call("/login", { method: "POST", ip: freshIp(), form: { login: acct.login, password: "wrong-password-1" } });
    const b = await call("/login", { method: "POST", ip: freshIp(), form: { login: "nobody-here", password: "wrong-password-1" } });
    expect(a.status).toBe(b.status);
    expect(a.headers.get("location")).toBe(b.headers.get("location"));
  });
  it("POST_pairConfirm_codesDevines_verrouillage", async () => {
    const acct = await newAccount();
    let last;
    for (let i = 0; i < 8; i++) {
      last = await call("/pair", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, code: "ABCD-EFG" + i, name: "x" } });
    }
    expect(last.status).toBe(429);
  });
  it("POST_signup_rafale_limiteParIp", async () => {
    const ip = freshIp();
    let blocked = false;
    for (let i = 0; i < 12 && !blocked; i++) {
      const r = await call("/signup", { method: "POST", ip, form: { login: `spam-${crypto.randomUUID()}`, password: strongPw, confirm: strongPw } });
      blocked = r.status === 429;
    }
    expect(blocked).toBe(true);
  });
});

describe("tableaux de bord crashs / logs", () => {
  it.each(["/crashes", "/logs"])("GET_%s_sansSecret_401", async (p) => {
    expect((await call(p, { ip: freshIp() })).status).toBe(401);
  });
  it.each(["/crashes", "/logs"])("GET_%s_tokenEnQueryString_refuse", async (p) => {
    expect((await call(`${p}?token=${OPS}`, { ip: freshIp() })).status).toBe(401);
  });
  it.each(["/crashes", "/logs"])("GET_%s_mauvaisSecret_401", async (p) => {
    expect((await call(p, { ip: freshIp(), headers: basic("nope") })).status).toBe(401);
  });
  it.each(["/crashes", "/logs"])("GET_%s_bonSecretBasic_200", async (p) => {
    const r = await call(p, { ip: freshIp(), headers: basic(OPS) });
    expect(r.status).toBe(200);
    expect(r.headers.get("content-security-policy")).toContain("default-src 'none'");
  });
  it("GET_logs_bonSecretBearer_200", async () => {
    expect((await call("/logs", { ip: freshIp(), headers: bearer(OPS) })).status).toBe(200);
  });
  it("GET_logs_unJetonDAppareilNOuvrePasLeTableauDeBord", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    expect((await call("/logs", { ip: dev.ip, headers: bearer(dev.token) })).status).toBe(401);
  });
  it("GET_logs_forceBrute_verrouille", async () => {
    const ip = freshIp(); let last;
    for (let i = 0; i < 12; i++) last = await call("/logs", { ip, headers: basic("guess" + i) });
    expect(last.status).toBe(429);
    expect((await call("/logs", { ip, headers: basic(OPS) })).status).toBe(429);
  });
  it("tokenAbsentDeLAPk_AucunSecretNEstExposeParLeWorker", async () => {
    const r = await call("/login", { ip: freshIp() });
    expect(await r.text()).not.toContain(OPS);
  });
});

describe("ingestion de crashs et d'événements", () => {
  it("POST_apiEvent_sansJeton_401", async () => {
    expect((await call("/api/event", { method: "POST", ip: freshIp(), json: { message: "x" } })).status).toBe(401);
  });
  it("POST_apiCrash_ancienEnTeteXCrashToken_401", async () => {
    const r = await call("/api/crash", { method: "POST", ip: freshIp(), headers: { "x-crash-token": "ancien-jeton-en-dur" }, json: { stack: "x" } });
    expect(r.status).toBe(401);
  });
  it("POST_apiEvent_appareilAppaire_stockeUnTexteNettoye", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const r = await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: {
      level: "error", tag: "xtream", message: "HTTP 403 http://h.tv/get.php?username=bob&password=hunter2 and /live/bob/hunter2/9.ts", mac: "aa:bb",
    } });
    expect(r.status).toBe(200);
    const kv = JSON.stringify(await allKv());
    expect(kv).not.toContain("hunter2");
    expect(kv).toContain("<redacted>");
  });
  it("POST_apiEvent_tropGros_413", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const r = await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "a".repeat(20_000) } });
    expect(r.status).toBe(413);
  });
  it("POST_apiCrash_tropGros_413", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const r = await call("/api/crash", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { stack: "a".repeat(100_000) } });
    expect(r.status).toBe(413);
  });
  it("POST_apiEvent_rafale_429", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    let last;
    for (let i = 0; i < 80; i++) last = await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "spam " + i } });
    expect(last.status).toBe(429);
  });
  it("POST_apiEvent_jsonInvalide_400", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const r = await call("/api/event", { method: "POST", ip: dev.ip, headers: { ...bearer(dev.token), "content-type": "application/json" }, body: "{pas du json" });
    expect(r.status).toBe(400);
  });
  it("GET_logs_xssDansUnEvenement_estEchappe", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "<img src=x onerror=alert(1)>", tag: "<script>alert(2)</script>" } });
    const html = await (await call("/logs", { ip: freshIp(), headers: basic(OPS) })).text();
    expect(html).not.toContain("<img src=x");
    expect(html).not.toContain("<script>alert(2)");
    expect(html).toContain("&lt;img src=x");
  });
});

describe("XSS, CSRF, en-têtes", () => {
  it("dashboard_nomDeFournisseurHostile_estEchappe", async () => {
    const acct = await newAccount();
    await addProvider(acct, { kind: "M3U", name: `"><script>alert(1)</script>`, url: "http://a.tv/l.m3u" });
    const html = await (await call("/", { ip: acct.ip, cookie: acct.cookie })).text();
    expect(html).not.toContain("<script>alert(1)</script>");
    expect(html).toContain("&lt;script&gt;alert(1)");
  });
  it("dashboard_cspStricte_sansUnsafeInline", async () => {
    const acct = await newAccount();
    const r = await call("/", { ip: acct.ip, cookie: acct.cookie });
    const csp = r.headers.get("content-security-policy");
    expect(csp).toContain("default-src 'none'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("form-action 'self'");
    expect(csp).not.toContain("unsafe-inline");
    const nonce = csp.match(/script-src 'nonce-([^']+)'/)[1];
    const html = await r.text();
    expect(html).toContain(`nonce="${nonce}"`);
    expect(html).not.toMatch(/\son(click|submit|load|error)=/i);
    expect(html).not.toMatch(/\sstyle="/i);
  });
  it("enTetesDeSecurite_surToutes_lesReponses", async () => {
    for (const p of ["/login", "/signup", "/api/config", "/nimporte-quoi"]) {
      const r = await call(p, { ip: freshIp() });
      expect(r.headers.get("x-content-type-options")).toBe("nosniff");
      expect(r.headers.get("strict-transport-security")).toContain("max-age=");
      expect(r.headers.get("referrer-policy")).toBe("no-referrer");
      expect(r.headers.get("cache-control")).toContain("no-store");
      expect(r.headers.get("x-frame-options")).toBe("DENY");
    }
  });
  it("cors_aucunOriginAutorisee", async () => {
    const r = await call("/api/config", { ip: freshIp(), headers: { origin: "https://evil.example" } });
    expect(r.headers.get("access-control-allow-origin")).toBeNull();
    const o = await call("/api/config", { method: "OPTIONS", ip: freshIp(), origin: "https://evil.example" });
    expect(o.headers.get("access-control-allow-origin")).toBeNull();
  });
  it("cookieDeSession_httpOnlySecureSameSiteStrictPrefixeHost", async () => {
    const ip = freshIp(); const login = `c-${crypto.randomUUID().slice(0, 8)}`;
    const r = await call("/signup", { method: "POST", ip, form: { login, password: strongPw, confirm: strongPw } });
    const sc = r.headers.get("set-cookie");
    expect(sc).toMatch(/^__Host-utv_sess=/);
    expect(sc).toMatch(/HttpOnly/); expect(sc).toMatch(/Secure/); expect(sc).toMatch(/SameSite=Strict/); expect(sc).toMatch(/Path=\//);
    expect(sc).not.toMatch(/Domain=/i);
  });
  it("POST_providers_sansCsrf_403", async () => {
    const acct = await newAccount();
    const r = await call("/providers", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { kind: "M3U", name: "x", url: "http://a.tv/x.m3u" } });
    expect(r.status).toBe(403);
  });
  it("POST_providers_originEtrangere_403", async () => {
    const acct = await newAccount();
    const r = await call("/providers", { method: "POST", ip: acct.ip, cookie: acct.cookie, origin: "https://evil.example", form: { csrf: acct.csrf, kind: "M3U", name: "x", url: "http://a.tv/x.m3u" } });
    expect(r.status).toBe(403);
  });
  it("POST_providers_csrfDUnAutreCompte_403", async () => {
    const a = await newAccount(); const b = await newAccount();
    const r = await call("/providers", { method: "POST", ip: a.ip, cookie: a.cookie, form: { csrf: b.csrf, kind: "M3U", name: "x", url: "http://a.tv/x.m3u" } });
    expect(r.status).toBe(403);
  });
  it("POST_providers_urlJavascript_refusee", async () => {
    const acct = await newAccount();
    const r = await addProvider(acct, { kind: "M3U", name: "x", url: "javascript:alert(1)" });
    expect(r.status).toBe(302);
    expect(r.headers.get("location")).toContain("e=");
    expect(await (await call("/", { ip: acct.ip, cookie: acct.cookie })).text()).not.toContain("javascript:alert");
  });
  it("sessionForgee_rejetee", async () => {
    const acct = await newAccount();
    const forged = acct.cookie.replace(/.$/, (c) => (c === "A" ? "B" : "A"));
    const r = await call("/", { ip: freshIp(), cookie: forged });
    expect(r.status).toBe(302);
    expect(r.headers.get("location")).toBe("/login");
  });
  it("changementDeMotDePasse_invalideLesAnciennesSessions", async () => {
    const acct = await newAccount();
    const r = await call("/password", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, current: strongPw, password: "another-strong-pw-1" } });
    expect(r.status).toBe(302);
    const after = await call("/", { ip: acct.ip, cookie: acct.cookie });
    expect(after.headers.get("location")).toBe("/login");
  });
});

describe("secrets au repos", () => {
  it("kv_motDePasseFournisseur_chiffre_etMotDePasseCompteHache", async () => {
    const acct = await newAccount();
    await addProvider(acct, { kind: "XTREAM", name: "Prov", url: "http://h.tv:80", username: "bobuser-visible", password: "hunter2-visible" });
    const dev = await pairDevice(acct);
    const kv = JSON.stringify(await allKv());
    expect(kv).not.toContain("hunter2-visible");
    expect(kv).not.toContain("bobuser-visible");
    expect(kv).not.toContain(strongPw);
    expect(kv).not.toContain(dev.token);
    expect(kv).toContain("pbkdf2$sha256$100000$");
    expect(kv).toContain("v1.");
    const cfg = await (await call("/api/config", { ip: dev.ip, headers: bearer(dev.token) })).json();
    expect(cfg.providers[0]).toMatchObject({ kind: "XTREAM", username: "bobuser-visible", password: "hunter2-visible" });
  });
  it("dashboard_neRenvoieJamaisLeMotDePasseDUnFournisseur", async () => {
    const acct = await newAccount();
    await addProvider(acct, { kind: "XTREAM", name: "Prov", url: "http://h.tv:80", username: "bob", password: "hunter2-visible" });
    expect(await (await call("/", { ip: acct.ip, cookie: acct.cookie })).text()).not.toContain("hunter2-visible");
  });
  it("jetonsEtCodes_stockesHaches", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const keys = Object.keys(await allKv());
    expect(keys.some((k) => k.startsWith("dev:"))).toBe(true);
    expect(keys.join()).not.toContain(dev.token);
  });
});

describe("migration depuis l'ancien format", () => {
  it("POST_adminMigrate_sansJeton_401", async () => {
    expect((await call("/api/admin/migrate", { method: "POST", ip: freshIp() })).status).toBe(401);
  });
  it("migration_ancienCompteProtege_loginParMac_etClairSupprime", async () => {
    // ancien format : clé = MAC en clair, mot de passe SHA-256(sel:mdp), identifiants en clair
    const salt = "aabbccddeeff00112233445566778899";
    const h = [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${salt}:old-password-1`)))].map((b) => b.toString(16).padStart(2, "0")).join("");
    await env.CONFIG.put("de:ad:be:ef:00:01", JSON.stringify({ salt, passwordHash: h, providers: [{ kind: "XTREAM", name: "Old", url: "http://o.tv", username: "olduser", password: "oldpass-visible" }] }));
    await env.CONFIG.put("de:ad:be:ef:00:02", JSON.stringify({ providers: [{ kind: "M3U", name: "NoPw", url: "http://u.tv/x.m3u?password=leaked" }] }));
    await env.CONFIG.put("event:1:abc", JSON.stringify({ message: "http://h/get.php?username=a&password=leakedlog" }));
    const m = await call("/api/admin/migrate", { method: "POST", ip: freshIp(), headers: bearer("test-admin-token-0123456789abcdef0123456789") });
    expect(m.status).toBe(200);
    const rep = await m.json();
    expect(rep.migrated).toBeGreaterThanOrEqual(1);
    expect(rep.purgedUnprotected).toBeGreaterThanOrEqual(1);
    const kv = JSON.stringify(await allKv());
    expect(kv).not.toContain("oldpass-visible");
    expect(kv).not.toContain("leaked");
    // l'utilisateur se reconnecte avec MAC + ancien mot de passe
    const ip = freshIp();
    const login = await call("/login", { method: "POST", ip, form: { login: "DE:AD:BE:EF:00:01", password: "old-password-1" } });
    expect(login.status).toBe(302);
    const cookie = cookieFrom(login);
    expect(cookie).toBeTruthy();
    expect(await (await call("/", { ip, cookie })).text()).toContain("Old");
    // et le hachage a été modernisé
    expect(JSON.stringify(await allKv())).toContain("pbkdf2$sha256$100000$");
  });
  it("migration_idempotente", async () => {
    const r1 = await (await call("/api/admin/migrate", { method: "POST", ip: freshIp(), headers: bearer("test-admin-token-0123456789abcdef0123456789") })).json();
    const r2 = await (await call("/api/admin/migrate", { method: "POST", ip: freshIp(), headers: bearer("test-admin-token-0123456789abcdef0123456789") })).json();
    expect(r2.migrated).toBe(0);
    expect(r1).toHaveProperty("done");
  });
  it("signup_macHeriteeNonMigree_refuseAuxTiers", async () => {
    await env.CONFIG.put("de:ad:be:ef:00:09", JSON.stringify({ salt: "s", passwordHash: "h", providers: [] }));
    const r = await call("/signup", { method: "POST", ip: freshIp(), form: { login: "DE:AD:BE:EF:00:09", password: strongPw, confirm: strongPw } });
    expect(r.headers.get("location")).toContain("e=taken");
    expect(cookieFrom(r)).toBeNull();
  });
});
