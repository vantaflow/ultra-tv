// Accueil Sport unifié : cartes « En direct », filtre « À venir », zones et forme des classements, identifiants opaques.
// Port fidèle de android-native/…/data/sport/SportHubLogic.kt. Fonctions pures, sans réseau.
import type { ChannelRow } from "@/db/types";
import { chanKey, matchLineOf, progress, type SportDay, type SportEntry, type SportKind } from "./sport";
import { channelsFor, type LeagueMatch, type StandingRow } from "./sportLeagues";
import { canonTeam, entryKey, parseEvent } from "./sportMatch";

// ───────────── pastilles d'équipe ─────────────

const CREST_PALETTE = ["#1E3A8A", "#0EA5E9", "#B91C1C", "#991B1B", "#CA8A04", "#15803D", "#1D4ED8", "#7C3AED", "#0F766E", "#4B5563"];

/** Initiales d'une équipe : un mot = 3 premières lettres, plusieurs = initiales des 3 premiers mots. */
export function initialsOf(name: string | null | undefined): string {
  const words = (name ?? "").trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "";
  if (words.length === 1) return Array.from(words[0]!).slice(0, 3).join("").toUpperCase();
  return words.slice(0, 3).map((w) => Array.from(w)[0]!.toUpperCase()).join("");
}

/** Couleur stable d'une équipe (même nom, même couleur ; alias courants rapprochés). */
export function crestColor(name: string | null | undefined): string {
  let h = 0;
  for (const ch of canonTeam(name ?? "")) h = (h * 31 + ch.charCodeAt(0)) | 0;
  return CREST_PALETTE[Math.abs(h) % CREST_PALETTE.length]!;
}

// ───────────── zones et forme des classements ─────────────

export type Zone = "none" | "qualified" | "europa" | "relegation";
// Places qualificatives (Ligue des champions), puis Europa, et nombre de derniers relégués, par ligue.
const SPECS: Record<string, { qualified: number; europa: number; relegated: number }> = {
  ligue1: { qualified: 3, europa: 1, relegated: 3 }, premier: { qualified: 4, europa: 1, relegated: 3 }, liga: { qualified: 4, europa: 1, relegated: 3 },
  seriea: { qualified: 4, europa: 1, relegated: 3 }, bundesliga: { qualified: 4, europa: 1, relegated: 2 }, botola: { qualified: 0, europa: 0, relegated: 2 },
};

export function zoneOf(leagueKey: string | null, rank: number | null, size: number, grouped = false): Zone {
  const s = leagueKey ? SPECS[leagueKey] : undefined;
  if (!s || rank === null || grouped || size < 8) return "none";
  if (rank <= s.qualified) return "qualified";
  if (rank <= s.qualified + s.europa) return "europa";
  if (s.relegated > 0 && rank > size - s.relegated) return "relegation";
  return "none";
}
export const hasZones = (key: string | null): boolean => !!key && key in SPECS;
export const hasEurope = (key: string | null): boolean => !!key && (SPECS[key]?.qualified ?? 0) > 0;

/** Cinq derniers résultats, V/N/D normalisés en W/D/L. */
export function parseForm(form: string | null | undefined): Array<"W" | "D" | "L"> {
  const out: Array<"W" | "D" | "L"> = [];
  for (const c of (form ?? "").toUpperCase()) {
    if (c === "W" || c === "V" || c === "G") out.push("W");
    else if (c === "D" || c === "N") out.push("D");
    else if (c === "L" || c === "P") out.push("L");
  }
  return out.slice(-5);
}

// ───────────── cartes « En direct » ─────────────

export interface LiveCard {
  key: string; league: string; minute: string | null; home: string | null; away: string | null; title: string;
  homeScore: number | null; awayScore: number | null; match: LeagueMatch | null;
  /** Chaînes, meilleure qualité d'abord (la première lance la lecture). */
  channels: SportEntry[]; progress: number | null;
}

/** Clé de compétition du guide (parseEvent) pour chaque ligue du catalogue. */
const COMPETITION: Record<string, string> = {
  ligue1: "ligue1", premier: "epl", liga: "laliga", seriea: "seriea", bundesliga: "bundesliga", ucl: "ucl", botola: "botola", nba: "nba", nfl: "nfl", nhl: "nhl", f1: "f1",
};

/** « 4K », « HD » (HD et FHD), « SD », ou vide si inconnue. */
export const qualityOf = (c: ChannelRow): string => (c.q >= 4 ? "4K" : c.q >= 2 ? "HD" : c.q === 1 ? "SD" : "");

/** Minute de jeu « 67' » d'un match en direct, ou null. */
export function minuteOf(m: LeagueMatch): string | null {
  const p = m.progress?.trim();
  if (!p) return null;
  return /\d$/.test(p) ? `${p}'` : p;
}

/** Part du premier sur le total, 0..100 (barres de statistiques) ; 50 si rien. */
export const sharePercent = (home: number, away: number): number => (home + away <= 0 ? 50 : Math.round((home * 100) / (home + away)));

/** Dernière journée jouée (sous-titre du classement). */
export function roundOf(rows: StandingRow[]): number | null {
  const m = Math.max(0, ...rows.map((r) => r.played ?? 0));
  return m > 0 ? m : null;
}

/** Entrées triées par ordre d'affichage (qualité, chaîne de sport, numéro), une par chaîne. */
export function orderChannels(entries: SportEntry[]): SportEntry[] {
  const seen = new Set<number>();
  return [...entries]
    .sort((x, y) => y.channel.q - x.channel.q || Number(y.sportChannel) - Number(x.sportChannel) || Number(x.channel.num === 0) - Number(y.channel.num === 0) || x.channel.num - y.channel.num || (x.channel.display < y.channel.display ? -1 : x.channel.display > y.channel.display ? 1 : 0))
    .filter((e) => { const k = chanKey(e.channel); if (seen.has(k)) return false; seen.add(k); return true; });
}

const liveMatches = (all: LeagueMatch[]) => all.filter((m) => m.state === "live");
const minuteFraction = (m: LeagueMatch, kind: SportKind | null): number | null => {
  if (kind !== "football") return null;
  const n = parseInt((m.progress ?? "").replace(/\D/g, ""), 10);
  return Number.isFinite(n) ? Math.min(1, Math.max(0, n / 90)) : null;
};

/**
 * Cartes « En direct ». Une ligue choisie : ses matchs en direct (avec score), reliés aux chaînes du guide. « Tout » : les
 * directs du guide, enrichis du score et de la minute quand ils correspondent à un match de la ligue chargée.
 */
export function liveCards(args: {
  leagueKey: string | null; leagueName: string | undefined; kind: SportKind | null; matches: LeagueMatch[];
  guideLive: SportEntry[]; entries: SportEntry[]; alsoOn: Map<string, SportEntry[]>; now: number; programmeLabel: (e: SportEntry) => string;
}): LiveCard[] {
  const { leagueKey, leagueName, kind, matches, guideLive, entries, alsoOn, now, programmeLabel } = args;
  const lives = liveMatches(matches);
  if (leagueKey !== null) {
    return lives.map((m) => {
      const ch = channelsFor(m, entries);
      const first = ch[0];
      return {
        key: `m${m.id}`, league: leagueName ?? "", minute: minuteOf(m), home: m.home, away: m.away, title: m.home && m.away ? `${m.home} – ${m.away}` : (m.name ?? ""),
        homeScore: m.homeScore, awayScore: m.awayScore, match: m, channels: ch,
        progress: first ? progress(first.prog.start, first.prog.end, now) : minuteFraction(m, kind),
      };
    });
  }
  const byEntry = new Map<string, LeagueMatch>();
  for (const m of lives) for (const e of channelsFor(m, entries)) byEntry.set(entryKey(e), m);
  return guideLive.map((e) => {
    const m = byEntry.get(entryKey(e)) ?? null;
    const line = matchLineOf(e.prog.title);
    return {
      key: `g${entryKey(e)}`, league: m ? (leagueName ?? "") : (line.competition ?? programmeLabel(e)),
      minute: m ? minuteOf(m) : null, home: m?.home ?? line.home, away: m?.away ?? line.away, title: line.title,
      homeScore: m?.homeScore ?? null, awayScore: m?.awayScore ?? null, match: m,
      channels: orderChannels([e, ...(alsoOn.get(entryKey(e)) ?? [])]), progress: progress(e.prog.start, e.prog.end, now),
    };
  });
}

/**
 * Programmes à venir pour la ligue choisie : ceux du guide qui diffusent un de ses matchs à venir ou en cours, ou dont le
 * titre annonce sa compétition. « Tout » (null) : tous les programmes reconnus, magazines et autres sports compris.
 */
export function filterUpcoming(leagueKey: string | null, matches: LeagueMatch[], entries: SportEntry[], days: SportDay[]): SportDay[] {
  if (leagueKey === null) return days;
  const covered = new Set<string>();
  for (const m of matches) if (m.state !== "post") for (const e of channelsFor(m, entries)) covered.add(entryKey(e));
  const comp = COMPETITION[leagueKey];
  return days
    .map((d) => ({ ...d, entries: d.entries.filter((e) => covered.has(entryKey(e)) || (comp !== undefined && parseEvent(e.prog.title).competition === comp)) }))
    .filter((d) => d.entries.length > 0);
}
