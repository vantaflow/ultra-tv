// Films et séries d'un portail Stalker contre un faux portail en mémoire (voir fakeVodPortal.ts) :
// module `type=series`, catégorie en erreur, jeton expiré en route, gros catalogue paginé, portail sans `total_items`.
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FAKE_MAC, FAKE_PORTAL, FakeVodPortal, FAKE_API } from "./fakeVodPortal";
import { MAX_PAGES, PAGE_ATTEMPTS, StalkerClient, parseStalkerLink, retryDefaults, stalkerLink } from "./stalker";
import { StalkerSyncLog, errorClass } from "./stalkerLog";
import { HttpError } from "./transport";

let fake: FakeVodPortal;
const client = (log?: StalkerSyncLog) => {
  vi.stubGlobal("fetch", fake.fetch);
  return new StalkerClient({ mode: "direct" }, { portal: FAKE_PORTAL, mac: FAKE_MAC }, { endpoint: FAKE_API, log });
};
const lists = () => fake.urls.filter((u) => u.searchParams.get("action") === "get_ordered_list");

beforeEach(() => { fake = new FakeVodPortal(); retryDefaults.delayMs = 1; });
afterEach(() => { vi.unstubAllGlobals(); retryDefaults.delayMs = 700; });

describe("adresse interne : type series", () => {
  it("les liens itv et vod ne changent pas, series est ajouté", () => {
    expect(parseStalkerLink(stalkerLink("itv", "ffmpeg http://localhost/ch/1_"))).toEqual({ type: "itv", cmd: "ffmpeg http://localhost/ch/1_", series: null });
    expect(parseStalkerLink(stalkerLink("vod", "eyJ9:x", 3))).toEqual({ type: "vod", cmd: "eyJ9:x", series: 3 });
    expect(parseStalkerLink(stalkerLink("series", "ep/1", 2))).toEqual({ type: "series", cmd: "ep/1", series: 2 });
    expect(parseStalkerLink("stalker:series::")).toEqual({ type: "series", cmd: "", series: null });
    expect(parseStalkerLink("stalker:vod::")).toBeNull();
    expect(parseStalkerLink("stalker:xyz:1:c")).toBeNull();
  });
});

describe("module type=series", () => {
  it("catégories préfixées, la VOD n'a rien, saisons puis épisodes, create_link vod puis series", async () => {
    const c = client();
    const cats = await c.seriesCategories();
    expect(cats.map((x) => x.id)).toEqual(["s:1", "s:2", "s:3"]);
    expect(await c.vodCategories()).toEqual([]);

    const series = await c.seriesOfCategory("s:1");
    expect(series).toHaveLength(30);
    expect(lists().some((u) => u.searchParams.get("type") === "series" && u.searchParams.get("category") === "1")).toBe(true);

    const eps = await c.episodes("s:1-0", null, null);
    expect(eps).toHaveLength(6); // 2 saisons x 3 épisodes
    expect([...new Set(eps.map((e) => e.season))]).toEqual([1, 2]);
    expect(lists().some((u) => u.searchParams.get("type") === "series" && u.searchParams.get("movie_id") === "1-0")).toBe(true);
    const link = parseStalkerLink(eps[0]!.url)!;
    expect(link.type).toBe("series");

    // create_link : vod d'abord (le plus courant)…
    expect(await c.createLink(link.type, link.cmd, link.series)).toBe(`http://flux.invalid/vod/${encodeURIComponent(link.cmd)}.mkv`);
    // … puis le module séries si la VOD ne donne rien ou échoue.
    fake.vodCreateLinkFails = true;
    expect(await c.createLink(link.type, link.cmd, link.series)).toBe(`http://flux.invalid/series/${encodeURIComponent(link.cmd)}.mkv`);
    fake.vodCreateLinkFails = false; fake.vodCreateLinkStatus = 500;
    expect(await c.createLink(link.type, link.cmd, link.series)).toBe(`http://flux.invalid/series/${encodeURIComponent(link.cmd)}.mkv`);
  });

  it("create_link : les deux modules en échec → la première erreur", async () => {
    const c = client();
    vi.stubGlobal("fetch", async (input: string, init?: RequestInit) => {
      const u = new URL(input);
      if (u.searchParams.get("action") !== "create_link") return fake.fetch(input, init);
      return new Response("", { status: u.searchParams.get("type") === "vod" ? 502 : 404 });
    });
    await c.probe();
    const err = await c.createLink("series", "ep/1", 1).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(HttpError);
    expect((err as HttpError).status).toBe(502);
  });

  it("sans saison : épisodes listés directement sous la série", async () => {
    fake.seasons = 0;
    const eps = await client().episodes("s:2-1", null, null);
    expect(eps.map((e) => [e.season, e.num])).toEqual([[1, 1], [1, 2], [1, 3]]);
    expect(parseStalkerLink(eps[0]!.url)!.type).toBe("series");
  });

  it("série du module VOD (sans préfixe) : comportement inchangé, liens vod", async () => {
    const c = client();
    vi.stubGlobal("fetch", async (input: string, init?: RequestInit) => {
      const u = new URL(input);
      if (u.searchParams.get("action") === "get_ordered_list") return new Response(JSON.stringify({ js: { total_items: 1, max_page_items: 14, data: [{ id: "777", name: "Dark", cmd: "S", series: [2, 1] }] } }));
      return fake.fetch(input, init);
    });
    const eps = await c.episodes("777", null, null);
    expect(eps.map((e) => parseStalkerLink(e.url)!.type)).toEqual(["vod", "vod"]);
  });
});

describe("pagination et reprises", () => {
  it("catégorie qui répond toujours 500 : tentatives bornées puis erreur", async () => {
    fake.failingCategories.add("3");
    const err = await client().seriesOfCategory("s:3").catch((e: unknown) => e);
    expect(err).toBeInstanceOf(HttpError);
    expect((err as HttpError).status).toBe(500);
    expect(lists().filter((u) => u.searchParams.get("category") === "3")).toHaveLength(PAGE_ATTEMPTS);
    expect(PAGE_ATTEMPTS).toBe(3);
  });

  it("jeton expiré en pleine pagination : une seule nouvelle poignée de main, rien ne manque", async () => {
    fake.seriesCategories = { "1": ["FR Séries", 200] };
    const c = client();
    await c.seriesCategories();
    const before = fake.handshakes;
    fake.expireAfterListRequests = 4;
    const series = await c.seriesOfCategory("1");
    expect(series).toHaveLength(200);
    expect(new Set(series.map((s) => s.id)).size).toBe(200);
    expect(fake.handshakes).toBe(before + 1);
  });

  it("50 001 films à 14 par page : 3 572 pages lues, JsHttpRequest sur toutes les requêtes", async () => {
    fake.seriesCategories = {};
    fake.vodCategories = { "9": ["FR Films", 50_001] };
    const movies = await client().vodOfCategory("9");
    expect(movies).toHaveLength(50_001);
    expect(MAX_PAGES).toBe(20_000);
    expect(lists()).toHaveLength(Math.ceil(50_001 / 14)); // 3 572, au-delà de l'ancien plafond de 3 000
    expect(fake.urls.every((u) => u.searchParams.get("JsHttpRequest") === "1-xml")).toBe(true);
  }, 60_000);

  it("portail sans total_items : pages lues jusqu'à la page vide", async () => {
    fake.omitTotal = true;
    fake.seriesCategories = { "1": ["FR Séries", 40] };
    const series = await client().seriesOfCategory("1");
    expect(series).toHaveLength(40);
    expect(lists()).toHaveLength(4); // 14 + 14 + 12, puis une page vide
  });

  it("portail qui ignore p et renvoie toujours la même page : fin de la liste", async () => {
    fake.omitTotal = true;
    const c = client();
    vi.stubGlobal("fetch", async (input: string, init?: RequestInit) => {
      const u = new URL(input);
      if (u.searchParams.get("action") === "get_ordered_list") return new Response(JSON.stringify({ js: { data: [{ id: "a", name: "A" }, { id: "b", name: "B" }] } }));
      return fake.fetch(input, init);
    });
    expect(await c.vodOfCategory("x")).toHaveLength(2);
  });
});

describe("journal nettoyé", () => {
  it("classes d'erreur, jamais d'adresse, de MAC ni de nom ; 20 lignes détaillées au plus", () => {
    expect(errorClass(new HttpError(500))).toBe("http");
    expect(errorClass(new HttpError(403))).toBe("denied");
    expect(errorClass(new DOMException("x", "TimeoutError"))).toBe("timeout");
    const out: string[] = [];
    const log = new StalkerSyncLog((l) => out.push(l));
    for (let i = 0; i < 50; i++) log.skipped("list", "vod", new Error(`http://portal.test ${FAKE_MAC} FR Films`));
    log.summary("vod", 3, 50, 12);
    expect(out).toHaveLength(21);
    expect(out.join("\n")).not.toMatch(/portal\.test|00:1A|FR Films/);
    expect(out[0]).toBe("stalker sync skip step=list type=vod http=- error=io");
  });
});
