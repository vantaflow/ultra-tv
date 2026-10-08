import { describe, expect, it, vi } from "vitest";
import { pkcePair, authorizeUrl, exchangeCode, refreshTokens, parseScrobble, searchTitle, resolveTmdb, traktScrobbleBody, sendScrobble } from "../src/trakt.js";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";

const res = (status, body) => new Response(typeof body === "string" ? body : JSON.stringify(body), { status });

describe("trakt : OAuth PKCE", () => {
  it("défi S256 du vérificateur, URL d'autorisation complète", async () => {
    const { verifier, challenge } = await pkcePair();
    expect(verifier.length).toBeGreaterThanOrEqual(43);
    const expected = btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier))))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    expect(challenge).toBe(expected);
    const u = new URL(authorizeUrl("CID", "https://x.test/trakt/callback", "STATE", challenge));
    expect(u.origin + u.pathname).toBe("https://trakt.tv/oauth/authorize");
    expect(Object.fromEntries(u.searchParams)).toEqual({ response_type: "code", client_id: "CID", redirect_uri: "https://x.test/trakt/callback", state: "STATE", code_challenge: challenge, code_challenge_method: "S256" });
  });
  it("échange du code : vérificateur envoyé, pas de secret client", async () => {
    const fetchFn = vi.fn(async () => res(200, { access_token: "A", refresh_token: "R", expires_in: 3600 }));
    expect(await exchangeCode("CID", "https://x.test/cb", "CODE", "VER", fetchFn, 1000)).toEqual({ access: "A", refresh: "R", expiresAt: 1000 + 3600_000 });
    const body = JSON.parse(fetchFn.mock.calls[0][1].body);
    expect(body).toEqual({ code: "CODE", client_id: "CID", redirect_uri: "https://x.test/cb", grant_type: "authorization_code", code_verifier: "VER" });
    expect(fetchFn.mock.calls[0][1].headers["trakt-api-key"]).toBe("CID");
  });
  it("code refusé / panne / renouvellement révoqué", async () => {
    expect(await exchangeCode("C", "u", "x", "v", async () => res(401, "{}"))).toEqual({ error: "denied" });
    expect(await exchangeCode("C", "u", "x", "v", async () => { throw new Error(); })).toEqual({ error: "upstream" });
    expect(await refreshTokens("C", "u", "R", async () => res(401, "{}"))).toEqual({ error: "revoked" });
    expect(await refreshTokens("C", "u", "R", async () => res(503, "{}"))).toEqual({ error: "upstream" });
  });
});

describe("trakt : scrobble", () => {
  it("corps d'appareil validé", () => {
    expect(parseScrobble({ action: "start", progress: 12.345, kind: "movie", title: "Alien", year: 1979 })).toEqual({ action: "start", progress: 12.35, kind: "movie", title: "Alien", year: 1979, tmdb: null });
    expect(parseScrobble({ action: "stop", progress: 90, kind: "episode", title: "Lost", season: 1, episode: 2, tmdb: 4607 })).toMatchObject({ season: 1, episode: 2, tmdb: 4607 });
    for (const bad of [null, {}, { action: "delete", progress: 1, kind: "movie", title: "a" }, { action: "start", progress: 101, kind: "movie", title: "a" },
      { action: "start", progress: 1, kind: "live", title: "a" }, { action: "start", progress: 1, kind: "movie", title: "" }, { action: "start", progress: 1, kind: "episode", title: "a", season: 1 }]) {
      expect(parseScrobble(bad)).toBeNull();
    }
  });
  it("titre IPTV nettoyé pour la recherche", () => {
    expect(searchTitle("FR - Alien (1979) 4K")).toBe("Alien");
    expect(searchTitle("|FR| La Casa de Papel HD")).toBe("La Casa de Papel");
    expect(searchTitle("Dune 2021")).toBe("Dune");
  });
  it("identifiant TMDB retrouvé par titre + année, puis mis en cache", async () => {
    const m = new Map();
    const cache = { match: async (k) => m.get(k.url)?.clone(), put: async (k, r) => { m.set(k.url, r); } };
    const fetchFn = vi.fn(async () => res(200, { results: [{ id: 348 }] }));
    const env = { TMDB_READ_TOKEN: "T" };
    expect(await resolveTmdb(env, "movie", "FR - Alien 4K", 1979, { fetchFn, cache })).toBe(348);
    expect(String(fetchFn.mock.calls[0][0])).toContain("year=1979");
    expect(await resolveTmdb(env, "movie", "FR - Alien 4K", 1979, { fetchFn, cache })).toBe(348);
    expect(fetchFn).toHaveBeenCalledTimes(1);
    expect(await resolveTmdb({}, "movie", "Alien", 1979, { fetchFn, cache })).toBeNull();
  });
  it("corps Trakt : film, épisode, non identifié", () => {
    expect(traktScrobbleBody({ kind: "movie", progress: 50 }, 348)).toMatchObject({ movie: { ids: { tmdb: 348 } }, progress: 50 });
    expect(traktScrobbleBody({ kind: "episode", progress: 80, season: 1, episode: 2 }, 4607)).toMatchObject({ show: { ids: { tmdb: 4607 } }, episode: { season: 1, number: 2 } });
    expect(traktScrobbleBody({ kind: "movie", progress: 1 }, null)).toBeNull();
  });
  it("réponses Trakt traduites", async () => {
    const st = async (s) => sendScrobble("C", "A", "start", {}, async () => res(s, "{}"));
    expect(await st(201)).toBe("ok");
    expect(await st(409)).toBe("ok");
    expect(await st(401)).toBe("unauthorized");
    expect(await st(404)).toBe("unmatched");
    expect(await st(500)).toBe("upstream");
  });
});

describe("trakt : routes", () => {
  it("« Connecter Trakt » : redirection vers Trakt avec état et défi", async () => {
    const acct = await newAccount();
    const page = await (await call("/", { cookie: acct.cookie, ip: acct.ip })).text();
    expect(page).toContain('action="/trakt/connect"');
    const r = await call("/trakt/connect", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(r.status).toBe(302);
    const u = new URL(r.headers.get("location"));
    expect(u.origin).toBe("https://trakt.tv");
    expect(u.searchParams.get("redirect_uri")).toMatch(/\/trakt\/callback$/);
    expect(u.searchParams.get("code_challenge_method")).toBe("S256");
    expect(u.searchParams.get("state")).toMatch(/^[A-Za-z0-9_-]{16,64}$/);
  });
  it("retour de Trakt : sans cookie de session (SameSite=Strict absent), lié au navigateur par le cookie Lax", async () => {
    const a = await newAccount();
    const start = await call("/trakt/connect", { method: "POST", ip: a.ip, cookie: a.cookie, form: { csrf: a.csrf } });
    const state = new URL(start.headers.get("location")).searchParams.get("state");
    const setCookie = start.headers.get("set-cookie");
    expect(setCookie).toContain(`utv_trakt=${state}`);
    expect(setCookie).toContain("SameSite=Lax");
    expect(setCookie).toContain("Path=/trakt");
    const back = (cookie, st = state) => call(`/trakt/callback?code=abc&state=${st}`, { cookie, ip: freshIp() });
    // Navigateur sans le cookie de départ (lien fabriqué par un tiers) : refusé, et jamais renvoyé à /login.
    const foreign = await back(undefined);
    expect(foreign.status).toBe(200);
    expect(await foreign.text()).toContain('url=/?e=trakt#compte');
    expect((await back(`utv_trakt=${"x".repeat(32)}`)).status).toBe(200);
    // Bon navigateur, sans session : traité (ici l'échange échoue faute de vrai Trakt → e=trakt), pas de boucle /login.
    const r = await back(`utv_trakt=${state}`);
    expect(r.status).toBe(200);
    expect(r.headers.get("location")).toBeNull();
    expect(r.headers.get("set-cookie")).toContain("Max-Age=0");
    // État consommé : rejeu refusé.
    expect(await (await back(`utv_trakt=${state}`)).text()).toContain("e=trakt");
    expect(await (await back("utv_trakt=zz", "zz")).text()).toContain("e=trakt");
  });
  it("scrobble d'un appareil : compte non relié → linked:false ; corps invalide → 400 ; sans jeton → 401", async () => {
    const acct = await newAccount();
    const dev = await pairDevice(acct, "Salon");
    const post = (json, headers = bearer(dev.token)) => call("/api/device/trakt/scrobble", { method: "POST", ip: freshIp(), headers, json });
    const ok = await post({ action: "start", progress: 3, kind: "movie", title: "Alien", year: 1979 });
    expect(ok.status).toBe(200);
    expect(await ok.json()).toEqual({ linked: false });
    expect((await post({ action: "start" })).status).toBe(400);
    expect((await post({ action: "start", progress: 3, kind: "movie", title: "A" }, {})).status).toBe(401);
  });
  it("déconnexion : redirection, panneau repasse en « Connecter »", async () => {
    const acct = await newAccount();
    const r = await call("/trakt/disconnect", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf } });
    expect(r.headers.get("location")).toBe("/?m=trakt_off#compte");
  });
});
