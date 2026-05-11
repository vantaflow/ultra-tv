import { useEffect } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Sidebar } from "@app/components/Sidebar";
import { SpatialFocusBootstrap } from "@app/components/SpatialFocus";
import { CommandPalette } from "@app/components/CommandPalette";
import { HomeScreen } from "@app/screens/HomeScreen";
import { LiveTvScreen } from "@app/screens/LiveTvScreen";
import { MoviesScreen } from "@app/screens/MoviesScreen";
import { MovieDetailScreen } from "@app/screens/MovieDetailScreen";
import { SeriesScreen } from "@app/screens/SeriesScreen";
import { SeriesDetailScreen } from "@app/screens/SeriesDetailScreen";
import { GuideScreen } from "@app/screens/GuideScreen";
import { RecordingsScreen } from "@app/screens/RecordingsScreen";
import { SearchScreen } from "@app/screens/SearchScreen";
import { FavoritesScreen } from "@app/screens/FavoritesScreen";
import { WatchlistScreen } from "@app/screens/WatchlistScreen";
import { CategoryScreen } from "@app/screens/CategoryScreen";
import { MultiViewScreen } from "@app/screens/MultiViewScreen";
import { SettingsScreen } from "@app/screens/SettingsScreen";
import { providerRepo } from "@data/db/repositories";
import { useProviderStore } from "@app/stores/providers";
import { useParentalStore } from "@app/stores/parental";
import { useI18n } from "@app/i18n";
import { useUiStore } from "@app/stores/ui";
import { initProxyCache } from "@data/net/proxy";
import { startReminderScheduler } from "@data/manager/ReminderScheduler";

export function App() {
  const setActive = useProviderStore((s) => s.setActive);
  const initParental = useParentalStore((s) => s.init);
  const initI18n = useI18n((s) => s.init);
  const initUi = useUiStore((s) => s.init);
  const collapsed = useUiStore((s) => s.sidebarCollapsed);

  useEffect(() => {
    void initProxyCache();
    void providerRepo.active().then((list) => {
      if (list.length > 0) setActive(list[0]!.id);
    });
    void initParental();
    void initI18n();
    void initUi();
    startReminderScheduler();
  }, [setActive, initParental, initI18n, initUi]);

  return (
    <SpatialFocusBootstrap>
      <BrowserRouter>
        <CommandPalette />
        <div className={`layout${collapsed ? " sidebar-collapsed" : ""}`}>
          <Sidebar />
          <main className="content">
            <Routes>
              <Route path="/" element={<HomeScreen />} />
              <Route path="/live" element={<LiveTvScreen />} />
              <Route path="/movies" element={<MoviesScreen />} />
              <Route path="/movies/category/:id" element={<CategoryScreen kind="MOVIE" />} />
              <Route path="/movies/:id" element={<MovieDetailScreen />} />
              <Route path="/series" element={<SeriesScreen />} />
              <Route path="/series/category/:id" element={<CategoryScreen kind="SERIES" />} />
              <Route path="/series/:id" element={<SeriesDetailScreen />} />
              <Route path="/guide" element={<GuideScreen />} />
              <Route path="/recordings" element={<RecordingsScreen />} />
              <Route path="/search" element={<SearchScreen />} />
              <Route path="/favorites" element={<FavoritesScreen />} />
              <Route path="/watchlist" element={<WatchlistScreen />} />
              <Route path="/multiview" element={<MultiViewScreen />} />
              <Route path="/settings" element={<SettingsScreen />} />
            </Routes>
          </main>
        </div>
      </BrowserRouter>
    </SpatialFocusBootstrap>
  );
}
