// Guide TV (XMLTV) : lecture en flux, filtré sur les chaînes du catalogue et une fenêtre de temps
// [maintenant - 3 h ; maintenant + 48 h]. Les titres/descriptions sont décodés des entités XML.

import { db, getSetting } from "@/db/db";
import type { ProgramRow, Source } from "@/db/types";
import { hasGzipMagic } from "@/lib/gzip";
import { transportFetch, type Transport } from "@/net/transport";
import { xmltvUrl, xtreamJson } from "@/net/xtream";
import { credsOf } from "./core";


const XMLTV_TIME = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-])(\d{2})(\d{2}))?/;

export function parseXmltvTime(raw: string | null | undefined): number {
  if (!raw) return 0;
  const m = XMLTV_TIME.exec(raw.trim());
  if (!m) return 0;
  const utc = Date.UTC(+m[1]!, +m[2]! - 1, +m[3]!, +m[4]!, +m[5]!, +m[6]!);
  const off = m[7] ? (m[7] === "-" ? -1 : 1) * (+m[8]! * 60 + +m[9]!) * 60000 : 0;
  return utc - off;
}

const ENT: Record<string, string> = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };
export function decodeXml(s: string): string {
  return s
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e: string) => {
      if (e[0] === "#") {
        const cp = e[1] === "x" || e[1] === "X" ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
        return Number.isFinite(cp) ? String.fromCodePoint(cp) : m;
      }
      return ENT[e.toLowerCase()] ?? m;
    });
}

const PROG = /<programme\b([^>]*)>([\s\S]*?)<\/programme>/g;
const ATTR = (name: string) => new RegExp(`\\b${name}="([^"]*)"`);
const A_START = ATTR("start");
const A_STOP = ATTR("stop");
const A_CHAN = ATTR("channel");
const TITLE = /<title\b[^>]*>([\s\S]*?)<\/title>/;
const DESC = /<desc\b[^>]*>([\s\S]*?)<\/desc>/;

export interface EpgScan { push(chunk: string): void; end(): void }

/** Extrait les programmes d'un flux de texte XMLTV (par blocs), sans jamais garder le document entier. */
export function scanXmltv(
  wanted: Set<string>, from: number, to: number, emit: (rows: Omit<ProgramRow, "sourceId">[]) => void,
): EpgScan {
  let buf = "";
  let out: Omit<ProgramRow, "sourceId">[] = [];
  // Les identifiants XMLTV et ceux du catalogue ne varient que par la casse chez beaucoup de fournisseurs
  // (« TF1.fr » dans le guide, « tf1.fr » dans les chaînes) : on compare sans casse et on écrit l'identifiant du catalogue.
  // Un même identifiant peut exister sous plusieurs casses dans le catalogue (« tf1.fr » et « TF1.fr ») : une ligne par variante.
  const index = new Map<string, string[]>();
  for (const w0 of wanted) { const w = String(w0); const k = w.toLowerCase(); index.set(k, [...(index.get(k) ?? []), w]); }
  const drain = (final: boolean) => {
    PROG.lastIndex = 0;
    let last = 0;
    let m: RegExpExecArray | null;
    while ((m = PROG.exec(buf)) !== null) {
      last = PROG.lastIndex;
      const attrs = m[1]!;
      const chan = A_CHAN.exec(attrs)?.[1];
      if (!chan) continue;
      const channel = decodeXml(chan);
      const owns = index.get(channel.toLowerCase());
      if (!owns) continue;
      const start = parseXmltvTime(A_START.exec(attrs)?.[1]);
      const end = parseXmltvTime(A_STOP.exec(attrs)?.[1]);
      if (!start || end <= from || start >= to) continue;
      const title = decodeXml(TITLE.exec(m[2]!)?.[1] ?? "").trim();
      const desc = decodeXml(DESC.exec(m[2]!)?.[1] ?? "").trim().slice(0, 500);
      for (const own of owns) out.push({ epg: own, start, end, title, desc });
    }
    buf = final ? "" : buf.slice(last);
    if (out.length >= 2000 || (final && out.length)) { emit(out); out = []; }
  };
  return {
    push(chunk) {
      buf += chunk;
      if (buf.includes("</programme>")) drain(false);
      else if (buf.length > 1 << 16 && !buf.includes("<programme")) buf = buf.slice(-32); // en-tête : liste de chaînes
    },
    end() { drain(true); },
  };
}

/** Réglage « guide complémentaire » (adresse http(s) d'un guide XMLTV pour les chaînes sans programme) ; "" = aucun. */
export const EXTRA_EPG_KEY = "epg.extra";

export interface EpgPlan {
  /** Guide principal : remplace l'ancien guide de la source une fois reçu en entier. "" = aucun. */
  main: string;
  /** Guides lus ensuite, SEULEMENT pour les chaînes restées sans programme (le guide principal n'est pas touché). */
  complements: string[];
}

/**
 * Quels guides lire pour une source.
 *  - « Guide de cette source » ([Source.epgUrl]) en mode « remplacer » (défaut, comportement historique) : seul lui est lu ;
 *  - mode « compléter » : guide du fournisseur (Xtream : xmltv.php) puis cette adresse pour les chaînes sans programme.
 *    M3U / Stalker n'ont pas de guide de fournisseur : l'adresse est alors le guide principal ;
 *  - [extraUrl] (réglage global) complète en dernier, pour toutes les sources.
 */
export function planEpg(source: Source, extraUrl = ""): EpgPlan {
  const provider = source.type === "xtream" ? xmltvUrl(credsOf(source)) : "";
  const custom = (source.epgUrl ?? "").trim();
  const complete = (source.epgMode ?? "replace") === "complete";
  const main = custom && (!complete || !provider) ? custom : provider;
  const extra = /^https?:\/\//i.test(extraUrl.trim()) ? extraUrl.trim() : "";
  const complements = [...(custom && complete && provider ? [custom] : []), ...(extra ? [extra] : [])].filter((u, i, a) => u !== main && a.indexOf(u) === i);
  return { main, complements };
}

export async function syncEpg(source: Source, t: Transport, signal?: AbortSignal, onProgress?: (ratio: number) => void): Promise<number> {
  if (!source.cid) return 0;
  const plan = planEpg(source, await getSetting<string>(EXTRA_EPG_KEY, ""));
  let written = 0;
  let failure: unknown;
  if (plan.main) {
    try { written += await syncEpgUrl(source, t, plan.main, signal, onProgress, false); }
    catch (e) { if (e instanceof DOMException && e.name === "AbortError") throw e; failure = e; }
  }
  // Compléments : tentés même si le guide principal a échoué ; leur propre échec n'est jamais bloquant.
  for (const url of plan.complements) {
    try { written += await syncEpgUrl(source, t, url, signal, undefined, true); }
    catch (e) { if (e instanceof DOMException && e.name === "AbortError") throw e; failure ??= plan.main ? undefined : e; }
  }
  if (failure && written === 0) throw failure;
  return written;
}

/**
 * Lit UN guide. [append] : guide complémentaire, il n'écrit que pour les chaînes SANS programme et ne retire rien
 * (sinon le guide principal qu'on vient d'écrire serait supprimé).
 */
async function syncEpgUrl(source: Source, t: Transport, url: string, signal: AbortSignal | undefined, onProgress: ((ratio: number) => void) | undefined, append: boolean): Promise<number> {
  const sourceId = source.cid;
  if (!sourceId) return 0;

  const wanted = new Set<string>();
  await db.channels.where("[sourceId+ord]").between([sourceId, -1], [sourceId, Infinity]).each((c) => { if (c.epg) wanted.add(c.epg); });
  if (append) {
    const have = new Set<string>();
    await db.programs.where("[sourceId+end]").between([sourceId, Date.now()], [sourceId, Infinity]).each((p) => { have.add(p.epg); });
    for (const id of have) wanted.delete(id);
  }
  if (wanted.size === 0) return 0;

  const res = await transportFetch(t, url, { signal, userAgent: source.userAgent, referer: source.referer });
  const total = Number(res.headers.get("content-length")) || 0;
  let stream: ReadableStream<Uint8Array> | null = res.body;
  if (!stream) return 0;

  // Détection gzip par octets magiques (les fournisseurs se trompent d'extension et de Content-Type).
  const reader0 = stream.getReader();
  const first = await reader0.read();
  if (first.done) return 0;
  const gz = hasGzipMagic(first.value);
  const replay = new ReadableStream<Uint8Array>({
    start(ctrl) { ctrl.enqueue(first.value); },
    async pull(ctrl) {
      const r = await reader0.read();
      if (r.done) ctrl.close(); else ctrl.enqueue(r.value);
    },
    cancel() { void reader0.cancel(); },
  });
  stream = gz && "DecompressionStream" in globalThis ? replay.pipeThrough(new DecompressionStream("gzip") as unknown as ReadableWritablePair<Uint8Array, Uint8Array>) : replay;

  const now = Date.now();
  const from = now - 3 * 3600_000;
  const to = now + 48 * 3600_000;
  // L'ancien guide n'est retiré qu'une fois le nouveau reçu en entier : avant, la purge précédait la lecture du flux
  // et une coupure laissait un guide vide. Les nouvelles lignes ont des identifiants supérieurs à `oldMax`.
  const oldMax = ((await db.programs.orderBy(":id").last())?.id as number | undefined) ?? 0;
  const dropNew = () => db.programs.where("[sourceId+end]").between([sourceId, 0], [sourceId, Infinity]).filter((p) => (p.id ?? 0) > oldMax).delete();

  let written = 0;
  const pending: Promise<unknown>[] = [];
  const scan = scanXmltv(wanted, from, to, (rows) => {
    written += rows.length;
    pending.push(db.programs.bulkAdd(rows.map((r) => ({ ...r, sourceId }))));
  });
  try {
    const reader = stream.getReader();
    const dec = new TextDecoder("utf-8");
    let read = 0;
    for (;;) {
      if (signal?.aborted) { void reader.cancel(); throw new DOMException("Annulé", "AbortError"); }
      const { done, value } = await reader.read();
      if (done) break;
      read += value.byteLength;
      if (total && !gz) onProgress?.(Math.min(0.99, read / total));
      scan.push(dec.decode(value, { stream: true }));
    }
    scan.push(dec.decode());
    scan.end();
    await Promise.all(pending);
  } catch (e) {
    // Flux coupé ou annulé : on garde l'ancien guide et on retire ce qui vient d'être écrit.
    await Promise.allSettled(pending);
    await dropNew().catch(() => undefined);
    throw e;
  }
  if (!append) await db.programs.where("[sourceId+end]").between([sourceId, 0], [sourceId, Infinity]).filter((p) => (p.id ?? 0) <= oldMax).delete();
  onProgress?.(1);
  return written;
}

interface ShortEpgListing { title?: string; description?: string; start_timestamp?: string | number; stop_timestamp?: string | number }

const b64 = (v: string | undefined) => {
  if (!v) return "";
  try { return new TextDecoder().decode(Uint8Array.from(atob(v), (ch) => ch.charCodeAt(0))); } catch { return v; }
};

/** Repli quand xmltv.php échoue (bloqué, trop lourd) : get_short_epg chaîne par chaîne, borné aux premières chaînes du catalogue. */
export async function syncShortEpg(source: Source, t: Transport, signal?: AbortSignal, maxChannels = 120): Promise<number> {
  const sourceId = source.cid;
  if (!sourceId || source.type !== "xtream") return 0;
  const creds = credsOf(source);
  const chans = await db.channels.where("[sourceId+ord]").between([sourceId, -1], [sourceId, Infinity]).filter((c) => !!c.epg && c.sep === 0).limit(maxChannels).toArray();
  const now = Date.now(); const from = now - 3 * 3600_000; const to = now + 48 * 3600_000;
  let written = 0;
  for (let i = 0; i < chans.length; i += 6) {
    if (signal?.aborted) throw new DOMException("Annulé", "AbortError");
    await Promise.all(chans.slice(i, i + 6).map(async (c) => {
      try {
        const r = await xtreamJson<{ epg_listings?: ShortEpgListing[] }>(t, creds, { action: "get_short_epg", stream_id: c.streamId, limit: 12 }, signal);
        const rows: ProgramRow[] = [];
        for (const l of r.epg_listings ?? []) {
          const start = Number(l.start_timestamp) * 1000, end = Number(l.stop_timestamp) * 1000;
          if (!start || end <= from || start >= to) continue;
          rows.push({ sourceId, epg: c.epg!, start, end, title: b64(l.title).trim(), desc: b64(l.description).trim().slice(0, 500) });
        }
        if (rows.length) { await db.programs.bulkAdd(rows); written += rows.length; }
      } catch (e) { if (e instanceof DOMException && e.name === "AbortError") throw e; }
    }));
  }
  return written;
}
