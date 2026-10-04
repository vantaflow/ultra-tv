import { useLiveQuery } from "dexie-react-hooks";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { moviesCol, seriesCol, type VodSort } from "@/db/queries";
import type { MovieRow, SeriesRow, Source } from "@/db/types";
import { useCategories, useFavorites } from "@/hooks/data";
import { useDebounced } from "@/hooks/misc";
import { usePagedQuery } from "@/hooks/paged";
import { useT } from "@/i18n";
import { normText } from "@/lib/text";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { CategoryList } from "@/ui/CategoryList";
import { Icon } from "@/ui/Icon";
import { PosterCard, PosterSkeleton } from "@/ui/Poster";
import { Seg } from "@/ui/common";
import { VGrid, arrayRows, type Rows } from "@/ui/Virtual";
import { EmptyCatalog, NoSource } from "./states";

export function VodScreen({ kind }: { kind: "movie" | "series" }) {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <VodInner source={source} kind={kind} />;
}

type Item = MovieRow | SeriesRow;

function VodInner({ source, kind }: { source: Source; kind: "movie" | "series" }) {
  const t = useT();
  const nav = useNavigate();
  const prefs = usePrefs();
  const cats = useCategories(source, kind);
  const favs = useFavorites(source, kind);
  const favSet = useMemo(() => new Set(favs.map((f) => f.refId)), [favs]);
  const key = `${source.id}:${kind}`;
  const cat = prefs.liveCat[key] ?? "";
  const [sort, setSort] = useState<VodSort>("recent");
  const [filter, setFilter] = useState("");
  const [pop, setPop] = useState(false);
  const dq = useDebounced(filter, 250);
  const effSort: VodSort = cat !== "" && sort === "rating" ? "recent" : sort;
  const total = kind === "movie" ? source.counts.movie : source.counts.series;

  const make = () => (kind === "movie" ? moviesCol(source.cid, cat || null, effSort) : seriesCol(source.cid, cat || null, effSort)) as import("dexie").Collection<Item, unknown>;
  const arrayMode = dq.trim().length > 0;
  const arr = useLiveQuery(async () => (arrayMode ? make().filter((r) => r.norm.includes(normText(dq))).limit(2000).toArray() : []), [arrayMode, dq, cat, effSort, source.cid, kind]);
  const paged = usePagedQuery<Item>(() => (arrayMode ? null : make()), [source.cid, cat, effSort, kind, arrayMode], 96);
  const rows: Rows<Item> = useMemo(() => (arrayMode ? arrayRows(arr ?? []) : paged), [arrayMode, arr, paged]);

  const idOf = (r: Item) => (kind === "movie" ? (r as MovieRow).streamId : (r as SeriesRow).seriesId);
  const chips = (cats ?? []).slice(0, 7);
  const catLabel = cat === "" ? "" : cats?.find((c) => c.extId === cat)?.label ?? "";
  const title = t(kind === "movie" ? "nav.movies" : "nav.series");

  if (!total && source.state !== "syncing") return <EmptyCatalog />;

  return (
    <div className="page" style={{ padding: 0, gap: 0, overflow: "hidden" }}>
      <div style={{ padding: "1.5rem 1.5rem 0.75rem", display: "flex", flexDirection: "column", gap: "0.875rem" }}>
        <div className="page-head">
          <div>
            <h1>{title}</h1>
            <div className="sub">{rows.count != null ? t("common.items", { n: rows.count.toLocaleString(prefs.lang) }) : t("common.loading")}{catLabel ? ` · ${catLabel}` : ""}</div>
          </div>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <div className="search-box" style={{ width: "14rem" }}>
              <Icon name="search" size={16} />
              <input className="input" placeholder={t("common.filter")} value={filter} onChange={(e) => setFilter(e.target.value)} aria-label={t("common.filter")} />
            </div>
            <Seg label={t("vod.sortBy")} value={effSort} onChange={setSort} options={[
              { v: "recent", label: t("common.sortRecent") }, { v: "provider", label: t("common.sortProvider") },
              ...(cat === "" ? [{ v: "rating" as const, label: t("common.sortRating") }] : []),
            ]} />
          </div>
        </div>
        <div className="chips scroll" role="tablist" aria-label={t("common.categories")}>
          <button className="chip" role="tab" aria-selected={cat === ""} onClick={() => prefs.set({ liveCat: { ...prefs.liveCat, [key]: "" } })}>{t("common.all")}</button>
          {chips.map((c) => <button key={c.extId} className="chip" role="tab" aria-selected={cat === c.extId} onClick={() => prefs.set({ liveCat: { ...prefs.liveCat, [key]: c.extId } })}>{c.label}</button>)}
          {cat !== "" && !chips.some((c) => c.extId === cat) && <button className="chip" role="tab" aria-selected>{catLabel}</button>}
          <button className="chip" onClick={() => setPop(true)}>{t("common.allCategories")} ▾</button>
        </div>
      </div>
      {rows.count === 0 ? <div className="state-card"><p>{t("vod.empty")}</p></div> : (
        <VGrid
          rows={rows} minW={150} gap={20} cellH={(w) => w * 1.5 + 58} resetKey={`${cat}|${effSort}|${dq}|${kind}`}
          render={(r) => r ? (
            <PosterCard
              kind={kind === "movie" ? "movie" : "tv"} year={r.year}
              title={r.title} image={kind === "movie" ? (r as MovieRow).poster : (r as SeriesRow).poster}
              meta={[r.year, r.rating > 0 ? `★ ${r.rating.toFixed(1)}` : null].filter(Boolean).join(" · ")}
              fav={favSet.has(idOf(r))} onClick={() => nav(`/${kind === "movie" ? "movie" : "serie"}/${idOf(r)}`)}
            />
          ) : <PosterSkeleton />}
        />
      )}
      {pop && (
        <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) setPop(false); }}>
          <div className="modal" style={{ height: "min(36rem, 80vh)", padding: "1.25rem 0.5rem 1rem" }} role="dialog" aria-label={t("common.categories")}>
            <h3 style={{ padding: "0 1rem" }}>{t("common.categories")}</h3>
            <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
              <CategoryList cats={cats ?? []} value={cat} extra={[{ id: "", label: t("common.all"), n: total }]}
                onPick={(c) => { prefs.set({ liveCat: { ...prefs.liveCat, [key]: c } }); setPop(false); }} />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
