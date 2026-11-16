import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { db } from "@/db/db";
import type { Source } from "@/db/types";
import { emptySource } from "@/db/sources";
import { parseHandoffHeader, sendHandoff, setCloudHttp, type HttpRequest } from "./client";
import { answerHandoff, handoffRefOf, HANDOFF_TTL_MS, noteHandoffHeader, resolveHandoff, useHandoff } from "./handoff";
import { useSources } from "@/state/sources";
import { usePlayer } from "@/player/store";

const enc = (o: unknown) => btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(o)))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const req = (over: Record<string, unknown> = {}) => ({ i: "r1", f: "Salon", s: "0a1b2c3d", t: "movie" as const, r: "42", o: 90_000, n: "Un film é", a: 1, ...over }) as import("./client").HandoffRequest;
const src = (over: Partial<Source> = {}): Source => ({ ...emptySource(), id: 7, cid: 3, cloudId: "0a1b2c3d", name: "S", ...over });

beforeEach(() => { useHandoff.setState({ pending: [] }); usePlayer.setState({ target: null, mode: "closed" }); });

describe("en-tête de reprise", () => {
  it("décode le JSON UTF-8 en base64url ; toute anomalie donne une liste vide", () => {
    expect(parseHandoffHeader(enc([req()]))[0]).toMatchObject({ i: "r1", f: "Salon", n: "Un film é", o: 90_000 });
    expect(parseHandoffHeader("")).toEqual([]);
    expect(parseHandoffHeader("%%%")).toEqual([]);
    expect(parseHandoffHeader(enc({ a: 1 }))).toEqual([]);
    expect(parseHandoffHeader(enc([req({ t: "stream" })]))).toEqual([]);
    expect(parseHandoffHeader(enc([1, 2, 3, 4, 5].map((k) => req({ i: `x${k}` })))).length).toBe(3);
  });
  it("une demande déjà traitée n'est pas reposée, une expirée disparaît", async () => {
    noteHandoffHeader(enc([req()]), 1000);
    expect(useHandoff.getState().pending.map((p) => p.i)).toEqual(["r1"]);
    noteHandoffHeader(enc([req()]), 2000);
    expect(useHandoff.getState().pending).toHaveLength(1);
    await answerHandoff("r1", false);
    expect(useHandoff.getState().pending).toHaveLength(0);
    noteHandoffHeader(enc([req()]), 3000); // « Non » : on ne repose pas la question
    expect(useHandoff.getState().pending).toHaveLength(0);
    noteHandoffHeader(enc([req({ i: "r2" })]), 10_000);
    noteHandoffHeader("", 10_000 + HANDOFF_TTL_MS + 1);
    expect(useHandoff.getState().pending).toHaveLength(0);
  });
});

describe("résolution dans le catalogue local", () => {
  it("film : retrouvé par identifiant dans SA source, reprise à la position", async () => {
    await db.movies.add({ sourceId: 3, catExt: "1", ord: 0, streamId: 42, name: "n", title: "Un film", norm: "un film", year: null, poster: null, rating: 0, ext: "mkv", added: 0 });
    const t = await resolveHandoff(req(), [src()]);
    expect(t).toMatchObject({ kind: "movie", sourceId: 7, refId: 42, startAt: 90, ext: "mkv" });
  });
  it("source du compte absente de cet appareil, ou titre retiré du catalogue : null", async () => {
    expect(await resolveHandoff(req(), [src({ cloudId: "ffffffff" })])).toBeNull();
    expect(await resolveHandoff(req({ r: "999" }), [src()])).toBeNull();
  });
  it("« Oui » lance la lecture, « Non » ne lance rien", async () => {
    await db.movies.add({ sourceId: 3, catExt: "1", ord: 1, streamId: 43, name: "n", title: "Deux", norm: "deux", year: null, poster: null, rating: 0, ext: "mp4", added: 0 });
    useSources.setState({ list: [src()] });
    noteHandoffHeader(enc([req({ i: "a", r: "43" })]));
    expect(usePlayer.getState().target).toBeNull(); // jamais de démarrage seul
    await answerHandoff("a", true);
    expect(usePlayer.getState().target?.refId).toBe(43);
  });
});

describe("envoi", () => {
  it("référence sans URL ni identifiants ; refus si la source n'est pas liée au compte", () => {
    const t = { kind: "episode" as const, sourceId: 7, cid: 3, refId: 55, title: "Série", subtitle: "S1 · E2", seriesId: 9, startAt: 0 };
    const ref = handoffRefOf({ ...t, url: "http://u:p@host/x.mp4" }, [src()], 61.5)!;
    expect(ref).toMatchObject({ src: "0a1b2c3d", t: "episode", id: "55", par: "9", pos: 61_500 });
    expect(JSON.stringify(ref)).not.toMatch(/http|host|:p@/);
    expect(handoffRefOf(t, [src({ cloudId: undefined })], 1)).toBeNull();
    expect(handoffRefOf({ ...t, kind: "live" }, [src()], 99)!.pos).toBe(0);
  });
  it("POST /api/device/handoff avec le jeton", async () => {
    const seen: HttpRequest[] = [];
    setCloudHttp(async (r) => { seen.push(r); return { status: 200, text: "{}", retryAfter: "", etag: "" }; });
    await sendHandoff("https://w.test", "tok", "dev1", { src: "0a1b2c3d", t: "live", id: "1", pos: 0, n: "TF1" });
    expect(seen[0]!.url).toBe("https://w.test/api/device/handoff");
    expect(seen[0]!.method).toBe("POST");
    expect(seen[0]!.headers!.authorization).toBe("Bearer tok");
    expect(JSON.parse(seen[0]!.body!).to).toBe("dev1");
    setCloudHttp(null);
  });
});
