// Lecteur unique de l'application : un seul <video> et un seul moteur, donc UNE connexion au fournisseur
// (la plupart des abonnements n'en autorisent qu'une). Il s'affiche soit dans l'aperçu du Direct
// (mode inline, calé sur un emplacement), soit en plein écran (mode full) avec la surcouche de la maquette.

import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { orderedList } from "@/db/channelOrder";
import { FAVORITES_SCOPE } from "@/lib/channelOrder";
import { channelsCol, favKey, nowNext, programsFor, toggleFavorite } from "@/db/queries";
import { qualityLabel } from "@/lib/channelName";
import { ribbonSegments, type RibbonSeg } from "./ribbon";
import { Img } from "@/ui/Img";
import { VList, arrayRows, type VListHandle } from "@/ui/Virtual";
import { db } from "@/db/db";
import type { ChannelRow } from "@/db/types";
import { histKey } from "@/db/queries";
import { useNowNext } from "@/hooks/epg";
import { useCategories, useFavorites, useHiddenCats } from "@/hooks/data";
import { useT } from "@/i18n";
import { fmtClock, fmtDuration } from "@/lib/text";
import { COMMIT_MS, fmtDelta, press as scrubPressState, release as scrubRelease, type ScrubState } from "@/lib/seekScrub";
import { bridge, currentTransport, requestHeaders, transportSync, wrapUrl } from "@/net/transport";
import { handshake } from "@/net/xtream";
import { credsOf } from "@/sync/core";
import { connCounts, isConnectionLimit, limitCounts, probeFailure } from "./connectionLimit";
import { slotConfig } from "./connectionSlot";
import { usePrefs } from "@/state/prefs";
import { useSources } from "@/state/sources";
import { hhmm, QBadge } from "@/ui/common";
import { Icon } from "@/ui/Icon";
import { createOsdIdle } from "./osdIdle";
import {
  appendDigit, backEffect, decide, keyOf, KEYBOARD_EXTRA_MS, navigate, NUMBER_ENTRY_MS, OSD_BANNER_HIDE_MS, pushRecent,
  recentsToShow, type OsdStage, type RecentChannel,
} from "./liveOsd";
import { parseEpisodes } from "@/screens/Detail";
import type { SeriesInfo } from "@/net/xtream";
import { LiveReconnect } from "./reconnect";
import { effectivePreferred } from "./trackChoice";
import { PlayerEngine, type PlayState, type Stats, type Tracks } from "./engine";
import { stalkerCandidate, candidates, type Candidate } from "./resolve";
import { STALKER_UA } from "@/net/stalker";
import { AudioWatcher, nextAudioFallback, readAudioSample, revertToSilent } from "./audio";
import { usePlayer, ZAP_DEBOUNCE_MS } from "./store";
import { startScrobble, type Scrobbler } from "./trakt";
import { loadSkips, skipOfferAt, type SkipSegment } from "./skip";
import { clampSeek, TX_LABEL, txClock, txUrl, type TxState } from "./transcode";
import type { PlayTarget } from "./resolve";
import { EpisodesPanel } from "./EpisodesPanel";
import { stepIndex } from "@/lib/episodePanel";

/** HDR annoncé dans le nom de la chaîne (aucune autre source d'information côté Direct). */
const HDR_RE = /\b(HDR10\+?|HDR|HLG|DOLBY ?VISION)\b/i;

const NO_TRACKS: Tracks = { audio: [], activeAudio: -1, text: [], activeText: -1, levels: [], activeLevel: -1 };

async function setOsFullscreen(on: boolean) {
  const b = bridge();
  try {
    if (b?.setFullscreen) await b.setFullscreen(on);
    else if (on && !document.fullscreenElement) await document.documentElement.requestFullscreen();
    else if (!on && document.fullscreenElement) await document.exitFullscreen();
  } catch { /* refusé */ }
}
async function isOsFullscreen(): Promise<boolean> {
  const b = bridge();
  if (b?.isFullscreen) return !!(await b.isFullscreen());
  return !!document.fullscreenElement;
}

export function PlayerHost() {
  const t = useT();
  // Sélecteurs ciblés : s'abonner au store entier re-rendait tout le lecteur à chaque mise à jour (ex. position de l'aperçu).
  const target = usePlayer((s) => s.target);
  const mode = usePlayer((s) => s.mode);
  const slot = usePlayer((s) => s.slot);
  const nonce = usePlayer((s) => s.nonce);
  const zapCat = usePlayer((s) => s.zapCat);
  const prefs = usePrefs();
  const sources = useSources((s) => s.list);
  const source = sources.find((s) => s.id === target?.sourceId);

  const wrapRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const engineRef = useRef<PlayerEngine | null>(null);
  const candRef = useRef<{ list: Candidate[]; i: number }>({ list: [], i: 0 });
  const modeRef = useRef(mode);
  modeRef.current = mode;

  const [state, setState] = useState<PlayState>("idle");
  const [error, setError] = useState(false);
  /** Flux protégé (DRM) : aucune lecture tentée, message dédié sans bouton « Réessayer ». */
  const [drmBlocked, setDrmBlocked] = useState(false);
  // Image sans son (AC-3/DTS non décodables) : message non bloquant, jamais l'écran d'erreur.
  const [noAudio, setNoAudio] = useState(false);
  const silentIdxRef = useRef<number | null>(null);
  const skipProbeRef = useRef(false);
  const resumeAtRef = useRef<number | null>(null);
  /** Conversion audio AC-3 / DTS en cours (bureau) ; null sinon. */
  const txRef = useRef<TxState | null>(null);
  const [tracks, setTracks] = useState<Tracks>(NO_TRACKS);
  const [stats, setStats] = useState<Stats | null>(null);
  const [ui, setUi] = useState(true);
  const [panel, setPanel] = useState<null | "tracks" | "display" | "player" | "stats" | "channels" | "episodes">(null);
  const [cur, setCur] = useState({ t: 0, d: 0, buf: 0 });
  const [clock, setClock] = useState(() => new Date());
  const [osFull, setOsFull] = useState(false);
  const [zapFlash, setZapFlash] = useState<string | null>(null);
  const [rate, setRate] = useState(1);
  /** Position survolée sur la barre de lecture (bulle de temps). */
  const [hoverT, setHoverT] = useState<{ f: number } | null>(null);
  const hideTimer = useRef<number | undefined>(undefined);
  // OSD du Direct : simple bandeau (programme) après un zap, saisie d'un numéro, ouverture au clavier (masquage à 8 s).
  const [banner, setBanner] = useState(false);
  const bannerTimer = useRef<number | undefined>(undefined);
  const [numBuf, setNumBuf] = useState("");
  const numRef = useRef("");
  const numTimer = useRef<number | undefined>(undefined);
  const kbOsdRef = useRef(false);
  const live =target?.kind === "live" && !target.replay;
  const liveRef = useRef(live);
  liveRef.current = live;
  const [reconnecting, setReconnecting] = useState(false);
  /** Limite de connexions simultanées du fournisseur atteinte : compteurs x/y si connus (null dedans = inconnus). */
  const [limit, setLimit] = useState<{ a: number; b: number } | null | false>(false);
  /** Nouvelle tentative unique après une limite de connexions (le fournisseur compte parfois encore l'ancienne). */
  const limitRetriedRef = useRef(false);
  const limitTimerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  /** Sonde d'un échec sans statut déjà faite pour cette lecture. */
  const probedRef = useRef(false);
  // Direct coupé (session fermée par le serveur, réseau, flux figé) : on rouvre la même adresse tout seul.
  const reconRef = useRef<LiveReconnect | null>(null);

  // Scrobble Trakt du média en cours (null : direct, rediffusion, non appairé).
  const scrobRef = useRef<Scrobbler | null>(null);

  // --- moteur ---
  useEffect(() => {
    const v = videoRef.current!;
    const recon = new LiveReconnect({
      reload: () => { const c = candRef.current; const a = c.list[c.i]; if (a) void startCandidate(a); },
      giveUp: () => { setReconnecting(false); setError(true); setState("error"); },
      onReconnecting: () => setReconnecting(true),
    });
    reconRef.current = recon;
    const eng = new PlayerEngine(v, transportSync, {
      onState: (s) => { setState(s); scrobRef.current?.onState(s, v.currentTime, v.duration); if (s === "playing") { setError(false); setReconnecting(false); } recon.onState(s, liveRef.current); },
      onError: (_code, status) => {
        // Limite de connexions : inutile d'enchaîner les replis (chacun rouvrirait une connexion refusée).
        if (isConnectionLimit({ status })) { void onLimit(); return; }
        if (recon.onError(liveRef.current)) return;
        if (status === undefined && !probedRef.current && (_code.startsWith("media:") || _code === "mpegts")) {
          // Échec sans statut (lecture native, MPEG-TS) : on libère la connexion, puis on sonde le fournisseur.
          probedRef.current = true;
          eng.stop();
          void probeCurrent().then((f) => { if (engineRef.current === eng) { if (isConnectionLimit(f)) void onLimit(); else fallback(); } });
          return;
        }
        fallback();
      },
      onTracks: setTracks,
    });
    const fallback = () => {
      const c = candRef.current;
      if (c.i + 1 < c.list.length) {
        c.i += 1;
        const a = c.list[c.i]!;
        void startCandidate(a);
      } else {
        // Les replis ont échoué après un candidat qui jouait sans son : mieux vaut l'image sans son que l'erreur.
        const back = revertToSilent(silentIdxRef.current, c.list.length);
        if (back >= 0) { c.i = back; skipProbeRef.current = true; setNoAudio(true); void startCandidate(c.list[back]!); return; }
        setError(true); setState("error");
      }
    };
    /** Sonde le flux courant (connexion précédente déjà fermée) : statut et début du corps de la réponse. */
    const probeCurrent = async () => {
      const c = candRef.current.list[candRef.current.i];
      const tg = usePlayer.getState().target;
      const src = useSources.getState().list.find((s) => s.id === tg?.sourceId);
      if (!c || !src) return {};
      const t = transportSync();
      const headers = requestHeaders(t, { userAgent: src.userAgent || (src.type === "stalker" ? STALKER_UA : null), referer: src.referer || null });
      return probeFailure(wrapUrl(t, c.url), headers);
    };
    /** Limite de connexions : une seule nouvelle tentative après un court délai, puis message clair (pas de repli de format). */
    const onLimit = async () => {
      eng.stop();
      if (!limitRetriedRef.current) {
        limitRetriedRef.current = true;
        setReconnecting(true); setState("loading");
        clearTimeout(limitTimerRef.current);
        limitTimerRef.current = setTimeout(() => {
          const c = candRef.current; const a = c.list[c.i];
          if (a) void startCandidate(a);
        }, slotConfig.retryMs);
        return;
      }
      setReconnecting(false);
      const tg = usePlayer.getState().target;
      const src = useSources.getState().list.find((s) => s.id === tg?.sourceId);
      let counts: { a: number; b: number } | null = null;
      if (src && src.type === "xtream") {
        try { counts = limitCounts(connCounts((await handshake(await currentTransport(), credsOf(src))).user_info)); } catch { /* compteurs inconnus */ }
      }
      if (engineRef.current !== eng) return;
      setLimit(counts); setError(true); setState("error");
    };
    engineRef.current = eng;
    eng.uiLang = usePrefs.getState().lang;
    eng.preferredAudio = effectivePreferred([], usePrefs.getState().lang);
    v.volume = usePrefs.getState().volume;
    v.muted = usePrefs.getState().muted;
    return () => { recon.dispose(); clearTimeout(limitTimerRef.current); eng.stop(); engineRef.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startCandidate = useCallback(async (c: Candidate) => {
    const tg = usePlayer.getState().target;
    const src = useSources.getState().list.find((s) => s.id === tg?.sourceId);
    if (!tg || !src || !engineRef.current) return;
    setError(false);
    const resume = resumeAtRef.current;
    resumeAtRef.current = null;
    const isTx = c.label === TX_LABEL;
    if (!isTx) txRef.current = null;
    await engineRef.current.load({
      url: c.url, format: c.format, live: tg.kind === "live" && !tg.replay,
      // Portail Stalker : flux demandés avec l'identité MAG du portail, sauf User-Agent choisi par l'utilisateur.
      userAgent: src.userAgent || (src.type === "stalker" ? STALKER_UA : null), referer: src.referer || null, startAt: isTx ? 0 : resume ?? tg.startAt,
    });
  }, []);

  useEffect(() => {
    reconRef.current?.reset();
    setReconnecting(false);
    clearTimeout(limitTimerRef.current);
    limitRetriedRef.current = false; probedRef.current = false;
    setLimit(false);
    if (!target || !source) { engineRef.current?.stop(); return; }
    setTracks(NO_TRACKS);
    setPanel(null);
    setCur({ t: 0, d: 0, buf: 0 });
    window.clearTimeout(scrubTimer.current); scrubRef.current = null; setScrub(null);
    setDrmBlocked(!!target.protectedStream);
    if (target.protectedStream) {
      engineRef.current?.stop();
      setNoAudio(false);
      setError(true); setState("error");
      return;
    }
    if (source.type === "stalker") {
      // Portail Stalker : lien à durée de vie courte demandé au portail (create_link) au moment de jouer.
      let dead = false;
      engineRef.current?.stop();
      setState("loading");
      silentIdxRef.current = null; skipProbeRef.current = false; resumeAtRef.current = null;
      setNoAudio(false);
      void stalkerCandidate(source, target).then((c) => {
        if (dead) return;
        if (!c) { setError(true); setState("error"); return; }
        candRef.current = { list: [c], i: 0 };
        void startCandidate(c);
      }).catch(() => { if (!dead) { setError(true); setState("error"); } });
      return () => { dead = true; };
    }
    const list = candidates(source, target, { liveFormat: prefs.liveFormat, preferMp4: prefs.preferMp4 });
    candRef.current = { list, i: 0 };
    silentIdxRef.current = null; skipProbeRef.current = false; resumeAtRef.current = null;
    setNoAudio(false);
    if (!list[0]) { setError(true); setState("error"); return; }
    if (usePlayer.getState().zapNonce === nonce) {
      // Zap : on coupe l'ancien flux tout de suite (libère la connexion du fournisseur) et on n'ouvre le nouveau
      // qu'après une courte pause ; un zap suivant annule celui-ci (cleanup) au lieu d'empiler des connexions.
      engineRef.current?.stop();
      setState("loading");
      const to = setTimeout(() => void startCandidate(list[0]!), ZAP_DEBOUNCE_MS);
      return () => clearTimeout(to);
    }
    void startCandidate(list[0]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target?.refId, target?.kind, target?.replay?.start, nonce, source?.id]);

  // Sonde audio (VOD) : image qui défile mais aucun octet audio décodé -> candidat suivant, à la même position.
  useEffect(() => {
    if (state !== "playing" || !target || (target.kind === "live" && !bridge()?.transcode) || skipProbeRef.current) return;
    const v = videoRef.current;
    if (!v) return;
    const w = new AudioWatcher();
    const iv = setInterval(() => {
      const verdict = w.tick(readAudioSample(v));
      if (verdict === "wait") return;
      clearInterval(iv);
      if (verdict !== "silent") return;
      const c = candRef.current;
      if (silentIdxRef.current === null) silentIdxRef.current = c.i;
      const nx = nextAudioFallback(c.list.length, c.i);
      if (nx < 0) {
        skipProbeRef.current = true;
        // Bureau avec ffmpeg : dernier recours, conversion de l'audio en AAC (AC-3 / E-AC-3 / DTS) à la position courante.
        const base = bridge()?.transcode ? bridge()?.proxyBase : undefined;
        const cur = c.list[c.i];
        if (base && cur && cur.label !== TX_LABEL) {
          const isLive = target.kind === "live" && !target.replay;
          const tx: TxState = { orig: cur.url, offset: isLive ? 0 : v.currentTime, duration: isLive || !Number.isFinite(v.duration) ? 0 : v.duration };
          txRef.current = tx;
          c.list = [...c.list, { url: txUrl(base, tx.orig, tx.offset, isLive), format: "ts", label: TX_LABEL }];
          c.i = c.list.length - 1;
          void startCandidate(c.list[c.i]!);
          return;
        }
        setNoAudio(true);
        return;
      }
      c.i = nx;
      resumeAtRef.current = v.currentTime > 1 ? v.currentTime : null;
      void startCandidate(c.list[nx]!);
    }, 1000);
    return () => clearInterval(iv);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state, target?.refId, target?.kind]);

  // Scrobble : une session par film / épisode ; la fermeture (changement de cible, fermeture du lecteur) envoie « stop ».
  useEffect(() => {
    if (!target || !source) return;
    const sc = startScrobble(target, source);
    scrobRef.current = sc;
    const iv = sc ? setInterval(() => { const v = videoRef.current; if (v) sc.note(v.currentTime, v.duration); }, 10_000) : undefined;
    return () => {
      clearInterval(iv);
      const v = videoRef.current;
      sc?.close(v?.currentTime ?? 0, v?.duration ?? NaN);
      if (scrobRef.current === sc) scrobRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target?.refId, target?.kind, target?.replay?.start, source?.id]);

  const saveHistory = useCallback(() => {
    const tg = usePlayer.getState().target;
    const v = videoRef.current;
    if (!tg || !v || tg.kind === "live") return;
    const { t: pos, d: dur } = txClock(txRef.current, v.currentTime, v.duration);
    if (!dur || dur < 30) return;
    const { profileId } = usePrefs.getState();
    const done = pos / dur > 0.95;
    const key = histKey(profileId, tg.sourceId, tg.kind === "movie" ? "movie" : "series", tg.kind === "movie" ? tg.refId : tg.seriesId ?? tg.refId) + (tg.kind === "episode" ? `:${tg.refId}` : "");
    if (done) { void db.history.delete(key); return; }
    if (pos < 20) return;
    void db.history.put({
      key, profile: profileId, sourceId: tg.sourceId, kind: tg.kind === "movie" ? "movie" : "series", refId: tg.kind === "movie" ? tg.refId : tg.seriesId ?? tg.refId,
      title: tg.title, image: tg.image ?? null, pos, dur, updatedAt: Date.now(),
      ext: tg.ext, seriesId: tg.seriesId, season: tg.season, episode: tg.episode,
      epId: tg.kind === "episode" ? tg.refId : undefined,
    });
  }, []);
  useEffect(() => {
    if (!target || target.kind === "live") return;
    const iv = setInterval(saveHistory, 10_000);
    return () => { clearInterval(iv); saveHistory(); };
  }, [target?.refId, target?.kind, saveHistory]);

  // --- épisode suivant (fiche de la série en cache) : null en fin de série ou hors épisode ---
  const nextEpisodeTarget = useCallback(async (): Promise<PlayTarget | null> => {
    const tg = usePlayer.getState().target;
    const src = useSources.getState().list.find((x) => x.id === tg?.sourceId);
    if (!tg || tg.kind !== "episode" || !src) return null;
    const row = await db.details.get(`series:${src.id}:${tg.seriesId}`);
    if (!row) return null;
    const all = parseEpisodes(row.json as SeriesInfo).seasons.flatMap((x) => x.eps);
    const k = all.findIndex((e) => e.id === tg.refId);
    const nx = k >= 0 ? all[k + 1] : undefined;
    return nx ? { ...tg, refId: nx.id, ext: nx.ext, season: nx.season, episode: nx.num, startAt: 0,
      subtitle: `${t("common.season", { n: nx.season })} · ${t("common.episodeShort", { n: nx.num })} ${nx.title}`.trim() } : null;
  }, [t]);

  // --- épisode suivant automatique ---
  useEffect(() => {
    if (state !== "ended" || !target || target.kind !== "episode" || !prefs.autoplayNext || !source) return;
    let dead = false;
    void nextEpisodeTarget().then((nx) => { if (!dead && nx) usePlayer.getState().open(nx, "full"); });
    return () => { dead = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  // --- passer l'intro / le résumé / le générique (TheIntroDB) : bouton seulement, rien n'est sauté tout seul ---
  const [skips, setSkips] = useState<SkipSegment[]>([]);
  const [skipNext, setSkipNext] = useState<PlayTarget | null>(null);
  const durKnown = cur.d > 0;
  useEffect(() => {
    setSkips([]); setSkipNext(null);
    if (!target || !source || !durKnown || target.replay || (target.kind !== "movie" && target.kind !== "episode")) return;
    let dead = false;
    void loadSkips(target, source, videoRef.current?.duration ?? 0).then((sg) => { if (!dead) setSkips(sg); });
    if (target.kind === "episode") void nextEpisodeTarget().then((nx) => { if (!dead) setSkipNext(nx); }).catch(() => {});
    return () => { dead = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target?.refId, target?.kind, target?.replay?.start, source?.id, durKnown]);
  const skipOffer = state === "playing" && skips.length > 0 && target?.kind !== "live"
    ? skipOfferAt(skips, cur.t, cur.d, target?.kind === "episode", !!skipNext) : null;
  // Défilement aux flèches (film / épisode) : position visée sur la barre, saut au relâchement.
  const scrubRef = useRef<ScrubState | null>(null);
  const scrubTimer = useRef(0);
  const [scrub, setScrub] = useState<{ target: number; origin: number } | null>(null);
  const skipRef = useRef<() => boolean>(() => false);
  skipRef.current = () => {
    if (!skipOffer) return false;
    window.clearTimeout(scrubTimer.current); scrubRef.current = null; setScrub(null);
    if (skipOffer.next && skipNext) { usePlayer.getState().open(skipNext, "full"); return true; }
    const v = videoRef.current;
    if (v && skipOffer.to != null) { v.currentTime = skipOffer.to; return true; }
    return false;
  };

  // --- surcouche : apparition au mouvement, masquage après 3 s ---
  const idle = useRef(createOsdIdle()).current;
  const armHide = useCallback(() => {
    window.clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => {
      // Barre survolée / focalisée ou menu ouvert : on repousse ; sinon on masque.
      if (idle.shouldHide()) setUi(false); else armHide();
    }, Math.max(250, idle.remaining() + (kbOsdRef.current ? KEYBOARD_EXTRA_MS : 0)));
  }, [idle]);
  // Touche, clic ou changement de lecture : activité certaine.
  const poke = useCallback(() => {
    idle.activity();
    setUi(true);
    armHide();
  }, [idle, armHide]);
  // Simple bandeau du programme (après un zap, ou à l'arrivée sur une chaîne) : 5 s.
  const showBanner = useCallback(() => {
    setBanner(true);
    window.clearTimeout(bannerTimer.current);
    bannerTimer.current = window.setTimeout(() => setBanner(false), OSD_BANNER_HIDE_MS);
  }, []);
  // Ferme tout l'OSD (bandeau compris) et rend le focus au lecteur.
  const closeOsd = useCallback(() => {
    window.clearTimeout(hideTimer.current);
    window.clearTimeout(bannerTimer.current);
    kbOsdRef.current = false;
    idle.setHold(false);
    setUi(false);
    setBanner(false);
    const a = document.activeElement as HTMLElement | null;
    if (a && a !== document.body && a.closest(".pbot")) a.blur();
  }, [idle]);
  // Souris : seulement si le pointeur a réellement bougé.
  const onMove = useCallback((e: React.MouseEvent) => {
    if (idle.move(e.screenX, e.screenY, e.movementX, e.movementY)) { kbOsdRef.current = false; poke(); }
  }, [idle, poke]);
  // Survol (ou focus clavier, hors Direct) dans la barre / le panneau : l'OSD reste affiché.
  // Au Direct, le focus au clavier ne retient pas l'OSD : il se masque après 8 s sans touche.
  const holdOver = useCallback((e: React.SyntheticEvent) => {
    idle.setHold(!!(e.target as HTMLElement).closest?.(".pbot,.pside"));
  }, [idle]);
  useEffect(() => { idle.setMenuOpen(panel !== null); }, [idle, panel]);
  useEffect(() => () => { window.clearTimeout(hideTimer.current); window.clearTimeout(bannerTimer.current); window.clearTimeout(numTimer.current); }, []);
  useEffect(() => {
    // Direct plein écran : l'arrivée sur une chaîne (zap, liste, lancement) montre le bandeau, pas tout l'OSD.
    if (mode === "full" && liveRef.current) showBanner(); else poke();
  }, [mode, target?.refId, poke, showBanner]);
  const showUi = ui || state !== "playing" || panel !== null;
  // Chaînes récentes (session) : la chaîne en cours passe en tête.
  const recents = usePlayer((s) => s.recents);
  useEffect(() => {
    const tg = usePlayer.getState().target;
    if (!tg || tg.kind !== "live" || tg.replay || !tg.channel) return;
    const c = tg.channel;
    usePlayer.setState((s) => ({
      recents: pushRecent(s.recents, { streamId: tg.refId, num: c.num, name: tg.title, logo: c.logo, ord: c.ord, catExt: c.catExt, epg: c.epg, archive: c.archive, q: c.q, url: tg.url }),
    }));
  }, [target?.refId, target?.kind, target?.replay]);

  // --- progression et statistiques : seulement en plein écran avec l'interface visible (rien à afficher sinon) ---
  const tickOn = mode === "full" && showUi;
  useEffect(() => {
    if (!tickOn) return;
    const v = videoRef.current!;
    const tick = () => {
      let buf = 0;
      for (let i = 0; i < v.buffered.length; i++) if (v.currentTime >= v.buffered.start(i) && v.currentTime <= v.buffered.end(i)) buf = v.buffered.end(i);
      // Conversion audio en cours : position = départ de la conversion + lecture, durée = celle du média.
      const { t: ct, d } = txClock(txRef.current, v.currentTime, v.duration);
      if (txRef.current) buf += txRef.current.offset;
      // Même valeur : on garde l'objet, pas de re-rendu.
      setCur((p) => (Math.abs(p.t - ct) < 0.25 && p.d === d && Math.abs(p.buf - buf) < 0.5 ? p : { t: ct, d, buf }));
      if (panel === "stats") setStats(engineRef.current?.stats() ?? null);
    };
    tick();
    const iv = setInterval(tick, 500);
    return () => clearInterval(iv);
  }, [tickOn, panel]);

  // --- horloge : l'affichage est à la minute, on ne se réveille qu'au changement de minute ---
  useEffect(() => {
    if (mode !== "full") return;
    let to: ReturnType<typeof setTimeout>;
    const arm = () => {
      const now = new Date();
      setClock(now);
      to = setTimeout(arm, 60_000 - (now.getTime() % 60_000) + 50);
    };
    arm();
    return () => clearTimeout(to);
  }, [mode]);

  // --- plein écran ---
  useEffect(() => {
    const b = bridge();
    const onFs = () => setOsFull(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", onFs);
    const off = b?.onFullscreenChange?.((v) => setOsFull(v));
    void isOsFullscreen().then(setOsFull);
    return () => { document.removeEventListener("fullscreenchange", onFs); if (typeof off === "function") off(); };
  }, []);

  const goFull = useCallback(() => usePlayer.getState().setMode("full"), []);
  const leaveFull = useCallback(async () => {
    if (await isOsFullscreen()) await setOsFullscreen(false);
    const s = usePlayer.getState();
    if (s.slot) s.setMode("inline"); else s.close();
  }, []);
  const toggleOsFull = useCallback(async () => {
    if (modeRef.current !== "full") usePlayer.getState().setMode("full");
    await setOsFullscreen(!(await isOsFullscreen()));
  }, []);

  const togglePlay = useCallback(() => {
    const v = videoRef.current!;
    if (v.paused) void v.play().catch(() => undefined); else v.pause();
  }, []);
  /** Saut à une position (s). Conversion audio en cours : le flux n'est pas cherchable, la conversion repart de là. */
  const seekTo = useCallback((sec: number) => {
    const v = videoRef.current!;
    const tx = txRef.current;
    const base = bridge()?.proxyBase;
    if (tx && base && tx.duration > 0) {
      const ss = clampSeek(sec, tx.duration);
      txRef.current = { ...tx, offset: ss };
      const c = candRef.current;
      c.list = c.list.map((x, k) => (k === c.i ? { ...x, url: txUrl(base, tx.orig, ss) } : x));
      void startCandidate(c.list[c.i]!);
      return;
    }
    if (Number.isFinite(v.duration)) v.currentTime = Math.max(0, Math.min(v.duration, sec));
  }, [startCandidate]);
  const seekBy = useCallback((s: number) => {
    const v = videoRef.current!;
    seekTo(txClock(txRef.current, v.currentTime, v.duration).t + s);
  }, [seekTo]);
  /** Fin du défilement : saut à la cible (valider) ou retour sans saut (annuler). Faux s'il n'y en avait pas. */
  const scrubEnd = useCallback((commit: boolean) => {
    window.clearTimeout(scrubTimer.current);
    const s = scrubRef.current;
    scrubRef.current = null;
    setScrub(null);
    if (!s) return false;
    if (commit) seekTo(s.target);
    kbOsdRef.current = false;
    poke();
    return true;
  }, [seekTo, poke]);
  const armScrub = useCallback((ms: number) => {
    window.clearTimeout(scrubTimer.current);
    scrubTimer.current = window.setTimeout(() => scrubEnd(true), ms);
  }, [scrubEnd]);
  /** Flèche / touche média en VOD : fait avancer la cible (le saut réel vient après COMMIT_MS sans appui). */
  const scrubPress = useCallback((dir: 1 | -1, repeat: boolean) => {
    const v = videoRef.current;
    if (!v) return;
    const c = txClock(txRef.current, v.currentTime, v.duration);
    if (!(c.d > 0)) { seekBy(dir * 10); return; }
    const st = scrubPressState(scrubRef.current, dir, performance.now(), c.t, c.d, repeat);
    scrubRef.current = st;
    setScrub({ target: st.target, origin: st.origin });
    // Touche encore enfoncée : filet de sécurité large (le délai de répétition du clavier peut dépasser 700 ms) ; le relâchement réarme à COMMIT_MS.
    armScrub(Math.max(COMMIT_MS, 1500));
  }, [seekBy, armScrub]);
  const scrubKeyUp = useCallback(() => {
    if (!scrubRef.current) return;
    scrubRef.current = scrubRelease(scrubRef.current, performance.now());
    armScrub(COMMIT_MS);
  }, [armScrub]);
  const setVol = useCallback((d: number) => {
    const v = videoRef.current!;
    v.volume = Math.max(0, Math.min(1, v.volume + d));
    v.muted = false;
    usePrefs.getState().set({ volume: v.volume, muted: false });
    setZapFlash(`${Math.round(v.volume * 100)} %`);
    window.setTimeout(() => setZapFlash(null), 900);
  }, []);
  const toggleMute = useCallback(() => {
    const v = videoRef.current!;
    v.muted = !v.muted;
    usePrefs.getState().set({ muted: v.muted });
  }, []);
  const pip = useCallback(async () => {
    const v = videoRef.current as HTMLVideoElement & { requestPictureInPicture?: () => Promise<unknown> };
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await v.requestPictureInPicture?.();
    } catch { /* non pris en charge */ }
  }, []);
  // Zap : au Direct plein écran, le bandeau du programme remplace le petit cartouche ; `close` referme l'OSD complet.
  const zap = useCallback(async (d: 1 | -1, close = false) => {
    await usePlayer.getState().zap(d);
    if (close) closeOsd();
    const tg = usePlayer.getState().target;
    if (tg && modeRef.current === "full") showBanner();
    else if (tg) { setZapFlash(`${tg.channel?.num ?? ""} ${tg.title}`); window.setTimeout(() => setZapFlash(null), 1400); }
  }, [closeOsd, showBanner]);
  // Ouvre une chaîne mémorisée (rangée des récents, rappel de la précédente, saisie d'un numéro).
  const tune = useCallback((c: RecentChannel) => {
    const tg = usePlayer.getState().target;
    if (!tg) return;
    usePlayer.getState().open({
      ...tg, refId: c.streamId, title: c.name, subtitle: undefined, image: c.logo, url: c.url, replay: undefined,
      channel: { ord: c.ord, catExt: c.catExt, num: c.num, epg: c.epg, archive: c.archive, q: c.q, logo: c.logo },
    }, "full");
  }, []);
  const clearNum = useCallback(() => {
    window.clearTimeout(numTimer.current);
    numRef.current = "";
    setNumBuf("");
  }, []);
  const commitNum = useCallback(async () => {
    const buf = numRef.current;
    clearNum();
    const tg = usePlayer.getState().target;
    if (!buf || !tg) return;
    const n = Number(buf);
    const row = await db.channels.where("[sourceId+ord]").between([tg.cid, -1], [tg.cid, Infinity], true, true).filter((c) => c.num === n && !c.sep).first();
    if (!row || usePlayer.getState().target?.refId === row.streamId) { showBanner(); return; }
    tune({ streamId: row.streamId, num: row.num, name: row.display, logo: row.logo, ord: row.ord, catExt: row.catExt, epg: row.epg, archive: !!row.archive, q: row.q, url: row.url });
  }, [clearNum, showBanner, tune]);
  const uiRef = useRef(ui);
  uiRef.current = ui;
  const stateRef = useRef(state);
  stateRef.current = state;
  const errorRef = useRef(error);
  errorRef.current = error;

  // Focus dans l'OSD du Direct : étage courant et boutons de l'étage.
  const stageOf = (): OsdStage | null => {
    const a = document.activeElement as HTMLElement | null;
    if (!a) return null;
    if (a.closest(".precent")) return "recents";
    if (a.closest(".pbot")) return "controls";
    return null;
  };
  const stageButtons = (stage: OsdStage) =>
    [...(wrapRef.current?.querySelectorAll<HTMLButtonElement>(stage === "recents" ? ".precent button" : ".pbot button") ?? [])]
      .filter((b) => stage === "recents" || !b.closest(".precent"));
  // Contrôles : « Lecture / Pause » (premier bouton) ; récents : la première chaîne.
  const focusStage = (stage: OsdStage) => stageButtons(stage)[0]?.focus({ preventScroll: true });

  // --- raccourcis (actifs en plein écran, ou en aperçu quand la lecture est lancée) ---
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const m = modeRef.current;
      const tg = usePlayer.getState().target;
      if (m === "closed" || !tg) return;
      const el = e.target as HTMLElement | null;
      // Recherche (Ctrl/⌘ + K ou « / ») : on quitte le plein écran ; l'application navigue vers la recherche.
      if (m === "full" && (((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) || (e.key === "/" && !e.metaKey && !e.ctrlKey && !(el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA"))))) { void leaveFull(); return; }
      if (el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable)) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const isLive = tg.kind === "live" && !tg.replay;
      const full = m === "full";

      // --- Direct plein écran : OSD à trois étages (décisions pures dans liveOsd.ts) ---
      if (full && isLive && !errorRef.current) {
        const p = panelRef.current;
        if (e.key === "Escape") {
          e.preventDefault(); e.stopPropagation();
          if (numRef.current) { clearNum(); return; }
          if (p) { setPanel(null); return; }
          const eff = backEffect(uiRef.current, stageOf() ?? "controls", stateRef.current === "playing");
          if (eff === "focusControls") focusStage("controls");
          else if (eff === "closeOsd") closeOsd();
          else void leaveFull();
          return;
        }
        if (p) {
          // Panneau ouvert : rien ne zappe. Liste des chaînes : ↑ ↓ parcourent les lignes (Entrée active la ligne focalisée).
          if (e.key === "ArrowUp" || e.key === "ArrowDown") {
            if (p === "channels") {
              const bs = [...(wrapRef.current?.querySelectorAll<HTMLButtonElement>(".pch-list button") ?? [])];
              const i = bs.indexOf(document.activeElement as HTMLButtonElement);
              if (bs.length) { e.preventDefault(); bs[Math.max(0, Math.min(bs.length - 1, i < 0 ? 0 : i + (e.key === "ArrowDown" ? 1 : -1)))]!.focus(); }
            }
            return;
          }
          if (e.key === "PageUp" || e.key === "PageDown") return;
        } else {
          // Saisie d'un numéro de chaîne : 0-9 ; Entrée valide tout de suite.
          const nb = appendDigit(numRef.current, e.key);
          if (nb !== null) {
            e.preventDefault();
            numRef.current = nb;
            setNumBuf(nb);
            window.clearTimeout(numTimer.current);
            numTimer.current = window.setTimeout(() => void commitNum(), NUMBER_ENTRY_MS);
            return;
          }
          if (e.key === "Enter" && numRef.current) { e.preventDefault(); void commitNum(); return; }
          const k = keyOf(e.key);
          if (k !== "other") {
            const osdOpen = uiRef.current;
            const stage = stageOf();
            // OK sur un bouton de l'OSD : le bouton agit (clic natif). OK sans focus dans l'OSD ouvert : liste des chaînes.
            if (k === "ok" && osdOpen && el && el.tagName === "BUTTON") { poke(); return; }
            const d = k === "ok" && osdOpen && stage === null ? ({ type: "openChannels" } as const) : decide(k, osdOpen, false, false);
            e.preventDefault();
            switch (d.type) {
              case "openOsd": kbOsdRef.current = true; setBanner(false); poke(); break;
              case "zap": void zap(d.forward ? 1 : -1, d.closeOsd); break;
              case "openChannels": closeOsd(); setPanel("channels"); break;
              case "recallPrevious": { const prev = usePlayer.getState().recents[1]; if (prev) tune(prev); break; }
              case "pass": {
                const nav = navigate(k, stage, recentsToShow(usePlayer.getState().recents, tg.refId).length > 0, document.dir === "rtl");
                kbOsdRef.current = true;
                if (nav.type === "focusStage") focusStage(nav.stage);
                else if (nav.type === "step" && stage) {
                  const bs = stageButtons(stage);
                  const i = bs.indexOf(document.activeElement as HTMLButtonElement);
                  bs[Math.max(0, Math.min(bs.length - 1, i + nav.delta))]?.focus({ preventScroll: true });
                }
                poke();
                break;
              }
              default: break;
            }
            return;
          }
        }
      }

      // Panneau « Épisodes » : ↑ ↓ (Début, Fin) parcourent les épisodes, ← → changent de saison ; Entrée lit (clic natif).
      if (full && panelRef.current === "episodes" && ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Home", "End"].includes(e.key)) {
        e.preventDefault();
        const root = wrapRef.current?.querySelector<HTMLElement>(".pep");
        if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
          const tabs = [...(root?.querySelectorAll<HTMLButtonElement>(".pep-seasons button") ?? [])];
          const cur = tabs.findIndex((b) => b.getAttribute("aria-selected") === "true");
          const fwd = (e.key === "ArrowRight") !== (document.dir === "rtl");
          tabs[Math.max(0, Math.min(tabs.length - 1, cur + (fwd ? 1 : -1)))]?.click();
          return;
        }
        const bs = [...(root?.querySelectorAll<HTMLButtonElement>(".pep-list button") ?? [])];
        const i = stepIndex(bs.indexOf(document.activeElement as HTMLButtonElement), bs.length, e.key);
        bs[i]?.focus();
        bs[i]?.scrollIntoView({ block: "nearest" });
        return;
      }

      // Défilement en cours : Échap annule (sans fermer le lecteur), Entrée valide tout de suite.
      if (full && scrubRef.current && (e.key === "Escape" || e.key === "Enter")) {
        e.preventDefault(); e.stopPropagation();
        scrubEnd(e.key === "Enter");
        return;
      }
      switch (e.key) {
        case "Escape":
          if (full) { e.preventDefault(); e.stopPropagation(); if (panelRef.current) setPanel(null); else void leaveFull(); }
          break;
        case "e": case "E": if (full && tg.kind === "episode") { e.preventDefault(); setPanel((p) => (p === "episodes" ? null : "episodes")); poke(); } break;
        // Passer l'intro : S, ou Entrée hors bouton focalisé (le bouton reste cliquable à la souris).
        case "s": case "S": if (full && skipRef.current()) e.preventDefault(); break;
        case "Enter": if (full && !(el && el.tagName === "BUTTON") && skipRef.current()) e.preventDefault(); break;
        case "f": case "F": e.preventDefault(); void toggleOsFull(); break;
        case "m": case "M": if (full) toggleMute(); break;
        case "p": case "P": if (full) void pip(); break;
        case "i": case "I": if (full) setPanel((p) => (p === "stats" ? null : "stats")); break;
        case " ": if (full) { e.preventDefault(); togglePlay(); poke(); } break;
        // VOD : défilement accéléré (sens inversé en RTL) ; différé : saut fixe de 10 s.
        case "ArrowLeft": case "ArrowRight": case "MediaRewind": case "MediaFastForward":
          if (full && !isLive) {
            e.preventDefault();
            if (tg.kind === "live") { seekBy(e.key === "ArrowRight" || e.key === "MediaFastForward" ? 10 : -10); poke(); }
            else {
              const fwd = e.key === "MediaFastForward" || (e.key === "ArrowRight") !== (document.dir === "rtl");
              scrubPress(fwd ? 1 : -1, e.repeat); poke();
            }
          }
          break;
        case "ArrowUp": if (full) { e.preventDefault(); if (isLive) void zap(-1); else setVol(0.05); poke(); } break;
        case "ArrowDown": if (full) { e.preventDefault(); if (isLive) void zap(1); else setVol(-0.05); poke(); } break;
        case "PageUp": if (full && isLive) void zap(-1); break;
        case "PageDown": if (full && isLive) void zap(1); break;
      }
    };
    const onKeyUp = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft" || e.key === "ArrowRight" || e.key === "MediaRewind" || e.key === "MediaFastForward") scrubKeyUp();
    };
    window.addEventListener("keydown", onKey, true);
    window.addEventListener("keyup", onKeyUp, true);
    return () => { window.removeEventListener("keydown", onKey, true); window.removeEventListener("keyup", onKeyUp, true); };
  }, [scrubEnd, scrubPress, scrubKeyUp, leaveFull, toggleOsFull, toggleMute, pip, togglePlay, seekBy, setVol, zap, poke, closeOsd, clearNum, commitNum, tune]);
  const panelRef = useRef(panel);
  panelRef.current = panel;

  const nn = useNowNext(source, target?.channel?.epg, target?.kind === "live" ? target.refId : undefined);

  // Étage « Chaînes récentes » : programme en cours de chaque chaîne, lu au guide local quand l'OSD est visible.
  const curId = target?.kind === "live" ? target.refId : undefined;
  const shownRecents = useMemo(() => recentsToShow(recents, curId), [recents, curId]);
  const [recentNow, setRecentNow] = useState<Map<string, string>>(new Map());
  const recentsVisible = mode === "full" && live && ui && shownRecents.length > 0;
  useEffect(() => {
    if (!recentsVisible || !source?.cid) return;
    let dead = false;
    const ids = shownRecents.map((r) => r.epg).filter((x): x is string => !!x);
    void nowNext(source.cid, ids).then((m) => {
      if (!dead) setRecentNow(new Map([...m].flatMap(([k, v]) => (v.now ? [[k, v.now.title] as [string, string]] : []))));
    }).catch(() => {});
    return () => { dead = true; };
  }, [recentsVisible, shownRecents, source?.cid]);
  // Favori de la chaîne en cours (bouton du bas).
  const isFav = useLiveQuery(
    async () => (source?.id != null && curId != null ? !!(await db.favorites.get(favKey(prefs.profileId, source.id, "live", curId))) : false),
    [source?.id, curId, prefs.profileId],
  ) ?? false;

  // Ruban de la soirée : programmes autour de maintenant, lus au guide local quand l'OSD complet est visible.
  const [ribbon, setRibbon] = useState<RibbonSeg[]>([]);
  const ribbonEpg = target?.channel?.epg ?? null;
  const ribbonOn = mode === "full" && live && showUi && !!ribbonEpg && !!source?.cid;
  const progNowStart = nn.now?.start;
  useEffect(() => {
    if (!ribbonOn || !source?.cid || !ribbonEpg) { setRibbon([]); return; }
    let dead = false;
    const now = Date.now();
    void programsFor(source.cid, ribbonEpg, now - 3 * 3600_000, now + 6 * 3600_000)
      .then((rows) => { if (!dead) setRibbon(ribbonSegments(rows, Date.now())); }).catch(() => {});
    return () => { dead = true; };
  }, [ribbonOn, source?.cid, ribbonEpg, progNowStart]);

  if (!target || mode === "closed") {
    return <div className="player hidden"><video ref={videoRef} playsInline /></div>;
  }

  const full = mode === "full";
  const style = !full && slot ? { left: slot.x, top: slot.y, width: slot.w, height: slot.h } : undefined;
  if (!full && !slot) return <div className="player hidden"><video ref={videoRef} playsInline /></div>;

  const prog = nn.now;
  const progRatio = prog ? Math.min(1, Math.max(0, (Date.now() - prog.start) / (prog.end - prog.start))) : 0;
  const vodRatio = cur.d ? cur.t / cur.d : 0;
  const onSeekBar = (e: React.MouseEvent<HTMLDivElement>) => {
    if (live || !cur.d) return;
    const r = e.currentTarget.getBoundingClientRect();
    const f = (e.clientX - r.left) / r.width;
    seekTo((document.dir === "rtl" ? 1 - f : f) * cur.d);
  };
  const onSeekHover = (e: React.MouseEvent<HTMLDivElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    const f = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    setHoverT({ f: document.dir === "rtl" ? 1 - f : f });
  };
  const locale = prefs.lang;
  const title = live ? (prog?.title || target.title) : target.title;
  const subtitle = live ? `${target.channel?.num ?? ""} · ${target.title}`.replace(/^ · /, "") : target.subtitle;

  return (
    <div
      ref={wrapRef}
      className={`player ${full ? "full" : "inline"}${showUi ? " ui" : ""}${!showUi && full && banner && live ? " banner" : ""}${!showUi && full && !(banner && live) ? " nocursor" : ""}`}
      style={style}
      onMouseMove={onMove}
      onMouseOver={holdOver}
      onMouseLeave={() => idle.setHold(false)}
      onFocus={live && full ? undefined : holdOver}
      onBlur={() => idle.setHold(false)}
      onClickCapture={poke}
      onDoubleClick={(e) => { if ((e.target as HTMLElement).closest("button,.seek,.pside")) return; if (full) void toggleOsFull(); else goFull(); }}
      onClick={(e) => { if ((e.target as HTMLElement).tagName === "VIDEO") { if (full) togglePlay(); else goFull(); } }}
    >
      <video ref={videoRef} playsInline className={prefs.fit === "cover" ? "fit-cover" : prefs.fit === "fill" ? "fit-fill" : ""} />
      <div className="shade-top" /><div className="shade-bot" />

      {(state === "loading" || state === "buffering") && !error && (
        <div className="pstate">
          <div className="spinner" role="status" aria-label={t(reconnecting ? "player.reconnecting" : "player.buffering")} />
          {reconnecting && <p className="reconnecting">{t("player.reconnecting")}</p>}
        </div>
      )}
      {error && (
        <div className="pstate">
          <div className="msg">
            <b>{limit !== false ? (limit ? t("player.limit", limit) : t("player.limitNoCount")) : t(drmBlocked ? "player.drmUnsupported" : "player.error")}</b>
            {!drmBlocked && limit === false && <p style={{ margin: "0.5rem 0 1rem", color: "#c4c4cc", fontSize: "0.875rem" }}>{t("player.errorHint")}</p>}
            {!drmBlocked && <button className="btn primary sm" onClick={() => { candRef.current.i = 0; usePlayer.getState().reload(); }}>{t("player.retry")}</button>}
          </div>
        </div>
      )}
      {noAudio && !error && <div className="noaudio" role="status">{t(bridge()?.isElectron && !bridge()?.transcode ? "player.noAudioInstall" : "player.noAudio")}</div>}
      {(numBuf || zapFlash) && <div className="zap" role="status" aria-label={numBuf ? t("player.chNumber") : undefined}>{numBuf || zapFlash}</div>}
      {full && skipOffer && !(skipOffer.next && skipNext) && panel === null && (
        <button className="skipbtn" onClick={(e) => { e.stopPropagation(); skipRef.current(); }}>
          <Icon name="fwd" size={20} />
          {t(skipOffer.next ? "player.nextEpisode" : skipOffer.kind === "recap" ? "player.skipRecap" : skipOffer.kind === "credits" ? "player.skipCredits" : "player.skipIntro")}
        </button>
      )}

      {full && live ? (
        <div className="ptop">
          <div className="id">
            <span className="id-num mono">{target.channel?.num || ""}</span>
            <span className="id-logo" aria-hidden="true">
              <span className="ini">{(target.title.match(/[\p{L}\p{N}]/gu) ?? []).slice(0, 2).join("").toUpperCase()}</span>
              <Img src={target.channel?.logo ?? target.image} contain />
            </span>
            <div className="id-meta">
              <h2 className="id-name ellipsis">{target.title}</h2>
              <div className="pills">
                <span className="pill live"><i />{t("common.live")}</span>
                {target.channel && target.channel.q > 0 && <span className="pill">{qualityLabel(target.channel.q)}</span>}
                {HDR_RE.test(target.title) && <span className="pill">{(target.title.match(HDR_RE) ?? [""])[0]!.toUpperCase()}</span>}
                {isFav && <span className="pill">{t("player.favPill")}</span>}
              </div>
            </div>
          </div>
          <div className="clock mono">{hhmm(clock.getTime(), locale)}</div>
        </div>
      ) : (
        <div className="ptop">
          <div className="vt">
            {subtitle && <div className="eyebrow vt-sub">{subtitle}</div>}
            <h2>{title}</h2>
          </div>
          {full && <div className="clock mono">{hhmm(clock.getTime(), locale)}</div>}
        </div>
      )}

      {full && live ? (
        <div className="pbot live">
          <div className="precent tier2" role="group" aria-label={t("player.recents")}>
            <div className="precent-t">{t("player.recents")}</div>
            {shownRecents.length === 0 ? <div className="precent-empty">{t("player.recentsEmpty")}</div> : (
              <div className="precent-row">
                {shownRecents.slice(0, 5).map((r) => (
                  <button key={r.streamId} className="precent-ch" onClick={() => tune(r)}>
                    <span className="logo" aria-hidden="true">
                      <span className="ini">{(r.name.match(/[\p{L}\p{N}]/gu) ?? []).slice(0, 2).join("").toUpperCase()}</span>
                      <Img src={r.logo} contain />
                    </span>
                    <span className="meta">
                      <span className="ellipsis nm"><span className="num mono">{r.num}</span> · {r.name}</span>
                      <span className="ellipsis sub">{(r.epg && recentNow.get(r.epg)) || ""}</span>
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="pnowrow">
            {prog && (
              <div className="pnow">
                <div className="pnow-t mono">{t("player.nowLabel")} · {hhmm(prog.start, locale)} – {hhmm(prog.end, locale)} · {t("common.minLeft", { t: `${Math.max(0, Math.round((prog.end - Date.now()) / 60_000))} min` })}</div>
                <h3 className="pnow-title clamp-2">{prog.title}</h3>
                {prog.desc?.trim() && <p className="pnow-desc clamp-2">{prog.desc.trim()}</p>}
              </div>
            )}
            {nn.next && (
              <div className="pnext tier2">
                <div className="pnext-t mono">{t("player.then")} · {hhmm(nn.next.start, locale)} – {hhmm(nn.next.end, locale)}</div>
                <b className="clamp-2">{nn.next.title}</b>
                {nn.next.desc?.trim() && <p className="clamp-2">{nn.next.desc.trim()}</p>}
              </div>
            )}
          </div>

          {ribbon.length > 0 && (
            <div className="ribbon tier2" aria-hidden="true">
              {ribbon.map((s) => (
                <div key={s.p.start} className={`seg ${s.state}`} style={{ flexGrow: s.grow }}>
                  {s.state === "now" && <i className="fill" style={{ width: `${s.ratio * 100}%` }} />}
                  <span className="st mono">{hhmm(s.p.start, locale)}{s.state === "now" ? ` · ${t("search.onAir")}` : ""}</span>
                  <span className="sn ellipsis">{s.p.title}</span>
                </div>
              ))}
            </div>
          )}

          <div className="pdock tier2">
            <button className="dbtn" aria-label={t("player.pause")} onClick={togglePlay}>
              <Icon name={state === "paused" ? "play" : "pause"} size={22} fill /><span className="lbl">{t("player.pause")}</span>
            </button>
            <button className="dbtn" aria-label={t("a11y.volume")} onClick={toggleMute}><Icon name={videoRef.current?.muted ? "mute" : "volume"} size={22} /><span className="lbl">{t("a11y.volume")}</span></button>
            <button className={`dbtn${panel === "tracks" ? " on" : ""}`} aria-label={t("player.tracks")} onClick={() => setPanel(panel === "tracks" ? null : "tracks")}><Icon name="sliders" size={22} /><span className="lbl">{t("player.tracks")}</span></button>
            <button className={`dbtn${panel === "display" ? " on" : ""}`} aria-label={t("player.display")} onClick={() => setPanel(panel === "display" ? null : "display")}><Icon name="display" size={22} /><span className="lbl">{t("player.display")}</span></button>
            <button className={`dbtn${panel === "channels" ? " on" : ""}`} aria-label={t("player.channels")} onClick={() => setPanel(panel === "channels" ? null : "channels")}><Icon name="list" size={22} /><span className="lbl">{t("player.channels")}</span></button>
            {source?.id != null && target.channel && (
              <button
                className={`dbtn${isFav ? " fav" : ""}`} aria-pressed={isFav} aria-label={t(isFav ? "common.removeFav" : "common.addFav")}
                onClick={() => void toggleFavorite({ profile: prefs.profileId, sourceId: (source?.id ?? 0), kind: "live", refId: target.refId, name: target.title, image: target.image ?? null })}
              ><Icon name="heart" size={22} fill={isFav} /><span className="lbl">{t(isFav ? "common.removeFav" : "common.addFav")}</span></button>
            )}
            <button className={`dbtn${panel === "player" || panel === "stats" ? " on" : ""}`} aria-label={t("player.player")} onClick={() => setPanel(panel === "player" ? null : "player")}><Icon name="info" size={22} /><span className="lbl">{t("player.player")}</span></button>
            <button className="dbtn" aria-label={t("player.pip")} onClick={() => void pip()}><Icon name="pip" size={22} /><span className="lbl">{t("player.pip")}</span></button>
            <button className="dbtn" aria-label={t("player.fullscreen")} onClick={() => void toggleOsFull()}><Icon name="full" size={22} /><span className="lbl">{t("player.fullscreen")}</span></button>
            <button className="dbtn" aria-label={t("common.close")} onClick={() => void leaveFull()}><Icon name="x" size={22} /><span className="lbl">{t("common.close")}</span></button>
            <span className="phint">{t("player.osdHint")}</span>
          </div>
        </div>
      ) : (
        <div className={`pbot${full ? " vod" : ""}`}>
          {full && skipOffer?.next && skipNext && panel === null && (
            <div className="pnextep">
              <button className="nx" onClick={(e) => { e.stopPropagation(); skipRef.current(); }}>
                <span className="th"><Img src={skipNext.image} />{skipNext.episode != null && <span className="ep">{t("common.episodeShort", { n: skipNext.episode })}</span>}</span>
                <span className="tx">
                  <span className="nx-t">{t("player.nextEpisode")}</span>
                  <b className="ellipsis">{skipNext.title}</b>
                  {skipNext.subtitle && <span className="ellipsis nx-s">{skipNext.subtitle}</span>}
                </span>
              </button>
            </div>
          )}
          {full && cur.d > 0 && (
            <div className="seek-wrap">
              <div className="seek mono">
                <div className="bar" onClick={onSeekBar} onMouseMove={onSeekHover} onMouseLeave={() => setHoverT(null)} role="slider" aria-label={t("a11y.position")} aria-valuenow={Math.round(vodRatio * 100)}>
                  <div className="buf" style={{ width: `${(cur.buf / cur.d) * 100}%` }} />
                  <div className="fill" style={{ width: `${vodRatio * 100}%` }} />
                  <div className="knob" style={{ insetInlineStart: `${vodRatio * 100}%` }} />
                  {scrub && (
                    <>
                      <div className="scrub-mark" aria-label={t("player.seekTarget")} style={{ insetInlineStart: `${(scrub.target / cur.d) * 100}%` }} />
                      <span className="bubble scrub" style={{ insetInlineStart: `${(scrub.target / cur.d) * 100}%` }}>{fmtClock(scrub.target)} <i>{fmtDelta(scrub.target - scrub.origin)}</i></span>
                    </>
                  )}
                  {hoverT && !scrub && <span className="bubble" style={{ insetInlineStart: `${hoverT.f * 100}%` }}>{fmtClock(hoverT.f * cur.d)}</span>}
                </div>
              </div>
              <div className="seek-meta mono">
                <span>{fmtClock(cur.t)}</span>
                <span className="mid">{t("player.remainingEnds", { rest: fmtDuration(Math.max(0, cur.d - cur.t)), time: hhmm(Date.now() + Math.max(0, cur.d - cur.t) * 1000, locale) })}</span>
                <span>{fmtClock(cur.d)}</span>
              </div>
            </div>
          )}
          <div className="pctl">
            <div className="grp">
              {full && <button className="pbtn" aria-label={t("player.back10")} onClick={() => seekBy(-10)}><Icon name="rew" size={22} /></button>}
              <button className={full ? "pbtn big" : "pbtn"} aria-label={t("player.pause")} onClick={togglePlay}>
                <Icon name={state === "paused" ? "play" : "pause"} size={full ? 26 : 20} fill />
              </button>
              {full && <button className="pbtn" aria-label={t("player.fwd10")} onClick={() => seekBy(10)}><Icon name="fwd" size={22} /></button>}
              {full && skipNext && target.kind === "episode" && (
                <button className="pbtn" aria-label={t("player.nextEpisode")} onClick={() => usePlayer.getState().open(skipNext, "full")}><Icon name="chevron" size={22} /></button>
              )}
              <button className="pbtn" aria-label={t("a11y.volume")} onClick={toggleMute}><Icon name={videoRef.current?.muted ? "mute" : "volume"} size={20} /></button>
            </div>
            <div className="grp">
              {full ? (
                <>
                  {target.kind === "episode" && target.seriesId != null && (
                    <button className={`popt${panel === "episodes" ? " on" : ""}`} aria-expanded={panel === "episodes"} onClick={() => setPanel(panel === "episodes" ? null : "episodes")}><Icon name="series" size={18} />{t("player.episodes")}</button>
                  )}
                  <button className={`popt${panel === "tracks" ? " on" : ""}`} onClick={() => setPanel(panel === "tracks" ? null : "tracks")}><Icon name="sliders" size={18} />{t("player.tracks")}</button>
                  <button className={`popt${panel === "display" ? " on" : ""}`} onClick={() => setPanel(panel === "display" ? null : "display")}><Icon name="display" size={18} />{t("player.display")}</button>
                  <button className={`popt${panel === "player" || panel === "stats" ? " on" : ""}`} onClick={() => setPanel(panel === "player" ? null : "player")}><Icon name="info" size={18} />{t("player.player")}</button>
                  <button className="pbtn" aria-label={t("player.pip")} onClick={() => void pip()}><Icon name="pip" size={20} /></button>
                  <button className="pbtn" aria-label={t("player.fullscreen")} onClick={() => void toggleOsFull()}><Icon name="full" size={20} /></button>
                  <button className="pbtn" aria-label={t("common.close")} onClick={() => void leaveFull()}><Icon name="x" size={20} /></button>
                </>
              ) : (
                <button className="pbtn" aria-label={t("player.fullscreen")} onClick={goFull}><Icon name="full" size={20} /></button>
              )}
            </div>
          </div>
        </div>
      )}

      {full && panel && panel !== "channels" && panel !== "episodes" && (
        <aside className="pside" aria-label={t("player.player")}>
          <div className="tabs" role="tablist">
            {(["tracks", "display", "player", "stats"] as const).map((p) => (
              <button key={p} role="tab" aria-selected={panel === p} onClick={() => setPanel(p)}>{t(`player.${p}` as "player.tracks")}</button>
            ))}
          </div>
          {panel === "tracks" && (
            <>
              <div className="grp-t">{t("player.quality")}</div>
              {tracks.levels.length === 0 ? <div className="muted">{t("player.noTracks")}</div> : (
                <>
                  <button className="opt" role="radio" aria-checked={tracks.activeLevel === -1} onClick={() => engineRef.current?.setLevel(-1)}>{t("player.auto")}</button>
                  {tracks.levels.map((l) => <button key={l.id} className="opt" role="radio" aria-checked={tracks.activeLevel === l.id} onClick={() => engineRef.current?.setLevel(l.id)}>{l.label}</button>)}
                </>
              )}
              {tracks.audio.length > 1 && (
                <>
                  <div className="grp-t">{t("player.audio")}</div>
                  {tracks.audio.map((a) => <button key={a.id} className="opt" role="radio" aria-checked={tracks.activeAudio === a.id} onClick={() => engineRef.current?.setAudio(a.id)}>{a.label}<span className="h">{a.lang}</span></button>)}
                </>
              )}
              {tracks.text.length > 0 && (
                <>
                  <div className="grp-t">{t("player.subtitles")}</div>
                  <button className="opt" role="radio" aria-checked={tracks.activeText === -1} onClick={() => engineRef.current?.setText(-1)}>{t("player.off")}</button>
                  {tracks.text.map((a) => <button key={a.id} className="opt" role="radio" aria-checked={tracks.activeText === a.id} onClick={() => engineRef.current?.setText(a.id)}>{a.label}</button>)}
                </>
              )}
            </>
          )}
          {panel === "display" && (
            <>
              <div className="grp-t">{t("player.fit")}</div>
              {(["contain", "cover", "fill"] as const).map((f) => (
                <button key={f} className="opt" role="radio" aria-checked={prefs.fit === f} onClick={() => prefs.set({ fit: f })}>
                  {t(f === "contain" ? "player.fitContain" : f === "cover" ? "player.fitCover" : "player.fitFill")}
                </button>
              ))}
              {!live && (
                <>
                  <div className="grp-t">{t("player.speed")}</div>
                  {[0.75, 1, 1.25, 1.5, 2].map((r) => (
                    <button key={r} className="opt" role="radio" aria-checked={rate === r} onClick={() => { videoRef.current!.playbackRate = r; setRate(r); }}>{r}×</button>
                  ))}
                </>
              )}
            </>
          )}
          {panel === "player" && (
            <>
              <div className="grp-t">{t("player.shortcuts")}</div>
              <div className="hotkeys">
                <span><span className="kbd">{t("key.space")}</span></span><span>{t("player.sk.space")}</span>
                {live && <><span><span className="kbd">{t("key.enter")}</span></span><span>{t("player.channels")}</span></>}
                <span><span className="kbd">←</span> <span className="kbd">→</span></span><span>{live ? t("player.sk.zap") : t("player.sk.arrows")}</span>
                <span><span className="kbd">↑</span> <span className="kbd">↓</span></span><span>{live ? t("player.sk.osd") : t("player.sk.vol")}</span>
                {live && <><span><span className="kbd">0-9</span></span><span>{t("player.chNumber")}</span></>}
                <span><span className="kbd">F</span></span><span>{t("player.sk.full")}</span>
                <span><span className="kbd">P</span></span><span>{t("player.sk.pip")}</span>
                <span><span className="kbd">M</span></span><span>{t("player.sk.mute")}</span>
                <span><span className="kbd">I</span></span><span>{t("player.stats")}</span>
                {target.kind === "episode" && <><span><span className="kbd">E</span></span><span>{t("player.sk.episodes")}</span></>}
                <span><span className="kbd">{t("key.esc")}</span></span><span>{t("player.sk.esc")}</span>
              </div>
              <div className="grp-t">{t("player.stats")}</div>
              <button className="opt" onClick={() => setPanel("stats")}>{t("player.stats")}<Icon name="chevron" size={16} /></button>
            </>
          )}
          {panel === "stats" && stats && (
            <>
              <div className="grp-t">{t("player.stats").toUpperCase()}</div>
              <div className="stat"><span>{t("player.stat.engine")}</span><b>{stats.engine}</b></div>
              <div className="stat"><span>{t("player.stat.res")}</span><b>{stats.width && stats.height ? `${stats.width}×${stats.height}` : "—"}</b></div>
              <div className="stat"><span>{t("player.stat.bitrate")}</span><b>{stats.bitrate ? `${(stats.bitrate / 1e6).toFixed(1)} Mb/s` : "—"}</b></div>
              <div className="stat"><span>{t("player.stat.buffer")}</span><b>{stats.buffer.toFixed(1)} s</b></div>
              <div className="stat"><span>{t("player.stat.dropped")}</span><b>{stats.dropped}</b></div>
              {stats.codec && <div className="stat"><span>{t("player.stat.codec")}</span><b>{stats.codec}</b></div>}
            </>
          )}
        </aside>
      )}
      {full && panel === "channels" && live && <ChannelsPanel onZap={() => setPanel(null)} cid={target.cid} cat={zapCat} />}
      {full && panel === "episodes" && target.kind === "episode" && <EpisodesPanel target={target} onClose={() => setPanel(null)} />}
    </div>
  );
}

/** Programme en cours d'une chaîne de la liste (lu au guide local, mis en cache 60 s). */
const rowNowCache = new Map<string, { at: number; title: string; ratio: number }>();
function useRowNow(cid: number, epg: string | null): { title: string; ratio: number } | null {
  const [v, setV] = useState<{ title: string; ratio: number } | null>(() => {
    const hit = epg ? rowNowCache.get(`${cid}:${epg}`) : undefined;
    return hit && Date.now() - hit.at < 60_000 ? hit : null;
  });
  useEffect(() => {
    if (!epg) { setV(null); return; }
    const key = `${cid}:${epg}`;
    const hit = rowNowCache.get(key);
    if (hit && Date.now() - hit.at < 60_000) { setV(hit); return; }
    let dead = false;
    void nowNext(cid, [epg]).then((m) => {
      const n = m.get(epg)?.now;
      const r = n ? { at: Date.now(), title: n.title, ratio: Math.min(1, Math.max(0, (Date.now() - n.start) / Math.max(1, n.end - n.start))) } : null;
      if (r) rowNowCache.set(key, r);
      if (!dead) setV(r);
    }).catch(() => {});
    return () => { dead = true; };
  }, [cid, epg]);
  return v;
}

/** Ligne de la liste « Chaînes » : mémoïsée, seule la chaîne courante change d'état au zapping. */
const ChannelRowBtn = memo(function ChannelRowBtn({ c, cid, on, onPick }: { c: ChannelRow; cid: number; on: boolean; onPick: (c: ChannelRow) => void }) {
  const t = useT();
  const now = useRowNow(cid, c.epg);
  return (
    <button className="opt ch" role="radio" aria-checked={on} data-cur={on ? "1" : undefined} onClick={() => onPick(c)}>
      <span className="num">{c.num}</span>
      <span className="logo" aria-hidden="true">
        <span className="ini">{(c.display.match(/[\p{L}\p{N}]/gu) ?? []).slice(0, 2).join("").toUpperCase()}</span>
        <Img src={c.logo} contain />
      </span>
      <span className="tx">
        <span className="ellipsis nm">{c.display}</span>
        {now && <span className="ellipsis now">{now.title}</span>}
        {now && <span className="trk"><i style={{ width: `${now.ratio * 100}%` }} /></span>}
      </span>
      {on && <span className="badge">{t("search.onAir")}</span>}
    </button>
  );
});

function ChannelsPanel({ cid, cat, onZap }: { cid: number; cat: string | null; onZap: () => void }) {
  const t = useT();
  const [rows, setRows] = useState<ChannelRow[]>([]);
  const cur = usePlayer((s) => s.target);
  const sources = useSources((s) => s.list);
  const source = sources.find((s) => s.id === cur?.sourceId);
  const list = useRef<VListHandle>(null);
  const curId = cur?.refId;
  // Onglets : « Chaînes » (catégorie choisie) ou « Favoris » (filtre favoris existant).
  const [tab, setTab] = useState<"all" | "fav">(cat === FAVORITES_SCOPE ? "fav" : "all");
  const favs = useFavorites(source, "live");
  // Catégorie de la chaîne en cours si aucune n'a été choisie (lancée depuis l'accueil, la recherche…).
  const [catSel, setCatSel] = useState<string | null | undefined>(undefined);
  const effCat = catSel !== undefined ? catSel : (cat === FAVORITES_SCOPE ? null : cat) ?? cur?.channel?.catExt ?? null;
  const profile = usePrefs((s) => s.profileId);
  const cats = useCategories(source, "live");
  const hid = useHiddenCats(source, "live");
  useEffect(() => {
    if (tab === "fav") {
      void orderedList(profile, (source?.id ?? 0), cid, FAVORITES_SCOPE).then((r) => setRows(r ?? []));
      return;
    }
    // Toute la catégorie (plafond large) : la chaîne regardée peut être loin du début (ex. n° 10965). Ordre personnel appliqué s'il existe.
    void (async () => {
      const own = effCat ? await orderedList(profile, (source?.id ?? 0), cid, effCat) : null;
      setRows(own ?? (await channelsCol(cid, effCat).filter((c) => !c.sep).limit(5000).toArray()));
    })();
  }, [cid, effCat, tab, favs, profile, source?.id]);
  // Liste virtualisée : seules les lignes visibles existent dans le DOM (jusqu'à 5000 chaînes).
  const arr = useMemo(() => arrayRows(rows), [rows]);
  const pick = useCallback((c: ChannelRow) => {
    const tg = usePlayer.getState().target;
    if (!tg) return;
    usePlayer.getState().open({
      ...tg, refId: c.streamId, title: c.display, image: c.logo, url: c.url, replay: undefined,
      channel: { ord: c.ord, catExt: c.catExt, num: c.num, epg: c.epg, archive: !!c.archive, q: c.q, logo: c.logo },
    }, "full");
    onZap();
  }, [onZap]);
  // À l'ouverture : centré sur la chaîne regardée, qui a le focus (Entrée = rester, ↑↓ = parcourir).
  const centered = useRef(false);
  useEffect(() => {
    if (centered.current || rows.length === 0) return;
    const k = rows.findIndex((c) => c.streamId === curId);
    if (k < 0) return;
    centered.current = true;
    list.current?.scrollTo(k, "center");
    requestAnimationFrame(() => requestAnimationFrame(() => {
      list.current?.el?.querySelector<HTMLButtonElement>("button[data-cur]")?.focus({ preventScroll: true });
    }));
  }, [rows, curId]);
  return (
    <aside className="pside pch" aria-label={t("player.channels")}>
      <div className="pch-head">
        <h3>{t("player.channels")}</h3>
        <div className="pch-tabs" role="tablist" aria-label={t("player.channels")}>
          <button role="tab" aria-selected={tab === "all"} onClick={() => { centered.current = false; setTab("all"); }}>{t("player.channels")}</button>
          <button role="tab" aria-selected={tab === "fav"} onClick={() => setTab("fav")}>{t("common.favorites")}</button>
        </div>
      </div>
      <div className="pch-body">
        {tab === "all" && (
          <div className="pch-cats" role="group" aria-label={t("common.categories")}>
            <button className={`cat${effCat == null ? " on" : ""}`} onClick={() => { centered.current = true; setCatSel(null); }}><span className="ellipsis">{t("common.allCategories")}</span></button>
            {(cats ?? []).map((c) => (
              <button key={c.extId} className={`cat${effCat === c.extId ? " on" : ""}`} onClick={() => { centered.current = true; setCatSel(c.extId); }}>
                <span className="ellipsis">{c.label}</span><span className="n">{c.count}</span>
              </button>
            ))}
          </div>
        )}
        <VList ref={list} rows={arr} rowH={76} className="vlist pch-list" label={t("player.channels")}
          render={(c) => (c ? <ChannelRowBtn c={c} cid={cid} on={curId === c.streamId} onPick={pick} /> : null)} />
      </div>
    </aside>
  );
}
