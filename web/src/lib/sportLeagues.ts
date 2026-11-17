// Espace « Compétitions » du Sport : ligues, ordre et masquage par profil, lecture des réponses du Worker, mise en page
// des matchs, mode sans spoiler et rapprochement avec les chaînes de l'utilisateur.
// Port fidèle de android-native/…/data/sport/SportLeagues.kt (mêmes clés de ligues que `LEAGUES` du Worker).
import { canonTeam, parseEvent } from "./sportMatch";
import type { SportEntry } from "./sport";

export interface League { key: string; name: string }

/** Catalogue (ordre par défaut) ; noms propres, non traduits. */
export const LEAGUES: League[] = [
  { key: "ligue1", name: "Ligue 1" }, { key: "premier", name: "Premier League" }, { key: "liga", name: "LaLiga" },
  { key: "seriea", name: "Serie A" }, { key: "bundesliga", name: "Bundesliga" }, { key: "ucl", name: "Champions League" },
  { key: "botola", name: "Botola Pro" }, { key: "nba", name: "NBA" }, { key: "nfl", name: "NFL" }, { key: "nhl", name: "NHL" },
  { key: "mlb", name: "MLB" }, { key: "top14", name: "Top 14" }, { key: "premrugby", name: "Premiership Rugby" }, { key: "f1", name: "Formula 1" },
];
const KNOWN = new Map(LEAGUES.map((l) => [l.key, l]));
export const leagueOf = (key: string): League | undefined => KNOWN.get(key);

// ───────────── ordre et masquage (codage « ligue1,nba,-nfl », comme Android) ─────────────

export interface LeaguePrefs { order: string[]; hidden: string[] }
export const DEFAULT_PREFS: LeaguePrefs = { order: LEAGUES.map((l) => l.key), hidden: [] };

export function decodePrefs(raw: string | null | undefined): LeaguePrefs {
  if (!raw || !raw.trim()) return DEFAULT_PREFS;
  const order: string[] = [];
  const hidden: string[] = [];
  for (const t of raw.split(",")) {
    const hide = t.startsWith("-");
    const k = t.replace(/^-/, "").trim();
    if (!KNOWN.has(k) || order.includes(k)) continue;
    order.push(k);
    if (hide) hidden.push(k);
  }
  for (const l of LEAGUES) if (!order.includes(l.key)) order.push(l.key);
  return { order, hidden };
}

export function encodePrefs(p: LeaguePrefs): string {
  const same = p.hidden.length === 0 && p.order.length === DEFAULT_PREFS.order.length && p.order.every((k, i) => k === DEFAULT_PREFS.order[i]);
  return same ? "" : p.order.map((k) => (p.hidden.includes(k) ? `-${k}` : k)).join(",");
}

export const visibleLeagues = (p: LeaguePrefs): League[] => p.order.filter((k) => !p.hidden.includes(k)).flatMap((k) => KNOWN.get(k) ?? []);
export const allLeagues = (p: LeaguePrefs): League[] => p.order.flatMap((k) => KNOWN.get(k) ?? []);

export function moveLeague(p: LeaguePrefs, key: string, delta: number): LeaguePrefs {
  const i = p.order.indexOf(key);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= p.order.length) return p;
  const order = [...p.order];
  [order[i], order[j]] = [order[j]!, order[i]!];
  return { ...p, order };
}

export function setLeagueHidden(p: LeaguePrefs, key: string, hide: boolean): LeaguePrefs {
  if (!KNOWN.has(key)) return p;
  const rest = p.hidden.filter((k) => k !== key);
  return { ...p, hidden: hide ? [...rest, key] : rest };
}

// ───────────── lecture des réponses du Worker ─────────────

export type MatchState = "pre" | "live" | "post" | "off";

export interface LeagueMatch {
  id: string; start: number | null; name: string | null; home: string | null; away: string | null;
  homeBadge: string | null; awayBadge: string | null; homeScore: number | null; awayScore: number | null;
  state: MatchState; progress: string | null; round: number | null;
}
export interface StandingRow {
  rank: number | null; team: string; badge: string | null; group: string | null; played: number | null;
  win: number | null; draw: number | null; loss: number | null; goalDiff: number | null; points: number | null; form: string | null;
}
export interface TimelineItem { kind: string; detail: string | null; home: boolean; minute: number | null; player: string | null; assist: string | null }
export interface StatItem { label: string; home: number; away: number }
export interface MatchSheet { match: LeagueMatch; venue: string | null; timeline: TimelineItem[]; stats: StatItem[] }

type Obj = Record<string, unknown>;
const isObj = (v: unknown): v is Obj => typeof v === "object" && v !== null && !Array.isArray(v);
const str = (v: unknown): string | null => (typeof v === "string" && v.trim() ? v : null);
const int = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? Math.trunc(v) : null);
const https = (v: unknown): string | null => { const s = str(v); return s && s.startsWith("https://") ? s : null; };

export function parseMatch(o: unknown): LeagueMatch | null {
  if (!isObj(o)) return null;
  // Identifiant opaque : chaîne (« fd-123 », « jo-2026-12 ») ou nombre (TheSportsDB) ; jamais validé comme un nombre.
  const id = str(o.i) ?? (typeof o.i === "number" && Number.isFinite(o.i) ? String(o.i) : null);
  if (!id) return null;
  const st = o.st;
  return {
    id, start: int(o.t), name: str(o.n), home: str(o.h), away: str(o.a), homeBadge: https(o.hb), awayBadge: https(o.ab),
    homeScore: int(o.hs), awayScore: int(o.as), state: st === "live" || st === "post" || st === "off" ? st : "pre", progress: str(o.p), round: int(o.r),
  };
}

export function parseEvents(body: unknown): LeagueMatch[] | null {
  if (!isObj(body) || !Array.isArray(body.events)) return null;
  return body.events.flatMap((e) => parseMatch(e) ?? []);
}

export function parseTable(body: unknown): StandingRow[] | null {
  if (!isObj(body)) return null;
  const rows = Array.isArray(body.rows) ? body.rows : [];
  return rows.flatMap((r): StandingRow[] => {
    if (!isObj(r)) return [];
    const team = str(r.team);
    return team ? [{ rank: int(r.rk), team, badge: https(r.badge), group: str(r.g), played: int(r.p), win: int(r.w), draw: int(r.d), loss: int(r.l), goalDiff: int(r.gd), points: int(r.pts), form: str(r.form) }] : [];
  });
}

export function parseSheet(body: unknown): MatchSheet | null {
  if (!isObj(body)) return null;
  const match = parseMatch(body.event);
  if (!match) return null;
  const ev = body.event as Obj;
  const timeline = (Array.isArray(body.timeline) ? body.timeline : []).flatMap((t): TimelineItem[] => {
    if (!isObj(t)) return [];
    const kind = str(t.k);
    return kind ? [{ kind, detail: str(t.d), home: t.h === true, minute: int(t.m), player: str(t.pl), assist: str(t.as) }] : [];
  });
  const stats = (Array.isArray(body.stats) ? body.stats : []).flatMap((s): StatItem[] => {
    if (!isObj(s)) return [];
    const label = str(s.k); const h = int(s.h); const a = int(s.a);
    return label && h !== null && a !== null ? [{ label, home: h, away: a }] : [];
  });
  return { match, venue: str(ev.v), timeline, stats };
}

// ───────────── mise en page ─────────────

export const MAX_FIXTURES = 20;
export const MAX_RESULTS = 20;

/** Les matchs du direct remplacent ceux du calendrier ayant le même identifiant (scores plus frais). */
export function mergeMatches(schedule: LeagueMatch[], live: LeagueMatch[]): LeagueMatch[] {
  const ids = new Set(live.map((m) => m.id));
  return [...schedule.filter((m) => !ids.has(m.id)), ...live].sort((a, b) => (a.start ?? Infinity) - (b.start ?? Infinity));
}
export const liveOf = (all: LeagueMatch[]) => all.filter((m) => m.state === "live");
export const fixturesOf = (all: LeagueMatch[], now: number) =>
  all.filter((m) => m.state === "pre" || (m.state === "off" && (m.start ?? 0) > now)).slice(0, MAX_FIXTURES);
export const resultsOf = (all: LeagueMatch[]) => all.filter((m) => m.state === "post").sort((a, b) => (b.start ?? 0) - (a.start ?? 0)).slice(0, MAX_RESULTS);

/** Interroger le direct seulement si un match est en cours, va commencer (10 min) ou a commencé il y a moins de 4 h. */
export const mayBeLive = (all: LeagueMatch[], now: number): boolean =>
  all.some((m) => m.state === "live" || (m.state === "pre" && m.start !== null && m.start <= now + 10 * 60_000 && m.start > now - 4 * 3_600_000));

// ───────────── mode sans spoiler ─────────────

export const showScore = (noSpoiler: boolean, id: string, revealed: ReadonlySet<string>): boolean => !noSpoiler || revealed.has(id);
export const hasSpoiler = (m: LeagueMatch): boolean => m.homeScore !== null && m.awayScore !== null && (m.state === "live" || m.state === "post");
export const showTable = (noSpoiler: boolean, key: string, revealed: ReadonlySet<string>): boolean => !noSpoiler || revealed.has(key);

// ───────────── « Aussi sur » : chaînes de l'utilisateur ─────────────

export const CHANNEL_TOLERANCE_MS = 30 * 60_000;

/** Programmes du guide qui diffusent ce match (mêmes deux équipes, alias et accents ignorés, horaire proche), meilleure chaîne d'abord. */
export function channelsFor(m: LeagueMatch, entries: SportEntry[]): SportEntry[] {
  if (!m.home || !m.away || m.start === null) return [];
  const h = canonTeam(m.home); const a = canonTeam(m.away);
  if (!h || !a) return [];
  const key = [h, a].sort().join("|");
  const seen = new Set<string>();
  return entries
    .filter((e) => { const p = parseEvent(e.prog.title); return p.isMatch && p.key === key && Math.abs(e.prog.start - m.start!) <= CHANNEL_TOLERANCE_MS; })
    .sort((x, y) => y.channel.q - x.channel.q || Number(y.sportChannel) - Number(x.sportChannel) || Number(x.channel.num === 0) - Number(y.channel.num === 0) || x.channel.num - y.channel.num)
    .filter((e) => { const k = String(e.channel.streamId); if (seen.has(k)) return false; seen.add(k); return true; });
}

export const matchTitle = (m: LeagueMatch): string => (m.home && m.away ? `${m.home} – ${m.away}` : (m.name ?? m.home ?? m.away ?? ""));
