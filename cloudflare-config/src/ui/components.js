// Briques d'interface partagées par toutes les pages : icônes, logo, messages, états vides, en-têtes de section.
// Règles : tout texte dynamique passe par escapeHtml ; aucun attribut on*= ni style="" (CSP à nonce).

import { escapeHtml as e } from "../http.js";

const ICONS = {
  tv: '<rect x="3" y="4" width="18" height="13" rx="3"/><path d="M8 21h8"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13"/><circle cx="3.5" cy="6" r="1"/><circle cx="3.5" cy="12" r="1"/><circle cx="3.5" cy="18" r="1"/>',
  lock: '<rect x="4" y="11" width="16" height="10" rx="3"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4-6 8-6s7 2 8 6"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
  share: '<circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="6" r="2.5"/><circle cx="18" cy="18" r="2.5"/><path d="M8.2 11l7.6-3.8M8.2 13l7.6 3.8"/>',
  scan: '<path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2M7 12h10"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  home: '<path d="M4 11l8-7 8 7v9a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1"/>',
  puzzle: '<path d="M10 4a2 2 0 1 1 4 0v2h4a1 1 0 0 1 1 1v4h-2a2 2 0 1 0 0 4h2v4a1 1 0 0 1-1 1h-4v-2a2 2 0 1 0-4 0v2H6a1 1 0 0 1-1-1v-4h2a2 2 0 1 0 0-4H5V7a1 1 0 0 1 1-1h4z"/>',
  shield: '<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
  logout: '<path d="M9 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3M16 8l4 4-4 4M20 12H9"/>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
  down: '<path d="M6 9l6 6 6-6"/>',
  ext: '<path d="M14 4h6v6M20 4l-9 9M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4"/>',
  phone: '<rect x="7" y="2.5" width="10" height="19" rx="3"/><path d="M11 18.5h2"/>',
  download: '<path d="M12 4v11M7 11l5 5 5-5M5 20h14"/>',
};
export const ico = (k) => `<svg class="ico" viewBox="0 0 24 24" aria-hidden="true">${ICONS[k]}</svg>`;

export const LOGO = `<svg class="logo" viewBox="0 0 512 512" role="img" aria-label="Ultra TV"><rect class="lg-bg" width="512" height="512" rx="116"/><rect class="lg-fr" x="96" y="120" width="320" height="216" rx="40" fill="none" stroke-width="28"/><path d="M224 188v80l70-40z" fill="#D91E2B"/><path class="lg-fr" d="M196 392h120" stroke-width="28" stroke-linecap="round"/></svg>`;
export const FAVICON = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 512 512'%3E%3Crect width='512' height='512' rx='116' fill='%230A0A0C'/%3E%3Crect x='96' y='120' width='320' height='216' rx='40' fill='none' stroke='%23F5F5F7' stroke-width='28'/%3E%3Cpath d='M224 188v80l70-40z' fill='%23D91E2B'/%3E%3Cpath d='M196 392h120' stroke='%23F5F5F7' stroke-width='28' stroke-linecap='round'/%3E%3C/svg%3E";

// JSON sûr à insérer dans un <script> : jamais de </script> ni de séparateurs de ligne Unicode.
export const jsonForScript = (v) => JSON.stringify(v).replace(/</g, "\\u003c").replace(new RegExp("[\\u2028\\u2029]", "g"), (c) => "\\u" + c.charCodeAt(0).toString(16));

/** Message d'information : kind = err | ok | info. Les erreurs sont annoncées (alert), le reste en statut poli. */
export function notice(kind, text) {
  return `<div class="notice ${e(kind)}" role="${kind === "err" ? "alert" : "status"}">${ico("info")}<span>${e(text)}</span></div>`;
}

/** État vide : icône, titre, aide. */
export function emptyState(icon, title, help) {
  return `<div class="empty"><div class="badge-ico">${ico(icon)}</div><strong>${e(title)}</strong><span class="small">${e(help)}</span></div>`;
}

/** Titre de section : [id] sert d'ancre ; [count] affiche un compteur ; [help] une phrase d'aide. */
export function sectionHead(id, title, { count = null, help = "", hid = "" } = {}) {
  return `<div class="section-h" id="${e(id)}"><div class="sh-t"><h2${hid ? ` id="${e(hid)}"` : ""}>${e(title)}${count === null ? "" : ` <span class="count">${e(String(count))}</span>`}</h2>${help ? `<p class="muted small">${e(help)}</p>` : ""}</div></div>`;
}
