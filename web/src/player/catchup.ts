// Construction PURE des URL de rattrapage (même logique que l'appli Android : data/replay/CatchupFormats.kt).
// Les URL contiennent des identifiants : ne jamais les afficher ni les journaliser.

export type CatchupType = "default" | "append" | "shift" | "flussonic" | "xc";

export function parseCatchupType(raw: string | null | undefined): CatchupType | null {
  switch ((raw ?? "").trim().toLowerCase()) {
    case "default": return "default";
    case "append": return "append";
    case "shift": case "timeshift": return "shift";
    case "flussonic": case "flussonic-hls": case "flussonic-ts": case "fs": return "flussonic";
    case "xc": case "xtream": case "xtream-codes": return "xc";
    default: return null;
  }
}

/** Jours d'archive supposés quand la playlist annonce un mode sans `catchup-days`. */
export const DEFAULT_CATCHUP_DAYS = 5;

/** `AAAA-MM-JJ:HH-MM` à l'heure du fuseau IANA [timeZone] (celui du serveur Xtream ; à défaut, l'heure locale). */
export function xtreamStamp(startMs: number, timeZone?: string): string {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
  }).formatToParts(new Date(startMs));
  const g = (t: string) => parts.find((p) => p.type === t)?.value ?? "00";
  return `${g("year")}-${g("month")}-${g("day")}:${g("hour")}-${g("minute")}`;
}

/** Durée en minutes, arrondie au-dessus, d'une minute au moins. */
export const durationMinutes = (startMs: number, endMs: number): number => Math.max(1, Math.ceil((endMs - startMs) / 60000));

/** Plus ancien instant rejouable : [days] jours avant [nowMs]. */
export const earliestMs = (days: number, nowMs: number): number => nowMs - Math.max(0, days) * 86_400_000;

export type CatchupAvailability = "available" | "notArchived" | "outOfWindow" | "notStarted";

export function availability(days: number, startMs: number, nowMs: number): CatchupAvailability {
  if (days <= 0) return "notArchived";
  if (startMs > nowMs) return "notStarted";
  if (startMs < earliestMs(days, nowMs)) return "outOfWindow";
  return "available";
}

const XTREAM_LIVE = /^(https?:\/\/[^/]+)\/(?:live\/)?([^/]+)\/([^/]+)\/([^./?]+)(\..+)?$/;

/** URL Xtream `timeshift/user/pass/durée/AAAA-MM-JJ:HH-MM/id.ts` (style "path") ou `timeshift.php` (style "php"). */
export function xtreamUrl(liveUrl: string, startMs: number, endMs: number, style: "path" | "php", timeZone?: string): string | null {
  const m = XTREAM_LIVE.exec(liveUrl);
  if (!m) return null;
  const [, base, user, pass, sid] = m;
  const start = xtreamStamp(startMs, timeZone);
  const dur = durationMinutes(startMs, endMs);
  return style === "path"
    ? `${base}/timeshift/${user}/${pass}/${dur}/${start}/${sid}.ts`
    : `${base}/streaming/timeshift.php?username=${user}&password=${pass}&stream=${sid}&start=${start}&duration=${dur}`;
}

/** Gabarit unique d'une chaîne M3U selon `catchup` / `catchup-source` ; null : rien à stocker (mode xc, ou aucune information). */
export function m3uTemplate(type: CatchupType | null, source: string | null | undefined, streamUrl: string): string | null {
  const src = source?.trim() || null;
  switch (type) {
    case "xc": return null;
    case "append": return src ? streamUrl + src : null;
    case "shift": return src ? streamUrl + src : `${streamUrl}${streamUrl.includes("?") ? "&" : "?"}utc={utc}&lutc={lutc}`;
    case "flussonic": return flussonic(src, streamUrl);
    default: return src ? (type === null && !/^http/i.test(src) ? streamUrl + src : src) : null;
  }
}

function flussonic(src: string | null, streamUrl: string): string | null {
  const noQuery = streamUrl.split("?")[0] ?? streamUrl;
  const query = streamUrl.includes("?") ? "?" + streamUrl.split("?").slice(1).join("?") : "";
  const slash = noQuery.lastIndexOf("/");
  if (src) return /^http/i.test(src) ? src : `${noQuery.slice(0, slash)}/${src.replace(/^\/+/, "")}`;
  if (slash < "https://x".length) return null;
  const dir = noQuery.slice(0, slash), last = noQuery.slice(slash + 1);
  if (last.toLowerCase() === "mpegts" || /\.ts$/i.test(last)) return `${dir}/timeshift_abs-{utc}.ts${query}`;
  if (/\.m3u8$/i.test(last)) return `${dir}/archive-{utc}-{duration}.m3u8${query}`;
  return null;
}

/** Remplace les jetons `{x}` / `${x}` : utc|start, utcend|end, lutc|now|timestamp, offset[:N], duration[:N], Y m d H M S (UTC). */
export function fillTemplate(template: string, startMs: number, endMs: number, nowMs: number): string {
  const sec = (ms: number) => Math.floor(ms / 1000);
  const d = new Date(startMs);
  const two = (n: number) => String(n).padStart(2, "0");
  const named: Record<string, string> = {
    utc: String(sec(startMs)), start: String(sec(startMs)), utcend: String(sec(endMs)), end: String(sec(endMs)),
    lutc: String(sec(nowMs)), now: String(sec(nowMs)), timestamp: String(sec(nowMs)),
    Y: String(d.getUTCFullYear()), m: two(d.getUTCMonth() + 1), d: two(d.getUTCDate()),
    H: two(d.getUTCHours()), M: two(d.getUTCMinutes()), S: two(d.getUTCSeconds()),
  };
  const durSec = Math.max(60, sec(endMs - startMs));
  const offSec = Math.max(0, sec(nowMs - startMs));
  return template.replace(/\$?\{(\w+)(?::(\d+))?\}/g, (whole, name: string, div?: string) => {
    const k = div && Number(div) > 0 ? Number(div) : 1;
    if (name === "duration") return String(Math.floor(durSec / k));
    if (name === "offset") return String(Math.floor(offSec / k));
    return named[name] ?? whole;
  });
}
