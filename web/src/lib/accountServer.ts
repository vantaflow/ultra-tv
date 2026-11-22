// Écran Abonnement : adresse du serveur affichée = celle que l'utilisateur a saisie (jamais `server_info.url` renvoyée par le
// panneau Xtream, souvent une adresse interne ou un autre domaine). Identifiants éventuels retirés de l'adresse.

export function accountServer(s: { type: string; server: string }): string | null {
  if (s.type !== "xtream" && s.type !== "stalker") return null;
  const raw = s.server.trim();
  if (!raw) return null;
  // « http://user:pass@hôte:port/chemin?x=y » -> « http://hôte:port/chemin » (ni identifiants, ni paramètres).
  return raw.replace(/^([a-z][a-z0-9+.-]*:\/\/)[^/@?#]*@/i, "$1").replace(/[?#].*$/, "").replace(/\/+$/, "");
}
