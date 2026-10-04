import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { db } from "./db";
import { setCategoriesEnabled } from "./categoryActions";
import type { CategoryRow } from "./types";
import { filterCategories } from "@/lib/categoryLang";

const mk = (name: string, kind: CategoryRow["kind"], extId: string): CategoryRow =>
  ({ sourceId: 7, kind, extId, name, label: name, badge: null, count: 0, enabled: 1, adult: 0, ord: 0 });

beforeEach(async () => {
  await db.categories.clear();
  await db.categories.bulkAdd([mk("FR| Cinema", "movie", "1"), mk("|FR| Series", "series", "2"), mk("UK | Sport", "live", "3"), mk("FRA | Info", "live", "4")]);
});

describe("actions groupées", () => {
  it("désactive la vue filtrée (langue + type) sans toucher au reste", async () => {
    const rows = await db.categories.toArray();
    const n = await setCategoriesEnabled(filterCategories(rows, { lang: "FR", kind: "live" }), 0);
    expect(n).toBe(1);
    const after = await db.categories.toArray();
    expect(after.filter((c) => !c.enabled).map((c) => c.extId)).toEqual(["4"]);
  });
  it("une case de langue couvre tous les types et alias, et ne réécrit que les changements", async () => {
    const rows = await db.categories.toArray();
    expect(await setCategoriesEnabled(filterCategories(rows, { lang: "FR", kind: null }), 0)).toBe(3);
    expect(await setCategoriesEnabled(filterCategories(await db.categories.toArray(), { lang: "FR", kind: null }), 0)).toBe(0);
    expect(await setCategoriesEnabled(await db.categories.toArray(), 1)).toBe(3);
  });
});
