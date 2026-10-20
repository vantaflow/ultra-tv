// DNS personnalisé (application de bureau uniquement) : modèle, validation et conversions pures.
// Mêmes modes et mêmes clés que l'application Android. La résolution elle-même vit dans le processus principal
// Electron (electron/dns.cjs) ; ici, seulement le réglage et ce qu'il faut en dire dans l'interface.

import type { Source } from "@/db/types";
import { bridge } from "./transport";

export type DnsMode = "system" | "google" | "cloudflare" | "quad9" | "doh" | "custom";
export type DohPreset = "cloudflare" | "google" | "quad9" | "custom";

export interface DnsSettings {
  mode: DnsMode;
  dohPreset: DohPreset;
  /** URL DoH personnalisée (https:// uniquement), utilisée quand dohPreset = "custom". */
  dohUrl: string;
  /** Mode « Personnalisé » : une ou deux adresses IPv4 / IPv6. */
  primary: string;
  secondary: string;
}

export const DNS_MODES: DnsMode[] = ["system", "google", "cloudflare", "quad9", "doh", "custom"];
export const DOH_PRESETS: DohPreset[] = ["cloudflare", "google", "quad9", "custom"];
export const DOH_URLS: Record<Exclude<DohPreset, "custom">, string> = {
  cloudflare: "https://cloudflare-dns.com/dns-query",
  google: "https://dns.google/dns-query",
  quad9: "https://dns.quad9.net/dns-query",
};
/** Noms propres des fournisseurs (identiques dans toutes les langues). */
export const PROVIDER_NAMES: Record<"google" | "cloudflare" | "quad9", string> = { google: "Google", cloudflare: "Cloudflare", quad9: "Quad9" };

export const DEFAULT_DNS: DnsSettings = { mode: "system", dohPreset: "cloudflare", dohUrl: "", primary: "", secondary: "" };

const KNOWN_IPS = new Set(["8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112"]);

/** Réglage tolérant (stockage ancien ou altéré) : valeur inconnue => défaut. */
export function normalizeDns(raw: unknown): DnsSettings {
  const r = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const s = (v: unknown, max: number) => (typeof v === "string" ? v.trim().slice(0, max) : "");
  const mode = s(r.mode, 20).toLowerCase() as DnsMode;
  const preset = s(r.dohPreset, 20).toLowerCase() as DohPreset;
  return {
    mode: DNS_MODES.includes(mode) ? mode : "system",
    dohPreset: DOH_PRESETS.includes(preset) ? preset : "cloudflare",
    dohUrl: s(r.dohUrl, 512),
    primary: s(r.primary, 64),
    secondary: s(r.secondary, 64),
  };
}

const V4 = /^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/;

/** Adresse IPv4 ou IPv6 littérale (sans port ni crochets). */
export function isIp(v: string): boolean {
  const s = v.trim();
  if (V4.test(s)) return true;
  if (!s.includes(":") || !/^[0-9a-fA-F:.]+$/.test(s)) return false;
  try {
    return new URL(`http://[${s}]/`).hostname.length > 2;
  } catch {
    return false;
  }
}

/** URL DoH acceptée : https, avec hôte, sans identifiants. */
export function isValidDohUrl(v: string): boolean {
  try {
    const u = new URL(v.trim());
    return u.protocol === "https:" && !!u.hostname && !u.username && !u.password;
  } catch {
    return false;
  }
}

export interface DnsIssues { dohUrl: boolean; primary: boolean; secondary: boolean }

/** Champs invalides du mode courant (un champ facultatif vide n'est pas une erreur). */
export function dnsIssues(s: DnsSettings): DnsIssues {
  return {
    dohUrl: s.mode === "doh" && s.dohPreset === "custom" && !isValidDohUrl(s.dohUrl),
    primary: s.mode === "custom" && !isIp(s.primary),
    secondary: s.mode === "custom" && s.secondary.trim() !== "" && !isIp(s.secondary),
  };
}

/** Réglage utilisable ; sinon le processus principal se comporte comme « Système ». */
export const dnsUsable = (s: DnsSettings): boolean => {
  if (s.mode === "system") return false;
  const i = dnsIssues(s);
  return !i.dohUrl && !i.primary;
};

/**
 * Portée côté Chromium (requêtes du cloud, mises à jour). Les flux et le catalogue du fournisseur passent par
 * le proxy local (Node) et suivent toujours le réglage. Chromium ne connaît que le DoH : les modes Google /
 * Cloudflare / Quad9 y utilisent le point DoH du fournisseur ; des adresses IP inconnues n'y s'appliquent pas.
 */
export function chromiumScope(s: DnsSettings): "doh-endpoint" | "proxy-only" | "full" {
  if (s.mode === "google" || s.mode === "cloudflare" || s.mode === "quad9") return "doh-endpoint";
  if (s.mode === "custom") return [s.primary, s.secondary].some((ip) => KNOWN_IPS.has(ip.trim())) ? "doh-endpoint" : "proxy-only";
  return "full";
}

/** Nom d'hôte du fournisseur actif (pour « Tester ») ; jamais l'URL complète, qui contient des identifiants. */
export function sourceHost(src: Pick<Source, "type" | "server" | "m3uUrl" | "stalkerApi"> | null | undefined): string | null {
  if (!src) return null;
  const raw = src.type === "m3u" ? src.m3uUrl : src.type === "stalker" ? src.stalkerApi || src.server : src.server;
  if (!raw || raw.startsWith("file:")) return null;
  try {
    const u = new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(raw) ? raw : `http://${raw}`);
    return u.protocol === "http:" || u.protocol === "https:" ? u.hostname || null : null;
  } catch {
    return null;
  }
}

export interface DnsTestResult { ok: boolean; address?: string; ms?: number; via?: "custom" | "system"; reason?: string }

/** Pont Electron du DNS (absent dans un navigateur : la section est masquée). */
export function dnsBridge() {
  const b = bridge();
  return b?.setDns && b.testDns ? { setDns: b.setDns, testDns: b.testDns } : null;
}

/** Envoie le réglage au processus principal (sans effet hors Electron). */
export async function pushDns(s: DnsSettings): Promise<void> {
  try { await dnsBridge()?.setDns(s); } catch { /* processus principal indisponible : réglage conservé localement */ }
}

export type DnsTestKey = "dns.testOk" | "dns.testSystem" | "dns.testFallback" | "dns.testFailed";

/** Message du bouton « Tester » (clé i18n + variables), sans dépendre de React. */
export function testOutcome(r: DnsTestResult | null | undefined): { key: DnsTestKey; vars: Record<string, string | number> } {
  if (!r || !r.ok || !r.address) return { key: "dns.testFailed", vars: {} };
  const vars = { ip: r.address, ms: Math.max(0, Math.round(r.ms ?? 0)) };
  if (r.via === "custom") return { key: "dns.testOk", vars };
  if (r.reason) return { key: "dns.testFallback", vars: { ip: r.address, reason: r.reason } };
  return { key: "dns.testSystem", vars };
}
