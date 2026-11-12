import { describe, expect, it } from "vitest";
import { fanartProxy, pickFanart, reduceFanart, reduceShow, sportsTeamProxy, tvmazeProxy } from "../src/extmeta.js";

const sp = (s) => new URL("https://x.test/?" + s).searchParams;
const ok = (body, extra = {}) => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" }, ...extra });

describe("Fanart.tv", () => {
  it("langue demandée, puis anglais, puis sans langue", () => {
    const l = [{ url: "https://a/x", lang: "de", likes: "9" }, { url: "https://a/en", lang: "en", likes: "1" }, { url: "https://a/fr", lang: "fr", likes: "0" }];
    expect(pickFanart(l, "fr")).toBe("https://a/fr");
    expect(pickFanart(l, "es")).toBe("https://a/en");
    expect(pickFanart([], "fr")).toBeNull();
  });
  it("film : logo HD, fond et vignette", () => {
    const r = reduceFanart("movie", { hdmovielogo: [{ url: "https://l", lang: "en" }], moviebackground: [{ url: "https://b", lang: "00" }] }, "fr");
    expect(r).toEqual({ logo: "https://l", background: "https://b", thumb: null });
  });
  it("sans clé : 503 ; chemin hors liste : 404 ; clé jamais renvoyée", async () => {
    expect((await fanartProxy("movie/603", sp("lang=fr"), {}, { cache: null })).status).toBe(503);
    expect((await fanartProxy("person/1", sp(""), { FANART_API_KEY: "k" }, { cache: null })).status).toBe(404);
    let called = "";
    const r = await fanartProxy("tv/121361", sp("lang=fr"), { FANART_API_KEY: "secret" }, { cache: null, fetchFn: async (u) => { called = u; return ok({ hdtvlogo: [{ url: "https://logo", lang: "fr" }] }); } });
    expect(called).toContain("/v3/tv/121361");
    const body = await r.text();
    expect(body).toContain("https://logo");
    expect(body).not.toContain("secret");
  });
});

describe("TVmaze", () => {
  it("épisodes précédent et prochain, résumé sans HTML", () => {
    const r = reduceShow({ id: 82, status: "Running", network: { name: "HBO" }, _embedded: { nextepisode: { season: 2, number: 5, name: "X", airstamp: "2026-11-20T02:00:00+00:00", summary: "<p>Un <b>bon</b> épisode</p>" } } });
    expect(r.next).toMatchObject({ season: 2, number: 5, name: "X", summary: "Un bon épisode" });
    expect(r.next.airAt).toBe(Date.parse("2026-11-20T02:00:00Z"));
    expect(r.previous).toBeNull();
  });
  it("recherche par IMDb puis fiche avec épisodes", async () => {
    const urls = [];
    const fetchFn = async (u) => {
      urls.push(u);
      if (u.includes("/lookup/")) return new Response(null, { status: 301, headers: { location: "https://api.tvmaze.com/shows/82" } });
      return ok({ id: 82, status: "Ended", _embedded: {} });
    };
    const r = await tvmazeProxy(sp("imdb=tt0944947"), {}, { cache: null, fetchFn });
    expect(r.status).toBe(200);
    expect(urls[0]).toContain("lookup/shows?imdb=tt0944947");
    expect(urls[1]).toContain("/shows/82?embed[]=previousepisode&embed[]=nextepisode");
    expect((await tvmazeProxy(sp("imdb=x"), {}, { cache: null })).status).toBe(404);
  });
});

describe("TheSportsDB", () => {
  it("écusson et compétition d'une équipe ; nom invalide refusé", async () => {
    const r = await sportsTeamProxy(sp("name=Paris SG"), {}, { cache: null, fetchFn: async (u) => { expect(u).toContain("/json/123/searchteams.php?t=Paris%20SG"); return ok({ teams: [{ strTeam: "Paris SG", strBadge: "https://b.png", strLeague: "French Ligue 1", strSport: "Soccer" }] }); } });
    expect(await r.json()).toEqual({ team: { name: "Paris SG", short: null, badge: "https://b.png", league: "French Ligue 1", sport: "Soccer" } });
    expect((await sportsTeamProxy(sp("name=<script>"), {}, { cache: null })).status).toBe(404);
  });
});
