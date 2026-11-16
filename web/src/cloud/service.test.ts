import "fake-indexeddb/auto";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";

vi.mock("@/sync/client", () => ({
  detectSourceLanguages: vi.fn(async () => ({ languages: [{ code: "FR", categories: 5 }, { code: "AR", categories: 3 }], counts: { live: 1, movie: 1, series: 1 } })),
  syncSource: vi.fn(async () => ({ live: 0, movie: 0, series: 0 })),
  cancelWork: vi.fn(),
  syncSourceEpg: vi.fn(),
  testSource: vi.fn(),
}));
vi.mock("@/net/secrets", () => ({
  encryptSecret: async (s: string) => (s ? `enc:v1:${s}` : ""),
  decryptSecret: async (s: string) => (s.startsWith("enc:v1:") ? s.slice(7) : s),
}));

import { db } from "@/db/db";
import { usePrefs } from "@/state/prefs";
import { emptySource, listSources, saveSource } from "@/db/sources";
import { setCloudHttp } from "./client";
import { startFakeWorker, type FakeWorker } from "./fakeWorker";
import { applyProviders, loadCloud, removeSource, saveDeviceName, saveWorker, shareSource, storeToken, syncCloud, unpair, useCloud } from "./service";

let w: FakeWorker;
beforeAll(async () => {
  w = await startFakeWorker();
  setCloudHttp(async (r) => {
    const res = await fetch(r.url, { method: r.method ?? "GET", headers: r.headers, body: r.body || undefined, redirect: "manual" });
    return { status: res.status, text: await res.text(), retryAfter: res.headers.get("retry-after") ?? "", etag: res.headers.get("etag") ?? "" };
  });
  usePrefs.getState().set({ lang: "fr" });
  await loadCloud();
  expect(await saveWorker(w.base, true)).toBe(true);
  await storeToken(w.token, "dev-1");
});
afterAll(() => { w.close(); setCloudHttp(null); });

const A = { id: "a0000001", kind: "XTREAM", name: "Abo A", url: "http://hote-a:80", username: "ua", password: "pa" };
const B = { id: "a0000002", kind: "M3U", name: "Liste B", url: "http://hote-b/liste.m3u" };

describe("synchronisation du compte", () => {
  it("ajoute les fournisseurs du compte, le jeton est chiffré au repos", async () => {
    w.providers = [A, B, { id: "a0000003", kind: "STALKER", name: "Portail", url: "http://p" }];
    const r = await syncCloud({ force: true, awaitSync: true });
    expect(r).toMatchObject({ added: 2, skipped: 1, removed: 0 });
    const rows = await db.sources.toArray();
    expect(rows.map((s) => s.cloudId).sort()).toEqual(["a0000001", "a0000002"]);
    expect(rows.find((s) => s.cloudId === "a0000001")).toMatchObject({ cloudOrigin: "cloud", langs: ["FR"] });
    expect((await db.settings.get("cloud.token"))!.value).toBe("enc:v1:tok-1");
    expect(useCloud.getState().devices).toHaveLength(2);
  });

  it("ne refait rien si rien n'a changé (ETag)", async () => {
    expect(await syncCloud({ awaitSync: true })).toMatchObject({ unchanged: true });
  });

  it("modification et retrait", async () => {
    w.providers = [{ ...A, password: "nouveau", name: "Abo A (renommé)" }];
    w.version++;
    const r = await syncCloud({ awaitSync: true });
    expect(r).toMatchObject({ added: 0, updated: 1, removed: 1 });
    const rows = await listSources();
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ name: "Abo A (renommé)", password: "nouveau" });
  });

  it("un jeton révoqué dissocie l'appareil", async () => {
    w.state.rejectToken = true;
    await expect(syncCloud({ force: true })).rejects.toThrow();
    expect(useCloud.getState().paired).toBe(false);
    w.state.rejectToken = false;
    await storeToken(w.token, "dev-1");
  });
});

describe("partage d'une source locale", () => {
  const local = async () => {
    const id = await saveSource({ ...emptySource(), name: "Locale", type: "xtream", server: "http://hote-l:80", username: "ul", password: "pl" });
    return (await listSources()).find((s) => s.id === id)!;
  };

  it("envoie la source avec les appareils choisis et garde le lien", async () => {
    const s = await local();
    const r = await shareSource(s, ["d1"]);
    expect(r).toEqual({ ok: true });
    expect(w.state.lastPut).toMatchObject({ kind: "XTREAM", url: "http://hote-l:80", username: "ul", shareWith: ["d1"] });
    const after = (await listSources()).find((x) => x.id === s.id)!;
    expect(after).toMatchObject({ cloudOrigin: "local", cloudShared: 1 });
    expect(after.cloudId).toBeTruthy();
    expect(useCloud.getState().pushAvailable).toBe(true);
  });

  it("Worker sans shareWith : repli sur « tous les appareils »", async () => {
    w.state.shareWithSupported = false;
    const s = await saveSource({ ...emptySource(), name: "L2", type: "m3u", m3uUrl: "http://x/l2.m3u" });
    const src = (await listSources()).find((x) => x.id === s)!;
    expect(await shareSource(src, "all")).toEqual({ ok: true });
    w.state.shareWithSupported = true;
  });

  it("endpoint absent (404) : l'option est masquée", async () => {
    w.state.hasPushEndpoint = false;
    const s = await saveSource({ ...emptySource(), name: "L3", type: "m3u", m3uUrl: "http://x/l3.m3u" });
    const src = (await listSources()).find((x) => x.id === s)!;
    expect(await shareSource(src, "all")).toEqual({ ok: false, reason: "unavailable" });
    expect(useCloud.getState().pushAvailable).toBe(false);
  });

  it("un fichier M3U local ne se partage pas", async () => {
    const s = await saveSource({ ...emptySource(), name: "F", type: "m3u", m3uUrl: "file:liste.m3u" });
    const src = (await listSources()).find((x) => x.id === s)!;
    expect(await shareSource(src, "all")).toEqual({ ok: false, reason: "unsupported" });
  });
});

describe("suppression propagée et affectation (#135, #136)", () => {
  const C = { id: "b0000001", kind: "XTREAM", name: "Abo C", url: "http://hote-c:80", username: "uc", password: "pc" };
  const reset = async () => {
    await db.sources.clear();
    await db.settings.delete("cloud.pendingDel");
    w.providers = []; w.removed = []; w.state.deletes = []; w.version++;
  };

  it("supprimer une source du compte appelle le Worker (all=1), elle ne revient pas à la synchro", async () => {
    await reset();
    w.providers = [C];
    await syncCloud({ force: true, awaitSync: true });
    const s = (await listSources())[0]!;
    await removeSource(s);
    expect(w.state.deletes).toEqual(["b0000001?all=1"]);
    expect(w.providers).toEqual([]);
    await syncCloud({ force: true, awaitSync: true });
    expect(await listSources()).toEqual([]);
  });

  it("suppression hors ligne : mémorisée, la synchro ne recrée pas la source puis la transmet", async () => {
    await reset();
    w.providers = [C];
    await syncCloud({ force: true, awaitSync: true });
    const s = (await listSources())[0]!;
    const realFetch = globalThis.fetch;
    globalThis.fetch = (async () => { throw new Error("hors ligne"); }) as typeof fetch;
    try { await removeSource(s); } finally { globalThis.fetch = realFetch; }
    expect(w.providers).toHaveLength(1);
    expect(await listSources()).toEqual([]);
    await applyProviders(w.providers, true);
    expect(await listSources()).toEqual([]);
    await syncCloud({ force: true, awaitSync: true });
    expect(w.state.deletes).toEqual(["b0000001?all=1"]);
    expect(w.providers).toEqual([]);
    expect(await listSources()).toEqual([]);
    expect(await db.settings.get("cloud.pendingDel")).toMatchObject({ value: [] });
  });

  it("supprimée depuis le tableau de bord : la source du compte disparaît ici ; une locale jamais synchronisée reste", async () => {
    await reset();
    w.providers = [C];
    await syncCloud({ force: true, awaitSync: true });
    const loc = await saveSource({ ...emptySource(), name: "Purement locale", type: "m3u", m3uUrl: "http://x/loc.m3u" });
    w.providers = []; w.removed = ["b0000001"]; w.version++;
    const r = await syncCloud({ awaitSync: true });
    expect(r.removed).toBe(1);
    expect((await listSources()).map((x) => x.id)).toEqual([loc]);
  });

  it("source locale partagée puis supprimée du compte : supprimée ici ; simple désaffectation : conservée", async () => {
    await reset();
    const id = await saveSource({ ...emptySource(), name: "Partagée", type: "xtream", server: "http://hote-p:80", username: "up", password: "pp", cloudId: "b0000002", cloudOrigin: "local", cloudShared: "all" });
    const id2 = await saveSource({ ...emptySource(), name: "Désaffectée", type: "xtream", server: "http://hote-q:80", username: "uq", password: "pq", cloudId: "b0000003", cloudOrigin: "local", cloudShared: "all" });
    w.removed = ["b0000002"]; w.version++;
    await syncCloud({ force: true, awaitSync: true });
    const rows = await listSources();
    expect(rows.find((x) => x.id === id)).toBeUndefined();
    expect(rows.find((x) => x.id === id2)).toMatchObject({ cloudId: undefined });
  });

  it("affectation : route dédiée pour une source liée, la liste revient à la synchro", async () => {
    await reset();
    w.providers = [{ ...C, sharedWith: "all" }];
    await syncCloud({ force: true, awaitSync: true });
    let s = (await listSources())[0]!;
    expect(await shareSource(s, ["dev-1", "d2"])).toEqual({ ok: true });
    expect(w.state.lastAssign).toEqual({ shareWith: ["dev-1", "d2"] });
    s = (await listSources())[0]!;
    expect(s).toMatchObject({ cloudShared: 2, cloudSharedIds: ["dev-1", "d2"] });
    await syncCloud({ force: true, awaitSync: true });
    expect((await listSources())[0]).toMatchObject({ cloudSharedIds: ["dev-1", "d2"] });
    // Une autre liste de même taille est détectée (avant : seul le nombre était comparé).
    w.providers[0]!.sharedWith = ["dev-1", "d3"]; w.version++;
    await syncCloud({ awaitSync: true });
    expect((await listSources())[0]).toMatchObject({ cloudSharedIds: ["dev-1", "d3"] });
    expect(await shareSource((await listSources())[0]!, "all")).toEqual({ ok: true });
    expect((await listSources())[0]).toMatchObject({ cloudShared: "all", cloudSharedIds: undefined });
  });
});

describe("nom de l'appareil", () => {
  it("le renommage est envoyé au compte, et le nom du compte revient à la synchro", async () => {
    await saveDeviceName("Mac du bureau");
    expect(w.state.lastRename).toBe("Mac du bureau");
    expect(useCloud.getState().deviceName).toBe("Mac du bureau");
    w.state.devices[0]!.name = "Renommé depuis le tableau de bord";
    w.version++;
    await syncCloud({ force: true, awaitSync: true });
    expect(useCloud.getState().deviceName).toBe("Renommé depuis le tableau de bord");
    expect(useCloud.getState().deviceId).toBe("dev-1");
  });
});

describe("dissociation", () => {
  it("efface le jeton et les liens, garde les sources", async () => {
    await unpair();
    expect(useCloud.getState().paired).toBe(false);
    expect(await db.settings.get("cloud.token")).toBeUndefined();
    expect((await listSources()).every((s) => !s.cloudId)).toBe(true);
  });
  it("applyProviders est utilisable seul", async () => {
    const r = await applyProviders([{ id: "a0000009", kind: "M3U", name: "Z", url: "http://z/z.m3u" }], true);
    expect(r.added).toBe(1);
  });
});
