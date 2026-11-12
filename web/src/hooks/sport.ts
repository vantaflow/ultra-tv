// Données de la rubrique Sport, calculées UNIQUEMENT à partir de la base locale (guide + chaînes + catégories de la source).
// Aucune requête réseau, sauf les écussons d'équipes (Worker, appareil appairé seulement). Même stratégie qu'Android
// (SportRepository) : chaînes de sport détectées une fois (catégories, puis noms), guide lu par fenêtre de temps pour ces
// chaînes, pré-filtre des titres borné pour les autres ; résultat gardé 5 min par génération de catalogue.
import { useEffect, useState } from "react";
import { cloudAvailable, sportsTeamGet } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";
import { db } from "@/db/db";
import { categoriesOf, channelsCol } from "@/db/queries";
import type { ChannelRow, ProgramRow, Source } from "@/db/types";
import {
  TITLE_HINT, UPCOMING_MS, badgeOfResponse, isSportChannel, isSportProgramme, readBadge, sportChannels, teamKey, teamQueryName,
  type SportEntry,
} from "@/lib/sport";

const DEMO = import.meta.env.VITE_DEMO === "1";

export interface SportData { entries: SportEntry[]; channels: ChannelRow[]; hasGuide: boolean }

const CACHE_MS = 5 * 60_000;
const MAX_PROG_CHANNELS = 400;
const MAX_CHANNELS = 800;
const MAX_OTHER_ROWS = 800;

const cache = new Map<number, { at: number; data: Promise<SportData> }>();

/** Chaînes de sport de la génération `cid` : catégories du direct (activées) dont le nom annonce du sport, puis noms de chaînes. */
async function detectChannels(cid: number): Promise<ChannelRow[]> {
  const cats = (await categoriesOf(cid, "live")).filter((c) => c.enabled && isSportChannel(null, c.name || c.label));
  const byCat = (await Promise.all(cats.map((c) => channelsCol(cid, c.extId).filter((x) => !x.sep).toArray()))).flat();
  const byName = await channelsCol(cid, null).filter((c) => !c.sep && isSportChannel(c.name || c.display, null)).toArray();
  return sportChannels([...byCat, ...byName], MAX_CHANNELS);
}

async function load(source: Source, now: number): Promise<SportData> {
  const cid = source.cid;
  const to = now + UPCOMING_MS;
  const channels = await detectChannels(cid);
  const hasGuide = (await db.programs.where("[sourceId+end]").between([cid, now], [cid, Infinity]).limit(1).count()) > 0;
  if (!hasGuide) return { entries: [], channels, hasGuide };
  // Chaînes de sport : tout leur guide de la fenêtre.
  const withEpg = channels.filter((c) => !!c.epg).slice(0, MAX_PROG_CHANNELS);
  const sportEpg = new Set(withEpg.map((c) => c.epg!));
  const onSport = (await Promise.all(withEpg.map(async (c) => {
    const rows = await db.programs.where("[sourceId+epg+start]").between([cid, c.epg!, now - 12 * 3600_000], [cid, c.epg!, to], true, true).filter((p) => p.end > now).toArray();
    return rows.map((p): SportEntry => ({ channel: c, prog: p, sportChannel: true, strict: isSportProgramme(p.title) }));
  }))).flat();
  // Autres chaînes : pré-filtre rapide sur le titre, puis filtre exact ; borné.
  const others: ProgramRow[] = await db.programs.where("[sourceId+end]").between([cid, now], [cid, to + 12 * 3600_000], false, true)
    .filter((p) => p.start <= to && !sportEpg.has(p.epg) && TITLE_HINT.test(p.title) && isSportProgramme(p.title))
    .limit(MAX_OTHER_ROWS).toArray();
  const chanOf = new Map<string, ChannelRow | undefined>();
  for (const epg of new Set(others.map((p) => p.epg))) {
    chanOf.set(epg, await db.channels.where("[sourceId+epg]").equals([cid, epg]).filter((c) => !c.sep).first());
  }
  const onOthers = others.flatMap((p): SportEntry[] => { const c = chanOf.get(p.epg); return c ? [{ channel: c, prog: p, sportChannel: false, strict: true }] : []; });
  return { entries: [...onSport, ...onOthers], channels, hasGuide };
}

/** Données Sport de la source (cache 5 min) ; `null` pendant le premier chargement. */
export function useSportData(source: Source | undefined, enabled = true): SportData | null {
  const [state, setState] = useState<{ cid: number; data: SportData } | null>(null);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const iv = setInterval(() => setTick((n) => n + 1), 60_000);
    return () => clearInterval(iv);
  }, []);
  useEffect(() => {
    if (!enabled || !source?.cid) return;
    let dead = false;
    const now = Date.now();
    let hit = cache.get(source.cid);
    if (!hit || now - hit.at >= CACHE_MS) {
      hit = { at: now, data: load(source, now) };
      cache.set(source.cid, hit);
      hit.data.catch(() => cache.delete(source.cid));
    }
    void hit.data.then((data) => { if (!dead) setState({ cid: source.cid, data }); }, () => { if (!dead) setState({ cid: source.cid, data: { entries: [], channels: [], hasGuide: false } }); });
    return () => { dead = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source?.cid, enabled, tick]);
  return state && state.cid === source?.cid ? state.data : null;
}

// ───────────────────────── Écussons ─────────────────────────

const badgeMem = new Map<string, string | null>();

async function badgeOf(name: string, c: { worker: string; token: string }): Promise<string | null> {
  const q = teamQueryName(name);
  const k = teamKey(name);
  if (!q || !k) return null;
  if (badgeMem.has(k)) return badgeMem.get(k)!;
  const key = `team:${k}`;
  const row = await db.details.get(key).catch(() => undefined);
  const hit = readBadge(row?.json, Date.now());
  if (hit) { badgeMem.set(k, hit.badge); return hit.badge; }
  // Seul le NOM d'équipe tiré du titre part vers le Worker ; échec réseau / 401 / 429 : rien, non mémorisé.
  const body = await sportsTeamGet(c.worker, c.token, q);
  if (body === null) return null;
  const badge = badgeOfResponse(body);
  if (badge === undefined) return null;
  const at = Date.now();
  badgeMem.set(k, badge);
  void db.details.put({ key, json: { badge, at }, fetchedAt: at }).catch(() => {});
  return badge;
}

/** Écussons des équipes (clé repliée → URL) ; vide hors appairage. Au plus 4 appels simultanés. */
export function useTeamBadges(names: string[]): Map<string, string> {
  const paired = useCloud((s) => s.paired);
  const [map, setMap] = useState<Map<string, string>>(new Map());
  const key = names.join("|");
  useEffect(() => {
    let dead = false;
    if (DEMO) {
      void import("@/demoExt").then(({ demoTeamBadge }) => {
        if (!dead) setMap(new Map(names.flatMap((n) => { const k = teamKey(n); return k ? [[k, demoTeamBadge(n)] as [string, string]] : []; })));
      });
      return () => { dead = true; };
    }
    if (!paired || !cloudAvailable() || !names.length) return;
    void (async () => {
      const s = useCloud.getState();
      const token = await getToken().catch(() => "");
      if (!token || dead) return;
      const out = new Map<string, string>();
      let i = 0;
      await Promise.all(Array.from({ length: Math.min(4, names.length) }, async () => {
        while (i < names.length && !dead) {
          const n = names[i++]!;
          const b = await badgeOf(n, { worker: s.worker, token }).catch(() => null);
          const k = teamKey(n);
          if (b && k) out.set(k, b);
        }
      }));
      if (!dead) setMap(out);
    })();
    return () => { dead = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, paired]);
  return map;
}
