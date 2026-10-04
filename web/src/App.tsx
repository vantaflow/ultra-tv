import { useEffect } from "react";
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { dirOf } from "@/i18n";
import { initTransport, bridge } from "@/net/transport";
import { PlayerHost } from "@/player/PlayerHost";
import { ErrorBoundary } from "@/ui/ErrorBoundary";
import { Rail } from "@/ui/Rail";
import { Toasts } from "@/ui/common";
import { resolveTheme, usePrefs } from "@/state/prefs";
import { startSourcesWatcher, useActiveSource, useSources } from "@/state/sources";
import { useSync } from "@/state/sync";
import { useUi } from "@/state/ui";
import { Home } from "@/screens/Home";
import { Live } from "@/screens/Live";
import { Guide } from "@/screens/Guide";
import { VodScreen } from "@/screens/Vod";
import { MovieDetail, SeriesDetail } from "@/screens/Detail";
import { Search } from "@/screens/Search";
import { Favorites } from "@/screens/Favorites";
import { Settings } from "@/screens/Settings";
import { Onboarding } from "@/screens/Onboarding";
import { Profiles } from "@/screens/Profiles";
import { loadCloud, startCloudSchedule } from "@/cloud/service";
import { cloudAvailable } from "@/cloud/client";

function Effects() {
  const { theme, accent, lang, syncOn } = usePrefs();
  const nav = useNavigate();
  const loc = useLocation();
  const { ready, list } = useSources();
  const active = useActiveSource();

  useEffect(() => {
    const apply = () => {
      const r = document.documentElement;
      r.dataset.theme = resolveTheme(theme);
      r.style.setProperty("--accent", accent);
      r.lang = lang;
      r.dir = dirOf(lang);
      void bridge()?.setTitleBarTheme?.(resolveTheme(theme) === "dark");
    };
    apply();
    const mq = matchMedia("(prefers-color-scheme: light)");
    mq.addEventListener("change", apply);
    return () => mq.removeEventListener("change", apply);
  }, [theme, accent, lang]);

  useEffect(() => {
    document.documentElement.classList.toggle("mac", bridge()?.platform === "darwin");
    startSourcesWatcher();
    let stopCloud: (() => void) | undefined;
    void loadCloud().then(() => { if (cloudAvailable()) stopCloud = startCloudSchedule(); });
    void initTransport();
    const on = () => useUi.getState().setOnline(true);
    const off = () => useUi.getState().setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    const unsub = bridge()?.onUpdateStatus?.((s) => useUi.getState().setUpdate(s));
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
      if (typeof unsub === "function") unsub();
      stopCloud?.();
    };
  }, []);

  // Raccourcis globaux : « / » ou Ctrl/⌘ + K ouvre la recherche.
  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      const typing = !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
      if ((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) { e.preventDefault(); nav("/search"); }
      else if (e.key === "/" && !typing && !e.metaKey && !e.ctrlKey) { e.preventDefault(); nav("/search"); }
    };
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  }, [nav]);

  // Première ouverture : aucune source -> accueil de configuration.
  useEffect(() => {
    if (ready && list.length === 0 && !loc.pathname.startsWith("/welcome")) nav("/welcome", { replace: true });
  }, [ready, list.length, loc.pathname, nav]);

  // Mise à jour silencieuse au lancement : l'ancien catalogue reste visible jusqu'à la bascule de génération.
  useEffect(() => {
    if (!ready || !active || syncOn !== "launch" || active.state !== "ready") return;
    if (Date.now() - active.lastSyncAt < 12 * 3600_000) return;
    void useSync.getState().start(active, { preserveFlags: true, silent: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, active?.id]);

  // Source jamais synchronisée hors assistant (ex. M3U get.php convertie en Xtream au démarrage) : première synchro sans action.
  useEffect(() => {
    if (!ready || !active || active.state !== "new" || active.lastSyncAt !== 0 || loc.pathname.startsWith("/welcome")) return;
    void useSync.getState().start(active, { silent: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, active?.id, active?.state, loc.pathname]);

  return null;
}

function Shell() {
  const loc = useLocation();
  const { ready } = useSources();
  const bare = loc.pathname.startsWith("/welcome") || loc.pathname.startsWith("/profiles");
  if (!ready) return <div className="app no-rail" />;
  return (
    <div className={`app${bare ? " no-rail" : ""}`}>
      {!bare && <Rail />}
      <main className="content">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/live" element={<Live />} />
          <Route path="/guide" element={<Guide />} />
          <Route path="/movies" element={<VodScreen kind="movie" />} />
          <Route path="/movie/:id" element={<MovieDetail />} />
          <Route path="/series" element={<VodScreen kind="series" />} />
          <Route path="/serie/:id" element={<SeriesDetail />} />
          <Route path="/search" element={<Search />} />
          <Route path="/favorites" element={<Favorites />} />
          <Route path="/settings/:section?" element={<Settings />} />
          <Route path="/welcome/*" element={<Onboarding />} />
          <Route path="/profiles" element={<Profiles />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <Toasts />
    </div>
  );
}

export function App() {
  return (
    <ErrorBoundary>
      <HashRouter>
        <Effects />
        <Shell />
        <PlayerHost />
      </HashRouter>
    </ErrorBoundary>
  );
}
