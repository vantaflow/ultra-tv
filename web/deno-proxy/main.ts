// Ultra TV Web — Deno Deploy proxy.
//
// Same contract as the Cloudflare Worker (cloudflare/worker.js) but hosted on Deno
// Deploy, which sits outside the Cloudflare network. Useful when the upstream IPTV
// server is itself CF-protected and rejects Worker-originated traffic (CF→CF
// anti-loop returning 403 + code:1003).
//
// Endpoints:
//   GET  /                       → tiny status page
//   *    /?target=<URL-encoded>  → forwards method/body/range; injects UA/Referer
//
// Headers: X-SV-UA / X-SV-Referer override the upstream User-Agent / Referer.
// Optional env ALLOWED_HOSTS (comma-separated) restricts the upstream hostnames.

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

function json(obj: unknown, status = 200): Response {
  const h = new Headers({ "Content-Type": "application/json" });
  applyCors(h);
  return new Response(JSON.stringify(obj), { status, headers: h });
}

Deno.serve(async (request) => {
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
    return json({ error: "Missing ?target=<url>" }, 400);
  }

  let upstream: URL;
  try {
    upstream = new URL(target);
  } catch {
    return json({ error: "Invalid target URL" }, 400);
  }

  if (ALLOWED_HOSTS.length > 0 && !ALLOWED_HOSTS.includes(upstream.hostname)) {
    return json({ error: `Host ${upstream.hostname} not in ALLOWED_HOSTS` }, 403);
  }

  const ua = request.headers.get("x-sv-ua") || REALISTIC_UA;
  const referer = request.headers.get("x-sv-referer");

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
    return json({ error: `Upstream fetch failed: ${(e as Error).message ?? e}` }, 502);
  }

  const responseHeaders = new Headers(upstreamRes.headers);
  applyCors(responseHeaders);
  responseHeaders.delete("set-cookie");

  return new Response(upstreamRes.body, {
    status: upstreamRes.status,
    statusText: upstreamRes.statusText,
    headers: responseHeaders,
  });
});
