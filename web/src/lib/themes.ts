// Thèmes et couleurs d'accent : identifiants COMMUNS avec Android (Personalization.kt / AppTheme), logique pure (contrastes WCAG).
// Valeur inconnue (autre appareil, version future, ancien réglage) : repli sur la valeur par défaut, jamais d'erreur.

export type ThemeId = "auto" | "dark" | "light" | "oled";
export const THEMES: readonly ThemeId[] = ["auto", "dark", "light", "oled"];
export const DEFAULT_THEME: ThemeId = "auto";

export function parseTheme(v: unknown): ThemeId {
  const s = typeof v === "string" ? v.trim().toLowerCase() : "";
  if (s === "amoled") return "oled";
  return (THEMES as readonly string[]).includes(s) ? (s as ThemeId) : DEFAULT_THEME;
}

export type AccentId =
  | "red" | "blue" | "violet" | "green" | "orange" | "pink"
  | "ocean" | "emerald" | "amber" | "teal" | "indigo" | "fuchsia";

/** Mêmes clés et mêmes couleurs que `AccentChoice` sur Android. Le rouge (défaut) laisse les jetons du thème décider. */
export const ACCENT_DEFS: readonly { id: AccentId; hex: string }[] = [
  { id: "red", hex: "#D91E2B" },
  { id: "blue", hex: "#2563EB" },
  { id: "violet", hex: "#7C4DFF" },
  { id: "green", hex: "#15803D" },
  { id: "orange", hex: "#D9480F" },
  { id: "pink", hex: "#DB2777" },
  { id: "ocean", hex: "#0284C7" },
  { id: "emerald", hex: "#059669" },
  { id: "amber", hex: "#B7791F" },
  { id: "teal", hex: "#0A9396" },
  { id: "indigo", hex: "#5B5BE0" },
  { id: "fuchsia", hex: "#C026D3" },
];
export const DEFAULT_ACCENT: AccentId = "red";

// Anciens réglages du bureau : l'accent était une couleur hexadécimale.
const LEGACY_HEX: Record<string, AccentId> = {
  "#FF3B47": "red", "#D91E2B": "red", "#7C3AED": "violet", "#0EA5E9": "ocean", "#F59E0B": "amber",
};

export function parseAccent(v: unknown): AccentId {
  if (typeof v !== "string") return DEFAULT_ACCENT;
  const s = v.trim();
  const id = s.toLowerCase();
  if (ACCENT_DEFS.some((a) => a.id === id)) return id as AccentId;
  return LEGACY_HEX[s.toUpperCase()] ?? DEFAULT_ACCENT;
}

export const accentHex = (id: AccentId): string => ACCENT_DEFS.find((a) => a.id === id)!.hex;

// ── Contrastes WCAG 2.x (couleurs « #RRGGBB » opaques) ──
const rgb = (hex: string): [number, number, number] => {
  const n = parseInt(hex.replace("#", ""), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};
const toHex = (c: [number, number, number]) => "#" + c.map((x) => Math.round(Math.min(255, Math.max(0, x))).toString(16).padStart(2, "0")).join("").toUpperCase();
const lin = (c: number) => { const x = c / 255; return x <= 0.03928 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4); };

export function luminance(hex: string): number {
  const [r, g, b] = rgb(hex);
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}
export function contrast(a: string, b: string): number {
  const la = luminance(a), lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}
export function mix(a: string, b: string, t: number): string {
  const x = rgb(a), y = rgb(b), k = Math.min(1, Math.max(0, t));
  return toHex([0, 1, 2].map((i) => x[i]! + (y[i]! - x[i]!) * k) as [number, number, number]);
}

export const WHITE = "#FFFFFF";
export const INK = "#0A0A0C";
export const AA_TEXT = 4.5;

/** Texte posé SUR [bg] : blanc ou encre, celui qui contraste le plus (toujours ≥ 4,5:1 pour nos accents). */
export const onColor = (bg: string): string => (contrast(WHITE, bg) >= contrast(INK, bg) ? WHITE : INK);

/** [fg] éclairci (fonds sombres) ou assombri (fonds clairs) juste assez pour atteindre [min] sur CHACUN des [backgrounds]. */
export function ensureContrast(fg: string, backgrounds: readonly string[], min = AA_TEXT): string {
  const dark = backgrounds.every((b) => luminance(b) < 0.5);
  const target = dark ? WHITE : "#000000";
  for (let t = 0; t <= 1.0001; t += 0.02) {
    const c = mix(fg, target, t);
    if (backgrounds.every((b) => contrast(c, b) >= min)) return c;
  }
  return target;
}

/** Surfaces des tokens.css (fond, rail, surface, surface-2, surface-3, tone) par thème résolu. */
export const SURFACES = {
  dark: ["#0A0C10", "#0E1117", "#161A22", "#1C2029", "#2A3140", "#12151C"],
  oled: ["#000000", "#050506", "#0D0F13", "#14171D", "#20242D", "#08090B"],
  light: ["#F5F6F8", "#FFFFFF", "#FFFFFF", "#E8EBF0", "#DCE1EA", "#E8EBF0"],
} as const;

export type ThemeMode = keyof typeof SURFACES;

/** Variables CSS d'un accent pour un thème résolu ; `null` pour le rouge (jetons du thème, aucun forçage). */
export function accentVars(id: AccentId, mode: ThemeMode): { accent: string; accentText: string; onAccent: string } | null {
  if (id === DEFAULT_ACCENT) return null;
  const hex = accentHex(id);
  return { accent: hex, accentText: ensureContrast(hex, SURFACES[mode]), onAccent: onColor(hex) };
}

/** Valeur de `data-theme` : « oled » reste distinct pour le CSS ; Automatique suit le système (sombre par défaut). */
export function themeAttr(t: ThemeId, systemLight: boolean): ThemeMode {
  if (t === "auto") return systemLight ? "light" : "dark";
  return t === "oled" ? "oled" : t;
}
