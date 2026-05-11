// Watchlist screen — content the user wants to watch later. Separate from
// Favorites which is "already liked". Auto-removes items that disappear from
// the catalog (e.g. after provider switch).

import { useLiveQuery } from "dexie-react-hooks";
import { Link } from "react-router-dom";
import { movieRepo, seriesRepo, watchlistRepo } from "@data/db/repositories";
import { useProviderStore } from "@app/stores/providers";
import { PosterCard } from "@app/components/PosterCard";

export function WatchlistScreen() {
  const providerId = useProviderStore((s) => s.activeProviderId);
  const items = useLiveQuery(
    async () => {
      if (providerId == null) return [];
      const list = await watchlistRepo.list(providerId);
      const movies = new Map((await movieRepo.forProvider(providerId)).map((m) => [m.id, m]));
      const series = new Map((await seriesRepo.forProvider(providerId)).map((s) => [s.id, s]));
      return list.map((w) => {
        if (w.contentType === "MOVIE") {
          const m = movies.get(w.contentId);
          return m ? { id: w.id, href: `/movies/${m.id}`, title: m.name, poster: m.posterUrl, kind: "Movie", year: m.year } : null;
        }
        if (w.contentType === "SERIES") {
          const s = series.get(w.contentId);
          return s ? { id: w.id, href: `/series/${s.id}`, title: s.name, poster: s.posterUrl, kind: "Series", year: s.releaseDate?.slice(0, 4) ?? null } : null;
        }
        return null;
      }).filter((x): x is NonNullable<typeof x> => x !== null);
    },
    [providerId],
  );

  if (providerId == null) return <div className="empty">Add a provider in Settings first.</div>;
  if (!items) return <div className="empty">Loading…</div>;
  if (items.length === 0) return <div className="empty">Watchlist empty. Click the + button on any movie/series to add it here.</div>;

  return (
    <div>
      <h2>Watchlist <span style={{ color: "var(--fg-muted)", fontSize: 14 }}>({items.length})</span></h2>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: 12 }}>
        {items.map((it) => (
          <div key={it.id} style={{ position: "relative" }}>
            <Link to={it.href} style={{ textDecoration: "none", color: "inherit" }}>
              <PosterCard posterUrl={it.poster} title={it.title} subtitle={`${it.kind}${it.year ? " · " + it.year : ""}`} />
            </Link>
            <button
              style={{ position: "absolute", top: 4, right: 4, background: "rgba(0,0,0,0.6)", border: 0, color: "#fff", borderRadius: "50%", width: 24, height: 24, padding: 0, cursor: "pointer" }}
              onClick={() => watchlistRepo.remove(it.id)}
              title="Remove from watchlist"
            >✕</button>
          </div>
        ))}
      </div>
    </div>
  );
}
