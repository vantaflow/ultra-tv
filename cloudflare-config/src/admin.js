// Logique pure de l'administration (aucune E/S) : paramètres de liste, pagination, tri, filtres, requêtes SQL
// paramétrées. Testée telle quelle ; le rendu est dans adminui.js, l'accès aux données dans telemetry.js / index.js.

export const DAY_MS = 86_400_000;
export const PAGE_SIZE = 50;
export const MAX_PAGE_SIZE = 200;

/** Périodes proposées par les filtres : clé d'URL → durée en millisecondes (« all » : pas de filtre). */
export const PERIODS = { "24h": DAY_MS, "7d": 7 * DAY_MS, "30d": 30 * DAY_MS, "90d": 90 * DAY_MS };
export const PERIOD_LABELS = { "24h": "24 dernières heures", "7d": "7 derniers jours", "30d": "30 derniers jours", "90d": "90 derniers jours" };

/** Période de l'URL (clé valide) ou chaîne vide. */
export const periodOf = (raw) => (Object.hasOwn(PERIODS, raw) ? raw : "");

/** Valeur de filtre texte : nettoyée (caractères de contrôle retirés) et limitée à 64 caractères. */
// eslint-disable-next-line no-control-regex
const cleanText = (v) => String(v ?? "").replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 64);

/**
 * Paramètres d'une liste à partir de l'URL : page (≥ 1), taille, tri (liste blanche `sorts`), sens, recherche `q`,
 * et filtres nommés (`filters` : ["platform", "version", ...]) ; `p` = période.
 */
export function parseListParams(sp, { sorts, sort, dir = "desc", filters = [], size = PAGE_SIZE } = {}) {
  const page = Math.max(1, Math.min(100_000, parseInt(sp.get("page") || "1", 10) || 1));
  const sz = Math.max(10, Math.min(MAX_PAGE_SIZE, parseInt(sp.get("size") || String(size), 10) || size));
  const s = sp.get("sort");
  const sortKey = s && Object.hasOwn(sorts, s) ? s : sort;
  const d = sp.get("dir");
  const sortDir = d === "asc" || d === "desc" ? d : dir;
  const f = {};
  for (const k of filters) f[k] = cleanText(sp.get(k));
  return { page, size: sz, sort: sortKey, dir: sortDir, q: cleanText(sp.get("q")), p: periodOf(sp.get("p")), f };
}

/** Découpe [total] éléments : page effective (bornée), nombre de pages, décalage SQL / tranche. */
export function paginate(total, page, size = PAGE_SIZE) {
  const pages = Math.max(1, Math.ceil(total / size));
  const cur = Math.max(1, Math.min(pages, page));
  return { total, page: cur, pages, size, offset: (cur - 1) * size, from: total ? (cur - 1) * size + 1 : 0, to: Math.min(total, cur * size) };
}

/** Tri en mémoire (stable) : [getters] associe une clé de tri à une fonction de valeur ; vides toujours en dernier. */
export function sortRows(rows, key, dir, getters) {
  const get = getters[key];
  if (!get) return [...rows];
  const mul = dir === "asc" ? 1 : -1;
  const isEmpty = (v) => v === "" || v == null || v === 0;
  return rows.map((r, i) => [r, i]).sort(([a, i], [b, j]) => {
    const x = get(a), y = get(b);
    if (isEmpty(x) !== isEmpty(y)) return isEmpty(x) ? 1 : -1;
    const c = typeof x === "number" && typeof y === "number" ? x - y : String(x).localeCompare(String(y), "fr", { numeric: true, sensitivity: "base" });
    return c ? c * mul : i - j;
  }).map(([r]) => r);
}

/** Part du total en pourcentage entier. */
export const pct = (k, n) => (n ? Math.round((100 * k) / n) : 0);

/** Garde les [n] premières entrées triées par valeur décroissante et regroupe le reste dans « autres ». */
export function topWithOthers(pairs, n = 10, otherKey = "autres") {
  const sorted = [...pairs].sort((a, b) => b[1] - a[1]);
  if (sorted.length <= n) return sorted;
  const rest = sorted.slice(n).reduce((t, p) => t + p[1], 0);
  return [...sorted.slice(0, n), [otherKey, rest]];
}

/** Compte les occurrences non vides : [[valeur, n]] triées par n décroissant. */
export function countBy(values) {
  const m = new Map();
  for (const v of values) if (v) m.set(v, (m.get(v) || 0) + 1);
  return [...m].sort((a, b) => b[1] - a[1]);
}

/** Fusionne des listes [[clé, n]] en additionnant les clés communes. */
export function mergeCounts(...lists) {
  const m = new Map();
  for (const l of lists) for (const [k, n] of l) m.set(k, (m.get(k) || 0) + n);
  return [...m].sort((a, b) => b[1] - a[1]);
}

// ---- requêtes SQL (D1) : fragments WHERE paramétrés, jamais de valeur concaténée ----------

const likeArg = (q) => `%${q.replace(/[\\%_]/g, (c) => "\\" + c)}%`;

/** Colonnes triables des installations sans compte (clé d'URL → colonne SQL). */
export const ANON_SORTS = { platform: "platform", version: "version", country: "country", model: "model", first: "first_seen", last: "last_seen", web: "web" };

/**
 * Installations sans compte. Filtres : platform (« unknown » = non déclarée), version, country, période (dernier
 * relevé), recherche libre (plateforme, modèle, version, pays).
 */
export function anonQuery(params, now = Date.now()) {
  const cond = [], args = [];
  const { q, p, f } = params;
  if (f.platform === "unknown") cond.push("platform IS NULL");
  else if (f.platform) { cond.push("platform = ?"); args.push(f.platform); }
  if (f.version) { cond.push("version = ?"); args.push(f.version); }
  if (f.country) { cond.push("country = ?"); args.push(f.country.toUpperCase()); }
  if (p) { cond.push("last_seen >= ?"); args.push(now - PERIODS[p]); }
  if (q) {
    cond.push("(platform LIKE ? ESCAPE '\\' OR model LIKE ? ESCAPE '\\' OR version LIKE ? ESCAPE '\\' OR country LIKE ? ESCAPE '\\')");
    const l = likeArg(q);
    args.push(l, l, l, l);
  }
  const col = ANON_SORTS[params.sort] || "last_seen";
  return { where: cond.length ? `WHERE ${cond.join(" AND ")}` : "", args, order: `${col} ${params.dir === "asc" ? "ASC" : "DESC"}, fingerprint ASC` };
}

/** Colonnes triables des journaux / plantages. */
export const EVENT_SORTS = { ts: "ts", version: "version_code", device: "device", country: "country", level: "level", tag: "tag" };

/** Journaux (kind = 'event') ou plantages (kind = 'crash') : filtres version, pays, niveau, période, recherche. */
export function eventsQuery(kind, params, now = Date.now()) {
  const cond = ["kind = ?"], args = [kind];
  const { q, p, f } = params;
  if (f.version) { cond.push("version = ?"); args.push(f.version); }
  if (f.country) { cond.push("country = ?"); args.push(f.country.toUpperCase()); }
  if (f.level) { cond.push("level = ?"); args.push(f.level.toLowerCase()); }
  if (p) { cond.push("ts >= ?"); args.push(now - PERIODS[p]); }
  if (q) {
    const cols = kind === "crash" ? ["device", "version", "stack", "country"] : ["message", "tag", "device", "version", "country"];
    cond.push(`(${cols.map((c) => `${c} LIKE ? ESCAPE '\\'`).join(" OR ")})`);
    const l = likeArg(q);
    for (let i = 0; i < cols.length; i++) args.push(l);
  }
  const col = EVENT_SORTS[params.sort] || "ts";
  return { where: `WHERE ${cond.join(" AND ")}`, args, order: `${col} ${params.dir === "asc" ? "ASC" : "DESC"}, id DESC` };
}

// ---- comptes et appareils (KV, en mémoire) ---------------------------------------------

/** Accepte une ligne si [f] (période) et les filtres country / version correspondent. */
const norm = (s) => String(s ?? "").toLowerCase();

/** Colonnes triables des comptes. */
export const ACCOUNT_SORTS = {
  login: (r) => norm(r.login), created: (r) => r.createdAt, logged: (r) => r.lastLoginAt, seen: (r) => r.lastSeen,
  country: (r) => r.country || r.devices.find((d) => d.country)?.country || "", devices: (r) => r.devices.length, sources: (r) => r.sourcesN || 0,
};

/** Filtre des comptes : recherche (identifiant, appareil, modèle, pays, version), pays, version, période (dernière activité). */
export function filterAccounts(rows, params, now = Date.now()) {
  const { q, p, f } = params;
  const ql = norm(q);
  return rows.filter((r) => {
    if (f.country && r.country !== f.country.toUpperCase() && !r.devices.some((d) => d.country === f.country.toUpperCase())) return false;
    if (f.version && !r.devices.some((d) => d.version === f.version)) return false;
    if (p && !(Math.max(r.lastSeen || 0, r.lastLoginAt || 0) >= now - PERIODS[p])) return false;
    if (ql) {
      const hay = [r.login, r.country, ...r.devices.flatMap((d) => [d.name, d.model, d.version, d.country])].map(norm).join("\n");
      if (!hay.includes(ql)) return false;
    }
    return true;
  });
}

/** Colonnes triables des appareils appairés (lignes aplaties : { login, name, model, ... }). */
export const DEVICE_SORTS = {
  name: (d) => norm(d.name), login: (d) => norm(d.login), model: (d) => norm(d.model), version: (d) => d.version,
  country: (d) => d.country || "", seen: (d) => d.lastSeen, created: (d) => d.createdAt, edition: (d) => d.edition,
};

/** Filtre des appareils : recherche (nom, modèle, compte, pays, version), pays, version, période (dernière activité). */
export function filterDevices(rows, params, now = Date.now()) {
  const { q, p, f } = params;
  const ql = norm(q);
  return rows.filter((d) => {
    if (f.country && d.country !== f.country.toUpperCase()) return false;
    if (f.version && d.version !== f.version) return false;
    if (p && !(d.lastSeen >= now - PERIODS[p])) return false;
    if (ql && ![d.login, d.name, d.model, d.version, d.country].map(norm).join("\n").includes(ql)) return false;
    return true;
  });
}

/** Lignes d'appareils à plat à partir des lignes de comptes (comptes « adresse MAC » hérités exclus). */
export const flattenDevices = (accounts) => accounts.filter((r) => !r.legacy).flatMap((r) => r.devices.map((d) => ({ ...d, login: r.login })));

/** Construit une URL de liste en conservant les paramètres actuels et en remplaçant [over] (null/"" : retire). */
export function listUrl(base, params, over = {}) {
  const sp = new URLSearchParams();
  const cur = { q: params.q, p: params.p, sort: params.sort, dir: params.dir, page: params.page, ...params.f, ...over };
  if (!("page" in over)) cur.page = 1;
  for (const [k, v] of Object.entries(cur)) if (v !== "" && v != null && !(k === "page" && Number(v) === 1)) sp.set(k, String(v));
  const qs = sp.toString();
  return qs ? `${base}?${qs}` : base;
}
