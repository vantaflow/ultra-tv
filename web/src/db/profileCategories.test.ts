import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { db } from "./db";
import { applyProfileHidden, hiddenCategoryIds, listProfileHidden, setCategoriesVisible, setProfileHiddenListener } from "./profileCategories";
import { deleteProfileData } from "./profileData";
import { deleteSource, emptySource, saveSource } from "./sources";
import { visibleCategories } from "./queries";
import type { CategoryRow } from "./types";

const cat = (extId: string, enabled: 0 | 1): CategoryRow => ({ sourceId: 1, kind: "live", extId, name: extId, label: extId, badge: null, count: 5, enabled, adult: 0, ord: 0 });
const rows = () => db.categories.toArray();
const enabled = async () => Object.fromEntries((await rows()).map((c) => [c.extId, c.enabled]));
const PROFILES = ["main", "kids"];

beforeEach(async () => {
  await db.categories.clear(); await db.profileHidden.clear();
  await db.categories.bulkAdd([cat("a", 1), cat("b", 1), cat("c", 0)]);
  setProfileHiddenListener(null);
});

describe("setCategoriesVisible", () => {
  it("masquer pour un profil : l'autre la voit encore, le téléchargement est conservé", async () => {
    const n = await setCategoriesVisible(1, "main", PROFILES, "live", (await rows()).filter((c) => c.extId === "a"), false);
    expect(n).toBe(0);
    expect(await enabled()).toEqual({ a: 1, b: 1, c: 0 });
    const main = await visibleCategories(1, "live", await hiddenCategoryIds("main", 1, "live"));
    const kids = await visibleCategories(1, "live", await hiddenCategoryIds("kids", 1, "live"));
    expect(main.map((c) => c.extId)).toEqual(["b"]);
    expect(kids.map((c) => c.extId).sort()).toEqual(["a", "b"]);
  });
  it("masquée par tous les profils : plus téléchargée (enabled = 0)", async () => {
    const a = () => rows().then((r) => r.filter((c) => c.extId === "a"));
    await setCategoriesVisible(1, "main", PROFILES, "live", await a(), false);
    expect(await setCategoriesVisible(1, "kids", PROFILES, "live", await a(), false)).toBe(1);
    expect((await enabled()).a).toBe(0);
  });
  it("réactiver une catégorie non téléchargée : téléchargée, et masquée pour les autres profils", async () => {
    const c = () => rows().then((r) => r.filter((x) => x.extId === "c"));
    expect(await setCategoriesVisible(1, "main", PROFILES, "live", await c(), true)).toBe(1);
    expect((await enabled()).c).toBe(1);
    expect([...await hiddenCategoryIds("kids", 1, "live")]).toEqual(["c"]);
    expect((await hiddenCategoryIds("main", 1, "live")).size).toBe(0);
  });
  it("« tout masquer » / « tout afficher » pour le profil courant", async () => {
    await setCategoriesVisible(1, "main", PROFILES, "live", await rows(), true); // c devient téléchargée
    await setCategoriesVisible(1, "main", PROFILES, "live", await rows(), false);
    expect(await enabled()).toEqual({ a: 1, b: 1, c: 0 }); // kids affiche encore a et b ; c, masquée par kids puis par main, n'est plus téléchargée
    expect((await hiddenCategoryIds("main", 1, "live")).size).toBe(3);
  });
  it("notifie la publication après un changement utilisateur, pas après une réception", async () => {
    const fn = vi.fn(); setProfileHiddenListener(fn);
    await setCategoriesVisible(1, "main", PROFILES, "live", await rows(), false);
    expect(fn).toHaveBeenCalledWith(1);
    fn.mockClear();
    await applyProfileHidden(1, new Map([["main", { live: ["a"], movie: [], series: [] }], ["kids", { live: [], movie: [], series: [] }]]));
    expect(fn).not.toHaveBeenCalled();
    expect([...await hiddenCategoryIds("main", 1, "live")]).toEqual(["a"]);
    expect((await listProfileHidden(1)).length).toBe(1);
  });
});

describe("suppression", () => {
  it("avec le profil et avec la source", async () => {
    await applyProfileHidden(1, new Map([["main", { live: ["a"], movie: [], series: [] }], ["kids", { live: ["b"], movie: ["m"], series: [] }]]));
    await deleteProfileData("kids");
    expect((await db.profileHidden.toArray()).map((r) => r.profile)).toEqual(["main"]);
    const id = await saveSource({ ...emptySource(), name: "S" });
    await db.profileHidden.put({ key: `main:${id}:live`, profile: "main", sourceId: id, kind: "live", ids: ["x"] });
    await deleteSource(id);
    expect((await db.profileHidden.toArray()).every((r) => r.sourceId !== id)).toBe(true);
  });
});
