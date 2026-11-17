import { describe, expect, it } from "vitest";
import { classifyFailure, ffmpegInstallCommand, planFallback } from "./failure";
import { parseTxDiag, txDiagUrl, txUrl, type TxDiag } from "./transcode";

const diag = (o: Partial<TxDiag>): TxDiag => ({ reason: "ok", status: null, codec: "", convert: false, canEncode: true, duration: 0, container: "", audio: "", ...o });

describe("classifyFailure : la vraie cause", () => {
  it("statuts du fournisseur : compte / connexions vs autre erreur HTTP", () => {
    for (const s of [401, 403, 429, 458, 509]) expect(classifyFailure({ status: s })).toEqual({ kind: "account", status: s });
    expect(classifyFailure({ status: 404 })).toEqual({ kind: "http", status: 404 });
    expect(classifyFailure({ status: 500 })).toEqual({ kind: "http", status: 500 });
  });
  it("le statut vu par ffmpeg compte aussi", () => {
    expect(classifyFailure({ diag: diag({ reason: "http", status: 403 }) })).toEqual({ kind: "account", status: 403 });
    expect(classifyFailure({ diag: diag({ reason: "http", status: 502 }) })).toEqual({ kind: "http", status: 502 });
  });
  it("codec non pris en charge : commande d'installation seulement sans conversion possible", () => {
    expect(classifyFailure({ diag: diag({ codec: "mpeg2video", convert: true, canEncode: false }), platform: "linux" }))
      .toEqual({ kind: "codec", codec: "mpeg2video", install: "sudo apt install ffmpeg  /  sudo dnf install ffmpeg" });
    expect(classifyFailure({ diag: diag({ codec: "hevc", convert: true, canEncode: true }) })).toEqual({ kind: "codec", codec: "hevc", install: null });
    expect(classifyFailure({ diag: diag({ codec: "h264", convert: false }) })).toEqual({ kind: "other" });
  });
  it("réseau, autre", () => {
    expect(classifyFailure({ diag: diag({ reason: "network" }) })).toEqual({ kind: "network" });
    expect(classifyFailure({ diag: diag({ reason: "other" }) })).toEqual({ kind: "other" });
    expect(classifyFailure({})).toEqual({ kind: "other" });
  });
  it("commandes d'installation par système", () => {
    expect(ffmpegInstallCommand("darwin")).toContain("brew");
    expect(ffmpegInstallCommand("linux")).toContain("apt");
    expect(ffmpegInstallCommand("linux")).toContain("dnf");
  });
});

describe("conversion vidéo : URL et diagnostic", () => {
  it("v=h264 seulement sur demande", () => {
    expect(txUrl("http://x/t/", "http://h/a.ts", 0, true, 0, true)).toBe(`${txUrl("http://x/t/", "http://h/a.ts", 0, true)}&v=h264`);
    expect(txUrl("http://x/t/", "http://h/a.ts", 0)).not.toContain("v=h264");
    expect(txDiagUrl("http://x/t/", "http://h/a.ts", true)).toMatch(/^http:\/\/x\/t\/txd\/.+\?hevc=1$/);
  });
  it("réponse /txd validée", () => {
    expect(parseTxDiag(null)).toBeNull();
    expect(parseTxDiag({ reason: "boum" })).toBeNull();
    expect(parseTxDiag({ reason: "http", status: 458 })).toEqual({ reason: "http", status: 458, codec: "", convert: false, canEncode: false, duration: 0, container: "", audio: "" });
    expect(parseTxDiag({ reason: "ok", video: { codec: "mpeg2video" }, convert: true, canEncode: true, duration: 5530.5 })).toEqual({ reason: "ok", status: null, codec: "mpeg2video", convert: true, canEncode: true, duration: 5530.5, container: "", audio: "" });
  });
});

describe("planFallback : remux, copie ou conversion", () => {
  it("vidéo illisible : conversion H.264 (ou installation de ffmpeg)", () => {
    expect(planFallback(diag({ codec: "hevc", convert: true }), { vod: true })).toBe("convert");
    expect(planFallback(diag({ codec: "mpeg2video", convert: true }), { vod: false })).toBe("convert");
    expect(planFallback(diag({ codec: "hevc", convert: true, canEncode: false }), { vod: true })).toBe("needEncoder");
  });
  it("codecs lisibles : film MKV / AC-3 remuxé (vidéo copiée), direct laissé à ses autres formats", () => {
    expect(planFallback(diag({ codec: "h264", convert: false }), { vod: true })).toBe("remux");
    expect(planFallback(diag({ codec: "h264", convert: false }), { vod: false })).toBeNull();
  });
  it("flux injoignable ou illisible : rien à tenter", () => {
    expect(planFallback(diag({ reason: "http", status: 403 }), { vod: true })).toBeNull();
    expect(planFallback(null, { vod: true })).toBeNull();
  });
});
