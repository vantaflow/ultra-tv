"use strict";
// Journal de lecture du bureau (diagnostic des échecs) : fichier rotatif dans le dossier des journaux d'Electron.
// Jamais d'identifiants : toute chaîne passe par maskSecrets (adresses Xtream /user/pass/, paramètres sensibles, jeton du proxy local).
// Taille bornée : 5 Mo par fichier, 3 fichiers (playback.log, playback.1.log, playback.2.log).

const fs = require("node:fs");
const path = require("node:path");

const MAX_BYTES = 5 * 1024 * 1024;
const FILES = 3;
const SENSITIVE_PARAM = /([?&;](?:username|user|login|password|pass|passwd|pwd|token|access_token|auth|authorization|key|api_key|apikey|secret|sig|signature|mac|sn|device_id|deviceid|stb_id)=)[^&\s"'#]*/gi;

/** Masque identifiants et jetons dans un texte libre (fonction pure, testée). */
function maskSecrets(text) {
  let s = String(text);
  // Adresses du proxy local : jeton puis adresse d'origine encodée (qui porte les identifiants) : on ne garde que l'hôte local.
  s = s.replace(/(https?:\/\/(?:127\.0\.0\.1|localhost|\[::1\]):\d+)\/[^\s"'<>]*/gi, "$1/***");
  // user:pass@hôte
  s = s.replace(/(\/\/)[^/\s@:]+:[^/\s@]*@/g, "$1***@");
  // Xtream : /live|movie|series|timeshift/user/pass/…
  s = s.replace(/(\/(?:live|movie|series|timeshift|vod)\/)[^/\s?#"']+\/[^/\s?#"']+(?=\/)/gi, "$1***/***");
  // Xtream sans préfixe : http://hôte:port/user/pass/123(.ext)
  s = s.replace(/(https?:\/\/[^/\s"']+\/)[^/\s?#"']+\/[^/\s?#"']+(?=\/\d+(?:\.[a-z0-9]{2,5})?(?:[?#\s"']|$))/gi, "$1***/***");
  // /tx/<base64url de l'adresse d'origine>, /txi/, /txd/, /txs/
  s = s.replace(/\/(txi?|txd|txs)\/[A-Za-z0-9_-]{8,}/g, "/$1/***");
  s = s.replace(SENSITIVE_PARAM, "$1***");
  return s;
}

/** Valeur journalisable : nombres et booléens tels quels, chaînes masquées et bornées, objets aplatis (profondeur 2). */
function cleanValue(v, depth = 0) {
  if (v == null) return null;
  if (typeof v === "number") return Number.isFinite(v) ? v : null;
  if (typeof v === "boolean") return v;
  if (typeof v === "string") return maskSecrets(v).replace(/[\r\n]+/g, " ").slice(0, 400);
  if (Array.isArray(v) && depth < 2) return v.slice(0, 20).map((x) => cleanValue(x, depth + 1));
  if (typeof v === "object" && depth < 2) {
    const o = {};
    for (const [k, x] of Object.entries(v).slice(0, 30)) o[String(k).slice(0, 40)] = cleanValue(x, depth + 1);
    return o;
  }
  return null;
}

/** Ligne de journal (JSON, une par événement) ; testée. */
function formatLine(event, fields, now = new Date()) {
  const o = { t: now.toISOString(), event: String(event).replace(/[^\w.:-]/g, "_").slice(0, 64) };
  for (const [k, v] of Object.entries(fields && typeof fields === "object" ? fields : {})) {
    if (k === "t" || k === "event") continue;
    o[String(k).replace(/[^\w.-]/g, "_").slice(0, 40)] = cleanValue(v);
  }
  return JSON.stringify(o);
}

/** Journal rotatif : `dir` créé au besoin ; toute erreur d'écriture est ignorée (le journal ne doit jamais gêner la lecture). */
function createLogger({ dir, maxBytes = MAX_BYTES, files = FILES, fsImpl = fs, now = () => new Date() } = {}) {
  const name = (i) => path.join(dir, i === 0 ? "playback.log" : `playback.${i}.log`);
  let size = -1;
  function rotate() {
    try { fsImpl.unlinkSync(name(files - 1)); } catch { /* absent */ }
    for (let i = files - 2; i >= 0; i--) {
      try { fsImpl.renameSync(name(i), name(i + 1)); } catch { /* absent */ }
    }
    size = 0;
  }
  function log(event, fields) {
    try {
      const line = formatLine(event, fields, now()) + "\n";
      if (size < 0) {
        fsImpl.mkdirSync(dir, { recursive: true });
        try { size = fsImpl.statSync(name(0)).size; } catch { size = 0; }
      }
      if (size + Buffer.byteLength(line) > maxBytes) rotate();
      fsImpl.appendFileSync(name(0), line);
      size += Buffer.byteLength(line);
    } catch { /* journal indisponible */ }
  }
  return { log, dir };
}

module.exports = { maskSecrets, formatLine, createLogger, MAX_BYTES, FILES };
