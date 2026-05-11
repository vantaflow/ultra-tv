// Favorites screen with drag-and-drop reordering. Uses HTML5 native DnD
// (touch fallback would need a touch shim — out of scope here). Persists the
// new order via `favoriteRepo.position`.

import { useLiveQuery } from "dexie-react-hooks";
import { useState } from "react";
import { Link } from "react-router-dom";
import { db } from "@data/db/database";
import { favoriteRepo, movieRepo, seriesRepo, channelRepo } from "@data/db/repositories";
import { useProviderStore } from "@app/stores/providers";
import { PosterCard } from "@app/components/PosterCard";
import type { Favorite } from "@domain/model";

interface ResolvedFavorite {
  fav: Favorite;
  title: string;
  posterUrl: string | null;
  href: string;
}

export function FavoritesScreen() {
  const providerId = useProviderStore((s) => s.activeProviderId);
  const favs = useLiveQuery(async () => providerId == null ? [] : favoriteRepo.list(providerId), [providerId]);

  const resolved = useLiveQuery<ResolvedFavorite[]>(
    async () => {
      if (providerId == null || !favs) return [];
      const movies = new Map((await movieRepo.forProvider(providerId)).map((m) => [m.id, m]));
      const series = new Map((await seriesRepo.forProvider(providerId)).map((s) => [s.id, s]));
      const channels = new Map((await channelRepo.forProvider(providerId)).map((c) => [c.id, c]));

      return favs
        .slice()
        .sort((a, b) => a.position - b.position)
        .map((f): ResolvedFavorite | null => {
          if (f.contentType === "MOVIE") {
            const m = movies.get(f.contentId); if (!m) return null;
            return { fav: f, title: m.name, posterUrl: m.posterUrl, href: `/movies/${m.id}` };
          }
          if (f.contentType === "SERIES") {
            const s = series.get(f.contentId); if (!s) return null;
            return { fav: f, title: s.name, posterUrl: s.posterUrl, href: `/series/${s.id}` };
          }
          if (f.contentType === "LIVE") {
            const c = channels.get(f.contentId); if (!c) return null;
            return { fav: f, title: c.name, posterUrl: c.logoUrl, href: "/live" };
          }
          return null;
        })
        .filter((x): x is ResolvedFavorite => x !== null);
    },
    [providerId, favs],
  );

  const [dragIdx, setDragIdx] = useState<number | null>(null);

  if (providerId == null) return <div className="empty">Add a provider in Settings first.</div>;
  if (!resolved) return <div className="empty">Loading…</div>;
  if (resolved.length === 0) return <div className="empty">No favorites yet. Click the ★ button on movies, series or channels.</div>;

  const onDragStart = (idx: number) => () => setDragIdx(idx);

  const onDrop = async (idx: number) => {
    if (dragIdx == null || dragIdx === idx) return;
    const ordered = resolved.slice();
    const [moved] = ordered.splice(dragIdx, 1);
    ordered.splice(idx, 0, moved!);
    // Reassign positions and persist
    await db.transaction("rw", db.favorites, async () => {
      for (let i = 0; i < ordered.length; i++) {
        await db.favorites.update(ordered[i]!.fav.id, { position: i });
      }
    });
    setDragIdx(null);
  };

  return (
    <div>
      <h2>Favorites</h2>
      <p style={{ color: "var(--fg-muted)" }}>Drag a poster to reorder.</p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: 12 }}>
        {resolved.map((r, idx) => (
          <div
            key={r.fav.id}
            draggable
            onDragStart={onDragStart(idx)}
            onDragOver={(e) => e.preventDefault()}
            onDrop={() => onDrop(idx)}
            style={{ opacity: dragIdx === idx ? 0.3 : 1, cursor: "grab" }}
          >
            <Link to={r.href} style={{ textDecoration: "none", color: "inherit" }}>
              <PosterCard posterUrl={r.posterUrl} title={r.title} />
            </Link>
            <div style={{ marginTop: 4, display: "flex", justifyContent: "space-between", fontSize: 11, color: "var(--fg-muted)" }}>
              <span>{r.fav.contentType}</span>
              <button onClick={(e) => { e.preventDefault(); void favoriteRepo.remove(r.fav.id); }}>✕</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
