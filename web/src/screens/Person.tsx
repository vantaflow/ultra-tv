// Page d'un acteur ou d'un réalisateur : ses films et séries présents dans la playlist.
// Filmographie TMDB via le Worker (bureau appairé) rapprochée par titre + année, plus les fiches déjà ouvertes qui le citent.
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { cloudAvailable, tmdbGet } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";
import { db } from "@/db/db";
import { searchByWords } from "@/db/queries";
import type { MovieRow, SeriesRow, Source } from "@/db/types";
import { useT } from "@/i18n";
import { creditMatches, personCredits, personSearchBest, type PersonCredit } from "@/lib/person";
import { normText } from "@/lib/text";
import { useActiveSource } from "@/state/sources";
import { Icon } from "@/ui/Icon";
import { PosterCard } from "@/ui/Poster";
import { NoSource } from "./states";

interface Works { movies: MovieRow[]; series: SeriesRow[] }

async function tmdbFilmography(name: string): Promise<PersonCredit[]> {
  const { paired, worker } = useCloud.getState();
  if (!paired || !cloudAvailable()) return [];
  const token = await getToken();
  if (!token) return [];
  const id = personSearchBest(await tmdbGet(worker, token, `search/person?query=${encodeURIComponent(name)}`), name);
  if (!id) return [];
  return personCredits(await tmdbGet(worker, token, `person/${id}/combined_credits`));
}

async function findWorks(source: Source, name: string): Promise<Works> {
  const movies = new Map<number, MovieRow>();
  const series = new Map<number, SeriesRow>();
  const credits = await tmdbFilmography(name).catch(() => [] as PersonCredit[]);
  for (const c of credits.slice(0, 150)) {
    const q = normText(c.title);
    if (!q) continue;
    const hits = c.tv ? await searchByWords(db.series, source.cid, q, 12) : await searchByWords(db.movies, source.cid, q, 12);
    const hit = (hits as Array<MovieRow | SeriesRow>).find((r) => creditMatches(c, r.title, r.year));
    if (hit && c.tv) series.set(hit.id!, hit as SeriesRow);
    else if (hit) movies.set(hit.id!, hit as MovieRow);
  }
  // Fiches déjà ouvertes (cache local) dont la distribution ou le réalisateur cite ce nom.
  const needle = normText(name);
  const details = await db.details.where("key").startsWithAnyOf([`vod:${source.id}:`, `series:${source.id}:`]).toArray();
  for (const d of details) {
    const info = (d.json as { info?: Record<string, unknown> } | undefined)?.info;
    const people = normText([info?.cast, info?.actors, info?.director].filter((x) => typeof x === "string").join(" "));
    if (!needle || !people.includes(needle)) continue;
    const id = Number(d.key.split(":")[2]);
    if (d.key.startsWith("vod:")) { const m = await db.movies.where("[sourceId+streamId]").equals([source.cid, id]).first(); if (m) movies.set(m.id!, m); }
    else { const s = await db.series.where("[sourceId+seriesId]").equals([source.cid, id]).first(); if (s) series.set(s.id!, s); }
  }
  return { movies: [...movies.values()], series: [...series.values()] };
}

export function Person() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <PersonInner source={source} />;
}

function PersonInner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const { name: raw } = useParams();
  const name = decodeURIComponent(raw ?? "");
  const [works, setWorks] = useState<Works | null>(null);
  useEffect(() => {
    let dead = false;
    setWorks(null);
    void findWorks(source, name).then((w) => { if (!dead) setWorks(w); }, () => { if (!dead) setWorks({ movies: [], series: [] }); });
    return () => { dead = true; };
  }, [source, name]);
  return (
    <div className="page">
      <button className="back" style={{ position: "static" }} onClick={() => nav(-1)}><Icon name="back" size={20} stroke={2.5} />{t("common.back")}</button>
      <h1>{name}</h1>
      {!works ? <p className="muted">{t("person.loading")}</p>
        : !works.movies.length && !works.series.length ? <p className="muted">{t("person.empty")}</p>
        : (
          <>
            {works.movies.length > 0 && <section><h2 className="section-title">{t("nav.movies")}</h2><div className="poster-grid">
              {works.movies.map((m) => <PosterCard key={m.id} kind="movie" title={m.title} image={m.poster} year={m.year} meta={m.year ? String(m.year) : undefined} onClick={() => nav(`/movie/${m.streamId}`)} />)}
            </div></section>}
            {works.series.length > 0 && <section><h2 className="section-title">{t("nav.series")}</h2><div className="poster-grid">
              {works.series.map((s) => <PosterCard key={s.id} kind="tv" title={s.title} image={s.poster} year={s.year} meta={s.year ? String(s.year) : undefined} onClick={() => nav(`/serie/${s.seriesId}`)} />)}
            </div></section>}
          </>
        )}
    </div>
  );
}
