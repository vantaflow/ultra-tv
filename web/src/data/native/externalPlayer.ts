// Bridge to the Android native ExternalPlayer plugin (Capacitor). Falls back to
// a `vlc://<url>` deep link on desktop / Electron so VLC still opens locally.

interface NativeBridge {
  Plugins?: {
    ExternalPlayer?: {
      openInVlc(opts: { url: string; title?: string; userAgent?: string | null; referer?: string | null }): Promise<{ launched: string }>;
      openInExo(opts: { url: string; title?: string }): Promise<{ launched: string }>;
      openExternal(opts: { url: string }): Promise<{ launched: string }>;
      availability(): Promise<{ vlc: boolean; justPlayer: boolean; mxPlayer: boolean; nextPlayer: boolean }>;
    };
  };
  isNativePlatform?: () => boolean;
  getPlatform?: () => string;
}

function bridge(): NativeBridge | null {
  if (typeof window === "undefined") return null;
  const w = window as unknown as { Capacitor?: NativeBridge };
  return w.Capacitor ?? null;
}

export const isNativeAndroid = (): boolean => {
  const b = bridge();
  return !!b?.isNativePlatform?.() && b.getPlatform?.() === "android";
};

export interface PlayerAvailability {
  vlc: boolean;
  exoPlayer: boolean;
  any: boolean;
}

const FALLBACK: PlayerAvailability = { vlc: false, exoPlayer: false, any: false };

export async function checkAvailability(): Promise<PlayerAvailability> {
  const b = bridge();
  if (!b?.Plugins?.ExternalPlayer) return FALLBACK;
  try {
    const a = await b.Plugins.ExternalPlayer.availability();
    return {
      vlc: a.vlc,
      exoPlayer: a.justPlayer || a.mxPlayer || a.nextPlayer,
      any: a.vlc || a.justPlayer || a.mxPlayer || a.nextPlayer,
    };
  } catch {
    return FALLBACK;
  }
}

export async function openInVlc(url: string, title?: string, userAgent?: string | null, referer?: string | null): Promise<boolean> {
  const b = bridge();
  if (b?.Plugins?.ExternalPlayer) {
    try { await b.Plugins.ExternalPlayer.openInVlc({ url, title, userAgent, referer }); return true; }
    catch (e) { console.warn("Native VLC launch failed:", e); }
  }
  // Desktop / web fallback: vlc:// URL scheme (works if VLC is registered as handler).
  window.location.href = `vlc://${url}`;
  return false;
}

export async function openInExoPlayer(url: string, title?: string): Promise<boolean> {
  const b = bridge();
  if (b?.Plugins?.ExternalPlayer) {
    try { await b.Plugins.ExternalPlayer.openInExo({ url, title }); return true; }
    catch (e) { console.warn("Native ExoPlayer launch failed:", e); return false; }
  }
  return false;
}

export async function openInExternalPlayer(url: string): Promise<boolean> {
  const b = bridge();
  if (b?.Plugins?.ExternalPlayer) {
    try { await b.Plugins.ExternalPlayer.openExternal({ url }); return true; }
    catch (e) { console.warn("Native external launch failed:", e); return false; }
  }
  window.open(url, "_blank", "noreferrer");
  return false;
}
