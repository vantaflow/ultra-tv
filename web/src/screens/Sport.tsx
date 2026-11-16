// Rubrique Sport (facultative, Réglages › Affichage) : « En direct maintenant », « À venir · 48 h », « Chaînes sport ».
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
import {
  dayOffset, liveNow, matchLineOf, ofKind, playMinute, presentKinds, progress, sportKindOf, teamKey, teamNamesOf, upcoming,
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
import { StateCard, hhmm } from "@/ui/common";
import { NoSource } from "./states";

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

function SportInner({ source }: { source: Source }) {
  const t = useT();
  const nav = useNavigate();
  const lang = usePrefs((s) => s.lang);
  const now = useNow(30_000);
  const data = useSportData(source);
  const [kind, setKind] = useState<SportKind | null>(null);
  const reminded = useReminderIds();
  const recorded = useRecordingIds();

  // Un même événement vu sur plusieurs chaînes (titres différents compris) : une carte, les autres chaînes dans « Aussi sur ».
  const live = useMemo(() => (data ? collapseEvents(liveNow(data.entries, now)) : []), [data, now]);
  const days = useMemo(() => (data ? upcoming(data.entries, now).map((d) => ({ ...d, entries: collapseEvents(d.entries) })) : []), [data, now]);
  const also = useMemo(() => (data ? alsoOnMap(data.entries) : new Map<string, SportEntry[]>()), [data]);
  const [openAlso, setOpenAlso] = useState<string | null>(null);
  const kinds = useMemo(() => presentKinds([...live, ...days.flatMap((d) => d.entries)]), [live, days]);
  const k = kind && kinds.includes(kind) ? kind : null;
  const liveShown = useMemo(() => ofKind(live, k), [live, k]);
  const upShown = useMemo(() => days.flatMap((d) => ofKind(d.entries, k).map((e) => ({ day: d.dayStart, e }))), [days, k]);
  const nowByChannel = useMemo(() => new Map(live.map((e) => [e.channel.streamId, e.prog.title])), [live]);
  const badges = useTeamBadges(useMemo(() => teamNamesOf([...liveShown, ...upShown.map((u) => u.e)]), [liveShown, upShown]));

  if (!source.cid) return <NoSource />;
  return (
    <div className="page sport">
      <header className="sp-head">
        <h1>{t("nav.sport")}</h1>
        {data?.hasGuide && kinds.length > 0 && (
          <HScroll wheel follow={k} className="chips scroll sp-kinds" role="tablist" aria-label={t("sport.disciplines")}>
            <button className="chip" role="tab" aria-selected={k === null} onClick={() => setKind(null)}>{t("common.all")}</button>
            {kinds.map((x) => <button key={x} className="chip" role="tab" aria-selected={k === x} onClick={() => setKind(x)}>{t(KIND_LABEL[x])}</button>)}
          </HScroll>
        )}
        {data?.hasGuide && <span className="sp-from">{t("sport.fromGuide")}</span>}
      </header>

      {!data ? <div className="skeleton" style={{ height: 220 }} aria-label={t("common.loading")} /> : (
        <>
          {!data.hasGuide ? (
            <StateCard icon="trophy" title={t("sport.noGuideTitle")} body={t("sport.noGuideBody")} actions={<button className="btn primary" onClick={() => nav("/guide")}>{t("sport.openGuide")}</button>} />
          ) : (
            <section aria-label={t("sport.liveNow")}>
              <h2 className="section-title sp-sec"><span><span className="sp-dot" aria-hidden="true" />{t("sport.liveNow")}{liveShown.length > 0 && <span className="sp-count"> · {liveShown.length}</span>}</span></h2>
              {liveShown.length === 0 ? <p className="sp-empty">{t("sport.emptyLive")}</p> : (
                <HScroll className="row-scroll">
                  {liveShown.map((e) => <LiveWithAlso key={`l${e.channel.streamId}`} source={source} e={e} now={now} badges={badges} others={also.get(entryKey(e)) ?? NO_ALSO} />)}
                </HScroll>
              )}
            </section>
          )}

          <div className="sp-cols">
            {data.hasGuide && (
              <section className="sp-up" aria-label={t("sport.upcoming")}>
                <h2 className="section-title">{t("sport.upcoming")}</h2>
                {upShown.length === 0 ? <p className="sp-empty">{t("sport.emptyUpcoming")}</p> : (
                  <div className="sp-list" role="list">
                    {upShown.map(({ day, e }) => {
                      const line = matchLineOf(e.prog.title);
                      const kd = sportKindOf(e.prog.title, e.prog.desc);
                      const ref = progRef(source, e.channel, e.prog);
                      const id = progId(ref);
                      const ek = entryKey(e);
                      const others = also.get(ek) ?? NO_ALSO;
                      return (
                        <div key={`u${e.channel.streamId}-${e.prog.start}`} className="sp-row" role="listitem">
                          <span className="sp-day">{dayLabel(t, day, now, lang)}</span>
                          <span className="sp-time mono">{hhmm(e.prog.start, lang)}</span>
                          <span className="sp-what">
                            <b className="ellipsis">{line.home && line.away ? `${line.home} – ${line.away}` : line.title}</b>
                            <span className="ellipsis">{[line.competition ?? (kd ? t(KIND_LABEL[kd]) : null), e.channel.display].filter(Boolean).join(" · ")}</span>
                          </span>
                          <span className="sp-acts">
                            {others.length > 0 && <AlsoOnToggle n={others.length} open={openAlso === ek} onToggle={() => setOpenAlso(openAlso === ek ? null : ek)} />}
                            <ProgActions source={source} prog={ref} now={now} reminded={reminded.has(id)} recorded={recorded.has(id)} />
                          </span>
                          {openAlso === ek && others.length > 0 && <div className="sp-also"><AlsoOnList source={source} others={others} /></div>}
                        </div>
                      );
                    })}
                  </div>
                )}
              </section>
            )}
            <section className="sp-chans" aria-label={t("sport.channels")}>
              <h2 className="section-title">{t("sport.channels")}</h2>
              {data.channels.length === 0 ? <p className="sp-empty">{t("sport.noChannels")}</p> : (
                <div className="sp-grid">
                  {data.channels.map((c) => (
                    <button key={c.streamId} className="sp-ch" onClick={() => play(source, c)}>
                      <span className="sp-chan-logo" aria-hidden="true"><span className="ini">{initials(c.display)}</span><Img src={c.logo} contain /></span>
                      <span className="tx">
                        <b className="ellipsis">{c.num ? <span className="mono">{c.num} · </span> : null}{c.display}</b>
                        <span className="ellipsis">{nowByChannel.get(c.streamId) ?? ""}</span>
                      </span>
                      <Icon name="play" size={16} fill className="go" />
                    </button>
                  ))}
                </div>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}

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
