// Home screen with multiple rails:
//   1. Continue Watching   (resume your media)
//   2. Favorites           (your starred movies/series/channels)
//   3. Top Rated Movies    (rating ≥ 7)
//   4. Top Rated Series    (rating ≥ 7)
//   5. Recently Added — Movies
//   6. Recently Updated — Series
//   7. Live TV shortcuts   (first 20 channels)
// Each rail self-hides when empty.

import { useLiveQuery } from "dexie-react-hooks";
import { Link } from "react-router-dom";
import {
  channelRepo,
  favoriteRepo,
  historyRepo,
  movieRepo,
  seriesRepo,
} from "@data/db/repositories";
import { useEffect } from "react";
import { useProviderStore } from "@app/stores/providers";
import { useCategoryFilters } from "@app/stores/categoryFilters";
import { PosterCard } from "@app/components/PosterCard";
import { ProxyImg } from "@app/components/ProxyImg";
import { useI18n } from "@app/i18n";
import { Skeleton } from "@app/components/Skeleton";

export function HomeScreen() {
  const providerId = useProviderStore((s) => s.activeProviderId);
  const t = useI18n((s) => s.t);
  const lang = useI18n((s) => s.lang); void lang;
  const loadFilters = useCategoryFilters((s) => s.load);
  const liveAllowed = useCategoryFilters((s) => providerId == null ? null : s.allowed[`${providerId}:LIVE`]);
  const movieAllowed = useCategoryFilters((s) => providerId == null ? null : s.allowed[`${providerId}:MOVIE`]);
  const seriesAllowed = useCategoryFilters((s) => providerId == null ? null : s.allowed[`${providerId}:SERIES`]);

  useEffect(() => { if (providerId != null) void loadFilters(providerId); }, [providerId, loadFilters]);

  const passesLive = (categoryId: number | null) =>
    liveAllowed == null ? true : categoryId != null && liveAllowed.includes(categoryId);
  const passesMovie = (categoryId: number | null) =>
    movieAllowed == null ? true : categoryId != null && movieAllowed.includes(categoryId);
  const passesSeries = (categoryId: number | null) =>
    seriesAllowed == null ? true : categoryId != null && seriesAllowed.includes(categoryId);

  const history = useLiveQuery(async () => providerId == null ? [] : historyRepo.list(providerId), [providerId]);
  const movies = useLiveQuery(async () => providerId == null ? [] : movieRepo.forProvider(providerId), [providerId]);
  const series = useLiveQuery(async () => providerId == null ? [] : seriesRepo.forProvider(providerId), [providerId]);
  const channels = useLiveQuery(async () => providerId == null ? [] : channelRepo.forProvider(providerId), [providerId]);
  const favs = useLiveQuery(async () => providerId == null ? [] : favoriteRepo.list(providerId), [providerId]);

  if (providerId == null) return <div className="empty">{t("home.noProvider")}</div>;
  if (movies === undefined || series === undefined) {
    return (
      <div>
        <h2 style={{ marginTop: 0 }}>{t("nav.home")}</h2>
        <Skeleton variant="shelf" count={6} />
        <div style={{ height: 24 }} />
        <Skeleton variant="shelf" count={6} />
      </div>
    );
  }

  const filteredMovies = (movies ?? []).filter((m) => passesMovie(m.categoryId));
  const filteredSeries = (series ?? []).filter((s) => passesSeries(s.categoryId));
  const filteredChannels = (channels ?? []).filter((c) => passesLive(c.categoryId));

  const recentMovies = filteredMovies.slice().sort((a, b) => b.addedAt - a.addedAt).slice(0, 20);
  const recentSeries = filteredSeries.slice().sort((a, b) => b.lastModified - a.lastModified).slice(0, 20);
  const topMovies = filteredMovies.filter((m) => m.rating >= 7).slice().sort((a, b) => b.rating - a.rating).slice(0, 20);
  const topSeries = filteredSeries.filter((s) => s.rating >= 7).slice().sort((a, b) => b.rating - a.rating).slice(0, 20);
  const liveSlice = filteredChannels.slice(0, 20);

  const moviesById = new Map((movies ?? []).map((m) => [m.id, m]));
  const seriesById = new Map((series ?? []).map((s) => [s.id, s]));

  const favRail = (favs ?? []).map((f) => {
    if (f.contentType === "MOVIE") {
      const m = moviesById.get(f.contentId);
      if (!m) return null;
      return { key: `m-${f.id}`, to: `/movies/${m.id}`, posterUrl: m.posterUrl, title: m.name };
    }
    if (f.contentType === "SERIES") {
      const s = seriesById.get(f.contentId);
      if (!s) return null;
      return { key: `s-${f.id}`, to: `/series/${s.id}`, posterUrl: s.posterUrl, title: s.name };
    }
    return null;
  }).filter((x): x is { key: string; to: string; posterUrl: string | null; title: string } => x !== null);

  const sectionStyle = { marginBottom: 28 } as const;

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>{t("nav.home")}</h2>

      {history && history.length > 0 && (
        <section style={sectionStyle}>
          <h3>{t("home.continue")}</h3>
          <div className="shelf">
            {history.slice(0, 20).map((h) => (
              <Link
                key={h.id}
                to={h.contentType === "MOVIE" ? `/movies/${h.contentId}` :
                    h.contentType === "SERIES_EPISODE" || h.contentType === "SERIES" ? `/series/${h.seriesId ?? h.contentId}` :
                    `/live`}
                style={{ textDecoration: "none", color: "inherit" }}
              >
                <PosterCard
                  posterUrl={h.posterUrl}
                  title={h.title}
                  subtitle={h.seasonNumber != null && h.episodeNumber != null ? `S${h.seasonNumber}E${h.episodeNumber}` : null}
                  progress={h.totalDurationMs ? h.resumePositionMs / h.totalDurationMs : 0}
                />
              </Link>
            ))}
          </div>
        </section>
      )}

      {favRail.length > 0 && (
        <section style={sectionStyle}>
          <h3>{t("home.favorites")}</h3>
          <div className="shelf">
            {favRail.map((f) => (
              <Link key={f.key} to={f.to} style={{ textDecoration: "none", color: "inherit" }}>
                <PosterCard posterUrl={f.posterUrl} title={f.title} />
              </Link>
            ))}
          </div>
        </section>
      )}

      {topMovies.length > 0 && (
        <section style={sectionStyle}>
          <h3>{t("home.topMovies")}</h3>
          <div className="shelf">
            {topMovies.map((m) => (
              <Link key={m.id} to={`/movies/${m.id}`} style={{ textDecoration: "none", color: "inherit" }}>
                <PosterCard posterUrl={m.posterUrl} title={m.name} subtitle={`★ ${m.rating.toFixed(1)}`} />
              </Link>
            ))}
          </div>
        </section>
      )}

      {topSeries.length > 0 && (
        <section style={sectionStyle}>
          <h3>{t("home.topSeries")}</h3>
          <div className="shelf">
            {topSeries.map((s) => (
              <Link key={s.id} to={`/series/${s.id}`} style={{ textDecoration: "none", color: "inherit" }}>
                <PosterCard posterUrl={s.posterUrl} title={s.name} subtitle={`★ ${s.rating.toFixed(1)}`} />
              </Link>
            ))}
          </div>
        </section>
      )}

      {recentMovies.length > 0 && (
        <section style={sectionStyle}>
          <h3>{t("home.recentMovies")}</h3>
          <div className="shelf">
            {recentMovies.map((m) => (
              <Link key={m.id} to={`/movies/${m.id}`} style={{ textDecoration: "none", color: "inherit" }}>
                <PosterCard posterUrl={m.posterUrl} title={m.name} subtitle={m.year ?? null} />
              </Link>
            ))}
          </div>
        </section>
      )}

      {recentSeries.length > 0 && (
        <section style={sectionStyle}>
          <h3>{t("home.recentSeries")}</h3>
          <div className="shelf">
            {recentSeries.map((s) => (
              <Link key={s.id} to={`/series/${s.id}`} style={{ textDecoration: "none", color: "inherit" }}>
                <PosterCard posterUrl={s.posterUrl} title={s.name} />
              </Link>
            ))}
          </div>
        </section>
      )}

      {liveSlice.length > 0 && (
        <section style={sectionStyle}>
          <h3>{t("home.live")}</h3>
          <div className="shelf">
            {liveSlice.map((c) => (
              <Link key={c.id} to="/live" style={{ textDecoration: "none", color: "inherit" }}>
                <div className="poster-card" style={{ width: 140 }}>
                  <div className="poster-img" style={{ width: 140, height: 140, borderRadius: 70 }}>
                    <ProxyImg src={c.logoUrl} alt="" style={{ width: "70%", height: "70%", objectFit: "contain" }} fallback={<span className="poster-fallback">{c.name.slice(0, 2)}</span>} />
                  </div>
                  <div className="poster-title" style={{ textAlign: "center" }}>{c.name}</div>
                </div>
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
