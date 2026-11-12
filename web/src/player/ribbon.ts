// Ruban de la soirée du Direct : programme précédent, en cours et suivants, en segments proportionnels à leur durée.
import type { ProgramRow } from "@/db/types";

export type RibbonSeg = { p: ProgramRow; state: "past" | "now" | "next"; grow: number; ratio: number };

/** Au plus `after` programmes après l'actuel ; durée plafonnée pour qu'un long film n'écrase pas les autres segments. */
export function ribbonSegments(programs: ProgramRow[], now: number, after = 3): RibbonSeg[] {
  const list = [...programs].filter((p) => p.end > p.start).sort((a, b) => a.start - b.start);
  const i = list.findIndex((p) => p.start <= now && p.end > now);
  if (i < 0) return [];
  const mins = (p: ProgramRow) => Math.min(180, Math.max(10, (p.end - p.start) / 60_000));
  const seg = (p: ProgramRow, state: RibbonSeg["state"]): RibbonSeg => ({
    p, state, grow: Math.round(mins(p) * 10) / 10,
    ratio: state === "now" ? Math.min(1, Math.max(0, (now - p.start) / (p.end - p.start))) : state === "past" ? 1 : 0,
  });
  const out: RibbonSeg[] = [];
  if (i > 0) out.push(seg(list[i - 1]!, "past"));
  out.push(seg(list[i]!, "now"));
  for (const p of list.slice(i + 1, i + 1 + after)) out.push(seg(p, "next"));
  return out;
}
