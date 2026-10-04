// Proxy OpenSubtitles : la clé d'API reste dans le secret Wrangler OPENSUBTITLES_API_KEY, jamais dans l'application.
//   GET /api/subtitles/search?query=<titre>&languages=fr,en[&year=2020] -> {"results":[{id,language,release,downloads}]}
//   GET /api/subtitles/download?id=<id>                                  -> le fichier SRT (UTF-8)
// Secret absent -> 503 propre (l'application désactive l'option). Paramètres filtrés, hôte de téléchargement en liste blanche.

const API = "https://api.opensubtitles.com/api/v1/";
const USER_AGENT = "UltraTV v1.1";
const FILE_ID = /^[1-9][0-9]{0,11}$/;
const LANG = /^[A-Za-z]{2,3}(-[A-Za-z]{2,4})?$/;
const YEAR = /^(19|20)[0-9]{2}$/;
const DOWNLOAD_HOSTS = /^([a-z0-9-]+\.)*opensubtitles\.(com|org)$/;

export const SEARCH_TTL_S = 24 * 3600;
export const DOWNLOAD_TTL_S = 7 * 24 * 3600;
export const MAX_SRT_BYTES = 2 * 1024 * 1024;
const MAX_RESULTS = 20;

const jsonRes = (obj, status = 200, extra = {}) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json", "cache-control": "no-store", ...extra } });

/** Paramètres de recherche normalisés, ou null si invalides. */
export function searchTarget(searchParams) {
  const query = (searchParams.get("query") ?? "").trim();
  if (!query || query.length > 120) return null;
  const params = new URLSearchParams();
  params.set("query", query.toLowerCase());
  const langs = searchParams.get("languages");
  if (langs !== null) {
    const parts = langs.split(",");
    if (parts.length === 0 || parts.length > 5 || !parts.every((l) => LANG.test(l))) return null;
    params.set("languages", [...new Set(parts.map((l) => l.toLowerCase()))].sort().join(","));
  }
  const year = searchParams.get("year");
  if (year !== null) {
    if (!YEAR.test(year)) return null;
    params.set("year", year);
  }
  params.sort();
  return params;
}

const cacheKey = (kind, key) => new Request(`https://subtitles-cache.invalid/${kind}/${key}`);

function notConfigured() { return jsonRes({ error: "subtitles_not_configured" }, 503); }

export async function subtitlesSearch(searchParams, env, { fetchFn = fetch, cache = globalThis.caches?.default } = {}) {
  if (!env.OPENSUBTITLES_API_KEY) return notConfigured();
  const params = searchTarget(searchParams);
  if (!params) return jsonRes({ error: "bad_request" }, 400);
  const key = cacheKey("search", params.toString());
  const hit = cache ? await cache.match(key) : undefined;
  if (hit) return hit;
  const up = new URL(API + "subtitles");
  for (const [k, v] of params) up.searchParams.set(k, v);
  let r;
  try {
    r = await fetchFn(up.toString(), { headers: { "api-key": env.OPENSUBTITLES_API_KEY, "user-agent": USER_AGENT, accept: "application/json" }, redirect: "manual" });
  } catch { return jsonRes({ error: "upstream" }, 502); }
  if (r.status !== 200) return jsonRes({ error: "upstream" }, 502);
  let data;
  try { data = (await r.json()).data; } catch { return jsonRes({ error: "upstream" }, 502); }
  const results = (Array.isArray(data) ? data : []).flatMap((d) => {
    const a = d?.attributes ?? {};
    const id = String(a.files?.[0]?.file_id ?? "");
    if (!FILE_ID.test(id)) return [];
    return [{ id, language: String(a.language ?? "").slice(0, 8), release: String(a.release ?? a.files?.[0]?.file_name ?? "").slice(0, 160), downloads: Number.isFinite(a.download_count) ? a.download_count : 0 }];
  }).slice(0, MAX_RESULTS);
  const res = jsonRes({ results }, 200, { "cache-control": `public, max-age=${SEARCH_TTL_S}` });
  if (cache) await cache.put(key, res.clone());
  return res;
}

export async function subtitlesDownload(searchParams, env, { fetchFn = fetch, cache = globalThis.caches?.default } = {}) {
  if (!env.OPENSUBTITLES_API_KEY) return notConfigured();
  const id = searchParams.get("id") ?? "";
  if (!FILE_ID.test(id)) return jsonRes({ error: "bad_request" }, 400);
  const key = cacheKey("download", id);
  const hit = cache ? await cache.match(key) : undefined;
  if (hit) return hit;
  let link;
  try {
    const r = await fetchFn(API + "download", {
      method: "POST", redirect: "manual",
      headers: { "api-key": env.OPENSUBTITLES_API_KEY, "user-agent": USER_AGENT, "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify({ file_id: Number(id), sub_format: "srt" }),
    });
    if (r.status === 429 || r.status === 406) return jsonRes({ error: "quota" }, 429);
    if (r.status !== 200) return jsonRes({ error: "upstream" }, 502);
    link = (await r.json()).link;
  } catch { return jsonRes({ error: "upstream" }, 502); }
  let u;
  try { u = new URL(link); } catch { return jsonRes({ error: "upstream" }, 502); }
  if (u.protocol !== "https:" || !DOWNLOAD_HOSTS.test(u.hostname)) return jsonRes({ error: "upstream" }, 502);
  let f;
  try { f = await fetchFn(u.toString(), { headers: { "user-agent": USER_AGENT }, redirect: "manual" }); } catch { return jsonRes({ error: "upstream" }, 502); }
  if (f.status !== 200) return jsonRes({ error: "upstream" }, 502);
  const buf = await f.arrayBuffer();
  if (buf.byteLength === 0 || buf.byteLength > MAX_SRT_BYTES) return jsonRes({ error: "upstream" }, 502);
  const res = new Response(buf, { status: 200, headers: { "content-type": "application/x-subrip; charset=utf-8", "cache-control": `public, max-age=${DOWNLOAD_TTL_S}`, "x-content-type-options": "nosniff" } });
  if (cache) await cache.put(key, res.clone());
  return res;
}
