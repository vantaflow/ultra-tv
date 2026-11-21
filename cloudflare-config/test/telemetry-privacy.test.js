// Ingestion de bout en bout : sur chacune des routes de télémétrie, une charge pleine d'identifiants FICTIFS
// (vecteurs partagés test/fixtures/sanitize-vectors.json) ne laisse rien de sensible en base ni dans les journaux du
// Worker. Plus : lignes de tests Robolectric écartées sans écriture, échec de base signalé par un 503.
import { describe, it, expect, vi, afterEach } from "vitest";
import { env } from "cloudflare:workers";
import vectors from "./fixtures/sanitize-vectors.json";
import worker from "../src/index.js";
import { __resetTelemetryBreaker } from "../src/telemetry.js";
import { call, freshIp, newAccount, pairDevice, bearer, ORIGIN } from "./helpers.js";

const leaky = vectors.filter((v) => !v.name.startsWith("intact"));
const secrets = [...new Set(leaky.flatMap((v) => v.mustNotContain))];
const rowsOf = async (version) => (await env.DB.prepare("SELECT * FROM events WHERE version = ?").bind(version).all()).results;
const anon = (path, body) => call(path, { method: "POST", ip: freshIp(), json: { install: crypto.randomUUID(), ...body } });
const chunks = (list, n) => Array.from({ length: Math.ceil(list.length / n) }, (_, i) => list.slice(i * n, i * n + n));
// Modèle « saisi » : un nom propre nu ne peut pas être reconnu, mais e-mail, MAC et clé=valeur le sont.
const device = "Box zzuserzz@example.invalid 00:1A:79:AB:CD:EF";
const tag = "token=ZZTOKENZZ";

function expectClean(rows, { stored = true } = {}) {
  const dump = JSON.stringify(rows).toLowerCase().replace(/\\\//g, "/");
  for (const s of secrets) expect(dump, `« ${s} » stocké`).not.toContain(s.toLowerCase());
  if (stored) for (const r of rows) expect(r.mac).toBeNull();
}

afterEach(() => vi.restoreAllMocks());

describe("ingestion : aucun identifiant fictif n'atteint la base", () => {
  it("POST /api/event (appairé), message + étiquette + modèle + version", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const logs = [vi.spyOn(console, "log"), vi.spyOn(console, "error")];
    for (const v of leaky.filter((v) => /^F[1-9] /.test(v.name)).filter((_, i) => i % 8 === 0)) {
      const r = await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: {
        level: "error", tag, message: v.input.slice(0, 3900), device, version: "8.1.1 password=ZZPASSZZ", mac: "00:1A:79:AB:CD:EF",
      } });
      expect(r.status).toBe(200);
    }
    const rows = (await env.DB.prepare("SELECT * FROM events WHERE device_id = ?").bind(dev.deviceId).all()).results;
    expect(rows.length).toBeGreaterThanOrEqual(8);
    expectClean(rows);
    expect(rows[0].version).toContain("8.1.1");
    expectClean(logs.flatMap((s) => s.mock.calls), { stored: false });
  });

  it("POST /api/events (appairé) : tous les vecteurs, par lots", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    for (const part of chunks(leaky, 25)) {
      const r = await call("/api/events", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: {
        version: "8.2.1", device, mac: "00:1A:79:AB:CD:EF", events: part.map((v) => ({ level: "error", tag, message: v.input })),
      } });
      expect(r.status).toBe(200);
      const body = await r.json();
      expect(body).toMatchObject({ ok: true, accepted: part.length, stored: part.length, dropped: { invalid: 0, filtered: 0 } });
    }
    const rows = await rowsOf("8.2.1");
    expect(rows.length).toBe(leaky.length);
    expectClean(rows);
  });

  it("POST /api/crash (appairé) : pile mêlant tous les vecteurs", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const stack = leaky.map((v) => v.input).join("\n\tat x.Y.z(Y.kt:1)\n");
    expect(stack.length).toBeLessThan(32 * 1024);
    const r = await call("/api/crash", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { version: "8.3.1", device, stack, androidSdk: 34 } });
    expect(r.status).toBe(200);
    const rows = await rowsOf("8.3.1");
    expect(rows.length).toBe(1);
    expect(rows[0].stack).toContain("Y.kt:1");
    expectClean(rows);
  });

  it("POST /api/anon/events : tous les vecteurs, 20 lignes par lot", async () => {
    for (const part of chunks(leaky, 20)) {
      const r = await anon("/api/anon/events", { version: "8.4.1", device, events: part.map((v) => ({ level: "warn", tag, message: v.input })) });
      expect(r.status).toBe(200);
    }
    const rows = await rowsOf("8.4.1");
    expect(rows.length).toBe(leaky.length);
    expectClean(rows);
  });

  it("POST /api/anon/crash : pile mêlant les vecteurs (coupée à 16 Ko)", async () => {
    for (const part of chunks(leaky, 30)) {
      const r = await anon("/api/anon/crash", { version: "8.5.1", device, stack: part.map((v) => v.input).join("\n\tat x.Y.z(Y.kt:1)\n") });
      expect(r.status).toBe(200);
    }
    const rows = await rowsOf("8.5.1");
    expect(rows.length).toBe(Math.ceil(leaky.length / 30));
    expectClean(rows);
  });
});

describe("ingestion : lignes de tests unitaires (Robolectric) écartées", () => {
  const dev = "robolectric robolectric";
  it("les cinq routes répondent 2xx sans rien écrire", async () => {
    const acct = await newAccount(); const d = await pairDevice(acct);
    const auth = { method: "POST", ip: d.ip, headers: bearer(d.token) };
    const res = [
      await call("/api/event", { ...auth, json: { version: "8.6.1", device: dev, level: "error", message: "m" } }),
      await call("/api/events", { ...auth, json: { version: "8.6.1", device: dev, events: [{ level: "error", message: "a" }, { level: "error", message: "b" }] } }),
      await call("/api/crash", { ...auth, json: { version: "8.6.1", device: "Robolectric", stack: "s" } }),
      await anon("/api/anon/events", { version: "8.6.1", device: dev, events: [{ level: "error", message: "a" }] }),
      await anon("/api/anon/crash", { version: "8.6.1", device: dev, stack: "s" }),
    ];
    for (const r of res) expect(r.status).toBe(200);
    expect(await res[1].json()).toMatchObject({ ok: true, accepted: 0, stored: 0, dropped: { test: 2 } });
    expect(await rowsOf("8.6.1")).toEqual([]);
  });
});

describe("ingestion : pertes signalées, jamais silencieuses", () => {
  const failingDb = () => {
    const real = env.DB;
    return {
      real,
      db: new Proxy(real, {
        get(t, k) {
          if (k === "prepare") return (sql) => { if (/INSERT INTO events/.test(sql)) throw new Error("base indisponible"); return t.prepare(sql); };
          const v = t[k];
          return typeof v === "function" ? v.bind(t) : v;
        },
      }),
    };
  };

  it("routes sans compte : écriture refusée → journaux 200 dropped.storage, plantage 503 ; ensuite le plantage passe", async () => {
    const { real, db } = failingDb();
    env.DB = db;
    try {
      const install = crypto.randomUUID();
      const ev = await call("/api/anon/events", { method: "POST", ip: freshIp(), json: { install, version: "8.7.1", events: [{ level: "error", message: "perdue?" }] } });
      expect(ev.status).toBe(200);
      expect((await ev.json()).dropped.storage).toBe(1);
      const crash = await anon("/api/anon/crash", { version: "8.7.1", stack: "s" });
      expect(crash.status).toBe(503);
      expect(crash.headers.get("retry-after")).toBe("60");
    } finally {
      env.DB = real;
      __resetTelemetryBreaker();
    }
    expect(await rowsOf("8.7.1")).toEqual([]);
    expect((await anon("/api/anon/crash", { version: "8.7.1", stack: "s" })).status).toBe(200);
    expect((await anon("/api/anon/events", { version: "8.7.1", events: [{ level: "error", message: "perdue?" }] })).status).toBe(200);
    expect((await rowsOf("8.7.1")).length).toBe(2);
  });

  it("lot sans compte de 50 lignes : la réponse dit combien sont gardées et pourquoi les autres sont écartées", async () => {
    const events = [...Array.from({ length: 48 }, (_, i) => ({ level: "error", message: `l${i}` })), "pas un objet", null];
    const r = await anon("/api/anon/events", { version: "8.8.1", events });
    expect(r.status).toBe(200);
    expect(await r.json()).toEqual({ ok: true, accepted: 20, stored: 20, dropped: { filtered: 0, invalid: 2, overflow: 28 } });
  });
});

describe("ingestion : empreinte d'hôte salée par LOG_HOST_SALT", () => {
  const msg = (v) => `échec http://fournisseur.invalid:8080/ZZUSERZZ/ZZPASSZZ/1.ts version ${v}`;
  it("avec le secret : <host#12 hexadécimaux>, identique d'une ligne à l'autre, jamais l'hôte", async () => {
    for (const v of ["9.1.1", "9.1.1"]) {
      const r = await anon("/api/anon/events", { version: v, events: [{ level: "error", message: msg(Math.random()) }] });
      expect(r.status).toBe(200);
    }
    const rows = await rowsOf("9.1.1");
    expect(rows.length).toBe(2);
    const tags = rows.map((r) => r.message.match(/<host#([0-9a-f]{12})>\/\*\.ts/)[1]);
    expect(tags[0]).toBe(tags[1]);
    expectClean(rows);
  });

  it("sans le secret : <host> sans empreinte, signalé une seule fois dans le journal du Worker", async () => {
    const { LOG_HOST_SALT: _salt, ...noSalt } = env;
    const warn = vi.spyOn(console, "warn");
    for (let i = 0; i < 2; i++) {
      const r = await worker.fetch(new Request(`${ORIGIN}/api/anon/events`, {
        method: "POST", headers: { "cf-connecting-ip": freshIp(), origin: ORIGIN, "content-type": "application/json" },
        body: JSON.stringify({ install: crypto.randomUUID(), version: "9.2.1", events: [{ level: "error", message: msg(i) }] }),
      }), noSalt);
      expect(r.status).toBe(200);
    }
    const rows = await rowsOf("9.2.1");
    expect(rows.length).toBe(2);
    for (const r of rows) { expect(r.message).toContain("http://<host>/*.ts"); expect(r.message).not.toContain("#"); }
    expectClean(rows);
    expect(warn.mock.calls.filter((c) => String(c[0]).includes("LOG_HOST_SALT")).length).toBeLessThanOrEqual(1);
  });
});
