// Ultra TV Web — Cloudflare Worker proxy (version web historique).
//
// Résout : CORS, User-Agent imposé par certains serveurs Xtream/M3U, contenu mixte HTTPS→HTTP.
//
// Un proxy ouvert est un relais d'abus (SSRF, contournement de filtrage, DDoS
// par rebond). Garde-fous, tous actifs par défaut :
//   - ALLOWED_HOSTS OBLIGATOIRE (liste de noms d'hôte séparés par des virgules) :
//     vide ou absent = tout est refusé (403). Comparaison insensible à la casse.
//   - schémas http: / https: uniquement, pas d'identifiants dans l'URL ;
//   - IP littérales privées, loopback, link-local (169.254.169.254), CGNAT,
//     multicast, `localhost` et `*.internal`/`*.local` refusés MÊME s'ils sont
//     dans la liste blanche ;
//   - redirections suivies à la main, 3 au maximum, chaque saut REVALIDÉ
//     (sinon un hôte autorisé pourrait rediriger vers une cible interdite) ;
//   - méthodes GET et HEAD uniquement ;
//   - CORS : ALLOWED_ORIGINS (liste d'origines exactes). Absent = aucun en-tête
//     CORS (le navigateur bloque), jamais « * ».
//
// Usage : GET /?target=<URL encodée>  (+ X-SV-UA / X-SV-Referer optionnels)

const REALISTIC_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
const MAX_REDIRECTS = 3;

const csv = (v) => (v || "").split(",").map((s) => s.trim().toLowerCase()).filter(Boolean);

/** Vrai pour toute IP littérale non routable publiquement (IPv4 et IPv6). */
export function isPrivateHost(hostname) {
  const h = hostname.toLowerCase().replace(/^\[|\]$/g, "");
  if (h === "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) return true;
  const v4 = h.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (v4) {
    const [a, b] = [Number(v4[1]), Number(v4[2])];
    return a === 0 || a === 10 || a === 127 || (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254)
      || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || (a === 192 && b === 0)
      || (a === 198 && (b === 18 || b === 19)) || a >= 224;
  }
  if (h.includes(":")) { // IPv6
    if (h === "::" || h === "::1") return true;
    if (/^f[cd]/.test(h) || /^fe[89ab]/.test(h) || /^ff/.test(h)) return true; // ULA, link-local, multicast
    const mapped = h.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
    if (mapped) return isPrivateHost(mapped[1]);
    if (/^::ffff:[0-9a-f]+:[0-9a-f]+$/.test(h)) return true; // IPv4-mapped en hexadécimal : refusé par prudence
  }
  // Formes numériques exotiques (entier, hex, octal) que certains résolveurs acceptent.
  if (/^(0x[0-9a-f]+|\d+)$/.test(h)) return true;
  return false;
}

/**
 * Valide une URL cible. Renvoie {url} ou {status, error}.
 */
export function validateTarget(target, env) {
  let u;
  try { u = new URL(target); } catch { return { status: 400, error: "Invalid target URL" }; }
  if (u.protocol !== "http:" && u.protocol !== "https:") return { status: 400, error: "Only http(s) targets are allowed" };
  if (u.username || u.password) return { status: 400, error: "Credentials in the URL are not allowed" };
  const allowed = csv(env.ALLOWED_HOSTS);
  if (allowed.length === 0) {
    return { status: 403, error: "Proxy not configured: ALLOWED_HOSTS is unset. The operator must set it before any request is forwarded." };
  }
  if (isPrivateHost(u.hostname)) return { status: 403, error: "Private or local addresses are not allowed" };
  if (!allowed.includes(u.hostname.toLowerCase())) return { status: 403, error: `Host ${u.hostname} not in ALLOWED_HOSTS` };
  return { url: u };
}

function corsHeaders(request, env) {
  const origins = csv(env.ALLOWED_ORIGINS);
  const origin = (request.headers.get("origin") || "").toLowerCase();
  const h = new Headers();
  if (origin && origins.includes(origin)) {
    h.set("Access-Control-Allow-Origin", request.headers.get("origin"));
    h.set("Vary", "Origin");
    h.set("Access-Control-Allow-Methods", "GET,HEAD,OPTIONS");
    h.set("Access-Control-Allow-Headers", "Content-Type,Range,X-SV-UA,X-SV-Referer");
    h.set("Access-Control-Expose-Headers", "Content-Length,Content-Range,Content-Type,X-SV-Diagnostic");
  }
  return h;
}

function json(request, env, obj, status) {
  const h = corsHeaders(request, env);
  h.set("Content-Type", "application/json");
  h.set("X-Content-Type-Options", "nosniff");
  return new Response(JSON.stringify(obj), { status, headers: h });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders(request, env) });
    if (request.method !== "GET" && request.method !== "HEAD") {
      return json(request, env, { error: "Method not allowed" }, 405);
    }

    const target = new URL(request.url).searchParams.get("target");
    if (!target) return json(request, env, { error: "Missing ?target=<url>" }, 400);

    let check = validateTarget(target, env);
    if (check.error) return json(request, env, { error: check.error }, check.status);

    const headers = new Headers();
    headers.set("User-Agent", (request.headers.get("x-sv-ua") || REALISTIC_UA).slice(0, 256));
    headers.set("Accept", "*/*");
    headers.set("Accept-Language", "en-US,en;q=0.9");
    const referer = request.headers.get("x-sv-referer");
    if (referer) headers.set("Referer", referer.slice(0, 512));
    const range = request.headers.get("range");
    if (range) headers.set("Range", range.slice(0, 128));

    let upstreamRes;
    let current = check.url;
    try {
      for (let hop = 0; ; hop++) {
        upstreamRes = await fetch(current.toString(), { method: request.method, headers, redirect: "manual", cf: { cacheTtl: 0 } });
        const loc = upstreamRes.headers.get("location");
        if (upstreamRes.status < 300 || upstreamRes.status >= 400 || !loc) break;
        if (hop >= MAX_REDIRECTS) return json(request, env, { error: "Too many redirects" }, 508);
        const next = validateTarget(new URL(loc, current).toString(), env); // chaque saut est revalidé
        if (next.error) return json(request, env, { error: `Redirect refused: ${next.error}` }, 403);
        current = next.url;
      }
    } catch (e) {
      return json(request, env, { error: `Upstream fetch failed: ${e.message || e}` }, 502);
    }

    const out = new Headers(upstreamRes.headers);
    out.delete("set-cookie");
    out.delete("location");
    for (const [k, v] of corsHeaders(request, env)) out.set(k, v);
    out.set("X-Content-Type-Options", "nosniff");
    if (upstreamRes.status === 403 && upstreamRes.headers.get("cf-ray")) {
      out.set("X-SV-Diagnostic", "Upstream appears to be CF-protected and is rejecting Worker traffic (CF→CF anti-loop). Try a non-CF proxy.");
    }
    return new Response(upstreamRes.body, { status: upstreamRes.status, statusText: upstreamRes.statusText, headers: out });
  },
};
