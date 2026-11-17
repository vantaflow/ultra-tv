// Logique pure de l'assistant « ma bibliothèque » (Jellyfin, Emby, Plex, NAS) et de l'écran « Mes bibliothèques » : isolée des
// composants pour être testée.
import type { Source } from "@/db/types";
import type { Library } from "@/net/mediaServer";
import { orderConnections, type PlexServer } from "@/net/plex";

/** Étapes de l'assistant (maquette « Ajout guidé ») : 1 trouver, 2 se connecter, 3 choisir les bibliothèques. */
export type WizardStep = "find" | "connect" | "libs";
export const WIZARD_STEPS: readonly WizardStep[] = ["find", "connect", "libs"];

/** Type de connexion d'un serveur Plex : celle que l'appli essaiera en premier (locale, puis distante, puis relais). */
export type ConnKind = "local" | "remote" | "relay";
export function plexConnKind(s: Pick<PlexServer, "connections">): ConnKind {
  const first = orderConnections(s.connections)[0];
  return !first ? "remote" : first.relay ? "relay" : first.local ? "local" : "remote";
}

/** Type de connexion d'après l'adresse retenue d'un serveur Plex déjà ajouté : relais Plex = « plex.direct » sur le port 8443. */
export function connKindOfAddress(address: string): ConnKind {
  let u: URL;
  try { u = new URL(address); } catch { return "remote"; }
  if (/\.plex\.direct$/i.test(u.hostname) && u.port === "8443") return "relay";
  const h = u.hostname;
  if (h === "localhost" || /^(10|127)\./.test(h) || /^192\.168\./.test(h) || /^172\.(1[6-9]|2\d|3[01])\./.test(h) || /^169\.254\./.test(h) || /\.local$/i.test(h)) return "local";
  // « 192-168-1-40.<hash>.plex.direct » : l'adresse privée est dans le nom d'hôte.
  const m = /^(\d+)-(\d+)-(\d+)-(\d+)\.[^.]+\.plex\.direct$/i.exec(h);
  if (m) return connKindOfAddress(`http://${m[1]}.${m[2]}.${m[3]}.${m[4]}`);
  return "remote";
}

/** Bibliothèques à enregistrer dans la source : null = toutes (cases toutes cochées), sinon les identifiants cochés. */
export function libsToSave(all: readonly Library[], checked: ReadonlySet<string>): string[] | null {
  const ids = all.map((l) => l.id);
  return ids.every((id) => checked.has(id)) ? null : ids.filter((id) => checked.has(id));
}

/** Une bibliothèque est importée si la source n'a pas de liste ou si elle y figure. */
export const libraryWanted = (libs: Source["libs"], id: string): boolean => libs == null || libs.includes(id);

/** Compte à rebours « 14 min » / « 45 s » du code Plex (arrondi au supérieur pour ne pas afficher 0 min tant qu'il reste du temps). */
export function countdownParts(remainingMs: number): { unit: "min" | "s"; n: number } {
  const s = Math.max(0, Math.ceil(remainingMs / 1000));
  return s > 60 ? { unit: "min", n: Math.ceil(s / 60) } : { unit: "s", n: s };
}

/** Reste d'une lecture en cours : positions en secondes. Fraction 0..1 pour la barre ; null si rien à montrer. */
export function progressRatio(pos: number, dur: number): number | null {
  if (!(dur > 0) || !(pos > 0)) return null;
  const r = pos / dur;
  return r >= 0.97 ? null : Math.min(1, r);
}

// ---------------------------------------------------------------- écran « Mes bibliothèques »

/** État affiché d'un serveur : « checking » le temps de la sonde, puis en ligne, relais Plex ou hors ligne (dernière copie locale). */
export type LibStatus = "checking" | "online" | "relay" | "offline";
export function libStatus(source: Pick<Source, "type" | "server">, reachable: boolean | null): LibStatus {
  if (reachable == null) return "checking";
  if (!reachable) return "offline";
  return source.type === "plex" && connKindOfAddress(source.server) === "relay" ? "relay" : "online";
}

/** Types de source qui sont des bibliothèques personnelles (et non de l'IPTV). */
export const isLibrarySource = (type: string | undefined): boolean => type === "jellyfin" || type === "emby" || type === "plex" || type === "nas";

/** Date de la dernière copie (« 12/11/2026 ») ; « — » si la source n'a jamais été synchronisée. */
export function copyDate(ts: number, locale: string): string {
  if (!ts) return "—";
  try { return new Date(ts).toLocaleDateString(locale); } catch { return new Date(ts).toLocaleDateString(); }
}
