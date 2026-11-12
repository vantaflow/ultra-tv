import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useState } from "react";
import { HScroll } from "@/ui/HScroll";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import { channelsCol, historyOf } from "@/db/queries";
import type { ChannelRow, HistoryRow, SeriesRow, Source } from "@/db/types";
import { useFavorites } from "@/hooks/data";
import { useNowNext } from "@/hooks/epg";
import { useNow } from "@/hooks/misc";
import { useT } from "@/i18n";
import { fmtDuration } from "@/lib/text";
import { usePlayer } from "@/player/store";
import { channelTarget, movieTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { hhmm } from "@/ui/common";
import { Icon } from "@/ui/Icon";
import { Img } from "@/ui/Img";
import { PosterCard } from "@/ui/Poster";
import { useIsWatchedMovie, useTraktRows, type TraktRow, type UpNextRow } from "@/trakt/hooks";
import { EmptyCatalog, NoSource } from "./states";
import { useFanart, useNewEpisodes, type NewEpSeries } from "@/hooks/extmeta";
import { fmtEpisodeLine, type NewEpisode } from "@/lib/extmeta";
import { localeOf } from "@/i18n/lang";
import { TitleLogo } from "@/ui/TitleLogo";

export function Home() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <HomeInner source={source} />;
}

function FavChannel({ source, ch }: { source: Source; ch: ChannelRow }) {
  const nn = useNowNext(source, ch.epg, ch.streamId, false);
  return (
    <button className="chan-card" onClick={() => usePlayer.getState().open(channelTarget(source, ch), "full", null)}>
      <b className="ellipsis">{ch.display}</b>
      <span className="ellipsis">{nn.now?.title ?? ""}</span>
    </button>
  );
}

function LiveNowCard({ source, ch }: { source: Source; ch: ChannelRow }) {
  const { lang } = usePrefs();
  const nn = useNowNext(source, ch.epg, ch.streamId, false);
  const p = nn.now;
  const ratio = p ? Math.min(1, Math.max(0, (Date.now() - p.start) / Math.max(1, p.end - p.start))) : 0;
  return (
    <button className="live-card" onClick={() => usePlayer.getState().open(channelTarget(source, ch), "full", null)}>
      <span className="lc-head">
        <span className="lc-logo" aria-hidden="true"><span className="ini">{(ch.display.match(/[\p{L}\p{N}]/gu) ?? []).slice(0, 2).join("").toUpperCase()}</span><Img src={ch.logo} contain /></span>
        <span className="lc-id">
          <b className="ellipsis">{ch.num} · {ch.display}</b>
          <span className="ellipsis s mono">{p ? `${hhmm(p.start, lang)} – ${hhmm(p.end, lang)}` : ""}</span>
        </span>
      </span>
      <span className="ellipsis lc-title">{p?.title ?? ""}</span>
      <span className="lc-trk"><i style={{ width: `${ratio * 100}%` }} /></span>
    </button>
  );
}

export function resumeOf(source: Source, h: HistoryRow) {
  const open = usePlayer.getState().open;
  if (h.kind === "movie") open(movieTarget(source, { streamId: h.refId, title: h.title, poster: h.image, ext: h.ext ?? "mp4" }, h.pos), "full");
  else open({
    kind: "episode", sourceId: source.id!, cid: source.cid, refId: h.epId ?? h.refId, title: h.title, image: h.image, ext: h.ext,
    seriesId: h.seriesId, season: h.season, episode: h.episode, startAt: h.pos,
  }, "full");
}

/** Rangée Trakt : uniquement des titres présents dans la playlist (films et séries mêlés, ordre Trakt). */
function TraktRowSection({ title, rows, seen, onOpen }: { title: string; rows: TraktRow[]; seen: (t: string, y?: number | null) => boolean; onOpen: (r: TraktRow) => void }) {
  if (!rows.length) return null;
  return (
    <section>
      <h2 className="section-title">{title}</h2>
      <HScroll className="row-scroll">
        {rows.map((r) => (
          <div key={`${r.kind}${r.ref}`} style={{ flex: "0 0 9.5rem" }}>
            <PosterCard kind={r.kind === "movie" ? "movie" : "tv"} year={r.row.year} title={r.row.title} image={r.row.poster} meta={r.row.year ? String(r.row.year) : undefined}
              watched={r.kind === "movie" && seen(r.row.title, r.row.year)} onClick={() => onOpen(r)} />
          </div>
        ))}
      </HScroll>
    </section>
  );
}

/** « À suivre (Trakt) » : séries dont l'épisode suivant est disponible ; ouvre la fiche (qui propose cet épisode). */
function UpNextSection({ title, rows, onOpen }: { title: string; rows: UpNextRow[]; onOpen: (u: UpNextRow) => void }) {
  const t = useT();
  if (!rows.length) return null;
  return (
    <section>
      <h2 className="section-title">{title}</h2>
      <HScroll className="row-scroll">
        {rows.map((u) => (
          <div key={`u${u.ref}`} style={{ flex: "0 0 9.5rem" }}>
            <PosterCard kind="tv" year={u.row.year} title={u.row.title} image={u.row.poster}
              meta={`${t("common.season", { n: u.season })} · ${t("common.episodeShort", { n: u.episode })}`} onClick={() => onOpen(u)} />
          </div>
        ))}
      </HScroll>
    </section>
  );
}

/** Titre d'une série « À la une » : logo Fanart si disponible, sinon le texte. */
function HeroSeriesTitle({ source, s }: { source: Source; s: SeriesRow }) {
  const art = useFanart({ kind: "tv", title: s.title, year: s.year, detailKey: `series:${source.id}:${s.seriesId}` });
  return <h2 className={art.logo ? "has-logo" : undefined}><TitleLogo logo={art.logo} title={s.title} /></h2>;
}

/** « Nouveaux épisodes » (TVmaze) : séries en cours ou favorites avec un épisode sorti ou à venir à ±7 jours. */
function NewEpisodesSection({ rows, onOpen }: { rows: NewEpisode<NewEpSeries>[]; onOpen: (seriesId: number) => void }) {
  const t = useT();
  const lang = usePrefs((p) => p.lang);
  if (!rows.length) return null;
  const now = Date.now();
  const code = (s: number, e: number) => t("ext.epCode", { s, e });
  return (
    <section>
      <h2 className="section-title">{t("home.newEpisodes")}</h2>
      <HScroll className="row-scroll">
        {rows.map((r) => (
          <div key={`n${r.series.seriesId}`} style={{ flex: "0 0 9.5rem" }}>
            <PosterCard kind="tv" year={r.series.year} title={r.series.title} image={r.series.poster}
              meta={fmtEpisodeLine(r.ep, now, localeOf(lang), code, false)} onClick={() => onOpen(r.series.seriesId)} />
          </div>
        ))}
      </HScroll>
    </section>
  );
}

/** Rangées calculées en local : affichées seulement avec assez de titres (une rangée de 2 affiches fait vide). */
const MIN_LOCAL_ROW = 4;

interface PosterItem { key: number; kind: "movie" | "tv"; title: string; year: number | null; image: string | null; meta?: string; watched?: boolean; open: () => void }

function PosterSection({ title, items }: { title: string; items: PosterItem[] }) {
  return (
    <section>
      <h2 className="section-title">{title}</h2>
      <HScroll className="row-scroll">
        {items.map((m) => <div key={m.key} style={{ flex: "0 0 9.5rem" }}><PosterCard kind={m.kind} year={m.year} title={m.title} image={m.image} meta={m.meta ?? (m.year ? String(m.year) : undefined)} watched={m.watched} onClick={m.open} /></div>)}
      </HScroll>
    </section>
  );
}

function HomeInner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const { profileId, lang } = usePrefs();
  const favs = useFavorites(source, "live");
  const hist = useLiveQuery(async () => (await historyOf(profileId, source.id!)).filter((h) => h.dur > 0 && h.pos / h.dur < 0.95).slice(0, 12), [profileId, source.id]) ?? [];
  const favChans = useLiveQuery(async () => (await Promise.all(favs.slice(0, 12).map((f) => db.channels.where("[sourceId+streamId]").equals([source.cid, f.refId]).first()))).filter((c): c is ChannelRow => !!c), [favs, source.cid]) ?? [];
  const featured = useLiveQuery(async () => favChans[0] ?? (await channelsCol(source.cid, null).filter((c) => !c.sep && !!c.epg).first()) ?? (await channelsCol(source.cid, null).filter((c) => !c.sep).first()), [favChans, source.cid]);
  const nn = useNowNext(source, featured?.epg, featured?.streamId);
  const trakt = useTraktRows(source);
  const favSeries = useFavorites(source, "series");
  const seriesHist = useLiveQuery(async () => (await historyOf(profileId, source.id!)).filter((h) => h.kind === "series"), [profileId, source.id]) ?? [];
  const newEps = useNewEpisodes(source, seriesHist, favSeries);
  const seen = useIsWatchedMovie();
  const openTrakt = (r: TraktRow) => nav(r.kind === "movie" ? `/movie/${r.ref}` : `/serie/${r.ref}`);
  const movies = useLiveQuery(() => db.movies.where("[sourceId+added]").between([source.cid, -1], [source.cid, Infinity]).reverse().limit(24).toArray(), [source.cid]) ?? [];
  const series = useLiveQuery(() => db.series.where("[sourceId+added]").between([source.cid, -1], [source.cid, Infinity]).reverse().limit(24).toArray(), [source.cid]) ?? [];
  // Mieux notés : note du fournisseur (index [sourceId+rating]), affiche obligatoire.
  const topMovies = useLiveQuery(() => db.movies.where("[sourceId+rating]").between([source.cid, 0.01], [source.cid, 10], true, true).reverse().filter((m) => !!m.poster).limit(20).toArray(), [source.cid]) ?? [];
  const topSeries = useLiveQuery(() => db.series.where("[sourceId+rating]").between([source.cid, 0.01], [source.cid, 10], true, true).reverse().filter((m) => !!m.poster).limit(20).toArray(), [source.cid]) ?? [];
  // « Parce que vous avez regardé X » : dernier film de l'historique, puis les mieux notés de la même catégorie.
  const because = useLiveQuery(async () => {
    const last = (await historyOf(profileId, source.id!)).find((h) => h.kind === "movie");
    if (!last) return null;
    const seed = await db.movies.where("[sourceId+streamId]").equals([source.cid, last.refId]).first();
    if (!seed) return null;
    const list = await db.movies.where("[sourceId+catExt+rating]").between([source.cid, seed.catExt, 0], [source.cid, seed.catExt, Infinity]).reverse()
      .filter((m) => m.streamId !== seed.streamId && !!m.poster).limit(20).toArray();
    return { title: seed.title, list };
  }, [profileId, source.id, source.cid]);

  // « À la une » qui défile : la chaîne mise en avant, puis les séries les mieux notées avec image paysage.
  const heroSeries = useLiveQuery(() => db.series.where("[sourceId+rating]").between([source.cid, 0.01], [source.cid, 10], true, true).reverse().filter((x) => !!x.backdrop).limit(5).toArray(), [source.cid]) ?? [];
  const [heroIdx, setHeroIdx] = useState(0);
  const [heroPaused, setHeroPaused] = useState(false);
  const animations = usePrefs((p) => p.animations);
  const heroCount = 1 + heroSeries.length;
  useEffect(() => {
    if (heroCount < 2 || heroPaused || document.documentElement.dataset.anim === "off") return;
    const iv = window.setInterval(() => setHeroIdx((k) => (k + 1) % heroCount), 9_000);
    return () => window.clearInterval(iv);
  }, [heroCount, heroPaused, animations]);
  const slide = heroIdx % heroCount === 0 ? null : heroSeries[(heroIdx % heroCount) - 1] ?? null;

  const now = useNow(30_000);
  const hour = new Date(now).getHours();
  const greet = t(hour < 12 ? "home.greetMorning" : hour < 18 ? "home.greetAfternoon" : "home.greetEvening");
  const liveRow = useLiveQuery(async () => {
    if (favChans.length >= 5) return favChans.slice(0, 5);
    const extra = await channelsCol(source.cid, null).filter((c) => !c.sep && !!c.epg).limit(8).toArray();
    return [...favChans, ...extra.filter((c) => !favChans.some((f) => f.streamId === c.streamId))].slice(0, 5);
  }, [favChans, source.cid]) ?? [];
  const shortcuts: { to: string; icon: "live" | "guide" | "movies" | "series" | "heart"; label: string; sub: string }[] = [
    { to: "/live", icon: "live", label: t("nav.live"), sub: t("common.channelsCount", { count: source.counts.live }) },
    { to: "/guide", icon: "guide", label: t("nav.guide"), sub: t("home.tonight") },
    { to: "/movies", icon: "movies", label: t("nav.movies"), sub: source.counts.movie.toLocaleString(lang) },
    { to: "/series", icon: "series", label: t("nav.series"), sub: source.counts.series.toLocaleString(lang) },
    { to: "/favorites", icon: "heart", label: t("nav.favorites"), sub: String(favs.length) },
  ];

  if (!source.cid) return <EmptyCatalog />;
  return (
    <div className="page home">
      <header className="home-head">
        <h1>{greet}</h1>
        <span className="home-stats">{t("home.stats", { live: source.counts.live.toLocaleString(lang), movies: source.counts.movie.toLocaleString(lang), series: source.counts.series.toLocaleString(lang) })}</span>
        <span className="home-clock mono">{hhmm(now, lang)}</span>
      </header>
      <div className="home-top">
      <section className="hero" aria-label={t("home.featured")}
        onMouseEnter={() => setHeroPaused(true)} onMouseLeave={() => setHeroPaused(false)}
        onFocus={() => setHeroPaused(true)} onBlur={() => setHeroPaused(false)}>
        {slide ? (
          <>
            <div className="art" aria-hidden="true"><div key={slide.id} className="slide-bg" style={{ backgroundImage: `url("${(slide.backdrop || slide.poster || "").replace(/"/g, "%22")}")` }} /></div>
            <div className="meta">
              <span className="badge-live">{t("nav.series")}</span>
              <span>{[slide.year, slide.rating > 0 ? `★ ${slide.rating.toFixed(1)}` : null].filter(Boolean).join(" · ")}</span>
            </div>
            <HeroSeriesTitle source={source} s={slide} />
            <div className="actions">
              <button className="btn primary lg" onClick={() => nav(`/serie/${slide.seriesId}`)}><Icon name="play" size={20} fill />{t("common.watch")}</button>
            </div>
          </>
        ) : (<>
        <div className="art" aria-hidden="true">{nn.now?.title && featured?.logo && <Img src={featured.logo} contain />}</div>
        {featured ? (
          <>
            <div className="meta">
              <span className="badge-live">{t("common.live")}</span>
              <span>{nn.now ? t("home.until", { c: featured.display, t: hhmm(nn.now.end, lang) }) : featured.display}</span>
            </div>
            <h2>{nn.now?.title || featured.display}</h2>
            <div className="actions">
              <button className="btn primary lg" onClick={() => usePlayer.getState().open(channelTarget(source, featured), "full", null)}><Icon name="play" size={20} fill />{t("common.watch")}</button>
              <button className="btn lg" onClick={() => nav("/guide")}>{t("home.guide")}</button>
            </div>
          </>
        ) : <h2>{t("home.emptyHero")}</h2>}
</>)}
        {heroCount > 1 && (
          <div className="dots" role="tablist" aria-label={t("home.featured")}>
            {Array.from({ length: heroCount }, (_, k) => <button key={k} type="button" role="tab" aria-current={k === heroIdx % heroCount} aria-label={String(k + 1)} onClick={() => setHeroIdx(k)} />)}
          </div>
        )}
      </section>
      <nav className="shortcuts" aria-label={t("home.shortcuts")}>
        {shortcuts.map((c, i) => (
          <button key={c.to} className={i === 0 ? "first" : ""} onClick={() => nav(c.to)}>
            <Icon name={c.icon} size={22} />
            <span><b>{c.label}</b><span className="s">{c.sub}</span></span>
          </button>
        ))}
      </nav>
      </div>

      {liveRow.length > 0 && (
        <section>
          <h2 className="section-title">{t("home.liveNow")}</h2>
          <div className="live-row">{liveRow.map((c) => <LiveNowCard key={c.id} source={source} ch={c} />)}</div>
        </section>
      )}

      {hist.length > 0 && (
        <section>
          <h2 className="section-title">{t("home.resume")}</h2>
          <HScroll className="row-scroll">
            {hist.map((h) => (
              <button key={h.key} className="resume-card" onClick={() => resumeOf(source, h)}>
                <Img src={h.image} />
                <span className="rc-tx">
                  <b className="clamp-2">{h.title}</b>
                  <span className="s ellipsis">{h.kind === "series" && h.season ? `${t("common.season", { n: h.season })} · ${t("common.episodeShort", { n: h.episode ?? 0 })} · ` : ""}{t("common.minLeft", { t: fmtDuration(h.dur - h.pos) })}</span>
                </span>
                <span className="progress"><i style={{ width: `${(h.pos / h.dur) * 100}%` }} /></span>
              </button>
            ))}
          </HScroll>
        </section>
      )}

      <NewEpisodesSection rows={newEps} onOpen={(id) => nav(`/serie/${id}`)} />
      <UpNextSection title={t("home.traktUpNext")} rows={trakt.upNext} onOpen={(u) => nav(`/serie/${u.ref}`)} />
      <TraktRowSection title={t("home.traktWatchlist")} rows={trakt.watchlist} seen={seen} onOpen={openTrakt} />
      <TraktRowSection title={t("home.traktRecs")} rows={trakt.recommendations} seen={seen} onOpen={openTrakt} />
      <TraktRowSection title={t("home.traktTrending")} rows={trakt.trending} seen={seen} onOpen={openTrakt} />
      <TraktRowSection title={t("home.traktPopular")} rows={trakt.popular} seen={seen} onOpen={openTrakt} />
      {trakt.lists.map((l) => <TraktRowSection key={l.id} title={t("home.traktList", { name: l.name })} rows={l.rows} seen={seen} onOpen={openTrakt} />)}

      {because && because.list.length >= MIN_LOCAL_ROW && (
        <PosterSection title={t("home.because", { t: because.title })} items={because.list.map((m) => ({ key: m.id!, kind: "movie" as const, title: m.title, year: m.year, image: m.poster, open: () => nav(`/movie/${m.streamId}`), watched: seen(m.title, m.year) }))} />
      )}

      {movies.length > 0 && (
        <section>
          <h2 className="section-title">{t("home.newMovies")}<button className="btn sm" onClick={() => nav("/movies")}>{t("common.all")}</button></h2>
          <HScroll className="row-scroll">
            {movies.map((m) => <div key={m.id} style={{ flex: "0 0 9.5rem" }}><PosterCard kind="movie" year={m.year} title={m.title} image={m.poster} meta={m.year ? String(m.year) : undefined} watched={seen(m.title, m.year)} onClick={() => nav(`/movie/${m.streamId}`)} /></div>)}
          </HScroll>
        </section>
      )}
      {series.length > 0 && (
        <section>
          <h2 className="section-title">{t("home.newSeries")}<button className="btn sm" onClick={() => nav("/series")}>{t("common.all")}</button></h2>
          <HScroll className="row-scroll">
            {series.map((m) => <div key={m.id} style={{ flex: "0 0 9.5rem" }}><PosterCard kind="tv" year={m.year} title={m.title} image={m.poster} meta={m.year ? String(m.year) : undefined} onClick={() => nav(`/serie/${m.seriesId}`)} /></div>)}
          </HScroll>
        </section>
      )}
      {topMovies.length >= MIN_LOCAL_ROW && (
        <PosterSection title={t("home.topMovies")} items={topMovies.map((m) => ({ key: m.id!, kind: "movie" as const, title: m.title, year: m.year, image: m.poster, meta: `★ ${m.rating.toFixed(1)}`, open: () => nav(`/movie/${m.streamId}`), watched: seen(m.title, m.year) }))} />
      )}
      {topSeries.length >= MIN_LOCAL_ROW && (
        <PosterSection title={t("home.topSeries")} items={topSeries.map((m) => ({ key: m.id!, kind: "tv" as const, title: m.title, year: m.year, image: m.poster, meta: `★ ${m.rating.toFixed(1)}`, open: () => nav(`/serie/${m.seriesId}`) }))} />
      )}
    </div>
  );
}
