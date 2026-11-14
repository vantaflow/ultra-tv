import { describe, expect, it } from "vitest";
import { env } from "cloudflare:workers";
import { call, newAccount, pairDevice, freshIp } from "./helpers.js";

/** Compte admin : ADMIN_LOGINS = "khalilbenaz" (wrangler.toml), créé via le parcours d'inscription normal. */
let adminP;
const adminAccount = () => (adminP ||= newAccount("khalilbenaz"));
const get = async (path, a) => call(path, { cookie: a.cookie, ip: a.ip });

const ADMIN_PATHS = ["/admin", "/admin/accounts", "/admin/anon", "/admin/devices", "/admin/alerts", "/events", "/crashes", "/admin/comptes.csv"];

describe("administration", () => {
  it("compte ordinaire : toutes les pages d'administration répondent 404 (existence non révélée), pas de lien dans le tableau de bord", async () => {
    const a = await newAccount();
    for (const p of ADMIN_PATHS) expect((await get(p, a)).status, p).toBe(404);
    const dash = await (await get("/", a)).text();
    expect(dash).not.toContain('href="/admin"');
    expect(dash).not.toContain('id="administration"');
  });
  it("sans session : renvoyé à la connexion (pages /admin), 401 pour les journaux sans secret", async () => {
    expect((await call("/admin")).headers.get("location")).toBe("/login");
    expect((await call("/admin/anon")).headers.get("location")).toBe("/login");
    expect((await call("/events", { ip: freshIp() })).status).toBe(401);
  });
  it("admin : un onglet par URL, navigation, aucune donnée sensible", async () => {
    const admin = await adminAccount();
    const dash = await (await get("/", admin)).text();
    expect(dash).toContain('href="/admin"');
    for (const p of ADMIN_PATHS.filter((x) => !x.endsWith(".csv"))) {
      const res = await get(p, admin);
      expect(res.status, p).toBe(200);
      const html = await res.text();
      for (const label of ["Vue d&#39;ensemble", "Comptes", "Sans compte", "Appareils", "Journaux", "Plantages", "Alertes"]) expect(html).toContain(`>${label}</a>`);
      expect(html).not.toMatch(/passwordHash|providersEnc|traktEnc|osEnc/);
    }
    expect(await (await get("/admin", admin)).text()).toContain('aria-current="page">Vue d&#39;ensemble</a>');
    expect(await (await get("/admin/accounts", admin)).text()).toContain('aria-current="page">Comptes</a>');
  });
  it("admin : comptes et appareils (tri, recherche, pagination), export CSV neutralisé", async () => {
    const other = await newAccount();
    await pairDevice(other, "=calc Salon");
    const admin = await adminAccount();
    const accounts = await (await get("/admin/accounts?refresh=1", admin)).text();
    expect(accounts).toContain(other.login);
    expect(accounts).toContain("=calc Salon");
    const hit = await (await get(`/admin/accounts?q=${encodeURIComponent(other.login)}`, admin)).text();
    expect(hit).toContain(other.login);
    expect(hit).toContain("1–1 sur 1");
    expect(await (await get("/admin/accounts?q=introuvable-xyz", admin)).text()).toContain("Aucun compte ne correspond");
    expect(await (await get("/admin/accounts?sort=login&dir=asc&page=999", admin)).text()).toContain('aria-sort="ascending"');
    const devices = await (await get("/admin/devices", admin)).text();
    expect(devices).toContain("=calc Salon");
    expect(devices).toContain(other.login);
    const csv = await call("/admin/comptes.csv", { cookie: admin.cookie, ip: admin.ip });
    expect(csv.headers.get("content-type")).toContain("text/csv");
    const text = await csv.text();
    expect(text).toMatch(/"'=calc Salon \(/);
    expect(text).not.toMatch(/pbkdf2|v1\.[0-9a-f]{8}\./);
  });
  it("sans compte : application, version, pays, premier et dernier relevé ; filtres SQL", async () => {
    const ping = (body) => call("/api/anon/ping", { method: "POST", ip: freshIp(), json: body });
    expect((await ping({ install: crypto.randomUUID(), version: "7.7.7", platform: "android-tv", model: "SKYWORTH 4K" })).status).toBe(200);
    expect((await ping({ install: crypto.randomUUID(), version: "7.7.8", platform: "windows" })).status).toBe(200);
    const admin = await adminAccount();
    const page = await (await get("/admin/anon", admin)).text();
    expect(page).toContain("Android TV");
    expect(page).toContain("SKYWORTH 4K");
    expect(page).toContain("v7.7.7");
    expect(page).toContain("Premier relevé");
    expect(page).toContain("Dernier relevé");
    const win = await (await get("/admin/anon?platform=windows", admin)).text();
    expect(win).toContain("<td>v7.7.8</td>");
    expect(win).not.toContain("<td>v7.7.7</td>");
    expect(await (await get("/admin/anon?version=7.7.7&p=24h", admin)).text()).toContain("SKYWORTH 4K");
    const sorted = await (await get("/admin/anon?sort=version&dir=asc", admin)).text();
    expect(sorted.indexOf("<td>v7.7.7</td>")).toBeLessThan(sorted.indexOf("<td>v7.7.8</td>"));
    // injection SQL dans la recherche et les filtres : traitée comme du texte
    expect((await get("/admin/anon?q=%27%3B+DROP+TABLE+anon_pings%3B--&platform=x%27+OR+1%3D1", admin)).status).toBe(200);
    expect((await env.DB.prepare("SELECT COUNT(*) AS n FROM anon_pings").first()).n).toBeGreaterThan(1);
  });
  it("journaux : session admin, filtres ; jeton d'exploitation toujours accepté (sans onglets)", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    await call("/api/event", { method: "POST", ip: dev.ip, headers: { authorization: `Bearer ${dev.token}` }, json: { level: "error", tag: "Lecteur", message: "boom-unique-42" } });
    const admin = await adminAccount();
    const page = await (await get("/events?level=error", admin)).text();
    expect(page).toContain("boom-unique-42");
    expect(await (await get("/events?q=absent-du-tout", admin)).text()).toContain("Aucun événement");
    expect(await (await get("/logs?q=boom-unique", admin)).text()).toContain("boom-unique-42");
    const ops = await call("/events", { ip: freshIp(), headers: { authorization: "Basic " + btoa("ops:" + env.OPS_TOKEN) } });
    expect(ops.status).toBe(200);
    expect(await ops.text()).not.toContain('class="tabs"');
  });
  it("alertes : l'onglet porte le formulaire de programmation", async () => {
    const admin = await adminAccount();
    const html = await (await get("/admin/alerts", admin)).text();
    expect(html).toContain('id="alertes"');
    expect(html).toContain('action="/admin/announcements"');
  });
  it("pays de l'inscription gardé (code à 2 lettres), jamais l'adresse IP", async () => {
    const a = await newAccount();
    const acct = await (await import("../src/store.js")).getAccount(env, a.login);
    expect(JSON.stringify(acct)).not.toContain(a.ip);
    expect(acct.country === null || /^[A-Z]{2}$/.test(acct.country)).toBe(true);
  });
});
