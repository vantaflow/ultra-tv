import { NavLink } from "react-router-dom";
import { useI18n } from "@app/i18n";
import { useUiStore } from "@app/stores/ui";
import { useTvMode } from "@app/tv/useTvMode";

interface Item {
  to: string;
  key: string;
  icon: string;
  end?: boolean;
}

const ITEMS: Item[] = [
  { to: "/", key: "nav.home", icon: "🏠", end: true },
  { to: "/live", key: "nav.live", icon: "📺" },
  { to: "/movies", key: "nav.movies", icon: "🎬" },
  { to: "/series", key: "nav.series", icon: "📚" },
  { to: "/guide", key: "nav.guide", icon: "🗓" },
  { to: "/recordings", key: "nav.recordings", icon: "⏺" },
  { to: "/search", key: "nav.search", icon: "🔍" },
  { to: "/favorites", key: "home.favorites", icon: "★" },
  { to: "/watchlist", key: "nav.watchlist", icon: "＋" },
  { to: "/multiview", key: "nav.multiview", icon: "▦" },
  { to: "/settings", key: "nav.settings", icon: "⚙" },
];

export function Sidebar() {
  const t = useI18n((s) => s.t);
  const lang = useI18n((s) => s.lang);
  const collapsedPref = useUiStore((s) => s.sidebarCollapsed);
  const toggle = useUiStore((s) => s.toggleSidebar);
  const isTv = useTvMode();
  // On TV, the sidebar is always expanded — the toggle button is removed since
  // there's no pointer and no reason to hide labels.
  const collapsed = isTv ? false : collapsedPref;
  void lang;

  return (
    <aside className={`sidebar${collapsed ? " collapsed" : ""}`}>
      <div className="sidebar-head">
        {!collapsed && <h1>Ultra TV</h1>}
        {!isTv && (
          <button className="sidebar-toggle" onClick={() => void toggle()} title={collapsed ? "Expand menu" : "Collapse menu"}>
            {collapsed ? "›" : "‹"}
          </button>
        )}
      </div>
      {ITEMS.map((it) => (
        <NavLink
          key={it.to}
          to={it.to}
          end={it.end}
          className={({ isActive }) => (isActive ? "active" : "")}
          title={collapsed ? t(it.key) : undefined}
        >
          <span className="sidebar-icon" aria-hidden>{it.icon}</span>
          {!collapsed && <span className="sidebar-label">{t(it.key)}</span>}
        </NavLink>
      ))}
    </aside>
  );
}
