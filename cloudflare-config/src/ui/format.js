// Formats partagés : durées relatives, dates, pays, sources.
import { escapeHtml as e } from "../http.js";

/** « il y a 3 jours » : durée relative lisible, calculée côté serveur. */
export function ago(t, now = Date.now()) {
  if (!t) return "";
  const s = Math.max(0, Math.round((now - t) / 1000));
  if (s < 60) return "à l'instant";
  const steps = [[3600, 60, "min"], [86400, 3600, "h"], [2592000, 86400, "j"], [31536000, 2592000, "mois"]];
  for (const [lim, div, unit] of steps) if (s < lim) return `il y a ${Math.floor(s / div)} ${unit}`;
  const y = Math.floor(s / 31536000);
  return `il y a ${y} an${y > 1 ? "s" : ""}`;
}

const MOIS = ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."];
export const frDate = (ms) => { const d = new Date(ms); return `${d.getUTCDate()} ${MOIS[d.getUTCMonth()]} ${d.getUTCFullYear()}`; };

export const fmtTime = (ts) => (ts ? new Date(ts).toISOString().replace("T", " ").slice(0, 19) : "");

/** Nom du pays (code ISO à 2 lettres), ou « — ». Pas d'emoji de drapeau : Windows les affiche en lettres. */
export function countryLabel(code) {
  if (!code || !/^[A-Z]{2}$/.test(code)) return "—";
  let name = code;
  try { name = new Intl.DisplayNames(["fr"], { type: "region" }).of(code) || code; } catch { /* ICU absent */ }
  return name;
}

/** « 3 (2 Xtream · 1 M3U) » : nombre et types des sources, jamais leur adresse ni leurs identifiants. */
export function sourcesLabel(r) {
  if (r.sourcesN == null) return r.sources ? "oui" : "—";
  if (!r.sourcesN) return "—";
  const parts = [r.sourcesXtream ? `${r.sourcesXtream} Xtream` : "", r.sourcesM3u ? `${r.sourcesM3u} M3U` : ""].filter(Boolean).join(" · ");
  return `${r.sourcesN}${parts ? ` <span class="muted">(${e(parts)})</span>` : ""}`;
}
