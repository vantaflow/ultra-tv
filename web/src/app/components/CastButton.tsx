// Cast button — surfaces Chromecast and AirPlay targets when available.
//   - Chromecast: lazy-loads cast_sender.js (CastSender), shows a button that opens
//     the native chooser, and casts the proxied stream URL.
//   - AirPlay: Safari/macOS — uses webkitShowPlaybackTargetPicker when supported.
// If neither is available the button hides itself rather than misleading the user.

import { useEffect, useState } from "react";
import { castStream, initCast } from "@player/CastSender";

interface Props {
  videoRef: React.MutableRefObject<HTMLVideoElement | null>;
  title: string;
  url: string | null;
}

export function CastButton({ videoRef, title, url }: Props) {
  const [castReady, setCastReady] = useState(false);
  const [airplayReady, setAirplayReady] = useState(false);

  useEffect(() => {
    void initCast().then(() => setCastReady(true)).catch(() => setCastReady(false));
    const v = videoRef.current as HTMLVideoElement & {
      webkitShowPlaybackTargetPicker?: () => void;
      webkitCurrentPlaybackTargetIsWireless?: boolean;
    } | null;
    if (v?.webkitShowPlaybackTargetPicker) setAirplayReady(true);
    // Allow AirPlay routing on the video element
    if (v) v.setAttribute("x-webkit-airplay", "allow");
  }, [videoRef]);

  const onCast = async () => {
    if (!url) return;
    try { await castStream(url, title); } catch (e) { console.error("Cast failed:", e); }
  };

  const onAirplay = () => {
    const v = videoRef.current as (HTMLVideoElement & { webkitShowPlaybackTargetPicker?: () => void }) | null;
    v?.webkitShowPlaybackTargetPicker?.();
  };

  if (!castReady && !airplayReady) return null;

  return (
    <>
      {castReady && <button onClick={onCast} title="Cast to Chromecast">📡 Cast</button>}
      {airplayReady && <button onClick={onAirplay} title="AirPlay">🎬 AirPlay</button>}
    </>
  );
}
