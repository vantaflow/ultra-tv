// Formats partagés : durées relatives, dates, pays, sources.
import { escapeHtml as e } from "../http.js";

/** « il y a 3 jours » : durée relative lisible, calculée côté serveur. */
export function ago(t, now = Date.now(), lang = "fr") {
  if (!t) return "";
  const s = Math.max(0, Math.round((now - t) / 1000));
  if (lang === "en") {
    if (s < 60) return "just now";
    const steps = [[3600, 60, "min ago"], [86400, 3600, "h ago"], [2592000, 86400, "d ago"], [31536000, 2592000, "mo ago"]];
    for (const [lim, div, unit] of steps) if (s < lim) return `${Math.floor(s / div)} ${unit}`;
    const y = Math.floor(s / 31536000);
    return `${y} yr${y > 1 ? "s" : ""} ago`;
  }
  if (lang === "es") {
    if (s < 60) return "ahora mismo";
    const steps = [[3600, 60, "min"], [86400, 3600, "h"], [2592000, 86400, "d"], [31536000, 2592000, "mes(es)"]];
    for (const [lim, div, unit] of steps) if (s < lim) return `hace ${Math.floor(s / div)} ${unit}`;
    const y = Math.floor(s / 31536000);
    return `hace ${y} año${y > 1 ? "s" : ""}`;
  }
  if (lang === "ar") {
    if (s < 60) return "الآن";
    const steps = [[3600, 60, "د"], [86400, 3600, "س"], [2592000, 86400, "ي"], [31536000, 2592000, "شهر"]];
    for (const [lim, div, unit] of steps) if (s < lim) return `منذ ${Math.floor(s / div)} ${unit}`;
    const y = Math.floor(s / 31536000);
    return `منذ ${y} سنة`;
  }
  if (lang === "pl") {
    if (s < 60) return "przed chwilą";
    const steps = [[3600, 60, "min temu"], [86400, 3600, "godz. temu"], [2592000, 86400, "dni temu"], [31536000, 2592000, "mies. temu"]];
    for (const [lim, div, unit] of steps) if (s < lim) return `${Math.floor(s / div)} ${unit}`;
    const y = Math.floor(s / 31536000);
    return `${y} lat temu`;
  }
  // fr par défaut
  if (s < 60) return "à l'instant";
  const steps = [[3600, 60, "min"], [86400, 3600, "h"], [2592000, 86400, "j"], [31536000, 2592000, "mois"]];
  for (const [lim, div, unit] of steps) if (s < lim) return `il y a ${Math.floor(s / div)} ${unit}`;
  const y = Math.floor(s / 31536000);
  return `il y a ${y} an${y > 1 ? "s" : ""}`;
}

const MOIS = {
  fr: ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."],
  en: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
  es: ["ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "sep.", "oct.", "nov.", "dic."],
  ar: ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"],
  pl: ["sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru"],
};
export const frDate = (ms, lang = "fr") => {
  const d = new Date(ms);
  const mList = MOIS[lang] || MOIS.fr;
  return `${d.getUTCDate()} ${mList[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
};

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
