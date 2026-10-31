// Alertes : messages programmés par l'administrateur (section Administration du tableau de bord) et affichés par les
// applications (TV, téléphone, bureau) pendant leur fenêtre de diffusion. Aucune donnée personnelle : un texte, des dates.
//
// Stockage : une seule clé KV (liste courte, quelques dizaines au plus). Lecture publique par /api/announcements
// (pas d'appairage exigé : un appareil non encore appairé doit aussi voir une alerte de service).

export const ANN_KEY = "announcements:v1";
export const ANN_MAX = 50;
export const ANN_LEVELS = ["info", "warning", "urgent"];
export const ANN_AUDIENCES = ["all", "tv", "mobile", "desktop"];
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

/**
 * Alertes à diffuser maintenant, de la plus urgente à la moins urgente puis de la plus récente à la plus ancienne.
 * [audience] (« tv », « mobile », « desktop ») filtre les alertes ciblées ; absente : toutes.
 * Seuls les champs utiles aux applications sortent (pas d'auteur ni de date de création).
 */
export function activeAnnouncements(list, now = Date.now(), audience = null) {
  // Rang croissant avec la gravité (info 0 … urgent 2) : tri décroissant = la plus grave d'abord.
  const rank = (l) => ANN_LEVELS.indexOf(l);
  return list
    .filter((a) => announcementState(a, now) === "live")
    .filter((a) => !audience || !a.audience || a.audience === "all" || a.audience === audience)
    .sort((x, y) => rank(y.level) - rank(x.level) || (y.startAt || 0) - (x.startAt || 0))
    .map((a) => ({ id: a.id, title: a.title, message: a.message, level: a.level, startAt: a.startAt || null, endAt: a.endAt || null }));
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
  const startAt = parseWhen(form.get("startMs"), form.get("start")) || now;
  const endAt = parseWhen(form.get("endMs"), form.get("end"));
  if (endAt && endAt <= startAt) return { error: "ann_dates" };
  if (endAt && endAt <= now) return { error: "ann_past" };
  return { value: { id: id || crypto.randomUUID().replace(/-/g, "").slice(0, 16), title, message, level, audience, startAt, endAt, createdAt: now } };
}
