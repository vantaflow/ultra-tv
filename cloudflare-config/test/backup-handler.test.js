import { describe, expect, it } from "vitest";
import worker from "../src/index.js";
import { classify, createHandler, isStateRead, withClientIp } from "../backup/src/handler.js";
import { buildEnv } from "../backup/src/env.js";

const VARS = { TURSO_URL: "libsql://x.test", TURSO_AUTH_TOKEN: "tok", PROVIDER_ENC_KEY: "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" };
const BASE = "https://utv-backup.example.test";
const emptyConn = { batch: async (steps) => steps.map(() => ({ columns: [], rows: [] })), exec: async () => {} };
const real = createHandler({ worker, build: (p) => buildEnv(p, { connect: () => emptyConn }) });
const req = (path, init) => new Request(BASE + path, init);

describe("secours : liste blanche", () => {
  it("classe les routes doublées et rien d'autre", () => {
    expect(classify("GET", "/api/config")).toBe("config");
    expect(classify("GET", "/api/announcements")).toBe("announcements");
    expect(classify("POST", "/api/device/providers/0123abcd/state")).toBe("state");
    expect(classify("GET", "/api/backup/health")).toBe("health");
    for (const [m, p] of [["POST", "/api/config"], ["POST", "/api/pair/start"], ["POST", "/api/pair/poll"], ["GET", "/"], ["GET", "/admin"],
      ["GET", "/login"], ["POST", "/api/device/providers"], ["POST", "/api/device/rotate"], ["POST", "/api/event"], ["GET", "/api/tmdb/x"],
      ["GET", "/api/device/trakt/library"], ["POST", "/api/device/profiles"], ["GET", "/api/config/aa:bb"]]) expect(classify(m, p)).toBeNull();
  });
  it("une écriture d'état n'est pas une lecture", () => {
    expect(isStateRead({})).toBe(true);
    expect(isStateRead({ fav: [], hist: [] })).toBe(true);
    expect(isStateRead({ fav: [{ k: 1 }] })).toBe(false);
    expect(isStateRead({ hist: [{}] })).toBe(false);
    expect(isStateRead(null)).toBe(false);
    expect(isStateRead([])).toBe(false);
  });
  it("l'IP cliente de Vercel devient cf-connecting-ip, sans accepter celle de l'appelant", () => {
    const h = withClientIp({ "cf-connecting-ip": "6.6.6.6", "x-forwarded-for": "1.2.3.4, 10.0.0.1" });
    expect(h.get("cf-connecting-ip")).toBe("1.2.3.4");
    expect(withClientIp({ "cf-connecting-ip": "6.6.6.6" }).get("cf-connecting-ip")).toBeNull();
  });
});

describe("secours : gestionnaire", () => {
  it("sans configuration : 503 « non configuré » avec les noms manquants, jamais de valeur", async () => {
    const r = await real(req("/api/config", { headers: { authorization: "Bearer utv_x" } }), { TURSO_AUTH_TOKEN: "secret-valeur" });
    expect(r.status).toBe(503);
    const text = await r.text();
    const body = JSON.parse(text);
    expect(body.error).toBe("backup_not_configured");
    expect(body.missing).toEqual(["TURSO_URL", "PROVIDER_ENC_KEY"]);
    expect(text).not.toContain("secret-valeur");
    expect(r.headers.get("x-utv-backup")).toBe("1");
  });
  it("santé : 200 sans secret, indique si c'est configuré", async () => {
    expect((await (await real(req("/api/backup/health"), {})).json())).toEqual({ ok: true, configured: false, readOnly: true });
    expect((await (await real(req("/api/backup/health"), VARS)).json()).configured).toBe(true);
  });
  it("configuré : route non doublée → 503 backup_unavailable", async () => {
    for (const [m, p] of [["POST", "/api/pair/start"], ["GET", "/admin"], ["POST", "/api/device/providers"]]) {
      const r = await real(req(p, { method: m }), VARS);
      expect(r.status).toBe(503);
      expect((await r.json()).error).toBe("backup_unavailable");
    }
  });
  it("annonces : servies par le vrai Worker (liste vide), marquées secours", async () => {
    const r = await real(req("/api/announcements?audience=tv"), VARS);
    expect(r.status).toBe(200);
    expect(await r.json()).toEqual({ items: [] });
    expect(r.headers.get("x-utv-backup")).toBe("1");
  });
  it("config : jeton absent ou inconnu → 401 du Worker (même contrat que le principal)", async () => {
    expect((await real(req("/api/config"), VARS)).status).toBe(401);
    expect((await real(req("/api/config", { headers: { authorization: "Bearer utv_inconnu" } }), VARS)).status).toBe(401);
  });
  it("état : une écriture est refusée en 503 sans appeler le Worker ; une lecture passe", async () => {
    let called = 0;
    const h = createHandler({ worker: { fetch: async () => { called++; return new Response("{}"); } }, build: () => ({}) });
    const path = "/api/device/providers/0123abcd/state";
    const w = await h(req(path, { method: "POST", body: JSON.stringify({ fav: [{ id: 1 }] }) }), VARS);
    expect(w.status).toBe(503);
    expect((await w.json()).error).toBe("backup_read_only");
    expect(called).toBe(0);
    const rd = await h(req(path, { method: "POST", body: JSON.stringify({ fav: [], hist: [] }) }), VARS);
    expect(rd.status).toBe(200);
    expect(called).toBe(1);
  });
  it("transmet la méthode, le chemin et l'IP au Worker", async () => {
    let seen;
    const h = createHandler({ worker: { fetch: async (r) => { seen = r; return new Response("{}"); } }, build: () => ({}) });
    await h(req("/api/config", { headers: { "x-real-ip": "9.9.9.9", authorization: "Bearer utv_a" } }), VARS);
    expect(new URL(seen.url).pathname).toBe("/api/config");
    expect(seen.headers.get("cf-connecting-ip")).toBe("9.9.9.9");
    expect(seen.headers.get("authorization")).toBe("Bearer utv_a");
  });
});
