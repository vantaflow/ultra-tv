// M3U / M3U8 playlist parser.
// Supports #EXTM3U, #EXTINF with tvg-* and group-title attributes, #EXTGRP, and #EXTVLCOPT
// (user-agent, http-referrer). Output is provider-agnostic — repositories map to Channel/Movie.

export interface M3uEntry {
  name: string;
  url: string;
  tvgId: string | null;
  tvgName: string | null;
  tvgLogo: string | null;
  tvgChno: number | null;
  groupTitle: string | null;
  duration: number;
  catchUp: string | null;
  catchUpDays: number;
  catchUpSource: string | null;
  userAgent: string | null;
  referrer: string | null;
  headers: Record<string, string>;
  rawAttributes: Record<string, string>;
}

const ATTR_RE = /([a-zA-Z0-9_-]+)="([^"]*)"/g;

function parseAttributes(line: string): Record<string, string> {
  const out: Record<string, string> = {};
  let m: RegExpExecArray | null;
  ATTR_RE.lastIndex = 0;
  while ((m = ATTR_RE.exec(line)) !== null) {
    out[m[1]!] = m[2]!;
  }
  return out;
}

export function parseM3u(text: string): M3uEntry[] {
  const lines = text.split(/\r?\n/);
  const entries: M3uEntry[] = [];

  let pending: Partial<M3uEntry> & { rawAttributes: Record<string, string>; headers: Record<string, string> } = {
    rawAttributes: {},
    headers: {},
    duration: -1,
    catchUpDays: 0,
  };
  let havePending = false;
  let pendingGroup: string | null = null;

  const reset = () => {
    pending = { rawAttributes: {}, headers: {}, duration: -1, catchUpDays: 0 };
    havePending = false;
  };

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) continue;

    if (line.startsWith("#EXTM3U")) continue;

    if (line.startsWith("#EXTINF")) {
      const colon = line.indexOf(":");
      const comma = line.indexOf(",", colon);
      const meta = comma > -1 ? line.slice(colon + 1, comma) : line.slice(colon + 1);
      const name = comma > -1 ? line.slice(comma + 1).trim() : "";

      const durationToken = meta.split(/\s+/)[0] ?? "-1";
      const duration = Number.parseFloat(durationToken);

      const attrs = parseAttributes(meta);
      pending = {
        rawAttributes: attrs,
        headers: {},
        name,
        duration: Number.isFinite(duration) ? duration : -1,
        tvgId: attrs["tvg-id"] ?? null,
        tvgName: attrs["tvg-name"] ?? null,
        tvgLogo: attrs["tvg-logo"] ?? null,
        tvgChno: attrs["tvg-chno"] ? Number.parseInt(attrs["tvg-chno"], 10) : null,
        groupTitle: attrs["group-title"] ?? pendingGroup,
        catchUp: attrs["catchup"] ?? attrs["catchup-type"] ?? null,
        catchUpDays: attrs["catchup-days"] ? Number.parseInt(attrs["catchup-days"], 10) : 0,
        catchUpSource: attrs["catchup-source"] ?? null,
        userAgent: null,
        referrer: null,
      };
      havePending = true;
      continue;
    }

    if (line.startsWith("#EXTGRP:")) {
      const group = line.slice("#EXTGRP:".length).trim();
      pendingGroup = group;
      if (havePending && !pending.groupTitle) pending.groupTitle = group;
      continue;
    }

    if (line.startsWith("#EXTVLCOPT:") || line.startsWith("#KODIPROP:")) {
      const eq = line.indexOf("=");
      if (eq < 0) continue;
      const key = line.slice(line.indexOf(":") + 1, eq).trim().toLowerCase();
      const value = line.slice(eq + 1).trim();
      if (key === "http-user-agent") pending.userAgent = value;
      else if (key === "http-referrer") pending.referrer = value;
      else pending.headers[key] = value;
      continue;
    }

    if (line.startsWith("#")) continue;

    if (havePending) {
      entries.push({
        name: (pending.name ?? "").trim() || "(unnamed)",
        url: line,
        tvgId: pending.tvgId ?? null,
        tvgName: pending.tvgName ?? null,
        tvgLogo: pending.tvgLogo ?? null,
        tvgChno: pending.tvgChno ?? null,
        groupTitle: pending.groupTitle ?? null,
        duration: pending.duration ?? -1,
        catchUp: pending.catchUp ?? null,
        catchUpDays: pending.catchUpDays ?? 0,
        catchUpSource: pending.catchUpSource ?? null,
        userAgent: pending.userAgent ?? null,
        referrer: pending.referrer ?? null,
        headers: pending.headers ?? {},
        rawAttributes: pending.rawAttributes ?? {},
      });
      reset();
    }
  }

  return entries;
}
