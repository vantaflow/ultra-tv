"use strict";
const test = require("node:test");
const { PassThrough } = require("node:stream");
const assert = require("node:assert");
const { ffmpegCandidates, findFfmpeg, parseTranscodePath, transcodeArgs, parseStreams, subtitleArgs, serveStreamInfo, serveTranscode } = require("../transcode.cjs");

test("parseTranscodePath : jeton, cible et position", () => {
  assert.deepStrictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?ss=754.5"), { kind: "tx", token: "tok", b64: "aGVsbG8", ss: 754.5, live: false, a: 0, s: 0 });
  assert.deepStrictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?live=1"), { kind: "tx", token: "tok", b64: "aGVsbG8", ss: 0, live: true, a: 0, s: 0 });
  assert.strictEqual(parseTranscodePath("/tok/aGVsbG8", ""), null);
  assert.strictEqual(parseTranscodePath("/tok/tx/a/b", ""), null);
  assert.strictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?ss=-1;rm")?.ss, 0);
});

test("transcodeArgs : vidéo copiée, audio AAC, MPEG-TS, -ss avant -i", () => {
  const a = transcodeArgs("http://h/movie/u/p/1.mkv", { ss: 90, userAgent: "UA" });
  assert.ok(a.indexOf("-ss") < a.indexOf("-i"));
  assert.deepStrictEqual(a.slice(a.indexOf("-c:v"), a.indexOf("-c:v") + 6), ["-c:v", "copy", "-c:a", "aac", "-b:a", "192k"]);
  assert.strictEqual(a[a.indexOf("-f") + 1], "mpegts");
  assert.strictEqual(a[a.indexOf("-user_agent") + 1], "UA");
  assert.ok(!transcodeArgs("http://h/x.ts").includes("-ss"));
  assert.ok(!a.includes("-reconnect_streamed"));
  assert.ok(transcodeArgs("http://h/live/1.ts", { live: true }).includes("-reconnect_streamed"));
});

test("ffmpegCandidates : embarqué d'abord, Homebrew sur macOS", () => {
  const c = ffmpegCandidates({ resourcesPath: "/app/res", platform: "darwin", env: {} });
  // Chemin construit avec le séparateur du système qui lance les tests (\ sous Windows).
  assert.strictEqual(c[0], require("node:path").join("/app/res", "ffmpeg", "ffmpeg"));
  assert.ok(c.includes("/opt/homebrew/bin/ffmpeg"));
  assert.strictEqual(ffmpegCandidates({ platform: "win32", env: {} }).at(-1), "ffmpeg.exe");
});

test("findFfmpeg : premier candidat qui répond à -version", () => {
  const run = (bin) => ({ status: bin === "ffmpeg" ? 0 : 1 });
  assert.strictEqual(findFfmpeg({ platform: "linux", env: {} }, run), "ffmpeg");
  assert.strictEqual(findFfmpeg({ platform: "linux", env: {} }, () => ({ status: 1 })), null);
});

function fakeRes() {
  const r = new (require("node:events"))();
  r.headersSent = false; r.writableEnded = false; r.status = 0; r.chunks = [];
  r.writeHead = (s, h) => { r.status = s; r.headers = h; r.headersSent = true; };
  r.write = (c) => { r.chunks.push(c); };
  r.end = () => { r.writableEnded = true; };
  return r;
}
function fakeChild() {
  const c = new (require("node:events"))();
  c.stdout = new PassThrough(); c.stderr = new PassThrough(); c.killed = false;
  c.kill = () => { c.killed = true; };
  return c;
}

test("serveTranscode : relaie la sortie en video/mp2t et arrête ffmpeg à la fermeture du client", async () => {
  const child = fakeChild();
  const res = fakeRes();
  let args;
  serveTranscode({ headers: { "x-ut-ua": "UA" } }, res, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), ss: 0, headers: {}, spawnFn: (_b, a) => { args = a; return child; } });
  child.stdout.write(Buffer.from("TS"));
  await new Promise((r) => setImmediate(r));
  assert.strictEqual(res.status, 200);
  assert.strictEqual(res.headers["Content-Type"], "video/mp2t");
  assert.strictEqual(Buffer.concat(res.chunks).toString(), "TS");
  assert.strictEqual(args[args.indexOf("-user_agent") + 1], "UA");
  res.emit("close");
  assert.ok(child.killed);
});

test("serveTranscode : échec de ffmpeg sans sortie → 502", async () => {
  const child = fakeChild();
  const res = fakeRes();
  serveTranscode({ headers: {} }, res, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), ss: 0, headers: {}, spawnFn: () => child });
  child.emit("close", 1);
  assert.strictEqual(res.status, 502);
  assert.ok(res.writableEnded);
});

test("parseTranscodePath : routes pistes (txi) et sous-titres (txs), index bornés", () => {
  assert.strictEqual(parseTranscodePath("/tok/txi/aGVsbG8", "")?.kind, "info");
  assert.deepStrictEqual(parseTranscodePath("/tok/txs/aGVsbG8", "?s=3"), { kind: "sub", token: "tok", b64: "aGVsbG8", ss: 0, live: false, a: 0, s: 3 });
  assert.strictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?a=2")?.a, 2);
  assert.strictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?a=2;rm")?.a, 0);
  assert.strictEqual(parseTranscodePath("/tok/zz/aGVsbG8", ""), null);
});

test("transcodeArgs : piste audio choisie", () => {
  const a = transcodeArgs("http://h/1.mkv", { audio: 2 });
  assert.ok(a.includes("0:a:2?"));
  assert.ok(transcodeArgs("http://h/1.mkv").includes("0:a:0?"));
});

const FF_OUT = `Input #0, matroska,webm, from 'http://h/1.mkv':
  Stream #0:0: Video: h264 (High), yuv420p, 1920x1080
  Stream #0:1(fre): Audio: ac3, 48000 Hz, 5.1(side), fltp, 448 kb/s (default)
    Metadata:
      title           : VFF 5.1
  Stream #0:2(eng): Audio: eac3, 48000 Hz, 5.1(side), fltp
  Stream #0:3(fre): Subtitle: hdmv_pgs_subtitle
  Stream #0:4(eng): Subtitle: subrip (srt)
    Metadata:
      title           : English SDH
  Stream #0:5[0x100](und): Subtitle: ass (ssa)
At least one output file must be specified
`;

test("parseStreams : pistes audio, sous-titres texte, index dans le type", () => {
  const r = parseStreams(FF_OUT);
  assert.deepStrictEqual(r.audio.map((x) => [x.i, x.lang, x.codec, x.title]), [[0, "fre", "ac3", "VFF 5.1"], [1, "eng", "eac3", ""]]);
  // Le sous-titre image (PGS) compte dans l'index (0:s:0) mais n'est pas proposé.
  assert.deepStrictEqual(r.subs.map((x) => [x.i, x.lang, x.codec, x.title]), [[1, "eng", "subrip", "English SDH"], [2, "", "ass", ""]]);
});

test("subtitleArgs : WebVTT sur la sortie standard", () => {
  const a = subtitleArgs("http://h/1.mkv", { s: 1, userAgent: "UA" });
  assert.ok(a.includes("0:s:1"));
  assert.strictEqual(a[a.indexOf("-f") + 1], "webvtt");
  assert.strictEqual(a.at(-1), "pipe:1");
});

test("serveStreamInfo : JSON des pistes, 502 sinon", async () => {
  const res = fakeRes();
  await serveStreamInfo({ headers: {} }, res, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), headers: {}, probe: async () => ({ audio: [], subs: [] }) });
  assert.strictEqual(res.status, 200);
  const bad = fakeRes();
  await serveStreamInfo({ headers: {} }, bad, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), headers: {}, probe: async () => null });
  assert.strictEqual(bad.status, 502);
});
