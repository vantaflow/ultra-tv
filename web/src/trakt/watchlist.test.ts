import { beforeEach, describe, expect, it } from "vitest";
import { installMemStorage } from "./memstorage";
installMemStorage();
import type { TraktItem } from "@/cloud/client";
import { loadWatch, RECENT_MS, saveWatch, summarize, updateWatch, watchId } from "./watchlist";

const t0 = 1_000_000_000;
const item = (type: "movie" | "show", title: string, keys: string[]): TraktItem => ({ type, tmdb: null, year: null, title, keys });

describe("« Disponible de ta liste »", () => {
  beforeEach(() => localStorage.clear());

  it("premier calcul : référence posée, rien de signalé", () => {
    const r = updateWatch(null, ["m:a", "m:b"], new Set(["m:a"]), t0);
    expect(r.newly).toEqual([]);
    expect(r.state).toEqual({ unavailable: ["m:b"], recent: {} });
  });
  it("devenu disponible : signalé une fois, reste dans la rangée 14 jours", () => {
    const ids = ["m:a", "m:b"];
    const base = updateWatch(null, ids, new Set(["m:a"]), t0).state;
    const r1 = updateWatch(base, ids, new Set(ids), t0 + 1000);
    expect(r1.newly).toEqual(["m:b"]);
    expect(r1.state.recent).toEqual({ "m:b": t0 + 1000 });
    const r2 = updateWatch(r1.state, ids, new Set(ids), t0 + 2000);
    expect(r2.newly).toEqual([]);
    expect(Object.keys(r2.state.recent)).toEqual(["m:b"]);
    expect(updateWatch(r2.state, ids, new Set(ids), t0 + 1000 + RECENT_MS + 1).state.recent).toEqual({});
  });
  it("déjà disponible à son entrée dans la liste : pas une nouveauté ; retiré de la liste : disparaît", () => {
    const base = updateWatch(null, ["m:a"], new Set(["m:a"]), t0).state;
    expect(updateWatch(base, ["m:a", "m:z"], new Set(["m:a", "m:z"]), t0 + 5).newly).toEqual([]);
    const b2 = updateWatch(null, ["m:a", "m:b"], new Set(), t0).state;
    const r1 = updateWatch(b2, ["m:a", "m:b"], new Set(["m:a", "m:b"]), t0 + 1);
    const r2 = updateWatch(r1.state, ["m:a"], new Set(["m:a"]), t0 + 2);
    expect(Object.keys(r2.state.recent)).toEqual(["m:a"]);
  });
  it("disparu du catalogue puis de retour : redevient une nouveauté", () => {
    const base = updateWatch(null, ["m:a"], new Set(["m:a"]), t0).state;
    const gone = updateWatch(base, ["m:a"], new Set(), t0 + 1);
    expect(gone.state.unavailable).toEqual(["m:a"]);
    expect(updateWatch(gone.state, ["m:a"], new Set(["m:a"]), t0 + 2).newly).toEqual(["m:a"]);
  });
  it("identifiant, résumé et persistance par source (fichier illisible ignoré)", () => {
    expect(watchId(item("movie", "Alien", ["alien"]))).toBe("m:alien");
    expect(watchId(item("show", "Lost", []))).toBe("s:lost");
    expect(summarize(["A", "B"])).toBe("A, B");
    expect(summarize(["A", "B", "C", "D"])).toBe("A, B, C…");
    const st = { unavailable: ["m:a"], recent: { "m:b": 42 } };
    saveWatch("3", st);
    expect(loadWatch("3")).toEqual(st);
    expect(loadWatch("4")).toBeNull();
    localStorage.setItem("utv.trakt.watch.v1", "{bad");
    expect(loadWatch("3")).toBeNull();
  });
});
