// Télémétrie par lots : POST /api/events (50 entrées, 32 Ko), même nettoyage / tri / authentification que /api/event.
import { env } from "cloudflare:workers";
import { describe, it, expect } from "vitest";
import worker from "../src/index.js";
import { call, freshIp, ORIGIN, newAccount, pairDevice, d1Rows, bearer } from "./helpers.js";

const lot = (dev, events, extra = {}) => call("/api/events", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { version: "1.3.40", events, ...extra } });

/** Envoie un lot avec un environnement modifié (base instrumentée, filtre activé…). */
const lotAvec = (e, dev, events) => worker.fetch(new Request(ORIGIN + "/api/events", {
  method: "POST", headers: { "cf-connecting-ip": dev.ip, origin: ORIGIN, "content-type": "application/json", ...bearer(dev.token) }, body: JSON.stringify({ events }),
}), e);

describe("POST /api/events", () => {
  it("exige un jeton d'appareil", async () => {
    expect((await call("/api/events", { method: "POST", ip: freshIp(), json: { events: [{ message: "x" }] } })).status).toBe(401);
  });

  it("écrit les entrées nettoyées en D1 et laisse /api/event en place", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const r = await lot(dev, [
      { level: "warn", tag: "player", message: "lot-un" },
      { level: "bidon", tag: "", message: "lot-deux <b>" },
      "pas-un-objet", null,
    ]);
    expect(r.status).toBe(200);
    expect((await r.json()).accepted).toBe(2);
    const rows = (await d1Rows("events")).filter((x) => x.device_id === dev.deviceId);
    expect(rows.find((x) => x.message === "lot-un")).toMatchObject({ kind: "event", level: "warn", tag: "player", version: "1.3.40" });
    expect(rows.find((x) => x.message.startsWith("lot-deux"))).toMatchObject({ level: "info", tag: "app" });
    expect((await call("/api/event", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "ancienne-route" } })).status).toBe(200);
  });

  it("garde l'horodatage plausible de l'appareil, ignore un horodatage futur ou trop ancien", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const now = Date.now();
    await lot(dev, [
      { level: "error", message: "ts-ok", ts: now - 120_000 },
      { level: "error", message: "ts-futur", ts: now + 3600_000 },
      { level: "error", message: "ts-vieux", ts: now - 5 * 3600_000 },
    ]);
    const rows = await d1Rows("events");
    expect(rows.find((x) => x.message === "ts-ok").ts).toBe(now - 120_000);
    expect(rows.find((x) => x.message === "ts-futur").ts).toBeGreaterThanOrEqual(now);
    expect(rows.find((x) => x.message === "ts-vieux").ts).toBeGreaterThanOrEqual(now);
  });

  it("refuse plus de 50 entrées, un corps de plus de 32 Ko et un corps sans liste", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    expect((await lot(dev, Array.from({ length: 51 }, (_, i) => ({ message: "m" + i })))).status).toBe(413);
    expect((await lot(dev, [{ message: "x".repeat(40 * 1024) }])).status).toBe(413);
    expect((await call("/api/events", { method: "POST", ip: dev.ip, headers: bearer(dev.token), json: { message: "x" } })).status).toBe(400);
    expect((await lot(dev, Array.from({ length: 50 }, (_, i) => ({ level: "error", message: "pile50-" + i })))).status).toBe(200);
  });

  it("un lot de 50 entrées s'écrit en UN seul batch de stockage", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const sizes = [];
    const db = new Proxy(env.DB, {
      get(t, k) {
        if (k === "batch") return (b) => { sizes.push(b.length); return t.batch(b); };
        const v = t[k]; return typeof v === "function" ? v.bind(t) : v;
      },
    });
    const events = Array.from({ length: 50 }, (_, i) => ({ level: "error", tag: "t", message: "un-batch-" + i }));
    const res = await lotAvec({ ...env, DB: db }, dev, events);
    expect(res.status).toBe(200);
    // En simulation Turso la base est remplacée par l'adaptateur (batch = une seule requête HTTP par construction).
    if (env.STORAGE_MODE !== "turso") expect(sizes.filter((n) => n === 50).length).toBe(1);
    expect((await d1Rows("events")).filter((x) => x.message.startsWith("un-batch-")).length).toBe(50);
  });

  it("applique le tri : les lignes d'info sont échantillonnées, les erreurs gardées", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const events = [
      ...Array.from({ length: 40 }, (_, i) => ({ level: "info", tag: "perf", message: "tri-info-" + i })),
      { level: "error", tag: "play", message: "tri-erreur" },
    ];
    const res = await lotAvec({ ...env, TELEMETRY_FILTER: "on" }, dev, events);
    expect(res.status).toBe(200);
    const rows = await d1Rows("events");
    expect(rows.some((x) => x.message === "tri-erreur")).toBe(true);
    expect(rows.filter((x) => x.message.startsWith("tri-info-")).length).toBeLessThan(40);
  });

  it("une ligne d'info déjà échantillonnée par le client (sampled) n'est pas tirée une seconde fois", async () => {
    const acct = await newAccount(); const dev = await pairDevice(acct);
    const events = Array.from({ length: 20 }, (_, i) => ({ level: "info", tag: "nav", message: "deja-echantillonne-" + i, sampled: true }));
    expect((await lotAvec({ ...env, TELEMETRY_FILTER: "on" }, dev, events)).status).toBe(200);
    expect((await d1Rows("events")).filter((x) => x.message.startsWith("deja-echantillonne-")).length).toBe(20);
  });
});
