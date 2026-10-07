import { describe, expect, it, vi } from "vitest";
import { osLogin, subtitlesDownload } from "../src/subtitles.js";
import { call, newAccount } from "./helpers.js";

const ENV = { OPENSUBTITLES_API_KEY: "OSKEY" };
const sp = (s) => new URL("https://x.test/?" + s).searchParams;
const res = (status, body) => new Response(typeof body === "string" ? body : JSON.stringify(body), { status });

describe("osLogin", () => {
  it("connexion réussie : jeton, hôte VIP en liste blanche, quota", async () => {
    const fetchFn = vi.fn(async () => res(200, { token: "T", base_url: "vip-api.opensubtitles.com", user: { level: "VIP Member", allowed_downloads: 1000, vip: true } }));
    expect(await osLogin(ENV, "bob", "pw", fetchFn)).toEqual({ token: "T", base: "vip-api.opensubtitles.com", level: "VIP Member", allowed: 1000, vip: true });
    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe("https://api.opensubtitles.com/api/v1/login");
    expect(init.headers["api-key"]).toBe("OSKEY");
    expect(JSON.parse(init.body)).toEqual({ username: "bob", password: "pw" });
  });
  it("hôte d'API inattendu : remplacé par l'hôte officiel", async () => {
    const r = await osLogin(ENV, "bob", "pw", async () => res(200, { token: "T", base_url: "evil.example.test", user: {} }));
    expect(r.base).toBe("api.opensubtitles.com");
  });
  it("identifiants refusés, saturation, panne, clé absente", async () => {
    expect(await osLogin(ENV, "b", "p", async () => res(401, "{}"))).toEqual({ error: "denied" });
    expect(await osLogin(ENV, "b", "p", async () => res(429, "{}"))).toEqual({ error: "busy" });
    expect(await osLogin(ENV, "b", "p", async () => { throw new Error("x"); })).toEqual({ error: "upstream" });
    expect(await osLogin({}, "b", "p", vi.fn())).toEqual({ error: "not_configured" });
  });
});

describe("subtitlesDownload avec le compte du client", () => {
  const srt = "1\n00:00:01,000 --> 00:00:02,000\nBonjour\n";
  const flow = (downloadStatuses) => {
    const calls = [];
    const fetchFn = async (url, init) => {
      calls.push({ url, init });
      if (String(url).endsWith("/download")) { const st = downloadStatuses.shift(); return st === 200 ? res(200, { link: "https://dl.opensubtitles.com/f/1.srt" }) : res(st, "{}"); }
      return new Response(srt);
    };
    return { calls, fetchFn };
  };

  it("jeton du client envoyé, sur son hôte d'API", async () => {
    const { calls, fetchFn } = flow([200]);
    const session = { get: vi.fn(async () => ({ token: "UT", base: "vip-api.opensubtitles.com" })) };
    const r = await subtitlesDownload(sp("id=42"), ENV, { fetchFn, cache: null, session });
    expect(await r.text()).toBe(srt);
    expect(calls[0].url).toBe("https://vip-api.opensubtitles.com/api/v1/download");
    expect(calls[0].init.headers.authorization).toBe("Bearer UT");
    expect(calls[0].init.headers["api-key"]).toBe("OSKEY");
  });
  it("jeton expiré : une reconnexion puis nouvel essai", async () => {
    const { calls, fetchFn } = flow([401, 200]);
    const session = { get: vi.fn(async (force) => ({ token: force ? "NEW" : "OLD", base: "api.opensubtitles.com" })) };
    expect((await subtitlesDownload(sp("id=42"), ENV, { fetchFn, cache: null, session })).status).toBe(200);
    expect(session.get).toHaveBeenLastCalledWith(true);
    expect(calls[1].init.headers.authorization).toBe("Bearer NEW");
  });
  it("compte injoignable : repli sur la clé seule", async () => {
    const { calls, fetchFn } = flow([200]);
    const session = { get: async () => { throw new Error("kv"); } };
    expect((await subtitlesDownload(sp("id=42"), ENV, { fetchFn, cache: null, session })).status).toBe(200);
    expect(calls[0].init.headers.authorization).toBeUndefined();
  });
  it("quota du client épuisé : 429", async () => {
    const { fetchFn } = flow([406]);
    const r = await subtitlesDownload(sp("id=42"), ENV, { fetchFn, cache: null, session: { get: async () => ({ token: "UT", base: "api.opensubtitles.com" }) } });
    expect(r.status).toBe(429);
  });
});

describe("espace client : compte OpenSubtitles", () => {
  it("panneau affiché, liaison et déliaison joignables (sans clé configurée : pas d'appel externe)", async () => {
    const acct = await newAccount();
    const page = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(page).toContain('action="/subtitles/link"');
    const link = await call("/subtitles/link", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, os_user: "bob", os_pass: "pw" } });
    expect(link.status).toBe(302);
    expect(link.headers.get("location")).toBe("/?e=os_upstream#compte");
    const empty = await call("/subtitles/link", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(empty.headers.get("location")).toBe("/?e=os_denied#compte");
    const unlink = await call("/subtitles/unlink", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(unlink.headers.get("location")).toBe("/?m=os_off#compte");
  });
  it("sans jeton CSRF : refusé", async () => {
    const acct = await newAccount();
    expect((await call("/subtitles/link", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { os_user: "a", os_pass: "b" } })).status).toBe(403);
  });
});
