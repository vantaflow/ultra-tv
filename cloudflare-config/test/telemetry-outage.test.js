// Panne de la base de télémétrie (quota Turso épuisé, D1 indisponible) : la flotte ne doit pas réessayer en boucle.
// L'appli réessaie tout code >= 500 sans lire Retry-After, et une file pleine déclenche un envoi à chaque nouvelle ligne
// (RemoteLog.kt) ; chaque essai consomme le quota de requêtes Worker partagé avec l'appairage et la synchro. Donc :
// - journaux (/api/event, /api/events, /api/anon/events) : 200 avec le bilan `dropped.storage` (l'appli les oublie) ;
// - plantages (/api/crash, /api/anon/crash) : 503 (l'appli ne les renvoie qu'au démarrage suivant, sans boucle) ;
// - coupe-circuit : pendant 60 s après un échec d'écriture, les journaux ne sollicitent plus la base du tout.
import { describe, it, expect, vi, afterEach } from "vitest";
import { env } from "cloudflare:workers";
import worker from "../src/index.js";
import { __resetTelemetryBreaker, BREAKER_MS } from "../src/telemetry.js";
import { __setTursoConnect, KV_TABLE_SQL } from "../src/turso.js";
import { freshIp, ORIGIN, newAccount, pairDevice, bearer } from "./helpers.js";

const post = (e, path, json, headers = {}) => worker.fetch(new Request(ORIGIN + path, {
  method: "POST", headers: { "cf-connecting-ip": freshIp(), origin: ORIGIN, "content-type": "application/json", ...headers }, body: JSON.stringify(json),
}), e);
const anonEvents = (e, version, n = 1) => post(e, "/api/anon/events", { install: crypto.randomUUID(), version, events: Array.from({ length: n }, (_, i) => ({ level: "error", message: `l${i}-${Math.random()}` })) });
const anonCrash = (e, version) => post(e, "/api/anon/crash", { install: crypto.randomUUID(), version, stack: `s-${Math.random()}` });
const rowsOf = async (version) => (await env.DB.prepare("SELECT * FROM events WHERE version = ?").bind(version).all()).results;

/** Base D1 dont les écritures dans `events` échouent tant que `down.on` ; compte les tentatives. */
function flakyD1() {
  const down = { on: true, attempts: 0 };
  const isEventWrite = (sql) => /INSERT INTO events/.test(sql);
  const db = new Proxy(env.DB, {
    get(t, k) {
      if (k === "prepare") return (sql) => {
        if (isEventWrite(sql)) { down.attempts++; if (down.on) throw new Error("base indisponible"); }
        return t.prepare(sql);
      };
      const v = t[k];
      return typeof v === "function" ? v.bind(t) : v;
    },
  });
  return { down, db };
}

/** Connexion Turso simulée (SQLite local des tests) dont les écritures dans `events` échouent tant que `down.on`. */
function flakyTurso(down) {
  return () => ({
    async batch(steps) {
      if (steps.some((s) => /INSERT INTO events/.test(typeof s === "string" ? s : s.sql))) {
        down.attempts++;
        if (down.on) throw new Error("SQL_INPUT_ERROR: quota exceeded");
      }
      const stmts = steps.map((s) => (typeof s === "string" ? env.DB.prepare(s) : env.DB.prepare(s.sql).bind(...(s.args || []))));
      return (await env.DB.batch(stmts)).map((r) => ({
        columns: r.results?.length ? Object.keys(r.results[0]) : [], rows: r.results || [],
        rowsAffected: r.meta?.changes ?? 0, lastInsertRowid: r.meta?.last_row_id ?? null,
      }));
    },
    async exec(sql) { await env.DB.exec(sql); },
  });
}

afterEach(() => { __resetTelemetryBreaker(); vi.restoreAllMocks(); });

// STORAGE_MODE "d1" : l'adaptateur D1 même sous `npm run test:turso` (sinon la base remplacée ici serait ignorée).
describe("panne de la base (adaptateur D1)", () => {
  it("journaux : 200 et dropped.storage ; plantages : 503 avec Retry-After", async () => {
    const { db } = flakyD1();
    const e = { ...env, DB: db, STORAGE_MODE: "d1" };
    const r = await anonEvents(e, "9.3.1", 3);
    expect(r.status).toBe(200);
    expect(await r.json()).toEqual({ ok: false, accepted: 3, stored: 0, dropped: { filtered: 0, invalid: 0, overflow: 0, storage: 3 } });
    const c = await anonCrash(e, "9.3.1");
    expect(c.status).toBe(503);
    expect(c.headers.get("retry-after")).toBe("60");
    expect(await rowsOf("9.3.1")).toEqual([]);
  });

  // Sous test:turso, les comptes sont dans la table KV de Turso : forcer D1 les rendrait invisibles.
  it.skipIf(Boolean(env.TURSO_AUTH_TOKEN))("routes appairées : /api/event et /api/events en 200 dropped.storage, /api/crash en 503", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const { db } = flakyD1();
    const e = { ...env, DB: db, STORAGE_MODE: "d1" };
    const h = { ...bearer(dev.token) };
    const one = await post(e, "/api/event", { version: "9.4.1", level: "error", message: "m" }, h);
    expect(one.status).toBe(200);
    expect(await one.json()).toEqual({ ok: false, stored: 0, dropped: { filtered: 0, storage: 1 } });
    const many = await post(e, "/api/events", { version: "9.4.1", events: [{ level: "error", message: "a" }, { level: "error", message: "b" }] }, h);
    expect(many.status).toBe(200);
    expect(await many.json()).toEqual({ ok: false, accepted: 2, stored: 0, dropped: { filtered: 0, invalid: 0, storage: 2 } });
    expect((await post(e, "/api/crash", { version: "9.4.1", stack: "s" }, h)).status).toBe(503);
  });

  it("coupe-circuit : après un échec, plus aucune écriture de journal pendant 60 s ; les plantages essaient toujours", async () => {
    const { down, db } = flakyD1();
    const e = { ...env, DB: db, STORAGE_MODE: "d1" };
    expect((await anonEvents(e, "9.5.1")).status).toBe(200);
    expect(down.attempts).toBe(1);
    // La base revient, mais le coupe-circuit est ouvert : réponse immédiate, sans appel à la base.
    down.on = false;
    for (let i = 0; i < 5; i++) {
      const r = await anonEvents(e, "9.5.1", 2);
      expect(r.status).toBe(200);
      expect((await r.json()).dropped.storage).toBe(2);
    }
    expect(down.attempts).toBe(1);
    // Un plantage, lui, tente l'écriture (et passe).
    expect((await anonCrash(e, "9.5.1")).status).toBe(200);
    expect(down.attempts).toBe(2);
    // 60 s plus tard, les journaux sont de nouveau écrits.
    const now = Date.now();
    vi.spyOn(Date, "now").mockReturnValue(now + BREAKER_MS + 1);
    const r = await anonEvents(e, "9.5.1");
    expect(await r.json()).toMatchObject({ ok: true, stored: 1 });
    const rows = await rowsOf("9.5.1");
    expect(rows.filter((x) => x.kind === "event").length).toBe(1);
    expect(rows.filter((x) => x.kind === "crash").length).toBe(1);
  });
});

describe("panne de la base (adaptateur Turso, celui de la production)", () => {
  it("quota épuisé : journaux 200 dropped.storage, plantages 503, coupe-circuit, puis reprise", async () => {
    await env.DB.prepare(KV_TABLE_SQL).run();
    const down = { on: true, attempts: 0 };
    __setTursoConnect(flakyTurso(down));
    const e = { ...env, STORAGE_MODE: "turso", TURSO_URL: "libsql://simule.test", TURSO_AUTH_TOKEN: "simule" };
    try {
      const r = await anonEvents(e, "9.6.1", 2);
      expect(r.status).toBe(200);
      expect(await r.json()).toMatchObject({ ok: false, stored: 0, dropped: { storage: 2 } });
      expect(down.attempts).toBe(1);
      const c = await anonCrash(e, "9.6.1");
      expect(c.status).toBe(503);
      expect(c.headers.get("retry-after")).toBe("60");
      expect(down.attempts).toBe(2);
      // Coupe-circuit ouvert : aucune requête Turso pour les journaux.
      for (let i = 0; i < 3; i++) expect((await anonEvents(e, "9.6.1")).status).toBe(200);
      expect(down.attempts).toBe(2);
      expect(await rowsOf("9.6.1")).toEqual([]);
      // Base revenue et coupe-circuit refermé : écriture normale.
      down.on = false;
      __resetTelemetryBreaker();
      expect(await (await anonEvents(e, "9.6.1")).json()).toMatchObject({ ok: true, stored: 1 });
      expect((await anonCrash(e, "9.6.1")).status).toBe(200);
      expect((await rowsOf("9.6.1")).length).toBe(2);
    } finally {
      // Connexion simulée saine (celle du mode Turso des tests ; sans effet en mode D1, où aucun jeton Turso n'est défini).
      __setTursoConnect(flakyTurso({ on: false, attempts: 0 }));
    }
  });
});

describe("relevé anonyme des tests unitaires (Robolectric)", () => {
  it("/api/anon/ping avec un modèle robolectric : 200 sans rien écrire", async () => {
    const install = crypto.randomUUID();
    const r = await post(env, "/api/anon/ping", { install, version: "9.7.1", platform: "android-tv", model: "robolectric robolectric" });
    expect(r.status).toBe(200);
    const n = await env.DB.prepare("SELECT COUNT(*) AS n FROM anon_pings WHERE version = ?").bind("9.7.1").first("n");
    expect(n).toBe(0);
  });
  it("modèle d'appareil nettoyé comme un journal (e-mail, MAC)", async () => {
    const r = await post(env, "/api/anon/ping", { install: crypto.randomUUID(), version: "9.7.2", platform: "android-tv", model: "Box zzuserzz@example.invalid 00:1A:79:AB:CD:EF" });
    expect(r.status).toBe(200);
    const m = await env.DB.prepare("SELECT model FROM anon_pings WHERE version = ?").bind("9.7.2").first("model");
    expect(m).toMatch(/^Box /);
    expect(m.toLowerCase()).not.toContain("zzuserzz");
    expect(m).not.toContain("00:1A");
  });
});
