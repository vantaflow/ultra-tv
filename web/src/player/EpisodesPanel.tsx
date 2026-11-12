// Panneau « Épisodes » du lecteur (séries) : saisons et épisodes de la série en cours, épisode joué mis en évidence,
// progression locale et « Vu » (Trakt compris). Clic ou Entrée : lecture de l'épisode (reprise si commencé).
// Clavier (géré par PlayerHost) : ↑ ↓ épisodes, ← → saisons, Échap ferme.
import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useRef, useState } from "react";
import { db } from "@/db/db";
import { useT } from "@/i18n";
import { episodePanel } from "@/lib/episodePanel";
import { fmtDuration } from "@/lib/text";
import type { SeriesInfo } from "@/net/xtream";
import { parseEpisodes } from "@/screens/Detail";
import { usePrefs } from "@/state/prefs";
import { useWatchedEpisodes } from "@/trakt/hooks";
import { Icon } from "@/ui/Icon";
import { Img } from "@/ui/Img";
import type { PlayTarget } from "./resolve";
import { usePlayer } from "./store";

export function EpisodesPanel({ target, onClose }: { target: PlayTarget; onClose: () => void }) {
  const t = useT();
  const profileId = usePrefs((s) => s.profileId);
  const sid = target.seriesId;
  const info = useLiveQuery(async () => (sid == null ? null : ((await db.details.get(`series:${target.sourceId}:${sid}`))?.json as SeriesInfo | undefined) ?? null), [target.sourceId, sid]);
  const hist = useLiveQuery(() => db.history.where("[profile+sourceId]").equals([profileId, target.sourceId]).filter((h) => h.seriesId === sid).toArray(), [profileId, target.sourceId, sid]) ?? [];
  const seen = useWatchedEpisodes(target.title);
  const data = useMemo(() => episodePanel(parseEpisodes(info ?? null).seasons, { currentId: target.refId, history: hist, seen }), [info, hist, seen, target.refId]);
  const [season, setSeason] = useState<number | null>(null);
  const cur = season ?? data.currentSeason;
  const items = data.seasons.find((s) => s.n === cur)?.items ?? [];
  const listRef = useRef<HTMLDivElement>(null);

  // Ouverture : focus sur l'épisode joué (sinon le premier) ; changement de saison : premier épisode.
  const focused = useRef<number | null>(null);
  useEffect(() => {
    if (!items.length || focused.current === cur) return;
    focused.current = cur;
    const el = listRef.current?.querySelector<HTMLButtonElement>("button[data-cur]") ?? listRef.current?.querySelector<HTMLButtonElement>("button");
    el?.focus({ preventScroll: true });
    el?.scrollIntoView({ block: "center" });
  }, [items.length, cur]);

  const play = (it: (typeof items)[number]) => {
    const e = it.ep;
    if (it.current) { onClose(); return; }
    usePlayer.getState().open({
      ...target, refId: e.id, ext: e.ext, season: e.season, episode: e.num, startAt: it.resumeAt, replay: undefined,
      subtitle: `${t("common.season", { n: e.season })} · ${t("common.episodeShort", { n: e.num })} ${e.title}`.trim(),
    }, "full");
  };

  return (
    <aside className="pside pep" aria-label={t("player.episodes")} onClick={(e) => e.stopPropagation()}>
      <div className="pep-head">
        <h3 className="ellipsis">{t("player.episodes")}<span className="pep-series"> · {target.title}</span></h3>
        <button className="pbtn" aria-label={t("common.close")} onClick={onClose}><Icon name="x" size={20} /></button>
      </div>
      {data.seasons.length > 1 && (
        <div className="tabs pep-seasons" role="tablist" aria-label={t("player.episodes")}>
          {data.seasons.map((s) => (
            <button key={s.n} role="tab" aria-selected={s.n === cur} tabIndex={s.n === cur ? 0 : -1} onClick={() => setSeason(s.n)}>{t("common.season", { n: s.n })}</button>
          ))}
        </div>
      )}
      {info === undefined ? <div className="muted">{t("common.loading")}</div> : items.length === 0 ? <div className="muted">{t("vod.noEpisodes")}</div> : (
        <div className="pep-list" ref={listRef} role="list">
          {items.map((it) => (
            <button key={it.ep.id} className={`pep-ep${it.current ? " cur" : ""}`} data-cur={it.current ? "1" : undefined} aria-current={it.current ? "true" : undefined} onClick={() => play(it)}>
              <span className="th">
                <Img src={it.ep.img ?? target.image} />
                <span className="n mono">{t("common.episodeShort", { n: it.ep.num })}</span>
                {it.ratio > 0 && !it.watched && <span className="progress"><i style={{ width: `${it.ratio * 100}%` }} /></span>}
              </span>
              <span className="tx">
                <b className="clamp-2">{it.ep.title || t("common.episodeShort", { n: it.ep.num })}</b>
                <span className="m">
                  {it.current ? <span className="tag cur">{t("player.epPlaying")}</span> : it.watched ? <span className="tag"><Icon name="check" size={12} stroke={3} />{t("player.epWatched")}</span> : null}
                  {it.ep.secs > 0 && <span>{fmtDuration(it.ep.secs)}</span>}
                </span>
              </span>
            </button>
          ))}
        </div>
      )}
    </aside>
  );
}
