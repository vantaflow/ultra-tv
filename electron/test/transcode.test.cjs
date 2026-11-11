"use strict";
const test = require("node:test");
const { PassThrough } = require("node:stream");
const assert = require("node:assert");
const { ffmpegCandidates, findFfmpeg, parseTranscodePath, transcodeArgs, serveTranscode } = require("../transcode.cjs");

test("parseTranscodePath : jeton, cible et position", () => {
  assert.deepStrictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?ss=754.5"), { token: "tok", b64: "aGVsbG8", ss: 754.5, live: false });
  assert.deepStrictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?live=1"), { token: "tok", b64: "aGVsbG8", ss: 0, live: true });
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
  assert.strictEqual(c[0], "/app/res/ffmpeg/ffmpeg");
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
