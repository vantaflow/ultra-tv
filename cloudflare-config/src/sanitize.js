// Nettoyage défensif de tout texte venant d'un appareil avant stockage.
// Le client nettoie déjà (LogSanitizer / RemoteLog.sanitize) mais le serveur ne lui fait pas confiance : un APK ancien,
// modifié ou un tiers peut envoyer n'importe quoi. Le serveur est la dernière barrière.
//
// Deux fonctions :
// - `sanitizeLog` : journaux et plantages (message, pile, étiquette, modèle…). Liste blanche : toute adresse est réduite
//   à « schéma + empreinte de l'hôte + type de ressource », puis des règles par clé (JSON, paramètres, en-têtes) et par
//   forme (MAC, e-mail, IP, jetons) retirent ce qui reste hors des adresses. Vecteurs : test/fixtures/sanitize-vectors.json.
// - `sanitizeText` : textes courts saisis (nom d'appareil, libellé d'appairage, nom de source). Règles historiques,
//   plus légères : un libellé d'appairage est une étiquette MAC que l'on garde lisible.
//
// Empreinte d'hôte : HMAC-SHA-256 de l'hôte (jamais du chemin) avec un secret du Worker (`LOG_HOST_SALT`, passé en
// `hostKey`), 12 chiffres hexadécimaux. Sans secret : `<host>` / `<lan>` sans empreinte (jamais d'empreinte non salée,
// qui se renverse par force brute pour une IPv4 publique). Calcul synchrone en JS pur : `sanitizeLog` reste synchrone.
//
// Coût : chaque passe est linéaire (classes de caractères disjointes, débuts de jeton ancrés par des regards en arrière,
// répétitions et balayages bornés) ; aucune n'a de retour arrière catastrophique (test de durée dédié).

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

// ---- empreinte d'hôte : HMAC-SHA-256 synchrone ---------------------------------------------------------------------

let K256 = null, H256 = null;
function initSha256() {
  K256 = new Uint32Array(64); H256 = new Uint32Array(8);
  const primes = [];
  for (let n = 2; primes.length < 64; n++) if (primes.every((p) => n % p)) primes.push(n);
  const frac = (x) => ((x - Math.floor(x)) * 0x100000000) >>> 0;
  primes.forEach((p, i) => { K256[i] = frac(Math.cbrt(p)); if (i < 8) H256[i] = frac(Math.sqrt(p)); });
}
const ror = (x, n) => (x >>> n) | (x << (32 - n));

/** SHA-256 (FIPS 180-4) d'octets ; vérifié contre Web Crypto dans les tests. */
export function sha256(msg) {
  if (!K256) initSha256();
  const len = msg.length;
  const buf = new Uint8Array(((len + 9 + 63) >> 6) << 6);
  buf.set(msg); buf[len] = 0x80;
  const dv = new DataView(buf.buffer);
  dv.setUint32(buf.length - 8, Math.floor(len / 0x20000000));
  dv.setUint32(buf.length - 4, (len * 8) >>> 0);
  const h = H256.slice(), w = new Uint32Array(64);
  for (let off = 0; off < buf.length; off += 64) {
    for (let t = 0; t < 16; t++) w[t] = dv.getUint32(off + t * 4);
    for (let t = 16; t < 64; t++) {
      const a = w[t - 15], b = w[t - 2];
      w[t] = w[t - 16] + (ror(a, 7) ^ ror(a, 18) ^ (a >>> 3)) + w[t - 7] + (ror(b, 17) ^ ror(b, 19) ^ (b >>> 10));
    }
    let a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], k = h[7];
    for (let t = 0; t < 64; t++) {
      const t1 = (k + (ror(e, 6) ^ ror(e, 11) ^ ror(e, 25)) + ((e & f) ^ (~e & g)) + K256[t] + w[t]) | 0;
      const t2 = ((ror(a, 2) ^ ror(a, 13) ^ ror(a, 22)) + ((a & b) ^ (a & c) ^ (b & c))) | 0;
      k = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0;
    }
    h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += k;
  }
  const out = new Uint8Array(32);
  new DataView(out.buffer).setUint32(0, h[0]);
  for (let i = 1; i < 8; i++) new DataView(out.buffer).setUint32(i * 4, h[i]);
  return out;
}

/** HMAC-SHA-256 (RFC 2104) ; clé et message en octets. */
export function hmacSha256(key, msg) {
  let k = key.length > 64 ? sha256(key) : key;
  const pad = new Uint8Array(64); pad.set(k);
  const inner = new Uint8Array(64 + msg.length), outer = new Uint8Array(64 + 32);
  for (let i = 0; i < 64; i++) { inner[i] = pad[i] ^ 0x36; outer[i] = pad[i] ^ 0x5c; }
  inner.set(msg, 64);
  outer.set(sha256(inner), 64);
  return sha256(outer);
}

const utf8 = (s) => new TextEncoder().encode(s);
const fpCache = new Map();

/**
 * Empreinte d'un hôte : 12 premiers chiffres hexadécimaux de HMAC-SHA-256(clé, hôte en minuscules), ou null sans clé.
 * L'appli ne connaît pas la clé : elle ne peut pas produire la même empreinte (elle écrit `<host>`).
 */
export function hostFingerprint(host, key) {
  if (!key) return null;
  const h = String(host).toLowerCase();
  const ck = `${key}\u0000${h}`;
  let fp = fpCache.get(ck);
  if (!fp) {
    fp = Array.from(hmacSha256(utf8(String(key)), utf8(h)).subarray(0, 6), (b) => b.toString(16).padStart(2, "0")).join("");
    if (fpCache.size > 1000) fpCache.clear();
    fpCache.set(ck, fp);
  }
  return fp;
}

// ---- sanitizeLog (journaux et plantages) -------------------------------------------------------------------------

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

// Clé de l'appel en cours de `sanitizeLog` (le nettoyage est synchrone : aucun autre appel ne peut s'intercaler) et
// nombre d'empreintes nouvelles encore permises dans cet appel : au-delà, `<host>` sans empreinte (une charge faite de
// milliers d'hôtes différents ne doit pas dépasser la limite de temps CPU du Worker).
let curKey = null, fpBudget = 0;
const FP_PER_CALL = 64;

/** `<host#empreinte>` (`<lan#…>` pour une adresse locale), ou `<host>` / `<lan>` sans clé ; le port n'entre pas dans l'empreinte. */
const hostTag = (raw) => {
  const str = String(raw);
  const host = (str.match(/:/g) || []).length === 1 ? str.replace(/:\d{0,5}$/, "") : str;
  const kind = isPrivateHost(host) ? "lan" : "host";
  let fp = null;
  if (curKey) {
    fp = fpCache.get(`${curKey}\u0000${host.toLowerCase()}`) || null;
    if (!fp && fpBudget > 0) { fpBudget--; fp = hostFingerprint(host, curKey); }
  }
  return fp ? `<${kind}#${fp}>` : `<${kind}>`;
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
/** Types Xtream dont la forme longue est `/type/identifiant/mot de passe/id`. */
const XTREAM_KINDS = new Set(["live", "movie", "series", "timeshift"]);

const decodePct = (s) => s.replace(/%([0-9a-f]{2})/gi, (_, h) => String.fromCharCode(parseInt(h, 16)));

/** Partie chemin réduite : premier mot connu + `/*` s'il reste des segments + extension connue + `?*` s'il y avait une requête. */
function reduceRest(rest) {
  let cut = rest.length;
  for (const c of ["?", "#"]) { const i = rest.indexOf(c); if (i >= 0 && i < cut) cut = i; }
  const hasQuery = rest.slice(cut).startsWith("?") && rest.length > cut + 1;
  const segs = rest.slice(0, cut).split("/").filter(Boolean);
  const kind = segs.map((s) => s.toLowerCase()).find((s) => PATH_KINDS.has(s)) || null;
  const lastSeg = segs.length ? segs[segs.length - 1].toLowerCase() : "";
  const extM = lastSeg.match(/\.([a-z0-9]{2,5})$/);
  const ext = extM && PATH_EXTS.has(extM[1]) && lastSeg !== kind ? extM[1] : null;
  let out = kind ? `/${kind}` : "";
  if (segs.length > (kind ? 1 : 0)) out += ext ? `/*.${ext}` : "/*";
  if (hasQuery) out += "?*";
  return out;
}

function reduceUrlIn(scheme, body, encoded, slashes) {
  const b = encoded ? decodePct(body) : body;
  let ae = b.length;
  for (const c of ["/", "?", "#"]) { const i = b.indexOf(c); if (i >= 0 && i < ae) ae = i; }
  const authority = b.slice(0, ae);
  const hostPort = authority.slice(authority.lastIndexOf("@") + 1);
  let host = hostPort;
  if (hostPort.startsWith("[")) host = hostPort.slice(0, hostPort.indexOf("]") + 1 || hostPort.length);
  else if ((hostPort.match(/:/g) || []).length === 1) host = hostPort.slice(0, hostPort.indexOf(":"));
  const prefix = scheme ? `${scheme}:${slashes ? "//" : ""}` : "";
  return prefix + (host ? hostTag(host) : "") + reduceRest(b.slice(ae));
}

/**
 * Adresse réduite : `schéma://<host#empreinte>` + premier mot connu du chemin + `/*` s'il reste des segments
 * + extension connue du dernier segment + `?*` s'il y avait une requête. Identifiants de l'autorité, segments,
 * valeurs de paramètres et fragment ne survivent jamais. `scheme` null : pas de schéma (valeur d'une clé d'hôte).
 */
export function reduceUrl(scheme, body, { encoded = false, slashes = true, hostKey = null } = {}) {
  const prev = [curKey, fpBudget];
  curKey = hostKey || null; fpBudget = FP_PER_CALL;
  try { return reduceUrlIn(scheme, body, encoded, slashes); } finally { [curKey, fpBudget] = prev; }
}

// Début d'adresse :
// 1. schéma quelconque en début de jeton, suivi de `://` ou `%3A%2F%2F` (adresse encodée) ;
// 2. schéma connu SANS frontière avant : collé à un chiffre, à `-` ou à un `%xx` (`403http://`, `%22http://`,
//    `url%3Dhttp://`) ;
// 3. `stalker:` sans barres.
const SCHEME_RE = /(?<![A-Za-z0-9+.-])([A-Za-z][A-Za-z0-9+.-]{1,19})(:\/\/|%3A%2F%2F)|(https?|rtmp[est]?|rtsp[su]?|rtp|udp|mms[ht]?|srt|s?ftp|smb|stalker)(:\/\/|%3A%2F%2F)|(?<![A-Za-z0-9+.-])(stalker):(?=[^\s/])/gi;
const isUrlStop = (c) => c <= " " || c === '"' || c === "<" || c === ">" || c === "`" || c === "\\";
const isEncStop = (c) => isUrlStop(c) || c === "&" || c === "'" || c === ";" || c === ",";
const TRAILING = ")]}'.,;:!?";
const CONT_MAX_TOKENS = 6;
const isBlank = (c) => c === " " || c === "\t";

/** Jeton (après une espace) qui peut prolonger une adresse coupée par une espace dans un identifiant. */
function isContinuation(tok) {
  if (!tok || tok.length > 256 || tok.includes("://")) return false;
  if ("([{'\"".includes(tok[0]) || ":,;".includes(tok[tok.length - 1])) return false;
  return !tok.includes("=") || tok.includes("&");
}

/**
 * Fin réelle d'une adresse : une espace dans un identifiant (`/live/Jean Dupont/mdp/1.ts`) coupe l'adresse au premier
 * blanc. On absorbe donc les jetons suivants (espaces ou tabulations, jamais un saut de ligne ; 6 jetons au plus)
 * jusqu'au dernier qui contient `/` ou `&`, tant qu'aucun ne ressemble à autre chose (`clé=valeur`, ponctuation).
 */
function extendOverSpaces(s, j) {
  let best = j, pos = j;
  for (let k = 0; k < CONT_MAX_TOKENS; k++) {
    let p = pos;
    while (p < s.length && isBlank(s[p])) p++;
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

/**
 * Caractère d'arrêt (`" < > \` ou accent grave) DANS un identifiant (`/QQU"x/QQP/1.ts`) : si la suite, jusqu'au
 * prochain blanc ou à la fin d'une chaîne JSON (`"` suivi de `, } ] : > ) ;`), contient un `/` (et pas `://`),
 * elle fait encore partie de l'adresse.
 */
function absorbStops(s, i, j) {
  if (j <= i || j >= s.length || s[j] <= " " || (s[j] === "<" && s[j + 1] === "/")) return j;
  let q = j;
  while (q < s.length && s[q] > " " && q - j <= 256) {
    if (s[q] === "\\") { if (q + 1 >= s.length || s[q + 1] <= " ") break; q += 2; continue; }
    if (s[q] === ":" && s.startsWith("://", q)) return j; // adresse suivante : pas une suite
    if (s[q] === '"' && (q + 1 >= s.length || s[q + 1] <= " " || ",}]:>);".includes(s[q + 1]))) break;
    q++;
  }
  q = Math.min(q, s.length);
  const part = s.slice(j, q);
  return part.includes("/") ? q : j;
}

/** Noms dont la valeur est saisie par une personne et peut contenir une espace (jamais un jeton). */
const CRED_NAMES = new Set(["username", "user", "login", "password", "passwd", "pass", "pwd", "psw", "pw"]);

/**
 * L'adresse finit-elle sur un identifiant (dernier paramètre `password=…`, ou forme longue Xtream à laquelle il manque
 * des segments) ? Sa fin peut alors être coupée par une espace : `?username=QQU&password=QQP QQPB`.
 */
function endsWithCredential(body) {
  const qi = body.indexOf("?");
  if (qi >= 0) {
    const last = body.slice(Math.max(body.lastIndexOf("?"), body.lastIndexOf("&"), body.lastIndexOf(";")) + 1);
    const eq = last.indexOf("=");
    return eq > 0 && CRED_NAMES.has(last.slice(0, eq).toLowerCase());
  }
  if (body.includes("#")) return false;
  const segs = body.split("/").slice(1).filter(Boolean);
  const k = segs.findIndex((x) => XTREAM_KINDS.has(x.toLowerCase()));
  const after = k < 0 ? 0 : segs.length - k - 1;
  return after >= 1 && after < 3 && !/\.[a-z0-9]{2,5}$/i.test(segs[segs.length - 1]);
}

/**
 * Suite d'une valeur d'identifiant coupée par une espace : jusqu'à 3 jetons (espaces ou tabulations, jamais un saut de
 * ligne) tant qu'ils ne contiennent ni `=` ni `:` et ne commencent pas par une ouverture. Le surmasquage d'un mot du
 * journal qui suivrait est accepté (`vidéo=-`, `code=401` arrêtent l'absorption). [isStop] : fin de jeton en plus du blanc.
 */
function absorbTail(s, j, isStop = null) {
  let pos = j;
  for (let k = 0; k < 3; k++) {
    let p = pos;
    while (p < s.length && isBlank(s[p])) p++;
    if (p === pos || p >= s.length) break;
    let q = p;
    while (q < s.length && s[q] > " " && !(isStop && isStop(s[q])) && q - p <= 256) q++;
    const tok = s.slice(p, q);
    if (!tok || /[=:]/.test(tok) || "([{\"'<".includes(tok[0])) break;
    pos = q;
    if (q < s.length && !isBlank(s[q])) break;
  }
  return pos;
}

function maskUrls(s) {
  let out = "", last = 0, m;
  SCHEME_RE.lastIndex = 0;
  while ((m = SCHEME_RE.exec(s))) {
    const start = m.index;
    const sep = m[2] || m[4] || "";
    const encoded = sep.length > 3;
    const scheme = (m[1] || m[3] || m[5]).toLowerCase();
    const i = SCHEME_RE.lastIndex;
    const stop = encoded ? isEncStop : isUrlStop;
    let j = i;
    while (j < s.length && !stop(s[j])) j++;
    if (!encoded) {
      for (let k = 0; k < 4; k++) { const n = extendOverSpaces(s, absorbStops(s, i, j)); if (n === j) break; j = n; }
      if (endsWithCredential(s.slice(i, j))) j = absorbTail(s, j);
    }
    let end = j;
    while (end > i && TRAILING.includes(s[end - 1])) end--;
    out += s.slice(last, start) + reduceUrlIn(scheme, s.slice(i, end), encoded, !m[5]);
    last = end;
    SCHEME_RE.lastIndex = Math.max(end, start + 1);
  }
  return out + s.slice(last);
}

// ---- requêtes et autorités sans schéma ----

const REQ_START = /\b(?:GET|POST|HEAD|PUT|DELETE|PATCH|OPTIONS) (?=\/)/g;

/**
 * Ligne de requête (`GET /QQU/QQP/123.ts HTTP/1.1`, espaces comprises jusqu'à ` HTTP/x` sur la même ligne) : chemin
 * réduit comme celui d'une adresse. Sans ` HTTP/` sur la ligne, le chemin court jusqu'au premier blanc.
 * Balayage linéaire : une zone déjà parcourue sans ` HTTP/` n'est pas relue.
 */
function maskRequestLines(s) {
  if (!s.includes("/")) return s;
  let out = "", last = 0, m, clearTo = 0;
  REQ_START.lastIndex = 0;
  while ((m = REQ_START.exec(s))) {
    const p = REQ_START.lastIndex;
    let e = -1, q = Math.max(p, clearTo);
    const limit = Math.min(s.length, p + 2048);
    for (; q < limit && s[q] !== "\n" && s[q] !== "\r"; q++) {
      if (s[q] === " " && s.startsWith(" HTTP/", q)) { e = q; break; }
    }
    if (e < 0) {
      clearTo = q;
      e = p;
      while (e < s.length && s[e] > " ") e++;
    }
    out += s.slice(last, p) + (reduceRest(s.slice(p, e)) || "/");
    last = e;
    REQ_START.lastIndex = Math.max(e, p);
  }
  return out + s.slice(last);
}

const HOST_RE_SRC = "(\\[[0-9A-Fa-f:.]{2,45}\\]|(?:[A-Za-z0-9-]{1,63}\\.){1,8}[A-Za-z]{2,63}|\\d{1,3}(?:\\.\\d{1,3}){3})";
// `QQU:QQP@hôte[:port][/chemin]` sans schéma.
const AUTH_NOSCHEME = new RegExp(`(?<![\\w.%+/:@-])[^\\s:/@"'<>()]{1,64}:[^\\s/@"'<>]{1,128}@${HOST_RE_SRC}(?::\\d{1,5})?(/[^\\s"'<>]*)?`, "g");
// `hôte:port/chemin` sans schéma (`connect qqhost.invalid:8080/QQU/QQP/1.ts`, `//qqhost.invalid:8080/…`).
const HOSTPORT_PATH = new RegExp(`(?<![\\w.@/-])(?://)?${HOST_RE_SRC}:\\d{1,5}(/[^\\s"'<>]*)`, "g");
// Chemin local avec un nom de compte (Windows, macOS, Linux).
const USER_PATH = /([A-Za-z]:[\\/](?:Users|Documents and Settings)[\\/]|\/Users\/|\/home\/)[^\\/\s"'<>]{1,64}/g;
// « login failed for QQU / QQP » (texte libre de forme connue).
const LOGIN_FAILED = /\b((?:log ?in|sign ?in|auth[a-z]{0,12}) failed for )[^\s,;]{1,128}(?:\s{0,4}\/\s{0,4}[^\s,;]{1,128})?/gi;

// ---- règles par clé (JSON, paramètres, en-têtes) ----

const SECRET_NAMES = new Set([
  "username", "user", "login", "password", "passwd", "pass", "pwd", "psw", "pw", "pin", "secret", "token", "key", "auth",
  "authorization", "apikey", "api_key", "mac", "sn", "serial", "serialnumber", "serial_number", "device_id", "device_id2",
  "deviceid", "signature", "email", "e-mail", "mail", "sid", "sessionid", "phpsessid", "jsessionid", "cookie", "set-cookie",
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
const isValueStop = (ch) => ch <= " " || "&;,\"'}])<>|".includes(ch);

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
  // Tableau en valeur d'une clé secrète (`"password":["QQP"]`) : son premier élément entre guillemets.
  if (c === "[" && kind === "secret" && (s[p + 1] === '"' || s[p + 1] === "'")) return valueSpan(s, p + 1, kind, "");
  if (c === "{" || c === "[") return null; // objet : ses propres clés sont traitées ensuite
  let v = p;
  if (kind === "secret" && /auth/i.test(rawName)) { const a = s.slice(p, p + 32).match(AUTH_SCHEMES); if (a) v = p + a[0].length; }
  let q = v;
  while (q < s.length && q - v <= VALUE_MAX) {
    const ch = s[q];
    if (isValueStop(ch)) break;
    if (ch === "%" && /^%(26|3B|22)/i.test(s.slice(q, q + 3))) break;
    q++;
  }
  if (q <= v) return null;
  // Identifiant saisi coupé par une espace (`password=QQP QQPB`) : sa fin suit.
  if (kind === "secret" && CRED_NAMES.has(rawName.toLowerCase()) && isBlank(s[q])) q = absorbTail(s, q, isValueStop);
  return [v, q];
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
    // Adresse déjà réduite (`url=http://<host#…>/…`, `url=<host>/*`) : rien à refaire.
    if (kind === "host" && (value.includes("://") || /^[A-Za-z][\w+.-]{0,19}:$/.test(value) || value.includes("<host") || value.includes("<lan"))) { KEY_RE.lastIndex = Math.max(ve, after); continue; }
    // Valeur d'une clé d'hôte : seul l'hôte entre dans l'empreinte, le chemin est réduit (jamais haché avec ses identifiants).
    out += s.slice(last, vs) + (kind === "host" ? reduceUrlIn(null, value, false, true) : "<redacted>");
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
  // MAC pointée (Cisco) : 001A.79AB.CDEF
  [/(?<![0-9A-Fa-f.])[0-9A-F]{4}\.[0-9A-F]{4}\.[0-9A-F]{4}(?![0-9A-Fa-f.])/gi, "<mac>"],
  // E-mail (lettres Unicode, `@` encodé `%40`)
  [/(?<![\p{L}\p{N}._+-])[\p{L}\p{N}._+-]{1,128}(?:@|%40)[\p{L}\p{N}-]{1,63}(?:\.[\p{L}\p{N}-]{1,63})+/gu, "<email>"],
  // XML : <password>QQP</password>
  [/<(password|passwd|pass|username|user|login|token|secret)>[^<]{1,256}<\/\1>/gi, "<$1><redacted></$1>"],
];
// Hôte nommé hors adresse dans les messages réseau (OkHttp, Java) : « resolve host "x" », « connect to x/ip »…
const HOST_PHRASE = /(resolve host |Hostname |connect to |UnknownHostException: |Host: ?|failed for |talking to |SNI )("?\/?)([A-Za-z0-9-]{1,63}(?:\.[A-Za-z0-9-]{1,63})+)/g;
const IPV4 = /(?<![\w.])(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(?!\w|\.\d)/g;
const IPV6 = /(?<![\w:.])(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?![\w:])/g;
// Chemin Xtream hors adresse (« chemin /live/u/p/1.ts ») : tout segment non vide.
const XTREAM_PATH = /\/(live|movie|series|vod|radio|timeshift)\/[^/\s?"]+\/[^/\s?"]+\//gi;
// Fin d'adresse Xtream restée hors adresse (adresse coupée par un saut de ligne ou trop de jetons) : `QQU/QQP/1.ts`.
// Début de jeton seulement (précédé d'un blanc, d'un guillemet ou du début) : chaque jeton n'est parcouru qu'une fois.
const XTREAM_TAIL = /(?<![^\s"'<>])(?:[^\s\/"'<>]{1,64}\/){1,2}\d{1,10}\.(ts|m3u8|mkv|mp4|avi|flv|mov)(?![\w\/])/gi;
// Échappements JSON de Gson et d'autres bibliothèques : \u002F (/), \u003A (:), \u003D (=), \u0026 (&), \u0040 (@), \u003F (?).
const UNICODE_ESC = /\\u00(2[fF]|3[aAdDfF]|26|40)/g;

/**
 * Journal ou plantage nettoyé, au plus [max] caractères. [hostKey] : secret du Worker (`LOG_HOST_SALT`) pour
 * l'empreinte d'hôte ; absent, les hôtes deviennent `<host>` / `<lan>` sans empreinte.
 */
export function sanitizeLog(input, max = 4000, { hostKey = null } = {}) {
  if (typeof input !== "string") return "";
  const prev = [curKey, fpBudget];
  curKey = hostKey || null; fpBudget = FP_PER_CALL;
  try {
    let out = input.slice(0, max * 2).replace(CONTROL, "").replace(/\\\//g, "/")
      .replace(UNICODE_ESC, (_, h) => String.fromCharCode(parseInt(h, 16)));
    // Double encodage (`%253A%252F%252F`, `%2520`) : un niveau retiré, la suite le traite comme un encodage simple.
    if (out.includes("%25")) out = out.replace(/%25([0-9A-Fa-f]{2})/g, "%$1");
    out = maskUrls(out);
    out = maskRequestLines(out);
    out = out.replace(AUTH_NOSCHEME, (_, h, path) => `<redacted>@${hostTag(h)}${path ? reduceRest(path) : ""}`);
    out = out.replace(HOSTPORT_PATH, (_, h, path) => `${hostTag(h)}${reduceRest(path)}`);
    for (const [re, rep] of LOG_RULES) out = out.replace(re, rep);
    out = maskKeyValues(out);
    for (const [re, rep] of SHAPE_RULES) out = out.replace(re, rep);
    out = out.replace(LOGIN_FAILED, "$1<redacted>");
    out = out.replace(HOST_PHRASE, (_, a, b, h) => `${a}${b}${hostTag(h)}`);
    out = out.replace(IPV4, (m0, ...o) => (o.slice(0, 4).every((x) => +x <= 255) ? (isPrivateHost(m0) ? "<lan>" : "<ip>") : m0));
    out = out.replace(IPV6, (m0) => {
      if (!/\d/.test(m0) || (!m0.includes("::") && (m0.match(/:/g) || []).length < 5)) return m0;
      return isPrivateHost(m0) ? "<lan>" : "<ip>";
    });
    out = out.replace(XTREAM_PATH, "/$1/<redacted>/<redacted>/");
    out = out.replace(XTREAM_TAIL, "<redacted>/*.$1");
    out = out.replace(USER_PATH, "$1<user>");
    return out.slice(0, max);
  } finally {
    [curKey, fpBudget] = prev;
  }
}
