// Réglages partagés facultatifs (ordre des catégories, chaînes masquées, ordre des chaînes) : envoi et application de bout en bout.
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
import { getOrder, listOrders, resetOrder, saveOrder } from "@/db/channelOrder";
import { addHidden, listHidden } from "@/db/hidden";
import { emptySource, getSource, saveSource } from "@/db/sources";
import { usePrefs } from "@/state/prefs";
import { setCloudHttp } from "./client";
import { startFakeWorker, type FakeWorker } from "./fakeWorker";
import { applyPrefsTo, loadCloud, publishPrefs, saveWorker, setPrefsSync, storeToken } from "./service";

let w: FakeWorker;
let sid = 0;
const ID = "a0000002";
const ch = (streamId: number, ord: number, catExt: string) => ({ sourceId: 4, catExt, ord, streamId, num: ord, name: `c${streamId}`, display: `c${streamId}`, norm: `c${streamId}`, sep: 0 } as never);
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
  await db.categories.clear(); await db.sources.clear(); await db.channels.clear(); await db.channelOrder.clear(); await db.hiddenChannels.clear();
  await db.settings.delete(`cloud.prefsAt.${ID}`);
  await setPrefsSync(true);
  usePrefs.setState({ profiles: [{ id: "main", name: "", color: "#000" }, { id: "p2", name: "Enfants", color: "#111" }] });
  w.providers = [{ id: ID, kind: "XTREAM", name: "A", url: "http://h:80", username: "u", password: "p" }];
  sid = await saveSource({ ...emptySource(), name: "A", type: "xtream", server: "http://h:80", username: "u", password: "p", cloudId: ID, cloudOrigin: "cloud", cid: 4, langs: null, state: "ready", counts: { live: 4, movie: 0, series: 0 } });
  const base = { sourceId: 4, label: "", badge: null, count: 2, adult: 0 as const, enabled: 1 as const, kind: "live" as const };
  await db.categories.bulkAdd([{ ...base, extId: "10", name: "A", ord: 0 }, { ...base, extId: "11", name: "B", ord: 1 }, { ...base, extId: "12", name: "C", ord: 2 }]);
  await db.channels.bulkAdd([ch(1, 0, "10"), ch(2, 1, "10"), ch(3, 2, "11"), ch(4, 3, "11")]);
});

describe("publication des champs facultatifs", () => {
  it("envoie l'ordre des catégories, les chaînes masquées et l'ordre des chaînes (profil par nom)", async () => {
    await db.categories.filter((c) => c.extId === "12").modify({ ord: -1 });
    await addHidden(sid, [{ streamId: 9, catExt: "10", name: "Cachée" }]);
    await saveOrder("main", sid, "__fav__", [2, 1]);
    await saveOrder("p2", sid, "10", [4, 3]);
    await saveOrder("ancien-profil", sid, "10", [1]); // profil supprimé : non envoyé
    expect(await publishPrefs(sid)).toBe("sent");
    const p = w.providers[0]!.prefs!;
    expect(p.order!.live).toEqual(["12", "10", "11"]);
    expect(p.hidden).toEqual([{ i: "9", c: "10", n: "Cachée" }]);
    expect(p.chOrder).toEqual(expect.arrayContaining([{ p: "Principal", s: "__fav__", k: ["2", "1"] }, { p: "Enfants", s: "10", k: ["4", "3"] }]));
    expect(p.chOrder).toHaveLength(2);
  });
  it("un changement d'ordre des chaînes déclenche l'envoi (écouteur branché sur saveOrder / resetOrder)", async () => {
    await saveOrder("main", sid, "__fav__", [2, 1]);
    await wait(() => w.state.putCount > 0);
    expect(w.state.putCount).toBe(1);
    await resetOrder("main", sid, "__fav__");
    await wait(() => w.state.putCount > 1);
    expect(w.state.putCount).toBe(2);
  });
});

describe("entrées que le bureau ne sait pas interpréter", () => {
  it("hidden à identifiant non numérique et chOrder d'un profil inconnu reçus d'Android : toujours présents à la publication suivante", async () => {
    const remote = {
      langs: null, disabled: { live: [], movie: [], series: [] }, updatedAt: Date.now() - 1000,
      hidden: [{ i: "abc-1", c: "10", n: "Chaîne TV" }, { i: "2", c: "10", n: "c2" }],
      chOrder: [{ p: "Enfants", s: "10", k: ["4", "3"] }, { p: "Principal", s: "11", k: ["3", "4"] }],
    };
    await applyPrefsTo((await getSource(sid))!, remote);
    // « Enfants » existe localement dans ces tests : on le renomme pour qu'il soit inconnu du bureau.
    usePrefs.setState({ profiles: [{ id: "main", name: "", color: "#000" }] });
    await applyPrefsTo((await getSource(sid))!, remote);
    expect(await publishPrefs(sid)).toBe("sent");
    const p = w.providers[0]!.prefs!;
    expect(p.hidden).toEqual(expect.arrayContaining([{ i: "abc-1", c: "10", n: "Chaîne TV" }, { i: "2", c: "10", n: "c2" }]));
    expect(p.chOrder).toEqual(expect.arrayContaining([{ p: "Enfants", s: "10", k: ["4", "3"] }, { p: "Principal", s: "11", k: ["3", "4"] }]));
    // Réaffichée sur le bureau : la chaîne numérique disparaît, l'entrée étrangère reste.
    const { removeHidden } = await import("@/db/hidden");
    await removeHidden(sid, [2]);
    await publishPrefs(sid);
    expect(w.providers[0]!.prefs!.hidden).toEqual([{ i: "abc-1", c: "10", n: "Chaîne TV" }]);
  });
});

describe("application des champs facultatifs", () => {
  const base = { langs: null, disabled: { live: [], movie: [], series: [] }, updatedAt: 99 };
  it("ordre des catégories réécrit dans ord ; chaînes masquées retirées du catalogue ; ordre des chaînes remplacé", async () => {
    await saveOrder("main", sid, "11", [3]); // absent de la réception : effacé
    await saveOrder("p2", sid, "10", [1]); // remplacé
    const resync = await applyPrefsTo((await getSource(sid))!, {
      ...base,
      order: { live: ["11", "12", "10"], movie: [], series: [] },
      hidden: [{ i: "2", c: "10", n: "c2" }],
      chOrder: [{ p: "enfants", s: "10", k: ["2", "1"] }, { p: "Inconnu", s: "10", k: ["1"] }],
    });
    expect(resync).toBe(false); // rien n'est à réafficher
    const cats = (await db.categories.toArray()).sort((a, b) => a.ord - b.ord).map((c) => c.extId);
    expect(cats).toEqual(["11", "12", "10"]);
    expect((await db.channels.toArray()).map((c) => c.streamId).sort()).toEqual([1, 3, 4]);
    expect((await db.categories.filter((c) => c.extId === "10").first())!.count).toBe(1);
    expect((await getSource(sid))!.counts.live).toBe(3);
    expect((await listHidden(sid)).map((h) => h.streamId)).toEqual([2]);
    expect(await getOrder("p2", sid, "10")).toEqual([2, 1]);
    expect(await getOrder("main", sid, "11")).toBeNull();
    expect((await listOrders(sid)).length).toBe(1);
  });
  it("une chaîne qui n'est plus masquée côté compte : synchro à relancer", async () => {
    await addHidden(sid, [{ streamId: 2, catExt: "10", name: "c2" }]);
    expect(await applyPrefsTo((await getSource(sid))!, { ...base, hidden: [] })).toBe(true);
    expect(await listHidden(sid)).toEqual([]);
  });
  it("champs absents (appareil plus ancien) : rien n'est touché", async () => {
    await addHidden(sid, [{ streamId: 2, catExt: "10", name: "c2" }]);
    await saveOrder("main", sid, "10", [2, 1]);
    expect(await applyPrefsTo((await getSource(sid))!, base)).toBe(false);
    expect((await db.categories.toArray()).sort((a, b) => a.ord - b.ord).map((c) => c.extId)).toEqual(["10", "11", "12"]);
    expect((await listHidden(sid)).length).toBe(1);
    expect(await getOrder("main", sid, "10")).toEqual([2, 1]);
  });
});
