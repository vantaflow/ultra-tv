import { describe, expect, it } from "vitest";
import { allowedTarget, handle } from "../relay/lib.js";
import { xtreamAccount } from "../src/store.js";

const KEY = "k".repeat(40);
const req = (body, key = KEY, method = "POST") =>
  new Request("https://relay.example.test/api/xtream", { method, headers: { "x-relay-key": key, "content-type": "application/json" }, body: method === "POST" ? JSON.stringify(body) : undefined });
const upstream = (status, text, headers = {}) => async () => new Response(text, { status, headers });
const API = "http://iptv.example.test/player_api.php?username=a&password=b";

describe("relais d'abonnement (Vercel)", () => {
  it("lit player_api.php et renvoie statut + corps, sans suivre de redirection", async () => {
    let seen;
    const r = await handle(req({ url: API, ua: "IPTVSmartersPro" }), KEY, async (u, init) => { seen = { u, init }; return new Response('{"user_info":{}}'); });
    expect(await r.json()).toEqual({ status: 200, location: null, body: '{"user_info":{}}' });
    expect(seen.u).toBe(API);
    expect(seen.init.redirect).toBe("manual");
    expect(seen.init.headers["user-agent"]).toBe("IPTVSmartersPro");
    const r2 = await handle(req({ url: API }), KEY, upstream(302, "", { location: "http://h2.example.test:25461/player_api.php" }));
    expect((await r2.json()).location).toBe("http://h2.example.test:25461/player_api.php");
  });
  it("clé absente, courte ou fausse : refusé", async () => {
    expect((await handle(req({ url: API }, "x".repeat(40)), KEY, upstream(200, "{}"))).status).toBe(403);
    expect((await handle(req({ url: API }), undefined, upstream(200, "{}"))).status).toBe(503);
    expect((await handle(req({ url: API }), "court", upstream(200, "{}"))).status).toBe(503);
  });
  it("pas un proxy ouvert : autre chemin, hôte local ou privé, autre protocole", async () => {
    for (const url of ["http://h.example.test/get.php", "http://127.0.0.1/player_api.php", "http://169.254.169.254/player_api.php",
      "http://10.0.0.1/player_api.php", "http://192.168.1.1/player_api.php", "http://localhost/player_api.php", "http://[::1]/player_api.php",
      "file:///etc/player_api.php", "http://u:p@h.example.test/player_api.php", 42]) {
      expect((await handle(req({ url }), KEY, upstream(200, "{}"))).status, String(url)).toBe(400);
    }
    expect((await handle(req(null, KEY, "GET"), KEY, upstream(200, "{}"))).status).toBe(404);
  });
  it("erreur réseau ou délai : signalés sans détail", async () => {
    expect(await (await handle(req({ url: API }), KEY, async () => { throw new TypeError("x"); })).json()).toEqual({ error: "network" });
    expect(await (await handle(req({ url: API }), KEY, async () => { throw Object.assign(new Error("t"), { name: "TimeoutError" }); })).json()).toEqual({ error: "timeout" });
  });
  it("adresse publique avec port : acceptée", () => {
    expect(allowedTarget("http://h.example.test:8080/x/player_api.php?username=a")?.host).toBe("h.example.test:8080");
  });
});

describe("xtreamAccount + relais", () => {
  const P = { kind: "M3U", url: "http://iptv.example.test/get.php?username=a&password=b&type=m3u_plus" };
  const RELAY = { url: "https://relay.example.test/api/xtream", key: KEY };
  const OK = { user_info: { auth: 1, status: "Active", exp_date: "1893456000" } };
  // Le Worker est bloqué (403) ; le relais, lui, passe et répond via handle().
  const net = (direct, viaRelay) => async (u, init) => {
    if (u === RELAY.url) return handle(new Request(u, init), KEY, viaRelay);
    return direct(u, init);
  };

  it("bloqué depuis Cloudflare : abonnement lu par le relais", async () => {
    const a = await xtreamAccount(P, net(upstream(403, "no"), upstream(200, JSON.stringify(OK))), 8000, RELAY);
    expect(a.expiresAt).toBe(1893456000000);
  });
  it("sans relais configuré : message de blocage inchangé", async () => {
    const a = await xtreamAccount(P, net(upstream(403, "no"), upstream(200, JSON.stringify(OK))), 8000, null);
    expect(a).toEqual({ error: "unreachable", detail: expect.stringContaining("Cloudflare") });
  });
  it("port fermé à Cloudflare : le relais l'atteint", async () => {
    const a = await xtreamAccount({ kind: "XTREAM", url: "http://iptv.example.test:25461", username: "a", password: "b" },
      net(upstream(500, ""), upstream(200, JSON.stringify(OK))), 8000, RELAY);
    expect(a.expiresAt).toBe(1893456000000);
  });
  it("refusé aussi par le relais : cause dite, sans accuser Cloudflare", async () => {
    const a = await xtreamAccount(P, net(upstream(403, "no"), upstream(403, "no")), 8000, RELAY);
    expect(a).toEqual({ error: "unreachable", detail: "accès refusé par le serveur (HTTP 403), même hors Cloudflare" });
  });
  it("relais en panne : signalé comme tel", async () => {
    const a = await xtreamAccount(P, async (u) => (u === RELAY.url ? new Response("boom", { status: 500 }) : new Response("no", { status: 403 })), 8000, RELAY);
    expect(a).toEqual({ error: "unreachable", detail: "relais d'abonnement indisponible" });
  });
  it("fournisseur qui répond directement : relais jamais appelé", async () => {
    let relayed = false;
    const a = await xtreamAccount(P, async (u) => { if (u === RELAY.url) relayed = true; return new Response(JSON.stringify(OK)); }, 8000, RELAY);
    expect(a.expiresAt).toBe(1893456000000);
    expect(relayed).toBe(false);
  });
});
