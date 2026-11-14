import "fake-indexeddb/auto";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { db } from "@/db/db";
import { usePrefs } from "@/state/prefs";
import { setCloudHttp } from "./client";
import { startFakeWorker, type FakeWorker } from "./fakeWorker";
import { resetProfileLog } from "./profilesSync";
import { syncProfiles } from "./profilesSync";
import { hexToArgb } from "./profilesLogic";

let w: FakeWorker;
beforeAll(async () => {
  w = await startFakeWorker();
  setCloudHttp(async (r) => {
    const res = await fetch(r.url, { method: r.method ?? "GET", headers: r.headers, body: r.body || undefined, redirect: "manual" });
    return { status: res.status, text: await res.text(), retryAfter: res.headers.get("retry-after") ?? "", etag: res.headers.get("etag") ?? "" };
  });
});
afterAll(() => { w.close(); setCloudHttp(null); });
beforeEach(async () => {
  resetProfileLog();
  w.state.profiles = [];
  usePrefs.setState({ profiles: [{ id: "main", name: "", color: "#D91E2B" }], profileId: "main", deviceLang: "en", lang: "en" });
  await db.favorites.clear(); await db.history.clear(); await db.channelOrder.clear();
});
const sync = () => syncProfiles(w.base, w.token);
const names = () => usePrefs.getState().profiles.map((p) => p.name);

describe("synchro des profils avec le faux Worker", () => {
  it("un profil créé ici part vers le Worker (sans PIN), un profil de la TV arrive ici", async () => {
    usePrefs.getState().set({ profiles: [...usePrefs.getState().profiles, { id: "p1", name: "Zoé", color: "#3B82F6" }] });
    w.state.profiles.push({ n: "Papa", c: hexToArgb("#10B981"), k: true, at: Date.now() - 1000, on: true, l: "es" });
    await sync();
    const sent = (w.state.lastProfilesBody as { profiles: { n: string; c: number }[] }).profiles;
    expect(sent.map((p) => p.n)).toEqual(["Principal", "Zoé"]);
    expect(sent[1]!.c).toBe(hexToArgb("#3B82F6"));
    expect(JSON.stringify(sent)).not.toMatch(/pin/i);
    expect(names()).toEqual(["", "Zoé", "Papa"]);
    expect(usePrefs.getState().profiles[2]).toMatchObject({ color: "#10B981", kids: true, lang: "es" });
    expect(w.state.profiles.map((p) => p.n).sort()).toEqual(["Papa", "Principal", "Zoé"]);
  });

  it("renommage depuis la TV : même id, favoris et historique gardés", async () => {
    usePrefs.getState().set({ profiles: [...usePrefs.getState().profiles, { id: "p1", name: "Papa", color: "#3B82F6" }] });
    await db.favorites.put({ key: "p1:1:live:5", profile: "p1", sourceId: 1, kind: "live", refId: 5, name: "TF1", image: null, addedAt: 1 });
    await sync();
    w.state.profiles.push({ n: "Papa", c: 0, k: false, at: Date.now() + 5000, on: false });
    w.state.profiles.push({ n: "Papa 2", c: hexToArgb("#3B82F6"), k: false, at: Date.now() + 5000, on: true, f: "Papa" });
    await sync();
    expect(names()).toEqual(["", "Papa 2"]);
    expect(usePrefs.getState().profiles[1]!.id).toBe("p1");
    expect(await db.favorites.count()).toBe(1);
    // Deuxième échange : plus rien à appliquer, pas de profil recréé.
    await sync();
    expect(names()).toEqual(["", "Papa 2"]);
  });

  it("renommage local envoyé avec l'ancien nom (f)", async () => {
    usePrefs.getState().set({ profiles: [...usePrefs.getState().profiles, { id: "p1", name: "Papa", color: "#3B82F6" }] });
    await sync();
    usePrefs.getState().set({ profiles: usePrefs.getState().profiles.map((p) => (p.id === "p1" ? { ...p, name: "Papi" } : p)) });
    await sync();
    const sent = (w.state.lastProfilesBody as { profiles: { n: string; f?: string; on: boolean }[] }).profiles;
    expect(sent.find((p) => p.n === "Papi")).toMatchObject({ f: "Papa", on: true });
    expect(sent.find((p) => p.n === "Papa")).toMatchObject({ on: false });
  });

  it("suppression depuis la TV : profil et données supprimés ici, jamais le dernier", async () => {
    usePrefs.getState().set({ profiles: [...usePrefs.getState().profiles, { id: "p1", name: "Léa", color: "#3B82F6" }], profileId: "p1" });
    await db.favorites.put({ key: "p1:1:live:5", profile: "p1", sourceId: 1, kind: "live", refId: 5, name: "TF1", image: null, addedAt: 1 });
    await db.history.put({ key: "p1:1:movie:2", profile: "p1", sourceId: 1, kind: "movie", refId: 2, title: "t", image: null, pos: 1, dur: 2, updatedAt: 1 });
    await sync();
    w.state.profiles = w.state.profiles.map((p) => (p.n === "Léa" ? { ...p, on: false, at: Date.now() + 5000 } : p));
    await sync();
    expect(names()).toEqual([""]);
    expect(usePrefs.getState().profileId).toBe("main");
    expect(await db.favorites.count()).toBe(0);
    expect(await db.history.count()).toBe(0);
    w.state.profiles = w.state.profiles.map((p) => (p.n === "Principal" ? { ...p, on: false, at: Date.now() + 9000 } : p));
    await sync();
    expect(names()).toEqual([""]);
  });

  it("suppression locale : tombe envoyée, le profil ne revient pas ; langue du profil courant suit", async () => {
    usePrefs.getState().set({ profiles: [...usePrefs.getState().profiles, { id: "p1", name: "Léa", color: "#3B82F6" }] });
    await sync();
    usePrefs.getState().set({ profiles: usePrefs.getState().profiles.filter((p) => p.id !== "p1") });
    await sync();
    expect(w.state.profiles.find((p) => p.n === "Léa")!.on).toBe(false);
    expect(names()).toEqual([""]);
    // Langue : profil avec langue propre prioritaire, sinon celle de l'appareil.
    expect(usePrefs.getState().lang).toBe("en");
    usePrefs.getState().set({ lang: "fr" });
    expect(usePrefs.getState().lang).toBe("fr");
    expect(usePrefs.getState().profiles[0]!.lang).toBe("fr");
    await sync();
    expect(w.state.profiles.find((p) => p.n === "Principal")!.l).toBe("fr");
  });

  it("langue reçue pour le profil courant : l'interface change ; Worker sans route : rien ne casse", async () => {
    w.state.profiles.push({ n: "Principal", c: hexToArgb("#D91E2B"), k: false, at: Date.now() + 1000, on: true, l: "es" });
    await sync();
    expect(usePrefs.getState().lang).toBe("es");
    w.state.hasProfiles = false;
    expect(await sync()).toBe(false);
    w.state.hasProfiles = true;
  });
});
