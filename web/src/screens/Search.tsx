import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import type { ChannelRow, MovieRow, SeriesRow, Source } from "@/db/types";
import { useDebounced } from "@/hooks/misc";
import { useT } from "@/i18n";
import { normText } from "@/lib/text";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { useActiveSource } from "@/state/sources";
import { QBadge } from "@/ui/common";
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

interface Results { channels: ChannelRow[]; movies: MovieRow[]; series: SeriesRow[] }

function Inner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const [q, setQ] = useState("");
  const dq = useDebounced(q, 250);
  const [res, setRes] = useState<Results | null>(null);
  const [busy, setBusy] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { input.current?.focus(); }, []);

  useEffect(() => {
    const nq = normText(dq);
    if (nq.length < 2) { setRes(null); return; }
    let dead = false;
    setBusy(true);
    const R = [[source.cid, -1], [source.cid, Infinity]] as const;
    void Promise.all([
      db.channels.where("[sourceId+ord]").between(...R).filter((c) => !c.sep && c.norm.includes(nq)).limit(24).toArray(),
      db.movies.where("[sourceId+ord]").between(...R).filter((c) => c.norm.includes(nq)).limit(30).toArray(),
      db.series.where("[sourceId+ord]").between(...R).filter((c) => c.norm.includes(nq)).limit(30).toArray(),
    ]).then(([channels, movies, series]) => { if (!dead) { setRes({ channels, movies, series }); setBusy(false); } });
    return () => { dead = true; };
  }, [dq, source.cid]);

  const vodCount = (res?.movies.length ?? 0) + (res?.series.length ?? 0);
  return (
    <div className="page">
      <div className="page-head"><h1>{t("search.title")}</h1></div>
      <div className="search-box" style={{ maxWidth: "40rem" }}>
        <Icon name="search" size={20} />
        <input ref={input} className="input" style={{ height: "3.25rem", fontSize: "1.125rem", paddingInlineStart: "3rem", borderRadius: "1rem", border: "2px solid var(--accent)" }}
          value={q} onChange={(e) => setQ(e.target.value)} placeholder={t("search.placeholder")} aria-label={t("search.title")} />
      </div>
      {!res && <div className="muted">{busy ? t("search.searching") : t("search.hint")}</div>}
      {res && !res.channels.length && !vodCount && <div className="muted">{t("search.empty", { q: dq })}</div>}
      {res && res.channels.length > 0 && (
        <section>
          <div className="eyebrow" style={{ marginBottom: 12 }}>{t("search.channels")} · {res.channels.length}</div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(18rem, 1fr))", gap: 12 }}>
            {res.channels.map((c) => (
              <button key={c.id} className="chan-row" style={{ height: 72, background: "var(--surface)", borderRadius: "1.125rem", padding: "0 1.25rem" }} onClick={() => usePlayer.getState().open(channelTarget(source, c), "full", null)}>
                <span className="logo" style={{ width: "3.5rem", height: "2.5rem" }}><Img src={c.logo} contain /></span>
                <span className="grow"><span className="nm ellipsis" style={{ display: "block" }}>{c.display}</span><span className="sub">{c.num}</span></span>
                <QBadge q={c.q} />
              </button>
            ))}
          </div>
        </section>
      )}
      {res && vodCount > 0 && (
        <section>
          <div className="eyebrow" style={{ marginBottom: 12 }}>{t("search.vod")} · {vodCount}</div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(9.5rem, 1fr))", gap: 20 }}>
            {dedupeByTitle(res.movies).map((m) => <PosterCard key={`m${m.id}`} kind="movie" year={m.year} title={m.title} image={m.poster} meta={`${t("nav.movies")}${m.year ? " · " + m.year : ""}`} onClick={() => nav(`/movie/${m.streamId}`)} />)}
            {dedupeByTitle(res.series).map((m) => <PosterCard key={`s${m.id}`} kind="tv" year={m.year} title={m.title} image={m.poster} meta={`${t("nav.series")}${m.year ? " · " + m.year : ""}`} onClick={() => nav(`/serie/${m.seriesId}`)} />)}
          </div>
        </section>
      )}
    </div>
  );
}
