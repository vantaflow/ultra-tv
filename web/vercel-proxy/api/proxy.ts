// Ultra TV Web — Vercel Edge Function proxy.
//
// Sits on Vercel's network (not Cloudflare) so it can talk to CF-protected IPTV
// upstreams without triggering the CF→CF anti-loop 403/code:1003.
//
// Contract: GET /?target=<url-encoded>  with optional X-SV-UA / X-SV-Referer headers.
//
// For .m3u8 (HLS) responses we rewrite every URI in the playlist to also flow
// through this proxy — otherwise relative segment URIs would be resolved against
// the proxy origin and break, and absolute http:// URIs would be blocked by
// mixed-content from an https:// page.

export const config = { runtime: "edge" };

const REALISTIC_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

const ALLOWED_HOSTS = ((globalThis as unknown as { process?: { env?: Record<string, string | undefined> } }).process?.env?.ALLOWED_HOSTS ?? "")
  .split(",")
  .map((h) => h.trim())
  .filter(Boolean);

function applyCors(h: Headers) {
  h.set("Access-Control-Allow-Origin", "*");
  h.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
  h.set("Access-Control-Allow-Headers", "Content-Type,Range,X-SV-UA,X-SV-Referer");
  h.set(
    "Access-Control-Expose-Headers",
    "Content-Length,Content-Range,Content-Type,X-SV-Diagnostic",
  );
}

function preflight(): Response {
  const h = new Headers();
  applyCors(h);
  return new Response(null, { status: 204, headers: h });
}

function jsonErr(message: string, status = 400): Response {
  const h = new Headers({ "Content-Type": "application/json" });
  applyCors(h);
  return new Response(JSON.stringify({ error: message }), { status, headers: h });
}

function selfProxyUrl(request: Request, target: string): string {
  const me = new URL(request.url);
  const out = new URL(me.origin);
  out.pathname = me.pathname; // keep the same /api/proxy entry
  out.searchParams.set("target", target);
  return out.toString();
}

function rewriteM3u8(body: string, manifestUrl: string, request: Request, ua: string | null, referer: string | null): string {
  const base = new URL(manifestUrl);
  const rewriteUri = (uri: string): string => {
    if (!uri || uri.startsWith("#")) return uri;
    // Resolve relative to the manifest's URL, then wrap via our proxy
    const abs = new URL(uri, base).toString();
    const wrapped = selfProxyUrl(request, abs);
    // Carry UA/Referer in query for media segments since the player can't set headers cross-origin
    const u = new URL(wrapped);
    if (ua) u.searchParams.set("ua", ua);
    if (referer) u.searchParams.set("referer", referer);
    return u.toString();
  };

  const lines = body.split(/\r?\n/);
  const out: string[] = [];
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) { out.push(line); continue; }
    if (trimmed.startsWith("#")) {
      // Rewrite URI="..." occurrences inside #EXT-X-KEY, #EXT-X-MEDIA, #EXT-X-MAP, etc.
      const rewritten = line.replace(/URI="([^"]+)"/g, (_m, uri) => `URI="${rewriteUri(uri)}"`);
      out.push(rewritten);
      continue;
    }
    out.push(rewriteUri(trimmed));
  }
  return out.join("\n");
}

export default async function handler(request: Request): Promise<Response> {
  if (request.method === "OPTIONS") return preflight();

  const url = new URL(request.url);
  const target = url.searchParams.get("target");
  if (!target) return jsonErr("Missing ?target=<url>", 400);

  let upstream: URL;
  try {
    upstream = new URL(target);
  } catch {
    return jsonErr("Invalid target URL", 400);
  }

  if (ALLOWED_HOSTS.length > 0 && !ALLOWED_HOSTS.includes(upstream.hostname)) {
    return jsonErr(`Host ${upstream.hostname} not in ALLOWED_HOSTS`, 403);
  }

  // UA/Referer can come from headers (preferred) or query params (used inside
  // rewritten m3u8 URIs because hls.js can't always set custom headers).
  const ua = request.headers.get("x-sv-ua") || url.searchParams.get("ua") || REALISTIC_UA;
  const referer = request.headers.get("x-sv-referer") || url.searchParams.get("referer");

  const headers = new Headers();
  headers.set("User-Agent", ua);
  headers.set("Accept", "*/*");
  headers.set("Accept-Language", "en-US,en;q=0.9");
  headers.set("Accept-Encoding", "gzip, deflate, br");
  if (referer) headers.set("Referer", referer);
  const range = request.headers.get("range");
  if (range) headers.set("Range", range);

  let body: ArrayBuffer | undefined = undefined;
  if (request.method !== "GET" && request.method !== "HEAD") {
    body = await request.arrayBuffer();
  }

  let upstreamRes: Response;
  try {
    upstreamRes = await fetch(upstream.toString(), {
      method: request.method,
      headers,
      body,
      redirect: "follow",
    });
  } catch (e) {
    return jsonErr(`Upstream fetch failed: ${(e as Error).message ?? e}`, 502);
  }

  const responseHeaders = new Headers(upstreamRes.headers);
  applyCors(responseHeaders);
  responseHeaders.delete("set-cookie");
  responseHeaders.delete("content-encoding"); // we may rewrite the body, drop stale encoding
  responseHeaders.delete("content-length");

  // Detect HLS playlists and rewrite URIs through the proxy
  const ct = (upstreamRes.headers.get("content-type") || "").toLowerCase();
  const pathLower = upstream.pathname.toLowerCase();
  const isM3u8 = ct.includes("mpegurl") || ct.includes("vnd.apple") || pathLower.endsWith(".m3u8") || pathLower.endsWith(".m3u");

  if (isM3u8 && upstreamRes.ok) {
    const text = await upstreamRes.text();
    const rewritten = rewriteM3u8(text, upstream.toString(), request, request.headers.get("x-sv-ua") ?? url.searchParams.get("ua"), referer);
    responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl");
    return new Response(rewritten, {
      status: upstreamRes.status,
      statusText: upstreamRes.statusText,
      headers: responseHeaders,
    });
  }

  return new Response(upstreamRes.body, {
    status: upstreamRes.status,
    statusText: upstreamRes.statusText,
    headers: responseHeaders,
  });
}
