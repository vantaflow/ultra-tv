// Fonds, vignettes et logos Fanart.tv / TVmaze : servis en WebP redimensionné par wsrv.nl (service open source de
// redimensionnement d'images). Logique pure. Seuls les hôtes assets.fanart.tv et static.tvmaze.com sont réécrits :
// image.tmdb.org et tout autre hôte restent intacts. Repli sur l'URL d'origine si wsrv.nl échoue ou dépasse 4 s.

export const PROXY_TIMEOUT_MS = 4000;
export const W_BACKGROUND = 1280;
export const W_BACKGROUND_LOW = 960;
export const W_THUMB = 640;
export const W_LOGO = 500;

export type ImageKind = "background" | "thumb" | "logo";

const HOSTS = new Set(["assets.fanart.tv", "static.tvmaze.com"]);

function hostOf(url: string): string | null {
  try {
    const u = new URL(url);
    return u.protocol === "https:" || u.protocol === "http:" ? u.hostname.toLowerCase() : null;
  } catch { return null; }
}

export const isProxiable = (url: string): boolean => { const h = hostOf(url); return !!h && HOSTS.has(h); };

/** Type déduit du chemin Fanart (hdmovielogo, showbackground, moviethumb…) ; TVmaze : vignette sauf indication. */
export function kindOf(url: string, hint?: ImageKind): ImageKind {
  const p = url.toLowerCase();
  if (p.includes("logo")) return "logo";
  if (p.includes("background")) return "background";
  return hint ?? "thumb";
}

export function widthFor(kind: ImageKind, lowMem: boolean): number {
  return kind === "background" ? (lowMem ? W_BACKGROUND_LOW : W_BACKGROUND) : kind === "logo" ? W_LOGO : W_THUMB;
}

/** URL wsrv.nl, ou null : préférence désactivée, hôte non concerné (TMDB compris), URL déjà proxifiée. */
export function optimizeImageUrl(url: string | null | undefined, opts: { enabled: boolean; lowMem?: boolean; hint?: ImageKind }): string | null {
  if (!url || !opts.enabled || !isProxiable(url)) return null;
  const bare = url.replace(/^https?:\/\//i, "");
  return `https://wsrv.nl/?url=${encodeURIComponent(bare)}&w=${widthFor(kindOf(url, opts.hint), !!opts.lowMem)}&output=webp&q=75`;
}

/** Adresse à afficher : la version wsrv.nl, sauf si elle a échoué / dépassé 4 s (`fellBack` = l'originale à laquelle on est revenu). */
export function chooseSrc(original: string | null | undefined, proxied: string | null, fellBack: string | null): string | null | undefined {
  return proxied && fellBack !== original ? proxied : original;
}

/** Appareil à peu de mémoire (navigateur : `deviceMemory` ≤ 2 Go). */
export function lowMemoryDevice(): boolean {
  const m = (typeof navigator !== "undefined" ? (navigator as { deviceMemory?: number }).deviceMemory : undefined);
  return typeof m === "number" && m <= 2;
}
