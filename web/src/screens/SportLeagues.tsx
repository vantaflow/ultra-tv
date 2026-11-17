// Espace « Compétitions » de la rubrique Sport : ligues dans l'ordre du profil, résultats / à venir / classement, feuille de
// match (score, temps forts, statistiques) et mode sans spoiler. Chaque match est relié aux chaînes de l'utilisateur
// (programmes du guide qui diffusent la même rencontre). Référence fonctionnelle : écran Compétitions d'Android.
import { useMemo, useState } from "react";
import type { Source } from "@/db/types";
import { useSheet, useLeagueData, useTable } from "@/hooks/sportLeagues";
import { useT } from "@/i18n";
import {
  allLeagues, channelsFor, decodePrefs, encodePrefs, fixturesOf, hasSpoiler, liveOf, matchTitle, moveLeague, resultsOf, setLeagueHidden,
  showScore, showTable, visibleLeagues, type LeagueMatch, type StandingRow,
} from "@/lib/sportLeagues";
import type { SportEntry } from "@/lib/sport";
import { badgesOf } from "@/lib/sportMatch";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { HScroll } from "@/ui/HScroll";
import { Img } from "@/ui/Img";
import { Modal, Switch, hhmm } from "@/ui/common";

type Tab = "results" | "fixtures" | "table";

function Crest({ src, name }: { src: string | null; name: string | null }) {
  return (
    <span className="crest" aria-hidden="true">
      {!src && <span className="ini">{(name?.match(/[\p{L}\p{N}]/gu) ?? []).slice(0, 2).join("").toUpperCase()}</span>}
      {src && <Img src={src} contain />}
    </span>
  );
}

const EMPTY: ReadonlySet<string> = new Set();

export function SportLeagues({ source, entries }: { source: Source; entries: SportEntry[] }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const profileId = usePrefs((s) => s.profileId);
  const rawPrefs = usePrefs((s) => s.sportLeagues[s.profileId] ?? "");
  const noSpoiler = usePrefs((s) => s.sportNoSpoiler[s.profileId] === true);
  const setPrefs = usePrefs((s) => s.set);
  const prefs = useMemo(() => decodePrefs(rawPrefs), [rawPrefs]);
  const visible = useMemo(() => visibleLeagues(prefs), [prefs]);
  const [chosen, setChosen] = useState<string | null>(null);
  const key = visible.find((l) => l.key === chosen)?.key ?? visible[0]?.key ?? null;
  const [tab, setTab] = useState<Tab>("results");
  const [customizing, setCustomizing] = useState(false);
  const [open, setOpen] = useState<LeagueMatch | null>(null);
  const [revealed, setRevealed] = useState<ReadonlySet<string>>(EMPTY);
  const [revealedTables, setRevealedTables] = useState<ReadonlySet<string>>(EMPTY);

  const data = useLeagueData(key);
  const matches = data.key === key ? data.matches : [];
  const status = data.key === key ? data.status : "loading";
  const tableOk = tab === "table" && !!key && showTable(noSpoiler, key, revealedTables);
  const table = useTable(key, tableOk);
  const now = Date.now();
  const live = useMemo(() => liveOf(matches), [matches]);
  const fixtures = useMemo(() => fixturesOf(matches, now), [matches, now]);
  const results = useMemo(() => resultsOf(matches), [matches]);
  const list = tab === "fixtures" ? fixtures : [...live, ...results];

  const writePrefs = (p: ReturnType<typeof decodePrefs>) => setPrefs({ sportLeagues: { ...usePrefs.getState().sportLeagues, [profileId]: encodePrefs(p) } });
  const setNoSpoiler = (on: boolean) => {
    setPrefs({ sportNoSpoiler: { ...usePrefs.getState().sportNoSpoiler, [profileId]: on } });
    if (on) { setRevealed(EMPTY); setRevealedTables(EMPTY); }
  };
  const unrevealed = noSpoiler && ((tab === "table" && key && !revealedTables.has(key)) || (tab === "results" && results.some((m) => !revealed.has(m.id))));
  const when = (ms: number | null) => (ms === null ? "" : `${new Date(ms).toLocaleDateString(lang, { weekday: "short", day: "numeric", month: "short" })} ${hhmm(ms, lang)}`);

  return (
    <div className="sp-lg">
      <div className="sp-lg-bar">
        <HScroll wheel follow={key} className="chips scroll sp-kinds" role="tablist">
          {visible.map((l) => <button key={l.key} className="chip" role="tab" aria-selected={key === l.key} onClick={() => { setChosen(l.key); setTab("results"); }}>{l.name}</button>)}
        </HScroll>
        <button className="btn sm" onClick={() => setCustomizing(true)}>{t("sport.lg.customize")}</button>
      </div>
      <div className="sp-lg-bar">
        <div className="chips sp-kinds" role="tablist">
          {(["results", "fixtures", "table"] as const).map((x) => (
            <button key={x} className="chip" role="tab" aria-selected={tab === x} onClick={() => setTab(x)}>
              {t(x === "results" ? "sport.lg.results" : x === "fixtures" ? "sport.lg.fixtures" : "sport.lg.table")}
            </button>
          ))}
        </div>
        {unrevealed && status === "ok" && (
          <button className="btn sm" onClick={() => (tab === "table" ? key && setRevealedTables(new Set([...revealedTables, key])) : setRevealed(new Set([...revealed, ...matches.map((m) => m.id)])))}>{t("sport.lg.showAll")}</button>
        )}
      </div>
      {data.stale && status === "ok" && <p className="sp-empty">{t("sport.lg.stale")}</p>}

      {status === "loading" && <div className="skeleton" style={{ height: 160 }} aria-label={t("common.loading")} />}
      {status === "unpaired" && <p className="sp-empty">{t("sport.lg.notPaired")}</p>}
      {status === "unavailable" && <p className="sp-empty">{t("sport.lg.unavailable")}</p>}
      {status === "ok" && tab !== "table" && (list.length === 0 ? <p className="sp-empty">{t("sport.lg.empty")}</p> : (
        <div className="sp-list" role="list">
          {list.map((m) => {
            const show = showScore(noSpoiler, m.id, revealed);
            return (
              <button key={m.id} className="sp-lg-row" role="listitem" onClick={() => setOpen(m)}>
                <span className="sp-lg-when">
                  <span>{when(m.start)}</span>
                  {m.state === "live" && <b className="sp-lg-live">{t("sport.lg.live").toUpperCase()}{show && m.progress ? ` ${m.progress}'` : ""}</b>}
                </span>
                {m.home && m.away ? (
                  <span className="sp-teams">
                    <span className="sp-team"><Crest src={m.homeBadge} name={m.home} /><b className="ellipsis">{m.home}</b>{show && m.homeScore !== null && <span className="sp-lg-score">{m.homeScore}</span>}</span>
                    <span className="sp-team"><Crest src={m.awayBadge} name={m.away} /><b className="ellipsis">{m.away}</b>{show && m.awayScore !== null && <span className="sp-lg-score">{m.awayScore}</span>}</span>
                  </span>
                ) : <b className="sp-title clamp-2">{matchTitle(m)}</b>}
                {!show && hasSpoiler(m) && <span className="sp-lg-hidden">{t("sport.lg.scoreHidden")}</span>}
              </button>
            );
          })}
        </div>
      ))}
      {status === "ok" && tab === "table" && (
        !key ? null : !showTable(noSpoiler, key, revealedTables) ? <p className="sp-empty">{t("sport.lg.scoreHidden")}</p>
          : table.failed ? <p className="sp-empty">{t("sport.lg.unavailable")}</p>
            : !table.rows ? <div className="skeleton" style={{ height: 160 }} aria-label={t("common.loading")} />
              : table.rows.length === 0 ? <p className="sp-empty">{t("sport.lg.empty")}</p> : <StandingsTable rows={table.rows} />
      )}

      {open && (
        <MatchModal
          source={source} m={matches.find((x) => x.id === open.id) ?? open} when={when} entries={entries}
          show={showScore(noSpoiler, open.id, revealed)} onReveal={() => setRevealed(new Set([...revealed, open.id]))} onClose={() => setOpen(null)}
        />
      )}
      {customizing && (
        <Modal title={t("sport.lg.customize")} onClose={() => setCustomizing(false)} foot={<button className="btn primary" onClick={() => setCustomizing(false)}>{t("sport.lg.done")}</button>}>
          <div className="sp-lg-pref">
            <span><b>{t("sport.lg.noSpoiler")}</b><span className="sp-empty">{t("sport.lg.noSpoilerD")}</span></span>
            <Switch on={noSpoiler} onChange={setNoSpoiler} label={t("sport.lg.noSpoiler")} />
          </div>
          {allLeagues(prefs).map((l) => {
            const hidden = prefs.hidden.includes(l.key);
            return (
              <div key={l.key} className="sp-lg-pref">
                <b style={{ opacity: hidden ? 0.5 : 1 }}>{l.name}</b>
                <span className="sp-acts">
                  <button className="btn sm" aria-label={t("sport.lg.up")} onClick={() => writePrefs(moveLeague(prefs, l.key, -1))}>↑</button>
                  <button className="btn sm" aria-label={t("sport.lg.down")} onClick={() => writePrefs(moveLeague(prefs, l.key, 1))}>↓</button>
                  <button className="btn sm" onClick={() => writePrefs(setLeagueHidden(prefs, l.key, !hidden))}>{hidden ? t("sport.lg.show") : t("sport.lg.hide")}</button>
                </span>
              </div>
            );
          })}
        </Modal>
      )}
    </div>
  );
}

function StandingsTable({ rows }: { rows: StandingRow[] }) {
  const t = useT();
  return (
    <table className="sp-lg-table">
      <thead><tr><th>#</th><th /><th className="num">{t("sport.lg.p")}</th><th className="num">{t("sport.lg.gd")}</th><th className="num">{t("sport.lg.pts")}</th></tr></thead>
      <tbody>
        {rows.map((r) => (
          <tr key={`${r.group ?? ""}-${r.rank}-${r.team}`}>
            <td className="mono">{r.rank ?? ""}</td>
            <td><span className="sp-team"><Crest src={r.badge} name={r.team} /><b className="ellipsis">{r.team}</b></span></td>
            <td className="num">{r.played ?? ""}</td>
            <td className="num">{r.goalDiff === null ? "" : r.goalDiff > 0 ? `+${r.goalDiff}` : r.goalDiff}</td>
            <td className="num"><b>{r.points ?? ""}</b></td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function MatchModal({ source, m, when, entries, show, onReveal, onClose }: {
  source: Source; m: LeagueMatch; when: (ms: number | null) => string; entries: SportEntry[]; show: boolean; onReveal: () => void; onClose: () => void;
}) {
  const t = useT();
  const { loading, sheet } = useSheet(m.id, m.state === "post");
  const channels = useMemo(() => channelsFor(m, entries), [m, entries]);
  const goals = show && sheet ? sheet.timeline.filter((x) => x.kind !== "sub").slice(0, 24) : [];
  const stats = show && sheet ? sheet.stats.slice(0, 12) : [];
  return (
    <Modal wide title={matchTitle(m)} onClose={onClose} foot={<button className="btn primary" onClick={onClose}>{t("sport.lg.done")}</button>}>
      <p className="sp-empty">{[when(m.start), sheet?.venue, m.round !== null ? t("sport.lg.round", { n: m.round }) : null].filter(Boolean).join(" · ")}</p>
      <div className="sp-lg-hero">
        <Crest src={m.homeBadge} name={m.home} />
        <b className="sp-lg-big">{m.homeScore !== null && m.awayScore !== null && show ? `${m.homeScore} – ${m.awayScore}` : hasSpoiler(m) ? "• – •" : "–"}</b>
        <Crest src={m.awayBadge} name={m.away} />
      </div>
      {!show && hasSpoiler(m) && <button className="btn" onClick={onReveal}>{t("sport.lg.showScore")}</button>}
      {goals.length > 0 && (
        <section>
          <h3 className="sp-lg-h">{t("sport.lg.moments")}</h3>
          {goals.map((g, i) => (
            <div key={i} className="sp-lg-ev">
              <span className="mono">{g.minute !== null ? `${g.minute}'` : ""}</span>
              <span aria-hidden="true">{g.kind === "goal" ? "⚽" : g.kind === "card" ? (/red/i.test(g.detail ?? "") ? "🟥" : "🟨") : "•"}</span>
              <b className="ellipsis">{[g.player, g.assist ? `(${g.assist})` : null].filter(Boolean).join(" ")}</b>
              <span className="sp-empty">{g.home ? m.home : m.away}</span>
            </div>
          ))}
        </section>
      )}
      {stats.length > 0 && (
        <section>
          <h3 className="sp-lg-h">{t("sport.lg.stats")}</h3>
          {stats.map((s) => <div key={s.label} className="sp-lg-stat"><b>{s.home}</b><span>{s.label}</span><b>{s.away}</b></div>)}
        </section>
      )}
      {show && loading && <p className="sp-empty">{t("common.loading")}</p>}
      {m.state !== "post" && (
        <section>
          <h3 className="sp-lg-h">{t("sport.lg.watch")}</h3>
          {channels.length === 0 ? <p className="sp-empty">{t("sport.lg.noChannel")}</p> : (
            <div className="sp-also-list">
              {channels.map((e) => (
                <button key={e.channel.streamId} className="sp-also-ch" onClick={() => { onClose(); usePlayer.getState().open(channelTarget(source, e.channel), "full", null); }}>
                  <span className="tx"><b className="ellipsis">{e.channel.num ? <span className="mono">{e.channel.num} · </span> : null}{e.channel.display}</b>{badgesOf(e.channel) ? <span className="sp-badges">{badgesOf(e.channel)}</span> : null}</span>
                </button>
              ))}
            </div>
          )}
        </section>
      )}
    </Modal>
  );
}
