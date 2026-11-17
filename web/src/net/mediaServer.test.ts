// Logique pure des serveurs Jellyfin / Emby, sur des réponses d'API simulées (forme réelle des deux serveurs).
import { describe, expect, it } from "vitest";
import {
  authBody, authorization, durationSecs, episodesUrl, hlsUrl, imageUrl, isMediaLink, isMediaType, itemUrl, itemsUrl, mediaLink, normalizeServerUrl,
  numericId, parseAuth, parseItem, parseItems, parseMediaLink, parseViews, pickUser, playUrl, reportBody, reportPath, resumeSeconds, secToTicks,
  seriesInfoOf, ticksToSec, vodInfoOf,
} from "./mediaServer";

describe("adresse du serveur", () => {
  it("ajoute le schéma et retire la page web", () => {
    expect(normalizeServerUrl("192.168.1.10:8096")).toBe("http://192.168.1.10:8096");
    expect(normalizeServerUrl(" https://jf.exemple.org/ ")).toBe("https://jf.exemple.org");
    expect(normalizeServerUrl("http://nas:8096/web/index.html#!/home")).toBe("http://nas:8096");
    expect(normalizeServerUrl("nas:8096/jellyfin/web/")).toBe("http://nas:8096/jellyfin");
    expect(normalizeServerUrl("http://nas:8096?x=1")).toBe("http://nas:8096");
  });
  it("refuse les adresses inexploitables", () => {
    for (const bad of ["", "   ", "ftp://nas", "http://", "mon serveur"]) expect(normalizeServerUrl(bad), bad).toBeNull();
  });
});

describe("authentification", () => {
  it("en-tête d'appareil, guillemets retirés, jeton facultatif", () => {
    expect(authorization('dev"1')).toBe('MediaBrowser Client="Ultra TV", Device="Desktop", DeviceId="dev1", Version="1"');
    expect(authorization("d", "tok")).toContain('Token="tok"');
    expect(authorization("d")).not.toContain("Token");
  });
  it("corps JSON échappé", () => {
    expect(authBody('a"b', "p\\w")).toBe('{"Username":"a\\"b","Pw":"p\\\\w"}');
  });
  it("réponse AuthenticateByName", () => {
    expect(parseAuth('{"User":{"Name":"lilou","Id":"u123"},"AccessToken":"abc","ServerId":"s"}')).toEqual({ token: "abc", userId: "u123", userName: "lilou" });
    expect(parseAuth('{"User":{"Id":"u"}}')).toBeNull();
    expect(parseAuth('{"AccessToken":"t"}')).toBeNull();
    expect(parseAuth("<html>")).toBeNull();
  });
  it("utilisateur d'une clé API : nom, puis administrateur, puis premier", () => {
    const users = '[{"Name":"enfant","Id":"1","Policy":{"IsAdministrator":false}},{"Name":"Papa","Id":"2","Policy":{"IsAdministrator":true}},{"Name":"maman","Id":"3"}]';
    expect(pickUser(users, "MAMAN")?.id).toBe("3");
    expect(pickUser(users, "")?.id).toBe("2");
    expect(pickUser(users, "inconnu")?.id).toBe("2");
    expect(pickUser('[{"Name":"seul","Id":"1"}]', "")?.id).toBe("1");
    expect(pickUser("[]", "")).toBeNull();
    expect(pickUser("{}", "")).toBeNull();
  });
});

describe("bibliothèques et éléments", () => {
  it("films et séries seulement", () => {
    const views = parseViews('{"Items":[{"Name":"Films","Id":"m1","CollectionType":"movies"},{"Name":"Séries","Id":"s1","CollectionType":"tvshows"},{"Name":"Musique","Id":"x1","CollectionType":"music"},{"Name":"Mélange","Id":"x2"},{"Name":"Sans id","CollectionType":"movies"}]}');
    expect(views).toEqual([{ id: "m1", name: "Films", type: "movies" }, { id: "s1", name: "Séries", type: "tvshows" }]);
  });

  const page = `{"Items":[
    {"Name":"Le Voyage","Id":"abc","Type":"Movie","ProductionYear":2019,"CommunityRating":7.4,"Overview":"Un long voyage.","Genres":["Drame","Aventure"],
     "People":[{"Name":"Ada","Type":"Actor"},{"Name":"Bob","Type":"Director"},{"Name":"Cy","Type":"Actor"}],"RunTimeTicks":54000000000,
     "DateCreated":"2024-03-01T10:20:30.1234567Z","MediaSources":[{"Id":"src1","Container":"mkv,webm"}],"ImageTags":{"Primary":"tagP"},"BackdropImageTags":["tagB"],
     "UserData":{"PlaybackPositionTicks":6000000000,"Played":false}},
    {"Name":"Minimal","Id":"min"},{"Id":"sansnom"}],"TotalRecordCount":3}`;

  it("champs, acteurs et réalisateurs séparés, total du serveur", () => {
    const p = parseItems(page);
    expect(p.total).toBe(3);
    expect(p.items).toHaveLength(2);
    const m = p.items[0]!;
    expect(m).toMatchObject({ id: "abc", year: 2019, rating: 7.4, genres: ["Drame", "Aventure"], cast: ["Ada", "Cy"], directors: ["Bob"], container: "mkv", mediaSourceId: "src1", primaryTag: "tagP", backdropTag: "tagB", positionTicks: 6000000000, played: false });
    expect(m.added).toBe(Date.parse("2024-03-01T10:20:30.1234567Z"));
    expect(p.items[1]).toMatchObject({ year: null, container: null, positionTicks: 0, genres: [] });
  });
  it("réponse illisible = page vide", () => {
    expect(parseItems("pas du json")).toEqual({ total: 0, items: [] });
    expect(parseItem("[]")).toBeNull();
  });
  it("identifiant numérique stable (48 bits) et liens internes", () => {
    expect(numericId("0a1b2c3d4e5f60718293a4b5c6d7e8f9")).toBe(parseInt("0a1b2c3d4e5f", 16));
    expect(numericId("0a1b2c3d4e5f60718293a4b5c6d7e8f9")).toBe(numericId("0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9"));
    expect(Number.isSafeInteger(numericId("ffffffffffffffffffffffffffffffff"))).toBe(true);
    expect(numericId("12345")).toBe(12345);   // Emby : identifiants numériques gardés tels quels
    expect(numericId("abc")).toBe(numericId("abc"));
    expect(mediaLink("abc-DEF_1")).toBe("media:abc-DEF_1");
    expect(parseMediaLink("media:abc-DEF_1")).toBe("abc-DEF_1");
    expect(parseMediaLink("media:../x")).toBeNull();
    expect(parseMediaLink("stalker:vod::x")).toBeNull();
    expect(isMediaLink("media:x")).toBe(true);
    expect(isMediaLink("http://x")).toBe(false);
    expect(isMediaLink(undefined)).toBe(false);
    expect(isMediaType("jellyfin") && isMediaType("emby")).toBe(true);
    expect(isMediaType("xtream")).toBe(false);
  });
});

describe("adresses", () => {
  it("affiche : jamais d'authentification", () => {
    expect(imageUrl("http://h", "abc", "Primary", "tagP")).toBe("http://h/Items/abc/Images/Primary?maxWidth=480&quality=90&tag=tagP");
    expect(imageUrl("http://h", "abc")).not.toContain("api_key");
  });
  it("lecture directe : extension du conteneur, jeton en paramètre, jamais ailleurs", () => {
    expect(playUrl("http://h", "abc", "tok", "src1", "MKV")).toBe("http://h/Videos/abc/stream.mkv?static=true&MediaSourceId=src1&api_key=tok");
    expect(playUrl("http://h", "abc", "tok")).toBe("http://h/Videos/abc/stream?static=true&api_key=tok");
    expect(playUrl("http://h", "abc", "tok", null, "../x")).toBe("http://h/Videos/abc/stream?static=true&api_key=tok");
    expect(playUrl("http://h", "abc", "a b&c")).toContain("api_key=a%20b%26c");
  });
  it("repli HLS", () => {
    const u = hlsUrl("http://h", "abc", "tok", "src1");
    expect(u.startsWith("http://h/Videos/abc/master.m3u8?MediaSourceId=src1&VideoCodec=h264")).toBe(true);
    expect(u.endsWith("&api_key=tok")).toBe(true);
  });
  it("requêtes", () => {
    expect(itemsUrl("http://h", "u", "lib", "Movie", 300)).toContain("/Users/u/Items?ParentId=lib&IncludeItemTypes=Movie&Recursive=true&Fields=");
    expect(itemsUrl("http://h", "u", "lib", "Movie", 300)).toMatch(/StartIndex=300&Limit=300$/);
    expect(episodesUrl("http://h", "ser", "u")).toBe("http://h/Shows/ser/Episodes?UserId=u&Fields=Overview,MediaSources,RunTimeTicks,ImageTags");
    expect(itemUrl("http://h", "u", "i")).toBe("http://h/Users/u/Items/i");
  });
});

describe("reprise et rapports", () => {
  it("ticks <-> secondes", () => {
    expect(ticksToSec(6_000_000_000)).toBe(600);
    expect(secToTicks(600)).toBe(6_000_000_000);
    expect(secToTicks(-5)).toBe(0);
    expect(durationSecs(54_000_000_000)).toBe(5400);
    expect(durationSecs(null)).toBe(0);
  });
  it("le serveur prime ; vu en entier = du début ; serveur injoignable = local", () => {
    expect(resumeSeconds({ positionTicks: 6_000_000_000, played: false }, 42)).toBe(600);
    expect(resumeSeconds({ positionTicks: 0, played: true }, 42)).toBeUndefined();
    expect(resumeSeconds({ positionTicks: 0, played: false }, 42)).toBe(42);
    expect(resumeSeconds(null, 42)).toBe(42);
  });
  it("corps et chemins des rapports", () => {
    expect(reportBody("abc", 600, true)).toBe('{"ItemId":"abc","PositionTicks":6000000000,"IsPaused":true,"PlayMethod":"DirectPlay","CanSeek":true}');
    expect(reportPath("start")).toBe("/Sessions/Playing");
    expect(reportPath("progress")).toBe("/Sessions/Playing/Progress");
    expect(reportPath("stop")).toBe("/Sessions/Playing/Stopped");
  });
});

describe("fiches pour l'écran de détail", () => {
  it("film : résumé, genres, reprise du serveur", () => {
    const it = parseItems('{"Items":[{"Name":"F","Id":"a","Overview":"Résumé","Genres":["Drame"],"People":[{"Name":"Ada","Type":"Actor"},{"Name":"Bob","Type":"Director"}],"RunTimeTicks":54000000000,"ProductionYear":2019,"UserData":{"PlaybackPositionTicks":6000000000}}]}').items[0]!;
    expect(vodInfoOf(it)).toEqual({ info: { plot: "Résumé", genre: "Drame", cast: "Ada", director: "Bob", duration_secs: 5400, releasedate: "2019" }, resumePos: 600 });
    expect(vodInfoOf(parseItems('{"Items":[{"Name":"G","Id":"b"}]}').items[0]!).resumePos).toBe(0);
  });
  it("série : épisodes par saison + lien interne de chacun", () => {
    const eps = parseItems('{"Items":[{"Name":"Pilote","Id":"e1","Type":"Episode","ParentIndexNumber":1,"IndexNumber":1,"ImageTags":{"Primary":"t"},"MediaSources":[{"Id":"e1","Container":"mp4"}]},{"Name":"Sans index","Id":"e2","Type":"Episode"},{"Name":"S2","Id":"e3","ParentIndexNumber":2,"IndexNumber":4}]}').items;
    const r = seriesInfoOf(eps, "http://h");
    expect(Object.keys(r.episodes)).toEqual(["1", "2"]);
    expect(r.episodes["1"]!.map((e) => e.episode_num)).toEqual([1, 2]);   // sans index : position dans la saison
    expect(r.episodes["1"]![0]).toMatchObject({ title: "Pilote", container_extension: "mp4", info: { movie_image: "http://h/Items/e1/Images/Primary?maxWidth=640&quality=90&tag=t" } });
    expect(r.episodes["2"]![0]!.episode_num).toBe(4);
    expect(r.media).toEqual([{ id: numericId("e1"), url: "media:e1" }, { id: numericId("e2"), url: "media:e2" }, { id: numericId("e3"), url: "media:e3" }]);
  });
});
