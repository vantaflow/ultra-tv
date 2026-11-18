import Dexie, { type Table } from "dexie";
import { wordKeys } from "@/lib/text";
import type { Recording, Reminder } from "@/lib/schedule";
import type {
  CategoryRow, ChannelOrderRow, ChannelRow, DetailRow, FavoriteRow, HiddenChannelRow, HistoryRow, ManualChannelRow, MovieRow,
  ProgramRow, ProfileCatOrderRow, ProfileHiddenChannelRow, ProfileHiddenRow, SeriesRow, SettingRow, Source,
} from "./types";
import { globalToProfiles, profileIdsFromPrefs } from "@/lib/channelHiding";

export class UltraTvDb extends Dexie {
  sources!: Table<Source, number>;
  categories!: Table<CategoryRow, number>;
  channels!: Table<ChannelRow, number>;
  movies!: Table<MovieRow, number>;
  series!: Table<SeriesRow, number>;
  programs!: Table<ProgramRow, number>;
  favorites!: Table<FavoriteRow, string>;
  history!: Table<HistoryRow, string>;
  details!: Table<DetailRow, string>;
  settings!: Table<SettingRow, string>;
  reminders!: Table<Reminder, string>;
  recordings!: Table<Recording, string>;
  channelOrder!: Table<ChannelOrderRow, string>;
  hiddenChannels!: Table<HiddenChannelRow, string>;
  profileHidden!: Table<ProfileHiddenRow, string>;
  manualChannels!: Table<ManualChannelRow, string>;
  profileHiddenChannels!: Table<ProfileHiddenChannelRow, string>;
  profileCatOrder!: Table<ProfileCatOrderRow, string>;

  constructor(name = "ultratv-desktop") {
    super(name);
    this.version(1).stores({
      sources: "++id",
      categories: "++id, [sourceId+kind], [sourceId+kind+extId]",
      channels: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+epg], [sourceId+streamId]",
      movies: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+streamId]",
      series: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+seriesId]",
      programs: "++id, [sourceId+epg+start], [sourceId+end]",
      favorites: "&key, [profile+sourceId+kind], addedAt",
      history: "&key, [profile+sourceId], updatedAt",
      details: "&key",
      settings: "&key",
    });
    // v2 : recherche texte par index (mot -> lignes) au lieu d'un filter() sur tout le catalogue, et tri « note » par catégorie.
    // `*words` est multiEntry ; les lignes existantes reçoivent leur champ ici (une seule fois, à l'ouverture).
    this.version(2).stores({
      channels: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+epg], [sourceId+streamId], *words",
      movies: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+catExt+rating], [sourceId+streamId], *words",
      series: "++id, [sourceId+ord], [sourceId+catExt+ord], [sourceId+added], [sourceId+catExt+added], [sourceId+rating], [sourceId+catExt+rating], [sourceId+seriesId], *words",
    }).upgrade(async (tx) => {
      for (const name of ["channels", "movies", "series"] as const) {
        await tx.table(name).toCollection().modify((r: { sourceId: number; norm?: string; sep?: number; words?: string[] }) => {
          r.words = r.sep ? [] : wordKeys(r.sourceId, r.norm ?? "");
        });
      }
    });
    // v3 : rappels et enregistrements programmés (tables neuves, aucune donnée à convertir).
    this.version(3).stores({
      reminders: "&id, start",
      recordings: "&id, recStart, state",
    });
    // v4 : ordre personnel des chaînes par profil / source / liste (table neuve, aucune donnée à convertir).
    this.version(4).stores({
      channelOrder: "&key, [profile+sourceId]",
    });
    // v5 : chaînes masquées par source (table neuve, aucune donnée à convertir).
    this.version(5).stores({
      hiddenChannels: "&key, sourceId",
    });
    // v6 : catégories masquées par profil / source / type (table neuve, aucune donnée à convertir : rien n'est masqué au départ).
    this.version(6).stores({
      profileHidden: "&key, [profile+sourceId], sourceId",
    });
    // v7 : chaînes ajoutées à la main par source (table neuve, aucune donnée à convertir : vide au départ).
    this.version(7).stores({
      manualChannels: "&key, sourceId",
    });
    // v8 : chaînes masquées et ordre des catégories PAR PROFIL (comme sur Android). `hiddenChannels` garde le sens « masquées par TOUS les profils ».
    // Migration : les chaînes masquées globales passent à tous les profils existants (rien ne réapparaît) et l'ordre actuel des catégories
    // (`ord`, celui de tous jusqu'ici) devient l'ordre de chaque profil.
    this.version(8).stores({
      profileHiddenChannels: "&key, [profile+sourceId], sourceId",
      profileCatOrder: "&key, [profile+sourceId], sourceId",
    }).upgrade(async (tx) => {
      let raw: string | null = null;
      try { raw = typeof localStorage !== "undefined" ? localStorage.getItem("ultratv.prefs.v1") : null; } catch { /* stockage indisponible */ }
      const profiles = profileIdsFromPrefs(raw);
      const hidden = await tx.table("hiddenChannels").toArray();
      if (hidden.length) await tx.table("profileHiddenChannels").bulkPut(globalToProfiles(hidden, profiles));
      for (const s of await tx.table("sources").toArray() as Source[]) {
        if (!s.cid || s.id == null) continue;
        const cats = await tx.table("categories").where("[sourceId+kind]").between([s.cid, ""], [s.cid, "\uffff"]).toArray();
        const rows: ProfileCatOrderRow[] = [];
        for (const kind of ["live", "movie", "series"] as const) {
          const ids = cats.filter((c: { kind: string }) => c.kind === kind).sort((a: { ord: number }, b: { ord: number }) => a.ord - b.ord).map((c: { extId: string }) => c.extId);
          if (ids.length) for (const profile of profiles) rows.push({ key: `${profile}:${s.id}:${kind}`, profile, sourceId: s.id, kind, ids });
        }
        if (rows.length) await tx.table("profileCatOrder").bulkPut(rows);
      }
    });
  }
}

export const db = new UltraTvDb();

export async function getSetting<T>(key: string, fallback: T): Promise<T> {
  const row = await db.settings.get(key);
  return (row?.value as T | undefined) ?? fallback;
}
export const setSetting = (key: string, value: unknown) => db.settings.put({ key, value });

const R = (cid: number): [[number, number], [number, number]] => [[cid, -1], [cid, Infinity]];

/** Supprime une génération de catalogue (les favoris et l'historique sont conservés). */
export async function clearCatalog(cid: number): Promise<void> {
  if (!cid) return;
  await db.categories.where("[sourceId+kind]").between([cid, ""], [cid, "\uffff"]).delete();
  await db.channels.where("[sourceId+ord]").between(...R(cid)).delete();
  await db.movies.where("[sourceId+ord]").between(...R(cid)).delete();
  await db.series.where("[sourceId+ord]").between(...R(cid)).delete();
  await db.programs.where("[sourceId+end]").between([cid, 0], [cid, Infinity]).delete();
}

/** Prochain numéro de génération de catalogue (compteur global). */
export async function nextCid(): Promise<number> {
  return db.transaction("rw", db.settings, async () => {
    const n = ((await db.settings.get("cid.counter"))?.value as number | undefined) ?? 0;
    await db.settings.put({ key: "cid.counter", value: n + 1 });
    return n + 1;
  });
}

/**
 * Supprime les générations de catalogue orphelines : une synchro interrompue (fermeture, plantage) laisse sa génération
 * (jusqu'à 180 000 films) dans IndexedDB pour toujours. Une génération est conservée si elle est la `cid` d'une source ou,
 * hors démarrage, si une synchro en cours l'écrit (`cid.pending`). Au démarrage aucune synchro ne tourne : tout pending est périmé.
 */
export async function purgeOrphanGenerations(opts: { startup?: boolean } = {}): Promise<number[]> {
  const keep = new Set<number>((await db.sources.toArray()).map((s) => s.cid).filter(Boolean));
  const pending = ((await db.settings.get("cid.pending"))?.value as number[] | undefined) ?? [];
  if (!opts.startup) for (const c of pending) keep.add(c);
  const counter = ((await db.settings.get("cid.counter"))?.value as number | undefined) ?? 0;
  const purged: number[] = [];
  for (let c = 1; c <= counter; c++) {
    if (keep.has(c)) continue;
    const any = (await db.categories.where("[sourceId+kind]").between([c, ""], [c, "\uffff"]).limit(1).count())
      || (await db.channels.where("[sourceId+ord]").between(...R(c)).limit(1).count())
      || (await db.movies.where("[sourceId+ord]").between(...R(c)).limit(1).count())
      || (await db.series.where("[sourceId+ord]").between(...R(c)).limit(1).count())
      || (await db.programs.where("[sourceId+end]").between([c, 0], [c, Infinity]).limit(1).count());
    if (!any) continue;
    await clearCatalog(c);
    purged.push(c);
  }
  if (opts.startup && pending.length) await db.settings.put({ key: "cid.pending", value: [] });
  return purged;
}

/** Marque (ou retire) une génération comme « en cours d'écriture » pour que la purge ne la prenne pas pour une orpheline. */
export async function setGenerationPending(cid: number, on: boolean): Promise<void> {
  await db.transaction("rw", db.settings, async () => {
    const cur = ((await db.settings.get("cid.pending"))?.value as number[] | undefined) ?? [];
    const next = on ? [...new Set([...cur, cid])] : cur.filter((c) => c !== cid);
    await db.settings.put({ key: "cid.pending", value: next });
  });
}
