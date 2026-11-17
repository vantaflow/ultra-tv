// Accueil Sport unifié, côté ligues : données (scores, classement, mode sans spoiler, ordre des ligues du profil), classement
// court, feuille de match et classement complet + « Mes ligues ». Référence : écran Sport d'Android TV (SportHub.kt).
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import type { Source } from "@/db/types";
import { useLeagueData, useSheet, useTable } from "@/hooks/sportLeagues";
import { useT } from "@/i18n";
import { crestColor, hasEurope, hasZones, initialsOf, orderChannels, parseForm, qualityOf, roundOf, sharePercent, zoneOf, type Zone } from "@/lib/sportHub";
import {
  allLeagues, channelsFor, decodePrefs, encodePrefs, hasSpoiler, leagueOf, matchTitle, moveLeague, setLeagueHidden, showScore, showTable, visibleLeagues,
  type LeagueMatch, type StandingRow,
} from "@/lib/sportLeagues";
import { teamKey, type SportEntry } from "@/lib/sport";
import { badgesOf } from "@/lib/sportMatch";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { Img } from "@/ui/Img";
import { Icon } from "@/ui/Icon";
import { Switch, hhmm } from "@/ui/common";

const EMPTY: ReadonlySet<string> = new Set();

/** Pastille d'équipe : écusson s'il est connu (match, classement ou Worker), sinon initiales sur fond coloré. */
export function Crest({ name, src, badges, size }: { name: string | null; src?: string | null; badges?: Map<string, string>; size?: number }) {
  const k = teamKey(name);
  const url = src ?? (k ? badges?.get(k) : undefined) ?? null;
  return (
    <span className="sh-crest" aria-hidden="true" style={{ background: url ? "transparent" : crestColor(name), ...(size ? { width: size, height: size, fontSize: size * 0.34 } : null) }}>
      {!url && initialsOf(name)}
      {url && (url.startsWith("data:image/svg+xml") ? <img className="img contain" src={url} alt="" draggable={false} /> : <Img src={url} contain />)}
    </span>
  );
}

/** État de l'espace ligues de l'accueil : ligue choisie, données, sans spoiler, feuille de match et écrans ouverts. */
export function useLeagueHub() {
  const profileId = usePrefs((s) => s.profileId);
  const rawPrefs = usePrefs((s) => s.sportLeagues[s.profileId] ?? "");
  const noSpoiler = usePrefs((s) => s.sportNoSpoiler[s.profileId] === true);
  const setPrefs = usePrefs((s) => s.set);
  const prefs = useMemo(() => decodePrefs(rawPrefs), [rawPrefs]);
  const visible = useMemo(() => visibleLeagues(prefs), [prefs]);
  const [all, setAll] = useState(true);
  const [chosen, setChosen] = useState<string | null>(null);
  const key = visible.find((l) => l.key === chosen)?.key ?? visible[0]?.key ?? null;
  const [revealed, setRevealed] = useState<ReadonlySet<string>>(EMPTY);
  const [revealedTables, setRevealedTables] = useState<ReadonlySet<string>>(EMPTY);
  const [open, setOpen] = useState<LeagueMatch | null>(null);
  const [screen, setScreen] = useState<null | "table" | "mine">(null);

  const data = useLeagueData(key);
  const matches = useMemo(() => (data.key === key && data.status === "ok" ? data.matches : []), [data, key]);
  const status = data.key === key ? data.status : "loading";
  const tableShown = !!key && showTable(noSpoiler, key, revealedTables);
  const table = useTable(key, tableShown);

  const writePrefs = (p: ReturnType<typeof decodePrefs>) => setPrefs({ sportLeagues: { ...usePrefs.getState().sportLeagues, [profileId]: encodePrefs(p) } });
  const setNoSpoiler = (on: boolean) => {
    setPrefs({ sportNoSpoiler: { ...usePrefs.getState().sportNoSpoiler, [profileId]: on } });
    if (on) { setRevealed(EMPTY); setRevealedTables(EMPTY); }
  };
  return {
    prefs, visible, all, key, leagueKey: all ? null : key, noSpoiler, setNoSpoiler, matches, status, stale: data.stale, table, tableShown, revealed, open, screen,
    select: (k: string) => { setAll(false); setChosen(k); },
    selectAll: () => setAll(true),
    reveal: (id: string) => setRevealed(new Set([...revealed, id])),
    revealTable: () => key && setRevealedTables(new Set([...revealedTables, key])),
    setOpen, setScreen,
    move: (k: string, d: number) => writePrefs(moveLeague(prefs, k, d)),
    setHidden: (k: string, hide: boolean) => writePrefs(setLeagueHidden(prefs, k, hide)),
  };
}
export type LeagueHub = ReturnType<typeof useLeagueHub>;

// ───────────── classement court (colonne de droite) ─────────────

function ZoneBar({ zone }: { zone: Zone }) { return <span className={`sh-zone z-${zone}`} aria-hidden="true" />; }

/** Classement court de la ligue choisie (8 lignes), zone qualifiée marquée ; sans spoiler : masqué, avec un bouton pour l'afficher. */
export function MiniTable({ hub, badges }: { hub: LeagueHub; badges: Map<string, string> }) {
  const t = useT();
  const key = hub.key;
  const league = key ? leagueOf(key) : undefined;
  const rows = hub.table.key === key ? hub.table.rows : null;
  const round = rows ? roundOf(rows) : null;
  const grouped = !!rows?.some((r) => r.group);
  return (
    <aside className="sh-aside" aria-labelledby="sh-table-h">
      <div className="sh-aside-head">
        <h2 id="sh-table-h">{t("sport.lg.table")}</h2>
        {league && <span>{[league.name, round !== null ? t("sport.lg.round", { n: round }) : null].filter(Boolean).join(" · ")}</span>}
      </div>
      {!key ? <p className="sp-empty">{t("sport.lg.empty")}</p>
        : !hub.tableShown ? (
          <>
            <p className="sp-empty">{t("sport.tableHidden")}</p>
            <button className="btn" onClick={hub.revealTable}>{t("sport.lg.showAll")}</button>
          </>
        )
          : hub.status === "unpaired" ? <p className="sp-empty">{t("sport.lg.notPaired")}</p>
            : hub.status === "unavailable" || hub.table.failed ? <p className="sp-empty">{t("sport.lg.unavailable")}</p>
              : !rows ? <div className="skeleton" style={{ height: 220 }} aria-label={t("common.loading")} />
                : rows.length === 0 ? <p className="sp-empty">{t("sport.lg.empty")}</p> : (
                  <table className="sh-mini" aria-label={t("sport.lg.table")}>
                    <thead><tr><th>#</th><th>{t("sport.tbl.team")}</th><th className="num">{t("sport.lg.p")}</th><th className="num">{t("sport.lg.gd")}</th><th className="num">{t("sport.lg.pts")}</th></tr></thead>
                    <tbody>
                      {rows.slice(0, 8).map((r) => (
                        <tr key={`${r.group ?? ""}-${r.rank}-${r.team}`}>
                          <td><ZoneBar zone={zoneOf(key, r.rank, rows.length, grouped)} />{r.rank ?? ""}</td>
                          <td><span className="sh-team"><Crest name={r.team} src={r.badge} badges={badges} size={34} /><span className="ellipsis">{r.team}</span></span></td>
                          <td className="num">{r.played ?? ""}</td>
                          <td className="num">{gd(r.goalDiff)}</td>
                          <td className="num"><b>{r.points ?? ""}</b></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
      {hub.tableShown && rows && rows.length > 0 && <button className="btn" onClick={() => hub.setScreen("table")}>{t("sport.fullTable")}</button>}
      <p className="sh-hint">{t("sport.okHint")}</p>
    </aside>
  );
}

const gd = (v: number | null): string => (v === null ? "" : v > 0 ? `+${v}` : v < 0 ? `−${-v}` : "0");

// ───────────── écrans plein cadre ─────────────

/** Écran plein cadre (feuille de match, classement) : Échap ou « Retour » ferment ; le focus part dans l'écran. */
function Overlay({ label, onClose, children }: { label: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const t = useT();
  useEffect(() => {
    const k = (e: KeyboardEvent) => { if (e.key === "Escape") { e.stopPropagation(); onClose(); } };
    window.addEventListener("keydown", k, true);
    const el = ref.current;
    el?.querySelector<HTMLElement>("[data-autofocus],button")?.focus();
    return () => window.removeEventListener("keydown", k, true);
  }, [onClose]);
  return (
    <div className="sh-overlay" role="dialog" aria-modal="true" aria-label={label} ref={ref}>
      <div className="sh-overlay-in">
        <div className="sh-top"><button className="btn sm" onClick={onClose}><Icon name="back" size={16} />{t("sport.back")}</button></div>
        {children}
      </div>
    </div>
  );
}

/** Feuille de match : score en grand, temps forts, statistiques en barres, chaînes de l'utilisateur (meilleure qualité d'abord). */
export function MatchSheet({ source, hub, entries, badges }: { source: Source; hub: LeagueHub; entries: SportEntry[]; badges: Map<string, string> }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const opened = hub.open!;
  const m = hub.matches.find((x) => x.id === opened.id) ?? opened;
  const show = showScore(hub.noSpoiler, m.id, hub.revealed);
  const { loading, sheet } = useSheet(m.id, m.state === "post");
  const channels = useMemo(() => orderChannels(channelsFor(m, entries)), [m, entries]);
  const { setOpen } = hub;
  const close = useCallback(() => setOpen(null), [setOpen]);
  const events = show && sheet ? sheet.timeline.filter((x) => x.kind !== "sub").slice(0, 24) : [];
  const stats = show && sheet ? sheet.stats.slice(0, 8) : [];
  const when = m.start === null ? "" : `${new Date(m.start).toLocaleDateString(lang, { weekday: "short", day: "numeric", month: "short" })} ${hhmm(m.start, lang)}`;
  const spoiler = hasSpoiler(m);
  return (
    <Overlay label={matchTitle(m)} onClose={close}>
      <div className="sh-meta">
        <span>{[m.round !== null ? t("sport.lg.round", { n: m.round }) : null, sheet?.venue, m.state === "live" ? null : when].filter(Boolean).join(" · ")}</span>
        {m.state === "live" && <b className="sh-livetag"><i />{[t("sport.lg.live"), show && m.progress ? `${m.progress}'` : null].filter(Boolean).join(" · ")}</b>}
      </div>
      <section className="sh-score" aria-label={t("sport.lg.live")}>
        {m.home && m.away ? (
          <>
            <div className="sh-side end"><b>{m.home}</b><Crest name={m.home} src={m.homeBadge} badges={badges} size={104} /></div>
            {m.homeScore !== null && m.awayScore !== null && show ? <div className="sh-big">{m.homeScore} <span>–</span> {m.awayScore}</div>
              : spoiler ? <button className="btn sh-reveal" data-autofocus onClick={() => hub.reveal(m.id)}>{t("sport.lg.showScore")}</button>
                : <div className="sh-big mute">–</div>}
            <div className="sh-side"><Crest name={m.away} src={m.awayBadge} badges={badges} size={104} /><b>{m.away}</b></div>
          </>
        ) : <b className="sh-side">{matchTitle(m)}</b>}
      </section>
      <div className="sh-cols">
        <section className="sh-panel" aria-labelledby="sh-ev">
          <h2 id="sh-ev">{t("sport.lg.moments")}</h2>
          {!show ? <p className="sp-empty">{t("sport.eventsHidden")}</p> : loading && events.length === 0 ? <div className="skeleton" style={{ height: 120 }} aria-label={t("common.loading")} />
            : events.length === 0 ? <p className="sp-empty">{t("sport.noEvents")}</p> : (
              <ul className="sh-events">
                {events.map((g, i) => (
                  <li key={i}>
                    <span className="mono">{g.minute !== null ? `${g.minute}'` : ""}</span>
                    <i className={g.kind === "goal" ? "goal" : g.kind === "card" ? (/red/i.test(g.detail ?? "") ? "red" : "yellow") : "other"} aria-hidden="true" />
                    <span><b>{[g.player, g.assist ? `(${g.assist})` : null].filter(Boolean).join(" ")}</b> <span className="mute">· {g.home ? m.home : m.away}</span></span>
                  </li>
                ))}
              </ul>
            )}
        </section>
        <section className="sh-panel" aria-labelledby="sh-st">
          <h2 id="sh-st">{t("sport.lg.stats")}</h2>
          {!show ? <p className="sp-empty">{t("sport.eventsHidden")}</p> : loading && stats.length === 0 ? <div className="skeleton" style={{ height: 120 }} aria-label={t("common.loading")} />
            : stats.length === 0 ? <p className="sp-empty">{t("sport.noEvents")}</p> : stats.map((s) => {
              const pa = sharePercent(s.home, s.away);
              return (
                <div key={s.label} className="sh-stat">
                  <div><b>{s.home}</b><span>{s.label}</span><b>{s.away}</b></div>
                  <div className="sh-bars" aria-hidden="true"><i style={{ flex: Math.max(1, pa) }} /><i className="b" style={{ flex: Math.max(1, 100 - pa) }} /></div>
                </div>
              );
            })}
        </section>
        <section className="sh-panel" aria-labelledby="sh-ch">
          <h2 id="sh-ch">{t("sport.lg.watch")}</h2>
          {m.state === "post" || channels.length === 0 ? <p className="sp-empty">{t("sport.lg.noChannel")}</p> : (
            <div className="sh-chlist">
              {channels.map((e) => {
                const q = qualityOf(e.channel);
                return (
                  <button key={e.channel.streamId} className="sh-ch" onClick={() => { close(); usePlayer.getState().open(channelTarget(source, e.channel), "full", null); }}>
                    <span className="logo" aria-hidden="true"><span className="ini">{initialsOf(e.channel.display)}</span><Img src={e.channel.logo} contain /></span>
                    <span className="tx"><b className="ellipsis">{e.channel.display}</b><span className="ellipsis">{[badgesOf(e.channel), e.channel.num ? `n° ${e.channel.num}` : null].filter(Boolean).join(" · ")}</span></span>
                    {q && <span className={`sh-q q-${q}`}>{q}</span>}
                  </button>
                );
              })}
            </div>
          )}
          <p className="sh-hint">{t("sport.watchHint")}</p>
        </section>
      </div>
    </Overlay>
  );
}

/** Classement complet (J, G, N, P, +/−, forme, points, zones, légende) et panneau « Mes ligues » + mode sans spoiler. */
export function StandingsScreen({ hub, badges }: { hub: LeagueHub; badges: Map<string, string> }) {
  const t = useT();
  const key = hub.key;
  const league = key ? leagueOf(key) : undefined;
  const rows = hub.table.key === key ? hub.table.rows : null;
  const round = rows ? roundOf(rows) : null;
  const grouped = !!rows?.some((r) => r.group);
  const { setScreen } = hub;
  const close = useCallback(() => setScreen(null), [setScreen]);
  return (
    <Overlay label={t("sport.lg.table")} onClose={close}>
      <div className="sh-std">
        <section aria-labelledby="sh-std-h" className="sh-std-main">
          <div className="sh-std-head">
            <h1 id="sh-std-h">{t("sport.lg.table")}</h1>
            {league && <span className="sh-lg-pill">{league.name}</span>}
            {round !== null && <span className="mute">{t("sport.lg.round", { n: round })}</span>}
          </div>
          {!key || (rows && rows.length === 0) ? <p className="sp-empty">{t("sport.lg.empty")}</p>
            : !hub.tableShown ? <><p className="sp-empty">{t("sport.tableHidden")}</p><button className="btn" onClick={hub.revealTable}>{t("sport.lg.showAll")}</button></>
              : !rows ? <div className="skeleton" style={{ height: 240 }} aria-label={t("common.loading")} /> : (
                <div className="sh-std-card">
                  <table className="sh-full" aria-label={t("sport.lg.table")}>
                    <thead><tr><th>#</th><th>{t("sport.tbl.team")}</th><th className="num">{t("sport.lg.p")}</th><th className="num w">{t("sport.tbl.w")}</th><th className="num w">{t("sport.tbl.d")}</th><th className="num w">{t("sport.tbl.l")}</th><th className="num">{t("sport.lg.gd")}</th><th className="form">{t("sport.tbl.form")}</th><th className="num">{t("sport.lg.pts")}</th></tr></thead>
                    <tbody>
                      {rows.map((r: StandingRow) => (
                        <tr key={`${r.group ?? ""}-${r.rank}-${r.team}`} tabIndex={0}>
                          <td><ZoneBar zone={zoneOf(key, r.rank, rows.length, grouped)} />{r.rank ?? ""}</td>
                          <td><span className="sh-team"><Crest name={r.team} src={r.badge} badges={badges} size={38} /><b className="ellipsis">{r.team}</b></span></td>
                          <td className="num">{r.played ?? ""}</td><td className="num w">{r.win ?? ""}</td><td className="num w">{r.draw ?? ""}</td><td className="num w">{r.loss ?? ""}</td>
                          <td className="num">{gd(r.goalDiff)}</td>
                          <td className="form"><span className="sh-form">{parseForm(r.form).map((c, i) => <i key={i} className={`f-${c}`}>{c}</i>)}</span></td>
                          <td className="num"><b>{r.points ?? ""}</b></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
          {hasZones(key) && (
            <div className="sh-legend">
              {hasEurope(key) && <><span><i className="z-qualified" />{t("sport.zone.ucl")}</span><span><i className="z-europa" />{t("sport.zone.uel")}</span></>}
              <span><i className="z-relegation" />{t("sport.zone.rel")}</span>
            </div>
          )}
        </section>
        <aside aria-labelledby="sh-my-h" className="sh-panel sh-my">
          <h2 id="sh-my-h">{t("sport.myLeagues")}</h2>
          <p className="sh-hint">{t("sport.myLeaguesHint")}</p>
          <div className="sh-my-list">
            {allLeagues(hub.prefs).map((l, i) => {
              const hidden = hub.prefs.hidden.includes(l.key);
              return (
                <div key={l.key} className={`sh-my-row${hidden ? " off" : ""}`}>
                  <button className="main" data-autofocus={i === 0 || undefined} onClick={() => hub.setHidden(l.key, !hidden)} aria-pressed={!hidden}>
                    <Icon name="list" size={20} /><span className="ellipsis">{l.name}</span><span className="st">{hidden ? t("sport.hidden") : t("sport.shown")}</span>
                  </button>
                  <button className="btn sm" aria-label={t("sport.lg.up")} onClick={() => hub.move(l.key, -1)}>↑</button>
                  <button className="btn sm" aria-label={t("sport.lg.down")} onClick={() => hub.move(l.key, 1)}>↓</button>
                </div>
              );
            })}
          </div>
          <div className="sh-pref">
            <span><b>{t("sport.lg.noSpoiler")}</b><span className="mute">{t("sport.lg.noSpoilerD")}</span></span>
            <Switch on={hub.noSpoiler} onChange={hub.setNoSpoiler} label={t("sport.lg.noSpoiler")} />
          </div>
        </aside>
      </div>
    </Overlay>
  );
}
