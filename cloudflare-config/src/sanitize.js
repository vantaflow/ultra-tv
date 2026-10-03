// Nettoyage défensif de tout texte venant d'un appareil avant stockage.
// Le client nettoie déjà (RemoteLog.sanitize) mais le serveur ne lui fait pas
// confiance : un APK ancien, modifié ou un tiers peut envoyer n'importe quoi.

const SENSITIVE_PARAMS =
  "username|user|login|password|passwd|pass|pwd|token|apikey|api_key|secret|auth|authorization|key";

const RULES = [
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

// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g;

export function sanitizeText(input, max = 4000) {
  if (typeof input !== "string") return "";
  let out = input.slice(0, max * 2).replace(CONTROL, "");
  for (const [re, rep] of RULES) out = out.replace(re, rep);
  return out.slice(0, max);
}
