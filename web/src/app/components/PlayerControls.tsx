// Custom playback controls overlay — seek bar, PiP, fullscreen, speed, audio/sub
// tracks, external sub upload. Also wires keyboard shortcuts: space (play/pause),
// arrows (seek), F (fullscreen), M (mute), P (pip), [ ] (speed -/+).

import { useEffect, useRef, useState } from "react";
import type { WebPlayerEngine, PlayerTrack } from "@player/PlayerEngine";
import { CastButton } from "@app/components/CastButton";

interface Props {
  videoRef: React.MutableRefObject<HTMLVideoElement | null>;
  engineRef: React.MutableRefObject<WebPlayerEngine | null>;
  containerRef: React.RefObject<HTMLElement>;
  castUrl?: string | null;
  castTitle?: string;
}

const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return "--:--";
  const s = Math.floor(seconds % 60);
  const m = Math.floor((seconds / 60) % 60);
  const h = Math.floor(seconds / 3600);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

export function PlayerControls({ videoRef, engineRef, containerRef, castUrl, castTitle }: Props) {
  const [speed, setSpeed] = useState(1);
  const [audioTracks, setAudioTracks] = useState<PlayerTrack[]>([]);
  const [textTracks, setTextTracks] = useState<PlayerTrack[]>([]);
  const [audioTrack, setAudioTrackId] = useState<string | null>(null);
  const [textTrack, setTextTrackId] = useState<string | null>(null);
  const [muted, setMuted] = useState(false);
  const [paused, setPaused] = useState(true);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [scrubbing, setScrubbing] = useState(false);
  const [scrubValue, setScrubValue] = useState(0);
  const fileRef = useRef<HTMLInputElement | null>(null);

  // Sync local state with video element at 4 Hz — enough for a smooth-looking
  // seek bar, far less CPU than rAF (which would re-render this component on every frame).
  useEffect(() => {
    const id = setInterval(() => {
      const v = videoRef.current;
      if (!v) return;
      if (!scrubbing) setCurrentTime(v.currentTime);
      setDuration(v.duration || 0);
      setPaused(v.paused);
      setMuted(v.muted);
    }, 250);
    return () => clearInterval(id);
  }, [videoRef, scrubbing]);

  // Refresh track lists periodically (HLS adds them after manifest parse).
  useEffect(() => {
    const id = setInterval(() => {
      if (!engineRef.current) return;
      setAudioTracks(engineRef.current.getAudioTracks());
      setTextTracks(engineRef.current.getTextTracks());
    }, 1500);
    return () => clearInterval(id);
  }, [engineRef]);

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      const v = videoRef.current;
      const eng = engineRef.current;
      if (!v || !eng) return;

      switch (e.key) {
        case " ":
        case "k":
          e.preventDefault();
          v.paused ? v.play() : v.pause();
          break;
        case "ArrowLeft":
          e.preventDefault();
          v.currentTime = Math.max(0, v.currentTime - 10);
          break;
        case "ArrowRight":
          e.preventDefault();
          v.currentTime = Math.min(v.duration || Infinity, v.currentTime + 10);
          break;
        case "j":
          v.currentTime = Math.max(0, v.currentTime - 30);
          break;
        case "l":
          v.currentTime = Math.min(v.duration || Infinity, v.currentTime + 30);
          break;
        case "f":
          void eng.toggleFullscreen(containerRef.current);
          break;
        case "p":
          void eng.togglePictureInPicture();
          break;
        case "m":
          v.muted = !v.muted;
          setMuted(v.muted);
          break;
        case "[":
          changeSpeed(-1);
          break;
        case "]":
          changeSpeed(1);
          break;
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const changeSpeed = (dir: -1 | 1 | 0, exact?: number) => {
    const eng = engineRef.current;
    if (!eng) return;
    let next = speed;
    if (exact != null) next = exact;
    else {
      const idx = SPEEDS.indexOf(speed);
      const newIdx = Math.max(0, Math.min(SPEEDS.length - 1, idx + dir));
      next = SPEEDS[newIdx] ?? 1;
    }
    eng.setPlaybackRate(next);
    setSpeed(next);
  };

  const onSubtitleFile = (file: File | null | undefined) => {
    if (!file || !engineRef.current) return;
    const url = URL.createObjectURL(file);
    engineRef.current.addExternalSubtitle(url, file.name, "");
    setTimeout(() => {
      const tracks = engineRef.current?.getTextTracks() ?? [];
      const last = tracks[tracks.length - 1];
      if (last) {
        engineRef.current?.setTextTrack(last.id);
        setTextTrackId(last.id);
        setTextTracks(tracks);
      }
    }, 200);
  };

  const onSeekInput = (val: number) => {
    setScrubbing(true);
    setScrubValue(val);
  };
  const onSeekCommit = (val: number) => {
    const v = videoRef.current;
    if (v && Number.isFinite(v.duration) && v.duration > 0) {
      v.currentTime = val;
    }
    setScrubbing(false);
  };

  const isLive = !Number.isFinite(duration) || duration === 0 || duration === Infinity;
  const sliderValue = scrubbing ? scrubValue : currentTime;
  const progressPct = duration > 0 ? (sliderValue / duration) * 100 : 0;

  return (
    <div className="player-controls-wrap">
      {/* Seek bar — hidden for live streams that have no fixed duration */}
      {!isLive && (
        <div className="player-seek">
          <span className="player-time">{formatTime(sliderValue)}</span>
          <input
            type="range"
            className="player-seek-bar"
            min={0}
            max={duration || 0}
            step={0.1}
            value={Math.min(sliderValue, duration || 0)}
            onChange={(e) => onSeekInput(Number(e.target.value))}
            onMouseUp={(e) => onSeekCommit(Number((e.target as HTMLInputElement).value))}
            onTouchEnd={(e) => onSeekCommit(Number((e.target as HTMLInputElement).value))}
            style={{ ['--progress' as string]: `${progressPct}%` } as React.CSSProperties}
          />
          <span className="player-time">{formatTime(duration)}</span>
        </div>
      )}

      <div className="player-controls">
        <button onClick={() => { const v = videoRef.current; if (v) { v.paused ? v.play() : v.pause(); } }} title="Play/Pause (space)">
          {paused ? "▶" : "⏸"}
        </button>
        <button onClick={() => { const v = videoRef.current; if (v) v.currentTime = Math.max(0, v.currentTime - 10); }} title="-10s (←)">⏪</button>
        <button onClick={() => { const v = videoRef.current; if (v) v.currentTime += 10; }} title="+10s (→)">⏩</button>

        <select value={speed} onChange={(e) => changeSpeed(0, Number(e.target.value))} title="Playback speed">
          {SPEEDS.map((s) => <option key={s} value={s}>{s}×</option>)}
        </select>

        {audioTracks.length > 1 && (
          <select
            value={audioTrack ?? ""}
            onChange={(e) => { engineRef.current?.setAudioTrack(e.target.value); setAudioTrackId(e.target.value); }}
            title="Audio track"
          >
            {audioTracks.map((tr) => <option key={tr.id} value={tr.id}>🎵 {tr.label}</option>)}
          </select>
        )}

        {textTracks.length > 0 && (
          <select
            value={textTrack ?? ""}
            onChange={(e) => { const v = e.target.value || null; engineRef.current?.setTextTrack(v); setTextTrackId(v); }}
            title="Subtitles"
          >
            <option value="">No subs</option>
            {textTracks.map((tr) => <option key={tr.id} value={tr.id}>📝 {tr.label}</option>)}
          </select>
        )}

        <button onClick={() => fileRef.current?.click()} title="Load external subtitle (.vtt / .srt)">+Sub</button>
        <input ref={fileRef} type="file" accept=".vtt,.srt,text/vtt" style={{ display: "none" }} onChange={(e) => onSubtitleFile(e.target.files?.[0])} />

        <button onClick={() => { const v = videoRef.current; if (v) { v.muted = !v.muted; setMuted(v.muted); } }} title="Mute (m)">{muted ? "🔇" : "🔊"}</button>
        <CastButton videoRef={videoRef} title={castTitle ?? "Ultra TV"} url={castUrl ?? null} />
        <button onClick={() => engineRef.current?.togglePictureInPicture()} title="Picture in Picture (p)">⧉</button>
        <button onClick={() => engineRef.current?.toggleFullscreen(containerRef.current)} title="Fullscreen (f)">⛶</button>
      </div>
    </div>
  );
}
