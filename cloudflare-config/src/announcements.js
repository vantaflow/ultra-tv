// Alertes : messages programmés par l'administrateur (section Administration du tableau de bord) et affichés par les
// applications (TV, téléphone, bureau) pendant leur fenêtre de diffusion. Aucune donnée personnelle : un texte, des dates.
//
// Stockage : une seule clé KV (liste courte, quelques dizaines au plus). Lecture publique par /api/announcements
// (pas d'appairage exigé : un appareil non encore appairé doit aussi voir une alerte de service).

export const ANN_KEY = "announcements:v1";
export const ANN_MAX = 50;
export const ANN_LEVELS = ["info", "warning", "urgent"];
export const ANN_AUDIENCES = ["all", "tv", "mobile"];
/** Bouton d'action proposé avec l'alerte : « update » ouvre la mise à jour intégrée de l'application. */
export const ANN_ACTIONS = ["none", "update"];
/** Statistiques comptées par appareil (une fois chacune) : affichée, fermée, bouton d'action utilisé. */
export const ANN_STAT_KINDS = ["shown", "dismissed", "action"];
const VERSION_RE = /^\d{1,4}(\.\d{1,4}){0,3}$/;
const TITLE_MAX = 80;
const MESSAGE_MAX = 600;
/** Une alerte terminée depuis plus de 30 jours est purgée à la prochaine écriture. */
const KEEP_ENDED_MS = 30 * 86400_000;

export async function loadAnnouncements(env) {
  try {
    const list = JSON.parse((await env.CONFIG.get(ANN_KEY)) || "[]");
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

export async function saveAnnouncements(env, list, now = Date.now()) {
  const kept = list.filter((a) => !(a.endAt && now - a.endAt > KEEP_ENDED_MS)).slice(-ANN_MAX);
  await env.CONFIG.put(ANN_KEY, JSON.stringify(kept));
  return kept;
}

/** État d'une alerte à l'instant [now] : « scheduled » (à venir), « live » (diffusée) ou « ended ». */
export function announcementState(a, now = Date.now()) {
  if (a.startAt && now < a.startAt) return "scheduled";
  if (a.endAt && now >= a.endAt) return "ended";
  return "live";
}

/** Version « 1.2.78 » (suffixe « -debug » ignoré) → nombres, ou null si illisible. */
export function parseVersion(v) {
  const m = String(v || "").trim().match(/^\d{1,4}(\.\d{1,4}){0,3}/);
  return m ? m[0].split(".").map(Number) : null;
}

/** Vrai si la version [v] est strictement antérieure à [ref] (composants manquants = 0). */
export function versionBefore(v, ref) {
  const a = parseVersion(v), b = parseVersion(ref);
  if (!a || !b) return false;
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const x = a[i] || 0, y = b[i] || 0;
    if (x !== y) return x < y;
  }
  return false;
}

/**
 * Alertes à diffuser maintenant, de la plus urgente à la moins urgente puis de la plus récente à la plus ancienne.
 * [audience] (« tv », « mobile », « desktop ») filtre les alertes ciblées ; absente : toutes.
 * Seuls les champs utiles aux applications sortent (pas d'auteur ni de date de création).
 */
export function activeAnnouncements(list, now = Date.now(), audience = null, version = null) {
  // Rang croissant avec la gravité (info 0 … urgent 2) : tri décroissant = la plus grave d'abord.
  const rank = (l) => ANN_LEVELS.indexOf(l);
  return list
    .filter((a) => announcementState(a, now) === "live")
    .filter((a) => !audience || !a.audience || a.audience === "all" || a.audience === audience)
    // Ciblage par version : une application qui ne dit pas sa version (1.2.78 et avant) est forcément ancienne.
    .filter((a) => !a.below || !parseVersion(version) || versionBefore(version, a.below))
    .sort((x, y) => rank(y.level) - rank(x.level) || (y.startAt || 0) - (x.startAt || 0))
    .map((a) => ({ id: a.id, title: a.title, message: a.message, level: a.level, action: a.action && a.action !== "none" ? a.action : null, startAt: a.startAt || null, endAt: a.endAt || null }));
}

const clean = (v, max) => String(v || "").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim().slice(0, max);

/** Date du formulaire : millisecondes (posées par le script de la page, heure locale du navigateur) ou ISO sans fuseau (UTC). */
function parseWhen(ms, local) {
  const n = Number(ms);
  if (Number.isFinite(n) && n > 0) return Math.trunc(n);
  if (!local) return null;
  const t = Date.parse(/[zZ]|[+-]\d\d:\d\d$/.test(local) ? local : `${local}Z`);
  return Number.isFinite(t) ? t : null;
}

/**
 * Formulaire de création → { value } ou { error } (code de message du tableau de bord).
 * Début vide : tout de suite. Fin vide : jusqu'à suppression.
 */
export function parseAnnouncementForm(form, now = Date.now(), id = null) {
  const title = clean(form.get("title"), TITLE_MAX);
  const message = clean(form.get("message"), MESSAGE_MAX);
  if (!message) return { error: "ann_empty" };
  const level = ANN_LEVELS.includes(form.get("level")) ? form.get("level") : "info";
  const audience = ANN_AUDIENCES.includes(form.get("audience")) ? form.get("audience") : "all";
  const below = clean(form.get("below"), 20);
  if (below && !VERSION_RE.test(below)) return { error: "ann_version" };
  const action = ANN_ACTIONS.includes(form.get("action")) ? form.get("action") : "none";
  const startAt = parseWhen(form.get("startMs"), form.get("start")) || now;
  const endAt = parseWhen(form.get("endMs"), form.get("end"));
  if (endAt && endAt <= startAt) return { error: "ann_dates" };
  if (endAt && endAt <= now) return { error: "ann_past" };
  return { value: { id: id || crypto.randomUUID().replace(/-/g, "").slice(0, 16), title, message, level, audience, below: below || null, action, startAt, endAt, createdAt: now } };
}
