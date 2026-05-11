// Validates provider credentials before saving. Returns a friendly diagnostic.
// For Xtream: runs the player_api handshake and reports user status / expiration.
// For M3U / EPG: HEAD probe, falls back to GET if HEAD is unsupported.

import { xtream, type XtreamCreds } from "@data/providers/xtream";
import { proxiedFetch } from "@data/net/proxy";

export interface ProviderTestResult {
  ok: boolean;
  message: string;
  details?: Record<string, string | number>;
}

interface TestInput {
  type: "XTREAM_CODES" | "M3U" | "STALKER_PORTAL";
  serverUrl: string;
  username?: string;
  password?: string;
  m3uUrl?: string;
  userAgent?: string;
  httpReferer?: string;
}

export async function testProvider(input: TestInput): Promise<ProviderTestResult> {
  if (input.type === "XTREAM_CODES") {
    if (!input.serverUrl || !input.username || !input.password) {
      return { ok: false, message: "Server URL, username and password are required" };
    }
    try {
      const creds: XtreamCreds = {
        serverUrl: input.serverUrl,
        username: input.username,
        password: input.password,
        userAgent: input.userAgent || null,
        referer: input.httpReferer || null,
      };
      const hs = await xtream.handshake(creds);
      const user = hs.user_info;
      const status = (user.status || "").toLowerCase();
      if (user.auth === 0 || status === "banned" || status === "disabled") {
        return { ok: false, message: `Authentication failed (status: ${user.status || "unknown"})` };
      }
      const expISO = user.exp_date ? new Date(Number.parseInt(user.exp_date, 10) * 1000).toISOString().slice(0, 10) : "never";
      return {
        ok: true,
        message: `Connected — ${user.status || "Active"}`,
        details: {
          status: user.status || "Unknown",
          expires: expISO,
          maxConnections: user.max_connections || "?",
          activeConnections: user.active_cons || "?",
          allowedFormats: (user.allowed_output_formats || []).join(", ") || "—",
          server: hs.server_info?.url || input.serverUrl,
        },
      };
    } catch (e) {
      return { ok: false, message: `Handshake failed: ${e instanceof Error ? e.message : String(e)}` };
    }
  }

  if (input.type === "M3U") {
    if (!input.m3uUrl) return { ok: false, message: "M3U URL is required" };
    try {
      const res = await proxiedFetch(input.m3uUrl, {
        method: "GET",
        userAgent: input.userAgent || null,
        referer: input.httpReferer || null,
        timeoutMs: 30_000,
      });
      if (!res.ok) return { ok: false, message: `HTTP ${res.status} ${res.statusText}` };
      // Read a small chunk to confirm it looks like a playlist
      const head = (await res.text()).slice(0, 256);
      const looksValid = /^#EXTM3U/.test(head) || head.includes("#EXTINF");
      if (!looksValid) return { ok: false, message: "URL responded but content doesn't look like an M3U playlist" };
      return { ok: true, message: "M3U playlist reachable and looks valid" };
    } catch (e) {
      return { ok: false, message: `Fetch failed: ${e instanceof Error ? e.message : String(e)}` };
    }
  }

  if (input.type === "STALKER_PORTAL") {
    return { ok: false, message: "Stalker test not implemented yet — connection will be attempted at sync." };
  }

  return { ok: false, message: "Unknown provider type" };
}
