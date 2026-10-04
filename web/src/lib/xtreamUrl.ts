// Détecte une adresse de type Xtream Codes collée là où l'on attendait un lien M3U :
// `http://hôte[:port]/get.php?username=U&password=P&type=m3u_plus&output=ts` (ou `player_api.php`, `xmltv.php`).
// Beaucoup de fournisseurs bloquent `get.php` (HTTP 884) mais acceptent l'API : on crée alors une source
// Xtream Codes (serveur, identifiant, mot de passe) à la place de la source M3U. Même logique que l'app Android.

import type { Source } from "@/db/types";

export interface XtreamCredentials { server: string; username: string; password: string }

const XTREAM_FILES = new Set(["get.php", "player_api.php", "xmltv.php"]);
const URL_RE = /^(https?):\/\/([^/?#]+)((?:\/[^?#]*)?)(?:\?([^#]*))?(?:#.*)?$/i;

/** Décodage %XX en UTF-8 ; le « + » reste un « + » (il peut faire partie d'un mot de passe). */
export function percentDecode(s: string): string {
  if (!s.includes("%")) return s;
  const bytes: number[] = [];
  const enc = new TextEncoder();
  for (let i = 0; i < s.length;) {
    const hex = s.slice(i + 1, i + 3);
    if (s[i] === "%" && /^[0-9a-f]{2}$/i.test(hex)) { bytes.push(parseInt(hex, 16)); i += 3; }
    else { const ch = String.fromCodePoint(s.codePointAt(i)!); bytes.push(...enc.encode(ch)); i += ch.length; }
  }
  return new TextDecoder().decode(new Uint8Array(bytes));
}

/** Identifiants Xtream contenus dans l'adresse, ou null si ce n'est pas une adresse Xtream complète. */
export function parseXtreamUrl(raw: string): XtreamCredentials | null {
  const m = URL_RE.exec(raw.trim());
  if (!m) return null;
  const [, scheme, authority, rawPath = "", query = ""] = m;
  const host = authority!.slice(authority!.lastIndexOf("@") + 1);
  if (!host || host.startsWith(":")) return null;
  const path = rawPath.replace(/\/+$/, "");
  const file = path.slice(path.lastIndexOf("/") + 1).toLowerCase();
  if (!XTREAM_FILES.has(file)) return null;
  const params = new Map<string, string>();
  for (const part of query.split("&")) {
    const i = part.indexOf("=");
    if (i < 0) continue;
    const k = part.slice(0, i).toLowerCase();
    if (!params.has(k)) params.set(k, percentDecode(part.slice(i + 1)));
  }
  const username = params.get("username") ?? "";
  const password = params.get("password") ?? "";
  if (!username.trim() || !password.trim()) return null;
  const prefix = path.slice(0, Math.max(0, path.lastIndexOf("/")));
  return { server: `${scheme!.toLowerCase()}://${host}${prefix}`, username, password };
}

/** Source M3U « get.php » → même source en Xtream Codes (synchro remise à zéro) ; null si ce n'est pas le cas. */
export function convertToXtream(s: Source): Source | null {
  if (s.type !== "m3u") return null;
  const c = parseXtreamUrl(s.m3uUrl);
  if (!c) return null;
  return { ...s, type: "xtream", server: c.server, username: c.username, password: c.password, m3uUrl: "", langs: null, lastSyncAt: 0, state: "new", error: undefined };
}

const STANDARD_STATUS = new Set([
  400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416, 417, 418, 421, 422, 423, 424, 425, 426, 428, 429, 431, 451,
  500, 501, 502, 503, 504, 505, 506, 507, 508, 510, 511,
]);

/** Code HTTP « maison » (884, 999…) d'un message `HTTP <code>` ; null pour un code standard ou un autre message. */
export function nonStandardHttpStatus(message: string): number | null {
  const m = /^HTTP (\d{3})$/.exec(message.trim());
  if (!m) return null;
  const c = Number(m[1]);
  return STANDARD_STATUS.has(c) ? null : c;
}
