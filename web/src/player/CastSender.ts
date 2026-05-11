// Chromecast Web Sender — replacement for the Android Cast SDK integration.
// Wraps the global cast.framework once the Chromecast script tag is loaded.
// The framework expects an Application ID — the default media receiver works for HLS/DASH.

declare global {
  interface Window {
    __onGCastApiAvailable?: (available: boolean) => void;
    cast?: unknown;
    chrome?: { cast?: { media?: unknown; SessionRequest?: unknown } };
  }
}

const FRAMEWORK_SRC = "https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1";
const DEFAULT_RECEIVER = "CC1AD845";

let initPromise: Promise<void> | null = null;

export function initCast(applicationId: string = DEFAULT_RECEIVER): Promise<void> {
  if (initPromise) return initPromise;
  initPromise = new Promise((resolve, reject) => {
    if ((window.chrome as unknown as { cast?: unknown })?.cast) {
      resolve();
      return;
    }
    window.__onGCastApiAvailable = (available) => {
      if (!available) {
        reject(new Error("Cast framework unavailable"));
        return;
      }
      const ctx = (window.cast as unknown as { framework: { CastContext: { getInstance(): unknown } } }).framework.CastContext.getInstance();
      (ctx as { setOptions(opts: { receiverApplicationId: string; autoJoinPolicy: string }): void }).setOptions({
        receiverApplicationId: applicationId,
        autoJoinPolicy: "ORIGIN_SCOPED",
      });
      resolve();
    };
    const tag = document.createElement("script");
    tag.src = FRAMEWORK_SRC;
    tag.onerror = () => reject(new Error("Failed to load cast_sender.js"));
    document.head.appendChild(tag);
  });
  return initPromise;
}

export async function castStream(url: string, title: string, mimeType = "application/x-mpegURL"): Promise<void> {
  await initCast();
  const ctx = (window.cast as unknown as {
    framework: {
      CastContext: { getInstance(): {
        requestSession(): Promise<void>;
        getCurrentSession(): { loadMedia(req: unknown): Promise<void> } | null;
      } };
    };
  }).framework.CastContext.getInstance();
  let session = ctx.getCurrentSession();
  if (!session) {
    await ctx.requestSession();
    session = ctx.getCurrentSession();
  }
  if (!session) throw new Error("Could not obtain Cast session");
  const chromeNs = (window.chrome as unknown as {
    cast: {
      media: {
        MediaInfo: new (url: string, contentType: string) => { metadata: unknown };
        GenericMediaMetadata: new () => { title: string };
        LoadRequest: new (info: unknown) => unknown;
      };
    };
  }).cast;
  const mediaInfo = new chromeNs.media.MediaInfo(url, mimeType);
  const metadata = new chromeNs.media.GenericMediaMetadata();
  metadata.title = title;
  mediaInfo.metadata = metadata;
  const req = new chromeNs.media.LoadRequest(mediaInfo);
  await session.loadMedia(req);
}
