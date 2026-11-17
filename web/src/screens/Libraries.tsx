// « Mes bibliothèques » (maquette « Bibliothèques par serveur », variante B) : une section par serveur Jellyfin / Emby / Plex ou partage
// NAS, avec son état (en ligne, relais Plex, hors ligne · dernière copie), le nombre de films et de séries et une rangée d'affiches
// (titres en cours d'abord, avec leur progression). Ouvrir un partage NAS montre ses dossiers et ce que l'appli a reconnu dans les
// noms de fichier (maquette « Bibliothèque serveur et NAS »). Le catalogue est local : hors ligne, les affiches restent affichées.
import { useEffect, useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { useNavigate } from "react-router-dom";
import { db } from "@/db/db";
import { historyOf } from "@/db/queries";
import type { HistoryRow, MovieRow, SeriesRow, Source } from "@/db/types";
import { useT } from "@/i18n";
import { copyDate, isLibrarySource, libStatus, progressRatio, type LibStatus } from "@/lib/mediaWizard";
import { nasAvailable, parseNasLink, scanNas } from "@/net/nas";
import { currentTransport } from "@/net/transport";
import { mediaOf } from "@/sync/core";
import { usePrefs } from "@/state/prefs";
import { useSources } from "@/state/sources";
import { Icon } from "@/ui/Icon";
import { PosterCard } from "@/ui/Poster";
import { resumeOf } from "./Home";
import { ServerLogo } from "./MediaWizard";
import { SERVER_COLORS } from "@/net/discovery";

const PROBE_MS = 6000;
const ROW_SIZE = 7;

/** Sonde courte et non bloquante : le serveur répond-il ? null tant que la vérification est en cours. */
function useReachable(source: Source): boolean | null {
  const [ok, setOk] = useState<boolean | null>(null);
  useEffect(() => {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), PROBE_MS);
    setOk(null);
    void (async () => {
      try {
        if (source.type === "nas") {
          if (!nasAvailable()) throw new Error("nas-unavailable");
          await scanNas(source.server.trim(), true);
        } else {
          await mediaOf(await currentTransport(), source, ctrl.signal).probe();
        }
        if (!ctrl.signal.aborted) setOk(true);
      } catch {
        setOk(false);
      } finally {
        clearTimeout(timer);
      }
    })();
    return () => { clearTimeout(timer); ctrl.abort(); };
  }, [source.id, source.type, source.server, source.password]);
  return ok;
}

interface Tile { key: string; title: string; image: string | null; year: number | null; kind: "movie" | "tv"; ratio: number | null; open: () => void }

/** Rangée d'affiches : titres en cours (avec barre), complétés par les derniers ajouts. */
function useTiles(source: Source, open: (path: string) => void): Tile[] {
  const profileId = usePrefs((s) => s.profileId);
  const hist = useLiveQuery(async () => (await historyOf(profileId, source.id!)).filter((h) => progressRatio(h.pos, h.dur) != null).slice(0, ROW_SIZE), [profileId, source.id]) ?? [];
  const movies = useLiveQuery(() => db.movies.where("[sourceId+added]").between([source.cid, -1], [source.cid, Infinity]).reverse().limit(ROW_SIZE * 2).toArray(), [source.cid]) ?? [];
  const series = useLiveQuery(() => db.series.where("sourceId").equals(source.cid).limit(ROW_SIZE).toArray(), [source.cid]) ?? [];
  const tiles: Tile[] = hist.map((h: HistoryRow) => ({
    key: `h${h.key}`, title: h.title, image: h.image, year: null, kind: h.kind === "movie" ? "movie" : "tv", ratio: progressRatio(h.pos, h.dur), open: () => resumeOf(source, h),
  }));
  const seen = new Set(hist.map((h) => `${h.kind === "movie" ? "m" : "s"}${h.kind === "movie" ? h.refId : (h.seriesId ?? h.refId)}`));
  const more: Tile[] = [
    ...movies.filter((m: MovieRow) => !seen.has(`m${m.streamId}`)).map((m: MovieRow) => ({ key: `m${m.id}`, title: m.title, image: m.poster, year: m.year, kind: "movie" as const, ratio: null, open: () => open(`/movie/${m.streamId}`) })),
    ...series.filter((s: SeriesRow) => !seen.has(`s${s.seriesId}`)).map((s: SeriesRow) => ({ key: `s${s.id}`, title: s.title, image: s.poster, year: s.year, kind: "tv" as const, ratio: null, open: () => open(`/serie/${s.seriesId}`) })),
  ];
  return [...tiles, ...more].slice(0, ROW_SIZE);
}

const STATE_KEY = { checking: "lib.checking", online: "lib.online", relay: "lib.relay", offline: "lib.offline" } as const;

function StateChip({ status, source }: { status: LibStatus; source: Source }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  return <span className={`lib-state ${status}`}><i aria-hidden="true" />{status === "offline" ? t("lib.offline", { d: copyDate(source.lastSyncAt, lang) }) : t(STATE_KEY[status])}</span>;
}

function LibrarySection({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const setPrefs = usePrefs((s) => s.set);
  const reachable = useReachable(source);
  const status = libStatus(source, reachable);
  const [folders, setFolders] = useState(false);
  const goTo = (path: string) => { setPrefs({ activeSourceId: source.id! }); nav(path); };
  const tiles = useTiles(source, goTo);
  const kind = source.type as keyof typeof SERVER_COLORS;
  const isNas = source.type === "nas";
  return (
    <section className="lib-section" aria-label={source.name}>
      <div className="lib-title">
        <button className="open" aria-label={t("lib.open", { name: source.name })} aria-expanded={isNas ? folders : undefined}
          onClick={() => (isNas ? setFolders((v) => !v) : goTo("/movies"))}>
          <ServerLogo kind={kind} size={46} />
          <h2>{source.name}</h2>
        </button>
        <StateChip status={status} source={source} />
        <span className="lib-count">{t("lib.counts", { m: source.counts.movie.toLocaleString(), s: source.counts.series.toLocaleString() })}</span>
      </div>
      {tiles.length > 0 ? (
        <div className="lib-row">
          {tiles.map((x) => (
            <PosterCard key={x.key} kind={x.kind} year={x.year} title={x.title} image={x.image} progress={x.ratio ?? undefined} onClick={x.open} />
          ))}
        </div>
      ) : (
        <p className="lib-empty">{t("lib.noTitles")}</p>
      )}
      {isNas && folders && <NasPanel source={source} />}
    </section>
  );
}

/** Vue dossiers d'un partage NAS + « Reconnu automatiquement » (nom de fichier → titre reconnu). */
function NasPanel({ source }: { source: Source }) {
  const t = useT();
  const cats = useLiveQuery(() => db.categories.where("sourceId").equals(source.cid).toArray(), [source.cid]) ?? [];
  const found = useLiveQuery(async () => {
    const movies = await db.movies.where("[sourceId+ord]").between([source.cid, -1], [source.cid, Infinity]).limit(6).toArray();
    const series = await db.series.where("sourceId").equals(source.cid).limit(3).toArray();
    const rows: Array<{ file: string; title: string }> = [];
    for (const m of movies) {
      const rel = m.url ? parseNasLink(m.url) : null;
      if (rel) rows.push({ file: rel, title: m.year ? `${m.title} (${m.year})` : m.title });
    }
    for (const s of series) {
      const info = (await db.details.get(`series:${source.id}:${s.seriesId}`))?.json as { media?: Array<{ url: string }> } | undefined;
      const rel = info?.media?.[0]?.url ? parseNasLink(info.media[0].url) : null;
      if (rel) rows.push({ file: rel, title: s.title });
    }
    return rows;
  }, [source.cid, source.id]) ?? [];
  // Un dossier peut être à la fois film et série : on additionne les titres.
  const folders = new Map<string, { name: string; n: number }>();
  for (const c of cats) { const f = folders.get(c.extId) ?? { name: c.name, n: 0 }; f.n += c.count; folders.set(c.extId, f); }
  return (
    <div className="lib-nas">
      <div className="lib-panel">
        <h3>{t("lib.folders")}</h3>
        <span className="lib-count">{source.server}</span>
        {[...folders.values()].map((f) => (
          <div key={f.name} className="lib-folder"><Icon name="folder" size={22} /><span>{f.name}</span><span className="n">{f.n.toLocaleString()}</span></div>
        ))}
      </div>
      <div className="lib-panel">
        <h3>{t("lib.auto")}</h3>
        {found.length === 0 && <p className="lib-empty">{t("lib.autoEmpty")}</p>}
        {found.map((x) => (
          <div key={x.file} className="lib-found"><code title={x.file}>{x.file}</code><Icon name="chevron" size={22} /><b>{x.title}</b></div>
        ))}
      </div>
    </div>
  );
}

export function Libraries() {
  const t = useT();
  const nav = useNavigate();
  const list = useSources((s) => s.list).filter((s) => isLibrarySource(s.type));
  return (
    <div className="lib-screen">
      <div className="lib-head">
        <h1>{t("lib.title")}</h1>
        <span className="grow" />
        <button className="btn" onClick={() => nav("/welcome?add=1")}><Icon name="plus" size={18} />{t("lib.add")}</button>
      </div>
      {list.length === 0 && <p className="lib-empty">{t("lib.empty")}</p>}
      {list.map((s) => <LibrarySection key={s.id} source={s} />)}
    </div>
  );
}
