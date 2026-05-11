// Movies library — shelf layout: categories rendered as horizontal rails, plus
// sort + genre/year filters + Random ("feeling lucky") jump.

import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { db } from "@data/db/database";
import { movieRepo, parentalLockRepo } from "@data/db/repositories";
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
import type { Category, Movie } from "@domain/model";

export function MoviesScreen() {
  const providerId = useProviderStore((s) => s.activeProviderId);
  const unlocked = useParentalStore((s) => s.unlocked);
  const loadFilters = useCategoryFilters((s) => s.load);
  const allowed = useCategoryFilters((s) => providerId == null ? null : s.allowed[`${providerId}:MOVIE`]);
  const [filter, setFilter] = useState("");
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: "added", dir: "desc" });
  const [genreYear, setGenreYear] = useState<{ selectedGenres: Set<string>; yearMin: number | null; yearMax: number | null }>({
    selectedGenres: new Set(), yearMin: null, yearMax: null,
  });
  const t = useI18n((s) => s.t);
  const lang = useI18n((s) => s.lang); void lang;
  const navigate = useNavigate();

  useEffect(() => { if (providerId != null) void loadFilters(providerId); }, [providerId, loadFilters]);

  const movies = useLiveQuery<Movie[]>(async () => providerId == null ? [] : movieRepo.forProvider(providerId), [providerId]);
  const cats = useLiveQuery<Category[]>(
    async () => {
      if (providerId == null) return [];
      const all = await db.categories.filter((c) => (c as Category & { providerId?: number }).providerId === providerId && c.type === "MOVIE").toArray();
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
  const filteredMovies = useMemo(() => {
    if (!movies) return [];
    const lc = debouncedFilter.toLowerCase().trim();
    let list = lc ? movies.filter((m) => m.name.toLowerCase().includes(lc)) : movies;
    list = filterByGenreYear(list, genreYear.selectedGenres, genreYear.yearMin, genreYear.yearMax);
    return list;
  }, [movies, debouncedFilter, genreYear]);

  const sorted = useMemo(() => {
    const sign = sort.dir === "asc" ? 1 : -1;
    return filteredMovies.slice().sort((a, b) => {
      switch (sort.key) {
        case "alpha": return sign * a.name.localeCompare(b.name);
        case "rating": return sign * (a.rating - b.rating);
        case "year": {
          const ay = Number.parseInt(a.year?.slice(0, 4) ?? a.releaseDate?.slice(0, 4) ?? "0", 10);
          const by = Number.parseInt(b.year?.slice(0, 4) ?? b.releaseDate?.slice(0, 4) ?? "0", 10);
          return sign * (ay - by);
        }
        case "added":
        default:
          return sign * (a.addedAt - b.addedAt);
      }
    });
  }, [filteredMovies, sort]);

  const groups = useMemo(() => {
    if (!cats) return [];
    return cats.map((c) => ({
      category: c,
      items: sorted.filter((m) => m.categoryId === c.id).slice(0, 30),
    })).filter((g) => g.items.length > 0);
  }, [sorted, cats]);

  const randomPick = () => {
    if (sorted.length === 0) return;
    const choice = sorted[Math.floor(Math.random() * sorted.length)]!;
    navigate(`/movies/${choice.id}`);
  };

  if (providerId == null) return <div className="empty">{t("home.noProvider")}</div>;
  if (!movies || !cats) return (
    <div>
      <h2>{t("movies.title")}</h2>
      <Skeleton variant="shelf" />
      <div style={{ height: 24 }} />
      <Skeleton variant="shelf" />
    </div>
  );
  if (movies.length === 0) return <div className="empty">{t("movies.noMovies")}</div>;

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16, alignItems: "center", flexWrap: "wrap" }}>
        <h2 style={{ margin: 0 }}>{t("movies.title")}</h2>
        <CategoryFilter providerId={providerId} contentType="MOVIE" />
        <GenreYearFilter
          items={movies}
          selectedGenres={genreYear.selectedGenres}
          yearMin={genreYear.yearMin}
          yearMax={genreYear.yearMax}
          onChange={setGenreYear}
        />
        <SortMenu value={sort} onChange={setSort} />
        <button onClick={randomPick} title="Pick a random movie">🎲 Random</button>
        <input placeholder={t("movies.filter")} value={filter} onChange={(e) => setFilter(e.target.value)} />
        <span style={{ color: "var(--fg-muted)" }}>{sorted.length} movies</span>
      </div>
      {groups.length === 0 && (
        <div className="empty" style={{ height: 200 }}>No movies match the current filters.</div>
      )}
      {groups.map((g) => {
        const totalInCat = sorted.filter((m) => m.categoryId === g.category.id).length;
        return (
          <section key={g.category.id} style={{ marginBottom: 24 }}>
            <div style={{ display: "flex", alignItems: "baseline", gap: 12, margin: "8px 0" }}>
              <h3 style={{ margin: 0 }}>{g.category.name}</h3>
              <span style={{ color: "var(--fg-muted)", fontSize: 13 }}>{totalInCat}</span>
              {totalInCat > g.items.length && (
                <Link to={`/movies/category/${g.category.id}`} style={{ marginLeft: "auto" }}>Show all →</Link>
              )}
            </div>
            <div className="shelf">
              {g.items.map((m) => (
                <Link key={m.id} to={`/movies/${m.id}`} style={{ textDecoration: "none", color: "inherit" }}>
                  <PosterCard
                    posterUrl={m.posterUrl}
                    title={m.name}
                    subtitle={[m.year, m.rating ? `★ ${m.rating.toFixed(1)}` : null].filter(Boolean).join(" · ") || null}
                    progress={m.watchProgress && m.durationSeconds ? m.watchProgress / (m.durationSeconds * 1000) : 0}
                  />
                </Link>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}
