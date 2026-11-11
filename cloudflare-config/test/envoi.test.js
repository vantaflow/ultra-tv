import { describe, expect, it } from "vitest";
import { env } from "cloudflare:workers";
import { call, freshIp, allKv, newAccount } from "./helpers.js";

const start = async (extra = {}, ip = freshIp()) => {
  const res = await call("/api/envoi/start", { method: "POST", ip, json: extra });
  return { res, ip, ...(await res.json()) };
};
const send = (code, fields, ip = freshIp()) => call("/api/envoi/send", { method: "POST", ip, json: { code, ...fields } });
const poll = (code, secret, ip = freshIp()) => call("/api/envoi/poll", { method: "POST", ip, json: { code, secret } });
const XTREAM = { kind: "XTREAM", name: "Salon", url: "http://serveur.test:8080", username: "alice", password: "s3cret-pw" };

describe("envoi d'une source depuis le web (sans compte)", () => {
  it("création de code : 6 caractères lisibles (sans 0/O/1/I) et un secret", async () => {
    const s = await start();
    expect(s.res.status).toBe(200);
    expect(s.code).toMatch(/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/);
    expect(s.secret.length).toBeGreaterThanOrEqual(32);
    expect(s.expiresIn).toBe(900);
  });

  it("parcours complet : envoi, remise à la TV une seule fois, rien d'exploitable en KV", async () => {
    const s = await start();
    expect((await poll(s.code, s.secret)).status).toBe(202); // rien encore
    const sent = await send(s.code.toLowerCase(), XTREAM);
    expect(sent.status).toBe(200);
    const got = await poll(s.code, s.secret);
    expect(got.status).toBe(200);
    const body = await got.json();
    expect(body.provider).toEqual({ kind: "XTREAM", name: "Salon", url: "http://serveur.test:8080", username: "alice", password: "s3cret-pw" });
    // Remise unique : la deuxième interrogation ne trouve plus rien.
    expect((await poll(s.code, s.secret)).status).toBe(404);
    expect(JSON.stringify(await allKv())).not.toContain("s3cret-pw");
  });

  it("M3U et Stalker (MAC normalisée) ; nom vide quand non fourni", async () => {
    const a = await start();
    await send(a.code, { kind: "M3U", url: "https://exemple.test/liste.m3u" });
    expect((await (await poll(a.code, a.secret)).json()).provider).toEqual({ kind: "M3U", name: "", url: "https://exemple.test/liste.m3u", username: "", password: "" });
    const b = await start();
    await send(b.code, { kind: "STALKER", url: "http://portail.test/c/", mac: "00-1a-79-ab-cd-ef" });
    const p = (await (await poll(b.code, b.secret)).json()).provider;
    expect(p.kind).toBe("STALKER");
    expect(p.mac).toBe("00:1A:79:AB:CD:EF");
  });

  it("adresse de guide XMLTV (GUIDE) : http(s) finissant par .xml ou .xml.gz, query tolérée", async () => {
    const a = await start();
    expect((await send(a.code, { kind: "GUIDE", url: "https://guide.test/epg.xml.gz?token=abc" })).status).toBe(200);
    expect((await (await poll(a.code, a.secret)).json()).provider).toEqual({ kind: "GUIDE", name: "", url: "https://guide.test/epg.xml.gz?token=abc", username: "", password: "" });
    const b = await start();
    const err = async (url) => { const r = await send(b.code, { kind: "GUIDE", url }); return [r.status, (await r.json()).error]; };
    expect(await err("ftp://guide.test/epg.xml")).toEqual([400, "url"]);
    expect(await err("https://guide.test/epg")).toEqual([400, "url"]);
    expect(await err("https://guide.test/epg.gz")).toEqual([400, "url"]);
    expect(await err("")).toEqual([400, "url"]);
  });

  it("le secret est obligatoire pour récupérer (le code seul ne suffit pas)", async () => {
    const s = await start();
    await send(s.code, XTREAM);
    expect((await poll(s.code, "")).status).toBe(404);
    expect((await poll(s.code, "mauvais-secret")).status).toBe(404);
    expect((await poll(s.code, s.secret)).status).toBe(200); // l'envoi n'a pas été consommé par les essais ratés
  });

  it("validations : type, adresse http(s), identifiants, MAC", async () => {
    const s = await start();
    const err = async (f) => { const r = await send(s.code, f); return [r.status, (await r.json()).error]; };
    expect(await err({ ...XTREAM, kind: "FTP" })).toEqual([400, "kind"]);
    expect(await err({ ...XTREAM, url: "ftp://x.test" })).toEqual([400, "url"]);
    expect(await err({ ...XTREAM, url: "javascript:alert(1)" })).toEqual([400, "url"]);
    expect(await err({ ...XTREAM, password: "" })).toEqual([400, "creds"]);
    expect(await err({ kind: "STALKER", url: "http://p.test/c/", mac: "xyz" })).toEqual([400, "mac"]);
    expect(await err({ ...XTREAM, username: { a: 1 } })).toEqual([400, "username"]);
    // Aucune de ces tentatives n'a consommé le code.
    expect((await send(s.code, XTREAM)).status).toBe(200);
  });

  it("un code ne reçoit qu'un envoi ; un code inconnu ou mal formé est refusé", async () => {
    const s = await start();
    expect((await send(s.code, XTREAM)).status).toBe(200);
    const again = await send(s.code, XTREAM);
    expect(again.status).toBe(409);
    expect((await again.json()).error).toBe("used");
    expect((await send("AAAAAA", XTREAM)).status).toBe(404);
    expect((await send("12", XTREAM)).status).toBe(404);
  });

  it("anti-devinette : verrouillage par IP après des codes inconnus répétés", async () => {
    const ip = freshIp();
    let last;
    for (let i = 0; i < 12; i++) last = await send("ZZZZZ" + "ABCDEFGH"[i % 8], XTREAM, ip);
    expect(last.status).toBe(429);
    expect(last.headers.get("retry-after")).toBeTruthy();
  });

  it("limite de débit sur la création de code", async () => {
    const ip = freshIp();
    let last;
    for (let i = 0; i < 21; i++) last = await call("/api/envoi/start", { method: "POST", ip, json: {} });
    expect(last.status).toBe(429);
  });

  it("envoi refusé depuis une autre origine", async () => {
    const s = await start();
    const r = await call("/api/envoi/send", { method: "POST", ip: freshIp(), origin: "https://evil.test", json: { code: s.code, ...XTREAM } });
    expect(r.status).toBe(403);
  });

  it("la page /envoyer est publique, échappe le code et porte une CSP", async () => {
    const res = await call("/envoyer?c=k7q-2m9&l=fr");
    expect(res.status).toBe(200);
    expect(res.headers.get("content-security-policy")).toContain("script-src 'nonce-");
    const html = await res.text();
    expect(html).toContain('value="K7Q2M9"');
    expect(html).toContain("Envoyer");
    const bad = await (await call('/envoyer?c="><script>alert(1)</script>')).text();
    expect(bad).not.toContain("<script>alert(1)");
    expect(bad).toContain('value=""');
  });
});

describe("installations sans compte (relevé anonyme)", () => {
  const install = () => crypto.randomUUID();
  const ping = (body, ip = freshIp()) => call("/api/anon/ping", { method: "POST", ip, json: body });

  it("enregistre une installation (hachée), version et pays, sans identifiant en clair", async () => {
    const id = install();
    const r = await ping({ install: id, version: "1.2.80" });
    expect(r.status).toBe(200);
    const keys = (await env.CONFIG.list({ prefix: "anon:" })).keys;
    const mine = keys.find((k) => k.metadata && k.metadata.v === "1.2.80");
    expect(mine).toBeTruthy();
    expect(mine.name).toMatch(/^anon:[0-9a-f]{32}$/);
    expect(JSON.stringify(await allKv())).not.toContain(id);
    expect(mine.metadata.f).toBeGreaterThan(0);
  });

  it("identifiant invalide refusé", async () => {
    expect((await ping({ install: "court" })).status).toBe(400);
    expect((await ping({})).status).toBe(400);
  });

  it("limite de débit par IP", async () => {
    const ip = freshIp();
    let last;
    for (let i = 0; i < 31; i++) last = await ping({ install: install() }, ip);
    expect(last.status).toBe(429);
  });

  it("un envoi web remis à la TV compte pour l'installation", async () => {
    const id = install();
    await ping({ install: id, version: "1.2.81" });
    const s = await start({ install: id });
    await send(s.code, XTREAM);
    await poll(s.code, s.secret);
    const all = (await env.CONFIG.list({ prefix: "anon:" })).keys;
    expect(all.some((k) => k.metadata.v === "1.2.81" && k.metadata.w === 1)).toBe(true);
  });

  it("statistiques d'administration : totaux, actives, nouvelles, sources web, versions et pays", async () => {
    const id = install();
    await ping({ install: id, version: "9.9.9" });
    const s = await start({ install: id });
    await send(s.code, XTREAM);
    await poll(s.code, s.secret);
    const admin = await newAccount("khalilbenaz").catch(() => null);
    // Le compte admin peut déjà exister (autre test) : on se reconnecte alors.
    let acct = admin;
    if (!acct) {
      const ip = freshIp();
      const login = await call("/login", { method: "POST", ip, form: { login: "khalilbenaz", password: "correct-horse-battery" } });
      const cookie = (login.headers.get("set-cookie") || "").match(/(__Host-utv_sess)=([^;]*)/);
      acct = { cookie: `${cookie[1]}=${cookie[2]}`, ip };
    }
    const page = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(page).toContain("Installations sans compte");
    expect(page).toContain("Sources envoyées depuis le web");
    expect(page).toContain("v9.9.9");
    expect(page).toContain("dont 1 sans compte");
    expect(page).not.toContain(id);
    expect(page).not.toContain("s3cret-pw");
  });
});
