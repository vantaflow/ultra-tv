import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  StalkerAuthError, StalkerClient, absoluteUrl, accountFrom, cleanCmd, directUrlOf, endpointCandidates, formatMacTyping, maskMac,
  md5Hex, normalizeMac, packCreds, parseStalkerDate, parseStalkerLink, stalkerLink, unpackCreds,
} from "./stalker";
import { requestHeaders } from "./transport";
import { stalkerFormat } from "@/player/resolve";

describe("règles du protocole", () => {
  it("MAC : normalisée, saisie formatée, masquée", () => {
    expect(normalizeMac("00-1a-79-ab-cd-ef")).toBe("00:1A:79:AB:CD:EF");
    expect(normalizeMac("00:1A:79:AB:CD")).toBeNull();
    expect(formatMacTyping("001a79ab")).toBe("00:1A:79:AB");
    expect(formatMacTyping("001a79abcdef12")).toBe("00:1A:79:AB:CD:EF");
    expect(maskMac("00:1A:79:AB:CD:EF")).toBe("••:••:••:••:CD:EF");
  });
  it("identifiants facultatifs empaquetés", () => {
    expect(packCreds("", "")).toBe("");
    expect(unpackCreds(packCreds("jean", "a:b"))).toEqual({ login: "jean", password: "a:b" });
    expect(unpackCreds("seul")).toEqual({ login: "", password: "seul" });
  });
  it("adresse interne stalker:", () => {
    expect(parseStalkerLink(stalkerLink("itv", "ffmpeg http://localhost/ch/1_"))).toEqual({ type: "itv", cmd: "ffmpeg http://localhost/ch/1_", series: null });
    expect(parseStalkerLink(stalkerLink("vod", "eyJ9:x", 3))).toEqual({ type: "vod", cmd: "eyJ9:x", series: 3 });
    expect(parseStalkerLink("http://h/x.ts")).toBeNull();
  });
  it("commande : préfixe retiré, localhost non jouable", () => {
    expect(cleanCmd("ffmpeg http://s/a.ts")).toBe("http://s/a.ts");
    expect(cleanCmd("auto http://s/a.ts")).toBe("http://s/a.ts");
    expect(directUrlOf("ffmpeg http://localhost/ch/1_")).toBeNull();
    expect(directUrlOf("ffmpeg http://real/x.ts")).toBe("http://real/x.ts");
  });
  it("points d'entrée, logos", () => {
    expect(endpointCandidates("http://h:8080/c/")).toEqual(["http://h:8080/c/portal.php", "http://h:8080/portal.php", "http://h:8080/server/load.php", "http://h:8080/stalker_portal/server/load.php"]);
    expect(endpointCandidates("h/stalker_portal/c")).toContain("http://h/stalker_portal/server/load.php");
    expect(absoluteUrl("http://h/stalker_portal/c/", "1.png")).toBe("http://h/stalker_portal/misc/logos/320/1.png");
    expect(absoluteUrl("http://h/c/", "/misc/x.png")).toBe("http://h/misc/x.png");
  });
  it("dates d'échéance", () => {
    expect(parseStalkerDate("2025-12-31 00:00:00")).toBe(Date.UTC(2025, 11, 31));
    expect(parseStalkerDate("1767139200")).toBe(Date.UTC(2025, 11, 31));
    expect(parseStalkerDate("December 31, 2025, 12:00 am")).toBe(Date.UTC(2025, 11, 31));
    expect(parseStalkerDate("0000-00-00 00:00:00")).toBeNull();
  });
  it("abonnement : actif, expiré, bloqué", () => {
    expect(accountFrom({ end_date: "2099-01-01" }, {}, 0)).toEqual({ status: "Active", expDate: Date.UTC(2099, 0, 1) });
    expect(accountFrom({ end_date: "2020-01-01" }, {}, Date.UTC(2024, 0, 1)).status).toBe("Expired");
    expect(accountFrom({}, { blocked: "1" }).status).toBe("Disabled");
  });
  it("MD5 (numéro de série dérivé)", () => {
    expect(md5Hex("")).toBe("d41d8cd98f00b204e9800998ecf8427e");
    expect(md5Hex("The quick brown fox jumps over the lazy dog")).toBe("9e107d9d372bb6826bd81d3542a419d6");
    expect(md5Hex("00:1A:79:AB:CD:EF".repeat(5))).toHaveLength(32);
  });
  it("format du lien de lecture", () => {
    expect(stalkerFormat("http://s/play/live.php?stream=1&extension=ts", "live")).toBe("ts");
    expect(stalkerFormat("http://s/a/index.m3u8?t=1", "live")).toBe("hls");
    expect(stalkerFormat("http://s/vod/a.mkv", "movie")).toBe("native");
  });
  it("en-têtes du portail relayés par le proxy Electron / Cloudflare (liste fermée)", () => {
    const o = { headers: { Cookie: "mac=x", Authorization: "Bearer t", "X-User-Agent": "Model: MAG250", Host: "evil" } };
    expect(requestHeaders({ mode: "electron", base: "http://127.0.0.1/" }, o)).toEqual({ "X-UT-H-cookie": "mac=x", "X-UT-H-authorization": "Bearer t", "X-UT-H-x-user-agent": "Model: MAG250" });
    expect(requestHeaders({ mode: "proxy", url: "https://p/" }, o)).toHaveProperty("X-SV-H-cookie", "mac=x");
  });
});

// ---------------------------------------------------------------- faux portail

type Handler = (q: URLSearchParams, headers: Headers) => unknown | string;

function fakePortal(routes: Record<string, Handler>) {
  const calls: { path: string; q: URLSearchParams; headers: Headers }[] = [];
  let tokens = 0;
  const refuse = new Set<string>();
  const fetchMock = vi.fn(async (input: string, init?: RequestInit) => {
    const u = new URL(input);
    const headers = new Headers(init?.headers);
    calls.push({ path: u.pathname, q: u.searchParams, headers });
    if (u.pathname !== "/stalker_portal/server/load.php") return new Response("not found", { status: 404 });
    const key = `${u.searchParams.get("type")}/${u.searchParams.get("action")}`;
    if (key === "stb/handshake") return new Response(JSON.stringify({ js: { token: `T${++tokens}` } }));
    if (!headers.get("Authorization")) return new Response("Authorization failed.");
    if (refuse.delete(key)) return new Response("Authorization failed.");
    const h = routes[key];
    const out = h ? h(u.searchParams, headers) : { js: [] };
    return new Response(typeof out === "string" ? out : JSON.stringify(out));
  });
  return { fetchMock, calls, refuse, tokens: () => tokens };
}

describe("StalkerClient contre un faux portail", () => {
  const portal = "http://portal.test/stalker_portal/c/";
  const mac = "00:1A:79:AB:CD:EF";
  let p: ReturnType<typeof fakePortal>;
  const make = (routes: Record<string, Handler>, opts: ConstructorParameters<typeof StalkerClient>[2] = {}) => {
    p = fakePortal({ "stb/get_profile": () => ({ js: { status: 0 } }), ...routes });
    vi.stubGlobal("fetch", p.fetchMock);
    return new StalkerClient({ mode: "direct" }, { portal, mac }, opts);
  };
  beforeEach(() => { vi.restoreAllMocks(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  it("sonde le point d'entrée, le mémorise, envoie Cookie MAC / X-User-Agent / jeton", async () => {
    let remembered = "";
    const c = make({}, { onEndpoint: (api) => { remembered = api; } });
    const api = await c.probe();
    expect(api).toBe("http://portal.test/stalker_portal/server/load.php");
    expect(remembered).toBe(api);
    expect(p.calls[0]!.path).toBe("/stalker_portal/c/portal.php");
    const hs = p.calls.find((x) => x.q.get("action") === "handshake" && x.path.endsWith("load.php"))!;
    expect(hs.headers.get("Cookie")).toBe("mac=00%3A1A%3A79%3AAB%3ACD%3AEF; stb_lang=en; timezone=UTC");
    expect(hs.headers.get("X-User-Agent")).toBe("Model: MAG250; Link: WiFi");
    expect(hs.q.get("JsHttpRequest")).toBe("1-xml");
    const prof = p.calls.find((x) => x.q.get("action") === "get_profile")!;
    expect(prof.headers.get("Authorization")).toBe("Bearer T1");
    expect(prof.q.get("sn")).toHaveLength(13);
  });

  it("point d'entrée connu : aucune sonde", async () => {
    const c = make({ "itv/get_genres": () => ({ js: [{ id: "*", title: "All" }, { id: "1", title: "News" }] }) }, { endpoint: "http://portal.test/stalker_portal/server/load.php" });
    expect(await c.genres()).toEqual([{ id: "1", title: "News" }]);
    expect(p.calls.some((x) => x.path.endsWith("portal.php"))).toBe(false);
  });

  it("jeton refusé : une seule nouvelle poignée de main", async () => {
    const c = make({ "itv/get_genres": () => ({ js: [{ id: "2", title: "Sport" }] }) });
    await c.probe();
    p.refuse.add("itv/get_genres");
    expect(await c.genres()).toEqual([{ id: "2", title: "Sport" }]);
    expect(p.tokens()).toBe(2);
  });

  it("refus persistant : erreur d'authentification", async () => {
    const c = make({ "itv/get_genres": () => "Authorization failed." });
    await expect(c.genres()).rejects.toBeInstanceOf(StalkerAuthError);
  });

  it("chaînes : get_all_channels, sinon pages genre=*", async () => {
    const all = make({ "itv/get_all_channels": () => ({ js: { data: [{ id: "1", name: "A", cmd: "c1", tv_genre_id: "5" }] } }) });
    expect((await all.allChannels()).map((x) => x.id)).toEqual(["1"]);
    const paged = make({
      "itv/get_all_channels": () => ({ js: { data: [] } }),
      "itv/get_ordered_list": (q) => ({ js: { total_items: 5, max_page_items: 2, data: [1, 2].map((i) => ({ id: String((Number(q.get("p")) - 1) * 2 + i), name: "x", cmd: "c" })).filter((o) => Number(o.id) <= 5) } }),
    });
    expect((await paged.allChannels()).map((x) => x.id)).toEqual(["1", "2", "3", "4", "5"]);
  });

  it("épisodes : tableau `series` de l'élément", async () => {
    const c = make({ "vod/get_ordered_list": () => ({ js: { total_items: 1, max_page_items: 14, data: [{ id: "777", name: "Dark", cmd: "S", series: [2, 1, 3] }] } }) });
    const eps = await c.episodes("777", null, null);
    expect(eps.map((e) => [e.season, e.num])).toEqual([[1, 1], [1, 2], [1, 3]]);
    expect(parseStalkerLink(eps[1]!.url)).toEqual({ type: "vod", cmd: "S", series: 2 });
  });

  it("épisodes : saisons du portail récent", async () => {
    const c = make({
      "vod/get_ordered_list": (q) => q.get("season_id") === "0"
        ? { js: { total_items: 1, max_page_items: 14, data: [{ id: "s1", name: "Season 2", is_season: true }] } }
        : { js: { total_items: 2, max_page_items: 14, data: [{ id: "e1", name: "Un", series_number: "1", cmd: "E1", is_episode: 1 }, { id: "e2", name: "Deux", series_number: "2", cmd: "E2" }] } },
    });
    const eps = await c.episodes("900", null, null);
    expect(eps.map((e) => [e.season, e.num, e.title])).toEqual([[2, 1, "Un"], [2, 2, "Deux"]]);
    expect(parseStalkerLink(eps[1]!.url)!.cmd).toBe("E2");
  });

  it("create_link : lien nettoyé ; échec → commande directe", async () => {
    const c = make({
      "itv/create_link": (q) => ({ js: { cmd: `ffmpeg http://real/play?stream=${encodeURIComponent(q.get("cmd")!)}&play_token=Z` } }),
      "vod/create_link": (q) => ({ js: { cmd: `auto http://real/vod.mkv?s=${q.get("series")}` } }),
    });
    expect(await c.createLink("itv", "ffmpeg http://localhost/ch/1_")).toBe("http://real/play?stream=ffmpeg%20http%3A%2F%2Flocalhost%2Fch%2F1_&play_token=Z");
    expect(await c.createLink("vod", "X", 4)).toBe("http://real/vod.mkv?s=4");
    const broken = make({ "itv/create_link": () => "Authorization failed." });
    expect(await broken.createLink("itv", "ffmpeg http://real.host/x.ts")).toBe("http://real.host/x.ts");
  });

  it("programme court", async () => {
    const c = make({ "itv/get_short_epg": () => ({ js: [{ name: "Journal", descr: "Infos", start_timestamp: 100, stop_timestamp: "200" }, { name: "x" }] }) });
    expect(await c.shortEpg(5)).toEqual([{ title: "Journal", desc: "Infos", start: 100_000, end: 200_000 }]);
  });
});
