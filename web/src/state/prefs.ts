// Préférences d'interface : petites, lues AVANT le premier rendu (pas de flash de thème ni de langue).
import { create } from "zustand";
import { detectLang, type Lang } from "@/i18n/lang";
import type { DnsSettings } from "@/net/dns";

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
  /** Animations : « auto » suit le réglage « réduire les animations » du système. */
  animations: "auto" | "off" | "subtle" | "normal";
  liveCat: Record<string, string>;
  /** Logos des chaînes (Direct, guide, listes) : faux = numéro et nom seulement. */
  channelLogos: boolean;
  /** Largeur de la colonne des noms du guide : étroite, normale, large. */
  guideCol: "narrow" | "normal" | "wide";
  /** DNS personnalisé (application de bureau) ; local à l'appareil, jamais synchronisé. */
  dns: DnsSettings;
  /** Rubrique Sport (entrée du menu + rangée de l'accueil), tirée du guide de la source. Désactivée par défaut. */
  sportSection: boolean;
  /** Fanart.tv / TVmaze via wsrv.nl (images plus légères). Activé par défaut. */
  optimizeImages: boolean;
}

// Le 1er accent est celui du design v2 : il laisse les jetons (rouge sombre / clair) décider.
export const ACCENTS = ["#FF3B47", "#7C3AED", "#0EA5E9", "#F59E0B"];
export const PROFILE_COLORS = ["#D91E2B", "#0EA5E9", "#F59E0B", "#7C3AED", "#10B981"];
const KEY = "ultratv.prefs.v1";

const defaults = (): Prefs => ({
  theme: "auto", accent: ACCENTS[0]!, lang: detectLang(), activeSourceId: null,
  profiles: [{ id: "main", name: "", color: PROFILE_COLORS[0]! }], profileId: "main", askProfile: false,
  autoPreview: true, autoplayNext: true, preferMp4: true, liveFormat: "m3u8", syncOn: "launch",
  volume: 1, muted: false, fit: "contain", animations: "auto", liveCat: {}, channelLogos: true, guideCol: "normal",
  dns: { mode: "system", dohPreset: "cloudflare", dohUrl: "", primary: "", secondary: "" },
  sportSection: false, optimizeImages: true,
});

function load(): Prefs {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const v = { ...defaults(), ...JSON.parse(raw) } as Prefs;
      // Ancien rouge par défaut : migré vers l'accent du design v2.
      if (typeof v.accent === "string" && v.accent.toUpperCase() === "#D91E2B") v.accent = ACCENTS[0]!;
      return v;
    }
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
