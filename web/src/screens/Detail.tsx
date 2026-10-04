import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { db } from "@/db/db";
import { histKey, toggleFavorite } from "@/db/queries";
import type { HistoryRow, MovieRow, SeriesRow, Source } from "@/db/types";
import { useFavorites } from "@/hooks/data";
import { useT } from "@/i18n";
import { fmtClock, fmtDuration, firstString, toNum } from "@/lib/text";
import { presentable } from "@/lib/titleCleaner";
import { currentTransport } from "@/net/transport";
import { seriesInfo, vodInfo, type SeriesInfo, type VodInfo } from "@/net/xtream";
import { usePlayer } from "@/player/store";
import { movieTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { useUi } from "@/state/ui";
import { Icon } from "@/ui/Icon";
import { Img } from "@/ui/Img";
import { StateCard } from "@/ui/common";
import { credsOf } from "@/sync/core";
import { NoSource } from "./states";

const WEEK = 7 * 86400_000;

/** Fiche détaillée en cache (get_vod_info / get_series_info) : lue du disque, rafraîchie si vieille de plus de 7 jours. */
function useInfo<T>(source: Source, kind: "vod" | "series", id: number): { info: T | null; error: boolean } {
  const [state, setState] = useState<{ info: T | null; error: boolean }>({ info: null, error: false });
  useEffect(() => {
    let dead = false;
    const key = `${kind}:${source.id}:${id}`;
    setState({ info: null, error: false });
    (async () => {
      const hit = await db.details.get(key);
      if (hit && !dead) setState({ info: hit.json as T, error: false });
      if (hit && Date.now() - hit.fetchedAt < WEEK) return;
      if (source.type !== "xtream") return;
      try {
        const tr = await currentTransport();
        const json = kind === "vod" ? await vodInfo(tr, credsOf(source), id) : await seriesInfo(tr, credsOf(source), id);
        await db.details.put({ key, json, fetchedAt: Date.now() });
        if (!dead) setState({ info: json as T, error: false });
      } catch { if (!dead && !hit) setState({ info: null, error: true }); }
    })();
    return () => { dead = true; };
  }, [source.id, kind, id]);
  return state;
}

function Facts({ items }: { items: (string | number | null | undefined)[] }) {
  const f = items.filter((x) => x != null && x !== "" && x !== 0);
  return <div className="facts">{f.map((x, i) => <span key={i} style={{ display: "contents" }}>{i > 0 && <span aria-hidden="true">·</span>}<span>{x}</span></span>)}</div>;
}

function Cast({ names, label }: { names: string[]; label: string }) {
  if (!names.length) return null;
  return (
    <section style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
      <h2 style={{ fontSize: "1.125rem" }}>{label}</h2>
      <div className="cast">{names.slice(0, 8).map((n, i) => <div key={i} className="c"><i>{n.trim().slice(0, 1).toUpperCase()}</i><span>{n.trim()}</span></div>)}</div>
    </section>
  );
}
const splitNames = (s: string | undefined | null) => (presentable(s) ?? "").split(/[,;/]/).map((x) => x.trim()).filter(Boolean);

export function MovieDetail() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <MovieInner source={source} />;
}

function MovieInner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const { id } = useParams();
  const sid = toNum(id);
  const prefs = usePrefs();
  const movie = useLiveQuery(() => db.movies.where("[sourceId+streamId]").equals([source.cid, sid]).first(), [source.cid, sid]);
  const { info, error } = useInfo<VodInfo>(source, "vod", sid);
  const favs = useFavorites(source, "movie");
  const hist = useLiveQuery(() => db.history.get(histKey(prefs.profileId, source.id!, "movie", sid)), [sid, prefs.profileId]);
  if (movie === undefined) return <div className="detail" />;
  if (!movie) return <StateCard icon="alert" title={t("vod.empty")} actions={<button className="btn" onClick={() => nav(-1)}>{t("common.back")}</button>} />;
  const i = info?.info;
  const plot = presentable(i?.plot ?? i?.description);
  const isFav = favs.some((f) => f.refId === sid);
  const dur = i?.duration_secs ? fmtDuration(toNum(i.duration_secs)) : presentable(i?.duration);
  const resume = hist && hist.pos > 20 ? hist.pos : 0;
  const m: MovieRow = movie;
  const play = (startAt?: number) => usePlayer.getState().open(movieTarget(source, { streamId: m.streamId, title: m.title, poster: m.poster, ext: m.ext }, startAt), "full");
  const back = firstString(i?.backdrop_path) ?? i?.movie_image ?? m.poster;
  return (
    <div className="detail">
      <div className="backdrop" aria-hidden="true"><Img src={back} /></div>
      <div className="body">
        <button className="back" onClick={() => nav(-1)}><Icon name="back" size={20} stroke={2.5} />{t("nav.movies")}</button>
        <div style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}>
          <h1>{m.title}</h1>
          <Facts items={[m.year ?? presentable(i?.releasedate ?? i?.release_date)?.slice(0, 4), dur, presentable(i?.genre), m.rating > 0 ? `★ ${m.rating.toFixed(1)}` : null, m.ext.toUpperCase()]} />
          <p className="plot clamp-4">{plot ?? (info || error ? t("vod.noPlot") : t("vod.loadingInfo"))}</p>
          {error && <div className="muted">{t("vod.infoError")}</div>}
        </div>
        <div className="actions">
          <button className="btn primary lg" onClick={() => play()}><Icon name="play" size={20} fill />{t("common.play")}</button>
          {resume > 0 && <button className="btn lg" onClick={() => play(resume)}>{t("common.resumeAt", { t: fmtClock(resume) })}</button>}
          <button className="btn icon lg" aria-label={t(isFav ? "common.removeFav" : "common.addFav")} aria-pressed={isFav} onClick={async () => {
            const on = await toggleFavorite({ profile: prefs.profileId, sourceId: source.id!, kind: "movie", refId: sid, name: m.title, image: m.poster });
            useUi.getState().toast(t(on ? "toast.favAdded" : "toast.favRemoved"));
          }}><Icon name="heart" size={24} fill={isFav} /></button>
        </div>
        <Cast names={[...splitNames(i?.director).map((n) => n), ...splitNames(i?.cast ?? i?.actors)]} label={t("vod.cast")} />
      </div>
    </div>
  );
}

interface Ep { id: number; title: string; num: number; season: number; ext: string; secs: number; plot: string | null; img: string | null }

function parseEpisodes(info: SeriesInfo | null): { seasons: { n: number; name: string; eps: Ep[] }[] } {
  if (!info?.episodes) return { seasons: [] };
  type Raw = Array<{ id: string | number; title?: string; episode_num?: number | string; container_extension?: string; info?: { duration_secs?: number; plot?: string; movie_image?: string } }>;
  const raw = info.episodes as unknown;
  const bySeason: Record<string, Raw> = Array.isArray(raw) ? Object.fromEntries((raw as Raw[]).map((v, i) => [String(i + 1), v])) : (raw as Record<string, Raw>);
  const seasons = Object.entries(bySeason).map(([k, eps]) => {
    const n = toNum(k);
    const meta = info.seasons?.find((s) => toNum(s.season_number) === n);
    return {
      n, name: meta?.name ?? "",
      eps: (eps ?? []).map((e): Ep => ({
        id: toNum(e.id), title: e.title ?? "", num: toNum(e.episode_num), season: n, ext: e.container_extension || "mp4",
        secs: toNum(e.info?.duration_secs), plot: presentable(e.info?.plot), img: e.info?.movie_image ?? null,
      })).sort((a, b) => a.num - b.num),
    };
  }).filter((s) => s.eps.length).sort((a, b) => a.n - b.n);
  return { seasons };
}

export function SeriesDetail() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <SeriesInner source={source} />;
}

function SeriesInner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const { id } = useParams();
  const sid = toNum(id);
  const prefs = usePrefs();
  const row = useLiveQuery(() => db.series.where("[sourceId+seriesId]").equals([source.cid, sid]).first(), [source.cid, sid]);
  const { info, error } = useInfo<SeriesInfo>(source, "series", sid);
  const favs = useFavorites(source, "series");
  const hist = useLiveQuery(() => db.history.where("[profile+sourceId]").equals([prefs.profileId, source.id!]).filter((h) => h.seriesId === sid).toArray(), [sid, prefs.profileId]) ?? [];
  const parsed = useMemo(() => parseEpisodes(info), [info]);
  const [season, setSeason] = useState<number | null>(null);
  const histBy = useMemo(() => new Map(hist.map((h) => [h.epId ?? -1, h] as [number, HistoryRow])), [hist]);
  const last = useMemo(() => [...hist].sort((a, b) => b.updatedAt - a.updatedAt)[0], [hist]);
  useEffect(() => { if (season == null && parsed.seasons.length) setSeason(last?.season ?? parsed.seasons[0]!.n); }, [parsed, last, season]);
  if (row === undefined) return <div className="detail" />;
  if (!row) return <StateCard icon="alert" title={t("vod.empty")} actions={<button className="btn" onClick={() => nav(-1)}>{t("common.back")}</button>} />;
  const s: SeriesRow = row;
  const i = info?.info;
  const cur = parsed.seasons.find((x) => x.n === season);
  const isFav = favs.some((f) => f.refId === sid);
  const plot = presentable(i?.plot) ?? presentable(s.plot);
  const playEp = (e: Ep, startAt?: number) => usePlayer.getState().open({
    kind: "episode", sourceId: source.id!, cid: source.cid, refId: e.id, title: `${s.title}`, subtitle: `${t("common.season", { n: e.season })} · ${t("common.episodeShort", { n: e.num })} ${e.title}`.trim(),
    image: s.poster, ext: e.ext, seriesId: sid, season: e.season, episode: e.num, startAt,
  }, "full");
  const allEps = parsed.seasons.flatMap((x) => x.eps);
  const resumeEp = last?.epId != null ? allEps.find((e) => e.id === last.epId) : undefined;
  const main = resumeEp ?? allEps[0];
  return (
    <div className="detail">
      <div className="backdrop" aria-hidden="true"><Img src={firstString(i?.backdrop_path) ?? s.backdrop} /></div>
      <div className="serie-layout" style={{ position: "relative" }}>
        <section style={{ display: "flex", flexDirection: "column", gap: "1.25rem" }}>
          <button className="back" style={{ position: "static" }} onClick={() => nav(-1)}><Icon name="back" size={20} stroke={2.5} />{t("nav.series")}</button>
          <div className="poster-big"><Img src={i?.cover || s.poster} /></div>
          <h1 style={{ fontSize: "2.25rem" }}>{s.title}</h1>
          <Facts items={[s.year, parsed.seasons.length ? t("common.seasons", { n: parsed.seasons.length }) : null, presentable(i?.genre), s.rating > 0 ? `★ ${s.rating.toFixed(1)}` : null]} />
          <p className="plot clamp-4" style={{ fontSize: "0.9375rem" }}>{plot ?? (info || error ? t("vod.noPlot") : t("vod.loadingInfo"))}</p>
          {error && <div className="muted">{t("vod.infoError")}</div>}
          <div className="actions">
            {main && (
              <button className="btn primary lg" onClick={() => playEp(main, resumeEp && last && last.pos > 20 ? last.pos : undefined)}>
                <Icon name="play" size={20} fill />
                {resumeEp && last ? t("vod.resumeEp", { s: resumeEp.season, e: resumeEp.num }) : t("vod.startEp", { s: main.season, e: main.num })}
              </button>
            )}
            <button className="btn icon lg" aria-label={t(isFav ? "common.removeFav" : "common.addFav")} aria-pressed={isFav} onClick={async () => {
              const on = await toggleFavorite({ profile: prefs.profileId, sourceId: source.id!, kind: "series", refId: sid, name: s.title, image: s.poster });
              useUi.getState().toast(t(on ? "toast.favAdded" : "toast.favRemoved"));
            }}><Icon name="heart" size={24} fill={isFav} /></button>
          </div>
          <Cast names={splitNames(i?.cast)} label={t("vod.cast")} />
        </section>
        <section aria-label={t("vod.episodes")} style={{ display: "flex", flexDirection: "column", gap: "1rem", minWidth: 0 }}>
          <div className="chips" role="tablist" aria-label={t("common.season", { n: "" })}>
            {parsed.seasons.map((x) => <button key={x.n} className="chip" role="tab" aria-selected={season === x.n} onClick={() => setSeason(x.n)}>{t("common.season", { n: x.n })}</button>)}
          </div>
          {!info && !error && <div className="skeleton" style={{ height: 120 }} />}
          {info && !cur && <div className="muted">{t("vod.noEpisodes")}</div>}
          {cur?.eps.map((e) => {
            const h = histBy.get(e.id);
            const ratio = h && h.dur ? h.pos / h.dur : 0;
            return (
              <button key={e.id} className="episode" onClick={() => playEp(e, h && h.pos > 20 ? h.pos : undefined)}>
                <span className="th"><Img src={e.img ?? s.poster} />{ratio > 0 && <span className="progress"><i style={{ width: `${ratio * 100}%` }} /></span>}</span>
                <span className="grow">
                  <b className="ellipsis" style={{ display: "block" }}>{t("common.episodeShort", { n: e.num })} · {e.title}</b>
                  <span className="m">{[e.secs ? fmtDuration(e.secs) : null, h ? (ratio > 0.95 ? t("common.watched") : t("common.minLeft", { t: fmtDuration(h.dur - h.pos) })) : null].filter(Boolean).join(" · ")}</span>
                  {e.plot && <span className="p clamp-2" style={{ display: "block" }}>{e.plot}</span>}
                </span>
              </button>
            );
          })}
        </section>
      </div>
    </div>
  );
}
