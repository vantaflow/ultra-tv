import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  InvalidFieldError, NotFoundError, TokenRejectedError, fetchConfig, groupPairingCode, normalizeWorkerUrl,
  pollPairing, putProvider, rotateToken, runPairing, setCloudHttp, startPairing, type PairingEvent,
} from "./client";
import { startFakeWorker, type FakeWorker } from "./fakeWorker";

let w: FakeWorker;
beforeAll(async () => {
  w = await startFakeWorker();
  setCloudHttp(async (r) => {
    const res = await fetch(r.url, { method: r.method ?? "GET", headers: r.headers, body: r.body || undefined, redirect: "manual" });
    return { status: res.status, text: await res.text(), retryAfter: res.headers.get("retry-after") ?? "", etag: res.headers.get("etag") ?? "" };
  });
});
afterAll(() => { w.close(); setCloudHttp(null); });
beforeEach(() => { w.state.rejectToken = false; w.state.hasPushEndpoint = true; w.state.shareWithSupported = true; });

describe("normalizeWorkerUrl", () => {
  it("exige HTTPS (HTTP seulement en boucle locale si autorisé)", () => {
    expect(normalizeWorkerUrl("https://ultratv-config.khalilbenaz.workers.dev/")).toBe("https://ultratv-config.khalilbenaz.workers.dev");
    expect(normalizeWorkerUrl("http://example.com")).toBeNull();
    expect(normalizeWorkerUrl("http://127.0.0.1:8787")).toBeNull();
    expect(normalizeWorkerUrl("http://127.0.0.1:8787", true)).toBe("http://127.0.0.1:8787");
    expect(normalizeWorkerUrl("https://u:p@example.com")).toBeNull();
    expect(normalizeWorkerUrl("https://example.com/?x=1")).toBeNull();
    expect(normalizeWorkerUrl("n'importe quoi")).toBeNull();
  });
  it("groupe le code d'appairage", () => {
    expect(groupPairingCode("K7Q2M9XF")).toBe("K7Q2-M9XF");
    expect(groupPairingCode("K7Q2-M9XF")).toBe("K7Q2-M9XF");
  });
});

describe("appairage", () => {
  it("demande un code puis attend la saisie sur le tableau de bord", async () => {
    const events: PairingEvent[] = [];
    const ctrl = new AbortController();
    w.state.pollCount = 0;
    const done = runPairing(w.base, "Mon Mac", (e) => events.push(e), ctrl.signal, async () => { if (w.state.pollCount === 2) w.confirm(); });
    await done;
    expect(events[0]).toEqual({ type: "code", code: "K7Q2M9XF", expiresInSec: 600 });
    expect(events.at(-1)).toEqual({ type: "paired", token: "tok-1", deviceId: "dev-1" });
    expect(w.state.pollCount).toBeGreaterThanOrEqual(3);
  });
  it("ralentit sur 429 au lieu d'insister", async () => {
    w.state.rateLimitNextPoll = true;
    const sleeps: number[] = [];
    const events: PairingEvent[] = [];
    await runPairing(w.base, "x", (e) => events.push(e), new AbortController().signal, async (ms) => { sleeps.push(ms); });
    expect(sleeps[0]).toBe(1000);
    expect(sleeps[1]).toBe(2000);
    expect(events.at(-1)?.type).toBe("paired");
  });
  it("s'arrête quand on annule", async () => {
    const ctrl = new AbortController();
    const events: PairingEvent[] = [];
    await runPairing(w.base, "x", (e) => events.push(e), ctrl.signal, async () => { ctrl.abort(); });
    expect(events.map((e) => e.type)).toEqual(["code"]);
  });
  it("code inconnu -> expiré", async () => {
    const r = await pollPairing(w.base, { code: "X", pollSecret: "mauvais", expiresInSec: 1, intervalSec: 1 });
    expect(r.state).toBe("gone");
    expect((await startPairing(w.base, "x")).code).toBe("K7Q2M9XF");
  });
  it("serveur injoignable -> échec", async () => {
    const events: PairingEvent[] = [];
    await runPairing("http://127.0.0.1:1", "x", (e) => events.push(e), new AbortController().signal, async () => undefined);
    expect(events[0]!.type).toBe("failed");
  });
});

describe("configuration", () => {
  it("lit les fournisseurs, utilise l'ETag (304) et signale un jeton révoqué", async () => {
    w.providers = [{ id: "a0000001", kind: "XTREAM", name: "Abo", url: "http://h:80", username: "u", password: "p" }];
    const r1 = await fetchConfig(w.base, w.token);
    expect(r1.unchanged).toBe(false);
    if (r1.unchanged) return;
    expect(r1.config.providers).toHaveLength(1);
    expect(await fetchConfig(w.base, w.token, r1.etag)).toEqual({ unchanged: true });
    w.state.rejectToken = true;
    await expect(fetchConfig(w.base, w.token)).rejects.toBeInstanceOf(TokenRejectedError);
  });
  it("rotation du jeton", async () => {
    expect(await rotateToken(w.base, w.token)).toEqual({ token: "tok-2", deviceId: "dev-1" });
  });
});

describe("envoi d'un fournisseur", () => {
  it("crée puis met à jour", async () => {
    const p = await putProvider(w.base, w.token, { kind: "XTREAM", name: "N", url: "http://h", username: "u", password: "p", shareWith: "all" });
    expect(p.id).toMatch(/^a/);
    const p2 = await putProvider(w.base, w.token, { id: p.id, kind: "XTREAM", name: "N2", url: "http://h", username: "u", password: "p", shareWith: ["d1"] });
    expect(p2.name).toBe("N2");
  });
  it("404 = endpoint absent ; 400 = champ refusé", async () => {
    w.state.hasPushEndpoint = false;
    await expect(putProvider(w.base, w.token, { kind: "M3U", name: "x", url: "http://a" })).rejects.toBeInstanceOf(NotFoundError);
    w.state.hasPushEndpoint = true;
    w.state.shareWithSupported = false;
    await expect(putProvider(w.base, w.token, { kind: "M3U", name: "x", url: "http://a", shareWith: "all" })).rejects.toBeInstanceOf(InvalidFieldError);
  });
});
