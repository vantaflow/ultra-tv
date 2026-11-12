// Fanart / TVmaze fictifs pour les rendus de contrôle (VITE_DEMO=1) : aucun appel réseau, aucun vrai titre ni vraie chaîne.
// Chargé uniquement par import dynamique derrière `import.meta.env.VITE_DEMO === "1"` (absent du build distribué).
import type { TitleRef } from "@/hooks/extmeta";
import { DAY_MS, NO_ART, type FanartArt, type TvEpisode, type TvShow } from "@/lib/extmeta";

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

/** « Logo » de titre dessiné en SVG (texte du titre fictif), comme un clearlogo transparent. */
export function demoLogo(title: string): string {
  const t = esc(title.toUpperCase());
  const w = Math.max(320, Math.min(900, title.length * 46));
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} 140" width="${w}" height="140">`
    + `<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff6d8"/><stop offset="1" stop-color="#e2b75a"/></linearGradient></defs>`
    + `<text x="50%" y="96" text-anchor="middle" font-family="Georgia, 'Times New Roman', serif" font-weight="700" font-size="76" letter-spacing="4" fill="url(#g)" stroke="#2a1a00" stroke-width="2" paint-order="stroke">${t}</text>`
    + `</svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

/** Titres de démo dotés d'un logo (les autres gardent leur titre texte). */
const WITH_LOGO = new Set(["Orbite basse", "Les Cerfs-volants", "Les Gardiens du phare", "Brume", "Capitale", "Station 9"]);

export function demoFanart(ref: TitleRef): FanartArt {
  return ref.title && WITH_LOGO.has(ref.title) ? { ...NO_ART, logo: demoLogo(ref.title) } : NO_ART;
}

const ep = (season: number, number: number, name: string, airAt: number): TvEpisode => ({ season, number, name, airAt, image: null, summary: null });

/** Calendrier fictif, relatif à aujourd'hui 21 h 00 (heure locale) pour que les rendus restent stables. */
export function demoTvShow(ref: TitleRef, now = Date.now()): TvShow | null {
  const d = new Date(now); d.setHours(21, 0, 0, 0);
  const at = (days: number) => d.getTime() + days * DAY_MS;
  const show = (status: string, previous: TvEpisode | null, next: TvEpisode | null): TvShow => ({ id: 1, status, network: null, previous, next });
  switch (ref.title) {
    case "Les Gardiens du phare": return show("Running", ep(3, 5, "Le registre", at(-1)), ep(3, 6, "La relève", at(6)));
    case "Brume": return show("Running", ep(1, 7, "Le col", at(-12)), ep(1, 8, "Les lanternes", at(1)));
    case "Capitale": return show("Running", ep(2, 9, "Hémicycle", at(-3)), null);
    case "Station 9": return show("Running", ep(4, 1, "Retour", at(-20)), ep(4, 2, "Quarantaine", at(3)));
    case "Les Héritiers": return show("Ended", ep(5, 10, "Le testament", at(-400)), null);
    default: return null;
  }
}

/** Écusson d'équipe fictif (SVG : blason aux initiales, couleur tirée du nom) : aucun vrai club, aucun appel réseau. */
export function demoTeamBadge(name: string): string {
  const colors = ["#2563eb", "#16a34a", "#dc2626", "#9333ea", "#ea580c", "#0891b2", "#ca8a04"];
  let h = 0;
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  const c = colors[h % colors.length]!;
  const ini = esc((name.match(/[\p{L}\p{N}]/gu) ?? []).filter((_, i, a) => i === 0 || i === a.length - 1).join("").toUpperCase().slice(0, 2));
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 72" width="64" height="72">`
    + `<path d="M32 2 60 12v22c0 18-12 30-28 36C16 64 4 52 4 34V12z" fill="${c}" stroke="#fff" stroke-width="3"/>`
    + `<path d="M4 30h56" stroke="#fff" stroke-opacity=".35" stroke-width="6"/>`
    + `<text x="32" y="46" text-anchor="middle" font-family="Arial, sans-serif" font-weight="700" font-size="22" fill="#fff">${ini}</text></svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}
