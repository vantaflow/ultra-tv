"use strict";
// DNS personnalisé d'Ultra TV (Réglages › Lecture › Réseau), aligné sur l'application Android.
//
// Deux piles réseau à couvrir :
//  - Chromium (net.fetch du cloud, mises à jour, renderer) : app.configureHostResolver. Chromium ne sait pas viser
//    un serveur DNS classique (UDP 53) : les modes Google / Cloudflare / Quad9 utilisent leur point DNS-over-HTTPS.
//    Le mode « Personnalisé » (adresses IP brutes) ne s'applique pas à Chromium, sauf adresse d'un fournisseur connu.
//  - Node (proxy loopback : catalogue, guide, images ET flux vidéo du fournisseur) : fonction `lookup` des agents
//    http/https. DNS classique via dns.promises.Resolver, DoH via un petit client RFC 8484 (format binaire, GET).
//
// Règle d'or : un serveur personnalisé injoignable ne casse jamais une requête. Au-delà de 2 s (ou réponse vide),
// on retombe sur dns.lookup (DNS du système). Les résolutions sont mises en cache (TTL, 60 s minimum).
// Aucun nom d'hôte ni URL n'est journalisé.

const dns = require("node:dns");
const net = require("node:net");
const http = require("node:http");
const https = require("node:https");

const TIMEOUT_MS = 2000;
const MIN_TTL_S = 60;
const MAX_TTL_S = 3600;
const MAX_CACHE = 512;

const MODES = ["system", "google", "cloudflare", "quad9", "doh", "custom"];
const DOH_PRESETS = ["cloudflare", "google", "quad9", "custom"];

/** Serveurs DNS classiques des modes prédéfinis (identiques à Android). */
const PLAIN_PRESETS = {
  google: ["8.8.8.8", "8.8.4.4"],
  cloudflare: ["1.1.1.1", "1.0.0.1"],
  quad9: ["9.9.9.9", "149.112.112.112"],
};

/** Points DoH ; `bootstrap` = adresses du serveur DoH lui-même (on ne dépend pas du DNS système pour le joindre). */
const DOH = {
  cloudflare: { url: "https://cloudflare-dns.com/dns-query", bootstrap: ["1.1.1.1", "1.0.0.1"] },
  google: { url: "https://dns.google/dns-query", bootstrap: ["8.8.8.8", "8.8.4.4"] },
  quad9: { url: "https://dns.quad9.net/dns-query", bootstrap: ["9.9.9.9", "149.112.112.112"] },
};

const DEFAULT_SETTINGS = Object.freeze({ mode: "system", dohPreset: "cloudflare", dohUrl: "", primary: "", secondary: "" });

// ---------------------------------------------------------------------------
// Réglage
// ---------------------------------------------------------------------------

const str = (v, max) => (typeof v === "string" ? v.trim().slice(0, max) : "");

/** Réglage tolérant : valeur inconnue => défaut (« Système »). */
function normalizeSettings(raw) {
  const r = raw && typeof raw === "object" ? raw : {};
  const mode = MODES.includes(str(r.mode, 20).toLowerCase()) ? str(r.mode, 20).toLowerCase() : "system";
  const dohPreset = DOH_PRESETS.includes(str(r.dohPreset, 20).toLowerCase()) ? str(r.dohPreset, 20).toLowerCase() : "cloudflare";
  return { mode, dohPreset, dohUrl: str(r.dohUrl, 512), primary: str(r.primary, 64), secondary: str(r.secondary, 64) };
}

/** URL DoH valide (https uniquement, sans identifiants) ou null. */
function validDohUrl(u) {
  try {
    const p = new URL(String(u || ""));
    if (p.protocol !== "https:" || !p.hostname || p.username || p.password) return null;
    return p.toString();
  } catch {
    return null;
  }
}

/**
 * Résolveur effectif : { kind: "system" } | { kind: "plain", servers } | { kind: "doh", url, bootstrap }.
 * Un réglage inutilisable (adresse manquante ou invalide) se comporte comme « Système ».
 */
function effectiveResolver(settings) {
  const s = normalizeSettings(settings);
  if (s.mode === "google" || s.mode === "cloudflare" || s.mode === "quad9") return { kind: "plain", servers: PLAIN_PRESETS[s.mode] };
  if (s.mode === "custom") {
    const servers = [...new Set([s.primary, s.secondary].filter((ip) => net.isIP(ip)))];
    return servers.length ? { kind: "plain", servers } : { kind: "system" };
  }
  if (s.mode === "doh") {
    if (s.dohPreset !== "custom") return { kind: "doh", url: DOH[s.dohPreset].url, bootstrap: DOH[s.dohPreset].bootstrap };
    const url = validDohUrl(s.dohUrl);
    return url ? { kind: "doh", url, bootstrap: [] } : { kind: "system" };
  }
  return { kind: "system" };
}

/** Clé de cache : changer de serveur invalide de fait les résolutions obtenues avec l'ancien. */
function resolverKey(r) {
  if (r.kind === "plain") return "plain|" + r.servers.join(",");
  if (r.kind === "doh") return "doh|" + r.url;
  return "system";
}

/** Fournisseur connu d'une adresse IP (mode « Personnalisé ») : Chromium peut alors utiliser son point DoH. */
function knownProviderOf(ip) {
  for (const [k, list] of Object.entries(PLAIN_PRESETS)) if (list.includes(ip)) return k;
  return null;
}

/**
 * Options de app.configureHostResolver. Mode « automatic » : DoH d'abord, DNS du système en repli (comme côté
 * Node, une panne du serveur choisi ne coupe pas l'application). `appliesToChromium` = faux quand Chromium garde
 * le DNS du système faute de point DoH (adresses IP personnalisées inconnues).
 */
function chromiumResolverConfig(settings) {
  const s = normalizeSettings(settings);
  const system = { secureDnsMode: "automatic", secureDnsServers: [], appliesToChromium: s.mode === "system" };
  const r = effectiveResolver(s);
  if (r.kind === "doh") return { secureDnsMode: "automatic", secureDnsServers: [r.url], appliesToChromium: true };
  if (r.kind === "plain") {
    const urls = [...new Set(r.servers.map(knownProviderOf).filter(Boolean).map((k) => DOH[k].url))];
    if (urls.length) return { secureDnsMode: "automatic", secureDnsServers: urls, appliesToChromium: true };
  }
  return system;
}

// ---------------------------------------------------------------------------
// DNS-over-HTTPS (RFC 8484, format binaire)
// ---------------------------------------------------------------------------

const TYPE_A = 1;
const TYPE_AAAA = 28;

/** Requête DNS binaire (id 0 recommandé par la RFC 8484 pour le cache HTTP, récursion demandée). */
function buildQuery(name, type) {
  const labels = String(name).replace(/\.$/, "").split(".");
  const parts = [Buffer.from([0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0])];
  for (const l of labels) {
    const b = Buffer.from(l, "ascii");
    if (b.length === 0 || b.length > 63) throw Object.assign(new Error("bad-name"), { code: "EBADNAME" });
    parts.push(Buffer.from([b.length]), b);
  }
  parts.push(Buffer.from([0, type >> 8, type & 0xff, 0, 1]));
  return Buffer.concat(parts);
}

/** Saute un nom (étiquettes ou pointeur de compression) ; retourne l'offset suivant. */
function skipName(buf, off) {
  for (let guard = 0; guard < 128; guard++) {
    if (off >= buf.length) throw new Error("truncated");
    const len = buf[off];
    if (len === 0) return off + 1;
    if ((len & 0xc0) === 0xc0) return off + 2;
    off += 1 + len;
  }
  throw new Error("bad-name");
}

/**
 * Analyse une réponse DNS binaire : adresses A / AAAA (chaîne CNAME comprise) et TTL minimal.
 * Lève une erreur si le message est tronqué ou si RCODE ≠ 0 (NXDOMAIN, SERVFAIL…).
 */
function parseResponse(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) throw new Error("truncated");
  const rcode = buf[3] & 0x0f;
  if (rcode !== 0) throw Object.assign(new Error("rcode-" + rcode), { code: rcode === 3 ? "ENOTFOUND" : "ESERVFAIL" });
  const qd = buf.readUInt16BE(4);
  const an = buf.readUInt16BE(6);
  let off = 12;
  for (let i = 0; i < qd; i++) off = skipName(buf, off) + 4;
  const addresses = [];
  let ttl = Infinity;
  for (let i = 0; i < an; i++) {
    off = skipName(buf, off);
    if (off + 10 > buf.length) throw new Error("truncated");
    const type = buf.readUInt16BE(off);
    const rrTtl = buf.readUInt32BE(off + 4);
    const rdlen = buf.readUInt16BE(off + 8);
    off += 10;
    if (off + rdlen > buf.length) throw new Error("truncated");
    if (type === TYPE_A && rdlen === 4) {
      addresses.push({ address: [...buf.subarray(off, off + 4)].join("."), family: 4 });
      ttl = Math.min(ttl, rrTtl);
    } else if (type === TYPE_AAAA && rdlen === 16) {
      const groups = [];
      for (let j = 0; j < 16; j += 2) groups.push(buf.readUInt16BE(off + j).toString(16));
      addresses.push({ address: compressV6(groups.join(":")), family: 6 });
      ttl = Math.min(ttl, rrTtl);
    }
    off += rdlen;
  }
  return { addresses, ttl: Number.isFinite(ttl) ? ttl : 0 };
}

/** Forme canonique d'une IPv6 (via l'analyseur d'URL de Node). */
function compressV6(full) {
  try {
    return new URL(`http://[${full}]/`).hostname.slice(1, -1);
  } catch {
    return full;
  }
}

/**
 * Une question DoH (GET ?dns=…). Le serveur DoH lui-même est joint par une adresse d'amorçage si fournie,
 * sinon par le DNS du système (jamais par ce module : pas de récursion). http:// n'est accepté qu'ici (tests).
 */
function dohQuery(url, name, type, { bootstrap = [], timeoutMs = TIMEOUT_MS, agent } = {}) {
  return new Promise((resolve, reject) => {
    let target;
    try {
      target = new URL(url);
      target.searchParams.set("dns", buildQuery(name, type).toString("base64url"));
    } catch (e) {
      reject(e);
      return;
    }
    const lib = target.protocol === "https:" ? https : http;
    const options = { method: "GET", headers: { accept: "application/dns-message" }, timeout: timeoutMs };
    if (agent) options.agent = agent;
    const boot = bootstrap.filter((ip) => net.isIP(ip));
    if (boot.length) {
      options.lookup = (_host, opts, cb) => {
        const list = boot.map((address) => ({ address, family: net.isIP(address) }));
        if (opts && opts.all) cb(null, list);
        else cb(null, list[0].address, list[0].family);
      };
    }
    const req = lib.request(target, options, (res) => {
      const chunks = [];
      let total = 0;
      res.on("data", (c) => {
        total += c.length;
        if (total > 65535) req.destroy(new Error("too-large"));
        else chunks.push(c);
      });
      res.on("end", () => {
        if (res.statusCode !== 200) return reject(Object.assign(new Error("http-" + res.statusCode), { code: "EDOHHTTP" }));
        try {
          resolve(parseResponse(Buffer.concat(chunks)));
        } catch (e) {
          reject(e);
        }
      });
      res.on("error", reject);
    });
    req.on("timeout", () => req.destroy(Object.assign(new Error("timeout"), { code: "ETIMEDOUT" })));
    req.on("error", reject);
    req.end();
  });
}

// ---------------------------------------------------------------------------
// Résolution personnalisée + cache + repli système
// ---------------------------------------------------------------------------

/** Fusionne A et AAAA (échec de l'un toléré si l'autre répond). */
async function bothFamilies(query) {
  const [a, aaaa] = await Promise.allSettled([query(TYPE_A), query(TYPE_AAAA)]);
  const ok = [a, aaaa].filter((r) => r.status === "fulfilled").map((r) => r.value);
  if (!ok.length) throw a.reason || aaaa.reason;
  const addresses = ok.flatMap((r) => r.addresses);
  const ttl = Math.min(...ok.filter((r) => r.addresses.length).map((r) => r.ttl), Infinity);
  return { addresses, ttl: Number.isFinite(ttl) ? ttl : 0 };
}

/** Résolution via un serveur DNS classique. `port` facultatif (tests : « 127.0.0.1:5353 »). */
async function resolvePlain(servers, host, timeoutMs = TIMEOUT_MS) {
  const resolver = new dns.promises.Resolver({ timeout: timeoutMs, tries: 1 });
  resolver.setServers(servers);
  const query = async (type) => {
    const recs = type === TYPE_A ? await resolver.resolve4(host, { ttl: true }) : await resolver.resolve6(host, { ttl: true });
    return { addresses: recs.map((r) => ({ address: r.address, family: type === TYPE_A ? 4 : 6 })), ttl: Math.min(...recs.map((r) => r.ttl), Infinity) };
  };
  try {
    return await bothFamilies(query);
  } finally {
    resolver.cancel();
  }
}

function resolveDoh(r, host, timeoutMs = TIMEOUT_MS) {
  return bothFamilies((type) => dohQuery(r.url, host, type, { bootstrap: r.bootstrap, timeoutMs }));
}

function withTimeout(p, ms) {
  let t;
  return Promise.race([
    p,
    new Promise((_, reject) => {
      t = setTimeout(() => reject(Object.assign(new Error("timeout"), { code: "ETIMEDOUT" })), ms);
    }),
  ]).finally(() => clearTimeout(t));
}

const LOCAL_NAMES = /^(localhost|.*\.localhost|.*\.local)$/i;

/**
 * Crée le résolveur de l'application.
 *  - `getSettings()` : réglage courant (lu à chaque résolution).
 *  - `resolve(resolver, host)` : injectable (tests) ; par défaut DNS classique ou DoH.
 *  - `systemLookup` : dns.lookup par défaut (repli).
 * Retourne { lookup, resolveHost, clearCache } ; `lookup` a la signature de dns.lookup (agents http/https).
 */
function createResolver({ getSettings, resolve, systemLookup = dns.lookup, timeoutMs = TIMEOUT_MS, now = Date.now } = {}) {
  const cache = new Map();
  const doResolve = resolve || ((r, host) => (r.kind === "doh" ? resolveDoh(r, host, timeoutMs) : resolvePlain(r.servers, host, timeoutMs)));

  function sysResolve(host, family) {
    return new Promise((ok, ko) => {
      systemLookup(host, { all: true, family: family || 0 }, (err, list) => (err ? ko(err) : ok(list)));
    });
  }

  /** Résout `host` ; retourne { addresses, via: "custom" | "system" | "cache", reason? }. Ne lève que si le système échoue aussi. */
  async function resolveHost(host, family = 0) {
    const r = effectiveResolver(typeof getSettings === "function" ? getSettings() : DEFAULT_SETTINGS);
    const pick = (list) => (family ? list.filter((a) => a.family === family) : list);
    if (r.kind === "system" || net.isIP(host) || LOCAL_NAMES.test(host) || !host.includes(".")) {
      return { addresses: await sysResolve(host, family), via: "system" };
    }
    const key = resolverKey(r) + "|" + host.toLowerCase();
    const hit = cache.get(key);
    if (hit && hit.expires > now() && pick(hit.addresses).length) return { addresses: pick(hit.addresses), via: "cache" };
    let reason;
    try {
      const res = await withTimeout(Promise.resolve(doResolve(r, host)), timeoutMs);
      if (res && res.addresses.length) {
        const ttl = Math.min(Math.max(res.ttl || 0, MIN_TTL_S), MAX_TTL_S);
        if (cache.size >= MAX_CACHE) cache.delete(cache.keys().next().value);
        cache.set(key, { addresses: res.addresses, expires: now() + ttl * 1000 });
        const own = pick(res.addresses);
        if (own.length) return { addresses: own, via: "custom" };
      }
      reason = "empty";
    } catch (e) {
      reason = (e && e.code) || "error";
    }
    return { addresses: await sysResolve(host, family), via: "system", reason };
  }

  function lookup(hostname, options, callback) {
    if (typeof options === "function") {
      callback = options;
      options = {};
    } else if (typeof options === "number") {
      options = { family: options };
    }
    const opts = options || {};
    const family = opts.family === "IPv4" ? 4 : opts.family === "IPv6" ? 6 : Number(opts.family) || 0;
    resolveHost(String(hostname), family).then(
      ({ addresses }) => {
        if (!addresses.length) {
          const err = Object.assign(new Error(`getaddrinfo ENOTFOUND`), { code: "ENOTFOUND", hostname });
          return callback(err);
        }
        // IPv4 d'abord (comme verbatim=false) : les panneaux IPTV sont rarement joignables en IPv6.
        const sorted = [...addresses].sort((a, b) => a.family - b.family);
        if (opts.all) callback(null, sorted);
        else callback(null, sorted[0].address, sorted[0].family);
      },
      (err) => callback(err),
    );
  }

  return { lookup, resolveHost, clearCache: () => cache.clear() };
}

module.exports = {
  DEFAULT_SETTINGS,
  PLAIN_PRESETS,
  DOH,
  normalizeSettings,
  validDohUrl,
  effectiveResolver,
  chromiumResolverConfig,
  buildQuery,
  parseResponse,
  dohQuery,
  resolvePlain,
  createResolver,
};
