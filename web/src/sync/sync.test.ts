import "fake-indexeddb/auto";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { db } from "@/db/db";
import { emptySource } from "@/db/sources";
import { detectLanguages, runSync } from "./core";

const cats = [
  { category_id: "1", category_name: "FR | SPORT" },
  { category_id: "2", category_name: "AR | أفلام" },
  { category_id: "3", category_name: "UK: News" },
];
const live = [
  { num: 1, name: "##### FR SPORT #####", stream_id: 10, category_id: "1" },
  { num: 2, name: "FR: Sport 1 FHD", stream_id: 11, category_id: "1", epg_channel_id: "sport1" },
  { num: 3, name: "AR: قناة", stream_id: 12, category_id: "2" },
  { num: 4, name: "UK: BBC News HD", stream_id: 13, category_id: "3" },
];
const vod = Array.from({ length: 50 }, (_, i) => ({
  num: i, name: `FR - Film numéro ${i} (20${10 + (i % 10)})\u0002`, stream_id: 1000 + i, category_id: i % 2 ? "1" : "2",
  container_extension: "mkv", rating_5based: 3.5, added: "1700000000",
}));
const series = [{ series_id: 7, name: "FR - Une série (2020)", category_id: "1", cover: "http://x/y.jpg", backdrop_path: ["http://x/b.jpg"] }];

let server: http.Server;
let base = "";
beforeAll(async () => {
  server = http.createServer((req, res) => {
    const u = new URL(req.url!, "http://x");
    const a = u.searchParams.get("action");
    const send = (v: unknown, raw = false) => { res.setHeader("content-type", "application/json"); res.end(raw ? (v as string) : JSON.stringify(v)); };
    if (u.searchParams.get("password") !== "pw") return send({ user_info: { auth: 0 } });
    switch (a) {
      case null: return send({ user_info: { auth: 1, status: "Active", max_connections: "1", exp_date: "1900000000" } });
      case "get_live_categories": case "get_vod_categories": case "get_series_categories": return send(cats);
      case "get_live_streams": return send(live);
      case "get_vod_streams": return send(JSON.stringify(vod).replace(/\\u0002/g, "\u0002"), true);
      case "get_series": return send(series);
      default: return send(false);
    }
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});
afterAll(() => { server.close(); });

const src = () => ({ ...emptySource(), id: 1, name: "t", server: base, username: "u", password: "pw" });

describe("synchronisation Xtream", () => {
  it("détecte les langues par catégories", async () => {
    const d = await detectLanguages({ mode: "direct" }, src());
    expect(d.languages.map((l) => l.code).sort()).toEqual(["AR", "FR", "UK"]);
    expect(d.counts).toEqual({ live: 3, movie: 3, series: 3 });
  });

  it("charge tout, analyse les noms et stocke maigre", async () => {
    await db.sources.put({ ...src() });
    const p: string[] = [];
    const counts = await runSync({ source: src(), transport: { mode: "direct" }, onProgress: (x) => p.push(x.phase) });
    expect(counts).toEqual({ live: 3, movie: 50, series: 1 });
    const cid = (await db.sources.get(1))!.cid;
    expect(cid).toBeGreaterThan(0);
    expect(new Set(p)).toContain("done");
    const ch = await db.channels.orderBy("[sourceId+ord]").toArray();
    expect(ch[0]).toMatchObject({ sep: 1, display: "FR SPORT" });
    expect(ch[1]).toMatchObject({ display: "Sport 1", country: "FR", q: 3, epg: "sport1" });
    expect(JSON.stringify(ch)).not.toContain("pw");
    const m = await db.movies.toArray();
    expect(m[0]).toMatchObject({ title: "Film numéro 0", year: 2010, ext: "mkv" });
    expect((await db.categories.toArray()).find((c) => c.extId === "1" && c.kind === "live")).toMatchObject({ label: "FR | SPORT", badge: "FR", count: 2 });
  });

  it("ne synchronise que les langues choisies", async () => {
    const before = (await db.sources.get(1))!;
    const counts = await runSync({ source: { ...before, langs: ["FR"] }, transport: { mode: "direct" }, onProgress: () => undefined });
    expect(counts.live).toBe(1);
    expect(counts.movie).toBe(25);
    const after = (await db.sources.get(1))!;
    expect(after.cid).not.toBe(before.cid);
    // l'ancienne génération a été supprimée, la nouvelle est complète
    expect(await db.channels.where("[sourceId+ord]").between([before.cid, -1], [before.cid, Infinity]).count()).toBe(0);
    const cs = await db.categories.where("[sourceId+kind]").equals([after.cid, "live"]).toArray();
    expect(cs.filter((c) => c.enabled).map((c) => c.badge)).toEqual(["FR"]);
  });

  it("conserve les catégories choisies à la main", async () => {
    const cur = (await db.sources.get(1))!;
    const cat = await db.categories.where("[sourceId+kind+extId]").equals([cur.cid, "movie", "2"]).first();
    await db.categories.update(cat!.id!, { enabled: 1 });
    const counts = await runSync({ source: { ...cur, langs: ["FR"] }, transport: { mode: "direct" }, onProgress: () => undefined, preserveFlags: true });
    expect(counts.movie).toBe(50);
  });

  it("rejette de mauvais identifiants", async () => {
    const { testConnection } = await import("./core");
    await expect(testConnection({ mode: "direct" }, { ...src(), password: "no" })).rejects.toThrow("auth");
    await expect(testConnection({ mode: "direct" }, src())).resolves.toMatchObject({ ok: true, maxConnections: 1 });
  });
});
