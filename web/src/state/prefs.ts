// Préférences d'interface : petites, lues AVANT le premier rendu (pas de flash de thème ni de langue).
import { create } from "zustand";
import { detectLang, type Lang } from "@/i18n";

export type Theme = "auto" | "dark" | "light";
export interface Profile { id: string; name: string; color: string }

export interface Prefs {
  theme: Theme;
  accent: string;
  lang: Lang;
  activeSourceId: number | null;
  profiles: Profile[];
  profileId: string;
  askProfile: boolean;
  autoPreview: boolean;
  autoplayNext: boolean;
  preferMp4: boolean;
  liveFormat: "m3u8" | "ts";
  syncOn: "launch" | "manual";
  volume: number;
  muted: boolean;
  fit: "contain" | "cover" | "fill";
  liveCat: Record<string, string>;
}

export const ACCENTS = ["#D91E2B", "#7C3AED", "#0EA5E9", "#F59E0B"];
export const PROFILE_COLORS = ["#D91E2B", "#0EA5E9", "#F59E0B", "#7C3AED", "#10B981"];
const KEY = "ultratv.prefs.v1";

const defaults = (): Prefs => ({
  theme: "auto", accent: ACCENTS[0]!, lang: detectLang(), activeSourceId: null,
  profiles: [{ id: "main", name: "", color: PROFILE_COLORS[0]! }], profileId: "main", askProfile: false,
  autoPreview: true, autoplayNext: true, preferMp4: true, liveFormat: "m3u8", syncOn: "launch",
  volume: 1, muted: false, fit: "contain", liveCat: {},
});

function load(): Prefs {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...defaults(), ...JSON.parse(raw) };
  } catch { /* stockage indisponible */ }
  return defaults();
}

interface PrefsStore extends Prefs { set: (p: Partial<Prefs>) => void }

export const usePrefs = create<PrefsStore>((set, get) => ({
  ...load(),
  set: (p) => {
    set(p);
    const { set: _s, ...rest } = get();
    void _s;
    try { localStorage.setItem(KEY, JSON.stringify(rest)); } catch { /* ignoré */ }
  },
}));

export function resolveTheme(t: Theme): "dark" | "light" {
  if (t !== "auto") return t;
  return typeof matchMedia !== "undefined" && matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}
