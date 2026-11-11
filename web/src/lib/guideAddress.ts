// Adresse d'un guide XMLTV saisie par l'utilisateur : forme (http/https, chemin en .xml ou .xml.gz) puis vérification
// réseau AVANT enregistrement (premiers Ko lus, gzip détecté par ses octets magiques, balise <tv obligatoire).
// L'adresse peut contenir un jeton : jamais journalisée, jamais affichée en entier.

import { hasGzipMagic } from "@/lib/gzip";
import { HttpError, transportFetch, type Transport } from "@/net/transport";

export type GuideProblem = "empty" | "scheme" | "host" | "extension";
export type GuideValidation = { ok: true; url: string } | { ok: false; problem: GuideProblem };

/** http:// ou https:// seulement, hôte non vide, chemin finissant par .xml ou .xml.gz (query string et fragment tolérés). */
export function validateGuideAddress(raw: string): GuideValidation {
  const s = raw.trim();
  if (!s) return { ok: false, problem: "empty" };
  if (!/^https?:\/\//i.test(s)) return { ok: false, problem: "scheme" };
  let u: URL;
  try { u = new URL(s); } catch { return { ok: false, problem: "host" }; }
  if (!u.hostname) return { ok: false, problem: "host" };
  const path = u.pathname.toLowerCase();
  if (!(path.endsWith(".xml") || path.endsWith(".xml.gz"))) return { ok: false, problem: "extension" };
  return { ok: true, url: s };
}

export type GuideCheck =
  | { ok: true }
  | { ok: false; reason: "unreachable" | "notXmltv" }
  | { ok: false; reason: "http"; status: number };

const HEAD_BYTES = 16 * 1024;
const TV_TAG = /<tv[\s>]/i;

async function readHead(stream: ReadableStream<Uint8Array>, max: number): Promise<Uint8Array> {
  const reader = stream.getReader();
  const parts: Uint8Array[] = [];
  let n = 0;
  try {
    while (n < max) {
      const { done, value } = await reader.read();
      if (done) break;
      parts.push(value); n += value.byteLength;
    }
  } finally { void reader.cancel().catch(() => undefined); }
  const out = new Uint8Array(Math.min(n, max));
  let o = 0;
  for (const p of parts) { const take = Math.min(p.byteLength, out.length - o); if (take <= 0) break; out.set(p.subarray(0, take), o); o += take; }
  return out;
}

/** Vérifie une adresse déjà valide : la requête doit aboutir et le début du document contenir une balise `<tv`. */
export async function verifyGuideAddress(t: Transport, url: string, signal?: AbortSignal): Promise<GuideCheck> {
  let res: Response;
  try {
    res = await transportFetch(t, url, { signal });
  } catch (e) {
    if (e instanceof HttpError) return { ok: false, reason: "http", status: e.status };
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    return { ok: false, reason: "unreachable" };
  }
  if (!res.body) return { ok: false, reason: "notXmltv" };
  try {
    // Le premier bloc dit si le flux est compressé (octets magiques) ; on rejoue ce bloc devant le reste.
    const reader0 = res.body.getReader();
    const first = await reader0.read();
    if (first.done) return { ok: false, reason: "notXmltv" };
    const gz = hasGzipMagic(first.value);
    const replay = new ReadableStream<Uint8Array>({
      start(ctrl) { ctrl.enqueue(first.value); },
      async pull(ctrl) { const r = await reader0.read(); if (r.done) ctrl.close(); else ctrl.enqueue(r.value); },
      cancel() { void reader0.cancel().catch(() => undefined); },
    });
    let head: Uint8Array;
    if (gz) {
      if (!("DecompressionStream" in globalThis)) return { ok: false, reason: "notXmltv" };
      head = await readHead(replay.pipeThrough(new DecompressionStream("gzip") as unknown as ReadableWritablePair<Uint8Array, Uint8Array>), HEAD_BYTES);
    } else head = await readHead(replay, HEAD_BYTES);
    return TV_TAG.test(new TextDecoder("latin1").decode(head)) ? { ok: true } : { ok: false, reason: "notXmltv" };
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    return { ok: false, reason: "notXmltv" };   // gzip illisible ou flux coupé avant la moindre balise
  }
}

/** Clé de traduction du message d'erreur d'un verdict (« guide.err.* »), ou null si l'adresse est bonne. */
export function guideErrorKey(v: GuideValidation | GuideCheck): "guide.err.empty" | "guide.err.scheme" | "guide.err.host" | "guide.err.extension" | "guide.err.unreachable" | "guide.err.http" | "guide.err.notXmltv" | null {
  if (v.ok) return null;
  if ("problem" in v) return `guide.err.${v.problem}` as const;
  return `guide.err.${v.reason}` as const;
}

/**
 * À l'enregistrement d'une source : si elle a un « guide de cette source », il doit être valable (forme + réseau).
 * Renvoie le message d'erreur traduit, ou null (aucune adresse, ou adresse bonne).
 */
export async function guideErrorForSave(t: Transport, epgUrl: string, tr: (key: NonNullable<ReturnType<typeof guideErrorKey>>) => string): Promise<string | null> {
  if (!epgUrl.trim()) return null;
  const r = await checkGuideAddress(t, epgUrl);
  return r.key ? tr(r.key) + (r.status ? ` (HTTP ${r.status})` : "") : null;
}

/** Forme puis réseau : clé de traduction de l'erreur (avec [status] pour « http »), ou null si l'adresse est un guide XMLTV. */
export async function checkGuideAddress(t: Transport, raw: string, signal?: AbortSignal): Promise<{ key: ReturnType<typeof guideErrorKey>; status?: number }> {
  const v = validateGuideAddress(raw);
  if (!v.ok) return { key: guideErrorKey(v) };
  const c = await verifyGuideAddress(t, v.url, signal);
  return c.ok ? { key: null } : { key: guideErrorKey(c), ...(c.reason === "http" ? { status: c.status } : {}) };
}
