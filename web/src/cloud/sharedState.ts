// État partagé entre appareils, par source du compte : favoris, positions de reprise, derniers vus.
// Profils rapprochés par NOM (le profil par défaut sans nom = « Principal », comme sur la TV). Le plus récent gagne.

import { db, getSetting, setSetting } from "@/db/db";
import { favKey, histKey } from "@/db/queries";
import type { FavoriteRow, HistoryRow, Kind, Source } from "@/db/types";
import { usePrefs } from "@/state/prefs";
import { parseEpisodes } from "@/screens/Detail";
import type { SeriesInfo } from "@/net/xtream";
import { syncState, type SharedFav, type SharedHist } from "./client";

export const DEFAULT_PROFILE_NAME = "Principal";
const KIND_TO_WIRE: Record<Kind, string> = { live: "LIVE", movie: "MOVIE", series: "SERIES" };
const WIRE_TO_KIND: Record<string, Kind> = { LIVE: "live", MOVIE: "movie", SERIES: "series" };
const fk = (e: { p: string; k: string; r: string }) => `${e.p}|${e.k}|${e.r}`;

/** Favoris connus mis à jour par l'état local : ajout = « on » maintenant, retrait = tombe datée de maintenant. */
export function localFavorites(known: Map<string, SharedFav>, current: Array<{ p: string; k: string; r: string }>, now: number): Map<string, SharedFav> {
  const out = new Map(known);
  const cur = new Set(current.map(fk));
  for (const c of current) { const o = out.get(fk(c)); if (!o || !o.on) out.set(fk(c), { ...c, on: true, at: now }); }
  for (const [key, o] of known) if (o.on && !cur.has(key)) out.set(key, { ...o, on: false, at: now });
  return out;
}

/** Entrées distantes plus récentes que l'état connu. */
export function remoteFavChanges(known: Map<string, SharedFav>, remote: SharedFav[]): SharedFav[] {
  return remote.filter((e) => { const o = known.get(fk(e)); return !o || e.at > o.at; });
}

export function profileNames(): { nameOf: (id: string) => string; idOf: (name: string) => string | undefined } {
  const ps = usePrefs.getState().profiles;
  const name = (p: { id: string; name: string }) => p.name.trim() || DEFAULT_PROFILE_NAME;
  return {
    nameOf: (id) => { const p = ps.find((x) => x.id === id); return p ? name(p) : DEFAULT_PROFILE_NAME; },
    idOf: (n) => ps.find((x) => name(x).toLowerCase() === n.trim().toLowerCase())?.id,
  };
}

/** Une source : envoie les changements locaux, applique l'état fusionné renvoyé. */
export async function syncSourceState(worker: string, token: string, s: Source): Promise<void> {
  if (!s.cloudId || !s.id) return;
  const sid = s.id;
  const { nameOf, idOf } = profileNames();
  const now = Date.now();
  const kFav = `cloud.st.fav.${s.cloudId}`; const kHist = `cloud.st.hist.${s.cloudId}`;
  const known = new Map((await getSetting<SharedFav[]>(kFav, [])).map((e) => [fk(e), e] as const));
  const favs = (await db.favorites.filter((f) => f.sourceId === sid).toArray()).map((f) => ({ p: nameOf(f.profile), k: KIND_TO_WIRE[f.kind], r: String(f.refId) }));
  const updated = localFavorites(known, favs, now);
  const since = await getSetting<number>(kHist, 0);
  const hist: SharedHist[] = (await db.history.filter((h) => h.sourceId === sid && h.updatedAt > since).toArray()).map((h) => h.kind === "series"
    ? { p: nameOf(h.profile), k: "EPISODE", r: String(h.epId ?? h.refId), t: h.title, img: h.image, pos: Math.round(h.pos * 1000), dur: Math.round(h.dur * 1000), at: h.updatedAt, par: String(h.seriesId ?? h.refId) }
    : { p: nameOf(h.profile), k: KIND_TO_WIRE[h.kind], r: String(h.refId), t: h.title, img: h.image, pos: Math.round(h.pos * 1000), dur: Math.round(h.dur * 1000), at: h.updatedAt, par: null });
  const res = await syncState(worker, token, s.cloudId, { fav: [...updated.values()], hist });
  if (!res) return;
  // Favoris distants plus récents, appliqués au profil du même nom (nom/image pris dans le catalogue local).
  for (const e of remoteFavChanges(updated, res.fav)) {
    const kind = WIRE_TO_KIND[e.k]; const profile = idOf(e.p); const refId = Number(e.r);
    if (!kind || !profile || !Number.isFinite(refId)) continue;
    const key = favKey(profile, sid, kind, refId);
    if (!e.on) { await db.favorites.delete(key); continue; }
    const meta = await catalogMeta(s, kind, refId);
    if (meta) await db.favorites.put({ key, profile, sourceId: sid, kind, refId, name: meta.name, image: meta.image, addedAt: e.at } satisfies FavoriteRow);
  }
  const merged = new Map(updated); for (const e of res.fav) merged.set(fk(e), e);
  await setSetting(kFav, [...merged.values()]);
  // Reprises distantes plus récentes que la ligne locale.
  let maxAt = Math.max(since, ...hist.map((h) => h.at));
  for (const e of res.hist) {
    const profile = idOf(e.p); if (!profile) continue;
    const row = await historyRow(s, profile, e);
    if (!row) continue;
    const cur = await db.history.get(row.key);
    if (cur && cur.updatedAt >= e.at) continue;
    await db.history.put(row);
    maxAt = Math.max(maxAt, e.at);
  }
  await setSetting(kHist, maxAt);
}

async function catalogMeta(s: Source, kind: Kind, refId: number): Promise<{ name: string; image: string | null } | null> {
  if (kind === "live") { const c = await db.channels.where("[sourceId+streamId]").equals([s.cid, refId]).first(); return c ? { name: c.display || c.name, image: c.logo } : null; }
  if (kind === "movie") { const m = await db.movies.where("[sourceId+streamId]").equals([s.cid, refId]).first(); return m ? { name: m.title || m.name, image: m.poster } : null; }
  const x = await db.series.where("[sourceId+seriesId]").equals([s.cid, refId]).first(); return x ? { name: x.title || x.name, image: x.poster } : null;
}

/** Ligne d'historique locale pour une reprise distante (le direct n'a pas de reprise ici). Épisode : détails de la série requis. */
async function historyRow(s: Source, profile: string, e: SharedHist): Promise<HistoryRow | null> {
  const sid = s.id!;
  const base = { profile, sourceId: sid, title: e.t, image: e.img, pos: e.pos / 1000, dur: e.dur / 1000, updatedAt: e.at };
  if (e.k === "MOVIE") {
    const refId = Number(e.r); if (!Number.isFinite(refId)) return null;
    const m = await db.movies.where("[sourceId+streamId]").equals([s.cid, refId]).first();
    return { ...base, key: histKey(profile, sid, "movie", refId), kind: "movie", refId, title: e.t || m?.title || "", image: e.img ?? m?.poster ?? null, ext: m?.ext };
  }
  if (e.k === "EPISODE" && e.par) {
    const seriesId = Number(e.par); const epId = Number(e.r);
    if (!Number.isFinite(seriesId) || !Number.isFinite(epId)) return null;
    const det = await db.details.get(`series:${sid}:${seriesId}`);
    const ep = det ? parseEpisodes(det.json as SeriesInfo).seasons.flatMap((x) => x.eps).find((x) => x.id === epId) : undefined;
    if (!ep) return null; // série jamais ouverte ici : réessayé à la prochaine synchro
    return { ...base, key: histKey(profile, sid, "series", seriesId) + `:${epId}`, kind: "series", refId: seriesId, epId, seriesId, season: ep.season, episode: ep.num, ext: ep.ext };
  }
  return null;
}
