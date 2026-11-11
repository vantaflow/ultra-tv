// Visibilité du catalogue PENDANT une synchro lente : l'ancien catalogue reste utilisable jusqu'à la bascule,
// et une première génération (ou un catalogue local vide) devient visible dès la fin du direct, catégories comprises.
import "fake-indexeddb/auto";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { db } from "@/db/db";
import { emptySource } from "@/db/sources";
import { visibleCategories } from "@/db/queries";
import { catalogNeedsRebuild } from "./rebuild";
import { runSync } from "./core";

const cats = [
  { category_id: "1", category_name: "FR | SPORT" },
  { category_id: "2", category_name: "FR | CINEMA" },
];
const live = Array.from({ length: 6 }, (_, i) => ({ num: i, name: `FR: Chaine ${i}`, stream_id: 10 + i, category_id: i % 2 ? "1" : "2" }));
const vod = Array.from({ length: 30 }, (_, i) => ({ num: i, name: `FR - Film ${i} (2020)`, stream_id: 1000 + i, category_id: i % 2 ? "1" : "2", container_extension: "mkv" }));
const series = [{ series_id: 7, name: "FR - Une série (2020)", category_id: "1", cover: "http://x/y.jpg" }];

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
/** Porte fermée : la réponse « films » du serveur n'est envoyée qu'à son ouverture (serveur lent). */
let gate: { vod: Promise<void>; openVod: () => void };
const newGate = () => { let open!: () => void; const p = new Promise<void>((r) => { open = r; }); gate = { vod: p, openVod: open }; };
newGate();

let server: http.Server;
let base = "";
beforeAll(async () => {
  server = http.createServer(async (req, res) => {
    const u = new URL(req.url!, "http://x");
    const a = u.searchParams.get("action");
    const send = async (v: unknown) => {
      // Réponse découpée et retardée : le flux arrive goutte à goutte.
      const s = JSON.stringify(v);
      res.setHeader("content-type", "application/json");
      const half = Math.ceil(s.length / 2);
      res.write(s.slice(0, half)); await sleep(15); res.end(s.slice(half));
    };
    if (u.searchParams.get("password") !== "pw") return send({ user_info: { auth: 0 } });
    await sleep(10);
    switch (a) {
      case null: return send({ user_info: { auth: 1, status: "Active" } });
      case "get_live_categories": case "get_vod_categories": case "get_series_categories": return send(cats);
      case "get_live_streams": return send(live);
      case "get_vod_streams": await gate.vod; return send(vod);
      case "get_series": return send(series);
      default: return send(false);
    }
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});
afterAll(() => { server.close(); });
beforeEach(async () => { newGate(); await Promise.all(db.tables.map((t) => t.clear())); });

const src = () => ({ ...emptySource(), id: 1, name: "t", server: base, username: "u", password: "pw" });
const T = { mode: "direct" } as const;
const waitFor = async (cond: () => boolean | Promise<boolean>, ms = 5000) => {
  const t0 = Date.now();
  while (!(await cond())) { if (Date.now() - t0 > ms) throw new Error("délai dépassé"); await sleep(10); }
};
const inGen = (table: "channels" | "movies" | "series", cid: number) =>
  db[table].where("[sourceId+ord]").between([cid, -1], [cid, Infinity]).count();

/** Lance une synchro dont la phase Films reste bloquée jusqu'à `gate.openVod()`. */
function startSlowSync(signal?: AbortSignal) {
  let phase = "";
  // Comme `useSync.start` : on repart de la ligne courante de la source (cid, compteurs à jour).
  const done = db.sources.get(1).then((row) => runSync({ source: { ...src(), ...row }, transport: T, signal, onProgress: (p) => { phase = p.phase; } }));
  done.catch(() => undefined);
  return { done, phase: () => phase };
}

describe("catalogue visible pendant une synchro lente", () => {
  it("source déjà synchronisée : l'ancien catalogue (catégories, compteurs, éléments) reste visible jusqu'à la bascule", async () => {
    await db.sources.put(src());
    gate.openVod();
    await runSync({ source: src(), transport: T, onProgress: () => undefined });
    const old = (await db.sources.get(1))!;
    expect(old.cid).toBeGreaterThan(0);
    expect((await visibleCategories(old.cid, "live")).length).toBeGreaterThan(0);

    newGate();
    const s = startSlowSync();
    await waitFor(() => s.phase() === "movie");
    // Synchro en cours (Films bloqués ; ~15 min chez l'utilisateur) : la source pointe toujours vers l'ancienne génération.
    const mid = (await db.sources.get(1))!;
    expect(mid.cid).toBe(old.cid);
    expect(mid.counts).toEqual(old.counts);
    for (const kind of ["live", "movie", "series"] as const) {
      const vis = await visibleCategories(mid.cid, kind);
      expect(vis.length).toBeGreaterThan(0);
      expect(vis.every((c) => c.count > 0)).toBe(true);
    }
    expect(await inGen("channels", mid.cid)).toBe(6);
    expect(await inGen("movies", mid.cid)).toBe(30);
    // La reconstruction au démarrage ne se déclenche pas à tort pendant la synchro.
    expect(await catalogNeedsRebuild(mid)).toBe(false);

    gate.openVod();
    await s.done;
    const end = (await db.sources.get(1))!;
    expect(end.cid).not.toBe(old.cid);
    expect((await visibleCategories(end.cid, "movie")).length).toBeGreaterThan(0);
    expect(await inGen("channels", old.cid)).toBe(0);
  });

  it("première synchro : le direct et ses catégories sont visibles dès sa fin, pendant l'arrivée des films", async () => {
    await db.sources.put(src());
    const s = startSlowSync();
    await waitFor(() => s.phase() === "movie");
    const mid = (await db.sources.get(1))!;
    expect(mid.cid).toBeGreaterThan(0);
    expect(mid.counts.live).toBe(6);
    const vis = await visibleCategories(mid.cid, "live");
    expect(vis.map((c) => c.count).sort()).toEqual([3, 3]);
    gate.openVod();
    await s.done;
    expect((await visibleCategories((await db.sources.get(1))!.cid, "movie")).length).toBeGreaterThan(0);
  });

  it("catalogue local vide (reconstruction au démarrage) : même comportement qu'une première synchro", async () => {
    // Source déjà synchronisée dont la génération a disparu (catégories perdues) : cid non nul mais plus rien dessous.
    await db.sources.put({ ...src(), cid: 5, state: "ready", lastSyncAt: Date.now() - 1000, counts: { live: 6, movie: 30, series: 1 } });
    await db.settings.put({ key: "cid.counter", value: 5 });
    expect(await catalogNeedsRebuild((await db.sources.get(1))!)).toBe(true);
    const s = startSlowSync();
    await waitFor(() => s.phase() === "movie");
    const mid = (await db.sources.get(1))!;
    expect((await visibleCategories(mid.cid, "live")).length).toBe(2);
    expect(mid.counts.live).toBe(6);
    gate.openVod();
    await s.done;
    const end = (await db.sources.get(1))!;
    expect(end.counts).toEqual({ live: 6, movie: 30, series: 1 });
    expect((await visibleCategories(end.cid, "series")).length).toBe(1);
  });

  it("annulation d'une première synchro déjà basculée : la source est débranchée proprement", async () => {
    await db.sources.put(src());
    const ctrl = new AbortController();
    const s = startSlowSync(ctrl.signal);
    await waitFor(() => s.phase() === "movie");
    ctrl.abort(); gate.openVod();
    await expect(s.done).rejects.toThrow();
    const after = (await db.sources.get(1))!;
    expect(after.cid).toBe(0);
    expect(after.counts).toEqual({ live: 0, movie: 0, series: 0 });
  });
});
