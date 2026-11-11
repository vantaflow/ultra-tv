// Télémétrie en D1 : journaux, relevés anonymes, « vu » des appareils, purge, dégradation silencieuse.
import { env } from "cloudflare:workers";
import { describe, it, expect } from "vitest";
import worker from "../src/index.js";
import { call, freshIp, ORIGIN, newAccount, pairDevice, allKv, d1Rows, bearer, basic, OPS } from "./helpers.js";
import { purgeTelemetry, recordEvent, EVENT_TTL_S, CRASH_TTL_S, ANON_TTL_S } from "../src/telemetry.js";

const DAY = 86400_000;

describe("journaux et plantages en D1", () => {
  it("un événement et un crash sont écrits en D1, aucune écriture KV, et la page d'admin les affiche", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const kvBefore = Object.keys(await allKv()).length;
    expect((await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { level: "warn", tag: "player", message: "msg-d1-unique" } })).status).toBe(200);
    expect((await call("/api/crash", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { stack: "stack-d1-unique", androidSdk: 34 } })).status).toBe(200);
    const rows = await d1Rows("events");
    const ev = rows.find((r) => r.message === "msg-d1-unique");
    expect(ev).toMatchObject({ kind: "event", level: "warn", tag: "player", device_id: dev.deviceId });
    expect(rows.find((r) => r.stack === "stack-d1-unique")).toMatchObject({ kind: "crash", android_sdk: 34 });
    expect(Object.keys(await allKv()).length).toBe(kvBefore); // aucun `event:` / `crash:` en KV
    const logs = await (await call("/logs", { ip: freshIp(), headers: basic(OPS) })).text();
    expect(logs).toContain("msg-d1-unique");
    const crashes = await (await call("/crashes", { ip: freshIp(), headers: basic(OPS) })).text();
    expect(crashes).toContain("stack-d1-unique");
  });

  it("la purge supprime ce qui a dépassé la conservation (7 j, 30 j, 180 j) et garde le reste", async () => {
    const now = Date.now();
    const mk = (kind, ts, msg) => recordEvent(env, kind, { ts, deviceId: "d", message: msg, stack: msg });
    await mk("event", now - EVENT_TTL_S * 1000 - DAY, "purge-vieux-event");
    await mk("event", now - DAY, "purge-recent-event");
    await mk("crash", now - EVENT_TTL_S * 1000 - DAY, "purge-crash-8j"); // gardé : 30 j pour les crashs
    await mk("crash", now - CRASH_TTL_S * 1000 - DAY, "purge-vieux-crash");
    await env.DB.prepare("INSERT INTO anon_pings (fingerprint, first_seen, last_seen, web) VALUES ('purge-anon-vieux', 1, ?, 0)").bind(now - ANON_TTL_S * 1000 - DAY).run();
    await env.DB.prepare("INSERT INTO anon_pings (fingerprint, first_seen, last_seen, web) VALUES ('purge-anon-frais', 1, ?, 0)").bind(now - DAY).run();
    await purgeTelemetry(env, now);
    const msgs = (await d1Rows("events")).map((r) => r.message);
    expect(msgs).toContain("purge-recent-event");
    expect(msgs).toContain("purge-crash-8j");
    expect(msgs).not.toContain("purge-vieux-event");
    expect(msgs).not.toContain("purge-vieux-crash");
    const fps = (await d1Rows("anon_pings")).map((r) => r.fingerprint);
    expect(fps).toContain("purge-anon-frais");
    expect(fps).not.toContain("purge-anon-vieux");
  });

  it("le handler scheduled purge la base", async () => {
    await recordEvent(env, "event", { ts: Date.now() - 30 * DAY, message: "purge-cron" });
    const tasks = [];
    await worker.scheduled({}, env, { waitUntil: (p) => tasks.push(p) });
    await Promise.all(tasks);
    expect((await d1Rows("events")).some((r) => r.message === "purge-cron")).toBe(false);
  });
});

describe("dégradation silencieuse", () => {
  const post = (e, path, token, ip, json) => worker.fetch(new Request(ORIGIN + path, {
    method: "POST", headers: { "cf-connecting-ip": ip, origin: ORIGIN, "content-type": "application/json", ...bearer(token) }, body: JSON.stringify(json),
  }), e);
  const brokenDb = { prepare() { throw new Error("D1 indisponible"); }, batch() { throw new Error("D1 indisponible"); } };

  for (const [nom, db] of [["sans binding DB", undefined], ["D1 qui lève", brokenDb]]) {
    it(`${nom} : événement, ping anonyme et authentification répondent normalement, sans écrire en KV`, async () => {
      const acct = await newAccount(); const dev = await pairDevice(acct);
      const e = { ...env, DB: db };
      const kvBefore = JSON.stringify(await allKv());
      expect((await post(e, "/api/event", dev.token, dev.ip, { message: "x" })).status).toBe(200);
      expect((await post(e, "/api/crash", dev.token, dev.ip, { stack: "x" })).status).toBe(200);
      const ping = await worker.fetch(new Request(ORIGIN + "/api/anon/ping", {
        method: "POST", headers: { "cf-connecting-ip": freshIp(), origin: ORIGIN, "content-type": "application/json" }, body: JSON.stringify({ install: crypto.randomUUID() }),
      }), e);
      expect(ping.status).toBe(200);
      expect((await worker.fetch(new Request(ORIGIN + "/logs", { headers: { "cf-connecting-ip": freshIp(), ...basic(OPS) } }), e)).status).toBe(200);
      expect(JSON.stringify(await allKv())).toBe(kvBefore);
    });
  }
});

describe("dernière activité des appareils (device_seen)", () => {
  it("l'authentification inscrit `seen` en D1 sans réécrire dev:<hash> en KV", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const devKeys = async () => Object.fromEntries(Object.entries(await allKv()).filter(([k]) => k.startsWith("dev:")));
    const before = await devKeys();
    expect((await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "a" } })).status).toBe(200);
    expect((await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "b" } })).status).toBe(200);
    expect(await devKeys()).toEqual(before);
    const seen = await d1Rows("device_seen");
    expect(seen.length).toBeGreaterThanOrEqual(1);
    const mine = seen.find((r) => before[`dev:${r.hash}`] && JSON.parse(before[`dev:${r.hash}`]).deviceId === dev.deviceId);
    expect(mine.seen).toBeGreaterThan(Date.now() - 60_000);
  });

  it("au plus une écriture par heure : un second appel immédiat ne change pas la valeur", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "a" } });
    const snap = async () => JSON.stringify(await d1Rows("device_seen"));
    const a = await snap();
    await new Promise((r) => setTimeout(r, 15));
    await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "b" } });
    expect(await snap()).toBe(a);
  });
});
