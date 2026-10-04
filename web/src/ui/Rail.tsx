import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useT, type Key } from "@/i18n";
import { usePrefs } from "@/state/prefs";
import { useSync } from "@/state/sync";
import { AppMark, Icon, type IconName } from "./Icon";

const ITEMS: { to: string; icon: IconName; label: Key; match: string }[] = [
  { to: "/", icon: "home", label: "nav.home", match: "/" },
  { to: "/live", icon: "live", label: "nav.live", match: "/live" },
  { to: "/guide", icon: "guide", label: "nav.guide", match: "/guide" },
  { to: "/movies", icon: "movies", label: "nav.movies", match: "/movie" },
  { to: "/series", icon: "series", label: "nav.series", match: "/serie" },
  { to: "/favorites", icon: "heart", label: "nav.favorites", match: "/favorites" },
  { to: "/settings", icon: "settings", label: "nav.settings", match: "/settings" },
];

export function Rail() {
  const t = useT();
  const loc = useLocation();
  const nav = useNavigate();
  const { profiles, profileId } = usePrefs();
  const syncing = useSync((s) => s.running);
  const me = profiles.find((p) => p.id === profileId) ?? profiles[0]!;
  const isActive = (m: string) => (m === "/" ? loc.pathname === "/" : loc.pathname.startsWith(m));
  return (
    <nav className="rail" aria-label={t("app.name")}>
      <AppMark />
      <NavLink to="/search" className={`rail-search${isActive("/search") ? " active" : ""}`} title={`${t("nav.search")} (Ctrl/⌘ K)`} aria-label={t("nav.search")}>
        <Icon name="search" />
      </NavLink>
      <nav aria-label={t("a11y.mainNav")}>
        {ITEMS.map((i) => (
          <NavLink key={i.to} to={i.to} className={`rail-item${isActive(i.match) ? " active" : ""}`} title={t(i.label)}>
            <span className="pill"><Icon name={i.icon} /></span>
            {t(i.label)}
          </NavLink>
        ))}
      </nav>
      <div className="rail-foot">
        {syncing && <span className="sync-dot" title={t("sync.background")} />}
        <button className="avatar" style={{ background: me.color }} onClick={() => nav("/profiles")} aria-label={t("profile.who")} title={me.name || t("profile.main")}>
          {(me.name || "U").slice(0, 1).toUpperCase()}
        </button>
      </div>
    </nav>
  );
}
