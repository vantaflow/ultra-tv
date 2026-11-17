// Rubrique Sport (facultative, Réglages › Affichage) : accueil unifié (pastilles de ligues, « En direct », « À venir », classement court).
// Tout vient du guide et des catégories de la source locale (hooks/sport.ts) ; seuls les écussons d'équipes viennent du
// Worker, sur un appareil appairé (échec silencieux). Référence fonctionnelle : écran Sport d'Android TV.
import { memo, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { ChannelRow, Source } from "@/db/types";
import { useNow } from "@/hooks/misc";
import { useSportData, useTeamBadges } from "@/hooks/sport";
import { useT, type Key } from "@/i18n";
import { progId } from "@/lib/schedule";
import { alsoOnMap, badgesOf, collapseEvents, entryKey } from "@/lib/sportMatch";
import { filterUpcoming, liveCards, orderChannels, qualityOf, type LiveCard } from "@/lib/sportHub";
import { leagueOf, showScore } from "@/lib/sportLeagues";
import {
  dayOffset, liveNow, matchLineOf, playMinute, progress, sportKindOf, teamKey, teamNamesOf, upcoming,
  type SportEntry, type SportKind,
} from "@/lib/sport";
import { usePlayer } from "@/player/store";
import { channelTarget } from "@/player/targets";
import { usePrefs } from "@/state/prefs";
import { progRef } from "@/state/schedule";
import { useActiveSource } from "@/state/sources";
import { HScroll } from "@/ui/HScroll";
import { Icon } from "@/ui/Icon";
import { Img } from "@/ui/Img";
import { ProgActions, useRecordingIds, useReminderIds } from "@/ui/ProgActions";
import { StateCard, Switch, hhmm } from "@/ui/common";
import { NoSource } from "./states";
import { Crest, MatchSheet, MiniTable, StandingsScreen, useLeagueHub, type LeagueHub } from "./SportLeagues";

export const KIND_LABEL: Record<SportKind, Key> = {
  football: "sport.kind.football", basket: "sport.kind.basket", tennis: "sport.kind.tennis", combat: "sport.kind.combat", motor: "sport.kind.motor", rugby: "sport.kind.rugby",
};

export function Sport() {
  const source = useActiveSource();
  if (!source) return <NoSource />;
  return <SportInner source={source} />;
}

const initials = (s: string) => (s.match(/[\p{L}\p{N}]/gu) ?? []).slice(0, 2).join("").toUpperCase();

/** Écusson d'équipe (Worker, ou SVG fictif en démo), sinon initiales. */
export function TeamCrest({ name, badges }: { name: string; badges: Map<string, string> }) {
  const k = teamKey(name);
  const src = k ? badges.get(k) : undefined;
  return (
    <span className="crest" aria-hidden="true">
      {!src && <span className="ini">{initials(name)}</span>}
      {src && (src.startsWith("data:image/svg+xml") ? <img className="img contain" src={src} alt="" draggable={false} /> : <Img src={src} contain />)}
    </span>
  );
}

const play = (source: Source, c: ChannelRow) => usePlayer.getState().open(channelTarget(source, c), "full", null);

const NO_ALSO: SportEntry[] = [];

/** « Aussi sur : … » : les autres chaînes du même événement avec leurs badges (qualité, pays) ; c'est l'utilisateur qui choisit. */
function AlsoOnList({ source, others }: { source: Source; others: SportEntry[] }) {
  const t = useT();
  return (
    <div className="sp-also-list" role="group" aria-label={t("sport.alsoOn")}>
      {others.map((o) => {
        const c = o.channel;
        const badges = badgesOf(c);
        return (
          <button key={c.streamId} className="sp-also-ch" onClick={() => play(source, c)}>
            <span className="sp-chan-logo" aria-hidden="true"><span className="ini">{initials(c.display)}</span><Img src={c.logo} contain /></span>
            <span className="tx">
              <b className="ellipsis">{c.num ? <span className="mono">{c.num} · </span> : null}{c.display}</b>
              {badges ? <span className="sp-badges">{badges}</span> : null}
            </span>
            <Icon name="play" size={14} fill className="go" />
          </button>
        );
      })}
    </div>
  );
}

/** Bouton « Aussi sur N » (déplie la liste des autres chaînes). */
function AlsoOnToggle({ n, open, onToggle }: { n: number; open: boolean; onToggle: () => void }) {
  const t = useT();
  return <button className="btn sm" aria-expanded={open} onClick={onToggle}>{t("sport.alsoOnN", { n })}</button>;
}

/** Carte d'un direct + « Aussi sur » dessous (la carte est un bouton : la liste ne peut pas être imbriquée dedans). */
function LiveWithAlso({ source, e, now, badges, others }: { source: Source; e: SportEntry; now: number; badges: Map<string, string>; others: SportEntry[] }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="sp-live-wrap">
      <SportLiveCard source={source} e={e} now={now} badges={badges} />
      {others.length > 0 && <AlsoOnToggle n={others.length} open={open} onToggle={() => setOpen((v) => !v)} />}
      {open && others.length > 0 && <AlsoOnList source={source} others={others} />}
    </div>
  );
}

/** Carte d'un direct : compétition + minute, équipes (écussons), chaîne, progression. */
export const SportLiveCard = memo(function SportLiveCard({ source, e, now, badges }: { source: Source; e: SportEntry; now: number; badges: Map<string, string> }) {
  const t = useT();
  const lang = usePrefs((s) => s.lang);
  const line = matchLineOf(e.prog.title);
  const kind = sportKindOf(e.prog.title, e.prog.desc);
  const minute = playMinute(kind, e.prog.start, now);
  const comp = line.competition ?? (kind ? t(KIND_LABEL[kind]) : `${hhmm(e.prog.start, lang)} – ${hhmm(e.prog.end, lang)}`);
  const c = e.channel;
  return (
    <button className="sp-live" onClick={() => play(source, c)}>
      <span className="sp-live-top">
        <span className="sp-comp ellipsis">{comp}</span>
        <span className="sp-pill"><i />{minute != null ? `${minute}'` : t("common.live")}</span>
      </span>
      {line.home && line.away ? (
        <span className="sp-teams">
          <span className="sp-team"><TeamCrest name={line.home} badges={badges} /><b className="ellipsis">{line.home}</b></span>
          <span className="sp-team"><TeamCrest name={line.away} badges={badges} /><b className="ellipsis">{line.away}</b></span>
        </span>
      ) : <b className="sp-title clamp-2">{line.title}</b>}
      <span className="sp-chan">
        <span className="sp-chan-logo" aria-hidden="true"><span className="ini">{initials(c.display)}</span><Img src={c.logo} contain /></span>
        <span className="ellipsis"><span className="mono">{c.num || ""}</span>{c.num ? " · " : ""}{c.display}</span>
      </span>
      <span className="lc-trk"><i style={{ width: `${progress(e.prog.start, e.prog.end, now) * 100}%` }} /></span>
    </button>
  );
});

function dayLabel(t: ReturnType<typeof useT>, day: number, now: number, lang: string): string {
  const off = dayOffset(day, now);
  if (off === 0) return t("guide.today");
  if (off === 1) return t("guide.tomorrow");
  const s = new Date(day).toLocaleDateString(lang, { weekday: "short", day: "numeric" });
  return s.charAt(0).toLocaleUpperCase(lang) + s.slice(1);
}

/** Carte « En direct » de l'accueil unifié : ligue + minute, équipes et score, avancement, « Aussi sur N chaînes » et puces. */
function HubLiveCard({ card, hub, badges, onOpen }: { card: LiveCard; hub: LeagueHub; badges: Map<string, string>; onOpen: () => void }) {
  const t = useT();
  const show = !card.match || showScore(hub.noSpoiler, card.match.id, hub.revealed);
  const row = (name: string, badge: string | null | undefined, score: number | null) => (
    <span className="sh-trow">
      <Crest name={name} src={badge} badges={badges} size={46} />
      <b className="ellipsis">{name}</b>
      <span className="sc">{show && score !== null ? score : score !== null ? "•" : "–"}</span>
    </span>
  );
  return (
    <button className="sh-live" onClick={onOpen}>
      <span className="sh-live-top">
        <span className="ellipsis">{card.league}</span>
        <span className="sh-min"><i />{card.minute ?? t("common.live")}</span>
      </span>
      {card.home && card.away ? (
        <span className="sh-teams">{row(card.home, card.match?.homeBadge, card.homeScore)}{row(card.away, card.match?.awayBadge, card.awayScore)}</span>
      ) : <b className="sh-live-title clamp-2">{card.title}</b>}
      {card.progress !== null && <span className="sh-trk" aria-hidden="true"><i style={{ width: `${Math.max(2, card.progress * 100)}%` }} /></span>}
      {card.channels.length >= 2 && <span className="sh-also">{t("sport.alsoOnChan", { n: card.channels.length })}</span>}
      {card.channels.length > 0 && <span className="sh-chips"><Chips channels={card.channels} max={2} /></span>}
    </button>
  );
}

/** Puces « chaîne · qualité » : liseré doré (4K), bleu (HD) ou gris (SD / inconnu). */
function Chips({ channels, max }: { channels: SportEntry[]; max: number }) {
  const seen = new Set<string>();
  const shown = channels.filter((e) => { const k = `${e.channel.display}|${qualityOf(e.channel)}`; if (seen.has(k)) return false; seen.add(k); return true; }).slice(0, max);
  return <>{shown.map((e) => { const q = qualityOf(e.channel); return <span key={e.channel.streamId} className={`sh-chip q-${q || "x"}`}>{q ? `${e.channel.display} · ${q}` : e.channel.display}</span>; })}</>;
}

function SportInner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const lang = usePrefs((s) => s.lang);
  const now = useNow(30_000);
  const data = useSportData(source);
  const hub = useLeagueHub();
  const reminded = useReminderIds();
  const recorded = useRecordingIds();
  const entries = data?.entries ?? NO_ENTRIES;

  // Un même événement vu sur plusieurs chaînes (titres différents compris) : une carte, les autres chaînes en puces.
  const live = useMemo(() => (data ? collapseEvents(liveNow(data.entries, now)) : []), [data, now]);
  const days = useMemo(() => (data ? upcoming(data.entries, now).map((d) => ({ ...d, entries: collapseEvents(d.entries) })) : []), [data, now]);
  const also = useMemo(() => (data ? alsoOnMap(data.entries) : new Map<string, SportEntry[]>()), [data]);
  const league = hub.key ? leagueOf(hub.key) : undefined;
  const programme = t("sport.programme");
  const cards = useMemo(() => liveCards({
    leagueKey: hub.leagueKey, leagueName: league?.name, kind: leagueKind(hub.key), matches: hub.matches, guideLive: live, entries, alsoOn: also, now,
    programmeLabel: (e) => { const kd = sportKindOf(e.prog.title, e.prog.desc); return kd ? t(KIND_LABEL[kd]) : programme; },
  }), [hub.leagueKey, hub.key, league?.name, hub.matches, live, entries, also, now, programme, t]);
  const upDays = useMemo(() => filterUpcoming(hub.leagueKey, hub.matches, entries, days), [hub.leagueKey, hub.matches, entries, days]);
  const upShown = useMemo(() => upDays.flatMap((d) => d.entries.map((e) => ({ day: d.dayStart, e }))), [upDays]);
  const names = useMemo(() => teamNamesOf([...live, ...upShown.map((u) => u.e)]), [live, upShown]);
  const leagueNames = useMemo(() => [...hub.matches.flatMap((m) => [m.home, m.away]), ...(hub.table.rows ?? []).map((r) => r.team)].filter((n): n is string => !!n).slice(0, 40), [hub.matches, hub.table.rows]);
  const badges = useTeamBadges(useMemo(() => [...new Set([...names, ...leagueNames])].slice(0, 40), [names, leagueNames]));

  if (!source.cid) return <NoSource />;
  const open = (c: LiveCard) => {
    if (c.match) hub.setOpen(c.match);
    else if (c.channels[0]) play(source, c.channels[0].channel);
  };
  return (
    <div className="page sport sh">
      <header className="sh-head">
        <h1>{t("nav.sport")}</h1>
        <span className="sh-sub">{t("sport.subtitle")}</span>
        <label className="sh-spoil"><span>{t("sport.noSpoilerShort")}</span><Switch on={hub.noSpoiler} onChange={hub.setNoSpoiler} label={t("sport.noSpoilerShort")} /></label>
      </header>
      <div className="sh-pills" role="tablist" aria-label={t("sport.myLeagues")}>
        <HScroll wheel follow={hub.all ? "all" : hub.key} className="sh-pills-scroll">
          <button className="sh-pill" role="tab" aria-selected={hub.all} onClick={hub.selectAll}>{t("common.all")}</button>
          {hub.visible.map((l) => <button key={l.key} className="sh-pill" role="tab" aria-selected={!hub.all && hub.key === l.key} onClick={() => hub.select(l.key)}>{l.name}</button>)}
        </HScroll>
        <button className="sh-pill" onClick={() => hub.setScreen("mine")}>{t("sport.myLeagues")}</button>
      </div>
      {hub.stale && <p className="sp-empty">{t("sport.lg.stale")}</p>}

      {!data ? <div className="skeleton" style={{ height: 220 }} aria-label={t("common.loading")} /> : (
        <div className="sh-grid">
          <div className="sh-main">
            <section aria-label={t("sport.lg.live")}>
              <h2 className="sh-h"><i className="sh-dot" aria-hidden="true" />{t("sport.lg.live")}{cards.length > 0 && <span className="sh-count">{t("sport.liveCount", { n: cards.length })}</span>}</h2>
              {cards.length === 0 ? <p className="sp-empty">{t("sport.emptyLive")}</p> : (
                <div className="sh-cards">{cards.map((c) => <HubLiveCard key={c.key} card={c} hub={hub} badges={badges} onOpen={() => open(c)} />)}</div>
              )}
            </section>
            <section aria-label={t("sport.upcomingShort")}>
              <h2 className="sh-h">{t("sport.upcomingShort")}<span className="sh-count">{t("sport.upcomingSub")}</span></h2>
              {!data.hasGuide ? (
                <StateCard icon="trophy" title={t("sport.noGuideTitle")} body={t("sport.noGuideBody")} actions={<button className="btn primary" onClick={() => nav("/guide")}>{t("sport.openGuide")}</button>} />
              ) : upShown.length === 0 ? <p className="sp-empty">{t("sport.emptyUpcoming")}</p> : (
                <div className="sh-next" role="list">
                  {upShown.map(({ day, e }) => {
                    const line = matchLineOf(e.prog.title);
                    const kd = sportKindOf(e.prog.title, e.prog.desc);
                    const ref = progRef(source, e.channel, e.prog);
                    const id = progId(ref);
                    const chans = orderChannels([e, ...(also.get(entryKey(e)) ?? NO_ALSO)]);
                    return (
                      <div key={`u${e.channel.streamId}-${e.prog.start}`} className="sh-row" role="listitem">
                        <span className="sh-when"><b className="mono">{hhmm(e.prog.start, lang)}</b><span>{dayLabel(t, day, now, lang)}</span></span>
                        <span className="sh-what">
                          <b className="ellipsis">{line.home && line.away ? `${line.home} – ${line.away}` : line.title}</b>
                          <span className="ellipsis">{line.competition ?? (kd ? t(KIND_LABEL[kd]) : programme)}</span>
                        </span>
                        <span className="sh-chips"><Chips channels={chans} max={2} /></span>
                        <span className="sh-acts"><ProgActions source={source} prog={ref} now={now} reminded={reminded.has(id)} recorded={recorded.has(id)} /></span>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
          </div>
          <MiniTable hub={hub} badges={badges} />
        </div>
      )}
      {hub.open && <MatchSheet source={source} hub={hub} entries={entries} badges={badges} />}
      {hub.screen && <StandingsScreen hub={hub} badges={badges} />}
    </div>
  );
}

const NO_ENTRIES: SportEntry[] = [];
const leagueKind = (key: string | null): SportKind | null => (key ? (({ ligue1: "football", premier: "football", liga: "football", seriea: "football", bundesliga: "football", ucl: "football", botola: "football", nba: "basket", top14: "rugby", premrugby: "rugby", f1: "motor" }) as Record<string, SportKind>)[key] ?? null : null);

/** Rangée « Sport en direct » de l'accueil (rubrique activée et au moins un direct). */
export function SportHomeRow({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const now = useNow(30_000);
  const data = useSportData(source);
  const live = useMemo(() => (data ? collapseEvents(liveNow(data.entries, now, 12)) : []), [data, now]);
  const badges = useTeamBadges(useMemo(() => teamNamesOf(live), [live]));
  const also = useMemo(() => (data ? alsoOnMap(data.entries) : new Map<string, SportEntry[]>()), [data]);
  if (!live.length) return null;
  return (
    <section aria-label={t("sport.homeRow")}>
      <h2 className="section-title"><span><span className="sp-dot" aria-hidden="true" />{t("sport.homeRow")}</span><button className="btn sm" onClick={() => nav("/sport")}>{t("common.all")}</button></h2>
      <HScroll className="row-scroll">
        {live.map((e) => <LiveWithAlso key={`h${e.channel.streamId}`} source={source} e={e} now={now} badges={badges} others={also.get(entryKey(e)) ?? NO_ALSO} />)}
      </HScroll>
    </section>
  );
}
