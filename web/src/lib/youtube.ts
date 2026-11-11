// Bande-annonce YouTube : clé de la vidéo depuis une valeur du fournisseur ou de TMDB, et choix de la bonne vidéo TMDB.
// Module pur (aucun DOM). Même règle que le Kotlin (`YoutubeKey` côté Android) : garder les deux alignés.

const KEY = /^[A-Za-z0-9_-]{11}$/;
/** Valeurs « absentes » que les fournisseurs écrivent à la place d'un vide. */
const ABSENT = new Set(["", "null", "none", "n/a", "undefined", "0", "false"]);
const HOSTS = new Set(["youtube.com", "youtube-nocookie.com", "youtu.be"]);
/** Premier segment utile : `/embed/<clé>`, `/shorts/<clé>`, `/v/<clé>`, `/live/<clé>`. */
const PATH_KEY = /^\/(?:embed|shorts|v|live)\/([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])/;

/**
 * Clé YouTube (11 caractères) d'une valeur fournisseur ou TMDB : `watch?v=`, `youtu.be/`, `embed/`, `shorts/`,
 * ou la clé brute. Renvoie null pour une valeur vide ou « null », ou une adresse qui n'est pas YouTube.
 */
export function youtubeKey(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const s = value.trim();
  if (ABSENT.has(s.toLowerCase())) return null;
  if (KEY.test(s)) return s;
  const rest = s.replace(/^(?:https?:)?\/\//i, "");
  const m = /^([^/?#:]+)(?::\d+)?([^#]*)/.exec(rest);
  if (!m) return null;
  const host = m[1]!.toLowerCase().replace(/^(?:www|m|music)\./, "");
  if (!HOSTS.has(host)) return null;
  const tail = m[2] ?? "";
  const q = tail.indexOf("?");
  const path = q >= 0 ? tail.slice(0, q) : tail;
  const query = q >= 0 ? tail.slice(q + 1) : "";
  if (host === "youtu.be") return /^\/([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])/.exec(path)?.[1] ?? null;
  const fromPath = PATH_KEY.exec(path)?.[1];
  if (fromPath) return fromPath;
  const v = new URLSearchParams(query).get("v");
  return v && KEY.test(v) ? v : null;
}

export interface TmdbVideo { site?: string; type?: string; official?: boolean; key?: string }

/** Bande-annonce YouTube la plus pertinente d'une liste de vidéos TMDB : officielle, puis « Trailer », puis la première. */
export function pickTmdbTrailer(videos: readonly TmdbVideo[] | null | undefined): string | null {
  const yt = (videos ?? []).filter((v) => v.site === "YouTube" && youtubeKey(v.key) !== null);
  const best = yt.find((v) => v.type === "Trailer" && v.official === true) ?? yt.find((v) => v.type === "Trailer") ?? yt[0];
  return best ? youtubeKey(best.key) : null;
}

/** Accès GET au proxy TMDB (chemin + requête, relatif à `/api/tmdb/`) ; null si introuvable ou non configuré. */
export type TmdbGet = (pathAndQuery: string) => Promise<unknown | null>;

interface TmdbSearch { results?: Array<{ id?: number }> }
interface TmdbDetail { videos?: { results?: TmdbVideo[] } }

/**
 * Cherche la bande-annonce d'un titre sur TMDB (via le proxy de l'appli, jamais une recherche YouTube) : identifiant TMDB
 * connu, sinon recherche par titre (+ année). TMDB ne renvoie que les vidéos de la langue demandée : on retente en anglais.
 * Ne lève jamais : null si rien.
 */
export async function findTmdbTrailer(
  get: TmdbGet,
  q: { kind: "movie" | "tv"; tmdb?: number; title?: string; year?: number | null; langs?: readonly string[] },
): Promise<string | null> {
  try {
    let id = q.tmdb;
    const langs = q.langs?.length ? q.langs : ["en-US"];
    if (!id) {
      const title = q.title?.trim();
      if (!title) return null;
      const p = new URLSearchParams({ query: title.slice(0, 120), language: langs[0]! });
      if (q.year && q.year >= 1900 && q.year <= 2099) p.set(q.kind === "tv" ? "first_air_date_year" : "year", String(q.year));
      const s = (await get(`search/${q.kind}?${p}`)) as TmdbSearch | null;
      id = s?.results?.find((r) => Number.isInteger(r.id) && (r.id as number) > 0)?.id;
      if (!id) return null;
    }
    for (const lang of [...new Set([...langs, "en-US"])]) {
      const d = (await get(`${q.kind}/${id}?append_to_response=videos&language=${lang}`)) as TmdbDetail | null;
      const key = pickTmdbTrailer(d?.videos?.results);
      if (key) return key;
    }
    return null;
  } catch {
    return null;
  }
}
