// Journal de lecture côté interface : relayé au processus principal (fichier rotatif, identifiants masqués là-bas).
// On n'envoie jamais d'adresse complète : seulement l'hôte, l'extension et le genre de chemin (voir urlInfo).
import { bridge } from "@/net/transport";

/** Hôte, extension et genre de chemin d'une adresse, sans identifiants ni requête. */
export function urlInfo(url: string): { host: string; ext: string; path: string } {
  try {
    const u = new URL(url);
    const seg = u.pathname.toLowerCase().split("/").filter(Boolean);
    const kind = ["live", "movie", "series", "timeshift"].find((k) => seg[0] === k) ?? (/\.(?:m3u8|ts)$/.test(u.pathname.toLowerCase()) ? "stream" : "other");
    return { host: u.host, ext: (/\.([a-z0-9]{2,5})$/i.exec(u.pathname)?.[1] ?? "").toLowerCase(), path: kind };
  } catch { return { host: "", ext: "", path: "other" }; }
}

/** Écrit une ligne de journal (sans effet hors bureau). Ne lève jamais. */
export function playLog(name: string, fields: Record<string, unknown> = {}): void {
  try { bridge()?.log?.(name, fields); } catch { /* journal indisponible */ }
}
