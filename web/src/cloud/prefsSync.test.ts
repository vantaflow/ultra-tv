import "fake-indexeddb/auto";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/sync/client", () => ({
  detectSourceLanguages: vi.fn(async () => ({ languages: [], counts: { live: 0, movie: 0, series: 0 } })),
  syncSource: vi.fn(async () => ({ live: 0, movie: 0, series: 0 })),
  cancelWork: vi.fn(), syncSourceEpg: vi.fn(), testSource: vi.fn(),
}));
vi.mock("@/net/secrets", () => ({
  encryptSecret: async (s: string) => (s ? `enc:v1:${s}` : ""),
  decryptSecret: async (s: string) => (s.startsWith("enc:v1:") ? s.slice(7) : s),
}));

import { db } from "@/db/db";
import { emptySource, listSources, saveSource } from "@/db/sources";
import { syncSource } from "@/sync/client";
import { setCloudHttp } from "./client";
import { startFakeWorker, type FakeWorker } from "./fakeWorker";
import { applyRemotePrefs, getPrefsAt, loadCloud, notePrefsChanged, publishPrefs, saveWorker, setPrefsSync, storeToken, useCloud } from "./service";

let w: FakeWorker;
let sid = 0;
const ID = "a0000001";
const enabledOf = async () => Object.fromEntries((await db.categories.toArray()).map((c) => [`${c.kind}:${c.extId}`, c.enabled]));
const wait = async (f: () => boolean) => { for (let i = 0; i < 100 && !f(); i++) await new Promise((r) => setTimeout(r, 20)); };

beforeAll(async () => {
  w = await startFakeWorker();
  setCloudHttp(async (r) => {
    const res = await fetch(r.url, { method: r.method ?? "GET", headers: r.headers, body: r.body || undefined, redirect: "manual" });
    return { status: res.status, text: await res.text(), retryAfter: res.headers.get("retry-after") ?? "", etag: res.headers.get("etag") ?? "" };
  });
  await loadCloud();
  await saveWorker(w.base, true);
  await storeToken(w.token, "dev-1");
});
afterAll(() => { w.close(); setCloudHttp(null); });

beforeEach(async () => {
  await db.categories.clear(); await db.sources.clear(); await db.settings.delete(`cloud.prefsAt.${ID}`);
  await setPrefsSync(true);
  w.providers = [{ id: ID, kind: "XTREAM", name: "A", url: "http://h:80", username: "u", password: "p" }];
  w.state.putCount = 0;
  sid = await saveSource({ ...emptySource(), name: "A", type: "xtream", server: "http://h:80", username: "u", password: "p", cloudId: ID, cloudOrigin: "cloud", cid: 3, langs: ["FR"], state: "ready" });
  const base = { sourceId: 3, label: "", badge: null, count: 0, adult: 0 as const, ord: 0, enabled: 1 as const };
  await db.categories.bulkAdd([
    { ...base, kind: "live", extId: "10", name: "FR| Sport" }, { ...base, kind: "live", extId: "11", name: "FR| Info" },
    { ...base, kind: "movie", extId: "20", name: "FR| Films" }, { ...base, kind: "series", extId: "30", name: "UK | Shows" },
  ]);
  vi.mocked(syncSource).mockClear();
});

describe("publication", () => {
  it("envoie langues + désactivées avec updatedAt et mémorise la version publiée", async () => {
    await db.categories.filter((c) => c.extId === "11").modify({ enabled: 0 });
    expect(await publishPrefs(sid)).toBe("sent");
    expect(w.providers[0]!.prefs).toMatchObject({ langs: ["fr"], disabled: { live: ["11"], movie: [], series: [] } });
    expect(await getPrefsAt(ID)).toBe(w.providers[0]!.prefs!.updatedAt);
  });
  it("source non liée ou préférence désactivée : rien n'est envoyé", async () => {
    await setPrefsSync(false);
    expect(await publishPrefs(sid)).toBe("skipped");
    notePrefsChanged(sid);
    expect(w.state.putCount).toBe(0);
  });
  it("409 : la version du compte plus récente est appliquée localement", async () => {
    w.providers[0]!.prefs = { langs: ["en"], disabled: { live: ["10"], movie: [], series: [] }, updatedAt: Date.now() + 60_000, by: "d2" };
    expect(await publishPrefs(sid)).toBe("applied");
    expect((await enabledOf())["live:10"]).toBe(0);
    expect((await listSources())[0]!.langs).toEqual(["UK"]);
    expect(await getPrefsAt(ID)).toBe(w.providers[0]!.prefs!.updatedAt);
  });
  it("anti-rebond : des changements rapprochés donnent un seul PUT", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    notePrefsChanged(sid); vi.advanceTimersByTime(1000); notePrefsChanged(sid); notePrefsChanged(sid);
    vi.advanceTimersByTime(1400);
    expect(w.state.putCount).toBe(0);
    vi.advanceTimersByTime(200);
    vi.useRealTimers();
    await wait(() => w.state.putCount > 0);
    await new Promise((r) => setTimeout(r, 100));
    expect(w.state.putCount).toBe(1);
  });
});

describe("réception", () => {
  const remote = (updatedAt: number) => [{ ...w.providers[0]!, prefs: { langs: ["fr"], disabled: { live: ["10"], movie: ["20"], series: [] }, updatedAt, by: "d2" } }];
  it("applique les réglages plus récents, sans republier (pas de ping-pong), et demande une relecture", async () => {
    const resync = await applyRemotePrefs(remote(500));
    expect(resync).toEqual([sid]);
    expect(await enabledOf()).toEqual({ "live:10": 0, "live:11": 1, "movie:20": 0, "series:30": 1 });
    expect(await getPrefsAt(ID)).toBe(500);
    expect(w.state.putCount).toBe(0);
    // Même version réexaminée : plus rien à faire.
    expect(await applyRemotePrefs(remote(500))).toEqual([]);
  });
  it("une version plus ancienne que celle déjà appliquée est ignorée", async () => {
    await applyRemotePrefs(remote(500));
    await db.categories.filter((c) => c.extId === "10").modify({ enabled: 1 });
    expect(await applyRemotePrefs(remote(400))).toEqual([]);
    expect((await enabledOf())["live:10"]).toBe(1);
  });
  it("préférence désactivée : rien n'est appliqué", async () => {
    await setPrefsSync(false);
    expect(await applyRemotePrefs(remote(900))).toEqual([]);
    expect((await enabledOf())["live:10"]).toBe(1);
    expect(useCloud.getState().prefsSync).toBe(false);
  });
});
