import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import { searchByWords, searchFuzzy } from "@/db/queries";
import type { ChannelRow, MovieRow, ProgramRow, SeriesRow, Source } from "@/db/types";
import { useDebounced } from "@/hooks/misc";
import { useT, type Key } from "@/i18n";
import { normText } from "@/lib/text";
import { letterIndex, orderResults, relevance, SEARCH_SORTS, validRating, type SearchSort } from "@/lib/searchRank";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { QBadge, Seg, hhmm } from "@/ui/common";
import { Icon } from "@/ui/Icon";
import { Img } from "@/ui/Img";
import { PosterCard } from "@/ui/Poster";
import { dedupeByTitle } from "@/lib/posterFallback";
import { NoSource } from "./states";

export function Search() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <Inner source={source} />;
}

interface Results { q: string; channels: ChannelRow[]; movies: MovieRow[]; series: SeriesRow[]; programs: { p: ProgramRow; c: ChannelRow }[]; fuzzy: boolean }
type Tab = "all" | "movies" | "series" | "channels" | "programs";
const TABS: Tab[] = ["all", "movies", "series", "channels", "programs"];
const TAB_KEY: Record<Tab, Key> = { all: "search.tabAll", movies: "search.movies", series: "search.series", channels: "search.channels", programs: "search.programs" };
const SORT_KEY: Record<SearchSort, Key> = { relevance: "search.sortRelevance", title: "search.sortTitle", year: "search.sortYear", rating: "search.sortRating" };
/** Plafond par type : tout parcourir sans charger un catalogue entier pour une saisie très courte. */
const LIMIT = 400;
/** Éléments par type dans l'onglet « Tout » ; « Tout voir » ouvre l'onglet complet. */
const PREVIEW = 12;

/** Guide TV : programmes en cours ou à venir dont le titre correspond, une ligne par chaîne (le plus proche). */
async function searchPrograms(cid: number, nq: string): Promise<{ p: ProgramRow; c: ChannelRow }[]> {
  const now = Date.now();
  const hits = await db.programs.where("[sourceId+end]").between([cid, now], [cid, now + 7 * 864e5])
    .filter((p) => normText(p.title).includes(nq)).limit(400).toArray();
  hits.sort((a, b) => a.start - b.start);
  const out: { p: ProgramRow; c: ChannelRow }[] = [];
  const seen = new Set<string>();
  for (const p of hits) {
    if (seen.has(p.epg)) continue;
    seen.add(p.epg);
    const c = await db.channels.where("[sourceId+epg]").equals([cid, p.epg]).filter((x) => !x.sep).first();
    if (c) out.push({ p, c });
    if (out.length >= 60) break;
  }
  return out;
}

function Inner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const [q, setQ] = useState("");
  const dq = useDebounced(q, 250);
  const [res, setRes] = useState<Results | null>(null);
  const [busy, setBusy] = useState(false);
  const [tab, setTab] = useState<Tab>("all");
  const [sort, setSort] = useState<SearchSort>("relevance");
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { input.current?.focus(); }, []);
  useEffect(() => { setTab("all"); }, [dq]);

  useEffect(() => {
    const nq = normText(dq);
    if (nq.length < 2) { setRes(null); return; }
    // Réponse périmée (frappe suivante, changement de source) : ignorée grâce à `dead`, positionné par le cleanup.
    let dead = false;
    setBusy(true);
    void Promise.all([
      searchByWords(db.channels, source.cid, nq, LIMIT, (c) => !c.sep),
      searchByWords(db.movies, source.cid, nq, LIMIT),
      searchByWords(db.series, source.cid, nq, LIMIT),
      searchPrograms(source.cid, nq).catch(() => []),
    ]).then(async ([channels, movies, series, programs]) => {
      // Aucun film ni série : repli tolérant aux fautes de frappe (« brekin bad » → Breaking Bad).
      let fuzzy = false;
      if (!movies.length && !series.length && nq.trim().length >= 4) {
        [movies, series] = await Promise.all([searchFuzzy(db.movies, source.cid, nq, 30), searchFuzzy(db.series, source.cid, nq, 30)]);
        fuzzy = movies.length + series.length > 0;
      }
      if (!dead) { setRes({ q: dq, channels, movies: dedupeByTitle(movies), series: dedupeByTitle(series), programs, fuzzy }); setBusy(false); }
    }).catch(() => { if (!dead) setBusy(false); });
    return () => { dead = true; };
  }, [dq, source.cid]);

  // Tri : sur la saisie qui a produit les résultats. Repli tolérant : l'ordre de ressemblance est gardé.
  const view = useMemo(() => {
    if (!res) return null;
    if (res.fuzzy) return { ...res, best: null as MovieRow | SeriesRow | null };
    const movies = orderResults(res.movies, res.q, sort);
    const series = orderResults(res.series, res.q, sort);
    const channels = orderResults(res.channels.map((c) => ({ ...c, title: c.display, year: null })), res.q, sort === "title" ? "title" : "relevance");
    let best: MovieRow | SeriesRow | null = null;
    if (sort === "relevance") {
      const rm = movies[0] ? relevance(res.q, movies[0].title) : 0;
      const rs = series[0] ? relevance(res.q, series[0].title) : 0;
      best = rm >= 3 && rm >= rs ? movies[0]! : rs >= 3 ? series[0]! : null;
    }
    return { ...res, movies, series, channels, best };
  }, [res, sort]);

  const count = (x: Tab) => !view ? 0 : x === "movies" ? view.movies.length : x === "series" ? view.series.length
    : x === "channels" ? view.channels.length : x === "programs" ? view.programs.length
    : view.movies.length + view.series.length + view.channels.length + view.programs.length;
  const eff: Tab = tab !== "all" && count(tab) === 0 ? "all" : tab;
  const show = (x: Tab) => eff === "all" || eff === x;
  const lim = <T,>(xs: T[]) => (eff === "all" ? xs.slice(0, PREVIEW) : xs);
  const openMovie = (m: MovieRow) => nav(`/movie/${m.streamId}`);
  const openSeries = (s: SeriesRow) => nav(`/serie/${s.seriesId}`);
  const playChannel = (c: ChannelRow) => usePlayer.getState().open(channelTarget(source, c), "full", null);
  const lang = usePrefs((s) => s.lang);
  const meta = (kind: "movies" | "series", y: number | null, r: number) =>
    [t(kind === "movies" ? "nav.movies" : "nav.series"), y, validRating(r) != null ? `★ ${validRating(r)!.toLocaleString(lang, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}` : null].filter(Boolean).join(" · ");

  const Head = ({ x, n, label }: { x: Tab; n: number; label?: string }) => (
    <div style={{ display: "flex", alignItems: "baseline", gap: 12, marginBottom: 12 }}>
      <div className="eyebrow">{label ?? t(TAB_KEY[x])} · {n}</div>
      {eff === "all" && n > PREVIEW && <button type="button" className="btn sm" style={{ marginInlineStart: "auto" }} onClick={() => setTab(x)}>{t("search.seeAll", { n })} →</button>}
    </div>
  );
  // Rail A–Z (onglet d'un type, tri alphabétique) : saut à la première œuvre de la lettre.
  const letters = view && sort === "title" && (eff === "movies" || eff === "series") ? letterIndex<{ title: string }>(eff === "movies" ? view.movies : view.series) : null;
  const total = count("all");
  const suggestion = view?.fuzzy ? (view.movies[0]?.title ?? view.series[0]?.title ?? null) : null;

  return (
    <div className="page">
      <div className="page-head"><h1>{t("search.title")}</h1></div>
      <div className="search-box" style={{ maxWidth: "40rem" }}>
        <Icon name="search" size={20} />
        <input ref={input} className="input" style={{ height: "3.25rem", fontSize: "1.125rem", paddingInlineStart: "3rem", borderRadius: "1rem", border: "2px solid var(--accent)" }}
          value={q} onChange={(e) => setQ(e.target.value)} placeholder={t("search.placeholder")} aria-label={t("search.title")} />
      </div>
      {!view && <div className="muted">{busy ? t("search.searching") : t("search.hint")}</div>}
      {view && total === 0 && <div className="muted">{t("search.empty", { q: dq })}</div>}
      {view && total > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 10, alignItems: "center" }}>
          <div className="chips" role="tablist" aria-label={t("search.title")} style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
            {TABS.filter((x) => x === "all" || count(x) > 0).map((x) => (
              <button key={x} type="button" className="chip" role="tab" aria-selected={eff === x} onClick={() => setTab(x)}>{t(TAB_KEY[x])} <span style={{ opacity: 0.7 }}>{count(x)}</span></button>
            ))}
          </div>
          {!view.fuzzy && eff !== "programs" && (
            <div style={{ marginInlineStart: "auto" }}>
              <Seg label={t("search.sortBy")} value={sort} onChange={setSort}
                options={(eff === "channels" ? (["relevance", "title"] as SearchSort[]) : SEARCH_SORTS).map((s) => ({ v: s, label: t(SORT_KEY[s]) }))} />
            </div>
          )}
        </div>
      )}
      {view?.fuzzy && suggestion && (
        <section>
          <div className="muted" style={{ marginBottom: 10 }}>{t("search.noExact", { q: view.q })}</div>
          <div style={{ display: "flex", alignItems: "center", gap: 12, fontSize: "1.5rem", fontWeight: 700 }}>
            {t("search.didYouMean")}
            <button type="button" className="btn" onClick={() => setQ(suggestion.toLowerCase())}>{suggestion}</button>
            ?
          </div>
        </section>
      )}
      {view?.best && eff === "all" && (
        <section style={{ display: "flex", gap: 24, alignItems: "center", background: "var(--surface)", borderRadius: "1.25rem", padding: "1rem" }}>
          <div style={{ width: "8rem", flexShrink: 0, aspectRatio: "2 / 3", borderRadius: "0.75rem", overflow: "hidden", position: "relative", background: "var(--surface-2)", display: "grid", placeItems: "center", textAlign: "center", padding: 8, boxSizing: "border-box" }}>
            <span className="muted" style={{ fontSize: "0.8125rem", fontWeight: 600 }}>{view.best.title}</span>
            <div style={{ position: "absolute", inset: 0 }}><Img src={view.best.poster} alt={view.best.title} /></div>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 0 }}>
            <div className="eyebrow" style={{ color: "var(--accent-text)" }}>{t("search.best")}</div>
            <div style={{ fontSize: "1.75rem", fontWeight: 700 }} className="ellipsis">{view.best.title}</div>
            <div className="muted">{meta("streamId" in view.best ? "movies" : "series", view.best.year, view.best.rating)}</div>
            <div><button type="button" className="btn" onClick={() => ("streamId" in view.best! ? openMovie(view.best as MovieRow) : openSeries(view.best as SeriesRow))}>{t("search.details")}</button></div>
          </div>
        </section>
      )}
      {letters && (
        <nav aria-label={t("search.sortTitle")} style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
          {[...letters.entries()].map(([c, i]) => (
            <button key={c} type="button" className="chip" onClick={() => document.getElementById(`sr-${eff}-${i}`)?.scrollIntoView({ block: "center" })}>{c}</button>
          ))}
        </nav>
      )}
      {view && view.movies.length > 0 && show("movies") && (
        <section>
          <Head x="movies" n={view.movies.length} label={view.fuzzy ? t("search.closeTitles") : undefined} />
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(9.5rem, 1fr))", gap: 20 }}>
            {lim(view.movies).map((m, i) => <div key={`m${m.id}`} id={`sr-movies-${i}`}><PosterCard kind="movie" year={m.year} title={m.title} image={m.poster} meta={meta("movies", m.year, m.rating)} onClick={() => openMovie(m)} /></div>)}
          </div>
        </section>
      )}
      {view && view.series.length > 0 && show("series") && (
        <section>
          <Head x="series" n={view.series.length} label={view.fuzzy ? t("search.closeTitles") : undefined} />
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(9.5rem, 1fr))", gap: 20 }}>
            {lim(view.series).map((s, i) => <div key={`s${s.id}`} id={`sr-series-${i}`}><PosterCard kind="tv" year={s.year} title={s.title} image={s.poster} meta={meta("series", s.year, s.rating)} onClick={() => openSeries(s)} /></div>)}
          </div>
        </section>
      )}
      {view && view.channels.length > 0 && show("channels") && (
        <section>
          <Head x="channels" n={view.channels.length} />
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(18rem, 1fr))", gap: 12 }}>
            {lim(view.channels).map((c) => (
              <button key={c.id} className="chan-row" style={{ height: 72, background: "var(--surface)", borderRadius: "1.125rem", padding: "0 1.25rem" }} onClick={() => playChannel(c)}>
                <span className="logo" style={{ width: "3.5rem", height: "2.5rem" }}><Img src={c.logo} contain /></span>
                <span className="grow"><span className="nm ellipsis" style={{ display: "block" }}>{c.display}</span><span className="sub">{c.num}</span></span>
                <QBadge q={c.q} />
              </button>
            ))}
          </div>
        </section>
      )}
      {view && view.programs.length > 0 && show("programs") && (
        <section>
          <Head x="programs" n={view.programs.length} />
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(20rem, 1fr))", gap: 12 }}>
            {lim(view.programs).map(({ p, c }) => {
              const live = p.start <= Date.now();
              const at = `${new Date(p.start).toLocaleDateString(undefined, { weekday: "short" })} ${hhmm(p.start)}`;
              return (
                <button key={`p${c.id}`} className="chan-row" style={{ height: 80, background: "var(--surface)", borderRadius: "1.125rem", padding: "0 1.25rem" }} onClick={() => playChannel(c)}>
                  <span className="logo" style={{ width: "3.5rem", height: "2.5rem" }}><Img src={c.logo} contain /></span>
                  <span className="grow" style={{ minWidth: 0 }}>
                    <span className="nm ellipsis" style={{ display: "block" }}>{p.title}</span>
                    <span className="sub ellipsis" style={{ display: "block" }}>{live ? t("search.onAir") : at} · {c.num} {c.display}</span>
                  </span>
                  {live && <span className="badge-live">{t("search.liveBadge")}</span>}
                </button>
              );
            })}
          </div>
        </section>
      )}
    </div>
  );
}
