// Anciennes adresses → site principal. Seules les PAGES ouvertes dans un navigateur sont redirigées : l'API (/api/*)
// reste servie sur les anciennes adresses pour les applis déjà installées, qui les ont en dur :
//  - workers.dev (bloquée sur certains réseaux) : versions ≤ 1.2.95 Android, ≤ 1.2.45 bureau ;
//  - tv.weedy.dpdns.org : adresse de l'API des versions actuelles (reste l'adresse API).

export const PRIMARY_HOST = "ultratv.dpdns.org";
/** Adresse de retour déclarée chez Trakt (voir traktRedirect). */
export const LEGACY_HOST = "ultratv-config.khalilbenaz.workers.dev";
export const API_HOST = "tv.weedy.dpdns.org";
export const LEGACY_HOSTS = [LEGACY_HOST, API_HOST];

/**
 * Traitement d'une requête arrivée sur l'ancienne adresse. Pur, testé.
 *  - { kind: "serve" } : API des applis installées (/api/*), servie normalement ;
 *  - { kind: "redirect", to, status } : tout le reste (site, connexion, tableau de bord, administration,
 *    téléchargements, retour Trakt, QR des applis installées) part sur la même page de la nouvelle adresse.
 *    302 pour GET/HEAD, 307 sinon (garde la méthode et le corps d'un formulaire).
 */
export function legacyDecision(method, urlString, site = PRIMARY_HOST) {
  const url = new URL(urlString);
  // Site principal choisi par la variable SITE_HOST (tant que le nouveau domaine n'est pas actif : tv.weedy reste le site).
  if (url.hostname === site || !LEGACY_HOSTS.includes(url.hostname)) return { kind: "serve" };
  if (url.pathname.startsWith("/api/")) return { kind: "serve" };
  const status = method === "GET" || method === "HEAD" ? 302 : 307;
  return { kind: "redirect", to: `https://${site}${url.pathname}${url.search}`, status };
}
