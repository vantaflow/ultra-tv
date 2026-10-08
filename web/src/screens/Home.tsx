import { useLiveQuery } from "dexie-react-hooks";
import { HScroll } from "@/ui/HScroll";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import { channelsCol, historyOf } from "@/db/queries";
import type { ChannelRow, HistoryRow, Source } from "@/db/types";
import { useFavorites } from "@/hooks/data";
import { useNowNext } from "@/hooks/epg";
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
import { useIsWatchedMovie, useTraktRows, type TraktRow } from "@/trakt/hooks";
import { EmptyCatalog, NoSource } from "./states";

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

function resumeOf(source: Source, h: HistoryRow) {
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
  const seen = useIsWatchedMovie();
  const openTrakt = (r: TraktRow) => nav(r.kind === "movie" ? `/movie/${r.ref}` : `/serie/${r.ref}`);
  const movies = useLiveQuery(() => db.movies.where("[sourceId+added]").between([source.cid, -1], [source.cid, Infinity]).reverse().limit(24).toArray(), [source.cid]) ?? [];
  const series = useLiveQuery(() => db.series.where("[sourceId+added]").between([source.cid, -1], [source.cid, Infinity]).reverse().limit(24).toArray(), [source.cid]) ?? [];

  if (!source.cid) return <EmptyCatalog />;
  return (
    <div className="page">
      <section className="hero" aria-label={t("home.featured")}>
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
      </section>

      {hist.length > 0 && (
        <section>
          <h2 className="section-title">{t("home.resume")}</h2>
          <HScroll className="row-scroll">
            {hist.map((h) => (
              <button key={h.key} className="thumb-card" onClick={() => resumeOf(source, h)}>
                <span className="t"><Img src={h.image} /><span className="progress"><i style={{ width: `${(h.pos / h.dur) * 100}%` }} /></span></span>
                <b className="ellipsis">{h.title}</b>
                <span className="s">{h.kind === "series" && h.season ? `${t("common.season", { n: h.season })} · ${t("common.episodeShort", { n: h.episode ?? 0 })} · ` : ""}{t("common.minLeft", { t: fmtDuration(h.dur - h.pos) })}</span>
              </button>
            ))}
          </HScroll>
        </section>
      )}

      <TraktRowSection title={t("home.traktWatchlist")} rows={trakt.watchlist} seen={seen} onOpen={openTrakt} />
      <TraktRowSection title={t("home.traktRecs")} rows={trakt.recommendations} seen={seen} onOpen={openTrakt} />
      <TraktRowSection title={t("home.traktTrending")} rows={trakt.trending} seen={seen} onOpen={openTrakt} />
      <TraktRowSection title={t("home.traktPopular")} rows={trakt.popular} seen={seen} onOpen={openTrakt} />

      {favChans.length > 0 && (
        <section>
          <h2 className="section-title">{t("home.favChannels")}</h2>
          <HScroll className="row-scroll">{favChans.map((c) => <FavChannel key={c.id} source={source} ch={c} />)}</HScroll>
        </section>
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
    </div>
  );
}
