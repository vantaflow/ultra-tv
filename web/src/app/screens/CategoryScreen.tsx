// Per-category grid view — reached from any shelf's "Show all" button. Renders the
// whole list (with virtualised pagination for huge categories) in a poster grid.

import { useLiveQuery } from "dexie-react-hooks";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { db } from "@data/db/database";
import { movieRepo, seriesRepo } from "@data/db/repositories";
import { PosterCard } from "@app/components/PosterCard";
import { SortMenu, type SortKey, type SortDir } from "@app/components/SortMenu";
import { useDebounced } from "@app/hooks/useDebounced";
import { useProviderStore } from "@app/stores/providers";
import type { Category, Movie, Series } from "@domain/model";

type Kind = "MOVIE" | "SERIES";

const PAGE = 100;

export function CategoryScreen({ kind }: { kind: Kind }) {
  const { id } = useParams();
  const categoryId = Number(id);
  const providerId = useProviderStore((s) => s.activeProviderId);
  const [filter, setFilter] = useState("");
  const debouncedFilter = useDebounced(filter, 250);
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: kind === "MOVIE" ? "added" : "modified", dir: "desc" });
  const [page, setPage] = useState(1);

  const category = useLiveQuery<Category | null>(
    async () => (await db.categories.get(categoryId)) ?? null,
    [categoryId],
  );

  const all = useLiveQuery<(Movie | Series)[]>(
    async () => {
      if (providerId == null) return [];
      const list = kind === "MOVIE"
        ? await movieRepo.forProvider(providerId)
        : await seriesRepo.forProvider(providerId);
      return list.filter((x) => x.categoryId === categoryId);
    },
    [providerId, categoryId, kind],
  );

  const filtered = useMemo(() => {
    if (!all) return [];
    const lc = debouncedFilter.toLowerCase().trim();
    return lc ? all.filter((x) => x.name.toLowerCase().includes(lc)) : all;
  }, [all, debouncedFilter]);

  const sorted = useMemo(() => {
    const sign = sort.dir === "asc" ? 1 : -1;
    return filtered.slice().sort((a, b) => {
      switch (sort.key) {
        case "alpha": return sign * a.name.localeCompare(b.name);
        case "rating": return sign * (a.rating - b.rating);
        case "year": {
          const ay = kind === "MOVIE"
            ? Number.parseInt((a as Movie).year?.slice(0, 4) ?? (a as Movie).releaseDate?.slice(0, 4) ?? "0", 10)
            : Number.parseInt((a as Series).releaseDate?.slice(0, 4) ?? "0", 10);
          const by = kind === "MOVIE"
            ? Number.parseInt((b as Movie).year?.slice(0, 4) ?? (b as Movie).releaseDate?.slice(0, 4) ?? "0", 10)
            : Number.parseInt((b as Series).releaseDate?.slice(0, 4) ?? "0", 10);
          return sign * (ay - by);
        }
        case "added": return sign * (((a as Movie).addedAt ?? 0) - ((b as Movie).addedAt ?? 0));
        case "modified": return sign * (((a as Series).lastModified ?? 0) - ((b as Series).lastModified ?? 0));
        default: return 0;
      }
    });
  }, [filtered, sort, kind]);

  const visible = sorted.slice(0, page * PAGE);
  const hasMore = sorted.length > visible.length;

  if (providerId == null) return <div className="empty">Add a provider in Settings first.</div>;
  if (!category || !all) return <div className="empty">Loading…</div>;

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16, alignItems: "center", flexWrap: "wrap" }}>
        <Link to={kind === "MOVIE" ? "/movies" : "/series"} style={{ color: "var(--fg-muted)" }}>← Back</Link>
        <h2 style={{ margin: 0 }}>{category.name}</h2>
        <SortMenu value={sort} onChange={setSort} options={kind === "MOVIE" ? ["added", "alpha", "rating", "year"] : ["modified", "alpha", "rating", "year"]} />
        <input placeholder="Filter…" value={filter} onChange={(e) => setFilter(e.target.value)} />
        <span style={{ color: "var(--fg-muted)" }}>{sorted.length} items</span>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: 12 }}>
        {visible.map((x) => (
          <Link
            key={x.id}
            to={kind === "MOVIE" ? `/movies/${x.id}` : `/series/${x.id}`}
            style={{ textDecoration: "none", color: "inherit" }}
          >
            <PosterCard
              posterUrl={x.posterUrl}
              title={x.name}
              subtitle={
                kind === "MOVIE"
                  ? [(x as Movie).year, x.rating ? `★ ${x.rating.toFixed(1)}` : null].filter(Boolean).join(" · ") || null
                  : [(x as Series).releaseDate?.slice(0, 4), x.rating ? `★ ${x.rating.toFixed(1)}` : null].filter(Boolean).join(" · ") || null
              }
            />
          </Link>
        ))}
      </div>

      {hasMore && (
        <div style={{ textAlign: "center", marginTop: 24 }}>
          <button onClick={() => setPage((p) => p + 1)}>
            Load more ({sorted.length - visible.length} left)
          </button>
        </div>
      )}
    </div>
  );
}
