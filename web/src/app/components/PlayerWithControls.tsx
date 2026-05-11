// Composite: VideoPlayer + custom PlayerControls underneath, plus an error overlay
// and automatic stream-candidate fallback.
//
// When playback fails we walk a list of alternative URLs (same stream, different
// container extensions — most Xtream servers expose .mp4 and .mkv side-by-side).
// Only when every candidate fails do we surface the error overlay to the user.

import { useEffect, useMemo, useRef, useState } from "react";
import { VideoPlayer } from "@app/components/VideoPlayer";
import { PlayerControls } from "@app/components/PlayerControls";
import { PlayerStats } from "@app/components/PlayerStats";
import { buildStreamCandidates } from "@player/streamCandidates";
import { checkAvailability, openInVlc, openInExoPlayer, openInExternalPlayer, isNativeAndroid, type PlayerAvailability } from "@data/native/externalPlayer";
import type { WebPlayerEngine } from "@player/PlayerEngine";
import type { ContentType, StreamInfo } from "@domain/model";
import { streamTypeFromUrl } from "@domain/model";

interface Props {
  stream: StreamInfo | null;
  tracking?: {
    providerId: number;
    contentId: number;
    contentType: ContentType;
    title: string;
    posterUrl: string | null;
    streamUrl: string;
    seriesId?: number | null;
    seasonNumber?: number | null;
    episodeNumber?: number | null;
  } | null;
  resumeMs?: number;
  onEnded?: () => void;
}

export function PlayerWithControls({ stream, tracking, resumeMs, onEnded }: Props) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const engineRef = useRef<WebPlayerEngine | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [candidateIdx, setCandidateIdx] = useState(0);
  const [streamKey, setStreamKey] = useState(0);

  const candidates = useMemo(() => {
    if (!stream) return [];
    const kind = tracking?.contentType === "LIVE" ? "LIVE" : "VOD";
    return buildStreamCandidates({ url: stream.url, containerExtension: stream.containerExtension, kind });
  }, [stream, tracking?.contentType]);

  // Reset to the first candidate whenever the source stream changes.
  useEffect(() => { setCandidateIdx(0); setError(null); }, [stream?.url]);

  // Auto-fallback: when an error fires, jump to the next candidate without bothering the user.
  const handleError = (msg: string) => {
    if (candidateIdx + 1 < candidates.length) {
      console.warn(`Playback failed on candidate ${candidateIdx + 1}/${candidates.length}, trying next:`, msg);
      setCandidateIdx((i) => i + 1);
      setStreamKey((k) => k + 1);
    } else {
      setError(msg);
    }
  };

  const activeStream: StreamInfo | null = useMemo(() => {
    if (!stream) return null;
    const url = candidates[candidateIdx] ?? stream.url;
    return { ...stream, url, streamType: streamTypeFromUrl(url) };
  }, [stream, candidates, candidateIdx]);

  const retry = () => { setError(null); setCandidateIdx(0); setStreamKey((k) => k + 1); };

  const openDirect = () => {
    if (activeStream?.url) window.open(activeStream.url, "_blank", "noreferrer");
  };

  const [nativePlayers, setNativePlayers] = useState<PlayerAvailability>({ vlc: false, exoPlayer: false, any: false });
  useEffect(() => { void checkAvailability().then(setNativePlayers); }, []);

  const handleOpenInVlc = async () => {
    if (!activeStream?.url) return;
    await openInVlc(activeStream.url, tracking?.title, activeStream.userAgent, activeStream.headers?.["Referer"] ?? null);
  };
  const handleOpenInExo = async () => {
    if (!activeStream?.url) return;
    await openInExoPlayer(activeStream.url, tracking?.title);
  };
  const handleOpenExternal = async () => {
    if (!activeStream?.url) return;
    await openInExternalPlayer(activeStream.url);
  };

  const copyUrl = async () => {
    if (!activeStream?.url) return;
    try { await navigator.clipboard.writeText(activeStream.url); } catch { /* noop */ }
  };

  const diagnostic = (msg: string): string => {
    if (/code=4/.test(msg)) return "Browser cannot decode this codec/container (likely H.265, AC3 or MKV).";
    if (/code=3/.test(msg)) return "Decoder failed — the stream may be corrupted or use unsupported features.";
    if (/code=2/.test(msg)) return "Network error — the proxy or upstream rejected the request.";
    if (/HLS fatal: networkError/i.test(msg)) return "HLS network error — likely 403/404/CORS on a segment.";
    if (/HLS fatal: mediaError/i.test(msg)) return "HLS media error — codec or buffering issue.";
    if (/timeout/i.test(msg)) return "Timeout — upstream too slow.";
    return msg;
  };

  return (
    <div ref={containerRef} className="player-with-controls">
      <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
        <VideoPlayer
          key={streamKey}
          stream={activeStream}
          tracking={tracking}
          resumeMs={resumeMs}
          nativeControls={false}
          videoRef={videoRef}
          engineRef={engineRef}
          onEnded={onEnded}
          onError={handleError}
        />
        {candidateIdx > 0 && !error && (
          <div className="player-fallback-pill">
            ⓘ Tried {candidateIdx} alternate{candidateIdx > 1 ? "s" : ""}
          </div>
        )}
        <PlayerStats videoRef={videoRef} engineRef={engineRef} />
        {error && (
          <div className="player-error-overlay">
            <div className="player-error-card">
              <div style={{ fontSize: 24, marginBottom: 4 }}>⚠️</div>
              <h3 style={{ margin: "0 0 8px" }}>Playback failed</h3>
              <p style={{ margin: "0 0 12px", color: "var(--fg-muted)" }}>{diagnostic(error)}</p>
              {candidates.length > 1 && (
                <p style={{ margin: "0 0 12px", fontSize: 12, color: "var(--fg-muted)" }}>
                  Tried {candidates.length} URL variant{candidates.length > 1 ? "s" : ""} — none worked.
                </p>
              )}
              <details style={{ margin: "0 0 12px", fontSize: 12, color: "var(--fg-muted)" }}>
                <summary style={{ cursor: "pointer" }}>Technical details</summary>
                <pre style={{ whiteSpace: "pre-wrap", marginTop: 6 }}>{error}</pre>
                {activeStream?.url && <div style={{ marginTop: 6 }}><strong>URL:</strong> <span style={{ wordBreak: "break-all" }}>{activeStream.url}</span></div>}
              </details>
              <div style={{ display: "flex", gap: 8, justifyContent: "center", flexWrap: "wrap" }}>
                <button onClick={retry}>Retry</button>
                <button onClick={handleOpenInVlc} title={isNativeAndroid() && !nativePlayers.vlc ? "Install VLC for Android" : "Open in VLC"}>
                  ▶ VLC
                </button>
                {(nativePlayers.exoPlayer || isNativeAndroid()) && (
                  <button onClick={handleOpenInExo} title="Open with ExoPlayer-based viewer (MX/Just/Next Player)">▶ ExoPlayer</button>
                )}
                {isNativeAndroid() && (
                  <button onClick={handleOpenExternal} title="System chooser">▶ Other player…</button>
                )}
                <button onClick={openDirect}>Open in tab</button>
                <button onClick={copyUrl}>Copy URL</button>
              </div>
              <p style={{ margin: "12px 0 0", fontSize: 11, color: "var(--fg-muted)" }}>
                Most likely the stream uses a codec (H.265/HEVC, AC3, EAC3) or container (MKV, MOV)
                that no desktop browser decodes natively. VLC handles them all — install it once,
                click "Open in VLC" and it'll launch with the stream.
              </p>
            </div>
          </div>
        )}
      </div>
      <PlayerControls
        videoRef={videoRef}
        engineRef={engineRef}
        containerRef={containerRef}
        castUrl={activeStream?.url ?? null}
        castTitle={tracking?.title}
      />
    </div>
  );
}
