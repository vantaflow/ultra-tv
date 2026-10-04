// Transport réseau, identique dans le thread principal et dans le worker de synchro.
//  - Electron : proxy loopback du processus principal (jeton + URL encodée), sans CORS ni proxy distant.
//  - Navigateur : proxy CORS configuré (Cloudflare, Vercel, Deno, Val Town...) si présent, sinon direct.
// Les URL cibles contiennent des identifiants : elles ne sont jamais journalisées.

import { getSetting, setSetting } from "@/db/db";

export type Transport =
  | { mode: "electron"; base: string }
  | { mode: "proxy"; url: string }
  | { mode: "direct" };

export interface UltraTvBridge {
  isElectron: true;
  platform: string;
  arch?: string;
  version?: string;
  proxyBase: string;
  secretsSecure?: boolean;
  encrypt(plain: string): Promise<string>;
  decrypt(cipher: string): Promise<string>;
  toggleFullscreen?(): Promise<void> | void;
  setFullscreen?(on: boolean): Promise<void> | void;
  isFullscreen?(): Promise<boolean> | boolean;
  onFullscreenChange?(cb: (on: boolean) => void): (() => void) | void;
  checkForUpdates?(): Promise<unknown>;
  onUpdateStatus?(cb: (s: { state: string; version?: string; percent?: number }) => void): (() => void) | void;
  openExternal?(url: string): Promise<unknown> | void;
  setTitleBarTheme?(isDark: boolean): Promise<unknown> | void;
}

declare global {
  interface Window { ultratv?: UltraTvBridge }
}

export const bridge = (): UltraTvBridge | undefined =>
  typeof window !== "undefined" ? window.ultratv : undefined;
export const isElectron = () => !!bridge()?.isElectron;

const PROXY_KEY = "net.proxyUrl";
const ENV_PROXY = (import.meta as unknown as { env?: { VITE_DEFAULT_PROXY_URL?: string } }).env?.VITE_DEFAULT_PROXY_URL || null;

export async function getProxySetting(): Promise<string | null> {
  return getSetting<string | null>(PROXY_KEY, null);
}
export const setProxySetting = async (url: string | null) => { await setSetting(PROXY_KEY, url || null); await initTransport(); };
export const defaultProxy = () => ENV_PROXY;

/** Transport à utiliser depuis le thread principal. */
export async function currentTransport(): Promise<Transport> {
  const b = bridge();
  if (b?.proxyBase) return { mode: "electron", base: b.proxyBase };
  const url = (await getProxySetting()) || ENV_PROXY;
  return url ? { mode: "proxy", url } : { mode: "direct" };
}

// Copie synchrone (images, lecteur) : initialisée au démarrage, mise à jour quand le proxy change.
let cached: Transport = { mode: "direct" };
export const transportSync = (): Transport => {
  const b = bridge();
  return b?.proxyBase ? { mode: "electron", base: b.proxyBase } : cached;
};
export async function initTransport(): Promise<void> { cached = await currentTransport(); }

function b64url(s: string): string {
  const bytes = new TextEncoder().encode(s);
  let bin = "";
  for (const x of bytes) bin += String.fromCharCode(x);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function wrapUrl(t: Transport, url: string): string {
  switch (t.mode) {
    case "electron": return t.base + b64url(url);
    case "proxy": {
      const u = new URL(t.url);
      u.searchParams.set("target", url);
      return u.toString();
    }
    default: return url;
  }
}

export interface ReqOptions {
  signal?: AbortSignal;
  userAgent?: string | null;
  referer?: string | null;
}

export function requestHeaders(t: Transport, o: ReqOptions): Record<string, string> {
  const h: Record<string, string> = {};
  if (t.mode === "electron") {
    if (o.userAgent) h["X-UT-UA"] = o.userAgent;
    if (o.referer) h["X-UT-Referer"] = o.referer;
  } else if (t.mode === "proxy") {
    if (o.userAgent) h["X-SV-UA"] = o.userAgent;
    if (o.referer) h["X-SV-Referer"] = o.referer;
  }
  return h;
}

export async function transportFetch(t: Transport, url: string, o: ReqOptions = {}): Promise<Response> {
  const res = await fetch(wrapUrl(t, url), { signal: o.signal, headers: requestHeaders(t, o) });
  if (!res.ok) throw new HttpError(res.status);
  return res;
}

export class HttpError extends Error {
  constructor(public status: number) {
    super(`HTTP ${status}`);
    this.name = "HttpError";
  }
}
