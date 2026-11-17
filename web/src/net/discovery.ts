// Serveurs multimédia détectés sur le réseau local (Jellyfin, Emby, Plex). La détection se fait par UDP dans le processus principal
// d'Electron (electron/discovery.cjs) : un navigateur ne peut pas le faire, la fonction est alors indisponible et l'assistant masque
// simplement cette partie. Jamais d'exception : en cas d'échec, la liste est vide.
import { bridge } from "./transport";

export type FoundKind = "jellyfin" | "emby" | "plex";
export interface FoundServer { kind: FoundKind; name: string; address: string; id: string }

/** Vrai dans l'application de bureau : seule à pouvoir écouter le réseau local. */
export const discoveryAvailable = (): boolean => !!bridge()?.discoverServers;

/** Couleur du logo de chaque type de serveur (maquette « Ajout guidé ») et initiale affichée dedans. */
export const SERVER_COLORS = { jellyfin: "#7C3AED", emby: "#16A34A", plex: "#CA8A04", nas: "#2563EB" } as const;
export const SERVER_MARKS = { jellyfin: "J", emby: "E", plex: "P", nas: "N" } as const;

/** Une recherche complète (3 s côté bureau). Liste vide si indisponible ou en échec. */
export async function discoverServers(): Promise<FoundServer[]> {
  try {
    const list = await bridge()?.discoverServers?.();
    return Array.isArray(list) ? list.filter(isFound) : [];
  } catch {
    return [];
  }
}

const KINDS = new Set<string>(["jellyfin", "emby", "plex"]);
export const isFound = (x: unknown): x is FoundServer => {
  const s = x as FoundServer | null;
  return !!s && typeof s === "object" && KINDS.has(s.kind) && typeof s.name === "string" && typeof s.address === "string" && /^https?:\/\//i.test(s.address) && typeof s.id === "string";
};

/** Ajoute les nouveaux résultats à ceux déjà affichés (relance de la recherche) sans doublon ni saut d'ordre : les anciens d'abord. */
export function mergeFound(prev: readonly FoundServer[], next: readonly FoundServer[]): FoundServer[] {
  const out = [...prev];
  const keys = new Set(prev.map((s) => `${s.kind}:${s.id}`));
  for (const s of next) {
    const k = `${s.kind}:${s.id}`;
    if (!keys.has(k)) { keys.add(k); out.push(s); }
  }
  return out;
}

/** Adresse affichée sur une carte : sans schéma (« 192.168.1.20:8096 »). */
export const displayAddress = (address: string): string => address.replace(/^https?:\/\//i, "").replace(/\/+$/, "");
