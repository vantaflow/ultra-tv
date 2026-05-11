// Video element + PlayerEngine glue. Optionally tracks playback position to
// historyRepo so the Home → Continue Watching rail stays accurate.

import { useEffect, useRef } from "react";
import { WebPlayerEngine } from "@player/PlayerEngine";
import { historyRepo } from "@data/db/repositories";
import type { ContentType, PlaybackHistory, StreamInfo } from "@domain/model";

interface TrackingMeta {
  providerId: number;
  contentId: number;
  contentType: ContentType;
  title: string;
  posterUrl: string | null;
  streamUrl: string;
  seriesId?: number | null;
  seasonNumber?: number | null;
  episodeNumber?: number | null;
}

interface Props {
  stream: StreamInfo | null;
  tracking?: TrackingMeta | null;
  /** Initial resume position (ms). Applied once after load. */
  resumeMs?: number;
  /** Show native controls (we replace these with custom controls when false). */
  nativeControls?: boolean;
  /** Expose a ref to the underlying video element (for custom controls / recording). */
  videoRef?: React.MutableRefObject<HTMLVideoElement | null>;
  /** Expose the engine for custom-control commands. */
  engineRef?: React.MutableRefObject<WebPlayerEngine | null>;
  onEnded?: () => void;
  onError?: (message: string) => void;
}

const WRITE_THROTTLE_MS = 10_000;
const COMPLETE_THRESHOLD = 0.95;

export function VideoPlayer({ stream, tracking, resumeMs, nativeControls = true, videoRef, engineRef, onEnded, onError }: Props) {
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const localEngineRef = useRef<WebPlayerEngine | null>(null);
  const lastWriteRef = useRef(0);
  const trackingRef = useRef(tracking);
  trackingRef.current = tracking;

  useEffect(() => {
    if (!localVideoRef.current) return;
    const engine = new WebPlayerEngine({
      onError: (e) => { console.error("[player]", e); onError?.(e.message); },
      onTimeUpdate: (positionMs, durationMs) => {
        const meta = trackingRef.current;
        if (!meta || !durationMs || !Number.isFinite(durationMs)) return;
        const now = Date.now();
        if (now - lastWriteRef.current < WRITE_THROTTLE_MS) return;
        lastWriteRef.current = now;

        const watched = positionMs / durationMs >= COMPLETE_THRESHOLD;
        void historyRepo.byContent(meta.providerId, meta.contentId).then((existing) => {
          const merged: PlaybackHistory = {
            id: existing?.id ?? 0,
            contentId: meta.contentId,
            contentType: meta.contentType,
            providerId: meta.providerId,
            title: meta.title,
            posterUrl: meta.posterUrl,
            streamUrl: meta.streamUrl,
            resumePositionMs: watched ? 0 : positionMs,
            totalDurationMs: durationMs,
            lastWatchedAt: now,
            watchCount: (existing?.watchCount ?? 0) + (existing ? 0 : 1),
            watchedStatus: watched ? "COMPLETED_AUTO" : "IN_PROGRESS",
            seriesId: meta.seriesId ?? null,
            seasonNumber: meta.seasonNumber ?? null,
            episodeNumber: meta.episodeNumber ?? null,
          };
          void historyRepo.upsert(merged);
        });
      },
    });
    engine.attach(localVideoRef.current);
    localEngineRef.current = engine;
    if (videoRef) videoRef.current = localVideoRef.current;
    if (engineRef) engineRef.current = engine;

    const onEndedHandler = () => onEnded?.();
    localVideoRef.current.addEventListener("ended", onEndedHandler);
    return () => {
      localVideoRef.current?.removeEventListener("ended", onEndedHandler);
      engine.release();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!localEngineRef.current || !stream) return;
    void localEngineRef.current.load(stream).then(() => {
      if (resumeMs && localVideoRef.current) {
        // Wait briefly for duration to be known before seeking
        const seek = () => {
          if (!localVideoRef.current) return;
          if (Number.isFinite(localVideoRef.current.duration) && localVideoRef.current.duration > 0) {
            localVideoRef.current.currentTime = resumeMs / 1000;
          } else {
            setTimeout(seek, 300);
          }
        };
        seek();
      }
      void localEngineRef.current?.play();
    });
  }, [stream, resumeMs]);

  return (
    <div className="player-wrap">
      <video ref={localVideoRef} controls={nativeControls} playsInline />
    </div>
  );
}
