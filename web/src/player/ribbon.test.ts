import { describe, expect, it } from "vitest";
import type { ProgramRow } from "@/db/types";
import { ribbonSegments } from "./ribbon";

const H = 3600_000;
const prog = (start: number, end: number, title: string): ProgramRow => ({ sourceId: 1, epg: "x", start, end, title, desc: "" });

describe("ribbonSegments", () => {
  const ps = [prog(0, H, "a"), prog(H, 2 * H, "b"), prog(2 * H, 3 * H, "c"), prog(3 * H, 4 * H, "d"), prog(4 * H, 5 * H, "e"), prog(5 * H, 6 * H, "f")];
  it("précédent, en cours puis trois suivants", () => {
    const s = ribbonSegments(ps, 1.5 * H);
    expect(s.map((x) => `${x.state}:${x.p.title}`)).toEqual(["past:a", "now:b", "next:c", "next:d", "next:e"]);
    expect(s[1]!.ratio).toBeCloseTo(0.5);
  });
  it("sans programme en cours : vide", () => {
    expect(ribbonSegments(ps, 9 * H)).toEqual([]);
  });
  it("sans précédent au début de la grille", () => {
    expect(ribbonSegments(ps, 0.2 * H)[0]!.state).toBe("now");
  });
});
