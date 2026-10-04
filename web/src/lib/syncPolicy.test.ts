import { describe, expect, it } from "vitest";
import { isStaleSyncing, langsToSync, needsFirstSync } from "./syncPolicy";

describe("langsToSync", () => {
  it("Xtream avec choix partiel : liste des langues", () => expect(langsToSync("xtream", new Set(["FR", "…"]), 14)).toEqual(["FR", "…"]));
  it("Xtream, tout coché : null (toutes)", () => expect(langsToSync("xtream", new Set(["FR"]), 1)).toBeNull());
  it("M3U : jamais de filtre", () => expect(langsToSync("m3u", new Set(["FR"]), 14)).toBeNull());
  it("get.php converti : le type effectif est xtream, le filtre s'applique (régression : tout était synchronisé)", () => {
    expect(langsToSync("xtream", new Set(["FR"]), 14)).toEqual(["FR"]);
  });
});
describe("états de synchro", () => {
  it("première synchro", () => { expect(needsFirstSync({ state: "new", lastSyncAt: 0 })).toBe(true); expect(needsFirstSync({ state: "new", lastSyncAt: 5 })).toBe(false); });
  it("syncing persisté = périmé", () => { expect(isStaleSyncing({ state: "syncing" })).toBe(true); expect(isStaleSyncing({ state: "ready" })).toBe(false); });
});
