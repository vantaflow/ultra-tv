// Source XTREAM après une mise à jour : vrai runSync contre un faux serveur Xtream en mémoire, avec une base et des
// préférences écrites au format du bureau 1.2.38. Les catégories doivent rester activées et visibles (enabled && count > 0,
// règle de useCategories), et une synchro qui échoue en route ne doit jamais remplacer un bon catalogue par un catalogue vide.
import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { db, purgeOrphanGenerations } from "@/db/db";
import { categoriesOf } from "@/db/queries";
import { emptySource, getSource, saveSource } from "@/db/sources";
import type { Source } from "@/db/types";
import { runSync } from "./core";

const SERVER = "http://xtream.test:8080";

/** Faux serveur player_api.php : catégories (fr/ar/sans langue), quelques éléments par catégorie ; pannes réglables. */
class FakeXtream {
  failActions = new Set<string>();
  /** Échecs aussi des appels par catégorie (category_id=…). */
  failPerCategory = true;
  calls: string[] = [];
  fetch = async (input: string | URL | Request): Promise<Response> => {
    const u = new URL(typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url);
    const action = u.searchParams.get("action") ?? "";
    this.calls.push(action);
    const json = (v: unknown) => new Response(JSON.stringify(v));
    const failing = this.failActions.has(action) && (this.failPerCategory || !u.searchParams.has("category_id"));
    if (failing) return new Response("boom", { status: 500 });
    const cats = [{ category_id: "1", category_name: "FR | News" }, { category_id: "2", category_name: "AR | Sport" }, { category_id: "3", category_name: "Divers" }];
    switch (action) {
      case "get_live_categories": case "get_vod_categories": case "get_series_categories": return json(cats);
      case "get_live_streams": return json(cats.flatMap((c, i) => [0, 1].map((n) => ({ num: i * 2 + n, name: `FR: ${c.category_name} ${n}`, stream_id: Number(c.category_id) * 100 + n, category_id: Number(c.category_id), epg_channel_id: null }))));
      case "get_vod_streams": return json(cats.flatMap((c) => [0, 1, 2].map((n) => ({ num: n, name: `Film ${c.category_id}-${n} (2020)`, stream_id: Number(c.category_id) * 1000 + n, category_id: c.category_id, container_extension: "mp4" }))));
      case "get_series": return json(cats.flatMap((c) => [0, 1].map((n) => ({ num: n, name: `Série ${c.category_id}-${n}`, series_id: Number(c.category_id) * 1000 + n, category_id: c.category_id }))));
      default: return json({ user_info: { auth: 1, status: "Active" }, server_info: {} });
    }
  };
}

let fake: FakeXtream;
const source = (over: Partial<Source> = {}): Source => ({ ...emptySource(), id: 1, name: "X", type: "xtream", server: SERVER, username: "u", password: "p", ...over });
const run = async (preserveFlags = true) => runSync({ source: (await getSource(1))!, transport: { mode: "direct" }, onProgress: () => {}, preserveFlags });
/** Ce que `useCategories` affiche : activées et non vides. */
const visible = async (kind: "live" | "movie" | "series") => { const s = (await getSource(1))!; return (await categoriesOf(s.cid, kind)).filter((c) => c.enabled && c.count > 0).map((c) => c.extId); };

beforeEach(async () => {
  fake = new FakeXtream();
  vi.stubGlobal("fetch", fake.fetch);
  vi.spyOn(console, "warn").mockImplementation(() => {});
  await Promise.all([db.sources, db.categories, db.channels, db.movies, db.series, db.settings].map((t) => t.clear()));
});
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe("filtre de langues au format 1.2.38", () => {
  it.each([
    ["['FR']", ["FR"], ["1"]],
    ["null (toutes les langues)", null, ["1", "2", "3"]],
    ["['FR','AR','other'] (code « autre » 1.2.38)", ["FR", "AR", "OTHER"], ["1", "2", "3"]],
  ])("première synchro, langs = %s : catégories activées et visibles", async (_n, langs, expected) => {
    await saveSource(source({ langs: langs as string[] | null }));
    await run(false);
    for (const k of ["live", "movie", "series"] as const) expect(await visible(k), k).toEqual(expected);
  });

  it("seconde synchro (lancement après mise à jour) avec choix manuels de 1.2.38 : activations et masquages conservés", async () => {
    await saveSource(source({ langs: ["FR"] }));
    await run(false);
    // L'utilisateur active « Divers » et masque « News » (comme l'écrivait le bureau 1.2.38).
    const s = (await getSource(1))!;
    await db.categories.where("[sourceId+kind+extId]").equals([s.cid, "live", "3"]).modify({ enabled: 1 });
    await db.categories.where("[sourceId+kind+extId]").equals([s.cid, "live", "1"]).modify({ enabled: 0 });
    await run(true);
    expect(await visible("live")).toEqual(["3"]);
    expect(await visible("movie")).toEqual(["1"]);
    const after = (await getSource(1))!;
    expect(await db.channels.where("[sourceId+ord]").between([after.cid, -1], [after.cid, Infinity]).count()).toBe(2);
    expect(await db.categories.where("[sourceId+kind]").between([s.cid, ""], [s.cid, "Ffff"]).count()).toBe(0); // ancienne génération supprimée
  });
});

describe("synchro interrompue : jamais un catalogue vide à la place d'un bon", () => {
  const snapshot = async () => {
    const s = (await getSource(1))!;
    const cats = await db.categories.where("[sourceId+kind]").between([s.cid, ""], [s.cid, "￿"]).toArray();
    return { cid: s.cid, counts: s.counts, cats: cats.map((c) => [c.kind, c.extId, c.enabled, c.count]), movies: await db.movies.where("[sourceId+ord]").between([s.cid, -1], [s.cid, Infinity]).count(), series: await db.series.where("[sourceId+ord]").between([s.cid, -1], [s.cid, Infinity]).count() };
  };

  it.each(["get_vod_streams", "get_series"])("%s en erreur (liste complète et appels par catégorie) : la synchro échoue, l'ancien catalogue reste intact", async (action) => {
    await saveSource(source({ langs: null }));
    await run(false);
    const before = await snapshot();
    expect(before.movies).toBeGreaterThan(0);
    fake.failActions.add(action);
    await expect(run(true)).rejects.toThrow();
    expect(await snapshot()).toEqual(before);
    expect(await visible("movie")).toEqual(["1", "2", "3"]);
    // Aucune génération orpheline : seule celle de la source existe.
    expect(await db.categories.count()).toBe(before.cats.length);
  });

  it("liste complète en erreur mais appels par catégorie qui répondent : catalogue complet", async () => {
    await saveSource(source({ langs: null }));
    await run(false);
    fake.failActions.add("get_vod_streams");
    fake.failPerCategory = false;
    await run(true);
    expect(await visible("movie")).toEqual(["1", "2", "3"]);
  });

  it("direct en erreur à la toute première synchro : échec franc, aucune génération laissée", async () => {
    await saveSource(source({ langs: null }));
    fake.failActions.add("get_live_streams");
    await expect(run(false)).rejects.toThrow();
    expect((await getSource(1))!.cid).toBe(0);
    expect(await db.categories.count()).toBe(0);
  });

  it("démarrage après une fermeture en cours de synchro : génération orpheline purgée, génération de la source gardée", async () => {
    await saveSource(source({ langs: null }));
    await run(false);
    const before = await snapshot();
    // Synchro tuée en route (installation de la mise à jour) : une génération partielle reste, marquée « en cours ».
    const orphan = before.cid + 1;
    await db.settings.put({ key: "cid.counter", value: orphan });
    await db.settings.put({ key: "cid.pending", value: [orphan] });
    await db.categories.add({ sourceId: orphan, kind: "live", extId: "1", name: "x", label: "x", badge: null, count: 0, enabled: 1, adult: 0, ord: 0 });
    await db.movies.add({ sourceId: orphan, catExt: "1", ord: 0, streamId: 1, name: "m", title: "m", norm: "m", words: [], year: null, poster: null, rating: 0, ext: "mp4", added: 0 });
    expect(await purgeOrphanGenerations({ startup: true })).toEqual([orphan]);
    expect(await snapshot()).toEqual(before);
  });
});

describe("count des catégories", () => {
  it("tous les compteurs sont recalculés (> 0) après une synchro complète, ids numériques ou texte", async () => {
    await saveSource(source({ langs: null }));
    await run(false);
    const s = (await getSource(1))!;
    const cats = await db.categories.where("[sourceId+kind]").between([s.cid, ""], [s.cid, "￿"]).toArray();
    expect(cats).toHaveLength(9);
    expect(cats.every((c) => c.count > 0)).toBe(true);
  });
});

describe("source M3U : réponse qui n'est pas une playlist", () => {
  const LIST = "#EXTM3U\n#EXTINF:-1 group-title=\"FR | News\",Chaîne 1\nhttp://s.test/1.ts\n#EXTINF:-1 group-title=\"Divers\",Chaîne 2\nhttp://s.test/2.ts\n";
  let body = LIST;
  const m3u = () => source({ type: "m3u", m3uUrl: "http://m3u.test/list.m3u", server: "", username: "", password: "" });
  beforeEach(() => { body = LIST; vi.stubGlobal("fetch", async () => new Response(body)); });

  it("page d'erreur, corps vide ou en-tête seul : la synchro échoue, l'ancien catalogue reste intact", async () => {
    await saveSource(m3u());
    await run(false);
    const s = (await getSource(1))!;
    expect(s.counts.live).toBe(2);
    expect(await visible("live")).toEqual(["FR | News", "Divers"]);
    for (const bad of ["<html>Service indisponible</html>", "", "#EXTM3U\n"]) {
      body = bad;
      await expect(run(true), JSON.stringify(bad)).rejects.toThrow("not-m3u");
      const after = (await getSource(1))!;
      expect(after.cid).toBe(s.cid);
      expect(await visible("live")).toEqual(["FR | News", "Divers"]);
    }
  });
});
