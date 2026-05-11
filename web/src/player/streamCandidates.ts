// Build a list of stream URL candidates ordered by likely browser playability.
//
// Most Xtream servers expose the same VOD/episode under multiple container
// extensions (`.mp4`, `.mkv`, `.ts`, …). Browsers play .mp4 best, so we prefer
// that one even if the catalog reports a different container. If the primary
// fails the caller can iterate over the rest.

const BROWSER_FRIENDLY = ["mp4", "m4v"];
const COMMON_VOD = ["mp4", "mkv", "ts", "avi", "mov", "m4v"];
const COMMON_LIVE = ["m3u8", "ts", "mp4"];

interface Options {
  url: string;
  containerExtension?: string | null;
  kind?: "VOD" | "LIVE";
}

export function buildStreamCandidates({ url, containerExtension, kind = "VOD" }: Options): string[] {
  if (!url) return [];
  // Strip query and hash to find the extension
  const [base, q = ""] = url.split("?", 2);
  const m = base?.match(/^(.*)\.([a-zA-Z0-9]+)$/);
  if (!m) return [url];

  const stem = m[1]!;
  const ext = (m[2] ?? "").toLowerCase();
  const query = q ? `?${q}` : "";

  // Anchor list of extensions to try, in priority order
  const order = kind === "LIVE" ? COMMON_LIVE : [...BROWSER_FRIENDLY, ext, ...COMMON_VOD];

  const seen = new Set<string>();
  const out: string[] = [];

  // If the catalog declares a specific container, prefer mp4 first if different
  const preferred = (containerExtension ?? ext).toLowerCase();
  const ordered = preferred === "mp4" ? [ext, ...order] : [...BROWSER_FRIENDLY, preferred, ext, ...order];

  for (const e of ordered) {
    if (!e || seen.has(e)) continue;
    seen.add(e);
    out.push(`${stem}.${e}${query}`);
  }

  return out;
}
