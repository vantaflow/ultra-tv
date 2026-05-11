// Web equivalent of  + Media3PlayerEngine.
// Chooses an adapter based on stream type:
//   HLS  -> hls.js (or native Safari)
//   DASH -> shaka-player
//   MP4/MKV/PROGRESSIVE -> native <video>
// All upstream URLs are routed through the configured proxy (CORS, UA, cleartext).

import type { StreamInfo } from "@domain/model";
import { streamTypeFromUrl } from "@domain/model";
import { proxify } from "@data/net/proxy";

export type PlayerState = "idle" | "loading" | "playing" | "paused" | "ended" | "error";

export interface PlayerEvents {
  onState?(state: PlayerState): void;
  onError?(error: Error): void;
  onTimeUpdate?(positionMs: number, durationMs: number): void;
}

export interface PlayerTrack {
  id: string;
  label: string;
  language: string;
}

export interface PlayerEngine {
  attach(video: HTMLVideoElement): void;
  load(stream: StreamInfo): Promise<void>;
  play(): Promise<void>;
  pause(): void;
  seek(positionMs: number): void;
  release(): void;
  setPlaybackRate(rate: number): void;
  togglePictureInPicture(): Promise<void>;
  toggleFullscreen(target?: HTMLElement | null): Promise<void>;
  getAudioTracks(): PlayerTrack[];
  setAudioTrack(id: string): void;
  getTextTracks(): PlayerTrack[];
  setTextTrack(id: string | null): void;
  addExternalSubtitle(url: string, label: string, lang: string): void;
}

type HlsCtor = typeof import("hls.js").default;

// mpegts.js — for raw MPEG-TS streams (Xtream live without HLS wrapper).
// Loaded lazily from a CDN so the bundle stays slim and npm install issues don't block us.
let mpegtsLoader: Promise<MpegtsModule> | null = null;
interface MpegtsModule {
  isSupported(): boolean;
  createPlayer(config: { type: string; url: string; isLive?: boolean }): MpegtsPlayer;
}
interface MpegtsPlayer {
  attachMediaElement(el: HTMLMediaElement): void;
  load(): void;
  play(): Promise<void> | void;
  unload(): void;
  detachMediaElement(): void;
  destroy(): void;
  on(event: string, cb: (...args: unknown[]) => void): void;
}
async function loadMpegts(): Promise<MpegtsModule> {
  if (mpegtsLoader) return mpegtsLoader;
  mpegtsLoader = (async () => {
    // ESM build hosted on jsDelivr — small (~120KB gzipped)
    const url = "https://cdn.jsdelivr.net/npm/mpegts.js@1.7.3/dist/mpegts.js";
    await new Promise<void>((resolve, reject) => {
      if ((window as unknown as { mpegts?: MpegtsModule }).mpegts) return resolve();
      const s = document.createElement("script");
      s.src = url;
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error("Failed to load mpegts.js from CDN"));
      document.head.appendChild(s);
    });
    const mod = (window as unknown as { mpegts?: MpegtsModule }).mpegts;
    if (!mod) throw new Error("mpegts.js loaded but global missing");
    return mod;
  })();
  return mpegtsLoader;
}

interface ShakaPlayerInstance {
  attach(video: HTMLVideoElement): Promise<void>;
  configure(config: unknown): void;
  load(url: string): Promise<void>;
  destroy(): Promise<void>;
  addEventListener(type: string, listener: (e: unknown) => void): void;
  getNetworkingEngine(): { registerRequestFilter(filter: (type: number, request: { uris: string[] }) => void): void };
}

export class WebPlayerEngine implements PlayerEngine {
  private video: HTMLVideoElement | null = null;
  private hls: InstanceType<HlsCtor> | null = null;
  private shaka: ShakaPlayerInstance | null = null;
  private mpegts: MpegtsPlayer | null = null;
  private rafId: number | null = null;

  constructor(private events: PlayerEvents = {}) {}

  attach(video: HTMLVideoElement) {
    this.video = video;
    video.addEventListener("play", () => this.events.onState?.("playing"));
    video.addEventListener("pause", () => this.events.onState?.("paused"));
    video.addEventListener("ended", () => this.events.onState?.("ended"));
    video.addEventListener("waiting", () => this.events.onState?.("loading"));
    video.addEventListener("error", () => {
      const err = video.error;
      this.events.onError?.(new Error(`MediaError code=${err?.code} message=${err?.message ?? ""}`));
      this.events.onState?.("error");
    });
    // 4 Hz is plenty for the progress UI and history throttling; rAF was burning
    // CPU running this 60×/sec on every active player.
    let lastTick = 0;
    const tick = (ts: number) => {
      if (!this.video) return;
      if (ts - lastTick > 250) {
        lastTick = ts;
        this.events.onTimeUpdate?.(this.video.currentTime * 1000, this.video.duration * 1000);
      }
      this.rafId = requestAnimationFrame(tick);
    };
    this.rafId = requestAnimationFrame(tick);
  }

  async load(stream: StreamInfo): Promise<void> {
    if (!this.video) throw new Error("PlayerEngine: video not attached");
    this.releaseAdapters();
    this.events.onState?.("loading");

    const type = stream.streamType === "UNKNOWN" ? streamTypeFromUrl(stream.url) : stream.streamType;

    if (type === "RTSP") {
      throw new Error("RTSP streams require a transmuxing proxy and are not playable directly in browsers.");
    }

    const proxiedUrl = proxify(stream.url);

    if (type === "DASH") {
      await this.loadShaka(stream, proxiedUrl);
      return;
    }

    if (type === "HLS") {
      await this.loadHls(stream, proxiedUrl);
      return;
    }

    if (type === "MPEG_TS") {
      const ok = await this.tryMpegts(proxiedUrl);
      if (ok) return;
      // fall through to native — most browsers will fail but at least we tried
    }

    this.video.src = proxiedUrl;
    this.video.load();
  }

  private async tryMpegts(sourceUrl: string): Promise<boolean> {
    const v = this.video;
    if (!v) return false;
    try {
      const mpegts = await loadMpegts();
      if (!mpegts.isSupported()) return false;
      const player = mpegts.createPlayer({ type: "mpegts", url: sourceUrl, isLive: true });
      player.on("error", (...args) => {
        this.events.onError?.(new Error(`mpegts: ${JSON.stringify(args)}`));
        this.events.onState?.("error");
      });
      player.attachMediaElement(v);
      player.load();
      void player.play();
      this.mpegts = player;
      return true;
    } catch (e) {
      console.warn("mpegts.js fallback failed:", e);
      return false;
    }
  }

  private async loadHls(stream: StreamInfo, sourceUrl: string) {
    const v = this.video!;
    if (v.canPlayType("application/vnd.apple.mpegurl")) {
      v.src = sourceUrl;
      v.load();
      return;
    }
    const Hls = (await import("hls.js")).default;
    if (!Hls.isSupported()) {
      v.src = sourceUrl;
      v.load();
      return;
    }

    // Custom loader: route every URL (manifest + segments + keys) through the proxy.
    const BaseLoader = Hls.DefaultConfig.loader;
    class ProxyLoader extends BaseLoader {
      override load(context: { url: string }, config: object, callbacks: object) {
        context.url = proxify(context.url);
        super.load(context as never, config as never, callbacks as never);
      }
    }

    const customHeaders = stream.headers;
    const hls = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
      backBufferLength: 90,
      manifestLoadingTimeOut: 30000,
      manifestLoadingMaxRetry: 3,
      levelLoadingTimeOut: 30000,
      fragLoadingTimeOut: 60000,
      loader: ProxyLoader as never,
      xhrSetup: (xhr) => {
        if (stream.userAgent) {
          try { xhr.setRequestHeader("X-SV-UA", stream.userAgent); } catch { /* forbidden */ }
        }
        for (const [k, v] of Object.entries(customHeaders)) {
          try { xhr.setRequestHeader(k, v); } catch { /* forbidden */ }
        }
      },
    });
    hls.on(Hls.Events.ERROR, (_evt, data) => {
      if (data.fatal) {
        this.events.onError?.(new Error(`HLS fatal: ${data.type} ${data.details}`));
        this.events.onState?.("error");
      }
    });
    hls.attachMedia(v);
    hls.loadSource(sourceUrl);
    this.hls = hls;
  }

  private async loadShaka(stream: StreamInfo, sourceUrl: string) {
    const shakaNs = (await import("shaka-player")) as unknown as {
      polyfill: { installAll(): void };
      Player: new () => ShakaPlayerInstance;
    };
    shakaNs.polyfill.installAll();
    const player = new shakaNs.Player();
    await player.attach(this.video!);

    // Route every shaka request through the proxy.
    player.getNetworkingEngine().registerRequestFilter((_type, request) => {
      request.uris = request.uris.map((u) => proxify(u));
    });

    if (stream.drmInfo) {
      const servers: Record<string, string> = {};
      if (stream.drmInfo.scheme === "WIDEVINE") servers["com.widevine.alpha"] = proxify(stream.drmInfo.licenseUrl);
      if (stream.drmInfo.scheme === "PLAYREADY") servers["com.microsoft.playready"] = proxify(stream.drmInfo.licenseUrl);
      player.configure({ drm: { servers } });
    }
    player.addEventListener("error", (e: unknown) => {
      this.events.onError?.(new Error(`Shaka error: ${JSON.stringify(e)}`));
      this.events.onState?.("error");
    });
    await player.load(sourceUrl);
    this.shaka = player;
  }

  async play() {
    await this.video?.play();
  }

  pause() {
    this.video?.pause();
  }

  seek(positionMs: number) {
    if (this.video) this.video.currentTime = positionMs / 1000;
  }

  release() {
    this.releaseAdapters();
    if (this.rafId != null) cancelAnimationFrame(this.rafId);
    this.video = null;
  }

  private releaseAdapters() {
    if (this.hls) {
      this.hls.destroy();
      this.hls = null;
    }
    if (this.shaka) {
      void this.shaka.destroy();
      this.shaka = null;
    }
    if (this.mpegts) {
      try { this.mpegts.unload(); this.mpegts.detachMediaElement(); this.mpegts.destroy(); } catch { /* noop */ }
      this.mpegts = null;
    }
    if (this.video) {
      this.video.removeAttribute("src");
      this.video.load();
    }
  }

  setPlaybackRate(rate: number) {
    if (this.video) this.video.playbackRate = rate;
  }

  async togglePictureInPicture() {
    if (!this.video) return;
    const doc = document as Document & { pictureInPictureElement?: Element | null; exitPictureInPicture?: () => Promise<void> };
    const v = this.video as HTMLVideoElement & { requestPictureInPicture?: () => Promise<PictureInPictureWindow>; disablePictureInPicture?: boolean };
    if (!v.requestPictureInPicture) throw new Error("Picture-in-Picture not supported in this browser");
    if (doc.pictureInPictureElement === v) {
      await doc.exitPictureInPicture?.();
    } else {
      await v.requestPictureInPicture();
    }
  }

  async toggleFullscreen(target: HTMLElement | null = null) {
    const el = (target ?? this.video?.parentElement ?? this.video) as
      | (HTMLElement & { webkitRequestFullscreen?: () => Promise<void> })
      | null;
    if (!el) return;
    const doc = document as Document & {
      webkitFullscreenElement?: Element | null;
      webkitExitFullscreen?: () => Promise<void>;
    };
    const fsEl = document.fullscreenElement ?? doc.webkitFullscreenElement;
    if (fsEl) {
      await (document.exitFullscreen?.() ?? doc.webkitExitFullscreen?.());
    } else {
      await (el.requestFullscreen?.() ?? el.webkitRequestFullscreen?.());
    }
  }

  getAudioTracks(): PlayerTrack[] {
    if (this.hls) {
      return this.hls.audioTracks.map((t, idx) => ({
        id: String(idx),
        label: t.name || t.lang || `Track ${idx + 1}`,
        language: t.lang || "",
      }));
    }
    if (this.shaka) {
      // Shaka exposes audio variants via getVariantTracks; simplified here.
      const tracks = (this.shaka as unknown as { getVariantTracks(): Array<{ id: number; language: string; label?: string }> }).getVariantTracks();
      return tracks.map((t) => ({ id: String(t.id), label: t.label || t.language || `Variant ${t.id}`, language: t.language }));
    }
    return [];
  }

  setAudioTrack(id: string) {
    if (this.hls) {
      this.hls.audioTrack = Number.parseInt(id, 10);
    }
  }

  getTextTracks(): PlayerTrack[] {
    if (!this.video) return [];
    const native = Array.from(this.video.textTracks);
    return native.map((t, idx) => ({
      id: String(idx),
      label: t.label || t.language || `Subtitle ${idx + 1}`,
      language: t.language,
    }));
  }

  setTextTrack(id: string | null) {
    if (!this.video) return;
    Array.from(this.video.textTracks).forEach((t, idx) => {
      t.mode = String(idx) === id ? "showing" : "disabled";
    });
  }

  addExternalSubtitle(url: string, label: string, lang: string) {
    if (!this.video) return;
    const track = document.createElement("track");
    track.kind = "subtitles";
    track.label = label;
    track.srclang = lang;
    track.src = url;
    track.default = false;
    this.video.appendChild(track);
  }
}
