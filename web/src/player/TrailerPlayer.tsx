// Fenêtre plein écran de bande-annonce : le lecteur officiel YouTube (iframe `youtube-nocookie`, API par postMessage) joue
// la vidéo sans ses commandes (controls=0) ; les commandes affichées sont les nôtres. Le logo YouTube et les éventuelles
// publicités restent ceux du lecteur (conditions de l'API) : on n'extrait pas le flux, on ne masque rien.
import { useCallback, useEffect, useRef, useState } from "react";
import { useT } from "@/i18n";
import { fmtClock } from "@/lib/text";
import { Icon } from "@/ui/Icon";
import { createOsdIdle } from "./osdIdle";
import {
  TRAILER_SKIP_S, YT_ORIGINS, createTrailerController, createYoutubePostMessagePlayer, parseYoutubeMessage, progressRatio,
  trailerEmbedUrl, type TrailerController, type TrailerSnapshot,
} from "./trailerController";

/** Sans aucun message du lecteur au bout de ce délai (hors ligne, intégration bloquée) : erreur claire plutôt qu'un écran noir. */
const NO_ANSWER_MS = 15000;

export function TrailerPlayer({ ytKey, title, onClose }: { ytKey: string; title: string; onClose: () => void }) {
  const t = useT();
  const frameRef = useRef<HTMLIFrameElement>(null);
  const playRef = useRef<HTMLButtonElement>(null);
  const ctlRef = useRef<TrailerController | null>(null);
  const [snap, setSnap] = useState<TrailerSnapshot>({ phase: "loading", position: 0, duration: 0, error: null });
  const [ui, setUi] = useState(true);
  const idle = useRef(createOsdIdle()).current;
  const hideTimer = useRef(0);

  // --- lecteur : poignée de main, messages entrants, délai de réponse ---
  useEffect(() => {
    const frame = frameRef.current;
    if (!frame) return;
    const player = createYoutubePostMessagePlayer({ postMessage: (m, o) => frame.contentWindow?.postMessage(m, o) });
    const ctl = createTrailerController(player);
    ctlRef.current = ctl;
    const off = ctl.subscribe(setSnap);
    let answered = false;
    const hello = () => { player.listen(); player.poll(); };
    const onMessage = (e: MessageEvent) => {
      if (e.source !== frame.contentWindow || !(YT_ORIGINS as readonly string[]).includes(e.origin)) return;
      answered = true;
      for (const ev of parseYoutubeMessage(e.data)) ctl.handle(ev);
    };
    window.addEventListener("message", onMessage);
    frame.addEventListener("load", hello);
    // Le lecteur ne parle qu'après avoir reçu « listening » : on le répète jusqu'à la première réponse.
    const hs = window.setInterval(() => { if (answered) window.clearInterval(hs); else hello(); }, 500);
    const giveUp = window.setTimeout(() => { if (!answered) ctl.handle({ type: "error", code: 0 }); }, NO_ANSWER_MS);
    return () => {
      window.removeEventListener("message", onMessage);
      frame.removeEventListener("load", hello);
      window.clearInterval(hs);
      window.clearTimeout(giveUp);
      off();
      ctlRef.current = null;
    };
  }, [ytKey]);

  // --- OSD : apparition au vrai mouvement, masquage après 3 s (même logique que le lecteur principal) ---
  const armHide = useCallback(() => {
    window.clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => {
      if (idle.shouldHide()) setUi(false); else armHide();
    }, Math.max(250, idle.remaining()));
  }, [idle]);
  const poke = useCallback(() => { idle.activity(); setUi(true); armHide(); }, [idle, armHide]);
  useEffect(() => { armHide(); return () => window.clearTimeout(hideTimer.current); }, [armHide]);
  const onMove = (e: React.MouseEvent) => { if (idle.move(e.screenX, e.screenY, e.movementX, e.movementY)) poke(); };
  const holdOver = (e: React.SyntheticEvent) => idle.setHold(!!(e.target as HTMLElement).closest?.(".pbot,.ptop button"));

  // --- clavier : Espace, ←/→, Échap (capturés avant les raccourcis globaux de l'appli) ---
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    playRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const c = ctlRef.current;
      let handled = true;
      if (e.key === "Escape") { if (e.type === "keydown") onClose(); }
      else if (e.key === " " || e.key === "k" || e.key === "K") { if (e.type === "keydown" && !e.repeat) c?.toggle(); }
      else if (e.key === "ArrowLeft") { if (e.type === "keydown") c?.skip(-TRAILER_SKIP_S); }
      else if (e.key === "ArrowRight") { if (e.type === "keydown") c?.skip(TRAILER_SKIP_S); }
      else handled = false;
      if (handled) { e.preventDefault(); e.stopImmediatePropagation(); if (e.type === "keydown") poke(); }
    };
    window.addEventListener("keydown", onKey, true);
    window.addEventListener("keyup", onKey, true);
    return () => {
      window.removeEventListener("keydown", onKey, true);
      window.removeEventListener("keyup", onKey, true);
      opener?.focus?.();
    };
  }, [onClose, poke]);

  const { phase, position, duration, error } = snap;
  const ratio = progressRatio(position, duration);
  const onBar = (e: React.MouseEvent<HTMLDivElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    const f = (e.clientX - r.left) / r.width;
    ctlRef.current?.seekRatio(document.dir === "rtl" ? 1 - f : f);
  };
  const playing = phase === "playing" || phase === "buffering";
  const errText = error === "notFound" ? t("trailer.errNotFound") : error === "embedBlocked" ? t("trailer.errEmbed") : error === "config" ? t("trailer.errConfig") : t("trailer.errUnknown");

  return (
    <div
      className={`player full trailer${ui ? " ui" : " nocursor"}`}
      role="dialog" aria-modal="true" aria-label={title}
      onMouseMove={onMove} onMouseOver={holdOver} onMouseLeave={() => idle.setHold(false)}
      onFocus={holdOver} onBlur={() => idle.setHold(false)} onClickCapture={poke}
      onClick={(e) => { if (e.target === e.currentTarget && !error) ctlRef.current?.toggle(); }}
    >
      {!error && (
        <iframe
          ref={frameRef} title={title} src={trailerEmbedUrl(ytKey, window.location.origin)}
          allow="autoplay; encrypted-media; picture-in-picture" referrerPolicy="strict-origin-when-cross-origin"
          sandbox="allow-scripts allow-same-origin allow-presentation"
        />
      )}
      <div className="shade-top" /><div className="shade-bot" />
      {(phase === "loading" || phase === "buffering") && !error && (
        <div className="pstate"><div className="spinner" role="status" aria-label={t("trailer.loading")} /></div>
      )}
      {error && (
        <div className="pstate">
          <div className="msg" role="alert">
            <b>{t("trailer.button")}</b>
            <p style={{ margin: "0.5rem 0 1rem", color: "#c4c4cc", fontSize: "0.875rem" }}>{errText}</p>
            <button className="btn primary sm" autoFocus onClick={onClose}>{t("common.close")}</button>
          </div>
        </div>
      )}
      <div className="ptop">
        <div><h2>{title}</h2></div>
        <button className="pbtn" aria-label={t("common.close")} onClick={onClose}><Icon name="x" size={20} /></button>
      </div>
      <div className="pbot">
        <div className="seek mono">
          <span>{fmtClock(position)}</span>
          <div className="bar" onClick={onBar} role="slider" aria-label={t("a11y.position")} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(ratio * 100)}>
            <div className="fill" style={{ width: `${ratio * 100}%` }} />
            <div className="knob" style={{ insetInlineStart: `${ratio * 100}%` }} />
          </div>
          <span>{fmtClock(duration)}</span>
        </div>
        <div className="pctl" style={{ justifyContent: "center" }}>
          <div className="grp">
            <button className="pbtn" aria-label={t("player.back10")} onClick={() => ctlRef.current?.skip(-TRAILER_SKIP_S)}><Icon name="rew" size={22} /></button>
            <button ref={playRef} className="pbtn big" aria-label={t(playing ? "player.pause" : "common.play")} onClick={() => ctlRef.current?.toggle()}>
              <Icon name={playing ? "pause" : "play"} size={26} fill />
            </button>
            <button className="pbtn" aria-label={t("player.fwd10")} onClick={() => ctlRef.current?.skip(TRAILER_SKIP_S)}><Icon name="fwd" size={22} /></button>
          </div>
        </div>
      </div>
    </div>
  );
}
