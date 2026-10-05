// Affiche de repli : quand le fournisseur n'en donne pas (ou qu'elle ne charge pas), on la cherche sur TMDB
// via le Worker (appareil appairé seulement). Résultats mis en cache (y compris « rien trouvé »), 3 requêtes à la fois.

/** Titre nettoyé pour la recherche : préfixe de langue/qualité, année et étiquettes retirés. Exporté pour les tests. */
export function cleanTitle(raw: string): { query: string; year: number | null } {
  let t = raw.normalize("NFC");
  const y = t.match(/[([]\s*((?:19|20)\d{2})\s*[)\]]/) ?? t.match(/\s-\s*((?:19|20)\d{2})\s*$/);
  const year = y ? Number(y[1]) : null;
  t = t
    .replace(/^\s*(?:\[[^\]]{1,12}\]|\|[^|]{1,12}\||[A-Z]{2,4}(?:[-/][A-Z]{2,4})?\s*[-:|])\s*/u, "")
    .replace(/[([]\s*(?:19|20)\d{2}\s*[)\]]/g, "")
    .replace(/\b(?:4K|UHD|FHD|HD|SD|HEVC|H\.?26[45]|MULTI|VOSTFR|VF|VFF|VOST|TRUEFRENCH|FRENCH)\b/gi, "")
    .replace(/[_.]+/g, " ")
    .replace(/\s{2,}/g, " ")
    .trim()
    .replace(/[-:|\s]+$/g, "");
  return { query: t, year };
}

/** Une même œuvre proposée plusieurs fois (qualités, doublons du fournisseur) : clé de regroupement. */
export function dedupeKey(title: string, year?: number | null): string {
  const { query, year: y } = cleanTitle(title);
  return `${query.toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").replace(/[^\p{L}\p{N}]+/gu, " ").trim()}|${year ?? y ?? ""}`;
}

/** Garde une entrée par œuvre, de préférence celle qui a une affiche. */
export function dedupeByTitle<T extends { title: string; year?: number | null; poster?: string | null }>(items: T[]): T[] {
  const best = new Map<string, T>();
  for (const it of items) {
    const k = dedupeKey(it.title, it.year);
    const cur = best.get(k);
    if (!cur || (!cur.poster && it.poster)) best.set(k, it);
  }
  const keep = new Set(best.values());
  return items.filter((it) => keep.has(it));
}

type Finder = (kind: "movie" | "tv", query: string, year: number | null) => Promise<string | null>;
const IMG = "https://image.tmdb.org/t/p/w342";
const KEY = "utv.posterCache.v2"; // v2 : nouvelles variantes de recherche, les « rien trouvé » de v1 sont réessayés
const MAX = 3000;
const cache = new Map<string, string | null>();
const inflight = new Map<string, Promise<string | null>>();
let finder: Finder | null = null;
let active = 0;
const waiters: (() => void)[] = [];

try { const raw = localStorage.getItem(KEY); if (raw) for (const [k, v] of JSON.parse(raw) as [string, string | null][]) cache.set(k, v); } catch { /* stockage indisponible */ }
let saveTimer: ReturnType<typeof setTimeout> | null = null;
function persist() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try { localStorage.setItem(KEY, JSON.stringify([...cache].slice(-MAX))); } catch { /* plein ou indisponible */ }
  }, 2000);
}

/** Branché par le service cloud quand l'appareil est appairé (null = pas de repli). */
export function setPosterFinder(f: Finder | null) { finder = f; }

async function slot<T>(fn: () => Promise<T>): Promise<T> {
  if (active >= 3) await new Promise<void>((r) => waiters.push(r));
  active++;
  try { return await fn(); } finally { active--; waiters.shift()?.(); }
}

/** URL d'affiche TMDB pour un titre, ou null. Jamais d'exception. */
export async function fallbackPoster(title: string, kind: "movie" | "tv", year?: number | null): Promise<string | null> {
  const { query, year: y } = cleanTitle(title);
  if (!query || !finder) return null;
  const yr = year ?? y;
  const key = `${kind}|${query.toLowerCase()}|${yr ?? ""}`;
  if (cache.has(key)) return cache.get(key) ?? null;
  const running = inflight.get(key);
  if (running) return running;
  const f = finder;
  const p = slot(async () => {
    try {
      let path = await f(kind, query, yr);
      if (!path && yr) path = await f(kind, query, null);
      // Titre long avec sous-titre (« Lupin the IIIrd the Movie : La lignée immortelle ») : essai sur le titre principal.
      const short = query.split(/\s+[:–—-]\s+|\s*:\s+/)[0]!.trim();
      if (!path && short && short !== query && short.length >= 3) path = await f(kind, short, yr) ?? (yr ? await f(kind, short, null) : null);
      const url = path ? IMG + path : null;
      cache.set(key, url); persist();
      return url;
    } catch { return null; } finally { inflight.delete(key); }
  });
  inflight.set(key, p);
  return p;
}
