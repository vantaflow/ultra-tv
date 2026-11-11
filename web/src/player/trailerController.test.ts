import { describe, expect, it } from "vitest";
import {
  TRAILER_SKIP_S, clampSeek, createTrailerController, createYoutubePostMessagePlayer, mapYoutubeError, parseYoutubeMessage,
  progressRatio, seekFromRatio, trailerEmbedUrl, type TrailerPlayerLike,
} from "./trailerController";

/** Faux lecteur : enregistre les ordres reçus. */
function fakePlayer() {
  const calls: string[] = [];
  const p: TrailerPlayerLike = {
    play: () => calls.push("play"),
    pause: () => calls.push("pause"),
    seekTo: (s) => calls.push(`seek:${s}`),
  };
  return { p, calls };
}

describe("trailerEmbedUrl", () => {
  it("nos commandes, pas de vidéos liées, API JS active", () => {
    const u = new URL(trailerEmbedUrl("dQw4w9WgXcQ"));
    expect(u.origin).toBe("https://www.youtube-nocookie.com");
    expect(u.pathname).toBe("/embed/dQw4w9WgXcQ");
    for (const [k, v] of [["enablejsapi", "1"], ["controls", "0"], ["rel", "0"], ["playsinline", "1"], ["modestbranding", "1"], ["autoplay", "1"]] as const) expect(u.searchParams.get(k), k).toBe(v);
  });
  it("origin seulement pour une page http(s)", () => {
    expect(new URL(trailerEmbedUrl("dQw4w9WgXcQ", "https://exemple.test")).searchParams.get("origin")).toBe("https://exemple.test");
    expect(new URL(trailerEmbedUrl("dQw4w9WgXcQ", "app://ultratv")).searchParams.has("origin")).toBe(false);
    expect(new URL(trailerEmbedUrl("dQw4w9WgXcQ")).searchParams.has("origin")).toBe(false);
  });
});

describe("mapYoutubeError", () => {
  it("regroupe les codes de l'API", () => {
    expect(mapYoutubeError(100)).toBe("notFound");
    expect(mapYoutubeError(105)).toBe("notFound");
    expect(mapYoutubeError(101)).toBe("embedBlocked");
    expect(mapYoutubeError(150)).toBe("embedBlocked");
    expect(mapYoutubeError(152)).toBe("config");
    expect(mapYoutubeError(153)).toBe("config");
    expect(mapYoutubeError(2)).toBe("unknown");
    expect(mapYoutubeError(5)).toBe("unknown");
  });
});

describe("parseYoutubeMessage", () => {
  it("événements du lecteur", () => {
    expect(parseYoutubeMessage('{"event":"onReady"}')).toEqual([{ type: "ready" }]);
    expect(parseYoutubeMessage('{"event":"onStateChange","info":1}')).toEqual([{ type: "state", state: 1 }]);
    expect(parseYoutubeMessage('{"event":"onError","info":150}')).toEqual([{ type: "error", code: 150 }]);
    expect(parseYoutubeMessage({ event: "infoDelivery", info: { currentTime: 12.5, duration: 90, playerState: 2 } }))
      .toEqual([{ type: "time", position: 12.5, duration: 90, state: 2 }]);
  });
  it("tout le reste est ignoré", () => {
    for (const v of ["", "pas du json", "42", "null", '{"foo":1}', '{"event":"infoDelivery","info":{"volume":50}}', '{"event":"onError","info":"x"}', null, 3, {}]) expect(parseYoutubeMessage(v), String(v)).toEqual([]);
  });
});

describe("sauts bornés et progression", () => {
  it("clampSeek", () => {
    expect(clampSeek(30, TRAILER_SKIP_S, 120)).toBe(40);
    expect(clampSeek(5, -TRAILER_SKIP_S, 120)).toBe(0);
    expect(clampSeek(115, TRAILER_SKIP_S, 120)).toBe(120);
    expect(clampSeek(30, TRAILER_SKIP_S, 0)).toBe(40);   // durée inconnue : pas de borne haute
    expect(clampSeek(NaN, 10, 100)).toBe(10);
  });
  it("seekFromRatio et progressRatio", () => {
    expect(seekFromRatio(0.5, 80)).toBe(40);
    expect(seekFromRatio(2, 80)).toBe(80);
    expect(seekFromRatio(-1, 80)).toBe(0);
    expect(seekFromRatio(0.5, 0)).toBe(0);
    expect(progressRatio(20, 80)).toBe(0.25);
    expect(progressRatio(200, 80)).toBe(1);
    expect(progressRatio(5, 0)).toBe(0);
  });
});

describe("createTrailerController", () => {
  it("suit l'état, la position et la durée du lecteur", () => {
    const { p } = fakePlayer();
    const c = createTrailerController(p);
    expect(c.snapshot().phase).toBe("loading");
    c.handle({ type: "state", state: 1 });
    c.handle({ type: "time", position: 12, duration: 90 });
    expect(c.snapshot()).toEqual({ phase: "playing", position: 12, duration: 90, error: null });
    c.handle({ type: "state", state: 2 });
    expect(c.snapshot().phase).toBe("paused");
    c.handle({ type: "state", state: 3 });
    expect(c.snapshot().phase).toBe("buffering");
    c.handle({ type: "state", state: 0 });
    expect(c.snapshot().phase).toBe("ended");
  });
  it("une durée nulle n'écrase pas la durée connue", () => {
    const c = createTrailerController(fakePlayer().p);
    c.handle({ type: "time", duration: 90 });
    c.handle({ type: "time", position: 3, duration: 0 });
    expect(c.snapshot().duration).toBe(90);
  });
  it("lecture / pause selon l'état", () => {
    const { p, calls } = fakePlayer();
    const c = createTrailerController(p);
    c.toggle();                              // encore en chargement : on demande la lecture
    c.handle({ type: "state", state: 1 });
    c.toggle();
    c.handle({ type: "state", state: 2 });
    c.toggle();
    expect(calls).toEqual(["play", "pause", "play"]);
  });
  it("fin de lecture : relecture depuis le début", () => {
    const { p, calls } = fakePlayer();
    const c = createTrailerController(p);
    c.handle({ type: "state", state: 0 });
    c.toggle();
    expect(calls).toEqual(["seek:0", "play"]);
  });
  it("−10 s / +10 s bornés, position mise à jour tout de suite", () => {
    const { p, calls } = fakePlayer();
    const c = createTrailerController(p);
    c.handle({ type: "time", position: 5, duration: 100 });
    c.skip(-10);
    expect(c.snapshot().position).toBe(0);
    c.skip(10); c.skip(10);
    expect(c.snapshot().position).toBe(20);
    c.handle({ type: "time", position: 95 });
    c.skip(10);
    expect(c.snapshot().position).toBe(100);
    expect(calls).toEqual(["seek:0", "seek:10", "seek:20", "seek:100"]);
  });
  it("clic sur la barre : sans durée connue, rien", () => {
    const { p, calls } = fakePlayer();
    const c = createTrailerController(p);
    c.seekRatio(0.5);
    expect(calls).toEqual([]);
    c.handle({ type: "time", duration: 60 });
    c.seekRatio(0.5);
    expect(calls).toEqual(["seek:30"]);
    expect(c.snapshot().position).toBe(30);
  });
  it("erreur : message de repli, plus aucune commande, plus de changement d'état", () => {
    const { p, calls } = fakePlayer();
    const c = createTrailerController(p);
    c.handle({ type: "error", code: 101 });
    expect(c.snapshot()).toMatchObject({ phase: "error", error: "embedBlocked" });
    c.toggle(); c.skip(10); c.seekRatio(0.5);
    c.handle({ type: "state", state: 1 });
    expect(calls).toEqual([]);
    expect(c.snapshot().phase).toBe("error");
  });
  it("les abonnés ne sont prévenus que d'un vrai changement", () => {
    const c = createTrailerController(fakePlayer().p);
    let n = 0;
    const off = c.subscribe(() => { n++; });
    c.handle({ type: "state", state: 1 });
    c.handle({ type: "state", state: 1 });
    expect(n).toBe(1);
    off();
    c.handle({ type: "state", state: 2 });
    expect(n).toBe(1);
  });
});

describe("createYoutubePostMessagePlayer", () => {
  it("envoie les ordres au format de l'API, vers l'origine du lecteur", () => {
    const sent: Array<[string, string]> = [];
    const pl = createYoutubePostMessagePlayer({ postMessage: (m, t) => sent.push([m, t]) });
    pl.play(); pl.pause(); pl.seekTo(42);
    expect(sent.map(([m]) => JSON.parse(m))).toEqual([
      { event: "command", func: "playVideo", args: [] },
      { event: "command", func: "pauseVideo", args: [] },
      { event: "command", func: "seekTo", args: [42, true] },
    ]);
    expect(new Set(sent.map(([, t]) => t))).toEqual(new Set(["https://www.youtube-nocookie.com"]));
  });
});
