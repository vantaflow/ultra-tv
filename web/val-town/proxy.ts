// Ultra TV — Val Town HTTP handler.
//
// Paste this whole file into a new HTTP val on https://www.val.town/. Val Town
// gives you a public URL of the form  https://<username>-<valname>.web.val.run
// — put that into the app under Settings → Network, or as the build-time
// VITE_DEFAULT_PROXY_URL.
//
// Same contract as the Cloudflare Worker / Vercel Edge / Deno Deploy proxies:
//
//   GET  /?target=<URL-encoded upstream>
//   Headers: X-SV-UA, X-SV-Referer (optional overrides)
//
// HLS playlists are rewritten so every segment / key URI is also routed through
// this proxy — fixes CORS, cleartext-on-HTTPS, and provider User-Agent filters.
//
// Optional: set an env var ALLOWED_HOSTS (comma-separated) in the val settings
// to restrict which upstream hostnames may be proxied.

const REALISTIC_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

const ALLOWED_HOSTS = (Deno.env.get("ALLOWED_HOSTS") ?? "")
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
  out.pathname = me.pathname;
  out.searchParams.set("target", target);
  return out.toString();
}

function rewriteM3u8(
  body: string,
  manifestUrl: string,
  request: Request,
  ua: string | null,
  referer: string | null,
): string {
  const base = new URL(manifestUrl);
  const rewriteUri = (uri: string): string => {
    if (!uri || uri.startsWith("#")) return uri;
    const abs = new URL(uri, base).toString();
    const wrapped = selfProxyUrl(request, abs);
    const u = new URL(wrapped);
    if (ua) u.searchParams.set("ua", ua);
    if (referer) u.searchParams.set("referer", referer);
    return u.toString();
  };

  return body
    .split(/\r?\n/)
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return line;
      if (trimmed.startsWith("#")) {
        return line.replace(/URI="([^"]+)"/g, (_m, uri) => `URI="${rewriteUri(uri)}"`);
      }
      return rewriteUri(trimmed);
    })
    .join("\n");
}

export default async function handler(request: Request): Promise<Response> {
  if (request.method === "OPTIONS") return preflight();

  const url = new URL(request.url);
  const target = url.searchParams.get("target");
  if (!target) {
    if (url.pathname === "/") {
      return new Response(
        "Ultra TV proxy is running. Use ?target=<url-encoded-upstream>.",
        { status: 200, headers: { "Content-Type": "text/plain" } },
      );
    }
    return jsonErr("Missing ?target=<url>", 400);
  }

  let upstream: URL;
  try {
    upstream = new URL(target);
  } catch {
    return jsonErr("Invalid target URL", 400);
  }

  if (ALLOWED_HOSTS.length > 0 && !ALLOWED_HOSTS.includes(upstream.hostname)) {
    return jsonErr(`Host ${upstream.hostname} not in ALLOWED_HOSTS`, 403);
  }

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
  responseHeaders.delete("content-encoding");
  responseHeaders.delete("content-length");

  const ct = (upstreamRes.headers.get("content-type") || "").toLowerCase();
  const pathLower = upstream.pathname.toLowerCase();
  const isM3u8 =
    ct.includes("mpegurl") ||
    ct.includes("vnd.apple") ||
    pathLower.endsWith(".m3u8") ||
    pathLower.endsWith(".m3u");

  if (isM3u8 && upstreamRes.ok) {
    const text = await upstreamRes.text();
    const rewritten = rewriteM3u8(text, upstream.toString(), request, ua, referer);
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
