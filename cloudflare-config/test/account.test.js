import { describe, expect, it } from "vitest";
import { xtreamAccount } from "../src/store.js";

const P = { kind: "XTREAM", url: "http://iptv.example.test:8080/", username: "alice", password: "p&ss" };
const fake = (status, body) => async () => new Response(typeof body === "string" ? body : JSON.stringify(body), { status });

describe("xtreamAccount", () => {
  it("abonnement normal : statut, expiration, connexions, essai", async () => {
    const a = await xtreamAccount(P, fake(200, { user_info: { auth: 1, status: "Active", exp_date: "1893456000", created_at: "1700000000", is_trial: "0", active_cons: "1", max_connections: "2" } }));
    expect(a).toEqual({ status: "Active", expiresAt: 1893456000000, createdAt: 1700000000000, trial: false, activeCons: 1, maxCons: 2 });
  });
  it("sans date d'expiration : illimité", async () => {
    const a = await xtreamAccount(P, fake(200, { user_info: { auth: 1, status: "Active", exp_date: null } }));
    expect(a.expiresAt).toBeNull();
  });
  it("identifiants refusés", async () => {
    expect(await xtreamAccount(P, fake(200, { user_info: { auth: 0 } }))).toEqual({ error: "denied" });
    // 403 HTTP : pare-feu (Cloudflare bloqué), pas un refus d'identifiants
    expect(await xtreamAccount(P, fake(403, "no"))).toMatchObject({ error: "unreachable", detail: expect.stringContaining("HTTP 403") });
  });
  it("serveur injoignable ou réponse invalide", async () => {
    expect(await xtreamAccount(P, async () => { throw new Error("net"); })).toEqual({ error: "unreachable", detail: "connexion impossible depuis Cloudflare" });
    expect(await xtreamAccount(P, fake(200, "<html>"))).toMatchObject({ error: "unreachable", detail: expect.stringContaining("pas une API Xtream") });
  });
  it("port non joignable depuis Cloudflare : pas d'appel", async () => {
    let called = false;
    const a = await xtreamAccount({ ...P, url: "http://iptv.example.test:25461" }, async () => { called = true; return new Response("{}"); });
    expect(a).toEqual({ error: "port", port: "25461" });
    expect(called).toBe(false);
  });
  it("M3U : non pris en charge", async () => {
    expect(await xtreamAccount({ kind: "M3U", url: "https://h.example.test/l.m3u" })).toEqual({ error: "unsupported" });
  });
  it("M3U qui est un lien Xtream (get.php) : abonnement lu avec ses identifiants", async () => {
    let url = "";
    const a = await xtreamAccount({ kind: "M3U", url: "http://h.example.test/get.php?username=bob&password=s3&type=m3u_plus" },
      async (u) => { url = u; return new Response(JSON.stringify({ user_info: { auth: 1, status: "Active", exp_date: "1893456000" } })); });
    expect(url).toBe("http://h.example.test/player_api.php?username=bob&password=s3");
    expect(a.expiresAt).toBe(1893456000000);
  });
  it("redirection vers un autre hôte : suivie", async () => {
    let n = 0;
    const a = await xtreamAccount(P, async (u) => (n++ === 0
      ? new Response(null, { status: 302, headers: { location: "http://cdn.example.test/player_api.php?x=1" } })
      : new Response(JSON.stringify({ user_info: { auth: 1, status: "Active" } }))));
    expect(a.status).toBe("Active");
  });
  it("redirection vers un port non joignable depuis Cloudflare : signalée", async () => {
    const a = await xtreamAccount(P, async () => new Response(null, { status: 302, headers: { location: "http://iptv.example.test:25461/player_api.php" } }));
    expect(a).toEqual({ error: "port", port: "25461" });
  });
  it("identifiants encodés dans l'appel, jamais renvoyés", async () => {
    let url = "";
    const a = await xtreamAccount(P, async (u) => { url = u; return new Response(JSON.stringify({ user_info: { auth: 1, status: "Active" } })); });
    expect(url).toBe("http://iptv.example.test:8080/player_api.php?username=alice&password=p%26ss");
    expect(JSON.stringify(a)).not.toContain("p&ss");
  });
});
