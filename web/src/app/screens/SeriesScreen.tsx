// Series library mirror of MoviesScreen — same sort/filter/random toolbox.

import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { db } from "@data/db/database";
import { seriesRepo, parentalLockRepo } from "@data/db/repositories";
import { PosterCard } from "@app/components/PosterCard";
import { CategoryFilter } from "@app/components/CategoryFilter";
import { SortMenu, type SortKey, type SortDir } from "@app/components/SortMenu";
import { GenreYearFilter, filterByGenreYear } from "@app/components/GenreYearFilter";
import { Skeleton } from "@app/components/Skeleton";
import { useProviderStore } from "@app/stores/providers";
import { useParentalStore } from "@app/stores/parental";
import { useCategoryFilters } from "@app/stores/categoryFilters";
import { useI18n } from "@app/i18n";
import { useDebounced } from "@app/hooks/useDebounced";
import type { Category, Series } from "@domain/model";

export function SeriesScreen() {
  const providerId = useProviderStore((s) => s.activeProviderId);
  const unlocked = useParentalStore((s) => s.unlocked);
  const loadFilters = useCategoryFilters((s) => s.load);
  const allowed = useCategoryFilters((s) => providerId == null ? null : s.allowed[`${providerId}:SERIES`]);
  const [filter, setFilter] = useState("");
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: "modified", dir: "desc" });
  const [genreYear, setGenreYear] = useState<{ selectedGenres: Set<string>; yearMin: number | null; yearMax: number | null }>({
    selectedGenres: new Set(), yearMin: null, yearMax: null,
  });
  const t = useI18n((s) => s.t);
  const lang = useI18n((s) => s.lang); void lang;
  const navigate = useNavigate();

  useEffect(() => { if (providerId != null) void loadFilters(providerId); }, [providerId, loadFilters]);

  const series = useLiveQuery<Series[]>(async () => providerId == null ? [] : seriesRepo.forProvider(providerId), [providerId]);
  const cats = useLiveQuery<Category[]>(
    async () => {
      if (providerId == null) return [];
      const all = await db.categories.filter((c) => (c as Category & { providerId?: number }).providerId === providerId && c.type === "SERIES").toArray();
      const locks = await parentalLockRepo.list(providerId);
      const lockedIds = new Set(locks.map((l) => l.categoryId));
      let list = all.filter((c) => !lockedIds.has(c.id) || unlocked);
      if (allowed != null) {
        const set = new Set(allowed);
        list = list.filter((c) => set.has(c.id));
      }
      return list;
    },
    [providerId, unlocked, allowed],
  );

  const debouncedFilter = useDebounced(filter, 250);
  const filteredSeries = useMemo(() => {
    if (!series) return [];
    const lc = debouncedFilter.toLowerCase().trim();
    const items = series.map((s) => ({ ...s, year: s.releaseDate?.slice(0, 4) ?? null }));
    let list = lc ? items.filter((s) => s.name.toLowerCase().includes(lc)) : items;
    list = filterByGenreYear(list, genreYear.selectedGenres, genreYear.yearMin, genreYear.yearMax);
    return list;
  }, [series, debouncedFilter, genreYear]);

  const sorted = useMemo(() => {
    const sign = sort.dir === "asc" ? 1 : -1;
    return filteredSeries.slice().sort((a, b) => {
      switch (sort.key) {
        case "alpha": return sign * a.name.localeCompare(b.name);
        case "rating": return sign * (a.rating - b.rating);
        case "year": {
          const ay = Number.parseInt(a.releaseDate?.slice(0, 4) ?? "0", 10);
          const by = Number.parseInt(b.releaseDate?.slice(0, 4) ?? "0", 10);
          return sign * (ay - by);
        }
        case "modified":
        default:
          return sign * (a.lastModified - b.lastModified);
      }
    });
  }, [filteredSeries, sort]);

  const groups = useMemo(() => {
    if (!cats) return [];
    return cats.map((c) => ({
      category: c,
      items: sorted.filter((s) => s.categoryId === c.id).slice(0, 30),
    })).filter((g) => g.items.length > 0);
  }, [sorted, cats]);

  const randomPick = () => {
    if (sorted.length === 0) return;
    const choice = sorted[Math.floor(Math.random() * sorted.length)]!;
    navigate(`/series/${choice.id}`);
  };

  if (providerId == null) return <div className="empty">{t("home.noProvider")}</div>;
  if (!series || !cats) return (
    <div>
      <h2>{t("series.title")}</h2>
      <Skeleton variant="shelf" />
      <div style={{ height: 24 }} />
      <Skeleton variant="shelf" />
    </div>
  );
  if (series.length === 0) return <div className="empty">{t("series.noSeries")}</div>;

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16, alignItems: "center", flexWrap: "wrap" }}>
        <h2 style={{ margin: 0 }}>{t("series.title")}</h2>
        <CategoryFilter providerId={providerId} contentType="SERIES" />
        <GenreYearFilter
          items={series.map((s) => ({ genre: s.genre, year: s.releaseDate?.slice(0, 4) ?? null, releaseDate: s.releaseDate }))}
          selectedGenres={genreYear.selectedGenres}
          yearMin={genreYear.yearMin}
          yearMax={genreYear.yearMax}
          onChange={setGenreYear}
        />
        <SortMenu value={sort} onChange={setSort} options={["modified", "alpha", "rating", "year"]} />
        <button onClick={randomPick} title="Pick a random series">🎲 Random</button>
        <input placeholder={t("movies.filter")} value={filter} onChange={(e) => setFilter(e.target.value)} />
        <span style={{ color: "var(--fg-muted)" }}>{sorted.length} series</span>
      </div>
      {groups.length === 0 && (
        <div className="empty" style={{ height: 200 }}>No series match the current filters.</div>
      )}
      {groups.map((g) => {
        const totalInCat = sorted.filter((s) => s.categoryId === g.category.id).length;
        return (
          <section key={g.category.id} style={{ marginBottom: 24 }}>
            <div style={{ display: "flex", alignItems: "baseline", gap: 12, margin: "8px 0" }}>
              <h3 style={{ margin: 0 }}>{g.category.name}</h3>
              <span style={{ color: "var(--fg-muted)", fontSize: 13 }}>{totalInCat}</span>
              {totalInCat > g.items.length && (
                <Link to={`/series/category/${g.category.id}`} style={{ marginLeft: "auto" }}>Show all →</Link>
              )}
            </div>
            <div className="shelf">
              {g.items.map((s) => (
                <Link key={s.id} to={`/series/${s.id}`} style={{ textDecoration: "none", color: "inherit" }}>
                  <PosterCard posterUrl={s.posterUrl} title={s.name} subtitle={[s.releaseDate?.slice(0, 4), s.rating ? `★ ${s.rating.toFixed(1)}` : null].filter(Boolean).join(" · ") || null} />
                </Link>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}
