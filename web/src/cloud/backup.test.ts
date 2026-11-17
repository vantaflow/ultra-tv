import { beforeEach, describe, expect, it } from "vitest";
import { BACKUP_HOST, BackupBreaker, backupAccepted, backupEligible, isOutageStatus, toBackupUrl } from "./backup";
import { resetFallbackMemory, withFallback, type HttpResult } from "./client";

const P = "https://tv.weedy.dpdns.org";
const ok: HttpResult = { status: 200, text: "{}", retryAfter: "", etag: "" };
const res = (status: number): HttpResult => ({ ...ok, status });

describe("critères de bascule", () => {
  it("lectures de la liste blanche seulement", () => {
    expect(backupEligible("GET", `${P}/api/config`)).toBe(true);
    expect(backupEligible(undefined, `${P}/api/announcements?audience=tv`)).toBe(true);
    expect(backupEligible("POST", `${P}/api/config`)).toBe(false);
    expect(backupEligible("GET", `${P}/api/pair/start`)).toBe(false);
    expect(backupEligible("GET", `${P}/api/device/trakt/library`)).toBe(false);
    expect(backupEligible("GET", "n'importe quoi")).toBe(false);
  });
  it("pannes : 500, 502, 503, 504, 52x ; jamais 4xx, 304, 429", () => {
    for (const s of [500, 502, 503, 504, 520, 522, 526]) expect(isOutageStatus(s)).toBe(true);
    for (const s of [200, 304, 401, 404, 410, 429, 501, 505, 527]) expect(isOutageStatus(s)).toBe(false);
  });
  it("une réponse du secours n'est retenue que si 200 ou 304", () => {
    expect(backupAccepted(200)).toBe(true);
    expect(backupAccepted(304)).toBe(true);
    for (const s of [401, 404, 429, 500, 503]) expect(backupAccepted(s)).toBe(false);
  });
  it("l'adresse de secours garde le chemin et la requête, en https", () => {
    expect(toBackupUrl(`${P}/api/config?a=1`)).toBe(`https://${BACKUP_HOST}/api/config?a=1`);
  });
});

describe("disjoncteur", () => {
  it("s'ouvre après 2 échecs, double la pause à chaque essai raté, se referme au premier succès", () => {
    let t = 0;
    const b = new BackupBreaker(() => t);
    b.failure();
    expect(b.preferBackup()).toBe(false);
    b.failure();
    expect(b.preferBackup()).toBe(true);
    t = 59_999; expect(b.preferBackup()).toBe(true);
    t = 60_000; expect(b.preferBackup()).toBe(false); // essai du principal
    b.failure();
    t = 60_000 + 119_999; expect(b.preferBackup()).toBe(true);
    t = 60_000 + 120_000; expect(b.preferBackup()).toBe(false);
    for (let i = 0; i < 5; i++) { b.failure(); t += 400_000; }
    b.failure(); expect(b.preferBackup()).toBe(true);
    t += 300_000; expect(b.preferBackup()).toBe(false); // plafond 5 min
    b.success();
    b.failure();
    expect(b.preferBackup()).toBe(false);
  });
});

describe("withFallback + secours", () => {
  beforeEach(() => resetFallbackMemory());
  const hosts = (seen: string[]) => (r: { url: string }) => { seen.push(new URL(r.url).host); };

  it("principal sain : le secours n'est jamais appelé", async () => {
    const seen: string[] = [];
    const h = withFallback(async (r) => { hosts(seen)(r); return ok; });
    await h({ url: `${P}/api/config` });
    expect(seen).toEqual(["tv.weedy.dpdns.org"]);
  });

  it("principal et workers.dev en panne réseau : le secours répond", async () => {
    const seen: string[] = [];
    const h = withFallback(async (r) => { hosts(seen)(r); if (!r.url.includes(BACKUP_HOST)) throw new TypeError("fetch failed"); return ok; });
    expect((await h({ url: `${P}/api/config`, headers: { authorization: "Bearer utv_x" } })).status).toBe(200);
    expect(seen).toEqual(["tv.weedy.dpdns.org", "ultratv-config.khalilbenaz.workers.dev", BACKUP_HOST]);
  });

  it("5xx du principal : le secours répond ; un 4xx du principal est rendu tel quel", async () => {
    const h = withFallback(async (r) => (r.url.includes(BACKUP_HOST) ? res(200) : res(503)));
    expect((await h({ url: `${P}/api/announcements` })).status).toBe(200);
    resetFallbackMemory();
    const seen: string[] = [];
    const h2 = withFallback(async (r) => { hosts(seen)(r); return res(401); });
    expect((await h2({ url: `${P}/api/config` })).status).toBe(401);
    expect(seen).toEqual(["tv.weedy.dpdns.org"]);
  });

  it("le secours ne délie jamais : son 401 ou son 503 laisse la réponse du principal", async () => {
    const h = withFallback(async (r) => (r.url.includes(BACKUP_HOST) ? res(401) : res(502)));
    expect((await h({ url: `${P}/api/config` })).status).toBe(502);
    resetFallbackMemory();
    const h2 = withFallback(async (r) => { if (r.url.includes(BACKUP_HOST)) return res(503); throw new Error("down"); });
    await expect(h2({ url: `${P}/api/config` })).rejects.toThrow("down");
  });

  it("jamais d'écriture ni d'autre route au secours, jamais pour un Worker auto-hébergé", async () => {
    const seen: string[] = [];
    const h = withFallback(async (r) => { hosts(seen)(r); return res(503); });
    await h({ url: `${P}/api/config`, method: "POST", body: "{}" });
    await h({ url: `${P}/api/device/providers`, method: "POST", body: "{}" });
    await h({ url: "https://mon-worker.example/api/config" });
    expect(seen.some((x) => x === BACKUP_HOST)).toBe(false);
  });

  it("après 2 pannes, les lectures vont d'emblée au secours ; au retour du principal, on le reprend", async () => {
    let primaryUp = false;
    const seen: string[] = [];
    const h = withFallback(async (r) => {
      hosts(seen)(r);
      if (r.url.includes(BACKUP_HOST)) return ok;
      if (!primaryUp) throw new TypeError("fetch failed");
      return ok;
    });
    await h({ url: `${P}/api/config` });
    await h({ url: `${P}/api/config` });
    seen.length = 0;
    await h({ url: `${P}/api/config` });
    expect(seen).toEqual([BACKUP_HOST]); // principal tenu pour en panne : pas d'attente
  });
});
