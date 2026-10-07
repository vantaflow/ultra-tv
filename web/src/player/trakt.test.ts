import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { traktScrobble, setCloudHttp, TokenRejectedError, RateLimitedError, type ScrobbleBody } from "@/cloud/client";
import { progressOf, Scrobbler, tmdbOf, type ScrobbleItem } from "./trakt";

const movie: ScrobbleItem = { kind: "movie", title: "Heat", year: 1995 };
const flush = () => new Promise((r) => setTimeout(r, 0));

function make(item: ScrobbleItem | null = movie, reply: { linked: boolean } | (() => Promise<{ linked: boolean }>) = { linked: true }) {
  const sent: ScrobbleBody[] = [];
  const sc = new Scrobbler(item, async (b) => { sent.push(b); return typeof reply === "function" ? reply() : reply; });
  return { sc, sent };
}

describe("progressOf / tmdbOf", () => {
  it("calcule la progression et refuse les durées inconnues", () => {
    expect(progressOf(50, 200)).toBe(25);
    expect(progressOf(300, 200)).toBe(100);
    expect(progressOf(10, NaN)).toBeNull();
    expect(progressOf(10, Infinity)).toBeNull();
    expect(progressOf(10, 0)).toBeNull();
  });
  it("lit un TMDB numérique ou en chaîne", () => {
    expect(tmdbOf("603")).toBe(603);
    expect(tmdbOf(603)).toBe(603);
    expect(tmdbOf("")).toBeUndefined();
    expect(tmdbOf("abc")).toBeUndefined();
    expect(tmdbOf(0)).toBeUndefined();
  });
});

describe("Scrobbler", () => {
  it("envoie start une seule fois (playing répété, buffering ignoré)", async () => {
    const { sc, sent } = make();
    sc.onState("playing", 0, 7200);
    sc.onState("buffering", 10, 7200);
    sc.onState("playing", 12, 7200);
    sc.onState("loading", 12, 7200);
    await flush();
    expect(sent.map((b) => b.action)).toEqual(["start"]);
    expect(sent[0]).toMatchObject({ kind: "movie", title: "Heat", year: 1995, progress: 0 });
  });
  it("pause puis reprise = start", async () => {
    const { sc, sent } = make();
    sc.onState("playing", 0, 100);
    sc.onState("paused", 40, 100);
    sc.onState("paused", 40, 100);
    sc.onState("playing", 40, 100);
    await flush();
    expect(sent.map((b) => [b.action, b.progress])).toEqual([["start", 0], ["pause", 40], ["start", 40]]);
  });
  it("stop avec la progression sur fin de lecture, puis plus rien", async () => {
    const { sc, sent } = make();
    sc.onState("playing", 0, 200);
    sc.onState("ended", 200, 200);
    sc.close(200, 200);
    sc.onState("playing", 0, 200);
    await flush();
    expect(sent.map((b) => [b.action, b.progress])).toEqual([["start", 0], ["stop", 100]]);
  });
  it("stop à la fermeture, avec repli sur la dernière position connue", async () => {
    const { sc, sent } = make();
    sc.onState("playing", 0, 100);
    sc.note(30, 100);
    sc.close(0, NaN);
    await flush();
    expect(sent.at(-1)).toMatchObject({ action: "stop", progress: 30 });
  });
  it("n'envoie pas de stop ni de pause sans start", async () => {
    const { sc, sent } = make();
    sc.onState("paused", 5, 100);
    sc.close(5, 100);
    await flush();
    expect(sent).toEqual([]);
  });
  it("ignore une durée inconnue ou infinie", async () => {
    const { sc, sent } = make();
    sc.onState("playing", 0, NaN);
    sc.onState("playing", 0, Infinity);
    await flush();
    expect(sent).toEqual([]);
  });
  it("média non scrobblable (item null, ex. direct) : rien n'est envoyé", async () => {
    const { sc, sent } = make(null);
    sc.onState("playing", 0, 100);
    sc.close(50, 100);
    await flush();
    expect(sent).toEqual([]);
  });
  it("linked:false coupe les envois pour le reste du média", async () => {
    const { sc, sent } = make(movie, { linked: false });
    sc.onState("playing", 0, 100);
    await flush();
    sc.onState("paused", 10, 100);
    sc.onState("playing", 10, 100);
    sc.close(20, 100);
    await flush();
    expect(sent.map((b) => b.action)).toEqual(["start"]);
  });
  it("un seul envoi en vol : les actions sont sérialisées dans l'ordre", async () => {
    let open = 0, max = 0;
    const order: string[] = [];
    const sc = new Scrobbler(movie, async (b) => { open++; max = Math.max(max, open); await flush(); order.push(b.action); open--; return { linked: true }; });
    sc.onState("playing", 0, 100);
    sc.onState("paused", 10, 100);
    sc.close(10, 100);
    await flush(); await flush(); await flush(); await flush();
    expect(max).toBe(1);
    expect(order).toEqual(["start", "pause", "stop"]);
  });
  it("les erreurs d'envoi sont avalées et la suite continue", async () => {
    const sent: string[] = [];
    let n = 0;
    const sc = new Scrobbler(movie, async (b) => { sent.push(b.action); if (n++ === 0) throw new Error("réseau"); return { linked: true }; });
    sc.onState("playing", 0, 100);
    sc.onState("paused", 10, 100);
    await flush();
    expect(sent).toEqual(["start", "pause"]);
  });
  it("épisode : transmet série, année, saison et épisode", async () => {
    const { sc, sent } = make({ kind: "episode", title: "Dark", year: 2017, tmdb: 70523, season: 2, episode: 3 });
    sc.onState("playing", 0, 3000);
    await flush();
    expect(sent[0]).toMatchObject({ kind: "episode", title: "Dark", tmdb: 70523, season: 2, episode: 3 });
  });
  it("accepte une identité résolue plus tard (promesse)", async () => {
    const sent: ScrobbleBody[] = [];
    const sc = new Scrobbler(new Promise<ScrobbleItem>((r) => setTimeout(() => r(movie), 5)), async (b) => { sent.push(b); });
    sc.onState("playing", 0, 100);
    await new Promise((r) => setTimeout(r, 20));
    expect(sent[0]?.title).toBe("Heat");
  });
});

describe("traktScrobble (client)", () => {
  let reqs: { url: string; method?: string; headers?: Record<string, string>; body?: string }[] = [];
  let reply = { status: 200, text: '{"linked":true,"matched":true,"ok":true}', retryAfter: "", etag: "" };
  beforeAll(() => setCloudHttp(async (r) => { reqs.push(r); return reply; }));
  afterAll(() => setCloudHttp(null));
  const body: ScrobbleBody = { action: "start", progress: 1.5, kind: "movie", title: "Heat", year: 1995 };

  it("POST authentifié avec le corps JSON", async () => {
    reqs = [];
    const r = await traktScrobble("https://w.example", "tok", body);
    expect(r).toEqual({ linked: true, matched: true, ok: true });
    expect(reqs[0]!.url).toBe("https://w.example/api/device/trakt/scrobble");
    expect(reqs[0]!.method).toBe("POST");
    expect(reqs[0]!.headers!.authorization).toBe("Bearer tok");
    expect(JSON.parse(reqs[0]!.body!)).toEqual(body);
  });
  it("linked:false, 401 et 429", async () => {
    reply = { status: 200, text: '{"linked":false}', retryAfter: "", etag: "" };
    expect(await traktScrobble("https://w.example", "t", body)).toMatchObject({ linked: false });
    reply = { status: 401, text: "", retryAfter: "", etag: "" };
    await expect(traktScrobble("https://w.example", "t", body)).rejects.toBeInstanceOf(TokenRejectedError);
    reply = { status: 429, text: "", retryAfter: "12", etag: "" };
    await expect(traktScrobble("https://w.example", "t", body)).rejects.toBeInstanceOf(RateLimitedError);
  });
});
