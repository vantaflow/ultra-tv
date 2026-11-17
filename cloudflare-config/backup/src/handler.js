// Gestionnaire du serveur de secours : liste blanche de routes en lecture, contrôle de configuration, puis appel du Worker
// (le même code que sur Cloudflare) avec l'environnement de lecture seule fabriqué par ./env.js.

import { buildEnv, missingVars } from "./env.js";

const JSON_HEADERS = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-utv-backup": "1" };
const reply = (body, status = 503, extra = {}) => new Response(JSON.stringify(body), { status, headers: { ...JSON_HEADERS, ...extra } });

/** Classe une requête : "config", "announcements", "state" (lecture seule), "health", ou null (non doublée). */
export function classify(method, pathname) {
  const m = method.toUpperCase();
  if ((m === "GET" || m === "HEAD") && pathname === "/api/backup/health") return "health";
  if (m === "GET" && pathname === "/api/config") return "config";
  if (m === "GET" && pathname === "/api/announcements") return "announcements";
  if (m === "POST" && /^\/api\/device\/providers\/[0-9a-f]{8}\/state$/.test(pathname)) return "state";
  return null;
}

/** IP du client telle que Vercel la voit, présentée sous l'en-tête que lit le Worker (cf-connecting-ip). */
export function withClientIp(headers) {
  const h = new Headers(headers);
  const ip = (h.get("x-real-ip") || (h.get("x-forwarded-for") || "").split(",")[0] || "").trim();
  h.delete("cf-connecting-ip");
  if (ip) h.set("cf-connecting-ip", ip);
  return h;
}

/** Un corps d'état est une lecture seule quand il ne porte aucun favori ni historique à écrire. */
export function isStateRead(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const empty = (x) => x === undefined || (Array.isArray(x) && x.length === 0);
  return empty(value.fav) && empty(value.hist);
}

export function createHandler({ worker, build = buildEnv } = {}) {
  return async function handle(req, processEnv = {}) {
    const url = new URL(req.url);
    const kind = classify(req.method, url.pathname);
    const missing = missingVars(processEnv);

    if (kind === "health") return reply({ ok: true, configured: missing.length === 0, readOnly: true }, 200);
    if (missing.length) return reply({ error: "backup_not_configured", missing });
    if (!kind) return reply({ error: "backup_unavailable", message: "Cette route n'est pas servie par le serveur de secours." });

    let body;
    if (kind === "state") {
      const text = await req.text();
      let parsed = null;
      try { parsed = text ? JSON.parse(text) : {}; } catch { parsed = null; }
      if (!isStateRead(parsed)) return reply({ error: "backup_read_only", message: "Écriture impossible pendant le secours." }, 503, { "retry-after": "60" });
      body = text;
    }

    const env = build(processEnv);
    if (!env) return reply({ error: "backup_not_configured", missing });
    const inner = new Request(url, { method: req.method, headers: withClientIp(req.headers), body });
    const ctx = { waitUntil() {}, passThroughOnException() {} };
    const res = await worker.fetch(inner, env, ctx);
    const out = new Response(res.body, res);
    out.headers.set("x-utv-backup", "1");
    return out;
  };
}
