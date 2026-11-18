// Chaînes masquées et ordre des catégories par profil : migration du schéma (v7 -> v8) et comportement sur une vraie base (fake-indexeddb).
import "fake-indexeddb/auto";
import Dexie from "dexie";
import { beforeEach, describe, expect, it } from "vitest";
import { db, UltraTvDb } from "./db";
import { listHidden } from "./hidden";
import { hiddenChannelIds, hideChannels, inheritHiddenAll, loadChannelView, projectCategoryOrder, putProfileCatOrders, reconcileHidden, replaceProfileHidden, saveProfileCatOrder, seedProfileCatOrder, showChannels } from "./profileChannels";
import { orderedList, saveOrder } from "./channelOrder";
import type { CategoryRow, ChannelRow } from "./types";

const V7 = {
  sources: "++id",
  categories: "++id, [sourceId+kind], [sourceId+kind+extId]",
  channels: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+epg], [sourceId+streamId], *words",
  movies: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+catExt+rating], [sourceId+streamId], *words",
  series: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+catExt+rating], [sourceId+seriesId], *words",
  programs: "++id, [sourceId+epg+start], [sourceId+end]",
  favorites: "&key, [profile+sourceId+kind], addedAt",
  history: "&key, [profile+sourceId], updatedAt",
  details: "&key",
  settings: "&key",
  reminders: "&id, start",
  recordings: "&id, recStart, state",
  channelOrder: "&key, [profile+sourceId]",
  hiddenChannels: "&key, sourceId",
  profileHidden: "&key, [profile+sourceId], sourceId",
  manualChannels: "&key, sourceId",
};

describe("migration v8", () => {
  it("les chaînes masquées globales passent à tous les profils et l'ordre actuel des catégories devient celui de chaque profil", async () => {
    const name = "ultratv-migration-v8";
    await Dexie.delete(name);
    const old = new Dexie(name);
    old.version(7).stores(V7);
    await old.open();
    await old.table("sources").add({ id: 1, name: "S", cid: 5 });
    await old.table("categories").bulkAdd([
      { sourceId: 5, kind: "live", extId: "a", ord: 1 }, { sourceId: 5, kind: "live", extId: "b", ord: 0 }, { sourceId: 5, kind: "movie", extId: "m", ord: 0 },
    ]);
    await old.table("hiddenChannels").bulkAdd([{ key: "1:10", sourceId: 1, streamId: 10, catExt: "a", name: "Dix" }, { key: "1:11", sourceId: 1, streamId: 11, catExt: null, name: "Onze" }]);
    old.close();

    const next = new UltraTvDb(name);
    await next.open();
    // Pas de localStorage en test : un seul profil d'origine « main ».
    const hid = await next.profileHiddenChannels.toArray();
    expect(hid.map((r) => r.key).sort()).toEqual(["main:1:10", "main:1:11"]);
    expect(await next.hiddenChannels.count()).toBe(2); // « par tous » conservé : la synchro continue de les écarter
    const ord = await next.profileCatOrder.toArray();
    expect(ord.find((r) => r.kind === "live")?.ids).toEqual(["b", "a"]);
    expect(ord.find((r) => r.kind === "movie")?.ids).toEqual(["m"]);
    next.close();
    await Dexie.delete(name);
  });
});

const cat = (kind: CategoryRow["kind"], extId: string, ord: number, sourceId = 7): CategoryRow => ({ sourceId, kind, extId, name: extId, label: extId, badge: null, count: 1, enabled: 1, adult: 0, ord });
const chan = (streamId: number, ord: number, catExt: string, num = ord + 1): ChannelRow => ({ sourceId: 7, catExt, ord, streamId, num, name: `c${streamId}`, display: `c${streamId}`, norm: `c${streamId}`, country: null, q: 0, flags: 0, sep: 0, logo: null, epg: null, archive: 0 });
const PROFILES = ["main", "p2"];
const SID = 1;

beforeEach(async () => {
  await Promise.all([db.sources, db.categories, db.channels, db.hiddenChannels, db.profileHiddenChannels, db.profileCatOrder, db.channelOrder, db.favorites].map((t) => t.clear()));
  await db.sources.put({ id: SID, name: "S", cid: 7, counts: { live: 4, movie: 0, series: 0 } } as never);
  await db.categories.bulkAdd([cat("live", "a", 0), cat("live", "b", 1), cat("live", "c", 2)]);
  await db.channels.bulkAdd([chan(1, 0, "a"), chan(2, 1, "a"), chan(3, 2, "a"), chan(4, 3, "b")]);
});

describe("masquer par profil", () => {
  it("une chaîne masquée par UN profil reste au catalogue et seul ce profil la filtre", async () => {
    const r = await hideChannels(SID, 7, "main", PROFILES, [{ streamId: 2, catExt: "a", name: "c2" }]);
    expect(r).toEqual({ removed: 0, reshow: false });
    expect(await db.channels.where("[sourceId+streamId]").equals([7, 2]).count()).toBe(1);
    expect(await listHidden(SID)).toHaveLength(0);
    expect([...await hiddenChannelIds("main", SID)]).toEqual([2]);
    expect((await hiddenChannelIds("p2", SID)).size).toBe(0);
  });

  it("masquée par TOUS les profils : supprimée du catalogue, comptée en moins, catégorie corrigée", async () => {
    await hideChannels(SID, 7, "main", PROFILES, [{ streamId: 2, catExt: "a", name: "c2" }]);
    const r = await hideChannels(SID, 7, "p2", PROFILES, [{ streamId: 2, catExt: "a", name: "c2" }]);
    expect(r.removed).toBe(1);
    expect(await db.channels.where("[sourceId+streamId]").equals([7, 2]).count()).toBe(0);
    expect((await listHidden(SID)).map((h) => h.streamId)).toEqual([2]);
    expect((await db.sources.get(SID))!.counts.live).toBe(3);
    expect((await db.categories.where("[sourceId+kind+extId]").equals([7, "live", "a"]).first())!.count).toBe(0);
  });

  it("réafficher pour un profil une chaîne masquée par tous : elle redevient « propre » aux autres et demande une synchro", async () => {
    await hideChannels(SID, 7, "main", PROFILES, [{ streamId: 2, catExt: "a", name: "c2" }]);
    await hideChannels(SID, 7, "p2", PROFILES, [{ streamId: 2, catExt: "a", name: "c2" }]);
    const r = await showChannels(SID, 7, "main", PROFILES, [2]);
    expect(r.reshow).toBe(true);
    expect(await listHidden(SID)).toHaveLength(0);
    expect((await hiddenChannelIds("p2", SID)).has(2)).toBe(true);
  });

  it("réafficher une chaîne masquée par ce seul profil : instantané, pas de synchro", async () => {
    await hideChannels(SID, 7, "main", PROFILES, [{ streamId: 2, catExt: "a", name: "c2" }]);
    const r = await showChannels(SID, 7, "main", PROFILES, [2]);
    expect(r.reshow).toBe(false);
    expect((await hiddenChannelIds("main", SID)).size).toBe(0);
  });

  it("un seul profil : tout ce qu'il masque est retiré du catalogue (comportement d'avant)", async () => {
    const r = await hideChannels(SID, 7, "main", ["main"], [{ streamId: 1, catExt: "a", name: "c1" }]);
    expect(r.removed).toBe(1);
  });

  it("un profil créé hérite des chaînes déjà supprimées ; un profil supprimé peut rendre des chaînes unanimes", async () => {
    await hideChannels(SID, 7, "main", ["main"], [{ streamId: 3, catExt: "a", name: "c3" }]);
    await inheritHiddenAll("p2");
    expect((await hiddenChannelIds("p2", SID)).has(3)).toBe(true);
    // p2 ne masque que 4 ; avec 3 hérité, 3 reste « par tous ».
    await db.profileHiddenChannels.where("[profile+sourceId]").equals(["main", SID]).delete();
    await hideChannels(SID, 7, "p2", ["main", "p2"], [{ streamId: 4, catExt: "b", name: "c4" }]);
    // main supprimé : p2 est seul, tout ce qu'il masque (3 et 4) est retiré du catalogue.
    const r = await reconcileHidden(SID, 7, ["p2"]);
    expect(r.removed).toBe(1);
    expect(await db.channels.where("[sourceId+streamId]").equals([7, 4]).count()).toBe(0);
  });

  it("remplacement d'un profil (réglages reçus) sans toucher aux autres", async () => {
    await hideChannels(SID, 7, "main", PROFILES, [{ streamId: 1, catExt: "a", name: "c1" }]);
    await replaceProfileHidden("p2", SID, [{ streamId: 4, catExt: "b", name: "c4" }]);
    expect([...await hiddenChannelIds("main", SID)]).toEqual([1]);
    expect([...await hiddenChannelIds("p2", SID)]).toEqual([4]);
  });
});

describe("lecture par profil", () => {
  it("vue : filtre en mémoire et numéros affichés sans les chaînes masquées qui précèdent", async () => {
    await hideChannels(SID, 7, "main", PROFILES, [{ streamId: 1, catExt: "a", name: "c1" }]);
    const view = await loadChannelView("main", { id: SID, cid: 7 });
    const rows = await db.channels.where("[sourceId+ord]").between([7, -1], [7, Infinity]).filter((c) => !view.keep || view.keep(c)).toArray();
    expect(rows.map((c) => c.streamId)).toEqual([2, 3, 4]);
    // Catégorie « a » : 1 masquée ; les chaînes 2 et 3 (num 2 et 3) deviennent 1 et 2 ; la catégorie « b » ne bouge pas.
    expect(rows.map((c) => view.num(c))).toEqual([1, 2, 4]);
    // L'autre profil voit tout, avec les numéros d'origine.
    const other = await loadChannelView("p2", { id: SID, cid: 7 });
    expect(other.keep).toBeNull();
    expect(other.num(chan(2, 1, "a"))).toBe(2);
  });

  it("la file de zapping d'une liste ordonnée saute les chaînes masquées par le profil", async () => {
    await saveOrder("main", SID, "a", [3, 1, 2]);
    await hideChannels(SID, 7, "main", PROFILES, [{ streamId: 1, catExt: "a", name: "c1" }]);
    expect((await orderedList("main", SID, 7, "a"))!.map((c) => c.streamId)).toEqual([3, 2]);
    await saveOrder("p2", SID, "a", [3, 1, 2]);
    expect((await orderedList("p2", SID, 7, "a"))!.map((c) => c.streamId)).toEqual([3, 1, 2]);
  });
});

describe("ordre des catégories par profil", () => {
  const order = async () => (await db.categories.where("[sourceId+kind]").equals([7, "live"]).sortBy("ord")).map((c) => c.extId);

  it("chaque profil a son ordre ; le changement de profil le recopie dans `ord`", async () => {
    await putProfileCatOrders(SID, [{ profile: "main", kind: "live", ids: ["c", "b", "a"] }, { profile: "p2", kind: "live", ids: ["b", "a", "c"] }]);
    await projectCategoryOrder("main");
    expect(await order()).toEqual(["c", "b", "a"]);
    await projectCategoryOrder("p2");
    expect(await order()).toEqual(["b", "a", "c"]);
  });

  it("une catégorie nouvelle (absente de l'ordre enregistré) passe à la suite", async () => {
    await putProfileCatOrders(SID, [{ profile: "main", kind: "live", ids: ["c", "a"] }]);
    await projectCategoryOrder("main");
    expect(await order()).toEqual(["c", "a", "b"]);
  });

  it("un profil sans ordre enregistré laisse `ord` tel quel", async () => {
    await projectCategoryOrder("main");
    expect(await order()).toEqual(["a", "b", "c"]);
  });

  it("l'ordre affiché est enregistré pour le profil courant seulement ; un nouveau profil part de l'ordre affiché", async () => {
    await db.categories.where("[sourceId+kind+extId]").equals([7, "live", "c"]).modify({ ord: -1 });
    await saveProfileCatOrder("main", SID, 7, "live");
    expect((await db.profileCatOrder.get(`main:${SID}:live`))!.ids).toEqual(["c", "a", "b"]);
    expect(await db.profileCatOrder.get(`p2:${SID}:live`)).toBeUndefined();
    await seedProfileCatOrder("p2");
    expect((await db.profileCatOrder.get(`p2:${SID}:live`))!.ids).toEqual(["c", "a", "b"]);
    await seedProfileCatOrder("main"); // n'écrase pas un ordre existant
    expect((await db.profileCatOrder.get(`main:${SID}:live`))!.ids).toEqual(["c", "a", "b"]);
  });
});
