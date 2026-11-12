// Ancienne adresse (workers.dev, bloquée sur certains réseaux) → nouvelle adresse publique.
// Seules les PAGES ouvertes dans un navigateur sont redirigées : l'API (/api/*) reste servie sur l'ancienne adresse
// pour les applis déjà installées, qui ont cette adresse en dur (versions ≤ 1.2.95 Android, ≤ 1.2.45 bureau).

export const PRIMARY_HOST = "tv.weedy.dpdns.org";
export const LEGACY_HOST = "ultratv-config.khalilbenaz.workers.dev";

/** Adresse de redirection pour [req], ou null (requête servie telle quelle). Pur, testé. */
export function legacyRedirectTarget(method, urlString, accept) {
  const url = new URL(urlString);
  if (url.hostname !== LEGACY_HOST) return null;
  if (method !== "GET" && method !== "HEAD") return null;
  const p = url.pathname;
  // API des applis, et retour OAuth Trakt (adresse de retour déclarée chez Trakt) : jamais redirigés.
  if (p.startsWith("/api/") || p === "/trakt/callback") return null;
  const page = /\btext\/html\b/i.test(accept || "");
  // Liens de téléchargement et page de choix de l'APK (appli Downloader, liens publiés) : redirigés eux aussi.
  const download = p === "/apk" || p === "/download" || p.startsWith("/download/");
  if (!page && !download) return null;
  return `https://${PRIMARY_HOST}${p}${url.search}`;
}
