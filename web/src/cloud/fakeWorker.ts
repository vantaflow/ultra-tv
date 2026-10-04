// Faux Worker de configuration (tests) : même contrat que cloudflare-config/.
import http from "node:http";
import type { AddressInfo } from "node:net";
import type { CloudProvider } from "./client";

export interface FakeWorker {
  base: string;
  close: () => void;
  providers: CloudProvider[];
  version: number;
  token: string;
  /** Appairage : marque le code courant comme confirmé sur le tableau de bord. */
  confirm: () => void;
  state: {
    pollCount: number;
    rejectToken: boolean;
    hasPushEndpoint: boolean;
    rateLimitNextPoll: boolean;
    shareWithSupported: boolean;
    lastPut?: unknown;
    devices: { id: string; name: string; model?: string; lastSeen?: number; isCurrent?: boolean }[];
    lastRename?: string;
    hasRename: boolean;
  };
}

export async function startFakeWorker(): Promise<FakeWorker> {
  const w: FakeWorker = {
    base: "", providers: [], version: 1, token: "tok-1", close: () => undefined, confirm: () => undefined,
    state: { pollCount: 0, rejectToken: false, hasPushEndpoint: true, rateLimitNextPoll: false, shareWithSupported: true, hasRename: true, devices: [{ id: "dev-1", name: "Ce Mac", model: "MacBook", lastSeen: 1760000000000, isCurrent: true }, { id: "d2", name: "Box salon", model: "Android TV", lastSeen: 1759990000000 }] },
  };
  let confirmed = false;
  w.confirm = () => { confirmed = true; };
  const server = http.createServer((req, res) => {
    const send = (code: number, body?: unknown, headers: Record<string, string> = {}) => {
      res.writeHead(code, { "content-type": "application/json", ...headers });
      res.end(body === undefined ? "" : JSON.stringify(body));
    };
    let raw = "";
    req.on("data", (c) => (raw += c));
    req.on("end", () => {
      const url = new URL(req.url!, "http://x");
      const body = raw ? (() => { try { return JSON.parse(raw); } catch { return {}; } })() : {};
      const bearer = req.headers.authorization === `Bearer ${w.token}` && !w.state.rejectToken;
      if (url.pathname === "/api/pair/start" && req.method === "POST") return send(200, { code: "K7Q2M9XF", pollSecret: "sec", expiresIn: 600, interval: 1 });
      if (url.pathname === "/api/pair/poll" && req.method === "POST") {
        w.state.pollCount++;
        if (body.pollSecret !== "sec") return send(404);
        if (w.state.rateLimitNextPoll) { w.state.rateLimitNextPoll = false; return send(429, { error: "rate" }, { "retry-after": "2" }); }
        return confirmed ? send(200, { token: w.token, deviceId: "dev-1" }) : send(202);
      }
      if (url.pathname === "/api/config" && req.method === "GET") {
        if (!bearer) return send(401);
        const etag = `"v${w.version}"`;
        if (req.headers["if-none-match"] === etag) return send(304, undefined, { etag });
        return send(200, { version: w.version, self: "dev-1", devices: w.state.devices, providers: w.providers }, { etag });
      }
      if (url.pathname === "/api/device" && req.method === "PATCH") {
        if (!bearer) return send(401);
        if (!w.state.hasRename) return send(404);
        if (typeof body.name !== "string" || !body.name.trim()) return send(400, { error: "invalid", field: "name" });
        w.state.lastRename = body.name;
        const me = w.state.devices.find((d) => d.isCurrent);
        if (me) me.name = body.name;
        w.version++;
        return send(200, { name: body.name });
      }
      if (url.pathname === "/api/device/rotate" && req.method === "POST") return bearer ? send(200, { token: "tok-2", deviceId: "dev-1" }) : send(401);
      if (url.pathname === "/api/device/providers" && req.method === "POST") {
        if (!bearer) return send(401);
        if (!w.state.hasPushEndpoint) return send(404);
        w.state.lastPut = body;
        if (body.shareWith !== undefined && !w.state.shareWithSupported) return send(400, { error: "invalid", field: "shareWith" });
        if (typeof body.kind !== "string") return send(400, { error: "invalid", field: "kind" });
        const id = body.id ?? `a${String(w.providers.length + 1).padStart(7, "0")}`;
        const p: CloudProvider = { id, kind: body.kind, name: body.name, url: body.url, username: body.username ?? "", password: body.password ?? "", sharedWith: body.shareWith };
        const i = w.providers.findIndex((x) => x.id === id);
        if (i >= 0) w.providers[i] = p; else w.providers.push(p);
        w.version++;
        return send(i >= 0 ? 200 : 201, { version: w.version, provider: p });
      }
      const del = url.pathname.match(/^\/api\/device\/providers\/([0-9a-f]{8})$/);
      if (del && req.method === "DELETE") {
        if (!bearer) return send(401);
        const n = w.providers.length;
        w.providers = w.providers.filter((p) => p.id !== del[1]);
        if (w.providers.length === n) return send(404);
        w.version++;
        return send(200, { version: w.version });
      }
      send(404);
    });
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  w.base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  w.close = () => server.close();
  return w;
}
