// Horaires de visionnage et temps d'écran quotidien d'un profil (surtout Enfants). Logique PURE (l'heure et le fuseau sont des
// paramètres), même modèle et même codage qu'Android (ScreenTime.kt) : plages, minuit, changement d'heure, cumul par jour local.
//
// Une plage = [début, fin) en minutes depuis minuit (heure du mur). Jour de la semaine (1 = lundi … 7 = dimanche) ABSENT = aucune
// restriction horaire ; présent avec liste VIDE = interdit toute la journée. La limite compte la lecture effective, par jour local.

export interface TimeWindow { start: number; end: number }
export interface Rules { limit: number | null; days: Record<number, TimeWindow[]> }
export type BlockReason = "outside" | "limit";
export type Verdict =
  | { kind: "free" }
  | { kind: "allowed"; left: number; warn: boolean }
  | { kind: "blocked"; reason: BlockReason; next: number | null };
/** Dérogation parentale : ignore plages ET limite jusqu'à `until` ; `wholeDay` = « pour aujourd'hui » (pas d'avertissement). */
export interface Grant { until: number; wholeDay: boolean }

export const WARN_SECONDS = 300;
export const EXTRA_MINUTES = 30;
export const MAX_WINDOWS_PER_DAY = 4;
export const MAX_ENCODED = 400;
export const KEEP_DAYS = 14;
export const LIMIT_CHOICES = [30, 60, 90, 120, 180, 240, 300, 360];

export const emptyRules = (): Rules => ({ limit: null, days: {} });
export const isEmpty = (r: Rules) => r.limit === null && Object.keys(r.days).length === 0;

// ---------- Codage compact « L90;1=480-1200,1260-1380;2=;… » ----------

/** Plages valides (0 ≤ début < fin ≤ 1440), triées, fusionnées si elles se touchent, 4 au plus. */
export function normalize(w: TimeWindow[]): TimeWindow[] {
  const ok = w.filter((x) => Number.isInteger(x.start) && Number.isInteger(x.end) && x.start >= 0 && x.start <= 1439 && x.end >= 1 && x.end <= 1440 && x.end > x.start)
    .sort((a, b) => a.start - b.start);
  const out: TimeWindow[] = [];
  for (const x of ok) {
    const last = out[out.length - 1];
    if (last && x.start <= last.end) out[out.length - 1] = { start: last.start, end: Math.max(last.end, x.end) };
    else out.push({ ...x });
  }
  return out.slice(0, MAX_WINDOWS_PER_DAY);
}

export function encode(r: Rules): string {
  const parts: string[] = [];
  if (r.limit !== null && r.limit >= 1 && r.limit <= 1440) parts.push(`L${r.limit}`);
  for (let d = 1; d <= 7; d++) {
    const w = r.days[d];
    if (w) parts.push(`${d}=${normalize(w).map((x) => `${x.start}-${x.end}`).join(",")}`);
  }
  return parts.join(";");
}

/** Tolérant : segment illisible ignoré, plages bornées, triées et fusionnées. Vide / indéfini = aucune règle. */
export function decode(raw: string | null | undefined): Rules {
  const r = emptyRules();
  if (!raw || !raw.trim() || raw.length > MAX_ENCODED) return r;
  for (const seg of raw.split(";")) {
    const s = seg.trim();
    if (s.startsWith("L")) { const n = Number(s.slice(1)); if (/^L\d+$/.test(s) && n >= 1 && n <= 1440) r.limit = n; continue; }
    const m = /^([1-7])=(.*)$/.exec(s);
    if (!m) continue;
    const wins: TimeWindow[] = [];
    for (const w of m[2]!.split(",")) {
      const p = /^\s*(\d+)\s*-\s*(\d+)\s*$/.exec(w);
      if (p) wins.push({ start: Number(p[1]), end: Number(p[2]) });
    }
    r.days[Number(m[1])] = normalize(wins);
  }
  return r;
}

// ---------- Fuseau et heure locale ----------

export interface Zone {
  /** Jour local « aaaa-mm-jj », jour de la semaine (1 = lundi … 7) et minute du jour (heure du mur). */
  parts(ms: number): { date: string; dow: number; minute: number };
  /** Instant de l'heure du mur `minute` du jour `date` (1440 = minuit suivant) ; dans un trou d'heure d'été, décalé vers l'avant. */
  epoch(date: string, minute: number): number;
}

const pad = (n: number) => String(n).padStart(2, "0");
export const addDays = (date: string, n: number): string => {
  const [y, m, d] = date.split("-").map(Number) as [number, number, number];
  const t = new Date(Date.UTC(y, m - 1, d + n));
  return `${t.getUTCFullYear()}-${pad(t.getUTCMonth() + 1)}-${pad(t.getUTCDate())}`;
};
const dowOf = (date: string): number => {
  const [y, m, d] = date.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay() || 7;
};

/** Zone IANA (« Europe/Paris ») ; sans argument, celle de l'appareil. */
export function zoneFor(timeZone?: string): Zone {
  const fmt = new Intl.DateTimeFormat("en-GB", { timeZone, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" });
  const read = (ms: number) => {
    const o: Record<string, number> = {};
    for (const p of fmt.formatToParts(new Date(ms))) if (p.type !== "literal") o[p.type] = Number(p.value);
    return o as { year: number; month: number; day: number; hour: number; minute: number; second: number };
  };
  /** Décalage du fuseau (ms) à l'instant `ms` : heure du mur lue comme UTC moins l'instant (à la seconde). */
  const offsetAt = (ms: number) => { const p = read(ms); return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - Math.floor(ms / 1000) * 1000; };
  const epoch = (date: string, minute: number): number => {
    if (minute >= 1440) return epoch(addDays(date, 1), 0);
    const [y, m, d] = date.split("-").map(Number) as [number, number, number];
    const utc = Date.UTC(y, m - 1, d, Math.floor(minute / 60), minute % 60);
    const o1 = offsetAt(utc);
    let t = utc - o1;
    const o2 = offsetAt(t);
    if (o2 !== o1) t = utc - o2;
    return t;
  };
  return {
    parts: (ms) => { const p = read(ms); const date = `${p.year}-${pad(p.month)}-${pad(p.day)}`; return { date, dow: dowOf(date), minute: p.hour * 60 + p.minute }; },
    epoch,
  };
}

export const dayKey = (ms: number, z: Zone) => z.parts(ms).date;
export const nextMidnight = (ms: number, z: Zone) => z.epoch(addDays(z.parts(ms).date, 1), 0);

// ---------- Plages ----------

/**
 * Fin de l'autorisation horaire en cours, ou null s'il n'y a aucune contrainte horaire dans les 8 jours à venir.
 * Renvoie `now` si `now` est HORS plage. Les plages qui se touchent à minuit sont enchaînées ; un jour sans restriction laisse
 * passer la journée entière.
 */
export function allowedUntil(r: Rules, now: number, z: Zone): number | null {
  const p = z.parts(now);
  let date = p.date; let minute = p.minute;
  for (let i = 0; i <= 8; i++) {
    const w = r.days[dowOf(date)];
    if (w) {
      const cur = w.find((x) => minute >= x.start && minute < x.end);
      if (!cur) return i === 0 ? now : z.epoch(date, 0);
      if (cur.end < 1440) return z.epoch(date, cur.end);
    }
    date = addDays(date, 1); minute = 0;
  }
  return null;
}

/** Premier instant autorisé à partir de `from` (lui-même s'il l'est déjà) dans les 8 jours ; null sinon. */
export function nextAllowed(r: Rules, from: number, z: Zone): number | null {
  const p = z.parts(from);
  for (let i = 0; i <= 8; i++) {
    const date = addDays(p.date, i);
    const w = r.days[dowOf(date)];
    if (!w) return i === 0 ? from : z.epoch(date, 0);
    const m = i === 0 ? p.minute : -1;
    const hit = w.find((x) => x.end > m);
    if (!hit) continue;
    return m >= hit.start ? from : z.epoch(date, hit.start);
  }
  return null;
}

// ---------- Décision ----------

/** `usedSec` : lecture effective cumulée aujourd'hui (jour local de `now`) ; `grant` : dérogation parentale en cours. */
export function evaluate(r: Rules, usedSec: number, now: number, z: Zone, grant?: Grant | null): Verdict {
  if (isEmpty(r)) return { kind: "free" };
  if (grant && now < grant.until) {
    const left = Math.floor((grant.until - now) / 1000);
    return { kind: "allowed", left, warn: !grant.wholeDay && left <= WARN_SECONDS };
  }
  let left = Infinity;
  if (Object.keys(r.days).length) {
    const until = allowedUntil(r, now, z);
    if (until !== null) {
      if (until <= now) return { kind: "blocked", reason: "outside", next: nextAllowed(r, now, z) };
      left = Math.floor((until - now) / 1000);
    }
  }
  if (r.limit !== null) {
    const rest = r.limit * 60 - usedSec;
    if (rest <= 0) return { kind: "blocked", reason: "limit", next: nextAllowed(r, nextMidnight(now, z), z) };
    left = Math.min(left, rest);
  }
  if (left === Infinity) return { kind: "free" };
  return { kind: "allowed", left, warn: left <= WARN_SECONDS };
}

/** Dérogation « +30 min » : prolonge la dérogation en cours, sinon part de maintenant. */
export const extend = (g: Grant | null | undefined, now: number): Grant => ({ until: Math.max(now, g?.until ?? 0) + EXTRA_MINUTES * 60_000, wholeDay: false });
/** Dérogation « pour aujourd'hui » : jusqu'à minuit local. */
export const forToday = (now: number, z: Zone): Grant => ({ until: nextMidnight(now, z), wholeDay: true });

// ---------- Cumul par jour local ----------

export type Usage = Record<string, number>;

/** Découpe [from, to[ par jour local (durée réelle écoulée : le changement d'heure ne fausse pas le cumul). Millisecondes par jour. */
export function splitByDay(from: number, to: number, z: Zone): Usage {
  const out: Usage = {};
  let a = from;
  while (a < to) {
    const end = Math.min(to, nextMidnight(a, z));
    const k = dayKey(a, z);
    out[k] = (out[k] ?? 0) + (end - a);
    a = end;
  }
  return out;
}

export function addUsage(usage: Usage, from: number, to: number, z: Zone): Usage {
  const m = { ...usage };
  for (const [k, ms] of Object.entries(splitByDay(from, to, z))) m[k] = (m[k] ?? 0) + ms;
  return m;
}

/** Durée créditée entre deux ticks : plafonnée (veille, onglet gelé) et jamais négative (horloge reculée). */
export const creditMs = (last: number, now: number, cap = 5000) => Math.min(Math.max(now - last, 0), cap);

/** Ne garde que les KEEP_DAYS derniers jours (« aaaa-mm-jj » : l'ordre du texte est celui des dates). */
export function prune(usage: Usage): Usage {
  return Object.fromEntries(Object.entries(usage).sort((a, b) => (a[0] < b[0] ? 1 : -1)).slice(0, KEEP_DAYS));
}

export const usedToday = (usage: Usage, now: number, z: Zone) => Math.floor((usage[dayKey(now, z)] ?? 0) / 1000);

/**
 * Un pas du compteur : crédite la lecture effective entre `last` et `now` (seulement si elle était autorisée à `last`,
 * durée plafonnée), puis rend la décision à `now`.
 */
export function advance(r: Rules, usage: Usage, grant: Grant | null | undefined, playing: boolean, last: number, now: number, z: Zone): { usage: Usage; verdict: Verdict; counted: boolean } {
  const before = evaluate(r, usedToday(usage, last, z), last, z, grant);
  const counted = playing && before.kind !== "blocked";
  const next = counted ? prune(addUsage(usage, last, last + creditMs(last, now), z)) : usage;
  return { usage: next, verdict: evaluate(r, usedToday(next, now, z), now, z, grant), counted };
}

// ---------- Mise en forme ----------

/** Nom du jour (1 = lundi … 7 = dimanche) dans la langue `locale`. */
export const dayName = (dow: number, locale: string): string => {
  const s = new Intl.DateTimeFormat(locale, { weekday: "long", timeZone: "UTC" }).format(new Date(Date.UTC(2024, 0, dow))); // 1er janvier 2024 = lundi
  return s.charAt(0).toLocaleUpperCase(locale) + s.slice(1);
};
export const hhmm = (min: number) => `${pad(Math.floor(min / 60))}:${pad(min % 60)}`;
export const parseHhmm = (s: string): number | null => { const m = /^(\d{1,2}):(\d{2})$/.exec(s); return m && Number(m[1]) <= 24 && Number(m[2]) < 60 ? Number(m[1]) * 60 + Number(m[2]) : null; };
