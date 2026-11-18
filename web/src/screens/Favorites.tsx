import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import { getOrder, resetOrder, saveOrder } from "@/db/channelOrder";
import type { ChannelRow, FavoriteRow, Kind, Source } from "@/db/types";
import { favoritesOf } from "@/db/queries";
import { useFavorites } from "@/hooks/data";
import { useNowNext } from "@/hooks/epg";
import { useReorderDnd } from "@/hooks/reorder";
import { useT } from "@/i18n";
import { concatBySource, dropHidden, hiddenKey, mixActive, canMixFavorites, type MixedChan } from "@/lib/favoritesMix";
import { visibleSources } from "@/lib/profileSources";
import { FAVORITES_SCOPE, commitMove, mergeOrder, startMove, stepMove, type MoveSession } from "@/lib/channelOrder";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { useActiveSource, useSources } from "@/state/sources";
import { useUi } from "@/state/ui";
import { buildM3u, countExportable, m3uFileName, type M3uEntry } from "@/lib/m3uExport";
import { Modal, QBadge, StateCard } from "@/ui/common";
import { Img } from "@/ui/Img";
import { PosterCard } from "@/ui/Poster";
import { NoSource } from "./states";

function Chan({ source, c, extra, extraCls = "", onMenu, moving, badge, onPlay }: { source: Source; c: ChannelRow; extra?: object; extraCls?: string; onMenu?: () => void; moving?: boolean; badge?: string; onPlay?: () => void }) {
  const nn = useNowNext(source, c.epg, c.streamId, false);
  return (
    <button className={`chan-row${extraCls}${moving ? " moving" : ""}`} data-chan={c.streamId} style={{ height: 62, background: "var(--surface)" }} onClick={onPlay ?? (() => usePlayer.getState().open(channelTarget(source, c), "full", FAVORITES_SCOPE))} onContextMenu={onMenu ? (e) => { e.preventDefault(); onMenu(); } : undefined} {...extra}>
      <span className="num mono">{c.num}</span>
      <span className="logo"><Img src={c.logo} contain /></span>
      <span className="grow" style={{ display: "flex", flexDirection: "column", gap: 2 }}><span className="nm ellipsis">{c.display}</span><span className="sub ellipsis">{[badge, nn.now?.title].filter(Boolean).join(" · ")}</span></span>
      <QBadge q={c.q} />
    </button>
  );
}

/** Puce « Toutes les sources » : affichée seulement avec 2 sources visibles ou plus, mémorisée par profil. */
function AllSourcesChip({ on }: { on: boolean }) {
  const t = useT();
  const profile = usePrefs((st) => st.profileId);
  return (
    <button className="chip" aria-pressed={on} onClick={() => { const st = usePrefs.getState(); st.set({ favAll: { ...st.favAll, [profile]: !on } }); }}>{t("fav.allSources")}</button>
  );
}

export function Favorites() {
  const source = useActiveSource();
  const list = useSources((s) => s.list);
  const names = usePrefs((s) => (s.profiles.find((p) => p.id === s.profileId) ?? s.profiles[0])?.sources);
  const chosen = usePrefs((s) => s.favAll[s.profileId]);
  if (!source) return <NoSource />;
  const visible = visibleSources(list, names);
  const can = canMixFavorites(visible.length);
  if (mixActive(chosen, visible.length)) return <Mixed sources={visible} />;
  return <Inner source={source} chip={can ? <AllSourcesChip on={false} /> : null} />;
}

/** Vue « Toutes les sources » : favoris de toutes les sources visibles du profil ; chaque élément porte sa source. */
function Mixed({ sources }: { sources: Source[] }) {
  const t = useT();
  const nav = useNavigate();
  const profile = usePrefs((st) => st.profileId);
  const [tab, setTab] = useState<Kind | "all">("all");
  const key = sources.map((s) => `${s.id}:${s.cid}`).join(",");
  // Seuls les favoris sont lus (et leurs chaînes, une par une) : jamais un catalogue entier.
  const favs = useLiveQuery(async () => (await Promise.all(sources.map((s) => favoritesOf(profile, s.id!)))).flat(), [profile, key]) ?? [];
  const chans = useLiveQuery(async () => {
    const hiddenRows = await db.profileHidden.where("sourceId").anyOf(sources.map((s) => s.id!)).toArray();
    const hidden = new Set<string>();
    for (const r of hiddenRows) if (r.profile === profile && r.kind === "live") for (const id of r.ids) hidden.add(hiddenKey(r.sourceId, id));
    const groups = await Promise.all(sources.map(async (s) => {
      const rows = (await Promise.all((await favoritesOf(profile, s.id!, "live")).map((f) => db.channels.where("[sourceId+streamId]").equals([s.cid, f.refId]).first()))).filter((c): c is ChannelRow => !!c);
      const ord = mergeOrder(rows, await getOrder(profile, s.id!, FAVORITES_SCOPE), (c) => c.streamId);
      return { sourceId: s.id!, items: ord.map((c): MixedChan => ({ sourceId: s.id!, cid: s.cid, name: s.name, c })) };
    }));
    return dropHidden(concatBySource(groups), hidden);
  }, [profile, key]) ?? [];
  const byId = new Map(sources.map((s) => [s.id!, s] as const));
  const nameOf = (id: number) => byId.get(id)?.name ?? "";
  const vod = (k: "movie" | "series"): FavoriteRow[] => favs.filter((f) => f.kind === k && byId.has(f.sourceId));
  const total = chans.length + vod("movie").length + vod("series").length;
  const count = (k: Kind) => (k === "live" ? chans.length : vod(k).length);
  const label = (k: Kind) => t(k === "live" ? "nav.live" : k === "movie" ? "nav.movies" : "nav.series");
  // La fiche d'un film / d'une série s'ouvre sur la source active : on la bascule sur celle de l'élément.
  const open = (f: FavoriteRow) => {
    if (usePrefs.getState().activeSourceId !== f.sourceId) usePrefs.getState().set({ activeSourceId: f.sourceId });
    nav(`/${f.kind === "movie" ? "movie" : "serie"}/${f.refId}`);
  };
  const chanGrid = (
    <div className="grid-x" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(22rem, 1fr))", gap: 8 }}>
      {chans.map((m) => <Chan key={`${m.sourceId}:${m.c.streamId}`} source={byId.get(m.sourceId)!} c={m.c} badge={m.name} onPlay={() => usePlayer.getState().open(channelTarget(byId.get(m.sourceId)!, m.c), "full", FAVORITES_SCOPE, chans)} />)}
    </div>
  );
  const posters = (k: "movie" | "series") => (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(9.5rem, 1fr))", gap: 20 }}>
      {vod(k).map((f) => <PosterCard key={f.key} title={f.name} meta={nameOf(f.sourceId)} image={f.image} fav onClick={() => open(f)} />)}
    </div>
  );
  return (
    <div className="page">
      <div className="page-head"><h1>{t("fav.title")}</h1></div>
      <div className="chips" role="tablist">
        <AllSourcesChip on />
        <button className="chip" role="tab" aria-selected={tab === "all"} onClick={() => setTab("all")}>{t("common.all")} <span className="n">{total}</span></button>
        {(["live", "movie", "series"] as const).map((k) => (
          <button key={k} className="chip" role="tab" aria-selected={tab === k} onClick={() => setTab(k)}>{label(k)} <span className="n">{count(k)}</span></button>
        ))}
      </div>
      {total === 0 ? (
        <StateCard icon="heart" title={t("fav.emptyTitle")} body={t("fav.emptyBody")} actions={<button className="btn primary" onClick={() => nav("/live")}>{t("fav.browse")}</button>} />
      ) : tab === "all" ? (
        <div style={{ display: "flex", flexDirection: "column", gap: "1.75rem" }}>
          {chans.length > 0 && <section><h2 className="section-title">{label("live")} <span className="muted">{chans.length}</span></h2>{chanGrid}</section>}
          {count("movie") > 0 && <section><h2 className="section-title">{label("movie")} <span className="muted">{count("movie")}</span></h2>{posters("movie")}</section>}
          {count("series") > 0 && <section><h2 className="section-title">{label("series")} <span className="muted">{count("series")}</span></h2>{posters("series")}</section>}
        </div>
      ) : count(tab) === 0 ? (
        <p className="muted">{t("fav.emptyTitle")}</p>
      ) : tab === "live" ? chanGrid : posters(tab)}
    </div>
  );
}

function Inner({ source, chip }: { source: Source; chip?: React.ReactNode }) {
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
  // Export M3U : avertissement (les liens contiennent les identifiants), puis téléchargement du fichier.
  const [exporting, setExporting] = useState(false);
  const exportM3u = async () => {
    setExporting(false);
    const toast = useUi.getState().toast;
    try {
      // Toutes les chaînes favorites de la source (pas seulement l'onglet affiché), dans l'ordre choisi par l'utilisateur.
      const rows = (await Promise.all(live.map((f) => db.channels.where("[sourceId+streamId]").equals([source.cid, f.refId]).first()))).filter((c): c is ChannelRow => !!c);
      const chans = mergeOrder(rows, order, (c) => c.streamId);
      const cats = await db.categories.where("[sourceId+kind]").equals([source.cid, "live"]).toArray();
      const group = new Map(cats.map((c) => [c.extId, c.name] as const));
      let urlOf: (c: ChannelRow) => string | undefined = (c) => c.url;
      if (source.type === "xtream") {
        const [{ liveUrl }, { credsOf }] = await Promise.all([import("@/net/xtream"), import("@/sync/core")]);
        const creds = credsOf(source);
        const ext = usePrefs.getState().liveFormat;
        urlOf = (c) => liveUrl(creds, c.streamId, ext);
      }
      const entries: M3uEntry[] = chans.map((c) => ({ name: c.name, url: urlOf(c) ?? "", tvgId: c.epg, logo: c.logo, group: group.get(c.catExt), num: c.num }));
      const count = countExportable(entries);
      if (count === 0) { toast(t("fav.exportNone")); return; }
      const href = URL.createObjectURL(new Blob([buildM3u(entries)], { type: "audio/x-mpegurl;charset=utf-8" }));
      const a = document.createElement("a");
      a.href = href; a.download = m3uFileName(new Date());
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(href), 10_000);
      toast(t("fav.exportDone", { n: count }));
    } catch { toast(t("fav.exportFailed")); }
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
      <div className="page-head">
        <h1>{t("fav.title")}</h1>
        {live.length > 0 && <button className="btn" onClick={() => setExporting(true)}>{t("fav.export")}</button>}
      </div>
      {exporting && (
        <Modal title={t("fav.exportTitle")} onClose={() => setExporting(false)} foot={<>
          <button className="btn" onClick={() => setExporting(false)}>{t("common.cancel")}</button>
          <button className="btn primary" onClick={() => void exportM3u()}>{t("fav.exportDownload")}</button>
        </>}>
          <p className="muted">{t("fav.exportWarn")}</p>
        </Modal>
      )}
      <div className="chips" role="tablist">
        {chip}
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
