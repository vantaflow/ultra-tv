// Ancienne adresse (workers.dev, bloquée sur certains réseaux) → nouvelle adresse publique.
// Seules les PAGES ouvertes dans un navigateur sont redirigées : l'API (/api/*) reste servie sur l'ancienne adresse
// pour les applis déjà installées, qui ont cette adresse en dur (versions ≤ 1.2.95 Android, ≤ 1.2.45 bureau).

export const PRIMARY_HOST = "tv.weedy.dpdns.org";
export const LEGACY_HOST = "ultratv-config.khalilbenaz.workers.dev";

/**
 * Traitement d'une requête arrivée sur l'ancienne adresse. Pur, testé.
 *  - { kind: "serve" } : API des applis installées (/api/*), servie normalement ;
 *  - { kind: "redirect", to } : retour Trakt (adresse déclarée chez Trakt) et pages ouvertes par le QR code des
 *    applis installées (/pair, /envoyer), poursuivis sur la nouvelle adresse ;
 *  - { kind: "gone" } : tout le reste (site, tableau de bord, administration, téléchargements) n'est plus servi ici.
 */
export function legacyDecision(method, urlString) {
  const url = new URL(urlString);
  if (url.hostname !== LEGACY_HOST) return { kind: "serve" };
  const p = url.pathname;
  if (p.startsWith("/api/")) return { kind: "serve" };
  if ((method === "GET" || method === "HEAD") && (p === "/trakt/callback" || p === "/pair" || p === "/envoyer")) {
    return { kind: "redirect", to: `https://${PRIMARY_HOST}${p}${url.search}` };
  }
  return { kind: "gone" };
}
