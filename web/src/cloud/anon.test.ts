import "fake-indexeddb/auto";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/net/secrets", () => ({
  encryptSecret: async (s: string) => s,
  decryptSecret: async (s: string) => s,
}));

import { db, getSetting } from "@/db/db";
import { setCloudHttp, sendAnonPing, type AnonPingBody } from "./client";
import { ANON_MIN_INTERVAL_MS, anonDue, anonPlatform, anonVersion, readAnonTelemetry, reportAnonPing, writeAnonTelemetry, type AnonDeps } from "./anon";

const H = 3600_000;

function deps(over: Partial<AnonDeps> = {}) {
  const sent: { base: string; body: AnonPingBody }[] = [];
  let last = 0;
  const d: AnonDeps = {
    now: () => 10 * 24 * H,
    enabled: async () => true,
    paired: () => false,
    worker: () => "https://worker.example",
    version: () => "1.2.43",
    platform: () => "win32",
    last: async () => last,
    setLast: async (ms) => { last = ms; },
    install: async () => "3f1c2b4a-0000-4000-8000-123456789abc",
    send: async (base, body) => { sent.push({ base, body }); return true; },
    ...over,
  };
  return { d, sent, getLast: () => last };
}

describe("relevé anonyme du bureau : règles", () => {
  it("dû : jamais envoyé, après 20 h, horloge revenue en arrière ; pas avant 20 h", () => {
    expect(anonDue(0, 1_000_000)).toBe(true);
    expect(anonDue(1_000_000, 1_000_000 + 19 * H)).toBe(false);
    expect(anonDue(1_000_000, 1_000_000 + ANON_MIN_INTERVAL_MS)).toBe(true);
    expect(anonDue(2_000_000, 1_000_000)).toBe(true);
  });
  it("système et version : correspondance stricte", () => {
    expect(anonPlatform("win32")).toBe("windows");
    expect(anonPlatform("darwin")).toBe("macos");
    expect(anonPlatform("linux")).toBe("linux");
    expect(anonPlatform("freebsd")).toBeNull();
    expect(anonPlatform(undefined)).toBeNull();
    expect(anonVersion("1.2.43")).toBe("1.2.43");
    expect(anonVersion("1.2.43-desktop")).toBe("1.2.43-desktop");
    expect(anonVersion("")).toBeNull();
    expect(anonVersion("<x>")).toBeNull();
  });
});

describe("relevé anonyme du bureau : envoi", () => {
  it("envoie identifiant, version et système, rien d'autre ; mémorise l'heure", async () => {
    const { d, sent, getLast } = deps();
    expect(await reportAnonPing(d)).toBe("sent");
    expect(sent).toHaveLength(1);
    expect(sent[0]!.base).toBe("https://worker.example");
    expect(sent[0]!.body).toEqual({ install: "3f1c2b4a-0000-4000-8000-123456789abc", version: "1.2.43", platform: "windows" });
    expect(getLast()).toBe(d.now());
  });
  it("au plus une fois par 20 h", async () => {
    const t = { now: 10 * 24 * H };
    const { d, sent } = deps({ now: () => t.now });
    expect(await reportAnonPing(d)).toBe("sent");
    t.now += 19 * H;
    expect(await reportAnonPing(d)).toBe("skipped");
    t.now += 1 * H;
    expect(await reportAnonPing(d)).toBe("sent");
    expect(sent).toHaveLength(2);
  });
  it("réglage désactivé : rien ne part", async () => {
    const { d, sent } = deps({ enabled: async () => false });
    expect(await reportAnonPing(d)).toBe("off");
    expect(sent).toHaveLength(0);
  });
  it("appareil appairé : rien ne part (déjà compté avec son compte)", async () => {
    const { d, sent } = deps({ paired: () => true });
    expect(await reportAnonPing(d)).toBe("paired");
    expect(sent).toHaveLength(0);
  });
  it("système inconnu, version absente ou adresse du Worker invalide : rien ne part", async () => {
    for (const over of [{ platform: () => "sunos" }, { version: () => undefined }, { worker: () => "http://pas-https.example" }]) {
      const { d, sent } = deps(over);
      expect(await reportAnonPing(d)).toBe("skipped");
      expect(sent).toHaveLength(0);
    }
  });
  it("échec réseau : l'heure n'est pas mémorisée (nouvel essai au prochain passage)", async () => {
    const { d, getLast } = deps({ send: async () => false });
    expect(await reportAnonPing(d)).toBe("error");
    expect(getLast()).toBe(0);
  });
});

describe("réglage « Télémétrie anonyme » et transport", () => {
  it("activé par défaut ; désactiver efface l'identifiant et la date du dernier envoi", async () => {
    expect(await readAnonTelemetry()).toBe(true);
    await db.settings.bulkPut([{ key: "anon.install", value: "x".repeat(20) }, { key: "anon.last", value: 123 }]);
    await writeAnonTelemetry(false);
    expect(await readAnonTelemetry()).toBe(false);
    expect(await getSetting("anon.install", "")).toBe("");
    expect(await getSetting("anon.last", 0)).toBe(0);
    await writeAnonTelemetry(true);
    expect(await readAnonTelemetry()).toBe(true);
  });
  it("sendAnonPing : POST JSON sans jeton vers /api/anon/ping ; faux sur erreur ou réseau absent", async () => {
    const calls: { url: string; method?: string; headers?: Record<string, string>; body?: string }[] = [];
    setCloudHttp(async (r) => { calls.push(r); return { status: calls.length === 1 ? 200 : 429, text: "{}", retryAfter: "", etag: "" }; });
    const body: AnonPingBody = { install: "3f1c2b4a-0000-4000-8000-123456789abc", version: "1.2.43", platform: "macos" };
    expect(await sendAnonPing("https://w.example", body)).toBe(true);
    expect(calls[0]!.url).toBe("https://w.example/api/anon/ping");
    expect(calls[0]!.method).toBe("POST");
    expect(Object.keys(calls[0]!.headers ?? {}).map((k) => k.toLowerCase())).not.toContain("authorization");
    expect(JSON.parse(calls[0]!.body!)).toEqual(body);
    expect(await sendAnonPing("https://w.example", body)).toBe(false);
    setCloudHttp(async () => { throw new Error("network"); });
    expect(await sendAnonPing("https://w.example", body)).toBe(false);
    setCloudHttp(null);
  });
});
