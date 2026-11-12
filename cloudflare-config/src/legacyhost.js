// Ancienne adresse (workers.dev, bloquée sur certains réseaux) → nouvelle adresse publique.
// Seules les PAGES ouvertes dans un navigateur sont redirigées : l'API (/api/*) reste servie sur l'ancienne adresse
// pour les applis déjà installées, qui ont cette adresse en dur (versions ≤ 1.2.95 Android, ≤ 1.2.45 bureau).

export const PRIMARY_HOST = "tv.weedy.dpdns.org";
export const LEGACY_HOST = "ultratv-config.khalilbenaz.workers.dev";

/**
 * Traitement d'une requête arrivée sur l'ancienne adresse. Pur, testé.
 *  - { kind: "serve" } : API des applis installées (/api/*), servie normalement ;
 *  - { kind: "redirect", to, status } : tout le reste (site, connexion, tableau de bord, administration,
 *    téléchargements, retour Trakt, QR des applis installées) part sur la même page de la nouvelle adresse.
 *    302 pour GET/HEAD, 307 sinon (garde la méthode et le corps d'un formulaire).
 */
export function legacyDecision(method, urlString) {
  const url = new URL(urlString);
  if (url.hostname !== LEGACY_HOST) return { kind: "serve" };
  if (url.pathname.startsWith("/api/")) return { kind: "serve" };
  const status = method === "GET" || method === "HEAD" ? 302 : 307;
  return { kind: "redirect", to: `https://${PRIMARY_HOST}${url.pathname}${url.search}`, status };
}
