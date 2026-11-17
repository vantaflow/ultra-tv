"use strict";
// Détection automatique des serveurs multimédia du réseau local : Jellyfin (UDP 7359, « Who is JellyfinServer? »), Emby (UDP 7359,
// « who is EmbyServer? ») et Plex (GDM : « M-SEARCH * HTTP/1.1 » en multicast 239.0.0.250:32414). Le parsing des réponses est fait
// par des fonctions pures (testées) ; l'écoute dure peu (3 s par défaut) et ne bloque jamais l'interface : [discover] est asynchrone,
// ne lève jamais et renvoie ce qui a répondu. Aucune adresse n'est journalisée.

const dgram = require("node:dgram");

const JELLYFIN_MSG = "Who is JellyfinServer?";
const EMBY_MSG = "who is EmbyServer?";
const MEDIA_PORT = 7359;
const GDM_MSG = "M-SEARCH * HTTP/1.1\r\n\r\n";
const GDM_ADDR = "239.0.0.250";
const GDM_PORT = 32414;
const DEFAULT_TIMEOUT_MS = 3000;

/** Adresse http(s) propre (sans barre finale), ou null si ce n'est pas une adresse exploitable. */
function cleanAddress(raw) {
  const s = String(raw ?? "").trim().replace(/\/+$/, "");
  if (!/^https?:\/\/[^\s/]+/i.test(s)) return null;
  return s;
}

/** Réponse JSON d'un serveur Jellyfin / Emby : { Address, Id, Name }. */
function parseMediaReply(buf, kind, fromAddress) {
  let j;
  try { j = JSON.parse(Buffer.from(buf).toString("utf8")); } catch { return null; }
  if (!j || typeof j !== "object") return null;
  const address = cleanAddress(j.Address) ?? (fromAddress ? `http://${fromAddress}:8096` : null);
  if (!address) return null;
  const id = String(j.Id ?? "").trim();
  const name = String(j.Name ?? "").trim() || (kind === "emby" ? "Emby" : "Jellyfin");
  return { kind, name, address, id: id || address };
}

/** Réponse GDM de Plex : en-têtes « Clé: valeur » ; l'adresse est celle de l'expéditeur, le port est annoncé (32400 par défaut). */
function parseGdmReply(buf, fromAddress) {
  const text = Buffer.from(buf).toString("utf8");
  if (!/^HTTP\/1\.[01] 200/i.test(text)) return null;
  const h = {};
  for (const line of text.split(/\r?\n/).slice(1)) {
    const i = line.indexOf(":");
    if (i > 0) h[line.slice(0, i).trim().toLowerCase()] = line.slice(i + 1).trim();
  }
  if (!fromAddress) return null;
  const port = Number.parseInt(h.port ?? "", 10);
  const p = Number.isInteger(port) && port > 0 && port < 65536 ? port : 32400;
  const id = h["resource-identifier"] || "";
  return { kind: "plex", name: h.name || "Plex", address: `http://${fromAddress}:${p}`, id: id || `${fromAddress}:${p}` };
}

/** Fusionne les réponses : un serveur répondant plusieurs fois (plusieurs interfaces) n'apparaît qu'une fois ; ordre stable. */
function mergeServers(list) {
  const seen = new Map();
  for (const s of list) {
    if (!s) continue;
    const key = `${s.kind}:${s.id}`;
    if (!seen.has(key)) seen.set(key, s);
  }
  return [...seen.values()].sort((a, b) => a.name.localeCompare(b.name) || a.kind.localeCompare(b.kind));
}

/** Ouvre une socket, envoie le message et transmet chaque réponse à [onReply] jusqu'à [timeoutMs] ; ne lève jamais. */
function probe({ message, port, target, timeoutMs, multicast, onReply }) {
  return new Promise((resolve) => {
    let sock;
    let finished = false;
    const done = () => { if (finished) return; finished = true; try { sock?.close(); } catch { /* déjà fermée */ } resolve(); };
    try {
      sock = dgram.createSocket({ type: "udp4", reuseAddr: true });
      sock.on("error", done);
      sock.on("message", (msg, rinfo) => { try { onReply(msg, rinfo.address); } catch { /* réponse illisible : ignorée */ } });
      sock.bind(0, () => {
        try {
          if (multicast) sock.setMulticastTTL(2); else sock.setBroadcast(true);
          sock.send(Buffer.from(message), port, target, () => undefined);
        } catch { done(); }
      });
      setTimeout(done, timeoutMs);
    } catch { done(); }
  });
}

/** Cherche les serveurs pendant [timeoutMs] ; le tout en parallèle, jamais d'exception. */
async function discover({ timeoutMs = DEFAULT_TIMEOUT_MS } = {}) {
  const found = [];
  await Promise.all([
    probe({ message: JELLYFIN_MSG, port: MEDIA_PORT, target: "255.255.255.255", timeoutMs, onReply: (m, a) => found.push(parseMediaReply(m, "jellyfin", a)) }),
    probe({ message: EMBY_MSG, port: MEDIA_PORT, target: "255.255.255.255", timeoutMs, onReply: (m, a) => found.push(parseMediaReply(m, "emby", a)) }),
    probe({ message: GDM_MSG, port: GDM_PORT, target: GDM_ADDR, timeoutMs, multicast: true, onReply: (m, a) => found.push(parseGdmReply(m, a)) }),
  ]);
  return mergeServers(found);
}

module.exports = { parseMediaReply, parseGdmReply, mergeServers, cleanAddress, discover, JELLYFIN_MSG, EMBY_MSG, GDM_MSG };
