import { describe, it, expect } from "vitest";
import { call, freshIp, newAccount, bearer, cookieFrom, strongPw } from "./helpers.js";

describe("parcours normal : compte, appairage, fournisseur, lecture", () => {
  it("parcoursComplet", async () => {
    const acct = await newAccount("alice");
    // la TV demande un code
    const tvIp = freshIp();
    const start = await (await call("/api/pair/start", { method: "POST", ip: tvIp, json: { label: "02:11:22:33:44:55" } })).json();
    expect(start.code).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    expect(start.pollSecret).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(start.expiresIn).toBeGreaterThan(0);
    // avant confirmation : en attente
    const pending = await call("/api/pair/poll", { method: "POST", ip: tvIp, json: { code: start.code, pollSecret: start.pollSecret } });
    expect(pending.status).toBe(202);
    // l'utilisateur saisit le code (minuscules et sans tiret acceptés)
    const typed = start.code.replace("-", "").toLowerCase();
    const ok = await call("/pair", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, code: typed, name: "Salon" } });
    expect(ok.status).toBe(302);
    expect(ok.headers.get("location")).not.toContain("e=");
    // la TV récupère son jeton 256 bits
    const done = await call("/api/pair/poll", { method: "POST", ip: tvIp, json: { code: start.code, pollSecret: start.pollSecret } });
    expect(done.status).toBe(200);
    const { token, deviceId } = await done.json();
    expect(token).toMatch(/^utv_[A-Za-z0-9_-]{43}$/);
    expect(deviceId).toBeTruthy();
    // le jeton n'est livré qu'une fois
    expect((await call("/api/pair/poll", { method: "POST", ip: tvIp, json: { code: start.code, pollSecret: start.pollSecret } })).status).toBe(404);
    // ajout d'un fournisseur factice
    const add = await call("/providers", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, kind: "XTREAM", name: "Factice", url: "http://fake.invalid:8080", username: "demo", password: "demo-pass" } });
    expect(add.status).toBe(302);
    // l'app récupère la config
    const cfg = await call("/api/config", { ip: tvIp, headers: bearer(token) });
    expect(cfg.status).toBe(200);
    expect(cfg.headers.get("access-control-allow-origin")).toBeNull();
    // Les champs de synchro (id, affectation, origine, dates) s'ajoutent ; les anciens champs restent identiques.
    expect((await cfg.json()).providers).toMatchObject([
      { kind: "XTREAM", name: "Factice", url: "http://fake.invalid:8080", username: "demo", password: "demo-pass", mac: "", sharedWith: "all" },
    ]);
    // le tableau de bord liste l'appareil
    const dash = await (await call("/", { ip: acct.ip, cookie: acct.cookie })).text();
    expect(dash).toContain("Salon");
    expect(dash).toContain("02:11:22:33:44:55");
  });

  it("login_apresDeconnexion_ok", async () => {
    const acct = await newAccount("bob-login");
    const out = await call("/logout", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(out.status).toBe(302);
    const r = await call("/login", { method: "POST", ip: freshIp(), form: { login: "bob-login", password: strongPw } });
    expect(r.status).toBe(302);
    expect(cookieFrom(r)).toBeTruthy();
  });

  it("signup_loginDejaPris_refuse", async () => {
    await newAccount("taken-login");
    const r = await call("/signup", { method: "POST", ip: freshIp(), form: { login: "taken-login", password: strongPw, confirm: strongPw } });
    expect(r.headers.get("location")).toContain("e=taken");
    expect(cookieFrom(r)).toBeNull();
  });

  it("signup_motDePasseCourt_refuse", async () => {
    const r = await call("/signup", { method: "POST", ip: freshIp(), form: { login: "short-pw", password: "short", confirm: "short" } });
    expect(r.headers.get("location")).toContain("e=short");
  });

  it("pairConfirm_codeDejaUtilise_refuse", async () => {
    const a = await newAccount(); const b = await newAccount();
    const start = await (await call("/api/pair/start", { method: "POST", ip: freshIp(), json: {} })).json();
    expect((await call("/pair", { method: "POST", ip: a.ip, cookie: a.cookie, form: { csrf: a.csrf, code: start.code, name: "A" } })).headers.get("location")).not.toContain("e=");
    expect((await call("/pair", { method: "POST", ip: b.ip, cookie: b.cookie, form: { csrf: b.csrf, code: start.code, name: "B" } })).headers.get("location")).toContain("e=code");
  });

  it("pairPoll_mauvaisSecret_refuse_etNeVolePasLeJeton", async () => {
    const acct = await newAccount();
    const tvIp = freshIp();
    const start = await (await call("/api/pair/start", { method: "POST", ip: tvIp, json: {} })).json();
    await call("/pair", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, code: start.code, name: "T" } });
    const steal = await call("/api/pair/poll", { method: "POST", ip: freshIp(), json: { code: start.code, pollSecret: "x".repeat(43) } });
    expect(steal.status).toBe(404);
    const real = await call("/api/pair/poll", { method: "POST", ip: tvIp, json: { code: start.code, pollSecret: start.pollSecret } });
    expect(real.status).toBe(200);
  });

  it("pairStart_rafale_limiteParIp", async () => {
    const ip = freshIp(); let last;
    for (let i = 0; i < 15; i++) last = await call("/api/pair/start", { method: "POST", ip, json: {} });
    expect(last.status).toBe(429);
  });

  it("pairStart_etiquetteHostile_nettoyee", async () => {
    const acct = await newAccount(); const tvIp = freshIp();
    const start = await (await call("/api/pair/start", { method: "POST", ip: tvIp, json: { label: "<script>x</script>" } })).json();
    await call("/pair", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, code: start.code, name: "<b>n</b>" } });
    const html = await (await call("/", { ip: acct.ip, cookie: acct.cookie })).text();
    expect(html).not.toContain("<script>x</script>");
    expect(html).not.toContain("<b>n</b>");
  });

  it("deviceRotate_nouveauJetonEtAncienInvalide", async () => {
    const acct = await newAccount(); const tvIp = freshIp();
    const start = await (await call("/api/pair/start", { method: "POST", ip: tvIp, json: {} })).json();
    await call("/pair", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, code: start.code, name: "T" } });
    const { token } = await (await call("/api/pair/poll", { method: "POST", ip: tvIp, json: { code: start.code, pollSecret: start.pollSecret } })).json();
    const rot = await call("/api/device/rotate", { method: "POST", ip: tvIp, headers: bearer(token) });
    expect(rot.status).toBe(200);
    const { token: t2 } = await rot.json();
    expect(t2).not.toBe(token);
    expect((await call("/api/config", { ip: tvIp, headers: bearer(token) })).status).toBe(401);
    expect((await call("/api/config", { ip: tvIp, headers: bearer(t2) })).status).toBe(200);
  });

  it("suppressionDuCompte_revoqueLesAppareils", async () => {
    const acct = await newAccount(); const tvIp = freshIp();
    const start = await (await call("/api/pair/start", { method: "POST", ip: tvIp, json: {} })).json();
    await call("/pair", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, code: start.code, name: "T" } });
    const { token } = await (await call("/api/pair/poll", { method: "POST", ip: tvIp, json: { code: start.code, pollSecret: start.pollSecret } })).json();
    const del = await call("/account/delete", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, password: strongPw } });
    expect(del.status).toBe(302);
    expect((await call("/api/config", { ip: tvIp, headers: bearer(token) })).status).toBe(401);
  });

  it("suppressionDeFournisseur_parIdentifiant", async () => {
    const acct = await newAccount(); const tvIp = freshIp();
    await call("/providers", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, kind: "M3U", name: "ToDelete", url: "http://a.tv/x.m3u" } });
    const html = await (await call("/", { ip: acct.ip, cookie: acct.cookie })).text();
    const id = html.match(/\/providers\/([0-9a-f]{8})\/delete/)[1];
    const del = await call(`/providers/${id}/delete`, { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(del.status).toBe(302);
    expect(await (await call("/", { ip: acct.ip, cookie: acct.cookie })).text()).not.toContain("ToDelete");
    expect(tvIp).toBeTruthy();
  });
});
