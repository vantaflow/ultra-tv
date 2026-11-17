// Budget des fournisseurs de données sportives et plafond de sous-requêtes d'un appel du Worker.
//   - chaque fournisseur a un budget en jetons par minute et par jour (et, au besoin, par capacité) ;
//   - l'état des compteurs est lu UNE fois (au premier besoin amont) et écrit UNE fois au plus par appel (flush) ;
//   - il vit dans l'API Cache (par centre de données, donc approximatif : les budgets gardent une marge sous le quota
//     réel) ou, si le stockage Turso est actif, dans le KV Turso (compteur global) ;
//   - un 429 met le fournisseur en pause (« cooldown ») pour que le suivant prenne le relais ;
//   - chaque fetch, lecture ou écriture de cache compte pour une sous-requête : plafond de 50 par appel.
// Aucune clé ni URL n'est jamais journalisée ; les adaptateurs n'émettent aucun log.

export const MAX_SUBREQUESTS = 50;
const STATE_KEY = "sportsdata/budget-state";
const MIN = 60000;
const DAY = 86400000;

export function createCtx(env, { fetchFn = fetch, cache = null, now = Date.now(), registry = {} } = {}) {
  let subs = 0;
  const bump = () => { if (subs >= MAX_SUBREQUESTS) return false; subs++; return true; };
  const wrapped = cache ? {
    async match(req) { return bump() ? cache.match(req) : undefined; },
    async put(req, res) { if (bump()) await cache.put(req, res); },
  } : null;
  const useKv = (env?.STORAGE === "turso") && env?.CONFIG && typeof env.CONFIG.get === "function";

  let state = null;
  let dirty = false;
  async function load() {
    if (state) return state;
    state = { p: {} };
    try {
      if (useKv) { if (bump()) { const v = await env.CONFIG.get(STATE_KEY, "json"); if (v?.p) state = v; } }
      else if (wrapped) {
        const hit = await wrapped.match(new Request(`https://sportsdata.cache/${STATE_KEY}`));
        if (hit) { const v = await hit.json(); if (v?.p) state = v; }
      }
    } catch { /* état perdu : on repart de zéro, le quota amont reste le garde-fou final */ }
    return state;
  }
  const entry = (id) => (state.p[id] ||= { m: [0, 0], d: [0, 0], c: {}, cool: 0 });
  const dayOf = (t) => Math.floor(t / DAY);
  const minOf = (t) => Math.floor(t / MIN);
  const used = (e, t) => ({
    m: e.m[0] === minOf(t) ? e.m[1] : 0,
    d: e.d[0] === dayOf(t) ? e.d[1] : 0,
  });
  const capUsed = (e, cap, t) => (e.c[cap]?.[0] === dayOf(t) ? e.c[cap][1] : 0);

  const ctx = {
    now, env, cache: wrapped, fetchFn,
    get subrequests() { return subs; },
    /** Clé présente et budget (minute, jour, capacité) non épuisé, sans consommer de jeton. */
    async canUse(id, cap) {
      const prov = registry[id];
      if (!prov || !prov.hasKey(env)) return false;
      await load();
      const e = entry(id);
      if (e.cool > now) return false;
      const b = prov.budget(env);
      const u = used(e, now);
      if (b.perMin && u.m + 1 > b.perMin) return false;
      if (b.perDay && u.d + 1 > b.perDay) return false;
      const cb = b.perCapDay?.[cap];
      if (cb && capUsed(e, cap, now) + 1 > cb) return false;
      return true;
    },
    /** Part restante (0..1) du budget journalier de la capacité : réserve API-Football aux besoins utiles. */
    async headroom(id, cap) {
      const prov = registry[id];
      if (!prov) return 0;
      await load();
      const b = prov.budget(env);
      const lim = b.perCapDay?.[cap] || b.perDay;
      if (!lim) return 1;
      const e = entry(id);
      const u = b.perCapDay?.[cap] ? capUsed(e, cap, now) : used(e, now).d;
      return Math.max(0, (lim - u) / lim);
    },
    /** Consomme un jeton ; false si refusé (budget, pause ou plafond de sous-requêtes). */
    async take(id, cap) {
      if (!(await ctx.canUse(id, cap))) return false;
      if (!bump()) return false;
      const e = entry(id);
      const u = used(e, now);
      e.m = [minOf(now), u.m + 1];
      e.d = [dayOf(now), u.d + 1];
      e.c[cap] = [dayOf(now), capUsed(e, cap, now) + 1];
      dirty = true;
      return true;
    },
    /** Met un fournisseur en pause (429 ou quota atteint). */
    async cooldown(id, ms) { await load(); entry(id).cool = now + ms; dirty = true; },
    /** JSON de l'amont (budgeté) ou null : refus de budget, 429, erreur HTTP, corps illisible ou erreur applicative. */
    async fetchJson(id, cap, url, headers = {}) {
      if (!(await ctx.take(id, cap))) return null;
      let r;
      try { r = await fetchFn(url, { headers: { accept: "application/json", ...headers } }); } catch { return null; }
      if (r.status === 429) { await ctx.cooldown(id, MIN); return null; }
      if (r.status !== 200) return null;
      let body;
      try { body = await r.json(); } catch { return null; }
      const verdict = registry[id]?.failed?.(body);
      if (verdict) { await ctx.cooldown(id, verdict === "quota" ? DAY / 4 : MIN); return null; }
      return body;
    },
    /** Écriture groupée des compteurs : au plus une par appel du Worker. */
    async flush() {
      if (!dirty || !state) return;
      dirty = false;
      try {
        if (useKv) { if (bump()) await env.CONFIG.put(STATE_KEY, JSON.stringify(state), { expirationTtl: 2 * 86400 }); }
        else if (wrapped) {
          await wrapped.put(new Request(`https://sportsdata.cache/${STATE_KEY}`), new Response(JSON.stringify(state), { headers: { "content-type": "application/json", "cache-control": "public, max-age=86400" } }));
        }
      } catch { /* sans conséquence : compteurs approximatifs */ }
    },
  };
  return ctx;
}
