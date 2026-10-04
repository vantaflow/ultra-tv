import { describe, it, expect, vi } from "vitest";
import { searchTarget, subtitlesSearch, subtitlesDownload, MAX_SRT_BYTES } from "../src/subtitles.js";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";

const sp = (s) => new URL("https://x.test/?" + s).searchParams;
const mkCache = () => { const m = new Map(); return { match: async (k) => m.get(k.url)?.clone(), put: async (k, r) => { m.set(k.url, r); } }; };
const ENV = { OPENSUBTITLES_API_KEY: "OSKEY" };
const upstreamSearch = { data: [
  { attributes: { language: "fr", release: "Alien.1979.BluRay", download_count: 10, files: [{ file_id: 111 }] } },
  { attributes: { language: "en", release: "Alien", download_count: 99, files: [{ file_id: 222 }] } },
  { attributes: { language: "en", files: [] } },
] };

describe("searchTarget", () => {
  it("normalise_triee_etSansCle", () => {
    const p = searchTarget(sp("query=%20Alien%20&languages=FR,en&year=1979"));
    expect(p.toString()).toBe("languages=en%2Cfr&query=alien&year=1979");
  });
  it.each(["", "query=%20", "query=a&languages=fr%0d%0a", "query=a&languages=a,b,c,d,e,f", "query=a&year=abcd", `query=${"x".repeat(121)}`])("refuse_%s", (q) =>
    expect(searchTarget(sp(q))).toBeNull());
});

describe("subtitlesSearch", () => {
  it("sansSecret_503_sansAppelAmont", async () => {
    const fetchFn = vi.fn();
    const r = await subtitlesSearch(sp("query=alien"), {}, { fetchFn, cache: mkCache() });
    expect(r.status).toBe(503); expect(await r.json()).toEqual({ error: "subtitles_not_configured" });
    expect(fetchFn).not.toHaveBeenCalled();
  });
  it("requeteInvalide_400", async () => expect((await subtitlesSearch(sp("languages=fr"), ENV, { fetchFn: vi.fn(), cache: mkCache() })).status).toBe(400));
  it("cleEnvoyeeEnEnteteAmont_jamaisDansLaReponse_formatDuContrat", async () => {
    const fetchFn = vi.fn(async () => new Response(JSON.stringify(upstreamSearch), { status: 200 }));
    const r = await subtitlesSearch(sp("query=Alien&languages=fr,en"), ENV, { fetchFn, cache: mkCache() });
    const body = await r.text();
    expect(fetchFn.mock.calls[0][1].headers["api-key"]).toBe("OSKEY");
    expect(fetchFn.mock.calls[0][0]).not.toContain("OSKEY");
    expect(fetchFn.mock.calls[0][1].redirect).toBe("manual");
    expect(body).not.toContain("OSKEY");
    expect(JSON.parse(body).results).toEqual([
      { id: "111", language: "fr", release: "Alien.1979.BluRay", downloads: 10 },
      { id: "222", language: "en", release: "Alien", downloads: 99 },
    ]);
  });
  it("deuxiemeAppel_serviDuCache", async () => {
    const fetchFn = vi.fn(async () => new Response(JSON.stringify(upstreamSearch), { status: 200 }));
    const cache = mkCache();
    await subtitlesSearch(sp("query=Alien"), ENV, { fetchFn, cache });
    await subtitlesSearch(sp("query=alien"), ENV, { fetchFn, cache });
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });
  it("erreurAmont_502_generique_nonCachee", async () => {
    const fetchFn = vi.fn(async () => new Response("bad api-key OSKEY", { status: 401 }));
    const r = await subtitlesSearch(sp("query=alien"), ENV, { fetchFn, cache: mkCache() });
    expect(r.status).toBe(502); expect(await r.text()).not.toContain("OSKEY");
  });
});

describe("subtitlesDownload", () => {
  const dl = (link, srt = "1\n00:00:01,000 --> 00:00:02,000\nSalut\n") => vi.fn(async (url) =>
    url.endsWith("/download") ? new Response(JSON.stringify({ link }), { status: 200 }) : new Response(srt, { status: 200 }));
  it("sansSecret_503", async () => expect((await subtitlesDownload(sp("id=1"), {}, { fetchFn: vi.fn(), cache: mkCache() })).status).toBe(503));
  it.each(["", "id=0", "id=abc", "id=1;2", "id=123456789012345"])("idInvalide_%s_400", async (q) =>
    expect((await subtitlesDownload(sp(q), ENV, { fetchFn: vi.fn(), cache: mkCache() })).status).toBe(400));
  it("renvoieLeSrt_enUtf8_etMetEnCache", async () => {
    const fetchFn = dl("https://dl.opensubtitles.com/x/abc.srt"); const cache = mkCache();
    const r = await subtitlesDownload(sp("id=111"), ENV, { fetchFn, cache });
    expect(r.status).toBe(200); expect(r.headers.get("content-type")).toContain("utf-8");
    expect(await r.text()).toContain("Salut");
    await subtitlesDownload(sp("id=111"), ENV, { fetchFn, cache });
    expect(fetchFn).toHaveBeenCalledTimes(2);   // POST + GET du premier appel seulement
  });
  it.each(["http://dl.opensubtitles.com/a.srt", "https://evil.example/a.srt", "https://opensubtitles.com.evil.example/a.srt", "https://127.0.0.1/a.srt", "pas une url"])(
    "lienAmont_%s_refuse_SSRF", async (link) => {
      const fetchFn = dl(link);
      expect((await subtitlesDownload(sp("id=111"), ENV, { fetchFn, cache: mkCache() })).status).toBe(502);
      expect(fetchFn).toHaveBeenCalledTimes(1);   // jamais de requête vers le lien refusé
    });
  it("fichierTropGros_502", async () => {
    const r = await subtitlesDownload(sp("id=111"), ENV, { fetchFn: dl("https://dl.opensubtitles.com/a.srt", "x".repeat(MAX_SRT_BYTES + 1)), cache: mkCache() });
    expect(r.status).toBe(502);
  });
  it("quotaAmont_429", async () => {
    const fetchFn = vi.fn(async () => new Response("{}", { status: 406 }));
    expect((await subtitlesDownload(sp("id=111"), ENV, { fetchFn, cache: mkCache() })).status).toBe(429);
  });
});

describe("/api/subtitles/* : authentification, secret absent, débit", () => {
  it("sansJeton_401", async () => {
    expect((await call("/api/subtitles/search?query=alien", { ip: freshIp() })).status).toBe(401);
    expect((await call("/api/subtitles/download?id=1", { ip: freshIp() })).status).toBe(401);
  });
  it("appareilAppaire_secretAbsent_503", async () => {
    const dev = await pairDevice(await newAccount());
    const r = await call("/api/subtitles/search?query=alien", { ip: dev.ip, headers: bearer(dev.token) });
    expect(r.status).toBe(503);
  });
  it("appareilAppaire_limiteDeDebit_429", async () => {
    const dev = await pairDevice(await newAccount());
    let last;
    for (let i = 0; i < 45; i++) last = await call("/api/subtitles/download?id=1", { ip: dev.ip, headers: bearer(dev.token) });
    expect(last.status).toBe(429);
  });
});
