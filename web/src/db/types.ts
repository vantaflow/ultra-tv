// Modèle local (IndexedDB). Lignes volontairement plates et maigres : un catalogue de 180 000 films
// doit tenir sur disque, pas en mémoire. Les URL de flux ne sont JAMAIS stockées : elles sont
// reconstruites à la lecture depuis la source (identifiants chiffrés) et l'identifiant du flux.

export type SourceType = "xtream" | "m3u";
export type Kind = "live" | "movie" | "series";

export interface Source {
  id?: number;
  name: string;
  type: SourceType;
  /** Xtream : URL du serveur. */
  server: string;
  username: string;
  password: string;
  /** M3U : lien de la playlist (contient souvent des identifiants). */
  m3uUrl: string;
  epgUrl: string;
  userAgent: string;
  referer: string;
  /**
   * Génération de catalogue courante. Les lignes de catalogue (catégories, chaînes, films, séries, guide)
   * portent ce numéro dans leur champ `sourceId` : une nouvelle synchro écrit une nouvelle génération,
   * puis bascule `cid` d'un coup — l'interface ne voit jamais un catalogue à moitié vidé. 0 = aucun.
   */
  cid: number;
  /** Langues (codes pays) à synchroniser ; null = toutes. */
  langs: string[] | null;
  createdAt: number;
  lastSyncAt: number;
  counts: { live: number; movie: number; series: number };
  expDate: number | null;
  maxConnections: number;
  /** Compte cloud : identifiant stable du fournisseur côté Worker (clé de fusion). */
  cloudId?: string;
  /** "cloud" : créée par le compte (identifiants fournis par le Worker, retirée avec lui) ; "local" : créée ici puis partagée. */
  cloudOrigin?: "cloud" | "local";
  /** Partage connu : "all" ou nombre d'appareils. */
  cloudShared?: "all" | number;
  /** Nom de l'appareil d'origine (tableau de bord ou autre appareil). */
  cloudOriginName?: string;
  /** "ready" une fois la première synchro terminée. */
  state: "new" | "syncing" | "ready" | "error";
  error?: string;
}

export interface CategoryRow {
  id?: number;
  /** Génération de catalogue (voir Source.cid). */
  sourceId: number;
  kind: Kind;
  extId: string;
  name: string;
  label: string;
  badge: string | null;
  count: number;
  enabled: 0 | 1;
  adult: 0 | 1;
  ord: number;
}

export interface ChannelRow {
  id?: number;
  sourceId: number;
  catExt: string;
  ord: number;
  streamId: number;
  num: number;
  name: string;
  display: string;
  norm: string;
  /** Index de recherche par mot (voir wordKeys) ; absent sur les lignes d'avant la version 2 du schéma. */
  words?: string[];
  country: string | null;
  q: number;
  flags: number;
  sep: 0 | 1;
  logo: string | null;
  epg: string | null;
  archive: 0 | 1;
  /** M3U : URL de flux fournie par la playlist (jamais journalisée). */
  url?: string;
}

export interface MovieRow {
  id?: number;
  sourceId: number;
  catExt: string;
  ord: number;
  streamId: number;
  name: string;
  title: string;
  norm: string;
  words?: string[];
  year: number | null;
  poster: string | null;
  rating: number;
  ext: string;
  added: number;
}

export interface SeriesRow {
  id?: number;
  sourceId: number;
  catExt: string;
  ord: number;
  seriesId: number;
  name: string;
  title: string;
  norm: string;
  words?: string[];
  year: number | null;
  poster: string | null;
  backdrop: string | null;
  plot: string | null;
  rating: number;
  added: number;
}

export interface ProgramRow {
  id?: number;
  sourceId: number;
  epg: string;
  start: number;
  end: number;
  title: string;
  desc: string;
}

export interface FavoriteRow {
  key: string;
  profile: string;
  /** Identifiant réel de la source (et non la génération de catalogue). */
  sourceId: number;
  kind: Kind;
  refId: number;
  name: string;
  image: string | null;
  addedAt: number;
}

export interface HistoryRow {
  key: string;
  profile: string;
  sourceId: number;
  kind: Kind;
  refId: number;
  title: string;
  image: string | null;
  pos: number;
  dur: number;
  updatedAt: number;
  /** Épisode : identifiant, extension de conteneur, saison/épisode et série parente. */
  ext?: string;
  /** Identifiant de l'épisode (séries) pour la reprise exacte. */
  epId?: number;
  seriesId?: number;
  season?: number;
  episode?: number;
}

export interface DetailRow {
  key: string;
  json: unknown;
  fetchedAt: number;
}

export interface SettingRow { key: string; value: unknown }

export interface SyncProgress {
  phase: "categories" | "live" | "movie" | "series" | "epg" | "done";
  /** 0..1 pour la phase courante. */
  ratio: number;
  counts: { live: number; movie: number; series: number };
  message?: string;
}
