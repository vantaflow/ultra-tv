// Stalker Portal (Ministra) client.
// Mirrors the handshake + itv/vod/series endpoints used by data/sync/SyncManager*.kt for
// stalker providers. The token returned by handshake is cached on the client.

export interface StalkerCreds {
  portalUrl: string;
  macAddress: string;
  deviceProfile?: string;
  timezone?: string;
  locale?: string;
}

interface StalkerEnvelope<T> {
  js: T;
}

export class StalkerClient {
  private token: string | null = null;

  constructor(private creds: StalkerCreds) {}

  private endpoint() {
    const base = this.creds.portalUrl.endsWith("/") ? this.creds.portalUrl : this.creds.portalUrl + "/";
    return new URL("server/load.php", base);
  }

  private headers(): HeadersInit {
    const h: Record<string, string> = {
      Cookie: `mac=${encodeURIComponent(this.creds.macAddress)}; stb_lang=en; timezone=${encodeURIComponent(this.creds.timezone ?? "Europe/Paris")}`,
      "User-Agent": "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 4 rev: 855 Safari/533.3",
      "X-User-Agent": "Model: MAG250; Link: WiFi",
    };
    if (this.token) h["Authorization"] = `Bearer ${this.token}`;
    return h;
  }

  private async call<T>(params: Record<string, string | number>, signal?: AbortSignal): Promise<T> {
    const url = this.endpoint();
    for (const [k, v] of Object.entries(params)) url.searchParams.set(k, String(v));
    const res = await fetch(url.toString(), { headers: this.headers(), signal, credentials: "omit" });
    if (!res.ok) throw new Error(`Stalker HTTP ${res.status}`);
    const json = (await res.json()) as StalkerEnvelope<T>;
    return json.js;
  }

  async handshake(signal?: AbortSignal): Promise<string> {
    const js = await this.call<{ token: string }>({ type: "stb", action: "handshake", JsHttpRequest: "1-xml" }, signal);
    this.token = js.token;
    return js.token;
  }

  async getProfile(signal?: AbortSignal) {
    return this.call<unknown>({ type: "stb", action: "get_profile", JsHttpRequest: "1-xml" }, signal);
  }

  async itvCategories(signal?: AbortSignal) {
    return this.call<{ id: string; title: string }[]>(
      { type: "itv", action: "get_genres", JsHttpRequest: "1-xml" },
      signal,
    );
  }

  async itvChannels(signal?: AbortSignal) {
    return this.call<{ data: Array<{ id: string; name: string; logo: string; cmd: string; tv_genre_id: string; xmltv_id: string }> }>(
      { type: "itv", action: "get_all_channels", JsHttpRequest: "1-xml" },
      signal,
    );
  }

  async createLink(cmd: string, signal?: AbortSignal): Promise<string> {
    const out = await this.call<{ cmd: string }>(
      { type: "itv", action: "create_link", cmd, JsHttpRequest: "1-xml" },
      signal,
    );
    // server returns "ffmpeg http://..." → strip prefix
    return out.cmd.replace(/^ffmpeg\s+/, "");
  }
}
