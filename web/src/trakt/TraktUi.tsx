// Gestes Trakt des fiches (watchlist, vu / non vu, note, saison / épisodes) et section des réglages.
// Rien ne s'affiche si le compte n'est pas lié à Trakt.

import { useState } from "react";
import type { TraktWriteResult } from "@/cloud/client";
import { useT } from "@/i18n";
import { useUi } from "@/state/ui";
import { Switch } from "@/ui/common";
import { Icon } from "@/ui/Icon";
import { rate, setEpisodesWatched, setMovieWatched, setWatchlist, type TraktWork } from "./actions";
import { useTraktLibrary } from "./library";
import { inWatchlist, ratingFor, type EpRef } from "./progress";
import { useTraktPrefs } from "./prefs";
import { watchedEpisodes } from "./availability";

type T = ReturnType<typeof useT>;

function useRunner(t: T) {
  const [busy, setBusy] = useState(false);
  const run = async (op: () => Promise<TraktWriteResult>) => {
    if (busy) return;
    setBusy(true);
    try {
      const r = await op();
      if (r === "unmatched") useUi.getState().toast(t("trakt.unmatched"));
      else if (r === "not-linked") useUi.getState().toast(t("trakt.notLinked"));
      else if (r === "failed") useUi.getState().toast(t("trakt.failed"));
    } finally { setBusy(false); }
  };
  return { busy, run };
}

function RatingSelect({ work, current, t, disabled, onRate }: { work: TraktWork; current: number | null; t: T; disabled: boolean; onRate: (n: number) => void }) {
  return (
    <select className="btn lg" aria-label={t("trakt.rateTitle")} title={t("trakt.rateTitle")} disabled={disabled} value={current ?? ""}
      onChange={(e) => { const v = Number(e.target.value); if (Number.isInteger(v)) onRate(v); }} data-work={work.type}>
      <option value="" disabled>{current ? t("trakt.yourRating", { n: current }) : t("trakt.rate")}</option>
      {[10, 9, 8, 7, 6, 5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{`★ ${n}`}</option>)}
      {current != null && <option value={0}>{t("trakt.removeRating")}</option>}
    </select>
  );
}

/** Fiche film : watchlist, vu / non vu, note. */
export function TraktMovieActions({ title, year, tmdb, seen }: { title: string; year: number | null; tmdb?: number; seen: boolean }) {
  const t = useT();
  const lib = useTraktLibrary((s) => s.lib);
  const { busy, run } = useRunner(t);
  if (!lib) return null;
  const work: TraktWork = { type: "movie", title, year, tmdb };
  const inList = inWatchlist(lib, "movie", title, year);
  const r = ratingFor(lib, "movie", title, year);
  return (
    <div className="actions" role="group" aria-label="Trakt">
      <button className="btn lg" disabled={busy} aria-pressed={inList} onClick={() => void run(() => setWatchlist(work, !inList))}>
        <Icon name={inList ? "check" : "plus"} size={18} stroke={2.5} />{t(inList ? "trakt.removeWatchlist" : "trakt.addWatchlist")}
      </button>
      <button className="btn lg" disabled={busy} aria-pressed={seen} onClick={() => void run(() => setMovieWatched(work, !seen))}>
        <Icon name="check" size={18} stroke={2.5} />{t(seen ? "trakt.markUnwatched" : "trakt.markWatched")}
      </button>
      <RatingSelect work={work} current={r} t={t} disabled={busy} onRate={(n) => void run(() => rate(work, n))} />
    </div>
  );
}

/** Fiche série : watchlist, note, saison affichée vue / non vue, et épisodes de cette saison un à un. */
export function TraktSeriesActions({ title, year, tmdb, season, episodes }: { title: string; year: number | null; tmdb?: number; season: number | null; episodes: EpRef[] }) {
  const t = useT();
  const lib = useTraktLibrary((s) => s.lib);
  const watched = useTraktLibrary((s) => s.watched);
  const { busy, run } = useRunner(t);
  if (!lib) return null;
  const work: TraktWork = { type: "show", title, year, tmdb };
  const inList = inWatchlist(lib, "show", title, year);
  const r = ratingFor(lib, "show", title, year);
  const seen = watchedEpisodes(watched, title, year) ?? new Set<string>();
  const eps = episodes.filter((e) => e.season === season);
  const seasonSeen = eps.length > 0 && eps.every((e) => seen.has(`${e.season}x${e.episode}`));
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
      <div className="actions" role="group" aria-label="Trakt">
        <button className="btn lg" disabled={busy} aria-pressed={inList} onClick={() => void run(() => setWatchlist(work, !inList))}>
          <Icon name={inList ? "check" : "plus"} size={18} stroke={2.5} />{t(inList ? "trakt.removeWatchlist" : "trakt.addWatchlist")}
        </button>
        <RatingSelect work={work} current={r} t={t} disabled={busy} onRate={(n) => void run(() => rate(work, n))} />
        {season != null && eps.length > 0 && (
          <button className="btn lg" disabled={busy} aria-pressed={seasonSeen} onClick={() => void run(() => setEpisodesWatched(work, eps, !seasonSeen))}>
            <Icon name="check" size={18} stroke={2.5} />{t(seasonSeen ? "trakt.seasonUnwatched" : "trakt.seasonWatched", { n: season })}
          </button>
        )}
      </div>
      {season != null && eps.length > 0 && (
        <details>
          <summary className="muted">{t("trakt.episodeToggle")}</summary>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", marginTop: "0.5rem" }}>
            {eps.map((e) => {
              const on = seen.has(`${e.season}x${e.episode}`);
              return (
                <label key={`${e.season}x${e.episode}`} className="chip" style={{ display: "inline-flex", gap: "0.375rem", alignItems: "center" }}>
                  <input type="checkbox" checked={on} disabled={busy} onChange={() => void run(() => setEpisodesWatched(work, [e], !on))} />
                  {t("common.episodeShort", { n: e.episode })}
                </label>
              );
            })}
          </div>
        </details>
      )}
    </div>
  );
}

/** Section Trakt des réglages (lecture) : marquer vu à la fin, listes personnelles de l'accueil. */
export function TraktSettings({ Pref }: { Pref: (p: { label: string; desc?: string; children: React.ReactNode }) => React.JSX.Element }) {
  const t = useT();
  const lib = useTraktLibrary((s) => s.lib);
  const p = useTraktPrefs();
  if (!lib) return null;
  return (
    <>
      <h3 style={{ marginTop: "1.5rem" }}>{t("settings.trakt")}</h3>
      <Pref label={t("settings.traktAutoWatched")} desc={t("settings.traktAutoWatchedHint")}>
        <Switch on={p.autoWatched} onChange={(v) => p.setAutoWatched(v)} label={t("settings.traktAutoWatched")} />
      </Pref>
      {lib.lists.length > 0 && <p className="muted">{t("settings.traktLists")} · {t("settings.traktListsHint")}</p>}
      {lib.lists.map((l) => (
        <Pref key={l.id} label={l.name}>
          <Switch on={!p.hiddenLists.includes(l.id)} onChange={(v) => p.setListVisible(l.id, v)} label={l.name} />
        </Pref>
      ))}
    </>
  );
}
