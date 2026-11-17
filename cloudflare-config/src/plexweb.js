// Plex depuis le tableau de bord : liaison du compte par code PIN plex.tv (fenêtre app.plex.tv/auth), puis choix du
// serveur. Le jeton du COMPTE ne quitte jamais le Worker (KV temporaire, 15 min) ; seule la source finale garde le
// jeton du SERVEUR choisi, chiffré avec les autres sources, et l'envoie aux appareils comme un mot de passe.
// Seul hôte contacté : plex.tv (adresse fixe, aucune URL fournie par l'utilisateur).

export const PLEX_PIN_TTL_S = 15 * 60;
const PRODUCT = "Ultra TV";

const headers = (cid, token = null) => ({
  accept: "application/json",
  "X-Plex-Product": PRODUCT,
  "X-Plex-Client-Identifier": cid,
  ...(token ? { "X-Plex-Token": token } : {}),
});

export const plexPinKey = (login) => `plexpin:${login}`;

/** Nouveau code PIN : { id, code, cid } ou null. */
export async function plexCreatePin(fetchFn = fetch) {
  const cid = `ultratv-web-${crypto.randomUUID()}`;
  const r = await fetchFn("https://plex.tv/api/v2/pins?strong=true", { method: "POST", headers: headers(cid) });
  if (!r.ok) return null;
  const j = await r.json().catch(() => null);
  return j && j.id && j.code ? { id: j.id, code: String(j.code), cid } : null;
}

/** Page plex.tv où l'utilisateur valide l'accès (ouverte dans une nouvelle fenêtre). */
export function plexAuthUrl(pin) {
  const q = new URLSearchParams({ clientID: pin.cid, code: pin.code, "context[device][product]": PRODUCT });
  return `https://app.plex.tv/auth#?${q.toString()}`;
}

/** Jeton du compte une fois le PIN validé, sinon null (pas encore validé, expiré). */
export async function plexPinToken(pin, fetchFn = fetch) {
  const r = await fetchFn(`https://plex.tv/api/v2/pins/${encodeURIComponent(pin.id)}`, { headers: headers(pin.cid) });
  if (!r.ok) return null;
  const j = await r.json().catch(() => null);
  return j && typeof j.authToken === "string" && j.authToken ? j.authToken : null;
}

/**
 * Serveurs du compte (`provides` contient `server`), avec le jeton propre à chacun et ses connexions classées :
 * locales d'abord, puis distantes, relais Plex en dernier.
 */
export function parsePlexServers(list) {
  if (!Array.isArray(list)) return [];
  return list.filter((r) => r && String(r.provides || "").split(",").map((s) => s.trim()).includes("server") && r.clientIdentifier && r.accessToken)
    .map((r) => {
      const conns = (Array.isArray(r.connections) ? r.connections : [])
        .filter((c) => c && typeof c.uri === "string" && /^https?:\/\//.test(c.uri))
        .map((c) => ({ uri: c.uri.slice(0, 512), local: c.local === true, relay: c.relay === true }))
        .sort((a, b) => (a.relay - b.relay) || (b.local - a.local))
        .slice(0, 12);
      return { id: String(r.clientIdentifier).slice(0, 128), name: String(r.name || "Plex").slice(0, 64), owned: r.owned === true, token: String(r.accessToken), conns };
    })
    .filter((s) => s.conns.length > 0)
    .slice(0, 30);
}

export async function plexServers(accountToken, cid, fetchFn = fetch) {
  const r = await fetchFn("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", { headers: headers(cid, accountToken) });
  if (!r.ok) return null;
  return parsePlexServers(await r.json().catch(() => null));
}
