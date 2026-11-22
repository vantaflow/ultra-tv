import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { db } from "./db";
import { blockedCategoryIds, codeCategoryIds, ensureCategoryAccess, lockedCategoryIds, setCategoryLock, unlistedCategoryIds } from "./contentGate";
import { applyProfileHidden } from "./profileCategories";
import type { CategoryRow } from "./types";
import { usePrefs } from "@/state/prefs";
import { useUnlocks } from "@/state/unlocks";

const CID = 7;
const SRC = { id: 1, cid: CID };
const cat = (extId: string, name: string, adult: 0 | 1 = 0): CategoryRow => ({ sourceId: CID, kind: "movie", extId, name, label: name, badge: null, count: 5, enabled: 1, adult, ord: 0 });
const sorted = (s: Set<string>) => [...s].sort();

beforeEach(async () => {
  await db.categories.clear(); await db.profileHidden.clear(); await db.settings.clear();
  await db.categories.bulkAdd([cat("1", "Action"), cat("2", "XXX Hot"), cat("3", "Kids"), cat("4", "Divers", 1), cat("5", "Drame")]);
  await applyProfileHidden(1, new Map([["main", { live: [], movie: ["5"], series: [] }]]));
  usePrefs.setState({ profiles: [{ id: "main", name: "", color: "#000" }, { id: "kid", name: "K", color: "#111", kids: true }], profileId: "main" });
  useUnlocks.getState().clear();
});

describe("porte des contenus (base locale)", () => {
  it("accueil : masquées et adultes écartées, même hors profil Enfants", async () => {
    expect(sorted(await blockedCategoryIds("main", SRC, "movie", "home"))).toEqual(["2", "4", "5"]);
    expect(sorted(await blockedCategoryIds("main", SRC, "movie", "list"))).toEqual(["5"]);
  });
  it("profil Enfants : adultes écartées des listes et de la liste des catégories", async () => {
    expect(sorted(await blockedCategoryIds("kid", SRC, "movie", "list"))).toEqual(["2", "4"]);
    expect(sorted(await unlistedCategoryIds("kid", SRC, "movie"))).toEqual(["2", "4"]);
  });
  it("verrou : écarté partout, cadenas dans la liste des catégories, déverrouillé par le code pour la session", async () => {
    await setCategoryLock(1, "movie", "3", true);
    expect(sorted(await lockedCategoryIds(1, "movie"))).toEqual(["3"]);
    expect(sorted(await blockedCategoryIds("main", SRC, "movie"))).toEqual(["3", "5"]);
    expect(sorted(await unlistedCategoryIds("main", SRC, "movie"))).toEqual(["5"]);
    expect(sorted(await codeCategoryIds("main", SRC, "movie"))).toEqual(["3"]);

    const refuse = vi.fn(async () => false);
    expect(await ensureCategoryAccess("main", SRC, "movie", "3", "Kids", refuse)).toBe(false);
    expect(refuse).toHaveBeenCalledOnce();
    const accept = vi.fn(async () => true);
    expect(await ensureCategoryAccess("main", SRC, "movie", "3", "Kids", accept)).toBe(true);
    expect(sorted(await blockedCategoryIds("main", SRC, "movie"))).toEqual(["5"]);
    // Accueil : jamais de contenu verrouillé, même déverrouillé.
    expect(sorted(await blockedCategoryIds("main", SRC, "movie", "home"))).toContain("3");
    // Déjà déverrouillée : plus de demande.
    expect(await ensureCategoryAccess("main", SRC, "movie", "3", "Kids", refuse)).toBe(true);
    expect(refuse).toHaveBeenCalledOnce();
  });
  it("changement de profil : tout se reverrouille", async () => {
    await setCategoryLock(1, "movie", "3", true);
    await ensureCategoryAccess("main", SRC, "movie", "3", "Kids", async () => true);
    usePrefs.getState().set({ profileId: "kid" });
    expect(useUnlocks.getState().keys.size).toBe(0);
  });
  it("catégorie libre : aucune demande de code", async () => {
    const ask = vi.fn(async () => false);
    expect(await ensureCategoryAccess("main", SRC, "movie", "1", "Action", ask)).toBe(true);
    expect(await ensureCategoryAccess("main", SRC, "movie", "2", "XXX", ask)).toBe(true);
    expect(await ensureCategoryAccess("kid", SRC, "movie", "2", "XXX", ask)).toBe(false);
    expect(ask).toHaveBeenCalledOnce();
  });
});
