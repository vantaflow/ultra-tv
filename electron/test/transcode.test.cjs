"use strict";
const test = require("node:test");
const { PassThrough } = require("node:stream");
const assert = require("node:assert");
const { ffmpegCandidates, findFfmpeg, parseTranscodePath, transcodeArgs, parseStreams, subtitleArgs, firstPts, serveStreamInfo, serveSubtitle, serveTranscode } = require("../transcode.cjs");

test("parseTranscodePath : jeton, cible et position", () => {
  assert.deepStrictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?ss=754.5"), { kind: "tx", token: "tok", b64: "aGVsbG8", ss: 754.5, live: false, a: 0, s: 0, video: "copy", hevc: false });
  assert.deepStrictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?live=1"), { kind: "tx", token: "tok", b64: "aGVsbG8", ss: 0, live: true, a: 0, s: 0, video: "copy", hevc: false });
  assert.strictEqual(parseTranscodePath("/tok/aGVsbG8", ""), null);
  assert.strictEqual(parseTranscodePath("/tok/tx/a/b", ""), null);
  assert.strictEqual(parseTranscodePath("/tok/tx/aGVsbG8", "?ss=-1;rm")?.ss, 0);
  assert.strictEqual(parseTranscodePath("/tok/txb/aGVsbG8", "?ss=754.5")?.kind, "base");
  assert.strictEqual(parseTranscodePath("/tok/txb/aGVsbG8", "?ss=754.5")?.ss, 754.5);
});

test("transcodeArgs : vidéo copiée, audio AAC, MPEG-TS, -ss avant -i", () => {
  const a = transcodeArgs("http://h/movie/u/p/1.mkv", { ss: 90, userAgent: "UA" });
  assert.ok(a.indexOf("-ss") < a.indexOf("-i"));
  assert.ok(a.includes("-re"), "la conversion est servie au rythme réel de la lecture (jamais d'idle fournisseur)");
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

test("serveTranscode : onSpawn expose l'arrêt de ffmpeg (une conversion vivante par hôte côté proxy)", async () => {
  const child = fakeChild();
  const res = fakeRes();
  let stop = null;
  serveTranscode({ headers: {} }, res, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), ss: 0, headers: {}, spawnFn: () => child, onSpawn: (s) => { stop = s; } });
  assert.strictEqual(typeof stop, "function");
  stop();
  assert.ok(child.killed);
});

test("parseTranscodePath : routes pistes (txi) et sous-titres (txs), index bornés", () => {
  assert.strictEqual(parseTranscodePath("/tok/txi/aGVsbG8", "")?.kind, "info");
  assert.deepStrictEqual(parseTranscodePath("/tok/txs/aGVsbG8", "?s=3"), { kind: "sub", token: "tok", b64: "aGVsbG8", ss: 0, live: false, a: 0, s: 3, video: "copy", hevc: false });
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

test("subtitleArgs : extraction qui part de la position courante (ss avant -i)", () => {
  const a = subtitleArgs("http://h/1.mkv", { s: 1, ss: 1345.5 });
  assert.strictEqual(a[a.indexOf("-ss") + 1], "1345.5");
  assert.ok(a.indexOf("-ss") < a.indexOf("-i"), "-ss AVANT -i (recherche rapide côté source)");
  const b = subtitleArgs("http://h/1.mkv", { s: 0, ss: 0 });
  assert.ok(!b.includes("-ss"), "pas de -ss depuis le début");
  assert.ok(!a.includes("-re") && !b.includes("-re"), "pas de -re : l'extraction des sous-titres doit rester rapide");
});

test("serveStreamInfo : JSON des pistes, 502 sinon", async () => {
  const res = fakeRes();
  await serveStreamInfo({ headers: {} }, res, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), headers: {}, probe: async () => ({ audio: [], subs: [] }) });
  assert.strictEqual(res.status, 200);
  const bad = fakeRes();
  await serveStreamInfo({ headers: {} }, bad, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), headers: {}, probe: async () => null });
  assert.strictEqual(bad.status, 502);
});

// --- conversion vidéo et diagnostic ---

const { pickVideoEncoder, videoEncodeArgs, parseVideo, needsVideoConversion, classifyProbe, findVideoFfmpeg, diagnoseStream, serveDiagnosis } = require("../transcode.cjs");

const MPEG2 = "  Stream #0:0[0x100]: Video: mpeg2video (Main), yuv420p(tv, progressive), 720x576 [SAR 16:15 DAR 4:3], 25 fps\n  Stream #0:1[0x101]: Audio: mp2, 48000 Hz, stereo";
const HEVC = "  Stream #0:0: Video: hevc (Main 10), yuv420p10le(tv, bt2020nc), 3840x2160, 25 fps\n  Stream #0:1: Audio: eac3, 48000 Hz";
const H264 = "  Stream #0:0: Video: h264 (High), yuv420p(progressive), 1920x1080, 25 fps\n  Stream #0:1: Audio: aac, 48000 Hz";
const H264_10 = "  Stream #0:0: Video: h264 (High 10), yuv420p10le(progressive), 1920x1080, 25 fps";
const H264_422 = "  Stream #0:0: Video: h264 (High 4:2:2), yuv422p10le, 1280x720, 25 fps";

test("parseVideo : codec, profil, pixel format, taille ; ignore les jaquettes", () => {
  assert.deepStrictEqual(parseVideo(MPEG2), { codec: "mpeg2video", profile: "Main", pix: "yuv420p", w: 720, h: 576 });
  assert.deepStrictEqual(parseVideo(HEVC), { codec: "hevc", profile: "Main 10", pix: "yuv420p10le", w: 3840, h: 2160 });
  assert.strictEqual(parseVideo("  Stream #0:1: Video: mjpeg (Baseline), yuvj420p, 500x500 (attached pic)"), null);
  assert.strictEqual(parseVideo("rien"), null);
});

test("needsVideoConversion : lisible directement = jamais convertie", () => {
  const v = (s) => parseVideo(s);
  assert.strictEqual(needsVideoConversion(v(H264)), false);
  assert.strictEqual(needsVideoConversion({ codec: "vp9", profile: "", pix: "yuv420p" }), false);
  assert.strictEqual(needsVideoConversion(v(MPEG2)), true);
  assert.strictEqual(needsVideoConversion(v(HEVC), { hevc: false }), true);
  assert.strictEqual(needsVideoConversion(v(HEVC), { hevc: true }), false);
  assert.strictEqual(needsVideoConversion(v(H264_10)), true);
  assert.strictEqual(needsVideoConversion(v(H264_422)), true);
  assert.strictEqual(needsVideoConversion(null), false);
});

test("classifyProbe : codec, injoignable (4xx/5xx), réseau, autre", () => {
  assert.deepStrictEqual(classifyProbe(MPEG2), { reason: "ok", video: parseVideo(MPEG2), hasAudio: true, convert: true, duration: 0, container: "", audio: "mp2" });
  assert.strictEqual(classifyProbe(`Input #0, matroska,webm, from 'x':\n${H264}`).container, "matroska,webm");
  assert.strictEqual(classifyProbe(`  Duration: 01:32:10.50, start: 0.0, bitrate: 4000 kb/s\n${H264}`).duration, 5530.5);
  assert.strictEqual(classifyProbe(H264).convert, false);
  assert.deepStrictEqual(classifyProbe("[http @ 0x1] HTTP error 458 \nError opening input"), { reason: "http", status: 458 });
  assert.deepStrictEqual(classifyProbe("Server returned 403 Forbidden (access denied)"), { reason: "http", status: 403 });
  assert.deepStrictEqual(classifyProbe("Server returned 509 Bandwidth Limit Exceeded"), { reason: "http", status: 509 });
  assert.deepStrictEqual(classifyProbe("Server returned 4XX Client Error, but not one of 40{0,1,3,4}"), { reason: "http", status: null });
  assert.deepStrictEqual(classifyProbe("Connection refused"), { reason: "network" });
  assert.deepStrictEqual(classifyProbe("Invalid data found when processing input"), { reason: "other" });
  assert.deepStrictEqual(classifyProbe(null), { reason: "other" });
  // Un flux qui répond puis coupe avec un code ne passe pas pour une erreur HTTP s'il a des pistes.
  assert.strictEqual(classifyProbe(`${H264}\nServer returned 404 Not Found`).reason, "ok");
});

test("pickVideoEncoder : libx264 d'abord, encodeurs de plateforme selon le système", () => {
  const l = (...names) => names.map((n) => ` V....D ${n.padEnd(20)} description`).join("\n") + "\n A....D aac  AAC";
  assert.strictEqual(pickVideoEncoder(l("h264_mf", "libx264")), "libx264");
  assert.strictEqual(pickVideoEncoder(l("libopenh264"), "linux"), "libopenh264");
  assert.strictEqual(pickVideoEncoder(l("h264_mf"), "win32"), "h264_mf");
  assert.strictEqual(pickVideoEncoder(l("h264_mf"), "linux"), null);
  assert.strictEqual(pickVideoEncoder(l("h264_videotoolbox"), "darwin"), "h264_videotoolbox");
  assert.strictEqual(pickVideoEncoder(" V..... mpeg4 MPEG-4 part 2"), null);
});

test("transcodeArgs : vidéo copiée par défaut, convertie en H.264 sur demande", () => {
  const copy = transcodeArgs("http://h/a.ts", { live: true });
  assert.deepStrictEqual(copy.slice(copy.indexOf("-c:v"), copy.indexOf("-c:v") + 2), ["-c:v", "copy"]);
  assert.ok(!copy.includes("libx264") && !copy.includes("-vf"));

  const live = transcodeArgs("http://h/live/1.ts", { live: true, video: "h264" });
  assert.ok(!live.includes("copy") || live[live.indexOf("copy") - 1] !== "-c:v");
  assert.strictEqual(live[live.indexOf("-c:v") + 1], "libx264");
  assert.strictEqual(live[live.indexOf("-preset") + 1], "veryfast");
  assert.strictEqual(live[live.indexOf("-tune") + 1], "zerolatency");
  assert.strictEqual(live[live.indexOf("-pix_fmt") + 1], "yuv420p");
  assert.match(live[live.indexOf("-vf") + 1], /min\(1080,ih\)/);
  assert.ok(live.indexOf("-c:v") < live.indexOf("-c:a"), "audio AAC conservé");
  assert.strictEqual(live[live.indexOf("-c:a") + 1], "aac");

  const vod = transcodeArgs("http://h/m.mkv", { ss: 30, video: "h264" });
  assert.ok(!vod.includes("-tune"));
  assert.ok(vod.indexOf("-ss") < vod.indexOf("-i"));

  const mf = videoEncodeArgs("h264_mf");
  assert.ok(!mf.includes("-preset") && mf.includes("-b:v"));
});

test("findVideoFfmpeg : saute un ffmpeg sans encodeur H.264 (embarqué minimal) pour celui du système", () => {
  const run = (bin) => (bin === "ffmpeg" ? { status: 0, stdout: " V....D libx264  x\n" } : { status: 0, stdout: " A....D aac  x\n" });
  assert.deepStrictEqual(findVideoFfmpeg({ platform: "linux", env: {} }, run), { path: "ffmpeg", encoder: "libx264" });
  assert.strictEqual(findVideoFfmpeg({ platform: "linux", env: {} }, () => ({ status: 0, stdout: "" })), null);
});

test("diagnoseStream / serveDiagnosis : ne renvoie jamais l'URL", async () => {
  const spawnFn = () => { const c = fakeChild(); setImmediate(() => { c.stderr.write(`Input #0 http://u:p@h/x\n${MPEG2}`); c.emit("close", 1); }); return c; };
  const d = await diagnoseStream("ffmpeg", "http://u:p@h/x", { spawnFn });
  assert.strictEqual(d.reason, "ok");
  assert.strictEqual(d.convert, true);
  const res = fakeRes();
  let body = "";
  res.end = (b) => { body = b; };
  await serveDiagnosis({ headers: {} }, res, { ffmpeg: "ffmpeg", target: new URL("http://u:p@h/x"), headers: {}, canEncode: true, diagnose: async () => ({ reason: "http", status: 403 }) });
  assert.deepStrictEqual(JSON.parse(body), { reason: "http", status: 403, canEncode: true });
  assert.ok(!body.includes("u:p"));
});

test("serveSubtitle : relaie le WebVTT au fil de l'extraction (premiers octets écrits sans attendre la fin)", async () => {
  const child = fakeChild();
  const res = fakeRes();
  serveSubtitle({ headers: {} }, res, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), s: 1, headers: {}, spawnFn: () => child });
  child.stdout.write(Buffer.from("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nBonjour\n"));
  await new Promise((r) => setImmediate(r));
  assert.strictEqual(res.status, 200);
  assert.strictEqual(res.headers["Content-Type"], "text/vtt; charset=utf-8");
  assert.ok(Buffer.concat(res.chunks).toString().startsWith("WEBVTT"));
  child.stdout.write(Buffer.from("\n00:00:03.000 --> 00:00:04.000\nSuite\n"));
  await new Promise((r) => setImmediate(r));
  assert.ok(Buffer.concat(res.chunks).toString().includes("Suite"), "les morceaux suivants sont relayés");
  child.emit("close", 0);
  await new Promise((r) => setImmediate(r));
  assert.ok(res.writableEnded);
});

test("serveSubtitle : échec de ffmpeg sans sortie → 502", async () => {
  const child = fakeChild();
  const res = fakeRes();
  serveSubtitle({ headers: {} }, res, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), s: 0, headers: {}, spawnFn: () => child });
  child.emit("close", 1);
  await new Promise((r) => setImmediate(r));
  assert.strictEqual(res.status, 502);
  assert.ok(res.writableEnded);
});

test("transcodeArgs : horodatages conservés (-copyts, muxer MPEG-TS réglé)", () => {
  const a = transcodeArgs("http://h/movie/u/p/1.mkv", { ss: 90 });
  assert.ok(a.includes("-copyts"), "les temps d'entrée restent ceux du fichier");
  assert.ok(a.indexOf("-copyts") < a.indexOf("-i"));
  assert.strictEqual(a[a.indexOf("-mpegts_copyts") + 1], "1");
});

test("subtitleArgs : -copyts (repères en temps de contenu absolus)", () => {
  const a = subtitleArgs("http://h/1.mkv", { s: 1, ss: 1345.5 });
  assert.ok(a.includes("-copyts"));
  assert.ok(a.indexOf("-copyts") < a.indexOf("-i"));
});

test("firstPts : premier PTS d'un paquet TS, : base de la conversion", () => {
  const pts90k = 111111030; // 1234.567 s
  const p = Buffer.alloc(188);
  p[0] = 0x47;
  const pes = Buffer.alloc(19);
  pes[0] = 0; pes[1] = 0; pes[2] = 1; pes[3] = 0xe0; // PES vidéo
  pes[4] = 0; pes[5] = 13;
  pes[6] = 0x80;
  pes[7] = 0x80; // PTS_DTS_flags = « 10 »
  pes[8] = 5;
  const v = BigInt(pts90k);
  pes[9] = 0x20 | (Number((v >> 30n) & 0x7n) << 1) | 1;
  pes[10] = Number((v >> 22n) & 0xffn);
  pes[11] = (Number((v >> 15n) & 0x7fn) << 1) | 1;
  pes[12] = Number((v >> 7n) & 0xffn);
  pes[13] = (Number(v & 0x7fn) << 1) | 1;
  p.set(pes, 8);
  assert.ok(Math.abs(firstPts(p) - 1234.567) < 0.001);
  // PTS_DTS_flags = « 11 » (image avec B-frames) : la timeline du lecteur se cale sur le DTS.
  const dts90k = pts90k - 11250; // 0.125 s de délai de B-frames
  const d = BigInt(dts90k);
  const p2 = Buffer.alloc(188);
  p2[0] = 0x47;
  const pes2 = Buffer.alloc(24);
  pes2[0] = 0; pes2[1] = 0; pes2[2] = 1; pes2[3] = 0xe0;
  pes2[4] = 0; pes2[5] = 18;
  pes2[6] = 0x80;
  pes2[7] = 0xc0; // PTS_DTS_flags = « 11 »
  pes2[8] = 10;
  pes2[9] = 0x20 | (Number((v >> 30n) & 0x7n) << 1) | 1;
  pes2[10] = Number((v >> 22n) & 0xffn);
  pes2[11] = (Number((v >> 15n) & 0x7fn) << 1) | 1;
  pes2[12] = Number((v >> 7n) & 0xffn);
  pes2[13] = (Number(v & 0x7fn) << 1) | 1;
  pes2[14] = 0x10 | (Number((d >> 30n) & 0x7n) << 1) | 1;
  pes2[15] = Number((d >> 22n) & 0xffn);
  pes2[16] = (Number((d >> 15n) & 0x7fn) << 1) | 1;
  pes2[17] = Number((d >> 7n) & 0xffn);
  pes2[18] = (Number(d & 0x7fn) << 1) | 1;
  p2.set(pes2, 8);
  assert.ok(Math.abs(firstPts(p2) - (pts90k - 11250) / 90000) < 0.001, "image avec B-frames : DTS servi, pas le PTS");
  // Pas de PES, ou flux non aligné sur des paquets de 188 octets : null.
  assert.strictEqual(firstPts(Buffer.alloc(376, 0x42)), null);
  assert.strictEqual(firstPts(Buffer.from("zz")), null);
});

test("serveSubtitle : la base de la conversion est exposée en x-ut-base", async () => {
  const child = fakeChild();
  const res = fakeRes();
  serveSubtitle({ headers: {} }, res, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), s: 1, base: 754.5, headers: {}, spawnFn: () => child });
  child.stdout.write("WEBVTT\n\n");
  await new Promise((r) => setTimeout(r, 20));
  assert.strictEqual(res.headers["x-ut-base"], "754.5");
  // Sans conversion en cours : pas d'en-tête (le lecteur retombe sur le temps demandé).
  const res2 = fakeRes();
  const child2 = fakeChild();
  serveSubtitle({ headers: {} }, res2, { ffmpeg: "ffmpeg", target: new URL("http://h/1.mkv"), s: 1, headers: {}, spawnFn: () => child2 });
  child2.stdout.write("WEBVTT\n\n");
  await new Promise((r) => setTimeout(r, 20));
  assert.strictEqual(res2.headers["x-ut-base"], undefined);
});
