import { useLiveQuery } from "dexie-react-hooks";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import { channelsCol, toggleFavorite } from "@/db/queries";
import type { ChannelRow, Source } from "@/db/types";
import { useCategories, useFavorites } from "@/hooks/data";
import { useNowNext } from "@/hooks/epg";
import { useElementSize, useDebounced } from "@/hooks/misc";
import { usePagedQuery } from "@/hooks/paged";
import { useT } from "@/i18n";
import { normText } from "@/lib/text";
import { usePlayer } from "@/player/store";
import { PreviewSlot } from "@/player/PreviewSlot";
import { channelTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { useUi } from "@/state/ui";
import { CategoryList } from "@/ui/CategoryList";
import { hhmm, QBadge, StateCard } from "@/ui/common";
import { HScroll } from "@/ui/HScroll";
import { Icon } from "@/ui/Icon";
import { Img } from "@/ui/Img";
import { VList, arrayRows, type Rows, type VListHandle } from "@/ui/Virtual";
import { NoSource } from "./states";

const FAV = "__fav";

export function Live() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <LiveInner source={source} />;
}

function ChanRow({ c, source, sel, playing, onPick }: { c: ChannelRow; source: Source; sel: boolean; playing: boolean; onPick: () => void }) {
  const nn = useNowNext(source, c.epg, c.streamId, false);
  if (c.sep) return <div className="sep-row" role="presentation"><span className="bar" /><span className="ellipsis">{c.display}</span><span className="rule" /></div>;
  return (
    <button className={`chan-row${sel ? " sel" : ""}${playing ? " playing" : ""}`} onClick={onPick} onDoubleClick={() => usePlayer.getState().setMode("full")} role="option" aria-selected={sel}>
      <span className="num mono">{c.num}</span>
      <span className="logo"><Img src={c.logo} contain /></span>
      <span className="grow" style={{ display: "flex", flexDirection: "column", gap: 2 }}>
        <span className="nm ellipsis">{c.display}</span>
        <span className="sub ellipsis">{nn.now?.title ?? ""}</span>
      </span>
      <span className="tags">{c.country && <span className="cbadge">{c.country}</span>}<QBadge q={c.q} /></span>
    </button>
  );
}

function LiveInner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const prefs = usePrefs();
  const cats = useCategories(source, "live");
  const favs = useFavorites(source, "live");
  const [root, size] = useElementSize<HTMLDivElement>();
  const wide = size.w >= 1400;
  const cat = prefs.liveCat[String(source.id)] ?? "";
  const setCat = (c: string) => prefs.set({ liveCat: { ...prefs.liveCat, [String(source.id)]: c } });
  const [filter, setFilter] = useState("");
  const dq = useDebounced(filter, 200);
  const [pop, setPop] = useState(false);
  const [selId, setSelId] = useState<number | null>(null);
  const list = useRef<VListHandle>(null);
  const playing = usePlayer((s) => (s.target?.kind === "live" ? s.target.refId : null));

  // Mode tableau (favoris, filtre texte) ou mode paginé (catalogue entier / catégorie).
  const arrayMode = cat === FAV || dq.trim().length > 0;
  const arr = useLiveQuery(async () => {
    if (!arrayMode) return [] as ChannelRow[];
    if (cat === FAV) {
      const rows = await Promise.all(favs.map((f) => db.channels.where("[sourceId+streamId]").equals([source.cid, f.refId]).first()));
      const nq = normText(dq);
      return rows.filter((r): r is ChannelRow => !!r && (!nq || r.norm.includes(nq)));
    }
    const nq = normText(dq);
    return channelsCol(source.cid, cat || null).filter((c) => !c.sep && c.norm.includes(nq)).limit(3000).toArray();
  }, [arrayMode, cat, dq, source.cid, favs]);
  const paged = usePagedQuery<ChannelRow>(() => (arrayMode ? null : channelsCol(source.cid, cat || null)), [source.cid, cat, arrayMode], 120);
  const rows: Rows<ChannelRow> = useMemo(() => (arrayMode ? arrayRows(arr ?? []) : paged), [arrayMode, arr, paged]);
  const count = rows.count;

  const selected = useLiveQuery(() => (selId != null ? db.channels.get(selId) : undefined), [selId]);
  const nn = useNowNext(source, selected?.epg, selected?.streamId);
  const isFav = !!selected && favs.some((f) => f.refId === selected.streamId);

  const play = useCallback((c: ChannelRow, full = false) => {
    if (c.sep) return;
    setSelId(c.id!);
    usePlayer.getState().open(channelTarget(source, c), full ? "full" : "inline", cat === FAV ? null : cat || null);
  }, [source, cat]);
  const pick = (c: ChannelRow) => {
    if (c.sep) return;
    if (selId === c.id && playing === c.streamId) { usePlayer.getState().setMode("full"); return; }
    if (prefs.autoPreview) play(c); else setSelId(c.id!);
  };

  // Sélection initiale : première vraie chaîne.
  useEffect(() => {
    if (selId != null || !count) return;
    let i = 0;
    const find = () => {
      for (; i < Math.min(count, 200); i++) { const r = rows.get(i); if (r === undefined) { rows.want(i, i + 20); return false; } if (!r.sep) { setSelId(r.id!); return true; } }
      return true;
    };
    if (!find()) { const iv = setInterval(() => { if (find()) clearInterval(iv); }, 150); return () => clearInterval(iv); }
  }, [count, rows, selId]);
  useEffect(() => { setSelId(null); }, [cat, arrayMode]);

  const move = (dir: number) => {
    if (!count) return;
    const curIdx = (() => { for (let i = 0; i < count; i++) if (rows.get(i)?.id === selId) return i; return -1; })();
    let i = curIdx < 0 ? (dir > 0 ? -1 : count) : curIdx;
    for (let n = 0; n < 400; n++) {
      i += dir;
      if (i < 0 || i >= count) return;
      const r = rows.get(i);
      if (r === undefined) { rows.want(i, i + 40); return; }
      if (!r.sep) { setSelId(r.id!); list.current?.scrollTo(i); return; }
    }
  };
  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") { e.preventDefault(); move(1); }
    else if (e.key === "ArrowUp") { e.preventDefault(); move(-1); }
    else if (e.key === "PageDown") { e.preventDefault(); for (let i = 0; i < 8; i++) move(1); }
    else if (e.key === "PageUp") { e.preventDefault(); for (let i = 0; i < 8; i++) move(-1); }
    else if (e.key === "Enter" && selected) { e.preventDefault(); if (playing === selected.streamId) usePlayer.getState().setMode("full"); else play(selected); }
  };

  const catLabel = cat === FAV ? t("common.favorites") : cat === "" ? t("common.all") : cats?.find((c) => c.extId === cat)?.label ?? "";
  const extra = useMemo(() => [{ id: FAV, label: t("common.favorites"), n: favs.length }, { id: "", label: t("common.all"), n: source.counts.live }], [favs.length, source.counts.live, t]);
  const pickCat = (c: string) => { setCat(c); setPop(false); };
  const sections = useLiveQuery(async () => (arrayMode ? 0 : await channelsCol(source.cid, cat || null).filter((c) => !!c.sep).count()), [source.cid, cat, arrayMode]) ?? 0;

  if (source.state === "syncing" && !source.cid) return <StateCard icon="refresh" title={t("sync.title")} body={t("sync.lead")} />;

  return (
    <div ref={root} className={`split${wide ? " wide" : ""}`}>
      {wide && (
        <section className="col" aria-label={t("common.categories")} style={{ paddingTop: "1.5rem" }}>
          <div className="col-head" style={{ paddingTop: 0 }}><h1 style={{ fontSize: "1.25rem" }}>{t("nav.live")}</h1></div>
          <CategoryList cats={cats ?? []} value={cat} onPick={pickCat} extra={extra} />
        </section>
      )}
      <section className="col" aria-label={t("nav.live")}>
        <div className="col-head">
          <div className="top">
            {!wide && <h1>{t("nav.live")}</h1>}
            {wide && <h1 className="ellipsis" style={{ fontSize: "1.5rem" }}>{catLabel}</h1>}
          </div>
          {!wide && (
            <HScroll wheel follow={cat} className="chips scroll" role="tablist" aria-label={t("common.categories")}>
              <button className="chip" role="tab" aria-selected={cat === FAV} onClick={() => pickCat(FAV)}>{t("common.favorites")}</button>
              <button className="chip" role="tab" aria-selected={cat === ""} onClick={() => pickCat("")}>{t("common.all")}</button>
              {cat !== "" && cat !== FAV && <button className="chip" role="tab" aria-selected>{catLabel}</button>}
              <button className="chip" onClick={() => setPop(true)}>{t("common.categories")} ▾</button>
            </HScroll>
          )}
          <div className="search-box">
            <Icon name="search" size={16} />
            <input className="input" placeholder={t("live.filterChannels")} value={filter} onChange={(e) => setFilter(e.target.value)} aria-label={t("live.filterChannels")} />
          </div>
          <div className="muted" style={{ fontSize: "0.8125rem", fontWeight: 600 }}>
            {count == null ? t("common.loading") : `${catLabel || t("common.all")} · ${t("common.channelsCount", { n: count - sections })}${sections ? " · " + t("common.sections", { n: sections }) : ""}`}
          </div>
        </div>
        {count === 0 ? <div className="state-card"><p>{t("live.noChannels")}</p></div> : (
          <VList
            ref={list} rows={rows} rowH={66} label={t("nav.live")} onKeyDown={onKey}
            render={(c) => c ? <ChanRow c={c} source={source} sel={c.id === selId} playing={c.streamId === playing} onPick={() => pick(c)} /> : <div className="skeleton" style={{ height: 58 }} />}
          />
        )}
      </section>
      <section className="preview" aria-label={t("a11y.preview")}>
        <PreviewSlot>{t("live.preview")}</PreviewSlot>
        {selected && (
          <>
            <div style={{ display: "flex", gap: 10, alignItems: "center", fontSize: "0.75rem", fontWeight: 800, letterSpacing: "0.1em" }}>
              <span className="badge-live">{t("common.live")}</span>
              <span style={{ color: "var(--text-2)", letterSpacing: 0 }}>{nn.now ? `${hhmm(nn.now.start)} – ${hhmm(nn.now.end)}` : `${selected.num} · ${selected.display}`}</span>
            </div>
            <h2>{nn.now?.title || selected.display}</h2>
            {nn.now && (
              <div className="progress" style={{ height: 8 }}><i style={{ width: `${Math.min(100, Math.max(0, ((Date.now() - nn.now.start) / (nn.now.end - nn.now.start)) * 100))}%` }} /></div>
            )}
            {nn.now?.desc ? <p className="desc clamp-4">{nn.now.desc}</p> : !nn.now && <p className="desc muted">{t("common.noProgram")}</p>}
            {nn.next && (
              <div className="next">
                <div className="eyebrow">{t("common.upNext")}</div>
                <div className="r"><span className="ellipsis">{hhmm(nn.next.start)} · {nn.next.title}</span><span className="muted">{Math.round((nn.next.end - nn.next.start) / 60000)} min</span></div>
              </div>
            )}
            <div className="actions">
              <button className="btn primary" onClick={() => play(selected, true)}><Icon name="play" size={18} fill />{t("common.watch")}</button>
              <button className="btn" onClick={async () => {
                const on = await toggleFavorite({ profile: prefs.profileId, sourceId: source.id!, kind: "live", refId: selected.streamId, name: selected.display, image: selected.logo });
                useUi.getState().toast(t(on ? "toast.favAdded" : "toast.favRemoved"));
              }} aria-pressed={isFav}><Icon name="heart" size={18} fill={isFav} />{t(isFav ? "common.removeFav" : "common.addFav")}</button>
            </div>
            <div className="muted" style={{ fontSize: "0.8125rem" }}>{t("live.previewHint")}</div>
          </>
        )}
      </section>
      {pop && (
        <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) setPop(false); }}>
          <div className="modal" style={{ height: "min(36rem, 80vh)", padding: "1.25rem 0.5rem 1rem" }} role="dialog" aria-label={t("common.categories")}>
            <h3 style={{ padding: "0 1rem" }}>{t("common.categories")}</h3>
            <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
              <CategoryList cats={cats ?? []} value={cat} onPick={pickCat} extra={extra} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
