// Partage réseau (NAS) déjà monté par le système comme source VOD du bureau : chemin local, UNC (\\hôte\partage) sous Windows,
// /Volumes/… sous macOS, point de montage sous Linux. Le processus principal d'Electron parcourt le dossier (borné) et sert les
// fichiers au lecteur par le proxy local ; ici : indexation (films, séries, épisodes reconnus par nom de fichier), liens internes
// et adresse de lecture. Disponible dans l'application de bureau seulement (un navigateur n'a pas accès aux dossiers).
//
// Aucun identifiant : le système monte le partage avec les siens (Windows, Finder…). Rien n'est stocké en dehors du chemin du dossier.

import { bridge } from "./transport";
import { extensionOf, nasSeriesKey, parseNasName } from "@/lib/nasNames";

export const isNasType = (t: string | undefined): t is "nas" => t === "nas";

/** Vrai dans l'application de bureau : seule à pouvoir parcourir un dossier et le servir au lecteur. */
export const nasAvailable = (): boolean => !!bridge()?.nasScan;

/** Fichier vidéo trouvé : `path` relatif à la racine, séparé par « / ». */
export interface NasFile { path: string; size: number; mtime: number }

// ---------------------------------------------------------------- liens internes

const toB64 = (s: string): string => {
  let bin = "";
  for (const b of new TextEncoder().encode(s)) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};
const fromB64 = (b: string): string | null => {
  if (!/^[A-Za-z0-9_-]+$/.test(b)) return null;
  try {
    const bin = atob(b.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (b.length % 4)) % 4));
    return new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(bin, (c) => c.charCodeAt(0)));
  } catch { return null; }
};

/** Lien interne stocké à la place d'une adresse : `nas:<chemin relatif en base64 URL>` (ni hôte ni dossier de départ). */
export const nasLink = (rel: string): string => `nas:${toB64(rel)}`;
export const isNasLink = (u: string | null | undefined): u is string => !!u && u.startsWith("nas:");

/** Chemin relatif d'un lien ; null si illisible ou s'il remonte l'arborescence. */
export function parseNasLink(u: string): string | null {
  if (!isNasLink(u)) return null;
  const rel = fromB64(u.slice(4));
  if (!rel) return null;
  const segs = rel.split("/").filter(Boolean);
  if (segs.length === 0 || segs.some((s) => s === ".." || s === ".")) return null;
  return segs.join("/");
}

/** Adresse de lecture : proxy local (`<base>nas/<racine>/<chemin>.<ext>`), l'extension aide le lecteur. */
export function nasUrl(proxyBase: string, rootId: string, rel: string): string {
  const ext = extensionOf(rel);
  return `${proxyBase}nas/${rootId}/${toB64(rel)}${/^[a-z0-9]{1,5}$/.test(ext) ? `.${ext}` : ""}`;
}

/** Nom affichable d'une racine : dernier dossier de son chemin. */
export function nasRootName(root: string): string {
  const parts = root.split(/[\\/]+/).filter(Boolean);
  return parts[parts.length - 1] ?? root;
}

// ---------------------------------------------------------------- identifiants locaux

/**
 * Identifiant numérique local (les lignes du catalogue sont indexées par nombre) : 48 bits issus de deux empreintes 32 bits
 * indépendantes du texte (les collisions deviennent négligeables jusqu'à des centaines de milliers de fichiers).
 */
export function numericHash(text: string): number {
  let a = 0x811c9dc5, b = 0x9e3779b9;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    a = Math.imul(a ^ c, 0x01000193) >>> 0;
    b = Math.imul(b + c, 0x85ebca6b) >>> 0;
    b ^= b >>> 13;
  }
  return (a & 0xffff) * 0x100000000 + (b >>> 0);
}

// ---------------------------------------------------------------- catalogue

export const NAS_ROOT_CATEGORY = "_root";

export interface NasMovie { streamId: number; name: string; year: number | null; ext: string; added: number; catExt: string; url: string }
export interface NasSeries { seriesId: number; portalId: string; name: string; year: number | null; added: number; catExt: string }
export interface NasEpisode { id: number; season: number; episode: number; title: string; ext: string; url: string }
export interface NasCatalog {
  movieCats: Array<{ extId: string; name: string }>;
  seriesCats: Array<{ extId: string; name: string }>;
  movies: NasMovie[];
  series: NasSeries[];
  /** Épisodes par série (clé = `portalId`), triés par saison puis numéro. */
  episodes: Record<string, NasEpisode[]>;
}

/**
 * Indexation des fichiers trouvés : films et épisodes reconnus d'après le nom de fichier et les dossiers (voir lib/nasNames.ts),
 * catégories = dossiers de premier niveau (les fichiers posés à la racine vont dans la catégorie du nom de la racine).
 * Fonction pure.
 */
export function buildNasCatalog(files: NasFile[], rootName: string): NasCatalog {
  const sorted = [...files].sort((a, b) => a.path.toLowerCase().localeCompare(b.path.toLowerCase()));
  const catOf = (segs: string[]) => (segs.length > 1 ? segs[0]! : NAS_ROOT_CATEGORY);
  const catName = (id: string) => (id === NAS_ROOT_CATEGORY ? rootName : id);
  const movieCats = new Map<string, string>();
  const seriesCats = new Map<string, string>();
  const movies: NasMovie[] = [];
  const groups = new Map<string, Array<{ p: ReturnType<typeof parseNasName>; f: NasFile; segs: string[] }>>();

  for (const f of sorted) {
    const segs = f.path.split("/").filter(Boolean);
    if (segs.length === 0) continue;
    const p = parseNasName(segs[segs.length - 1]!, segs.slice(0, -1));
    if (p.kind === "movie") {
      const cat = catOf(segs);
      if (!movieCats.has(cat)) movieCats.set(cat, catName(cat));
      movies.push({ streamId: numericHash(segs.join("/")), name: p.title, year: p.year, ext: extensionOf(f.path), added: f.mtime, catExt: cat, url: nasLink(segs.join("/")) });
    } else {
      const title = p.seriesTitle || rootName;
      const key = nasSeriesKey(title) || "x";
      let g = groups.get(key);
      if (!g) groups.set(key, (g = []));
      g.push({ p: { ...p, seriesTitle: title }, f, segs });
    }
  }

  const series: NasSeries[] = [];
  const episodes: Record<string, NasEpisode[]> = {};
  for (const [key, items] of groups) {
    const portalId = `s_${key}`;
    const cat = catOf(items[0]!.segs);
    if (!seriesCats.has(cat)) seriesCats.set(cat, catName(cat));
    // Nom affiché : l'écriture la plus fréquente parmi les fichiers du groupe.
    const counts = new Map<string, number>();
    for (const it of items) counts.set(it.p.seriesTitle!, (counts.get(it.p.seriesTitle!) ?? 0) + 1);
    const name = [...counts.entries()].sort((a, b) => b[1] - a[1])[0]![0];
    series.push({ seriesId: numericHash(portalId), portalId, name, year: items.find((i) => i.p.year != null)?.p.year ?? null, added: Math.max(...items.map((i) => i.f.mtime)), catExt: cat });
    const seen = new Set<number>();
    let next = 1;
    const eps: NasEpisode[] = [];
    const ordered = [...items].sort((a, b) => (a.p.season ?? 1) - (b.p.season ?? 1) || (a.p.episode ?? 0) - (b.p.episode ?? 0) || a.f.path.localeCompare(b.f.path));
    for (const it of ordered) {
      const rel = it.segs.join("/");
      const id = numericHash(rel);
      if (seen.has(id)) continue;
      seen.add(id);
      const season = it.p.season ?? 1;
      const ep = it.p.episode && it.p.episode > 0 ? it.p.episode : next;
      next = ep + 1;
      eps.push({ id, season, episode: ep, title: it.p.title || `S${String(season).padStart(2, "0")}E${String(ep).padStart(2, "0")}`, ext: extensionOf(rel), url: nasLink(rel) });
    }
    episodes[portalId] = eps;
  }

  const cats = (m: Map<string, string>) => [...m.entries()].map(([extId, name]) => ({ extId, name })).sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  return { movieCats: cats(movieCats), seriesCats: cats(seriesCats), movies, series, episodes };
}

/** Épisodes présentés comme `get_series_info` (par saison) + lien interne de chaque épisode (`media`, voir player/resolve.ts). */
export function nasSeriesInfo(eps: NasEpisode[]) {
  const episodes: Record<string, Array<{ id: number; title: string; episode_num: number; container_extension: string; info: Record<string, never> }>> = {};
  const media: Array<{ id: number; url: string }> = [];
  for (const e of eps) {
    (episodes[String(e.season)] ??= []).push({ id: e.id, title: e.title, episode_num: e.episode, container_extension: e.ext, info: {} });
    media.push({ id: e.id, url: e.url });
  }
  return { episodes, media };
}

// ---------------------------------------------------------------- pont Electron

/** Parcours du dossier par le processus principal (borné) ; déclare aussi la racine au proxy local pour la lecture. */
export async function scanNas(root: string, probe = false): Promise<{ id: string; files: NasFile[]; truncated: boolean }> {
  const b = bridge();
  if (!b?.nasScan) throw new Error("nas-unavailable");
  return b.nasScan(root, probe);
}

/** Identifiant de racine pour la lecture (idempotent) ; null si le dossier est refusé. */
export async function registerNas(root: string): Promise<string | null> {
  const b = bridge();
  if (!b?.nasRegister) return null;
  return b.nasRegister(root);
}

/** Adresse de lecture d'un fichier du partage `root` ; null hors application de bureau ou si la racine est refusée. */
export async function nasPlayUrl(root: string, rel: string): Promise<string | null> {
  const b = bridge();
  if (!b?.proxyBase) return null;
  const id = await registerNas(root);
  return id ? nasUrl(b.proxyBase, id, rel) : null;
}
