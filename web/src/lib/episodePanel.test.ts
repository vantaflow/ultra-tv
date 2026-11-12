import { describe, expect, it } from "vitest";
import { episodePanel, stepIndex, type PanelEpisode } from "./episodePanel";

const ep = (season: number, num: number): PanelEpisode => ({ id: season * 100 + num, title: `E${num}`, num, season, ext: "mp4", secs: 3000, img: null });
const seasons = [1, 2, 3].map((n) => ({ n, eps: [1, 2, 3].map((k) => ep(n, k)) }));

describe("Panneau Épisodes", () => {
  it("saison de l'épisode joué ouverte, épisode courant marqué", () => {
    const p = episodePanel(seasons, { currentId: 202, history: [] });
    expect(p.currentSeason).toBe(2);
    expect(p.seasons[1]!.items.filter((i) => i.current).map((i) => i.ep.id)).toEqual([202]);
    expect(p.seasons.flatMap((s) => s.items).filter((i) => i.current)).toHaveLength(1);
  });

  it("progression, reprise et « Vu »", () => {
    const p = episodePanel(seasons, {
      currentId: 101,
      history: [{ epId: 102, pos: 1500, dur: 3000 }, { epId: 103, pos: 2900, dur: 3000 }, { epId: 201, pos: 10, dur: 3000 }, { pos: 99, dur: 100 }],
      seen: new Set(["3x1"]),
    });
    const s1 = p.seasons[0]!.items;
    expect(s1[1]).toMatchObject({ ratio: 0.5, watched: false, resumeAt: 1500 });
    expect(s1[2]).toMatchObject({ watched: true, resumeAt: undefined });
    expect(p.seasons[1]!.items[0]!.resumeAt).toBeUndefined(); // à peine commencé : repart du début
    expect(p.seasons[2]!.items[0]!.watched).toBe(true); // vu sur Trakt
    expect(s1[0]!.ratio).toBe(0);
  });

  it("épisode inconnu ou saisons vides : première saison", () => {
    const p = episodePanel([{ n: 0, eps: [] }, ...seasons], { currentId: 999, history: [] });
    expect(p.currentSeason).toBe(1);
    expect(p.seasons).toHaveLength(3);
    expect(episodePanel([], { currentId: 1, history: [] }).currentSeason).toBeNull();
  });

  it("navigation au clavier bornée", () => {
    expect(stepIndex(-1, 5, "ArrowDown")).toBe(0);
    expect(stepIndex(0, 5, "ArrowUp")).toBe(0);
    expect(stepIndex(3, 5, "ArrowDown")).toBe(4);
    expect(stepIndex(4, 5, "ArrowDown")).toBe(4);
    expect(stepIndex(2, 5, "Home")).toBe(0);
    expect(stepIndex(2, 5, "End")).toBe(4);
    expect(stepIndex(0, 0, "ArrowDown")).toBe(-1);
  });
});
