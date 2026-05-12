// TV-mode detection. Returns true when running on an Android TV / Google TV /
// Fire TV device, inside the Capacitor native shell, or when the URL contains
// `?tv=1` (used for browser debugging).
//
// When TV mode is on, the hook adds the `tv-mode` class to <html> so CSS can
// switch to the 10-foot UI variant. Removal on cleanup keeps the toggle clean
// during HMR.

import { useEffect, useSyncExternalStore } from "react";

declare global {
  interface Window {
    Capacitor?: { isNativePlatform?: () => boolean; getPlatform?: () => string };
    __ultratv_isTv?: boolean;
    __ultratv_device?: { model: string; manufacturer: string; sdk: number };
  }
}

function detect(): boolean {
  if (typeof window === "undefined") return false;
  // Native-side decision wins — see MainActivity.detectTelevision().
  if (window.__ultratv_isTv === true) return true;
  const ua = (navigator.userAgent || "").toLowerCase();
  const isAndroidTv =
    ua.includes("android tv") ||
    ua.includes("googletv") ||
    ua.includes("google tv") ||
    ua.includes("aftt") ||           // Fire TV
    ua.includes("afts") ||
    ua.includes("smart-tv") ||
    ua.includes("smarttv") ||
    ua.includes("bravia") ||
    ua.includes("netcast") ||
    ua.includes("webos") ||
    ua.includes("tizen");
  const isCapacitorNative = !!window.Capacitor?.isNativePlatform?.();
  // Our APK is only shipped for TV boxes (Android TV, Google TV, Fire TV, Mecool,
  // etc). Some boxes (e.g. Mecool KM2/KT1) report "Mobile" in their UA, so we
  // intentionally do NOT exclude that here — being inside our Capacitor shell
  // is sufficient evidence we're on a TV.
  const forced = typeof location !== "undefined" && /[?&]tv=1\b/.test(location.search);
  return isAndroidTv || isCapacitorNative || forced;
}

let cached: boolean | null = null;
function getSnapshot(): boolean {
  if (cached === null) cached = detect();
  return cached;
}
function subscribe(): () => void {
  // Detection is one-shot at boot; no reactive updates needed.
  return () => {};
}

export function useTvMode(): boolean {
  const isTv = useSyncExternalStore(subscribe, getSnapshot, () => false);
  useEffect(() => {
    const root = document.documentElement;
    if (isTv) {
      root.classList.add("tv-mode");
      // Disable text selection — irrelevant on a remote and a constant
      // accidental-trigger source.
      root.style.userSelect = "none";
    } else {
      root.classList.remove("tv-mode");
      root.style.userSelect = "";
    }
    return () => {
      root.classList.remove("tv-mode");
      root.style.userSelect = "";
    };
  }, [isTv]);
  return isTv;
}

export function isTvMode(): boolean {
  return getSnapshot();
}
