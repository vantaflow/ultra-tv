// Player stats overlay — bitrate, resolution, dropped frames, buffer health.
// Toggled with Shift+D (or via a future debug button). Read directly off the
// HTMLVideoElement's getVideoPlaybackQuality() API + hls.js stats when available.

import { useEffect, useState } from "react";
import type { WebPlayerEngine } from "@player/PlayerEngine";

interface Props {
  videoRef: React.MutableRefObject<HTMLVideoElement | null>;
  engineRef: React.MutableRefObject<WebPlayerEngine | null>;
}

interface Stats {
  width: number;
  height: number;
  droppedFrames: number;
  totalFrames: number;
  decodedFrames: number;
  bufferAhead: number; // seconds
  currentTime: number;
  duration: number;
  bitrateKbps: number | null;
  networkActivity: string | null;
}

export function PlayerStats({ videoRef, engineRef }: Props) {
  const [visible, setVisible] = useState(false);
  const [stats, setStats] = useState<Stats | null>(null);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      if (e.shiftKey && (e.key === "D" || e.key === "d")) {
        setVisible((v) => !v);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  useEffect(() => {
    if (!visible) { setStats(null); return; }
    const id = setInterval(() => {
      const v = videoRef.current;
      const eng = engineRef.current as unknown as { hls?: { stats?: { total?: { bw?: number } }; levels?: Array<{ bitrate: number }>; currentLevel?: number } };
      if (!v) return;
      const quality = (v as unknown as { getVideoPlaybackQuality?: () => { droppedVideoFrames: number; totalVideoFrames: number } }).getVideoPlaybackQuality?.();
      const buffered = v.buffered.length > 0 ? v.buffered.end(v.buffered.length - 1) - v.currentTime : 0;
      const hls = eng?.hls;
      const lvl = hls?.currentLevel != null && hls.levels ? hls.levels[hls.currentLevel] : undefined;
      setStats({
        width: v.videoWidth,
        height: v.videoHeight,
        droppedFrames: quality?.droppedVideoFrames ?? 0,
        totalFrames: quality?.totalVideoFrames ?? 0,
        decodedFrames: (quality?.totalVideoFrames ?? 0) - (quality?.droppedVideoFrames ?? 0),
        bufferAhead: Math.max(0, buffered),
        currentTime: v.currentTime,
        duration: v.duration || 0,
        bitrateKbps: lvl ? Math.round(lvl.bitrate / 1000) : null,
        networkActivity: v.networkState === 2 ? "loading" : v.networkState === 1 ? "idle" : "no source",
      });
    }, 500);
    return () => clearInterval(id);
  }, [visible, videoRef, engineRef]);

  if (!visible || !stats) return null;
  return (
    <div className="player-stats">
      <div><strong>Resolution:</strong> {stats.width}×{stats.height}</div>
      <div><strong>Bitrate:</strong> {stats.bitrateKbps ?? "—"} kbps</div>
      <div><strong>Frames:</strong> {stats.decodedFrames} / {stats.totalFrames} (dropped {stats.droppedFrames})</div>
      <div><strong>Buffer ahead:</strong> {stats.bufferAhead.toFixed(1)}s</div>
      <div><strong>Network:</strong> {stats.networkActivity}</div>
      <div style={{ marginTop: 6, fontSize: 10, opacity: 0.6 }}>Shift+D to toggle</div>
    </div>
  );
}
