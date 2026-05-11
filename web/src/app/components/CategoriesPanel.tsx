// Settings → Categories tab. Per-provider × per-content-type category management.
// Picking a provider exposes three CategoryFilter pickers (Live / Movies / Series)
// reusing the same chip UI as the in-screen filters. Selections are persisted in
// IndexedDB (key `filters:<providerId>:<contentType>`).
//
// When a whitelist is set, the next manual resync will *skip* fetching items in
// the unchecked categories — meaningful bandwidth/time savings on huge providers.

import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useState } from "react";
import { providerRepo } from "@data/db/repositories";
import { CategoryFilter } from "@app/components/CategoryFilter";
import { SyncStatusBanner } from "@app/components/SyncStatusBanner";
import { useCategoryFilters } from "@app/stores/categoryFilters";
import { useProviderStore } from "@app/stores/providers";

export function CategoriesPanel() {
  const providers = useLiveQuery(() => providerRepo.list(), []) ?? [];
  const activeId = useProviderStore((s) => s.activeProviderId);
  const syncProvider = useProviderStore((s) => s.syncProvider);
  const syncing = useProviderStore((s) => s.syncing);
  const loadFilters = useCategoryFilters((s) => s.load);
  const filtersMap = useCategoryFilters((s) => s.allowed);

  const [selectedId, setSelectedId] = useState<number | null>(activeId);

  useEffect(() => {
    if (selectedId == null && providers.length > 0) {
      setSelectedId(activeId ?? providers[0]!.id);
    }
  }, [providers, activeId, selectedId]);

  useEffect(() => {
    if (selectedId != null) void loadFilters(selectedId);
  }, [selectedId, loadFilters]);

  if (providers.length === 0) {
    return <div className="banner">No providers yet. Add one in the Providers tab first.</div>;
  }

  return (
    <div>
      <h2>Categories</h2>
      <p style={{ color: "var(--fg-muted)", maxWidth: 720 }}>
        Choose which categories to display in each section. When a whitelist is set,
        the <strong>next resync</strong> only fetches items from the checked categories
        — useful for huge providers (saves time and bandwidth).
      </p>

      <div className="form-row">
        <label>Provider</label>
        <select value={selectedId ?? ""} onChange={(e) => setSelectedId(Number(e.target.value))}>
          {providers.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </div>

      <SyncStatusBanner />

      {selectedId != null && (() => {
        const liveAllowed = filtersMap[`${selectedId}:LIVE`];
        const movieAllowed = filtersMap[`${selectedId}:MOVIE`];
        const seriesAllowed = filtersMap[`${selectedId}:SERIES`];
        const totalSelected = (liveAllowed?.length ?? -1) + (movieAllowed?.length ?? -1) + (seriesAllowed?.length ?? -1);
        const allEmpty = (movieAllowed?.length === 0) && (seriesAllowed?.length === 0) && (liveAllowed?.length === 0);
        void totalSelected;
        return <div style={{ display: "grid", gap: 16 }}>
          {allEmpty && (
            <div className="banner error">
              ⚠️ All three lists have 0 categories checked — resync will fetch nothing.
              Click "All" inside each filter or remove a whitelist to fetch everything.
            </div>
          )}
          <div className="cat-section">
            <div className="cat-section-head">
              <h3>📺 Live TV</h3>
              <CategoryFilter providerId={selectedId} contentType="LIVE" label="Live categories" />
            </div>
          </div>
          <div className="cat-section">
            <div className="cat-section-head">
              <h3>🎬 Movies</h3>
              <CategoryFilter providerId={selectedId} contentType="MOVIE" label="Movie categories" />
            </div>
          </div>
          <div className="cat-section">
            <div className="cat-section-head">
              <h3>📚 Series</h3>
              <CategoryFilter providerId={selectedId} contentType="SERIES" label="Series categories" />
            </div>
          </div>

          <div className="banner" style={{ marginTop: 8 }}>
            <strong>Apply selection:</strong> the next manual resync will only fetch the categories
            you've checked above. Click below to resync now.
            <div style={{ marginTop: 8 }}>
              <button onClick={() => syncProvider(selectedId)} disabled={syncing}>
                {syncing ? "Syncing…" : "Resync this provider"}
              </button>
            </div>
          </div>
        </div>;
      })()}
    </div>
  );
}
