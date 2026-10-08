import { beforeEach, describe, expect, it } from "vitest";
import { installMemStorage } from "./memstorage";
installMemStorage();
import { loadSavedRefs, saveRefs, ROWS_STORAGE_KEY, type TraktRows } from "./hooks";

const row = (kind: "movie" | "show", ref: number) => ({ kind, ref, row: {} as never });
describe("rangées résolues persistées", () => {
  beforeEach(() => localStorage.clear());
  it("aller-retour par cid", () => {
    const rows: TraktRows = { watchlist: [row("movie", 1)], recommendations: [], trending: [row("show", 5), row("movie", 2)], popular: [] };
    saveRefs(7, rows);
    expect(loadSavedRefs(7)).toEqual({ watchlist: [["movie", 1]], recommendations: [], trending: [["show", 5], ["movie", 2]], popular: [] });
    expect(loadSavedRefs(8)).toBeNull();
  });
  it("données corrompues ignorées", () => {
    localStorage.setItem(ROWS_STORAGE_KEY, "{bad");
    expect(loadSavedRefs(1)).toBeNull();
    localStorage.setItem(ROWS_STORAGE_KEY, JSON.stringify({ cid: 1, refs: { watchlist: [["x", 1], ["movie", 3]] } }));
    expect(loadSavedRefs(1)!.watchlist).toEqual([["movie", 3]]);
    expect(loadSavedRefs(1)!.popular).toEqual([]);
  });
});
