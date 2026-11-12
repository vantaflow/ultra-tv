// Format de l'heure (12 h / 24 h) : module UNIQUE pour tout ce qui affiche une heure. Mêmes clés que sur Android : "auto" | "12" | "24".
// Logique pure (formatHm, parseTimeFormat) + un habillage Intl pour « Automatique » et les mentions AM / PM de la langue.

export type TimeFormat = "auto" | "12" | "24";
export const TIME_FORMATS: readonly TimeFormat[] = ["auto", "12", "24"];

/** Tolérant : valeur absente ou inconnue (autre appareil, version future) = Automatique. */
export function parseTimeFormat(v: unknown): TimeFormat {
  const s = typeof v === "string" ? v.trim().toLowerCase() : "";
  return s === "12" || s === "24" ? s : "auto";
}

const two = (n: number) => (n < 10 ? `0${n}` : String(n));

/** « 16:30 » (24 h) ou « 4:30 PM » (12 h ; minuit « 12:00 AM », midi « 12:00 PM »). Chiffres latins dans toutes les langues. */
export function formatHm(hour: number, minute: number, use24: boolean, am = "AM", pm = "PM"): string {
  const h = ((Math.trunc(hour) % 24) + 24) % 24;
  if (use24) return `${two(h)}:${two(minute)}`;
  return `${h % 12 === 0 ? 12 : h % 12}:${two(minute)} ${h < 12 ? am : pm}`;
}

/** « 16:00 – 17:30 » : isolé en LTR pour que les bornes ne s'inversent pas en arabe. */
export const formatRange = (a: string, b: string) => `⁦${a} – ${b}⁩`;

/** Le système / la langue affiche-t-il l'heure sur 24 h ? (Intl ; repli sur 24 h si indisponible.) */
const sys24 = new Map<string, boolean>();
export function systemUses24(locale?: string): boolean {
  const key = locale ?? "";
  const hit = sys24.get(key);
  if (hit !== undefined) return hit;
  const r = detect24(locale);
  sys24.set(key, r);
  return r;
}
function detect24(locale?: string): boolean {
  try {
    const o = new Intl.DateTimeFormat(locale, { hour: "numeric" }).resolvedOptions();
    if (o.hourCycle) return o.hourCycle === "h23" || o.hourCycle === "h24";
    return o.hour12 === false;
  } catch { return true; }
}

export const use24 = (format: TimeFormat, locale?: string): boolean => format === "24" || (format === "auto" && systemUses24(locale));

const periods = new Map<string, [string, string]>();
/** Mentions matin / après-midi de la langue (« AM », « PM » ; « ص », « م » en arabe). */
export function dayPeriods(locale?: string): [string, string] {
  const key = locale ?? "";
  const hit = periods.get(key);
  if (hit) return hit;
  let r: [string, string] = ["AM", "PM"];
  try {
    const f = new Intl.DateTimeFormat(locale, { hour: "numeric", hour12: true });
    const part = (d: Date) => f.formatToParts(d).find((p) => p.type === "dayPeriod")?.value;
    const am = part(new Date(2024, 0, 1, 9)), pm = part(new Date(2024, 0, 1, 15));
    if (am && pm) r = [am, pm];
  } catch { /* garde AM / PM */ }
  periods.set(key, r);
  return r;
}

/** Heure locale d'un instant selon le réglage. */
export function clockText(ms: number, format: TimeFormat, locale?: string): string {
  const d = new Date(ms);
  const h24 = use24(format, locale);
  const [am, pm] = h24 ? ["", ""] : dayPeriods(locale);
  return formatHm(d.getHours(), d.getMinutes(), h24, am, pm);
}
