import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/net/secrets", () => ({ encryptSecret: async (s: string) => s, decryptSecret: async (s: string) => s }));
vi.mock("@/cloud/service", async () => {
  const { create } = await import("zustand");
  return { getToken: async () => "tok", useCloud: create(() => ({ paired: true, worker: "https://w.example" })) };
});

import { setCloudHttp, traktLibrary, TokenRejectedError, RateLimitedError } from "@/cloud/client";
import { usePrefs } from "@/state/prefs";
import { refreshTraktLibrary, traktLang, useTraktLibrary, hydrateTraktLibrary, LIB_STORAGE_KEY } from "./library";
import { useCloud } from "@/cloud/service";
import { installMemStorage } from "./memstorage";
installMemStorage();

const item = { type: "movie", tmdb: 1, year: 1979, title: "Alien", keys: ["alien"] };
const linked = { linked: true, updatedAt: 5, watchlist: [item], recommendations: [], trending: [{ ...item, title: "Heat", keys: ["heat"] }, { bad: 1 }], popular: [{ type: "show", tmdb: 2, year: 2008, title: "BB", keys: ["breaking bad"] }], watched: { movies: [item], shows: [{ type: "show", tmdb: 2, year: 2008, title: "BB", keys: ["breaking bad"], episodes: ["1x1"] }] } };
let calls: { url: string; headers?: Record<string, string> }[] = [];
let reply: { status: number; body: unknown } = { status: 200, body: linked };
const http = async (r: { url: string; headers?: Record<string, string> }) => {
  calls.push(r);
  return { status: reply.status, text: JSON.stringify(reply.body), retryAfter: "12", etag: "" };
};

beforeEach(() => {
  calls = []; reply = { status: 200, body: linked };
  setCloudHttp(http);
  useCloud.setState({ paired: true, worker: "https://w.example" });
  useTraktLibrary.setState({ lib: null, loadedAt: 0, key: "" });
  usePrefs.getState().set({ lang: "fr" });
});
afterEach(() => setCloudHttp(null));

describe("traktLibrary (client)", () => {
  it("GET avec jeton et langue", async () => {
    const r = await traktLibrary("https://w.example", "tok", "es");
    expect(calls[0]!.url).toBe("https://w.example/api/device/trakt/library?lang=es");
    expect(calls[0]!.headers?.authorization).toBe("Bearer tok");
    expect(r.linked && r.watched.shows[0]!.episodes).toEqual(["1x1"]);
  });
  it("linked:false", async () => {
    reply = { status: 200, body: { linked: false } };
    expect(await traktLibrary("https://w.example", "tok", "en")).toEqual({ linked: false });
  });
  it("401 et 429", async () => {
    reply = { status: 401, body: {} };
    await expect(traktLibrary("https://w.example", "tok", "en")).rejects.toBeInstanceOf(TokenRejectedError);
    reply = { status: 429, body: {} };
    await expect(traktLibrary("https://w.example", "tok", "en")).rejects.toBeInstanceOf(RateLimitedError);
  });
});

describe("refreshTraktLibrary", () => {
  it("langue de l'interface, repli « en »", async () => {
    expect(traktLang("xx")).toBe("en");
    await refreshTraktLibrary();
    expect(calls[0]!.url).toContain("lang=fr");
    expect(useTraktLibrary.getState().lib?.watchlist.length).toBe(1);
  });
  it("pas de nouvel appel avant 15 min, sauf force", async () => {
    await refreshTraktLibrary(); await refreshTraktLibrary();
    expect(calls.length).toBe(1);
    await refreshTraktLibrary(true);
    expect(calls.length).toBe(2);
  });
  it("un échec garde la dernière bonne valeur", async () => {
    await refreshTraktLibrary();
    reply = { status: 429, body: {} };
    await refreshTraktLibrary(true);
    expect(useTraktLibrary.getState().lib?.updatedAt).toBe(5);
  });
  it("linked:false vide tout", async () => {
    await refreshTraktLibrary();
    reply = { status: 200, body: { linked: false } };
    await refreshTraktLibrary(true);
    expect(useTraktLibrary.getState().lib).toBeNull();
  });
  it("non appairé : aucun appel, état vide", async () => {
    await refreshTraktLibrary();
    useCloud.setState({ paired: false });
    calls = [];
    await refreshTraktLibrary(true);
    expect(calls.length).toBe(0);
    expect(useTraktLibrary.getState().lib).toBeNull();
  });
});

describe("tendances / populaires et persistance", () => {
  it("parse trending et popular, absents -> []", async () => {
    const r = await traktLibrary("https://w.example", "tok", "fr");
    expect(r.linked && r.trending.map((i) => i.title)).toEqual(["Heat"]);
    expect(r.linked && r.popular.length).toBe(1);
    reply = { status: 200, body: { linked: true, updatedAt: 1, watchlist: [], recommendations: [], watched: {} } };
    const r2 = await traktLibrary("https://w.example", "tok", "fr");
    expect(r2.linked && r2.trending).toEqual([]);
    expect(r2.linked && r2.popular).toEqual([]);
  });
  it("persiste la bibliothèque puis la réhydrate", async () => {
    localStorage.clear();
    await refreshTraktLibrary(true);
    expect(localStorage.getItem(LIB_STORAGE_KEY)).toContain("Alien");
    const at = useTraktLibrary.getState().loadedAt;
    useTraktLibrary.setState({ lib: null, loadedAt: 0, key: "" });
    expect(hydrateTraktLibrary()).toBe(true);
    const s = useTraktLibrary.getState();
    expect(s.lib?.watchlist[0]!.title).toBe("Alien");
    expect(s.loadedAt).toBe(at);
    expect(s.watched.movies.size).toBeGreaterThan(0);
    // clé identique et récent : aucun appel réseau au démarrage
    calls = []; await refreshTraktLibrary(); expect(calls.length).toBe(0);
  });
  it("ancienne sauvegarde sans trending : []; JSON corrompu ignoré", () => {
    localStorage.setItem(LIB_STORAGE_KEY, JSON.stringify({ key: "k", loadedAt: 1, lib: { linked: true, updatedAt: 1, watchlist: [], recommendations: [], watched: { movies: [], shows: [] } } }));
    expect(hydrateTraktLibrary()).toBe(true);
    expect(useTraktLibrary.getState().lib?.trending).toEqual([]);
    localStorage.setItem(LIB_STORAGE_KEY, "{oops");
    expect(hydrateTraktLibrary()).toBe(false);
  });
  it("non lié ou non appairé : efface la sauvegarde", async () => {
    localStorage.setItem(LIB_STORAGE_KEY, "x");
    reply = { status: 200, body: { linked: false } };
    await refreshTraktLibrary(true);
    expect(localStorage.getItem(LIB_STORAGE_KEY)).toBeNull();
  });
});
