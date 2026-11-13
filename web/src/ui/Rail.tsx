import { useEffect } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { db } from "@/db/db";
import { useFavorites } from "@/hooks/data";
import { useActiveSource } from "@/state/sources";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useT, type Key } from "@/i18n";
import { usePrefs } from "@/state/prefs";
import { useSync } from "@/state/sync";
import type { SyncProgress } from "@/db/types";
import { AppMark, Icon, type IconName } from "./Icon";

/** `short` : libellé du rail replié quand le nom complet ne tient pas (titre au survol = nom complet). */
type Item = { to: string; icon: IconName; label: Key; match: string; short?: Key };

/** « Regarder » : les contenus. */
const WATCH: Item[] = [
  { to: "/", icon: "home", label: "nav.home", match: "/" },
  { to: "/live", icon: "live", label: "nav.live", match: "/live" },
  { to: "/guide", icon: "guide", label: "nav.guide", match: "/guide" },
  { to: "/movies", icon: "movies", label: "nav.movies", match: "/movie" },
  { to: "/series", icon: "series", label: "nav.series", match: "/serie" },
];
/** Rubrique facultative (Réglages › Affichage), placée après le Guide. */
const SPORT: Item = { to: "/sport", icon: "trophy", label: "nav.sport", match: "/sport" };
/** « Ma liste » : ce que l'utilisateur a gardé. */
const MINE: Item[] = [
  { to: "/favorites", icon: "heart", label: "nav.favorites", match: "/favorites" },
  { to: "/recordings", icon: "rec", label: "nav.recordings", match: "/recordings", short: "nav.recordingsRail" },
];
/** En bas, avec le profil : ni contenus ni listes. */
const FOOT: Item[] = [
  { to: "/account", icon: "user", label: "nav.account", match: "/account" },
  { to: "/settings", icon: "settings", label: "nav.settings", match: "/settings" },
];

/** Repère à droite d'une entrée de la barre large (maquette « Menu latéral ») ; null = rien. Pur, testé. */
export function railBadge(to: string, favorites: number, recording: number, running: (n: number) => string): string | null {
  if (to === "/favorites") return favorites > 0 ? String(favorites) : null;
  if (to === "/recordings") return recording > 0 ? running(recording) : null;
  return null;
}

const PHASES: SyncProgress["phase"][] = ["categories", "live", "movie", "series", "epg"];
/** Avancement global de la synchro (0–100) d'après la phase et sa progression. */
export function syncPercent(p: SyncProgress | null): number | null {
  if (!p) return null;
  if (p.phase === "done") return 100;
  const i = PHASES.indexOf(p.phase);
  if (i < 0) return null;
  return Math.round(((i + Math.min(1, Math.max(0, p.ratio))) / PHASES.length) * 100);
}

/**
 * Barre latérale : large (libellés, groupes « Regarder » / « Ma liste », compte et réglages en bas avec le profil)
 * ou repliée (icônes et libellés courts). Le choix est gardé ; sous 1100 px de large, elle est toujours repliée.
 */
export function Rail() {
  const t = useT();
  const loc = useLocation();
  const nav = useNavigate();
  const { profiles, profileId, sportSection, railCollapsed, set } = usePrefs();
  const watch = sportSection ? [...WATCH.slice(0, 3), SPORT, ...WATCH.slice(3)] : WATCH;
  const syncing = useSync((s) => s.running);
  const pct = syncPercent(useSync((s) => s.progress));
  const me = profiles.find((p) => p.id === profileId) ?? profiles[0]!;
  const favCount = useFavorites(useActiveSource()).length;
  const recCount = useLiveQuery(() => db.recordings.where("state").equals("recording").count().catch(() => 0), []) ?? 0;
  const isActive = (m: string) => (m === "/" ? loc.pathname === "/" : loc.pathname.startsWith(m));

  // Largeur de la colonne du rail (grille de l'application, toasts, barre de titre) : classe sur <html>.
  useEffect(() => {
    document.documentElement.classList.toggle("rail-wide", !railCollapsed);
    return () => document.documentElement.classList.remove("rail-wide");
  }, [railCollapsed]);

  const link = (i: Item) => {
    const badge = railBadge(i.to, favCount, recCount, (n) => t("rail.recordingRunning", { n }));
    return (
      <NavLink key={i.to} to={i.to} className={`rail-item${isActive(i.match) ? " active" : ""}${i.to === "/recordings" && recCount > 0 ? " rail-rec" : ""}`} title={t(i.label)}>
        <span className="pill"><Icon name={i.icon} /></span>
        <span className="rail-tx rail-short">{t(i.short ?? i.label)}</span>
        <span className="rail-tx rail-long">{t(i.label)}</span>
        {badge && <span className="rail-badge">{badge}</span>}
      </NavLink>
    );
  };

  return (
    <nav className="rail" aria-label={t("app.name")}>
      <div className="rail-head">
        <AppMark />
        <span className="rail-brand" aria-hidden="true">ULTRA <span>TV</span></span>
        <button type="button" className="rail-toggle" onClick={() => set({ railCollapsed: !railCollapsed })}
          aria-label={t(railCollapsed ? "rail.expand" : "rail.collapse")} title={t(railCollapsed ? "rail.expand" : "rail.collapse")}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d={railCollapsed ? "M4 4h16v16H4zM9 4v16M13 10l2 2-2 2" : "M4 4h16v16H4zM9 4v16M15 10l-2 2 2 2"} />
          </svg>
        </button>
      </div>
      <NavLink to="/search" className={`rail-search${isActive("/search") ? " active" : ""}`} title={`${t("nav.search")} (Ctrl/⌘ K)`} aria-label={t("nav.search")}>
        <Icon name="search" />
        <span className="rail-tx rail-long">{t("nav.search")}</span>
        <kbd className="rail-kbd" aria-hidden="true">/</kbd>
      </NavLink>
      <nav aria-label={t("a11y.mainNav")}>
        <div className="rail-group" role="presentation"><span>{t("nav.groupWatch")}</span></div>
        {watch.map(link)}
        <div className="rail-group" role="presentation"><span>{t("nav.groupMine")}</span></div>
        {MINE.map(link)}
      </nav>
      <div className="rail-foot">
        {syncing && (
          <div className="rail-sync" title={t("sync.background")} role="status" aria-label={`${t("rail.syncing")} ${pct ?? 0} %`}>
            <span className="rail-ring" style={{ ["--p" as string]: `${pct ?? 0}%` }}><span>{pct ?? 0}</span></span>
            <span className="rail-sync-wide">
              <span className="rail-sync-row"><span>{t("rail.syncing")}</span><span>{pct ?? 0} %</span></span>
              <span className="rail-bar"><span style={{ width: `${pct ?? 0}%` }} /></span>
            </span>
          </div>
        )}
        {FOOT.map(link)}
        <button type="button" className="rail-profile" onClick={() => nav("/profiles")} aria-label={t("profile.who")} title={me.name || t("profile.main")}>
          <span className="avatar" style={{ background: me.color }}>{(me.name || "U").slice(0, 1).toUpperCase()}</span>
          <span className="rail-tx rail-long rail-profile-tx">
            <span className="nm">{me.name || t("profile.main")}</span>
            <span className="sb">{t("rail.switchProfile")}</span>
          </span>
        </button>
      </div>
    </nav>
  );
}
