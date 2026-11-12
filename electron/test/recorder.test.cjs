"use strict";
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { PassThrough } = require("node:stream");
const { sanitizeFileName, uniquePath, isInside, isRecordingFile, validateJob, parseM3u8, pickVariant, createRecorder } = require("../recorder.cjs");

test("sanitizeFileName : caractères interdits, point final, noms réservés", () => {
  assert.strictEqual(sanitizeFileName('2026-11-12 20h45 - Stade 1 - A/B: "C"?'), "2026-11-12 20h45 - Stade 1 - A B C");
  assert.strictEqual(sanitizeFileName("Fin. "), "Fin");
  assert.strictEqual(sanitizeFileName("CON"), "_CON");
  assert.strictEqual(sanitizeFileName(""), "Ultra TV");
  assert.ok(sanitizeFileName("x".repeat(300)).length <= 120);
  assert.ok(!sanitizeFileName("..\\..\\evil").includes("\\"));
});

test("uniquePath : suffixe (2), (3)… si le fichier existe", () => {
  const taken = new Set([path.join("/d", "a.ts"), path.join("/d", "a (2).ts")]);
  assert.strictEqual(uniquePath("/d", "a", ".ts", (p) => taken.has(p)), path.join("/d", "a (3).ts"));
  assert.strictEqual(uniquePath("/d", "b", ".ts", () => false), path.join("/d", "b.ts"));
});

test("isInside / isRecordingFile : jamais hors du dossier ni autre chose qu'un .ts", () => {
  const dir = path.resolve("/videos/Ultra TV");
  assert.ok(isInside(dir, path.join(dir, "a.ts")));
  assert.ok(!isInside(dir, dir));
  assert.ok(!isInside(dir, path.join(dir, "..", "x.ts")));
  assert.ok(isRecordingFile([dir], path.join(dir, "a.ts")));
  assert.ok(!isRecordingFile([dir], path.join(dir, "a.exe")));
  assert.ok(!isRecordingFile([dir], path.resolve("/etc/passwd.ts")));
  assert.ok(!isRecordingFile([dir], 42));
});

test("validateJob : types et bornes, aucune URL hors http(s)", () => {
  const now = 1000;
  const ok = validateJob({ id: "1:2:3", url: "http://h/live/u/p/1.ts", endAt: 5000, name: "a/b", userAgent: "UA" }, now);
  assert.strictEqual(ok.job.name, "a b");
  assert.strictEqual(ok.job.userAgent, "UA");
  assert.strictEqual(validateJob({ id: "x", url: "file:///etc/passwd", endAt: 5000 }, now).error, "invalid-url");
  assert.strictEqual(validateJob({ id: "x y", url: "http://h", endAt: 5000 }, now).error, "invalid-id");
  assert.strictEqual(validateJob({ id: "x", url: "http://h", endAt: 10 }, now).error, "invalid-end");
  assert.strictEqual(validateJob({ id: "x", url: "http://h", endAt: 5000, userAgent: "a\r\nb" }, now).job.userAgent, null);
  assert.ok(validateJob({ id: "x", url: "http://h", endAt: now + 48 * 3600e3 }, now).job.endAt < now + 9 * 3600e3);
});

test("parseM3u8 : liste maîtresse puis liste média", () => {
  const master = parseM3u8("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=3000000\nhigh/index.m3u8\n", "http://h/live/1.m3u8");
  assert.ok(master.master);
  assert.strictEqual(pickVariant(master.variants).uri, "http://h/live/high/index.m3u8");
  const media = parseM3u8("#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:120\n#EXTINF:4,\ns120.ts\n#EXTINF:4,\nhttp://cdn/s121.ts\n", "http://h/live/high/index.m3u8");
  assert.ok(!media.master);
  assert.strictEqual(media.targetDuration, 4);
  assert.deepStrictEqual(media.segments, [{ seq: 120, uri: "http://h/live/high/s120.ts" }, { seq: 121, uri: "http://cdn/s121.ts" }]);
  assert.ok(!media.endList);
});

function fakeRes(status, headers, chunks, endIt = true) {
  const r = new PassThrough();
  r.statusCode = status;
  r.headers = headers;
  r.complete = true;
  setImmediate(() => { for (const c of chunks) r.write(c); if (endIt) r.end(); });
  return r;
}

test("enregistreur : copie le flux, rouvre après coupure, s'arrête à l'heure de fin", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "utv-rec-"));
  let t = 0;
  let calls = 0;
  const events = [];
  const done = new Promise((resolve) => {
    const rec = createRecorder({
      getDir: () => dir,
      now: () => t,
      retryDelayMs: 1,
      onEvent: (e) => { events.push(e); if (e.type !== "progress") resolve(e); },
      request: async () => {
        calls++;
        if (calls === 1) return fakeRes(302, { location: "http://h2/stream.ts" }, []);
        if (calls >= 4) { t = 10_000; return fakeRes(404, {}, []); } // fin du créneau après la reconnexion
        return fakeRes(200, { "content-type": "video/mp2t" }, [Buffer.from("abc")]);
      },
    });
    const r = rec.start({ id: "1:2:3", url: "http://h/live/u/p/1.ts", endAt: 5000, name: "Test" });
    assert.ok(r.ok);
    assert.ok(r.path.endsWith("Test.ts"));
    assert.ok(!JSON.stringify(rec.list()).includes("http"));
  });
  const ev = await done;
  assert.strictEqual(ev.type, "done");
  assert.strictEqual(fs.readFileSync(ev.path, "utf8"), "abcabc");
  assert.ok(!JSON.stringify(events).includes("http://"));
  fs.rmSync(dir, { recursive: true, force: true });
});

test("enregistreur : HLS, segments ajoutés dans l'ordre jusqu'à la fin de liste", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "utv-rec-"));
  const pages = {
    "http://h/live/1.m3u8": "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv.m3u8\n",
    "http://h/live/v.m3u8": "#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:5\n#EXTINF:2,\na.ts\n#EXTINF:2,\nb.ts\n#EXT-X-ENDLIST\n",
    "http://h/live/a.ts": "AA",
    "http://h/live/b.ts": "BB",
  };
  const ev = await new Promise((resolve) => {
    const rec = createRecorder({
      getDir: () => dir,
      now: () => 0,
      retryDelayMs: 1,
      onEvent: (e) => { if (e.type !== "progress") resolve(e); },
      request: async (u) => fakeRes(200, { "content-type": u.pathname.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t" }, [Buffer.from(pages[u.toString()])]),
    });
    // Fin de liste atteinte : la boucle principale rouvre l'adresse ; on arrête l'enregistrement après le premier passage.
    rec.start({ id: "hls", url: "http://h/live/1.m3u8", endAt: 60_000, name: "Hls" });
    setTimeout(() => rec.stop("hls"), 50);
  });
  assert.strictEqual(ev.type, "done");
  assert.ok(fs.readFileSync(ev.path, "utf8").startsWith("AABB"));
  fs.rmSync(dir, { recursive: true, force: true });
});

test("enregistreur : refus du fournisseur répété -> échec avec un code, fichier vide supprimé", async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "utv-rec-"));
  const ev = await new Promise((resolve) => {
    const rec = createRecorder({ getDir: () => dir, now: () => 0, retryDelayMs: 0, onEvent: (e) => { if (e.type !== "progress") resolve(e); }, request: async () => fakeRes(458, {}, []) });
    rec.start({ id: "x", url: "http://h/1.ts", endAt: 60_000, name: "Vide" });
  });
  assert.strictEqual(ev.type, "error");
  assert.strictEqual(ev.code, "HTTP_458");
  assert.deepStrictEqual(fs.readdirSync(dir), []);
  fs.rmSync(dir, { recursive: true, force: true });
});
