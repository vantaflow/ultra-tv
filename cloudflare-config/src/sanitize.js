// Nettoyage défensif de tout texte venant d'un appareil avant stockage.
// Le client nettoie déjà (LogSanitizer / RemoteLog.sanitize) mais le serveur ne lui fait pas confiance : un APK ancien,
// modifié ou un tiers peut envoyer n'importe quoi. Le serveur est la dernière barrière.
//
// Deux fonctions :
// - `sanitizeLog` : journaux et plantages (message, pile, étiquette, modèle…). Liste blanche : toute adresse est réduite
//   à « schéma + empreinte de l'hôte + type de ressource », puis des règles par clé (JSON, paramètres, en-têtes) et par
//   forme (MAC, e-mail, IP, jetons) retirent ce qui reste hors des adresses. Vecteurs partagés avec l'appli :
//   test/fixtures/sanitize-vectors.json.
// - `sanitizeText` : textes courts saisis (nom d'appareil, libellé d'appairage, nom de source). Règles historiques,
//   plus légères : un libellé d'appairage est une étiquette MAC que l'on garde lisible.
//
// Coût : chaque passe est linéaire (classes de caractères disjointes, débuts de jeton ancrés par des regards en arrière,
// répétitions bornées) ; aucune n'a de retour arrière catastrophique sur une pile de 64 Ko (test de durée dédié).

// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g;

// ---- sanitizeText (textes courts saisis) ------------------------------------------------------------------------

const SENSITIVE_PARAMS =
  "username|user|login|password|passwd|pass|pwd|token|apikey|api_key|secret|auth|authorization|key";

const TEXT_RULES = [
  // http://user:pass@host/...
  [/(\b[a-z][a-z0-9+.-]*:\/\/)[^\s/@]+@/gi, "$1<redacted>@"],
  // ?username=...&password=...  /  "user=bob pass=x"
  [new RegExp(`(^|[?&;\\s,{"'])(${SENSITIVE_PARAMS})(\\s*[=:]\\s*"?)[^&\\s"',}]+`, "gi"), "$1$2$3<redacted>"],
  // Xtream : /live/<user>/<pass>/<id>.ts (live, movie, series, timeshift…)
  [/\/(live|movie|series|vod|radio|timeshift)\/[^/\s?]+\/[^/\s?]+\//gi, "/$1/<redacted>/<redacted>/"],
  // Jetons d'appareil et en-têtes Bearer
  [/\butv_[A-Za-z0-9_-]{16,}/g, "utv_<redacted>"],
  [/\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, "Bearer <redacted>"],
];

export function sanitizeText(input, max = 4000) {
  if (typeof input !== "string") return "";
  let out = input.slice(0, max * 2).replace(CONTROL, "");
  for (const [re, rep] of TEXT_RULES) out = out.replace(re, rep);
  return out.slice(0, max);
}

// ---- sanitizeLog (journaux et plantages) -------------------------------------------------------------------------

/** Empreinte stable et courte d'un hôte : FNV-1a 32 bits sur l'UTF-8 du nom en minuscules, 8 chiffres hexadécimaux. */
export function hostFingerprint(host) {
  let h = 0x811c9dc5;
  for (const b of new TextEncoder().encode(String(host).toLowerCase())) {
    h ^= b;
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h.toString(16).padStart(8, "0");
}

/** Adresse privée, de lien local ou de boucle (IPv4, IPv6, noms locaux). */
export function isPrivateHost(host) {
  const h = String(host).toLowerCase().replace(/^\[|\]$/g, "");
  const v4 = h.match(/^(\d{1,3})\.(\d{1,3})\.\d{1,3}\.\d{1,3}$/);
  if (v4) {
    const a = +v4[1], b = +v4[2];
    return a === 10 || a === 127 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || (a === 169 && b === 254) || (a === 100 && b >= 64 && b <= 127);
  }
  if (h.includes(":")) return h === "::1" || /^fe[89ab][0-9a-f]?:/.test(h) || /^f[cd][0-9a-f]{0,2}:/.test(h);
  return h === "localhost" || /\.(local|lan|home|internal)$/.test(h);
}

/** `<host#empreinte>` (ou `<lan#…>` pour une adresse locale) ; le port éventuel n'entre pas dans l'empreinte. */
const hostTag = (raw) => {
  const str = String(raw);
  const host = (str.match(/:/g) || []).length === 1 ? str.replace(/:\d{1,5}$/, "") : str;
  return `<${isPrivateHost(host) ? "lan" : "host"}#${hostFingerprint(host)}>`;
};

/** Seuls mots de chemin gardés (type de ressource utile au diagnostic, jamais un identifiant). */
const PATH_KINDS = new Set([
  "live", "movie", "movies", "series", "vod", "radio", "timeshift", "hls", "dash", "play", "get", "playlist",
  "get.php", "player_api.php", "panel_api.php", "portal.php", "load.php", "xmltv.php", "stalker_portal", "c",
  "library", "videos", "items", "audio",
]);
/** Extensions gardées (dernier segment du chemin). */
const PATH_EXTS = new Set([
  "ts", "m3u8", "m3u", "mp4", "mkv", "avi", "mov", "webm", "flv", "mpd", "aac", "mp3", "ac3", "json", "xml", "gz",
  "php", "jpg", "jpeg", "png", "webp", "srt", "vtt",
]);

const decodePct = (s) => s.replace(/%([0-9a-f]{2})/gi, (_, h) => String.fromCharCode(parseInt(h, 16)));

/**
 * Adresse réduite : `schéma://<host#empreinte>` + premier mot connu du chemin + `/*` s'il reste des segments
 * + extension connue du dernier segment + `?*` s'il y avait une requête. Identifiants de l'autorité, segments,
 * valeurs de paramètres et fragment ne survivent jamais.
 */
export function reduceUrl(scheme, body, { encoded = false, slashes = true } = {}) {
  const b = encoded ? decodePct(body) : body;
  let ae = b.length;
  for (const c of ["/", "?", "#"]) { const i = b.indexOf(c); if (i >= 0 && i < ae) ae = i; }
  const authority = b.slice(0, ae);
  const rest = b.slice(ae);
  const hostPort = authority.slice(authority.lastIndexOf("@") + 1);
  let host = hostPort;
  if (hostPort.startsWith("[")) host = hostPort.slice(0, hostPort.indexOf("]") + 1 || hostPort.length);
  else if ((hostPort.match(/:/g) || []).length === 1) host = hostPort.slice(0, hostPort.indexOf(":"));
  let path = rest;
  let cut = path.length;
  for (const c of ["?", "#"]) { const i = path.indexOf(c); if (i >= 0 && i < cut) cut = i; }
  const hasQuery = path.slice(cut).startsWith("?") && path.length > cut + 1;
  path = path.slice(0, cut);
  const segs = path.split("/").filter(Boolean);
  const kind = segs.map((s) => s.toLowerCase()).find((s) => PATH_KINDS.has(s)) || null;
  const lastSeg = segs.length ? segs[segs.length - 1].toLowerCase() : "";
  const extM = lastSeg.match(/\.([a-z0-9]{2,5})$/);
  const ext = extM && PATH_EXTS.has(extM[1]) && lastSeg !== kind ? extM[1] : null;
  let out = `${scheme}:${slashes ? "//" : ""}${host ? hostTag(host) : ""}`;
  if (kind) out += `/${kind}`;
  if (segs.length > (kind ? 1 : 0)) out += ext ? `/*.${ext}` : "/*";
  if (hasQuery) out += "?*";
  return out;
}

// Début d'adresse : schéma quelconque suivi de `://` (ou `%3A%2F%2F` encodé), ou `stalker:` sans barres.
const SCHEME_RE = /(?<![A-Za-z0-9+.-])([A-Za-z][A-Za-z0-9+.-]{1,19})(:\/\/|%3A%2F%2F)|(?<![A-Za-z0-9+.-])(stalker):(?=[^\s/])/gi;
const isUrlStop = (c) => c <= " " || c === '"' || c === "<" || c === ">" || c === "`" || c === "\\";
const isEncStop = (c) => isUrlStop(c) || c === "&" || c === "'" || c === ";" || c === ",";
const TRAILING = ")]}'.,;:!?";
const CONT_MAX_TOKENS = 6;

/** Jeton (après une espace) qui peut prolonger une adresse coupée par une espace dans un identifiant. */
function isContinuation(tok) {
  if (!tok || tok.length > 256 || tok.includes("://")) return false;
  if ("([{'\"".includes(tok[0]) || ":,;".includes(tok[tok.length - 1])) return false;
  return !tok.includes("=") || tok.includes("&");
}

/**
 * Fin réelle d'une adresse : une espace dans un identifiant (`/live/Jean Dupont/mdp/1.ts`) coupe l'adresse au premier
 * blanc. On absorbe donc les jetons suivants (espaces seules, jamais un saut de ligne ; 6 jetons au plus) jusqu'au dernier
 * qui contient `/` ou `&`, tant qu'aucun ne ressemble à autre chose (`clé=valeur`, ponctuation de phrase).
 */
function extendOverSpaces(s, j) {
  let best = j, pos = j;
  for (let k = 0; k < CONT_MAX_TOKENS; k++) {
    let p = pos;
    while (p < s.length && s[p] === " ") p++;
    if (p === pos || p >= s.length) break;
    let q = p;
    while (q < s.length && !isUrlStop(s[q]) && q - p <= 256) q++;
    const tok = s.slice(p, q);
    if (!isContinuation(tok)) break;
    if (tok.includes("/") || tok.includes("&")) best = q;
    pos = q;
  }
  return best;
}

function maskUrls(s) {
  let out = "", last = 0, m;
  SCHEME_RE.lastIndex = 0;
  while ((m = SCHEME_RE.exec(s))) {
    const start = m.index;
    const encoded = Boolean(m[2]) && m[2].length > 3;
    const scheme = (m[1] || m[3]).toLowerCase();
    const i = SCHEME_RE.lastIndex;
    const stop = encoded ? isEncStop : isUrlStop;
    let j = i;
    while (j < s.length && !stop(s[j])) j++;
    if (!encoded) j = extendOverSpaces(s, j);
    let end = j;
    while (end > i && TRAILING.includes(s[end - 1])) end--;
    out += s.slice(last, start) + reduceUrl(scheme, s.slice(i, end), { encoded, slashes: !m[3] });
    last = end;
    SCHEME_RE.lastIndex = Math.max(end, start + 1);
  }
  return out + s.slice(last);
}

// ---- règles par clé (JSON, paramètres, en-têtes) ----

const SECRET_NAMES = new Set([
  "username", "user", "login", "password", "passwd", "pass", "pwd", "secret", "token", "key", "auth", "authorization",
  "apikey", "api_key", "mac", "sn", "serial", "serialnumber", "serial_number", "device_id", "device_id2", "deviceid",
  "signature", "email", "e-mail", "mail", "sid", "sessionid", "phpsessid", "jsessionid", "cookie", "set-cookie",
  "x-plex-client-identifier", "install_id", "android_id", "imei",
]);
const SECRET_SUFFIXES = ["token", "secret", "password", "passwd", "apikey", "api_key", "_key", "-key", "auth", "authorization", "signature", "sessid"];
const HOST_NAMES = new Set([
  "url", "uri", "host", "hostname", "server", "server_url", "portal", "portal_url", "base_url", "baseurl", "stream_url",
  "direct_source", "epg_url", "m3u_url", "link",
]);
const AUTH_SCHEMES = /^(Bearer|Basic|Digest|MediaBrowser|Emby|Token|OAuth|Negotiate)\s+/i;

function nameKind(raw) {
  const n = raw.toLowerCase();
  if (SECRET_NAMES.has(n) || SECRET_SUFFIXES.some((s) => n.endsWith(s))) return "secret";
  if (HOST_NAMES.has(n)) return "host";
  return null;
}

// Nom (début de jeton, ou après un `&`, `?`, `;` encodé) + guillemet fermant éventuel + séparateur `=`, `:`, `%3D`, `%3A`.
const KEY_RE = /(?:(?<![\w-])|(?<=%(?:26|3F|3B|20|22|2C|7B)))([A-Za-z_][\w-]{0,63})(\\?["'])?(\s{0,8}(?:[=:]|%3D|%3A)\s{0,8})/gi;
const VALUE_MAX = 4096;

/** Étendue [début, fin) de la valeur qui suit le séparateur en [p], ou null (structure, valeur vide). */
function valueSpan(s, p, kind, rawName) {
  const c = s[p];
  if (c === "\\" && s[p + 1] === '"') { // JSON échappé dans une chaîne : jusqu'au prochain \"
    const q = s.indexOf('\\"', p + 2);
    return [p + 2, q < 0 ? Math.min(s.length, p + 2 + VALUE_MAX) : Math.min(q, p + 2 + VALUE_MAX)];
  }
  if (c === '"' || c === "'") {
    let q = p + 1;
    while (q < s.length && q - p <= VALUE_MAX && s[q] !== c) q += s[q] === "\\" ? 2 : 1;
    return [p + 1, Math.min(q, s.length)];
  }
  if (c === "{" || c === "[") return null; // objet : ses propres clés sont traitées ensuite
  let v = p;
  if (kind === "secret" && /auth/i.test(rawName)) { const a = s.slice(p, p + 32).match(AUTH_SCHEMES); if (a) v = p + a[0].length; }
  let q = v;
  while (q < s.length && q - v <= VALUE_MAX) {
    const ch = s[q];
    if (ch <= " " || "&;,\"'}])<>|".includes(ch)) break;
    if (ch === "%" && /^%(26|3B|22)/i.test(s.slice(q, q + 3))) break;
    q++;
  }
  return q > v ? [v, q] : null;
}

function maskKeyValues(s) {
  let out = "", last = 0, m;
  KEY_RE.lastIndex = 0;
  while ((m = KEY_RE.exec(s))) {
    const kind = nameKind(m[1]);
    const after = KEY_RE.lastIndex;
    if (!kind) { KEY_RE.lastIndex = m.index + m[1].length; continue; }
    const span = valueSpan(s, after, kind, m[1]);
    if (!span) { KEY_RE.lastIndex = after; continue; }
    const [vs, ve] = span;
    const value = s.slice(vs, ve);
    // Adresse déjà réduite par maskUrls (`url=http://<host#…>/…`) : rien à refaire.
    if (kind === "host" && (value.includes("://") || /^[A-Za-z][\w+.-]{0,19}:$/.test(value) || value.includes("<host#") || value.includes("<lan#"))) { KEY_RE.lastIndex = Math.max(ve, after); continue; }
    out += s.slice(last, vs) + (kind === "host" ? hostTag(value) : "<redacted>");
    last = ve;
    KEY_RE.lastIndex = Math.max(ve, after);
  }
  return out + s.slice(last);
}

// ---- règles par forme ----

const LOG_RULES = [
  // En-têtes entiers : cookies et autorisations (Emby/Jellyfin, Plex, Basic…) jusqu'à la fin de la ligne.
  [/(?<![\w-])(set-cookie|cookie|(?:x-emby-|x-mediabrowser-|proxy-)?authorization)(\s{0,8}:\s{0,8})[^\r\n]*/gi, "$1$2<redacted>"],
];
const SHAPE_RULES = [
  [/\butv_[A-Za-z0-9_-]{16,}/g, "utv_<redacted>"],
  // Sensible à la casse : « token_type=bearer expires_in=… » n'est pas un jeton.
  [/\b(Bearer|BEARER)\s+[A-Za-z0-9._~+/=-]+/g, "$1 <redacted>"],
  [/\bBasic\s+[A-Za-z0-9+/=]{8,}/g, "Basic <redacted>"],
  // JWT
  [/\beyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*/g, "<jwt>"],
  // MAC : 00:1A:79:…, 00-1a-79-…, encodée 00%3A1A%3A…
  [/(?<![0-9A-Fa-f])[0-9A-F]{2}([:-])[0-9A-F]{2}(?:\1[0-9A-F]{2}){4}(?![0-9A-Fa-f])/gi, "<mac>"],
  [/(?<![0-9A-Fa-f])[0-9A-F]{2}(?:%3A[0-9A-F]{2}){5}(?![0-9A-Fa-f])/gi, "<mac>"],
  // E-mail
  [/(?<![\w.+-])[\w.+-]{1,128}@[A-Za-z0-9-]{1,63}(?:\.[A-Za-z0-9-]{1,63})+/g, "<email>"],
];
// Hôte nommé hors adresse dans les messages réseau (OkHttp, Java) : « resolve host "x" », « connect to x/ip »…
const HOST_PHRASE = /(resolve host |Hostname |connect to |UnknownHostException: |Host: ?)("?\/?)([A-Za-z0-9-]{1,63}(?:\.[A-Za-z0-9-]{1,63})+)/g;
const IPV4 = /(?<![\w.])(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?!\w|\.\d)/g;
const IPV6 = /(?<![\w:.])(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?![\w:])/g;
// Chemin Xtream hors adresse (« GET /live/u/p/1.ts HTTP/1.1 ») : tout segment non vide.
const XTREAM_PATH = /\/(live|movie|series|vod|radio|timeshift)\/[^/\s?"]+\/[^/\s?"]+\//gi;

export function sanitizeLog(input, max = 4000) {
  if (typeof input !== "string") return "";
  let out = input.slice(0, max * 2).replace(CONTROL, "").replace(/\\\//g, "/");
  out = maskUrls(out);
  for (const [re, rep] of LOG_RULES) out = out.replace(re, rep);
  out = maskKeyValues(out);
  for (const [re, rep] of SHAPE_RULES) out = out.replace(re, rep);
  out = out.replace(HOST_PHRASE, (_, a, b, h) => `${a}${b}${hostTag(h)}`);
  out = out.replace(IPV4, (m0, ...o) => (o.slice(0, 4).every((x) => +x <= 255) ? (isPrivateHost(m0) ? "<lan>" : "<ip>") : m0));
  out = out.replace(IPV6, (m0) => {
    if (!/\d/.test(m0) || (!m0.includes("::") && (m0.match(/:/g) || []).length < 5)) return m0;
    return isPrivateHost(m0) ? "<lan>" : "<ip>";
  });
  out = out.replace(XTREAM_PATH, "/$1/<redacted>/<redacted>/");
  return out.slice(0, max);
}
