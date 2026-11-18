import { describe, expect, it } from "vitest";
import { dedupeFavChanges, localFavorites, migrateLegacyFavs, remoteFavChanges } from "./sharedState";
import { identityMapper, m3uMapper } from "./idmap";
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

describe("migration de l'ancien identifiant vers l'identifiant traduit", () => {
  // entrées : 1 = « a », 2 = « m3u-0 », 3 = « 2 » (tvg-id numérique réel)
  const map = m3uMapper(["a", null, "2"]);
  const loc = (r: string) => `Principal|LIVE|${r}`;
  it("renomme sans tombe, en gardant on/at", () => {
    const { known, wasLocal } = migrateLegacyFavs(m(f("1", true, 100)), new Set([loc("1")]), map);
    expect(known.get(loc("a"))).toEqual(f("a", true, 100));
    expect(known.has(loc("1"))).toBe(false);
    expect(wasLocal).toEqual(new Set([loc("a")]));
    // le favori toujours présent localement ne devient pas une tombe
    const out = localFavorites(known, [{ p: "Principal", k: "LIVE", r: "a" }], 500, wasLocal);
    expect([...out.values()].filter((e) => !e.on)).toEqual([]);
    expect(out.get(loc("a"))).toEqual(f("a", true, 100));
  });
  it("un tvg-id numérique réel n'est pas renommé", () => {
    const { known } = migrateLegacyFavs(m(f("2", true, 100)), new Set(), map);
    expect(known.get(loc("2"))).toEqual(f("2", true, 100));
  });
  it("sans traduction : inchangé", () => {
    const k = m(f("1"));
    expect(migrateLegacyFavs(k, new Set(), identityMapper).known).toBe(k);
  });
});

describe("dedupeFavChanges", () => {
  const map = m3uMapper(["a", null]);
  it("tombe à l'ancien numéro + « on » traduit : le favori reste, quel que soit l'ordre", () => {
    for (const order of [[f("1", false, 500), f("a", true, 400)], [f("a", true, 400), f("1", false, 500)]]) {
      expect(dedupeFavChanges(order, map)).toEqual([f("a", true, 400)]);
    }
  });
  it("même élément sous deux identifiants : le plus récent gagne, « on » à égalité", () => {
    expect(dedupeFavChanges([f("a", true, 100), f("a", false, 200)], map)).toEqual([f("a", false, 200)]);
    expect(dedupeFavChanges([f("a", false, 100), f("1", true, 100)], map)).toEqual([f("1", true, 100)]);
  });
  it("tombe à l'ancien numéro seule : appliquée ; entrée non traduisible : gardée", () => {
    expect(dedupeFavChanges([f("1", false, 300)], map)).toEqual([f("1", false, 300)]);
    expect(dedupeFavChanges([f("zzz", true, 1)], map)).toEqual([f("zzz", true, 1)]);
  });
});
