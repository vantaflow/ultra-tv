// Préférences d'interface : petites, lues AVANT le premier rendu (pas de flash de thème ni de langue).
import { create } from "zustand";
import { detectLang, LANGS, type Lang } from "@/i18n/lang";
import type { DnsSettings } from "@/net/dns";
import { clockText, parseTimeFormat, type TimeFormat } from "@/lib/clock";
import { parseStartScreen, type StartScreen } from "@/lib/startScreen";
import { DEFAULT_ACCENT, parseAccent, parseTheme, themeAttr, type AccentId, type ThemeId } from "@/lib/themes";

export type Theme = ThemeId;
/** `kids` et `lang` viennent de la synchro entre appareils : « enfants » est conservé tel quel (aucun effet ici), `lang` = langue de l'interface du profil (absent = celle de l'appareil). */
export interface Profile { id: string; name: string; color: string; kids?: boolean; lang?: Lang }

/** Langue effective : celle du profil courant si elle existe, sinon celle de l'appareil. */
export function effectiveLang(profiles: Profile[], profileId: string, deviceLang: Lang): Lang {
  const l = (profiles.find((p) => p.id === profileId) ?? profiles[0])?.lang;
  return l && LANGS.some((x) => x.code === l) ? l : deviceLang;
}

/** Appelé après chaque changement de la liste des profils (journal de synchro) ; branché par cloud/profilesSync. */
let profilesHook: ((before: Profile[], after: Profile[]) => void) | null = null;
export const setProfilesHook = (h: typeof profilesHook) => { profilesHook = h; };

export interface Prefs {
  theme: Theme;
  /** Identifiant de la couleur d'accent (voir lib/themes.ts) : mêmes clés qu'Android. */
  accent: AccentId;
  /** Langue effective de l'interface (calculée : profil courant, sinon `deviceLang`). */
  lang: Lang;
  /** Langue de l'appareil, utilisée par les profils sans langue propre. */
  deviceLang: Lang;
  /** Format de l'heure : « auto » (langue / système), 12 h ou 24 h. Local à l'appareil : les réglages synchronisés ne contiennent que langues et catégories. */
  timeFormat: TimeFormat;
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
  /** Barre latérale repliée (icônes seules) ; dépliée par défaut. */
  railCollapsed: boolean;
  /** Écran ouvert au lancement (Réglages › Affichage › Ouvrir sur). */
  startScreen: StartScreen;
  /** Dernière rubrique ouverte, pour « Dernier écran ». */
  lastScreen: string;
}

export const PROFILE_COLORS = ["#D91E2B", "#0EA5E9", "#F59E0B", "#7C3AED", "#10B981"];
const KEY = "ultratv.prefs.v1";

const defaults = (): Prefs => ({
  theme: "auto", accent: DEFAULT_ACCENT, lang: detectLang(), deviceLang: detectLang(), timeFormat: "auto", activeSourceId: null,
  profiles: [{ id: "main", name: "", color: PROFILE_COLORS[0]! }], profileId: "main", askProfile: false,
  autoPreview: true, autoplayNext: true, preferMp4: true, liveFormat: "m3u8", syncOn: "launch",
  volume: 1, muted: false, fit: "contain", animations: "auto", liveCat: {}, channelLogos: true, guideCol: "normal",
  dns: { mode: "system", dohPreset: "cloudflare", dohUrl: "", primary: "", secondary: "" },
  sportSection: false, optimizeImages: true, railCollapsed: false, startScreen: "home", lastScreen: "/",
});

function load(): Prefs {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const v = { ...defaults(), ...JSON.parse(raw) } as Prefs;
      // Anciens réglages (accent en hexadécimal, valeur inconnue) migrés / repliés sur la valeur par défaut.
      const saved = (JSON.parse(raw) as { deviceLang?: string }).deviceLang;
      const stored = LANGS.find((l) => l.code === saved)?.code ?? LANGS.find((l) => l.code === v.lang)?.code ?? detectLang();
      v.deviceLang = stored;
      v.lang = effectiveLang(v.profiles, v.profileId, stored);
      v.accent = parseAccent(v.accent);
      v.theme = parseTheme(v.theme);
      v.timeFormat = parseTimeFormat(v.timeFormat);
      v.startScreen = parseStartScreen(v.startScreen);
      return v;
    }
  } catch { /* stockage indisponible */ }
  return defaults();
}

interface PrefsStore extends Prefs { set: (p: Partial<Prefs>) => void }

export const usePrefs = create<PrefsStore>((set, get) => ({
  ...load(),
  set: (p) => {
    const before = get();
    const next: Partial<Prefs> = { ...p };
    // Choisir une langue = la langue du profil courant (elle suit le profil, comme sur Android).
    if (p.lang !== undefined) {
      const pid = p.profileId ?? before.profileId;
      next.profiles = (p.profiles ?? before.profiles).map((x) => (x.id === pid ? { ...x, lang: p.lang } : x));
    }
    const m = { ...before, ...next };
    next.lang = effectiveLang(m.profiles, m.profileId, m.deviceLang);
    set(next);
    if (get().profiles !== before.profiles) profilesHook?.(before.profiles, get().profiles);
    const { set: _s, ...rest } = get();
    void _s;
    try { localStorage.setItem(KEY, JSON.stringify(rest)); } catch { /* ignoré */ }
  },
}));

const systemLight = () => typeof matchMedia !== "undefined" && matchMedia("(prefers-color-scheme: light)").matches;

/** Thème clair ou sombre effectif (Noir OLED = sombre). */
export function resolveTheme(t: Theme): "dark" | "light" {
  return themeAttr(t, systemLight()) === "light" ? "light" : "dark";
}

/** Valeur de `data-theme` : « dark », « light » ou « oled ». */
export const resolveThemeAttr = (t: Theme) => themeAttr(t, systemLight());

/** Heure d'un instant selon le réglage « Format de l'heure » (langue de l'interface par défaut). Point d'entrée UNIQUE de l'affichage des heures. */
export function timeLabel(ms: number, locale?: string): string {
  const s = usePrefs.getState();
  return clockText(ms, s.timeFormat, locale ?? s.lang);
}

/** Date + heure (dernière synchro, dernier passage d'un appareil…) : l'heure suit le réglage « Format de l'heure ». */
export function dateTimeLabel(ms: number, locale?: string): string {
  const loc = locale ?? usePrefs.getState().lang;
  return `${new Date(ms).toLocaleDateString(loc)}, ${timeLabel(ms, loc)}`;
}
