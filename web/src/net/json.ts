// Lecture JSON tolérante pour les catalogues IPTV : caractères de contrôle dans les chaînes,
// BOM, réponses « false » / objet à la place d'un tableau, et lecture EN FLUX d'un grand tableau
// (180 000 films = 70 Mo) sans jamais tenir la réponse entière en mémoire.

const CONTROL = /[\u0000-\u001f]/g;

/** JSON.parse tolérant : si l'analyse échoue, remplace les caractères de contrôle par des espaces. */
export function parseTolerant<T = unknown>(text: string): T {
  const t = text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
  try {
    return JSON.parse(t) as T;
  } catch {
    return JSON.parse(t.replace(CONTROL, " ")) as T;
  }
}

/** Un tableau JSON attendu ; « false », null ou objet indexé → tableau (possiblement vide). */
export function asArray<T>(v: unknown): T[] {
  if (Array.isArray(v)) return v as T[];
  if (v && typeof v === "object") return Object.values(v as Record<string, T>);
  return [];
}

/**
 * Découpe un texte contenant un tableau JSON en objets de premier niveau, en une passe,
 * en respectant chaînes et échappements. `feed()` renvoie les objets complets reçus jusqu'ici.
 */
export class ArrayObjectSplitter {
  private buf = "";
  private depth = 0;
  private inStr = false;
  private esc = false;
  private start = -1;
  private scanned = 0;

  feed(chunk: string): string[] {
    this.buf += chunk;
    const out: string[] = [];
    const b = this.buf;
    for (let i = this.scanned; i < b.length; i++) {
      const c = b.charCodeAt(i);
      if (this.inStr) {
        if (this.esc) this.esc = false;
        else if (c === 92) this.esc = true;
        else if (c === 34) this.inStr = false;
        continue;
      }
      if (c === 34) this.inStr = true;
      else if (c === 123) { if (this.depth === 0) this.start = i; this.depth++; }
      else if (c === 125) {
        this.depth--;
        if (this.depth === 0 && this.start >= 0) {
          out.push(b.slice(this.start, i + 1));
          this.start = -1;
        }
      }
    }
    if (this.depth === 0) { this.buf = ""; this.scanned = 0; }
    else if (this.start > 0) { this.buf = b.slice(this.start); this.scanned = this.buf.length; this.start = 0; }
    else this.scanned = b.length;
    return out;
  }
}

/**
 * Lit une réponse HTTP contenant un grand tableau d'objets et appelle `onBatch` par lots.
 * Retourne le nombre d'éléments. Si le corps n'est pas un tableau d'objets (ex. `false`, `{}`),
 * retourne 0. `onBytes` permet d'afficher une progression.
 */
export async function streamObjects<T>(
  res: Response,
  onBatch: (items: T[]) => Promise<void> | void,
  opts: { batchSize?: number; onBytes?: (n: number) => void; signal?: AbortSignal } = {},
): Promise<number> {
  const batchSize = opts.batchSize ?? 2000;
  if (!res.body) {
    const all = asArray<T>(parseTolerant(await res.text()));
    for (let i = 0; i < all.length; i += batchSize) await onBatch(all.slice(i, i + batchSize));
    return all.length;
  }
  const reader = res.body.getReader();
  const dec = new TextDecoder("utf-8");
  const split = new ArrayObjectSplitter();
  let batch: T[] = [];
  let total = 0;
  let bytes = 0;
  const flush = async () => {
    if (batch.length) { const b = batch; batch = []; await onBatch(b); }
  };
  for (;;) {
    if (opts.signal?.aborted) { void reader.cancel(); throw new DOMException("Annulé", "AbortError"); }
    const { done, value } = await reader.read();
    if (done) break;
    bytes += value.byteLength;
    opts.onBytes?.(bytes);
    for (const raw of split.feed(dec.decode(value, { stream: true }))) {
      try {
        batch.push(parseTolerant<T>(raw));
        total++;
      } catch { /* objet illisible : ignoré */ }
      if (batch.length >= batchSize) await flush();
    }
  }
  const rest = split.feed(dec.decode());
  for (const raw of rest) {
    try { batch.push(parseTolerant<T>(raw)); total++; } catch { /* ignoré */ }
  }
  await flush();
  return total;
}
