import { describe, expect, it } from "vitest";
import { ConnectionSlot, acquire, initialSlot, release } from "./connectionSlot";
import { connCounts, isConnectionLimit, limitCounts } from "./connectionLimit";

describe("machine à états de l'emplacement", () => {
  it("acquire sur emplacement libre : aucune attente, pas d'éviction", () => {
    const r = acquire(initialSlot(), 1000, 0);
    expect(r).toMatchObject({ token: 1, evicted: null, waitMs: 0 });
    expect(r.state.holder).toBe(1);
  });
  it("acquire → release → acquire", () => {
    const a = acquire(initialSlot(), 0, 0);
    const s = release(a.state, a.token, 10);
    expect(s.holder).toBeNull();
    const b = acquire(s, 20, 0);
    expect(b.token).toBe(2);
    expect(b.evicted).toBeNull();
  });
  it("acquire par-dessus un titulaire : il est évincé", () => {
    const a = acquire(initialSlot(), 0, 0);
    const b = acquire(a.state, 5, 0);
    expect(b.evicted).toBe(a.token);
    expect(b.state.holder).toBe(b.token);
  });
  it("délai de grâce : reste à attendre depuis la libération", () => {
    const a = acquire(initialSlot(), 0, 1000);
    const s = release(a.state, a.token, 100);
    expect(acquire(s, 400, 1000).waitMs).toBe(700);
    expect(acquire(s, 1200, 1000).waitMs).toBe(0);
  });
  it("éviction : la grâce complète court depuis l'éviction", () => {
    const a = acquire(initialSlot(), 0, 1000);
    expect(acquire(a.state, 50, 1000).waitMs).toBe(1000);
  });
  it("release d'un jeton périmé ignoré", () => {
    const a = acquire(initialSlot(), 0, 0);
    const b = acquire(a.state, 1, 0);
    expect(release(b.state, a.token, 2)).toBe(b.state);
  });
});

describe("ConnectionSlot", () => {
  const mk = (grace: number) => {
    let t = 0; const evicted: number[] = []; const sleeps: number[] = [];
    const slot = new ConnectionSlot((k) => evicted.push(k), () => t, async (ms) => { sleeps.push(ms); t += ms; }, { graceMs: grace, retryMs: 1500 });
    return { slot, evicted, sleeps };
  };
  it("sans grâce : pas d'attente, éviction signalée", async () => {
    const { slot, evicted, sleeps } = mk(0);
    const a = await slot.acquire();
    await slot.acquire();
    expect(evicted).toEqual([a!.token]);
    expect(sleeps).toEqual([]);
  });
  it("avec grâce : attend après libération", async () => {
    const { slot, sleeps } = mk(800);
    const a = await slot.acquire();
    slot.release(a);
    await slot.acquire();
    expect(sleeps).toEqual([800]);
  });
  it("une ouverture dépassée pendant l'attente est abandonnée", async () => {
    let resolve!: () => void;
    const slot = new ConnectionSlot(() => {}, () => 0, () => new Promise<void>((r) => { resolve = r; }), { graceMs: 500, retryMs: 0 });
    const a = await slot.acquire();
    slot.release(a);
    const p = slot.acquire();
    slot.releaseAll();
    resolve();
    expect(await p).toBeNull();
  });
});

describe("classification de la limite de connexions", () => {
  it.each([403, 429, 458, 509])("statut %i", (status) => expect(isConnectionLimit({ status })).toBe(true));
  it.each([200, 404, 500, 502, 504])("statut %i : pas une limite", (status) => expect(isConnectionLimit({ status })).toBe(false));
  it.each(["Max connections reached", "connection limit exceeded", "Too many connections", "max_connections", "Maximum connection", "simultaneous streams"])("message %s", (message) =>
    expect(isConnectionLimit({ status: 200, message })).toBe(true));
  it("message anodin", () => expect(isConnectionLimit({ status: 404, message: "Not found" })).toBe(false));
  it("sans info", () => expect(isConnectionLimit({})).toBe(false));
});

describe("compteurs de connexions", () => {
  it("chaînes et nombres", () => expect(connCounts({ active_cons: "1", max_connections: "1" })).toEqual({ active: 1, max: 1 }));
  it("absents / null", () => expect(connCounts({ active_cons: null, max_connections: "" })).toEqual({ active: null, max: null }));
  it("limitCounts", () => {
    expect(limitCounts({ active: 2, max: 2 })).toEqual({ a: 2, b: 2 });
    expect(limitCounts({ active: null, max: 3 })).toEqual({ a: 3, b: 3 });
    expect(limitCounts({ active: 1, max: null })).toBeNull();
  });
});
