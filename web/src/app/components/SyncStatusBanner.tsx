// Visible feedback during a provider sync: message, progress bar, cancel button.
// Mounted by SettingsScreen / CategoriesPanel so users always know what's happening.

import { useProviderStore } from "@app/stores/providers";

export function SyncStatusBanner() {
  const syncing = useProviderStore((s) => s.syncing);
  const message = useProviderStore((s) => s.syncMessage);
  const progress = useProviderStore((s) => s.syncProgress);
  const summary = useProviderStore((s) => s.syncSummary);
  const error = useProviderStore((s) => s.lastError);
  const cancel = useProviderStore((s) => s.cancelSync);

  if (!syncing && !summary && !error) return null;

  const pct = progress && progress.total > 0 ? Math.round((progress.current / progress.total) * 100) : null;

  return (
    <div className={`sync-banner${error ? " error" : ""}`}>
      {syncing && (
        <>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
            <strong>{message || "Working…"}</strong>
            <button onClick={cancel}>Cancel</button>
          </div>
          <div className="sync-bar">
            <div className="sync-bar-fill" style={{ width: pct != null ? `${pct}%` : "30%", animation: pct == null ? "sync-bar-pulse 1.4s ease-in-out infinite" : undefined }} />
          </div>
          {pct != null && <small style={{ color: "var(--fg-muted)" }}>{progress!.current} / {progress!.total} ({pct}%)</small>}
        </>
      )}
      {!syncing && error && <><strong>Error: </strong>{error}</>}
      {!syncing && !error && summary && <>{summary}</>}
    </div>
  );
}
