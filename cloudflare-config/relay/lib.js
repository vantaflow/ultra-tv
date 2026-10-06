// Relais d'abonnement Xtream, hébergé sur Vercel (hors Cloudflare).
// Certains fournisseurs refusent toute requête venant des serveurs Cloudflare : le Worker de configuration délègue alors
// la lecture de `player_api.php` à ce relais. Volontairement minimal : une seule requête, vers un seul chemin
// (`/player_api.php`), sans suivre les redirections (le Worker les suit et re-valide chaque saut), réservé au Worker
// (clé partagée RELAY_KEY). Ce n'est PAS un proxy ouvert. Rien n'est journalisé ni conservé (identifiants dans l'URL).

const MAX_BODY = 512 * 1024;
const TIMEOUT_MS = 8_000;

/** Adresse acceptée : http(s), chemin `/player_api.php`, pas d'hôte local / privé écrit en clair. null sinon. */
export function allowedTarget(raw) {
  let u;
  try { u = new URL(raw); } catch { return null; }
  if (u.protocol !== "http:" && u.protocol !== "https:") return null;
  if (u.username || u.password) return null;
  if (!/\/player_api\.php$/i.test(u.pathname)) return null;
  const h = u.hostname.replace(/^\[|\]$/g, "").toLowerCase();
  if (h === "localhost" || h.endsWith(".localhost") || h.endsWith(".internal") || h.endsWith(".local")) return null;
  const v4 = h.match(/^(\d+)\.(\d+)\.(\d+)\.(\d+)$/);
  if (v4) {
    const a = Number(v4[1]), b = Number(v4[2]);
    if (a === 0 || a === 10 || a === 127 || a >= 224 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31)
      || (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127)) return null;
  }
  if (h.includes(":") && (h === "::1" || h === "::" || h.startsWith("::ffff:") || /^(fc|fd|fe[89ab])/.test(h))) return null;
  return u;
}

function safeEqual(a, b) {
  const ea = new TextEncoder().encode(a), eb = new TextEncoder().encode(b);
  let diff = ea.length ^ eb.length;
  for (let i = 0; i < Math.max(ea.length, eb.length); i++) diff |= (ea[i] ?? 0) ^ (eb[i] ?? 0);
  return diff === 0;
}

const json = (v, status = 200) =>
  new Response(JSON.stringify(v), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });

/** POST { url, ua? } avec l'en-tête x-relay-key → { status, location, body } ou { error }. */
export async function handle(req, key, fetchImpl = fetch) {
  if (!key || key.length < 32) return json({ error: "relay-not-configured" }, 503);
  if (req.method !== "POST") return json({ error: "not-found" }, 404);
  if (!safeEqual(req.headers.get("x-relay-key") ?? "", key)) return json({ error: "forbidden" }, 403);
  let body;
  try { body = await req.json(); } catch { return json({ error: "bad-request" }, 400); }
  const target = body && typeof body.url === "string" ? allowedTarget(body.url) : null;
  if (!target) return json({ error: "bad-target" }, 400);
  const ua = typeof body.ua === "string" && body.ua.length <= 100 ? body.ua : "UltraTV/1.0";
  try {
    const res = await fetchImpl(target.toString(), { headers: { "user-agent": ua, accept: "*/*" }, redirect: "manual", signal: AbortSignal.timeout(TIMEOUT_MS) });
    const buf = new Uint8Array(await res.arrayBuffer());
    return json({ status: res.status, location: res.headers.get("location"), body: new TextDecoder().decode(buf.subarray(0, MAX_BODY)) });
  } catch (e) {
    return json({ error: e && (e.name === "TimeoutError" || e.name === "AbortError") ? "timeout" : "network" });
  }
}
