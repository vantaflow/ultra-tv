import "fake-indexeddb/auto";
import { readFileSync } from "node:fs";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { emptySource } from "@/db/sources";
import type { SyncProgress } from "@/db/types";
import { GuideBanner } from "@/screens/Guide";
import { EPG_STALE_MS, GUIDE_IDLE, guideNeedsLoad, startGuideLoad, type GuideLoadDeps, type GuideLoadState } from "./guideLoad";

const src = (cid: number) => ({ ...emptySource(), id: cid, cid });
const deps = (o: Partial<GuideLoadDeps> = {}): GuideLoadDeps => ({
  sync: vi.fn(async () => 10), upcoming: async () => 0, lastAt: async () => 0, markLoaded: vi.fn(async () => undefined), now: () => 1_000_000_000_000, ...o,
});

describe("ouverture du guide : chargement en arrière-plan", () => {
  it("jamais chargé : la synchro part toute seule, sans page intermédiaire", async () => {
    const d = deps();
    const states: GuideLoadState[] = [];
    expect(await startGuideLoad(src(1), (s) => states.push(s), d)).toBe(true);
    expect(d.sync).toHaveBeenCalledTimes(1);
    expect(states.map((s) => s.phase)).toEqual(["loading", "idle"]);
    expect(d.markLoaded).toHaveBeenCalledTimes(1);
  });

  it("guide récent : rien n'est relancé ; guide périmé : relancé", async () => {
    const d = deps({ upcoming: async () => 5000, lastAt: async () => 1_000_000_000_000 - 1000 });
    expect(await startGuideLoad(src(2), () => undefined, d)).toBe(false);
    expect(d.sync).not.toHaveBeenCalled();
    const old = deps({ upcoming: async () => 5000, lastAt: async () => 1_000_000_000_000 - EPG_STALE_MS - 1 });
    expect(await startGuideLoad(src(3), () => undefined, old)).toBe(true);
    expect(guideNeedsLoad({ upcoming: 0, lastAt: 1_000_000_000_000, now: 1_000_000_000_000 })).toBe(true);
  });

  it("progression : octets et programmes remontent à l'écran", async () => {
    const states: GuideLoadState[] = [];
    const d = deps({ sync: async (_s, onProgress) => { onProgress({ phase: "epg", ratio: 0, counts: { live: 0, movie: 0, series: 0 }, bytes: 5_000_000, programs: 1200 } as SyncProgress); return 1; } });
    await startGuideLoad(src(4), (s) => states.push(s), d);
    expect(states.some((s) => s.phase === "loading" && s.bytes === 5_000_000 && s.programs === 1200)).toBe(true);
  });

  it("échec : état d'erreur (la grille reste affichée), « Réessayer » relance et réussit", async () => {
    let calls = 0;
    const d = deps({ sync: async () => { if (++calls === 1) throw new Error("réseau"); return 3; } });
    const states: GuideLoadState[] = [];
    await startGuideLoad(src(5), (s) => states.push(s), d);
    expect(states.at(-1)!.phase).toBe("error");
    await startGuideLoad(src(5), (s) => states.push(s), d, true);
    expect(states.at(-1)).toEqual(GUIDE_IDLE);
    expect(calls).toBe(2);
  });

  it("rouvrir l'écran pendant un chargement se rattache à celui-ci (pas de seconde synchro)", async () => {
    let release: () => void = () => undefined;
    const d = deps({ sync: vi.fn(() => new Promise<number>((r) => { release = () => r(1); })) });
    const first = startGuideLoad(src(6), () => undefined, d);
    await new Promise((r) => setTimeout(r, 10));
    const got: string[] = [];
    expect(await startGuideLoad(src(6), (s) => got.push(s.phase), d)).toBe(true);
    release();
    await first;
    expect(d.sync).toHaveBeenCalledTimes(1);
    expect(got).toEqual(["loading", "idle"]);
  });
});

describe("bandeau du guide", () => {
  const html = (state: GuideLoadState) => renderToStaticMarkup(createElement(GuideBanner, { state, onRetry: () => undefined }));
  it("chargement : bandeau discret avec les Mo reçus", () => {
    const h = html({ phase: "loading", bytes: 12_300_000, programs: 0 });
    expect(h).toContain("g-banner");
    expect(h).toMatch(/12\.3/);
  });
  it("erreur : message et bouton Réessayer", () => {
    const h = html({ phase: "error", bytes: 0, programs: 0 });
    expect(h).toContain('role="alert"');
    expect(h).toContain("<button");
  });
  it("repos : aucun bandeau", () => expect(html(GUIDE_IDLE)).toBe(""));
});

describe("écran Guide", () => {
  it("n'a plus de page intermédiaire « Charger le guide » et affiche la grille", () => {
    const code = readFileSync(new URL("../screens/Guide.tsx", import.meta.url), "utf8");
    expect(code).not.toContain("guide.loadEpg");
    expect(code).not.toContain("StateCard");
    expect(code).toContain("useGuideLoad(");
    expect(code).toContain('className="guide-grid"');
  });
});
