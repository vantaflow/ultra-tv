import { useLiveQuery } from "dexie-react-hooks";
import { useVirtualizer } from "@tanstack/react-virtual";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { db } from "@/db/db";
import { channelsCol, programsFor } from "@/db/queries";
import type { ChannelRow, ProgramRow, Source } from "@/db/types";
import { useCategories, useFavorites } from "@/hooks/data";
import { useNow } from "@/hooks/misc";
import { useT } from "@/i18n";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { guideColPx } from "@/lib/guideCol";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { type GuideLoadState, useGuideLoad } from "@/hooks/guideLoad";
import { sortGuideChannels } from "@/lib/guideOrder";
import { HScroll } from "@/ui/HScroll";
import { hhmm } from "@/ui/common";
import { Icon } from "@/ui/Icon";
import { Img } from "@/ui/Img";
import { ProgActions, useRecordingIds, useReminderIds } from "@/ui/ProgActions";
import { progId } from "@/lib/schedule";
import { progRef } from "@/state/schedule";
import { NoSource } from "./states";

const PX_MIN = 5;
const HOURS = 6;
const TOTAL_W = HOURS * 60 * PX_MIN;

/** Nombre maximal de chaînes affichées dans une catégorie (au-delà, une mention le signale). */
const GUIDE_MAX_CHANNELS = 400;

export function Guide() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <Inner source={source} />;
}

/** Les guides XMLTV se chevauchent souvent : on garde une chronologie sans recouvrement. */
function dedupe(rows: ProgramRow[]): ProgramRow[] {
  const out: ProgramRow[] = [];
  let last = 0;
  for (const p of [...rows].sort((a, b) => a.start - b.start || b.end - a.end)) {
    const start = Math.max(p.start, last);
    if (p.end - start < 60_000) continue;
    out.push(start === p.start ? p : { ...p, start });
    last = p.end;
  }
  return out;
}

interface Pick { prog: ProgramRow; chan: ChannelRow; x: number; y: number }

const sameProgs = (a: ProgramRow[] | undefined, b: ProgramRow[]) =>
  !!a && a.length === b.length && a.every((p, i) => p.id === b[i]!.id && p.start === b[i]!.start && p.end === b[i]!.end);

/**
 * Programmes des seules lignes visibles, lus par fenêtre. Pendant le chargement du guide, la lecture est rafraîchie
 * par lots (toutes les 1,5 s) et ne remplace que les lignes dont le contenu a changé : pas de re-rendu complet à chaque lot écrit.
 */
export function useVisibleProgs(cid: number, ids: string[], from: number, to: number, loading: boolean): Map<string, ProgramRow[]> {
  const [map, setMap] = useState<Map<string, ProgramRow[]>>(new Map());
  const [tick, setTick] = useState(0);
  const key = ids.join("\u0001");
  useEffect(() => {
    if (!loading) return;
    const i = setInterval(() => setTick((n) => n + 1), 1500);
    return () => clearInterval(i);
  }, [loading]);
  useEffect(() => {
    let dead = false;
    const list = key ? key.split("\u0001") : [];
    void Promise.all(list.map((id) => programsFor(cid, id, from, to).then(dedupe))).then((rows) => {
      if (dead) return;
      setMap((prev) => {
        let changed = false;
        const next = new Map(prev);
        list.forEach((id, i) => { if (!sameProgs(prev.get(`${from}|${id}`), rows[i]!)) { next.set(`${from}|${id}`, rows[i]!); changed = true; } });
        return changed ? next : prev;
      });
    });
    return () => { dead = true; };
  }, [cid, key, from, to, tick, loading]);
  return map;
}

const EMPTY_PROGS: ProgramRow[] = [];
const NO_CHANS: ChannelRow[] = [];

const Row = memo(function Row({ c, progs, from, to, top, now, col, picked, onPick }: { c: ChannelRow; col: number; progs: ProgramRow[]; from: number; to: number; top: number; now: number; picked: ProgramRow | null; onPick: (p: ProgramRow, c: ChannelRow, e: React.MouseEvent) => void }) {
  return (
    <div className="g-row" style={{ position: "absolute", top, left: 0, width: TOTAL_W + col }}>
      <div className="g-chan">
        <span className="num mono">{c.num}</span>
        <span className="logo"><Img src={c.logo} contain /></span>
        <b className="clamp-2" title={c.display}>{c.display}</b>
      </div>
      <div className="g-track" style={{ width: TOTAL_W }}>
        {progs.map((p) => {
          const s = Math.max(p.start, from);
          const e = Math.min(p.end, to);
          if (e <= s) return null;
          const left = ((s - from) / 60000) * PX_MIN;
          const w = ((e - s) / 60000) * PX_MIN - 3;
          const isNow = p.start <= now && p.end > now;
          return (
            <button key={p.id} className={`g-prog${isNow ? " now" : ""}${picked?.id === p.id ? " sel" : ""}`} style={{ left, width: Math.max(w, 6) }} onClick={(ev) => onPick(p, c, ev)} title={p.title}>
              <b className="ellipsis">{p.title}</b>
              {w > 90 && <span className="mono">{hhmm(p.start)} – {hhmm(p.end)}</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
});

/** Bandeau discret : chargement en cours (octets reçus) ou échec avec « Réessayer ». La grille reste affichée dessous. */
export function GuideBanner({ state, onRetry }: { state: GuideLoadState; onRetry: () => void }) {
  const t = useT();
  if (state.phase === "idle") return null;
  if (state.phase === "error") {
    return (
      <div className="g-banner err" role="alert">
        <span>{t("guide.loadError")}</span>
        <button className="btn sm" onClick={onRetry}>{t("common.retry")}</button>
      </div>
    );
  }
  return (
    <div className="g-banner" role="status" aria-live="polite">
      <span>{state.bytes > 0 ? t("guide.loadingMb", { mb: (state.bytes / 1e6).toFixed(1) }) : t("guide.loading")}</span>
      <span className="bar" aria-hidden />
    </div>
  );
}

function Inner({ source }: { source: Source }) {
  const t = useT();
  const prefs = usePrefs();
  const cats = useCategories(source, "live");
  const favs = useFavorites(source, "live");
  const now = useNow(30_000);
  const [day, setDay] = useState(0);
  const [cat, setCat] = useState<string | null>(null);
  const [pick, setPick] = useState<Pick | null>(null);
  // Le guide se charge en arrière-plan à l'ouverture (si absent ou périmé) ; la grille s'affiche tout de suite.
  const { state: load, reload } = useGuideLoad(source);
  const loading = load.phase === "loading";
  const scroller = useRef<HTMLDivElement>(null);
  const col = guideColPx(prefs.guideCol);

  const from = useMemo(() => {
    if (day === 0) return Math.floor((Date.now() - 30 * 60_000) / (30 * 60_000)) * 30 * 60_000;
    const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() + day); return d.getTime() + 8 * 3600_000;
  }, [day, Math.floor(now / 1_800_000)]); // eslint-disable-line react-hooks/exhaustive-deps
  const to = from + HOURS * 3600_000;

  // Catégorie par défaut : favoris s'il y en a, sinon première catégorie du direct.
  const effCat = cat ?? (favs.length ? "__fav" : "");
  const chansQ = useLiveQuery(async () => {
    if (!source.cid) return [];
    // Tri par numéro de chaîne croissant partout (Tout, catégories, Favoris), puis plafond d'affichage.
    if (effCat === "__fav") return sortGuideChannels((await Promise.all(favs.map((f) => db.channels.where("[sourceId+streamId]").equals([source.cid, f.refId]).first()))).filter((c): c is ChannelRow => !!c && !!c.epg));
    return sortGuideChannels(await channelsCol(source.cid, effCat || null).filter((c) => !c.sep && !!c.epg).toArray()).slice(0, GUIDE_MAX_CHANNELS);
  }, [source.cid, effCat, favs.length]);
  const chans = chansQ ?? NO_CHANS;

  const v = useVirtualizer({ count: chans.length, getScrollElement: () => scroller.current, estimateSize: () => 72, overscan: 6 });
  const items = v.getVirtualItems();
  const visibleIds = useMemo(() => [...new Set(items.map((it) => chans[it.index]?.epg).filter((e): e is string => !!e))], [items, chans]);
  const progs = useVisibleProgs(source.cid, visibleIds, from, to, loading);
  const onPick = useCallback((p: ProgramRow, c: ChannelRow, e: React.MouseEvent) => { e.stopPropagation(); setPick({ prog: p, chan: c, x: e.clientX, y: e.clientY }); }, []);
  const label = effCat === "__fav" ? t("common.favorites") : effCat === "" ? t("guide.withEpg") : cats?.find((c) => c.extId === effCat)?.label ?? t("guide.withEpg");
  const nowX = ((now - from) / 60000) * PX_MIN;
  const days = [t("guide.today"), t("guide.tomorrow"), new Date(Date.now() + 2 * 86400_000).toLocaleDateString(prefs.lang, { weekday: "long" })];

  const watch = (c: ChannelRow, p: ProgramRow, replay: boolean) => {
    const tg = channelTarget(source, c);
    if (replay) tg.replay = { start: p.start, minutes: Math.ceil((p.end - p.start) / 60000) + 2 };
    usePlayer.getState().open(tg, "full", effCat === "__fav" ? null : effCat || null);
    setPick(null);
  };

  // Programme présenté en haut : celui choisi dans la grille, sinon le programme en cours de la première chaîne.
  const firstChan = chans[0];
  const firstProg = firstChan?.epg ? (progs.get(`${from}|${firstChan.epg}`) ?? EMPTY_PROGS).find((p) => p.start <= now && p.end > now) : undefined;
  const shown: { prog: ProgramRow; chan: ChannelRow } | null = pick ? { prog: pick.prog, chan: pick.chan } : firstChan && firstProg ? { prog: firstProg, chan: firstChan } : null;
  const shownLive = !!shown && shown.prog.start <= now && shown.prog.end > now;
  const reminded = useReminderIds();
  const recorded = useRecordingIds();
  const shownRef = shown ? progRef(source, shown.chan, shown.prog) : null;

  return (
    <div className="guide" onClick={() => setPick(null)}>
      <section className="g-top" aria-label={t("guide.title")}>
        <div className="g-preview" aria-hidden="true">
          {shown && <Img src={shown.chan.logo} contain />}
        </div>
        <div className="g-info" onClick={(e) => e.stopPropagation()}>
          {shown ? (
            <>
              <div className="g-eyebrow mono">{shownLive ? `${t("player.nowLabel")} · ` : ""}{shown.chan.display} · {hhmm(shown.prog.start)} – {hhmm(shown.prog.end)}</div>
              <h1 className="g-title clamp-2">{shown.prog.title}</h1>
              {shown.prog.desc && <p className="g-desc clamp-2">{shown.prog.desc}</p>}
              <div className="g-actions">
                {shown.prog.end > now && <button className="btn accent lg" onClick={() => watch(shown.chan, shown.prog, false)}><Icon name="play" size={16} fill />{t("guide.watchLive")}</button>}
                {shown.prog.end <= now && !!shown.chan.archive && <button className="btn accent lg" onClick={() => watch(shown.chan, shown.prog, true)}><Icon name="play" size={16} fill />{t("guide.replay")}</button>}
                {shownRef && <ProgActions source={source} prog={shownRef} now={now} size="lg" reminded={reminded.has(progId(shownRef))} recorded={recorded.has(progId(shownRef))} />}
              </div>
            </>
          ) : (
            <>
              <h1 className="g-title">{t("guide.title")}</h1>
              <p className="g-desc">{new Date(from).toLocaleDateString(prefs.lang, { weekday: "long", day: "numeric", month: "long" })} · {hhmm(from)} – {hhmm(to)}</p>
            </>
          )}
        </div>
      </section>
      <div className="g-filters" onClick={(e) => e.stopPropagation()}>
        <div className="chips" role="group" aria-label={t("a11y.day")}>{days.map((d, i) => <button key={i} className="chip" aria-selected={day === i} onClick={() => setDay(i)}>{d}</button>)}</div>
        <span className="g-sep" aria-hidden="true" />
        <span className="eyebrow g-catlabel">{t("common.categories")}</span>
        <HScroll wheel follow={effCat} className="chips scroll g-cats" role="tablist" aria-label={t("common.categories")}>
          <button className="chip" role="tab" aria-selected={effCat === "__fav"} onClick={() => setCat("__fav")}>{t("common.favorites")}<span className="n">{favs.length}</span></button>
          <button className="chip" role="tab" aria-selected={effCat === ""} onClick={() => setCat("")}>{t("common.all")}</button>
          {(cats ?? []).map((c) => <button key={c.extId} className="chip" role="tab" aria-selected={effCat === c.extId} onClick={() => setCat(c.extId)}>{c.label}<span className="n">{c.count}</span></button>)}
        </HScroll>
      </div>
      <GuideBanner state={load} onRetry={reload} />
      <div className="guide-grid" ref={scroller}>
        <div className="g-head" style={{ width: TOTAL_W + col }}>
          <div className="g-corner" />
          <div style={{ position: "relative", width: TOTAL_W }}>
            {Array.from({ length: HOURS * 2 }, (_, i) => <span key={i} className="g-time mono" style={{ left: i * 30 * PX_MIN, width: 30 * PX_MIN }}>{hhmm(from + i * 30 * 60_000)}</span>)}
            {nowX >= 0 && nowX <= TOTAL_W && <span className="g-now-pill mono" style={{ insetInlineStart: nowX }}>{hhmm(now)}</span>}
          </div>
        </div>
        <div style={{ position: "relative", height: v.getTotalSize(), width: TOTAL_W + col }}>
          {nowX >= 0 && nowX <= TOTAL_W && <div className="g-now" style={{ insetInlineStart: col + nowX }} />}
          {items.map((it) => {
            const c = chans[it.index]!;
            return <Row key={c.id} c={c} progs={(c.epg && progs.get(`${from}|${c.epg}`)) || EMPTY_PROGS} from={from} to={to} top={it.start} now={now} col={col} picked={pick?.prog ?? null} onPick={onPick} />;
          })}
        </div>
        {chansQ && chans.length === 0 && <div className="g-empty">{t("guide.noChannels")}</div>}
        {chans.length >= GUIDE_MAX_CHANNELS && effCat !== "__fav" && <div className="g-empty">{t("guide.capped", { n: GUIDE_MAX_CHANNELS })}</div>}
      </div>
    </div>
  );
}
