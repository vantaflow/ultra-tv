import { describe, expect, it } from "vitest";
import { localFavorites, remoteFavChanges } from "./sharedState";
import type { SharedFav } from "./client";

const f = (r: string, on = true, at = 100): SharedFav => ({ p: "Principal", k: "LIVE", r, on, at });
const m = (...l: SharedFav[]) => new Map(l.map((e) => [`${e.p}|${e.k}|${e.r}`, e] as const));

describe("localFavorites", () => {
  it("ajout = on maintenant, retrait = tombe, inchangé = date gardée", () => {
    const out = localFavorites(m(f("1"), f("2")), [{ p: "Principal", k: "LIVE", r: "1" }, { p: "Principal", k: "LIVE", r: "3" }], 500);
    expect(out.get("Principal|LIVE|1")).toEqual(f("1", true, 100));
    expect(out.get("Principal|LIVE|2")).toEqual(f("2", false, 500));
    expect(out.get("Principal|LIVE|3")).toEqual(f("3", true, 500));
  });
});

describe("localFavorites : favori reçu mais jamais appliqué ici", () => {
  it("n'est pas transformé en retrait (sinon il disparaîtrait de la TV)", () => {
    const out = localFavorites(m(f("1"), f("9")), [{ p: "Principal", k: "LIVE", r: "1" }], 500, new Set(["Principal|LIVE|1"]));
    expect(out.get("Principal|LIVE|9")).toEqual(f("9", true, 100));
  });
  it("retiré ici après avoir été présent : tombe", () => {
    const out = localFavorites(m(f("1")), [], 500, new Set(["Principal|LIVE|1"]));
    expect(out.get("Principal|LIVE|1")).toEqual(f("1", false, 500));
  });
});

describe("remoteFavChanges", () => {
  it("seulement le plus récent", () => {
    expect(remoteFavChanges(m(f("1", true, 100), f("2", true, 300)), [f("1", false, 200), f("2", false, 250), f("4")]).map((e) => e.r)).toEqual(["1", "4"]);
  });
});
