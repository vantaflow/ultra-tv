// Sous-titres du bureau : lecture d'un WebVTT produit par ffmpeg et pose sur la vidéo (pistes texte HTML5).
// Le flux converti repart de zéro à chaque saut (position affichée = départ + lecture) : les repères sont donc
// décalés du départ courant. Fonctions pures, testées.

export interface Cue { start: number; end: number; text: string }

/** « 01:02:03.456 » ou « 02:03.456 » → secondes ; NaN si illisible. */
export function vttTime(s: string): number {
  const m = /^(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})$/.exec(s.trim());
  if (!m) return NaN;
  return Number(m[1] ?? 0) * 3600 + Number(m[2]) * 60 + Number(m[3]) + Number(m[4]!.padEnd(3, "0")) / 1000;
}

/** Repères d'un fichier WebVTT (balises de style conservées telles quelles : le navigateur les interprète). */
export function parseVtt(text: string): Cue[] {
  const out: Cue[] = [];
  for (const block of text.replace(/\r/g, "").split(/\n{2,}/)) {
    const lines = block.split("\n");
    const k = lines.findIndex((l) => l.includes("-->"));
    if (k < 0) continue;
    const [a, rest] = lines[k]!.split("-->");
    const start = vttTime(a ?? "");
    const end = vttTime((rest ?? "").trim().split(/\s+/)[0] ?? "");
    const body = lines.slice(k + 1).join("\n").trim();
    if (Number.isFinite(start) && Number.isFinite(end) && end > start && body) out.push({ start, end, text: body });
  }
  return out;
}

/** Repères décalés du départ de la conversion ; ceux déjà passés sont écartés. */
export function shiftCues(cues: Cue[], offset: number): Cue[] {
  return cues.flatMap((c) => (c.end - offset > 0 ? [{ start: Math.max(0, c.start - offset), end: c.end - offset, text: c.text }] : []));
}

/** Aplatit les chevauchements : un repère ne déborde plus sur le suivant (le rendu HTML5 affiche sinon les deux
 * lignes empilées — certains flux ont des durées qui débordent). 25 ms de battement, un repère entièrement
 * avalé par le suivant est écarté. Fonction pure, testée. */
export function flattenOverlaps(cues: Cue[]): Cue[] {
  const s = [...cues].sort((a, b) => a.start - b.start || a.end - b.end);
  return s.flatMap((c, i) => {
    const n = s[i + 1];
    if (n && c.end > n.start + 0.05) {
      const end = n.start - 0.025;
      if (end <= c.start + 0.05) return [];
      return [{ ...c, end }];
    }
    return [c];
  });
}
