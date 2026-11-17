// Données de l'espace Compétitions : scores, calendriers, classements et feuilles de match, lus UNIQUEMENT via le Worker
// (jamais TheSportsDB en direct), appareil appairé seulement, et mémorisés ici : direct 60 s, calendrier 1 h, classement 6 h,
// feuille de match 2 min (1 h une fois terminé). Rien d'identifiant n'est envoyé (une clé de ligue ou un identifiant de match).
// Après un échec, la même ressource n'est pas redemandée avant 30 s ; la dernière valeur connue est servie (« ancienne »).
import { useEffect, useState } from "react";
import { cloudAvailable, sportsDataGet } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";
import {
  mayBeLive, mergeMatches, parseEvents, parseSheet, parseTable,
  type LeagueMatch, type MatchSheet, type StandingRow,
} from "@/lib/sportLeagues";

export const LIVE_MS = 60_000;
export const SCHEDULE_MS = 3_600_000;
export const TABLE_MS = 6 * 3_600_000;
export const SHEET_LIVE_MS = 120_000;
export const SHEET_DONE_MS = 3_600_000;
export const COOLDOWN_MS = 30_000;
const IDLE_MS = 10 * 60_000;

export type Fetched<T> = { status: "ok"; value: T; stale: boolean } | { status: "unavailable" } | { status: "unpaired" };

const fresh = new Map<string, { at: number; value: unknown }>();
const lastGood = new Map<string, unknown>();
const failedAt = new Map<string, number>();
const inflight = new Map<string, Promise<Fetched<unknown>>>();

/** Vide les mémoires (tests). */
export function resetSportLeaguesCache() { fresh.clear(); lastGood.clear(); failedAt.clear(); inflight.clear(); }

/** Lecture mémorisée d'une ressource du Worker ; `get` est injectable pour les tests. */
export async function fetchCached<T>(
  key: string, path: string, ttl: number, parse: (b: unknown) => T | null, now = Date.now(),
  get: (path: string) => Promise<unknown | null> = defaultGet,
): Promise<Fetched<T>> {
  const hit = fresh.get(key);
  if (hit && now - hit.at >= 0 && now - hit.at < ttl) return { status: "ok", value: hit.value as T, stale: false };
  const old = lastGood.get(key) as T | undefined;
  const failed = failedAt.get(key);
  if (failed !== undefined && now - failed >= 0 && now - failed < COOLDOWN_MS) return old !== undefined ? { status: "ok", value: old, stale: true } : { status: "unavailable" };
  const running = inflight.get(key);
  if (running) return running as Promise<Fetched<T>>;
  const p = (async (): Promise<Fetched<T>> => {
    let v: T | null = null;
    try { const body = await get(path); v = body === null ? null : parse(body); } catch { v = null; }
    if (v === null) {
      failedAt.set(key, now);
      return old !== undefined ? { status: "ok", value: old, stale: true } : { status: "unavailable" };
    }
    failedAt.delete(key);
    fresh.set(key, { at: now, value: v });
    lastGood.set(key, v);
    if (fresh.size > 200) { fresh.clear(); lastGood.clear(); }
    return { status: "ok", value: v, stale: false };
  })().finally(() => inflight.delete(key));
  inflight.set(key, p);
  return p;
}

async function defaultGet(path: string): Promise<unknown | null> {
  const s = useCloud.getState();
  if (!s.paired || !cloudAvailable()) return null;
  const token = await getToken().catch(() => "");
  if (!token) return null;
  return sportsDataGet(s.worker, token, path);
}

export type LeagueStatus = "loading" | "ok" | "unavailable" | "unpaired";
export interface LeagueData { status: LeagueStatus; stale: boolean; matches: LeagueMatch[] }
const LOADING: LeagueData = { status: "loading", stale: false, matches: [] };

/** Calendrier + direct d'une ligue. Le direct n'est relu (à la minute) que si un match est en cours ou proche ; sinon toutes les 10 min (le dépôt répond sans appel). */
export function useLeagueData(key: string | null): LeagueData & { key: string | null } {
  const paired = useCloud((s) => s.paired);
  const [state, setState] = useState<LeagueData & { key: string | null }>({ ...LOADING, key });
  useEffect(() => {
    if (!key) { setState({ status: "ok", stale: false, matches: [], key }); return; }
    if (!paired || !cloudAvailable()) { setState({ status: "unpaired", stale: false, matches: [], key }); return; }
    let dead = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    setState({ ...LOADING, key });
    let schedule: LeagueMatch[] = [];
    let live: LeagueMatch[] = [];
    const tick = async () => {
      const now = Date.now();
      let status: LeagueStatus = "ok";
      let stale = false;
      const s = await fetchCached(`s/${key}`, `league?id=${key}&view=schedule`, SCHEDULE_MS, parseEvents, now);
      if (s.status === "ok") { schedule = s.value; stale = s.stale; } else if (!schedule.length) status = "unavailable";
      if (status === "ok" && mayBeLive([...schedule, ...live], now)) {
        const l = await fetchCached(`l/${key}`, `league?id=${key}&view=live`, LIVE_MS, parseEvents, now);
        if (l.status === "ok") live = l.value;
      } else if (status === "ok") live = [];
      if (dead) return;
      setState({ status, stale, matches: mergeMatches(schedule, live), key });
      const next = status !== "ok" ? LIVE_MS : mayBeLive([...schedule, ...live], Date.now()) ? LIVE_MS : IDLE_MS;
      timer = setTimeout(() => void tick(), next);
    };
    void tick();
    return () => { dead = true; if (timer) clearTimeout(timer); };
  }, [key, paired]);
  return state;
}

/** Classement d'une ligue, lu seulement quand `enabled` (onglet ouvert ; en mode sans spoiler, une fois affiché). */
export function useTable(key: string | null, enabled: boolean): { key: string | null; rows: StandingRow[] | null; failed: boolean } {
  const paired = useCloud((s) => s.paired);
  const [state, setState] = useState<{ key: string | null; rows: StandingRow[] | null; failed: boolean }>({ key, rows: null, failed: false });
  useEffect(() => {
    if (!key || !enabled || !paired || !cloudAvailable()) { setState({ key, rows: null, failed: false }); return; }
    let dead = false;
    setState({ key, rows: null, failed: false });
    void fetchCached(`t/${key}`, `league?id=${key}&view=table`, TABLE_MS, parseTable).then((r) => {
      if (!dead) setState(r.status === "ok" ? { key, rows: r.value, failed: false } : { key, rows: null, failed: true });
    });
    return () => { dead = true; };
  }, [key, enabled, paired]);
  return state;
}

/** Feuille d'un match (score, temps forts, statistiques) ; null tant qu'elle charge ou si elle est indisponible. */
export function useSheet(matchId: string | null, finished: boolean): { loading: boolean; sheet: MatchSheet | null } {
  const paired = useCloud((s) => s.paired);
  const [state, setState] = useState<{ id: string | null; sheet: MatchSheet | null; loading: boolean }>({ id: matchId, sheet: null, loading: !!matchId });
  useEffect(() => {
    if (!matchId || !paired || !cloudAvailable()) { setState({ id: matchId, sheet: null, loading: false }); return; }
    let dead = false;
    setState({ id: matchId, sheet: null, loading: true });
    void fetchCached(`m/${matchId}`, `match?id=${encodeURIComponent(matchId)}`, finished ? SHEET_DONE_MS : SHEET_LIVE_MS, parseSheet).then((r) => {
      if (!dead) setState({ id: matchId, sheet: r.status === "ok" ? r.value : null, loading: false });
    });
    return () => { dead = true; };
  }, [matchId, finished, paired]);
  return { loading: state.id === matchId && state.loading, sheet: state.id === matchId ? state.sheet : null };
}
