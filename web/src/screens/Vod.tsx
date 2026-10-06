import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import { moviesCol, searchByWords, seriesCol, type VodSort } from "@/db/queries";
import type { CategoryRow, MovieRow, SeriesRow, Source } from "@/db/types";
import { useCategories, useFavorites } from "@/hooks/data";
import { useDebounced } from "@/hooks/misc";
import { usePagedQuery } from "@/hooks/paged";
import { useT } from "@/i18n";
import { normText } from "@/lib/text";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { CategoryList } from "@/ui/CategoryList";
import { HScroll } from "@/ui/HScroll";
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
  const total = kind === "movie" ? source.counts.movie : source.counts.series;

  const make = () => (kind === "movie" ? moviesCol(source.cid, cat || null, sort) : seriesCol(source.cid, cat || null, sort)) as import("dexie").Collection<Item, unknown>;
  // Filtre texte = mode tableau. Dans « Tout » : index par mot (200 résultats, retriés selon le tri choisi) ;
  // dans une catégorie : balayage de la catégorie seule (déjà bornée par l'index).
  const arrayMode = dq.trim().length > 0;
  const arr = useLiveQuery(async () => {
    if (!arrayMode) return [];
    const q = normText(dq.trim());
    if (q && cat === "") {
      const table = (kind === "movie" ? db.movies : db.series) as unknown as import("dexie").Table<Item, number>;
      return sortHits(await searchByWords(table, source.cid, q, 200), sort);
    }
    return (q ? make().filter((r) => r.norm.includes(q)) : make()).limit(2000).toArray();
  }, [arrayMode, dq, cat, sort, source.cid, kind]);
  const paged = usePagedQuery<Item>(() => (arrayMode ? null : make()), [source.cid, source.lastSyncAt, cat, sort, kind, arrayMode], 96);
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
            <Seg label={t("vod.sortBy")} value={sort} onChange={setSort} options={[
              { v: "recent", label: t("common.sortRecent") }, { v: "provider", label: t("common.sortProvider") },
              { v: "rating", label: t("common.sortRating") },
            ]} />
          </div>
        </div>
        <HScroll wheel follow={cat} className="chips scroll" role="tablist" aria-label={t("common.categories")}>
          <button className="chip" role="tab" aria-selected={cat === ""} onClick={() => prefs.set({ liveCat: { ...prefs.liveCat, [key]: "" } })}>{t("common.all")}</button>
          {chips.map((c) => <button key={c.extId} className="chip" role="tab" aria-selected={cat === c.extId} onClick={() => prefs.set({ liveCat: { ...prefs.liveCat, [key]: c.extId } })}>{c.label}</button>)}
          {cat !== "" && !chips.some((c) => c.extId === cat) && <button className="chip" role="tab" aria-selected>{catLabel}</button>}
          <button className="chip" onClick={() => setPop(true)}>{t("common.allCategories")} ▾</button>
        </HScroll>
      </div>
      {cat === "" && !arrayMode && (cats?.length ?? 0) > 0 ? (
        <CategoryRows source={source} kind={kind} cats={cats!} favSet={favSet} sort={sort}
          onSeeAll={(id) => prefs.set({ liveCat: { ...prefs.liveCat, [key]: id } })}
          onOpen={(id) => nav(`/${kind === "movie" ? "movie" : "serie"}/${id}`)} />
      ) : rows.count === 0 ? <div className="state-card"><p>{t("vod.empty")}</p></div> : (
        <VGrid
          rows={rows} minW={150} gap={20} cellH={(w) => w * 1.5 + 58} resetKey={`${cat}|${sort}|${dq}|${kind}`}
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

const ROW_SIZE = 20;

/** Mieux notés d'abord (tri stable : à note égale, ordre de la collection conservé). Exporté pour les tests. */
export function byRating<T extends { rating: number }>(items: T[]): T[] {
  return [...items].sort((a, b) => (b.rating || 0) - (a.rating || 0));
}

/** Résultats d'une recherche par index, remis dans l'ordre du tri choisi (l'index les rend par mot, pas par tri). */
export function sortHits<T extends { rating: number; added: number; ord: number }>(items: T[], sort: VodSort): T[] {
  if (sort === "rating") return byRating(items);
  if (sort === "recent") return [...items].sort((a, b) => b.added - a.added);
  return [...items].sort((a, b) => a.ord - b.ord);
}

/** Vue « Tout » : une rangée défilante par catégorie (20 premiers selon le tri choisi), « Voir tout » ouvre la grille. Chargement progressif. */
function CategoryRows({ source, kind, cats, favSet, sort, onSeeAll, onOpen }: {
  source: Source; kind: "movie" | "series"; cats: CategoryRow[]; favSet: Set<number>; sort: VodSort;
  onSeeAll: (extId: string) => void; onOpen: (id: number) => void;
}) {
  const [shown, setShown] = useState(8);
  const sentinel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver((e) => { if (e.some((x) => x.isIntersecting)) setShown((n) => Math.min(cats.length, n + 8)); }, { rootMargin: "800px" });
    io.observe(el);
    return () => io.disconnect();
  }, [cats.length]);
  return (
    <div className="cat-rows">
      {cats.slice(0, shown).map((c) => <LazyRow key={c.extId}><CategoryRowView source={source} kind={kind} cat={c} favSet={favSet} sort={sort} onSeeAll={onSeeAll} onOpen={onOpen} /></LazyRow>)}
      <div ref={sentinel} style={{ height: 1 }} />
    </div>
  );
}

/**
 * Ne monte son contenu que près de l'écran : `shown` ne fait qu'augmenter, et chaque rangée garde sinon sa requête
 * Dexie et ses centaines de cartes pour toute la session. Hors écran, on garde la hauteur mesurée (pas de saut de défilement).
 */
function LazyRow({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [near, setNear] = useState(false);
  const height = useRef(300);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver((entries) => {
      const e = entries[entries.length - 1];
      if (!e) return;
      if (!e.isIntersecting) height.current = el.offsetHeight || height.current;
      setNear(e.isIntersecting);
    }, { rootMargin: "700px 0px" });
    io.observe(el);
    return () => io.disconnect();
  }, []);
  return <div ref={ref} className={near ? "lazy-row on" : "lazy-row"} style={near ? undefined : { minHeight: height.current }}>{near ? children : null}</div>;
}

function CategoryRowView({ source, kind, cat, favSet, sort, onSeeAll, onOpen }: {
  source: Source; kind: "movie" | "series"; cat: CategoryRow; favSet: Set<number>; sort: VodSort;
  onSeeAll: (extId: string) => void; onOpen: (id: number) => void;
}) {
  const t = useT();
  const items = useLiveQuery(async () => {
    const col = (kind === "movie" ? moviesCol(source.cid, cat.extId, sort) : seriesCol(source.cid, cat.extId, sort)) as import("dexie").Collection<Item, unknown>;
    // Tri « note » servi par l'index [sourceId+catExt+rating] : plus de lecture de la catégorie entière.
    return col.limit(ROW_SIZE).toArray();
  }, [source.cid, cat.extId, kind, sort]);
  if (items && items.length === 0) return null;
  const idOf = (r: Item) => (kind === "movie" ? (r as MovieRow).streamId : (r as SeriesRow).seriesId);
  return (
    <section className="cat-row-v" aria-label={cat.label}>
      <div className="cat-row-head">
        <h2 className="ellipsis">{cat.label}</h2>
        <button className="btn sm" onClick={() => onSeeAll(cat.extId)}>{t("vod.seeAll")}</button>
      </div>
      <HScroll className="cat-row-scroll">
        {(items ?? []).map((r) => (
          <div key={r.id} className="cat-row-item">
            <PosterCard kind={kind === "movie" ? "movie" : "tv"} year={r.year} title={r.title}
              image={kind === "movie" ? (r as MovieRow).poster : (r as SeriesRow).poster}
              meta={[r.year, r.rating > 0 ? `★ ${r.rating.toFixed(1)}` : null].filter(Boolean).join(" · ")}
              fav={favSet.has(idOf(r))} onClick={() => onOpen(idOf(r))} />
          </div>
        ))}
        {!items && Array.from({ length: 6 }, (_, i) => <div key={i} className="cat-row-item"><PosterSkeleton /></div>)}
        {items && items.length >= ROW_SIZE && (
          <button className="cat-row-more" onClick={() => onSeeAll(cat.extId)}>{t("vod.seeAll")}</button>
        )}
      </HScroll>
    </section>
  );
}
