import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import { getOrder, resetOrder, saveOrder } from "@/db/channelOrder";
import type { ChannelRow, Kind, Source } from "@/db/types";
import { useFavorites } from "@/hooks/data";
import { useNowNext } from "@/hooks/epg";
import { useReorderDnd } from "@/hooks/reorder";
import { useT } from "@/i18n";
import { FAVORITES_SCOPE, commitMove, mergeOrder, startMove, stepMove, type MoveSession } from "@/lib/channelOrder";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { useUi } from "@/state/ui";
import { QBadge, StateCard } from "@/ui/common";
import { Img } from "@/ui/Img";
import { PosterCard } from "@/ui/Poster";
import { NoSource } from "./states";

function Chan({ source, c, extra, extraCls = "", onMenu, moving }: { source: Source; c: ChannelRow; extra?: object; extraCls?: string; onMenu?: () => void; moving?: boolean }) {
  const nn = useNowNext(source, c.epg, c.streamId, false);
  return (
    <button className={`chan-row${extraCls}${moving ? " moving" : ""}`} data-chan={c.streamId} style={{ height: 62, background: "var(--surface)" }} onClick={() => usePlayer.getState().open(channelTarget(source, c), "full", FAVORITES_SCOPE)} onContextMenu={onMenu ? (e) => { e.preventDefault(); onMenu(); } : undefined} {...extra}>
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
  // « Tout » par défaut : tous les favoris classés par section (chaînes, films, séries).
  const [tab, setTab] = useState<Kind | "all">("all");
  const all = useFavorites(source);
  const live = all.filter((f) => f.kind === "live");
  const profile = usePrefs((st) => st.profileId);
  const sid = source.id!;
  const natural = useLiveQuery(async () => (tab === "live" || tab === "all" ? (await Promise.all(live.map((f) => db.channels.where("[sourceId+streamId]").equals([source.cid, f.refId]).first()))).filter((c): c is ChannelRow => !!c) : []), [tab, live.length, source.cid]);
  const order = useLiveQuery(() => getOrder(profile, sid, FAVORITES_SCOPE), [profile, sid]);
  // Déplacer (même ordre que la liste « Favoris » du Direct) : flèches, Entrée valide, Échap annule ; glisser-déposer à la souris.
  const [mv, setMv] = useState<MoveSession | null>(null);
  const ordered = useMemo(() => mergeOrder(natural ?? [], order, (c) => c.streamId), [natural, order]);
  const chans = useMemo(() => {
    if (!mv) return ordered;
    const by = new Map(ordered.map((c) => [c.streamId, c] as const));
    return mv.ids.map((i) => by.get(i)).filter((c): c is ChannelRow => !!c);
  }, [mv, ordered]);
  const idsOf = () => ordered.map((c) => c.streamId);
  const beginMove = (id: number) => { if (!mv && ordered.some((c) => c.streamId === id)) setMv(startMove(FAVORITES_SCOPE, id, idsOf(), order ?? null)); };
  const endMove = async (ok: boolean) => {
    if (!mv) return;
    const m = mv; setMv(null);
    const out = ok ? commitMove(m, (natural ?? []).map((c) => c.streamId)) : null;
    if (out) await saveOrder(profile, sid, FAVORITES_SCOPE, out);
  };
  useEffect(() => { if (mv) document.querySelector<HTMLElement>(`[data-chan="${mv.id}"]`)?.focus(); }, [mv]);
  const dnd = useReorderDnd({ enabled: !mv, axis: "x", getIds: idsOf, onReorder: (ids) => void saveOrder(profile, sid, FAVORITES_SCOPE, ids) });
  const onGridKey = (e: React.KeyboardEvent) => {
    if (mv) {
      const rtl = document.dir === "rtl";
      const step: Record<string, number> = { ArrowUp: -1, ArrowLeft: rtl ? 1 : -1, ArrowDown: 1, ArrowRight: rtl ? -1 : 1, Home: -mv.ids.length, End: mv.ids.length };
      if (e.key in step) setMv(stepMove(mv, step[e.key]!));
      else if (e.key === "Enter") void endMove(true);
      else if (e.key === "Escape") void endMove(false);
      else return;
      e.preventDefault(); e.stopPropagation();
    } else if ((e.key === "m" || e.key === "M") && !e.ctrlKey && !e.metaKey && !e.altKey) {
      const id = Number((e.target as HTMLElement).closest<HTMLElement>("[data-chan]")?.dataset.chan);
      if (Number.isFinite(id)) { e.preventDefault(); e.stopPropagation(); beginMove(id); }
    }
  };
  const n = (k: Kind) => all.filter((f) => f.kind === k).length;
  const label = (k: Kind) => t(k === "live" ? "nav.live" : k === "movie" ? "nav.movies" : "nav.series");
  const chanGrid = (
    <>
      {mv ? (
        <div className="move-bar" role="group" aria-label={t("cor.title")}>
          <span className="ellipsis">{t("cor.hint")}</span>
          <button className="btn primary" onClick={() => void endMove(true)}>{t("cor.done")}</button>
          <button className="btn" onClick={() => void endMove(false)}>{t("cor.cancel")}</button>
        </div>
      ) : (
        <div className="muted" style={{ fontSize: "0.8125rem", display: "flex", gap: 12, alignItems: "center", marginBottom: 8 }}>
          <span>{t("cor.favHint")}</span>
          {order && <button className="btn" onClick={async () => { await resetOrder(profile, sid, FAVORITES_SCOPE); useUi.getState().toast(t("cor.resetDone")); }}>{t("cor.reset")}</button>}
        </div>
      )}
      <div className="grid-x" onKeyDown={onGridKey} style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(22rem, 1fr))", gap: 8 }}>
        {chans.map((c) => <Chan key={c.id} source={source} c={c} extra={dnd.props(c.streamId)} extraCls={dnd.cls(c.streamId)} moving={mv?.id === c.streamId} onMenu={mv ? undefined : () => beginMove(c.streamId)} />)}
      </div>
    </>
  );
  const posters = (k: "movie" | "series") => (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(9.5rem, 1fr))", gap: 20 }}>
      {all.filter((f) => f.kind === k).map((f) => <PosterCard key={f.key} title={f.name} image={f.image} fav onClick={() => nav(`/${k === "movie" ? "movie" : "serie"}/${f.refId}`)} />)}
    </div>
  );
  return (
    <div className="page">
      <div className="page-head"><h1>{t("fav.title")}</h1></div>
      <div className="chips" role="tablist">
        <button className="chip" role="tab" aria-selected={tab === "all"} onClick={() => setTab("all")}>{t("common.all")} <span className="n">{all.length}</span></button>
        {(["live", "movie", "series"] as const).map((k) => (
          <button key={k} className="chip" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}>
            {label(k)} <span className="n">{n(k)}</span>
          </button>
        ))}
      </div>
      {all.length === 0 ? (
        <StateCard icon="heart" title={t("fav.emptyTitle")} body={t("fav.emptyBody")} actions={<button className="btn primary" onClick={() => nav("/live")}>{t("fav.browse")}</button>} />
      ) : tab === "all" ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "1.75rem" }}>
          {live.length > 0 && <section><h2 className="section-title">{label("live")} <span className="muted">{live.length}</span></h2>{chanGrid}</section>}
          {n("movie") > 0 && <section><h2 className="section-title">{label("movie")} <span className="muted">{n("movie")}</span></h2>{posters("movie")}</section>}
          {n("series") > 0 && <section><h2 className="section-title">{label("series")} <span className="muted">{n("series")}</span></h2>{posters("series")}</section>}
        </div>
      ) : n(tab) === 0 ? (
        <p className="muted">{t("fav.emptyTitle")}</p>
      ) : tab === "live" ? chanGrid : posters(tab)}
    </div>
  );
}
