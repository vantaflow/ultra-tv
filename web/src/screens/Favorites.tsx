import { useLiveQuery } from "dexie-react-hooks";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import type { ChannelRow, Kind, Source } from "@/db/types";
import { useFavorites } from "@/hooks/data";
import { useNowNext } from "@/hooks/epg";
import { useT } from "@/i18n";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { useActiveSource } from "@/state/sources";
import { QBadge, StateCard } from "@/ui/common";
import { Img } from "@/ui/Img";
import { PosterCard } from "@/ui/Poster";
import { NoSource } from "./states";

function Chan({ source, c }: { source: Source; c: ChannelRow }) {
  const nn = useNowNext(source, c.epg, c.streamId, false);
  return (
    <button className="chan-row" style={{ height: 62, background: "var(--surface)" }} onClick={() => usePlayer.getState().open(channelTarget(source, c), "full", null)}>
      <span className="num mono">{c.num}</span>
      <span className="logo"><Img src={c.logo} contain /></span>
      <span className="grow" style={{ display: "flex", flexDirection: "column", gap: 2 }}><span className="nm ellipsis">{c.display}</span><span className="sub ellipsis">{nn.now?.title ?? ""}</span></span>
      <QBadge q={c.q} />
    </button>
  );
}

export function Favorites() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <Inner source={source} />;
}

function Inner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const [tab, setTab] = useState<Kind>("live");
  const all = useFavorites(source);
  const favs = all.filter((f) => f.kind === tab);
  const chans = useLiveQuery(async () => (tab === "live" ? (await Promise.all(favs.map((f) => db.channels.where("[sourceId+streamId]").equals([source.cid, f.refId]).first()))).filter((c): c is ChannelRow => !!c) : []), [tab, favs.length, source.cid]) ?? [];
  const n = (k: Kind) => all.filter((f) => f.kind === k).length;
  return (
    <div className="page">
      <div className="page-head"><h1>{t("fav.title")}</h1></div>
      <div className="chips" role="tablist">
        {(["live", "movie", "series"] as const).map((k) => (
          <button key={k} className="chip" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}>
            {t(k === "live" ? "nav.live" : k === "movie" ? "nav.movies" : "nav.series")} <span className="n">{n(k)}</span>
          </button>
        ))}
      </div>
      {all.length === 0 ? (
        <StateCard icon="heart" title={t("fav.emptyTitle")} body={t("fav.emptyBody")} actions={<button className="btn primary" onClick={() => nav("/live")}>{t("fav.browse")}</button>} />
      ) : tab === "live" ? (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(22rem, 1fr))", gap: 8 }}>{chans.map((c) => <Chan key={c.id} source={source} c={c} />)}</div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(9.5rem, 1fr))", gap: 20 }}>
          {favs.map((f) => <PosterCard key={f.key} title={f.name} image={f.image} fav onClick={() => nav(`/${tab === "movie" ? "movie" : "serie"}/${f.refId}`)} />)}
        </div>
      )}
    </div>
  );
}
