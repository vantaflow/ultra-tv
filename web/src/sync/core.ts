// Synchronisation du catalogue : exécutée dans un Web Worker (sync/worker.ts) pour ne jamais
// bloquer l'interface, même avec 55 000 chaînes, 180 000 films et 48 000 séries.
// Lecture en flux (net/json.ts) + écriture par lots dans IndexedDB.

import { clearCatalog, db, nextCid } from "@/db/db";
import type { CategoryRow, ChannelRow, Kind, MovieRow, SeriesRow, Source, SyncProgress } from "@/db/types";
import { parseChannelName, parseCategoryName } from "@/lib/channelName";
import { cleanTitle, prettyCategoryName } from "@/lib/titleCleaner";
import { firstString, normText, rating10, toNum } from "@/lib/text";
import { parseM3u } from "@/lib/m3u";
import { streamObjects } from "@/net/json";
import { transportFetch, type Transport } from "@/net/transport";
import {
  handshake, xtreamArray, xtreamStream,
  type XtreamCategory, type XtreamCreds, type XtreamLive, type XtreamSeries, type XtreamVod,
} from "@/net/xtream";

export type ProgressFn = (p: SyncProgress) => void;

export const OTHER_LANG = "OTHER";

export interface DetectedLanguage { code: string; categories: number }

export function credsOf(s: Source): XtreamCreds {
  return { server: s.server, username: s.username, password: s.password, userAgent: s.userAgent || null, referer: s.referer || null };
}

function mapCategories(kind: Kind, sourceId: number, list: XtreamCategory[], langs: string[] | null): CategoryRow[] {
  return list.map((c, i) => {
    const parsed = parseCategoryName(c.category_name ?? "");
    const badge = parsed.badge;
    const code = badge ?? OTHER_LANG;
    const name = String(c.category_name ?? "");
    return {
      sourceId, kind, extId: String(c.category_id), name,
      label: prettyCategoryName(parsed.label || name), badge, count: 0,
      enabled: langs == null || langs.includes(code) ? 1 : 0,
      adult: /(^|\W)(xxx|adult|adulte|18\+|\+18|porn|erotic)(\W|$)/i.test(name) ? 1 : 0,
      ord: i,
    } satisfies CategoryRow;
  });
}

/** Étape rapide (3 petits appels) : catégories et langues détectées, pour l'écran de choix de langues. */
export async function detectLanguages(t: Transport, s: Source, signal?: AbortSignal): Promise<{ languages: DetectedLanguage[]; counts: Record<Kind, number> }> {
  const c = credsOf(s);
  const [live, vod, ser] = await Promise.all([
    xtreamArray<XtreamCategory>(t, c, "get_live_categories", {}, signal),
    xtreamArray<XtreamCategory>(t, c, "get_vod_categories", {}, signal),
    xtreamArray<XtreamCategory>(t, c, "get_series_categories", {}, signal),
  ]);
  const map = new Map<string, number>();
  for (const cat of [...live, ...vod, ...ser]) {
    const code = parseCategoryName(cat.category_name ?? "").badge ?? OTHER_LANG;
    map.set(code, (map.get(code) ?? 0) + 1);
  }
  const languages = [...map].map(([code, categories]) => ({ code, categories })).sort((a, b) => b.categories - a.categories);
  return { languages, counts: { live: live.length, movie: vod.length, series: ser.length } };
}

export async function testConnection(t: Transport, s: Source, signal?: AbortSignal) {
  if (s.type === "m3u") {
    const res = await transportFetch(t, s.m3uUrl, { signal, userAgent: s.userAgent, referer: s.referer });
    const head = (await res.text()).slice(0, 200);
    if (!head.includes("#EXTM3U") && !head.includes("#EXTINF")) throw new Error("not-m3u");
    return { ok: true as const, expDate: null, maxConnections: 1 };
  }
  const h = await handshake(t, credsOf(s), signal);
  const ui = h.user_info;
  if (!ui || ui.auth === 0 || (ui.status && ui.status !== "Active")) throw new Error(ui?.status === "Expired" ? "expired" : "auth");
  const exp = ui.exp_date ? toNum(ui.exp_date, 0) * 1000 : 0;
  return { ok: true as const, expDate: exp || null, maxConnections: toNum(ui.max_connections, 1) };
}

class Throttle {
  private last = 0;
  constructor(private fn: ProgressFn, private ms = 120) {}
  push(p: SyncProgress, force = false) {
    const now = Date.now();
    if (force || now - this.last >= this.ms) { this.last = now; this.fn(p); }
  }
}

const assertNotAborted = (signal?: AbortSignal) => {
  if (signal?.aborted) throw new DOMException("Annulé", "AbortError");
};

export interface SyncOptions {
  source: Source;
  transport: Transport;
  onProgress: ProgressFn;
  signal?: AbortSignal;
  /** Conserve les catégories activées/désactivées à la main (au lieu de les recalculer depuis les langues). */
  preserveFlags?: boolean;
}

/** Contexte d'une synchro : `cid` est la NOUVELLE génération de catalogue écrite par cette exécution. */
interface Ctx {
  source: Source;
  cid: number;
  firstGeneration: boolean;
  counts: { live: number; movie: number; series: number };
  flags: Map<string, 0 | 1> | null;
  report: ReportFn;
}

export async function runSync({ source, transport: t, onProgress, signal, preserveFlags }: SyncOptions): Promise<{ live: number; movie: number; series: number }> {
  const oldCid = source.cid;
  const cid = await nextCid();
  const counts = { live: 0, movie: 0, series: 0 };
  const th = new Throttle(onProgress);
  const report: ReportFn = (phase, ratio, force = false) => th.push({ phase, ratio, counts: { ...counts } }, force);

  let flags: Map<string, 0 | 1> | null = null;
  if (preserveFlags && oldCid) {
    flags = new Map();
    await db.categories.where("[sourceId+kind]").between([oldCid, ""], [oldCid, "\uffff"]).each((c) => { flags!.set(`${c.kind}:${c.extId}`, c.enabled); });
  }
  const ctx: Ctx = { source, cid, firstGeneration: oldCid === 0, counts, flags, report };

  report("categories", 0, true);
  try {
    if (source.type === "m3u") await syncM3u(ctx, t, signal);
    else await syncXtream(ctx, t, signal);
    assertNotAborted(signal);
  } catch (e) {
    // Échec ou annulation : la nouvelle génération est jetée, l'ancienne reste intacte.
    await clearCatalog(cid).catch(() => undefined);
    throw e;
  }
  await db.sources.update(source.id!, { cid, counts, lastSyncAt: Date.now(), state: "ready", error: undefined });
  if (oldCid) await clearCatalog(oldCid);
  report("done", 1, true);
  return counts;
}

type ReportFn = (phase: SyncProgress["phase"], ratio: number, force?: boolean) => void;

async function syncXtream({ source, cid: sourceId, counts, report, flags, firstGeneration }: Ctx, t: Transport, signal?: AbortSignal) {
  const c = credsOf(source);
  const langs = source.langs;

  const [liveCats, vodCats, serCats] = await Promise.all([
    xtreamArray<XtreamCategory>(t, c, "get_live_categories", {}, signal),
    xtreamArray<XtreamCategory>(t, c, "get_vod_categories", {}, signal),
    xtreamArray<XtreamCategory>(t, c, "get_series_categories", {}, signal),
  ]);
  const cats = [
    ...mapCategories("live", sourceId, liveCats, langs),
    ...mapCategories("movie", sourceId, vodCats, langs),
    ...mapCategories("series", sourceId, serCats, langs),
  ];
  if (flags) for (const cat of cats) { const f = flags.get(`${cat.kind}:${cat.extId}`); if (f !== undefined) cat.enabled = f; }
  const enabled: Record<Kind, Set<string>> = { live: new Set(), movie: new Set(), series: new Set() };
  const catCount: Record<string, number> = {};
  for (const cat of cats) if (cat.enabled) enabled[cat.kind].add(cat.extId);
  // Catégories écrites tout de suite (compteurs mis à jour en fin de synchro) : le direct est utilisable dès la fin de sa phase.
  const catIds = await db.categories.bulkAdd(cats, { allKeys: true });
  assertNotAborted(signal);

  // Catégories enfants d'un bloc de catalogue : un flux sans catégorie connue est rattaché à "" (affiché dans « Tout »).
  const bump = (kind: Kind, ext: string) => { const k = `${kind}:${ext}`; catCount[k] = (catCount[k] ?? 0) + 1; };

  // --- Direct ---
  report("live", 0, true);
  let liveOrd = 0;
  const approxLive = Math.max(1, liveCats.length * 60);
  await streamIntoDb<XtreamLive>(t, c, "get_live_streams", enabled.live, signal, async (items) => {
    const rows: ChannelRow[] = [];
    for (const s of items) {
      if (!enabled.live.has(String(s.category_id))) continue;
      const p = parseChannelName(String(s.name ?? ""));
      bump("live", String(s.category_id));
      rows.push({
        sourceId, catExt: String(s.category_id), ord: liveOrd++, streamId: toNum(s.stream_id), num: toNum(s.num, liveOrd),
        name: String(s.name ?? ""), display: p.displayName, norm: normText(p.displayName), country: p.country, q: p.quality, flags: p.flags,
        sep: p.isSeparator ? 1 : 0, logo: s.stream_icon || null, epg: s.epg_channel_id || null, archive: toNum(s.tv_archive) === 1 ? 1 : 0,
      });
    }
    if (rows.length) await db.channels.bulkAdd(rows);
    counts.live = liveOrd;
    report("live", Math.min(0.99, liveOrd / approxLive));
  });
  counts.live = (await db.channels.where("[sourceId+ord]").between([sourceId, -1], [sourceId, Infinity]).filter((r) => r.sep === 0).count());
  // Première synchro : on bascule dès maintenant pour permettre de regarder le direct pendant que films et séries arrivent.
  if (firstGeneration) await db.sources.update(source.id!, { cid: sourceId, counts: { ...counts } });
  report("live", 1, true);

  // --- Films ---
  report("movie", 0, true);
  let movieOrd = 0;
  await streamIntoDb<XtreamVod>(t, c, "get_vod_streams", enabled.movie, signal, async (items) => {
    const rows: MovieRow[] = [];
    for (const m of items) {
      if (!enabled.movie.has(String(m.category_id))) continue;
      const ct = cleanTitle(String(m.name ?? ""));
      bump("movie", String(m.category_id));
      rows.push({
        sourceId, catExt: String(m.category_id), ord: movieOrd++, streamId: toNum(m.stream_id), name: String(m.name ?? ""),
        title: ct.title, norm: normText(ct.title), year: ct.year, poster: m.stream_icon || null,
        rating: rating10(m.rating, m.rating_5based), ext: m.container_extension || "mp4", added: toNum(m.added) * 1000,
      });
    }
    if (rows.length) await db.movies.bulkAdd(rows);
    counts.movie = movieOrd;
    report("movie", Math.min(0.99, movieOrd / Math.max(1, vodCats.length * 200)));
  });
  counts.movie = movieOrd;
  report("movie", 1, true);

  // --- Séries ---
  report("series", 0, true);
  let serOrd = 0;
  await streamIntoDb<XtreamSeries>(t, c, "get_series", enabled.series, signal, async (items) => {
    const rows: SeriesRow[] = [];
    for (const s of items) {
      if (!enabled.series.has(String(s.category_id))) continue;
      const ct = cleanTitle(String(s.name ?? ""));
      bump("series", String(s.category_id));
      const rel = String(s.releaseDate ?? s.release_date ?? "");
      rows.push({
        sourceId, catExt: String(s.category_id), ord: serOrd++, seriesId: toNum(s.series_id), name: String(s.name ?? ""),
        title: ct.title, norm: normText(ct.title), year: ct.year ?? (/^\d{4}/.test(rel) ? parseInt(rel.slice(0, 4), 10) : null),
        poster: s.cover || null, backdrop: firstString(s.backdrop_path), plot: s.plot ? String(s.plot).slice(0, 600) : null,
        rating: rating10(s.rating, s.rating_5based), added: toNum(s.last_modified) * 1000,
      });
    }
    if (rows.length) await db.series.bulkAdd(rows);
    counts.series = serOrd;
    report("series", Math.min(0.99, serOrd / Math.max(1, serCats.length * 100)));
  });
  counts.series = serOrd;
  report("series", 1, true);

  await db.transaction("rw", db.categories, async () => {
    for (let i = 0; i < cats.length; i++) {
      const n = catCount[`${cats[i]!.kind}:${cats[i]!.extId}`] ?? 0;
      if (n) await db.categories.update(catIds[i]!, { count: n });
    }
  });
}

/**
 * Récupère `action` en un seul appel (flux) ; si le serveur ou le proxy échoue sur la réponse complète,
 * retombe sur un appel par catégorie activée (proxys à limite de taille, serveurs capricieux).
 */
async function streamIntoDb<T>(
  t: Transport, c: XtreamCreds, action: string, enabledCats: Set<string>, signal: AbortSignal | undefined,
  onBatch: (items: T[]) => Promise<void>,
) {
  if (enabledCats.size === 0) return;
  try {
    const res = await xtreamStream(t, c, action, {}, signal);
    await streamObjects<T>(res, onBatch, { signal, batchSize: 3000 });
    return;
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    // repli par catégorie
  }
  for (const id of enabledCats) {
    assertNotAborted(signal);
    try {
      const res = await xtreamStream(t, c, action, { category_id: id }, signal);
      await streamObjects<T>(res, onBatch, { signal, batchSize: 3000 });
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") throw e;
    }
  }
}

async function syncM3u({ source, cid: sourceId, counts, report }: Ctx, t: Transport, signal?: AbortSignal) {
  report("live", 0, true);
  const res = await transportFetch(t, source.m3uUrl, { signal, userAgent: source.userAgent, referer: source.referer });
  const entries = parseM3u(await res.text());
  const groups = new Map<string, number>();
  const rows: ChannelRow[] = [];
  entries.forEach((e, i) => {
    const group = (e.groupTitle ?? "").trim() || "—";
    if (!groups.has(group)) groups.set(group, groups.size);
    const p = parseChannelName(e.name);
    rows.push({
      sourceId, catExt: group, ord: i, streamId: i + 1, num: e.tvgChno ?? i + 1, name: e.name, display: p.displayName,
      norm: normText(p.displayName), country: p.country, q: p.quality, flags: p.flags, sep: p.isSeparator ? 1 : 0,
      logo: e.tvgLogo, epg: e.tvgId, archive: e.catchUp ? 1 : 0, url: e.url,
    });
  });
  for (let i = 0; i < rows.length; i += 5000) {
    assertNotAborted(signal);
    await db.channels.bulkAdd(rows.slice(i, i + 5000));
    report("live", Math.min(0.99, i / Math.max(1, rows.length)));
  }
  counts.live = rows.filter((r) => !r.sep).length;
  const cats: CategoryRow[] = [...groups].map(([name, ord]) => {
    const parsed = parseCategoryName(name);
    return {
      sourceId, kind: "live" as const, extId: name, name, label: prettyCategoryName(parsed.label), badge: parsed.badge,
      count: rows.filter((r) => r.catExt === name).length, enabled: 1 as const, adult: 0 as const, ord,
    };
  });
  await db.categories.bulkAdd(cats);
  report("live", 1, true);
}
