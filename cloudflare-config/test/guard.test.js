import { env } from "cloudflare:workers";
import { runDurableObjectAlarm } from "cloudflare:test";
import { describe, it, expect } from "vitest";

const stub = (name) => env.GUARD.get(env.GUARD.idFromName(name));
const uniq = () => crypto.randomUUID();

describe("limite de débit (fenêtre fixe)", () => {
  it("hit_sousLaLimite_autorise", async () => {
    const g = stub(uniq());
    for (let i = 0; i < 3; i++) expect((await g.hit(3, 60)).ok).toBe(true);
  });
  it("hit_auDelaDeLaLimite_refuseAvecRetryAfter", async () => {
    const g = stub(uniq());
    for (let i = 0; i < 3; i++) await g.hit(3, 60);
    const r = await g.hit(3, 60);
    expect(r.ok).toBe(false);
    expect(r.retryAfter).toBeGreaterThan(0);
    expect(r.retryAfter).toBeLessThanOrEqual(60);
  });
  it("hit_parallele_neDepassePasLaLimite", async () => {
    const g = stub(uniq());
    const results = await Promise.all(Array.from({ length: 20 }, () => g.hit(5, 60)));
    expect(results.filter((r) => r.ok)).toHaveLength(5);
  });
  it("hit_cleDifferente_compteurIndependant", async () => {
    const a = stub(uniq()); const b = stub(uniq());
    for (let i = 0; i < 3; i++) await a.hit(3, 60);
    expect((await a.hit(3, 60)).ok).toBe(false);
    expect((await b.hit(3, 60)).ok).toBe(true);
  });
});

describe("verrouillage progressif", () => {
  const cfg = { threshold: 3, baseSec: 60, capSec: 900 };
  it("lockFail_sousLeSeuil_pasDeVerrou", async () => {
    const g = stub(uniq());
    expect((await g.lockFail(cfg)).locked).toBe(false);
    expect((await g.lockFail(cfg)).locked).toBe(false);
    expect((await g.lockState()).locked).toBe(false);
  });
  it("lockFail_auSeuil_verrouille60s", async () => {
    const g = stub(uniq());
    for (let i = 0; i < 2; i++) await g.lockFail(cfg);
    const r = await g.lockFail(cfg);
    expect(r.locked).toBe(true);
    expect(r.retryAfter).toBeGreaterThan(55);
    expect((await g.lockState()).locked).toBe(true);
  });
  it("lockFail_echecsSuivants_delaiDouble_plafonne", async () => {
    const g = stub(uniq());
    const waits = [];
    for (let i = 0; i < 9; i++) waits.push((await g.lockFail(cfg)).retryAfter);
    expect(waits[2]).toBeGreaterThan(55); expect(waits[2]).toBeLessThanOrEqual(60);
    expect(waits[3]).toBeGreaterThan(115); expect(waits[3]).toBeLessThanOrEqual(120);
    expect(waits[8]).toBeLessThanOrEqual(900);
    expect(waits[8]).toBeGreaterThan(890);
  });
  it("lockClear_remetAZero", async () => {
    const g = stub(uniq());
    for (let i = 0; i < 3; i++) await g.lockFail(cfg);
    await g.lockClear();
    expect((await g.lockState()).locked).toBe(false);
  });
  it("alarm_nettoieLetat", async () => {
    const g = stub(uniq());
    await g.hit(1, 60);
    await runDurableObjectAlarm(g);
    expect((await g.hit(1, 60)).ok).toBe(true);
  });
});

describe("appairage", () => {
  const secretHash = "h".repeat(64);
  it("pairInit_nouveauCode_ok_secondAppelRefuse", async () => {
    const g = stub("pair:" + uniq());
    expect((await g.pairInit({ secretHash, label: "aa:bb", ttlSec: 600 })).ok).toBe(true);
    expect((await g.pairInit({ secretHash, label: "aa:bb", ttlSec: 600 })).ok).toBe(false);
  });
  it("pairPoll_avantConfirmation_pending", async () => {
    const g = stub("pair:" + uniq());
    await g.pairInit({ secretHash, label: "tv", ttlSec: 600 });
    expect((await g.pairPoll(secretHash)).status).toBe("pending");
  });
  it("pairPoll_mauvaisSecret_inconnu", async () => {
    const g = stub("pair:" + uniq());
    await g.pairInit({ secretHash, label: "tv", ttlSec: 600 });
    expect((await g.pairPoll("x".repeat(64))).status).toBe("unknown");
  });
  it("pairConfirm_puisPoll_livreLeJetonUneSeuleFois", async () => {
    const g = stub("pair:" + uniq());
    await g.pairInit({ secretHash, label: "tv", ttlSec: 600 });
    const c = await g.pairConfirm({ login: "alice", deviceId: "d1", token: "utv_tok" });
    expect(c).toEqual({ ok: true, label: "tv" });
    const p1 = await g.pairPoll(secretHash);
    expect(p1).toMatchObject({ status: "ready", token: "utv_tok", deviceId: "d1" });
    expect((await g.pairPoll(secretHash)).status).toBe("unknown");
  });
  it("pairConfirm_deuxFois_secondRefuse", async () => {
    const g = stub("pair:" + uniq());
    await g.pairInit({ secretHash, label: "tv", ttlSec: 600 });
    await g.pairConfirm({ login: "alice", deviceId: "d1", token: "t1" });
    expect((await g.pairConfirm({ login: "mallory", deviceId: "d2", token: "t2" })).ok).toBe(false);
  });
  it("pairConfirm_codeInexistant_refuse", async () => {
    const g = stub("pair:" + uniq());
    expect((await g.pairConfirm({ login: "alice", deviceId: "d", token: "t" })).ok).toBe(false);
  });
  it("pairConfirm_codeExpire_refuse", async () => {
    const g = stub("pair:" + uniq());
    await g.pairInit({ secretHash, label: "tv", ttlSec: 0 });
    await new Promise((r) => setTimeout(r, 5));
    expect((await g.pairConfirm({ login: "alice", deviceId: "d", token: "t" })).ok).toBe(false);
    expect((await g.pairPoll(secretHash)).status).toBe("unknown");
  });
});

describe("unicité", () => {
  it("claim_premierAppelSeul_puisRelease", async () => {
    const g = stub("acct:" + uniq());
    expect(await g.claim()).toBe(true);
    expect(await g.claim()).toBe(false);
    await g.release();
    expect(await g.claim()).toBe(true);
  });
  it("claim_parallele_unSeulGagnant", async () => {
    const g = stub("acct:" + uniq());
    const r = await Promise.all(Array.from({ length: 10 }, () => g.claim()));
    expect(r.filter(Boolean)).toHaveLength(1);
  });
});
