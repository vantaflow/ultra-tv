// Chaînes ajoutées à la main à une source existante (Xtream, M3U, Stalker).
// Une synchro écrit une NOUVELLE génération de catalogue puis jette l'ancienne : les chaînes manuelles vivent donc dans
// leur propre table (manualChannels, jamais touchée par la synchro) et sont réinjectées dans la génération courante après
// chaque synchro (sync/core.ts), puis à chaque ajout / modification / suppression. Aucune adresse n'est journalisée :
// elles peuvent contenir des identifiants.

import { parseChannelName } from "@/lib/channelName";
import { normText, wordKeys } from "@/lib/text";
import { db } from "./db";
import type { CategoryRow, ChannelRow, ManualChannelRow } from "./types";

/** Préfixe des identifiants fournisseur des chaînes manuelles (identique sur Android). */
export const MANUAL_PREFIX = "custom:";
/** Catégorie dédiée « Mes chaînes », créée à la demande. */
export const MANUAL_CAT = "custom:cat";
const MANUAL_BASE = 2 ** 52;
const SCHEMES = new Set(["http", "https", "rtmp", "rtsp", "udp", "rtp", "mms", "srt"]);

export const newManualRef = (): string => `${MANUAL_PREFIX}${crypto.randomUUID()}`;
export const isManualRef = (ref: string): boolean => ref.startsWith(MANUAL_PREFIX);
export const manualKey = (sourceId: number, ref: string) => `${sourceId}:${ref}`;

/**
 * Identifiant numérique de flux d'une chaîne manuelle (le catalogue local n'a que des entiers) : dérivé de l'UUID, donc
 * STABLE d'une synchro à l'autre (favoris, historique, ordre personnel y restent attachés), et toujours ≥ 2^52, bien
 * au-dessus des identifiants des fournisseurs.
 */
export function manualStreamId(ref: string): number {
  const hex = ref.replace(MANUAL_PREFIX, "").replace(/[^0-9a-f]/gi, "");
  if (hex.length >= 12) return MANUAL_BASE + parseInt(hex.slice(0, 12), 16);
  let h = 0xcbf29ce4; // repli (identifiant qui n'est pas un UUID) : FNV-1a 32 bits
  for (const c of ref) { h ^= c.charCodeAt(0); h = Math.imul(h, 0x01000193) >>> 0; }
  return MANUAL_BASE + h;
}
export const isManualStreamId = (id: number): boolean => id >= MANUAL_BASE;

/** Adresse de flux acceptée : un protocole de lecture courant et un hôte non vide. */
export function isValidStreamUrl(raw: string): boolean {
  const s = raw.trim();
  const i = s.indexOf("://");
  if (i < 0 || !SCHEMES.has(s.slice(0, i).toLowerCase())) return false;
  const host = s.slice(i + 3).split("/")[0]!.split("@").pop()!;
  return host.length > 0;
}
/** Logo facultatif : vide ou adresse http(s). */
export const isValidLogoUrl = (raw: string): boolean => { const s = raw.trim(); return s === "" || /^https?:\/\/\S+$/i.test(s); };

export interface ManualInput { name: string; url: string; logo: string; epg: string; catExt: string; catName: string | null }

export interface ManualPlan {
  /** « Mes chaînes » à créer (absente de la génération) ou à garder à jour. */
  customCat: CategoryRow | null;
  channels: ChannelRow[];
  /** Nombre de chaînes ajoutées par catégorie (extId). */
  perCat: Map<string, number>;
}

/**
 * Calcule les lignes à écrire dans la génération [cid]. Pur (testé sans base).
 *  - [cats] : catégories du Direct de la génération ; [maxNum] : plus grand `num` du fournisseur par catégorie ;
 *    [startOrd] : première position libre (`ord`) ; [startCatOrd] : première position libre des catégories.
 *  - Catégorie choisie désactivée : chaîne sautée (elle revient avec la réactivation + synchro). Catégorie disparue du
 *    serveur : la chaîne bascule dans « Mes chaînes ». Numéros à la suite de ceux du fournisseur, ordre de création.
 */
export function planManual(a: {
  cid: number; rows: ManualChannelRow[]; cats: CategoryRow[]; maxNum: Map<string, number>; startOrd: number; startCatOrd: number; defaultCatName: string;
}): ManualPlan {
  const byExt = new Map(a.cats.map((c) => [c.extId, c]));
  const num = new Map(a.maxNum);
  const perCat = new Map<string, number>();
  const channels: ChannelRow[] = [];
  let ord = a.startOrd;
  let customName: string | null = null;
  for (const m of [...a.rows].sort((x, y) => x.createdAt - y.createdAt || x.ref.localeCompare(y.ref))) {
    let ext = m.catExt;
    if (ext !== MANUAL_CAT && !byExt.has(ext)) ext = MANUAL_CAT;
    const known = byExt.get(ext);
    if (known && !known.enabled) continue;
    if (ext === MANUAL_CAT && customName == null) customName = m.catName?.trim() || a.defaultCatName;
    const n = (num.get(ext) ?? 0) + 1; num.set(ext, n);
    perCat.set(ext, (perCat.get(ext) ?? 0) + 1);
    const p = parseChannelName(m.name);
    channels.push({
      sourceId: a.cid, catExt: ext, ord: ord++, streamId: manualStreamId(m.ref), num: n, name: m.name,
      // Un nom comme « 2 » ou « ### » reste affiché : jamais pris pour un séparateur (c'est le choix de l'utilisateur).
      display: p.isSeparator ? m.name : p.displayName, norm: normText(m.name), words: wordKeys(a.cid, normText(m.name)),
      country: p.country, q: p.quality, flags: p.flags, sep: 0, logo: m.logo || null, epg: m.epg || null, archive: 0, url: m.url,
    });
  }
  let customCat: CategoryRow | null = null;
  const used = perCat.get(MANUAL_CAT) ?? 0;
  if (used > 0 && customName != null) {
    const own = byExt.get(MANUAL_CAT);
    customCat = own
      ? { ...own, count: own.count + used }
      : { sourceId: a.cid, kind: "live", extId: MANUAL_CAT, name: customName, label: customName, badge: null, count: used, enabled: 1, adult: 0, ord: a.startCatOrd };
  }
  return { customCat, channels, perCat };
}

export const listManual = (sourceId: number): Promise<ManualChannelRow[]> => db.manualChannels.where("sourceId").equals(sourceId).toArray();

/**
 * (Ré)injecte les chaînes manuelles de la source dans la génération [cid] : retire d'abord leurs lignes éventuelles (plus
 * celles de [drop], chaînes supprimées), puis écrit les lignes à jour et corrige l'effectif des catégories.
 * Retourne la variation du nombre de chaînes du Direct (à reporter sur les totaux de la source). Idempotent.
 */
export async function injectManual(cid: number, sourceId: number, drop: number[] = [], defaultCatName = "Mes chaînes"): Promise<number> {
  if (!cid) return 0;
  return db.transaction("rw", db.channels, db.categories, db.manualChannels, async () => {
    const rows = await listManual(sourceId);
    const mine = new Set([...rows.map((r) => manualStreamId(r.ref)), ...drop]);
    // 1. Retrait des lignes déjà présentes (même identifiant de flux), effectifs corrigés.
    const removedPerCat = new Map<string, number>();
    let removed = 0;
    for (const id of mine) {
      const found = await db.channels.where("[sourceId+streamId]").equals([cid, id]).toArray();
      if (!found.length) continue;
      await db.channels.bulkDelete(found.map((f) => f.id!));
      for (const f of found) { removedPerCat.set(f.catExt, (removedPerCat.get(f.catExt) ?? 0) + 1); removed++; }
    }
    for (const [ext, n] of removedPerCat) {
      const c = await db.categories.where("[sourceId+kind+extId]").equals([cid, "live", ext]).first();
      if (!c) continue;
      const left = Math.max(0, c.count - n);
      // « Mes chaînes » vide : la catégorie disparaît (elle est recréée à la prochaine chaîne).
      if (ext === MANUAL_CAT && left === 0) await db.categories.delete(c.id!); else await db.categories.update(c.id!, { count: left });
    }
    if (!rows.length) return -removed;
    // 2. Écriture.
    const cats = await db.categories.where("[sourceId+kind]").equals([cid, "live"]).toArray();
    const wanted = new Set(rows.map((r) => r.catExt));
    const maxNum = new Map<string, number>();
    for (const ext of [...wanted, MANUAL_CAT]) {
      let m = 0;
      await db.channels.where("[sourceId+catExt+ord]").between([cid, ext, -1], [cid, ext, Infinity]).each((c) => { if (c.num > m) m = c.num; });
      if (m) maxNum.set(ext, m);
    }
    const last = await db.channels.where("[sourceId+ord]").between([cid, -1], [cid, Infinity]).reverse().first();
    const allCats = await db.categories.where("[sourceId+kind]").equals([cid, "live"]).toArray();
    const plan = planManual({
      cid, rows, cats, maxNum, startOrd: (last?.ord ?? -1) + 1, startCatOrd: allCats.reduce((m, c) => Math.max(m, c.ord), -1) + 1, defaultCatName,
    });
    if (plan.channels.length) await db.channels.bulkAdd(plan.channels);
    if (plan.customCat) { if (plan.customCat.id != null) await db.categories.put(plan.customCat); else await db.categories.add(plan.customCat); }
    for (const [ext, n] of plan.perCat) {
      if (ext === MANUAL_CAT) continue;
      const c = cats.find((x) => x.extId === ext);
      if (c?.id != null) await db.categories.update(c.id, { count: c.count + n });
    }
    return plan.channels.length - removed;
  });
}

async function refresh(sourceId: number, drop: number[] = []): Promise<void> {
  const s = await db.sources.get(sourceId);
  if (!s?.cid) return;
  const delta = await injectManual(s.cid, sourceId, drop);
  if (delta) await db.sources.update(sourceId, { counts: { ...s.counts, live: Math.max(0, s.counts.live + delta) } });
}

const clean = (v: ManualInput) => ({ name: v.name.trim(), url: v.url.trim(), logo: v.logo.trim() || null, epg: v.epg.trim() || null, catExt: v.catExt, catName: v.catExt === MANUAL_CAT ? (v.catName?.trim() || null) : null });

/** Ajoute une chaîne manuelle à la source et l'injecte tout de suite dans son catalogue. Retourne son identifiant « custom:… ». */
export async function addManual(sourceId: number, v: ManualInput): Promise<string> {
  const ref = newManualRef();
  await db.manualChannels.put({ key: manualKey(sourceId, ref), sourceId, ref, ...clean(v), createdAt: Date.now() });
  await refresh(sourceId);
  return ref;
}

/** Modifie une chaîne manuelle (même identifiant : favoris et historique restent attachés). */
export async function updateManual(sourceId: number, ref: string, v: ManualInput): Promise<void> {
  const old = await db.manualChannels.get(manualKey(sourceId, ref));
  if (!old) return;
  await db.manualChannels.put({ ...old, ...clean(v) });
  await refresh(sourceId);
}

/** Supprime une chaîne manuelle : de la table locale et du catalogue courant. */
export async function deleteManual(sourceId: number, ref: string): Promise<void> {
  await db.manualChannels.delete(manualKey(sourceId, ref));
  await refresh(sourceId, [manualStreamId(ref)]);
}

/** Retire les chaînes manuelles d'une source supprimée. */
export const deleteManualOfSource = (sourceId: number) => db.manualChannels.where("sourceId").equals(sourceId).delete();
