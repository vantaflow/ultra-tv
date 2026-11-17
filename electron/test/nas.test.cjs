"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const http = require("node:http");
const nas = require("../nas.cjs");
const { createProxyServer } = require("../proxy.cjs");

const ORIGIN = "app://ultratv";

function tmpTree(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "utv-nas-"));
  for (const [rel, content] of Object.entries(files)) {
    const abs = path.join(root, ...rel.split("/"));
    fs.mkdirSync(path.dirname(abs), { recursive: true });
    fs.writeFileSync(abs, content ?? "x");
  }
  return root;
}
const b64 = (s) => Buffer.from(s, "utf8").toString("base64url");

test("racines : chemins absolus, UNC et /Volumes acceptés ; relatifs et « .. » refusés", () => {
  assert.ok(nas.validRoot("/Volumes/video"));
  assert.ok(nas.validRoot("/mnt/nas/Films"));
  assert.ok(nas.validRoot("\\\\nas\\partage\\Films"));
  assert.ok(nas.validRoot("//nas/partage"));
  assert.ok(nas.validRoot("C:\\Videos"));
  assert.equal(nas.validRoot("Films"), null);
  assert.equal(nas.validRoot("../Films"), null);
  assert.equal(nas.validRoot("/mnt/../etc"), null);
  assert.equal(nas.validRoot(""), null);
  assert.equal(nas.validRoot("/a\0b"), null);
  assert.equal(nas.validRoot(42), null);
});

test("parcours : vidéos seulement, corbeilles / échantillons / extras ignorés, chemins relatifs avec « / »", async () => {
  const root = tmpTree({
    "Titre (2019).mkv": "12345",
    "notes.txt": "x", "cover.jpg": "x", "sample.mkv": "x",
    "Films/Autre.2020.mp4": "x", "Films/Autre.2020.srt": "x",
    "Films/Sous/Profond.avi": "x",
    "@eaDir/meta.mkv": "x", "#recycle/old.mkv": "x", "Extras/bonus.mkv": "x", ".cache/h.mkv": "x",
  });
  const { files, truncated } = await nas.scanRoot(root);
  assert.equal(truncated, false);
  assert.deepEqual(files.map((f) => f.path).sort(), ["Films/Autre.2020.mp4", "Films/Sous/Profond.avi", "Titre (2019).mkv"]);
  const titre = files.find((f) => f.path === "Titre (2019).mkv");
  assert.equal(titre.size, 5);
  assert.ok(titre.mtime > 1_500_000_000);
});

test("parcours : bornes de profondeur, de fichiers et de dossiers", async () => {
  const root = tmpTree({ "a/1.mkv": "x", "a/b/2.mkv": "x", "a/b/c/3.mkv": "x" });
  const d2 = await nas.scanRoot(root, { maxDepth: 2 });
  assert.deepEqual(d2.files.map((f) => f.path).sort(), ["a/1.mkv", "a/b/2.mkv"]);
  const many = tmpTree(Object.fromEntries(Array.from({ length: 30 }, (_, i) => [`v${i}.mkv`, "x"])));
  const f10 = await nas.scanRoot(many, { maxFiles: 10 });
  assert.equal(f10.files.length, 10);
  assert.equal(f10.truncated, true);
  const tr = await nas.scanRoot(root, { maxFolders: 1 });
  assert.equal(tr.truncated, true);
});

test("parcours : racine illisible => erreur ; dossier illisible sauté ; annulation", async () => {
  await assert.rejects(() => nas.scanRoot(path.join(os.tmpdir(), "utv-nas-inexistant-" + process.pid)));
  const root = tmpTree({ "ok/a.mkv": "x" });
  assert.equal((await nas.scanRoot(root, undefined, () => true)).files.length, 0);
});

test("segments : jamais de sortie de racine", () => {
  assert.deepEqual(nas.safeSegments("a/b/c.mkv"), ["a", "b", "c.mkv"]);
  assert.equal(nas.safeSegments("../x.mkv"), null);
  assert.equal(nas.safeSegments("a/../../x.mkv"), null);
  assert.equal(nas.safeSegments("a/./b.mkv"), null);
  assert.equal(nas.safeSegments("a\\b.mkv"), null);
  assert.equal(nas.safeSegments(""), null);
  assert.equal(nas.safeSegments("a\0b"), null);
  // « : » est un caractère valide sous macOS / Linux, interdit sous Windows (lecteur, flux alternatif).
  assert.deepEqual(nas.safeSegments("Titre: suite.mkv", "darwin"), ["Titre: suite.mkv"]);
  assert.equal(nas.safeSegments("C:/Windows/x.mkv", "win32"), null);
  assert.equal(nas.safeSegments("x.mkv:stream", "win32"), null);
});

test("registre : seuls les fichiers vidéo des racines déclarées sont résolus", () => {
  const root = tmpTree({ "Films/a.mkv": "x", "secret.txt": "x" });
  const reg = nas.createRegistry();
  assert.equal(reg.register("relatif"), null);
  const id = reg.register(root);
  assert.match(id, /^[0-9a-f]{12}$/);
  assert.equal(reg.register(root), id);   // stable
  assert.equal(reg.resolve(id, "Films/a.mkv"), path.join(root, "Films", "a.mkv"));
  assert.equal(reg.resolve(id, "secret.txt"), null);          // pas une vidéo
  assert.equal(reg.resolve(id, "../../etc/passwd.mkv"), null);  // sortie de racine
  assert.equal(reg.resolve("000000000000", "Films/a.mkv"), null);   // racine inconnue
});

test("plages : formes usuelles, hors fichier, absent", () => {
  assert.deepEqual(nas.parseRange(undefined, 100), { full: true });
  assert.deepEqual(nas.parseRange("items=0-5", 100), { full: true });
  assert.deepEqual(nas.parseRange("bytes=0-9", 100), { start: 0, end: 9 });
  assert.deepEqual(nas.parseRange("bytes=50-", 100), { start: 50, end: 99 });
  assert.deepEqual(nas.parseRange("bytes=-10", 100), { start: 90, end: 99 });
  assert.deepEqual(nas.parseRange("bytes=-500", 100), { start: 0, end: 99 });
  assert.deepEqual(nas.parseRange("bytes=10-5000", 100), { start: 10, end: 99 });
  assert.deepEqual(nas.parseRange("bytes=0-4,10-14", 100), { start: 0, end: 4 });
  assert.deepEqual(nas.parseRange("bytes=100-", 100), { unsatisfiable: true });
  assert.deepEqual(nas.parseRange("bytes=9-3", 100), { unsatisfiable: true });
  assert.deepEqual(nas.parseRange("bytes=0-5", 0), { unsatisfiable: true });
  assert.deepEqual(nas.parseRange("bytes=abc-", 100), { full: true });
});

test("chemin du proxy : forme stricte", () => {
  assert.deepEqual(nas.parseNasPath(`/tok/nas/0123456789ab/${b64("a/b.mkv")}.mkv`), { token: "tok", id: "0123456789ab", rel: "a/b.mkv" });
  assert.deepEqual(nas.parseNasPath(`/tok/nas/0123456789ab/${b64("é/é.mp4")}`), { token: "tok", id: "0123456789ab", rel: "é/é.mp4" });
  assert.equal(nas.parseNasPath("/tok/nas/zz/abc"), null);
  assert.equal(nas.parseNasPath("/tok/other/0123456789ab/abc"), null);
  assert.equal(nas.parseNasPath("/tok/nas/0123456789ab/***"), null);
  assert.equal(nas.parseNasPath("/tok/nas/0123456789ab"), null);
});

function request(url, { method = "GET", headers = {} } = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request(url, { method, headers }, (res) => {
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
    });
    req.on("error", reject);
    req.end();
  });
}

test("proxy : lecture d'un fichier du partage par plages, jeton et racines vérifiés", async () => {
  const payload = Buffer.alloc(300_000);
  for (let i = 0; i < payload.length; i++) payload[i] = i % 251;
  const root = tmpTree({ "Films/Titre (2019).mkv": payload, "Films/lisez-moi.txt": "secret" });
  const registry = nas.createRegistry();
  const id = registry.register(root);
  const proxy = createProxyServer({ allowedOrigin: ORIGIN, token: "tok", nas: registry });
  await proxy.start();
  const url = (rel, tok = "tok") => `${proxy.base.replace(/[^/]+\/$/, "")}${tok}/nas/${id}/${b64(rel)}.mkv`;
  try {
    const full = await request(url("Films/Titre (2019).mkv"));
    assert.equal(full.status, 200);
    assert.equal(full.headers["content-type"], "video/x-matroska");
    assert.equal(full.headers["accept-ranges"], "bytes");
    assert.equal(Number(full.headers["content-length"]), payload.length);
    assert.ok(full.body.equals(payload));

    const part = await request(url("Films/Titre (2019).mkv"), { headers: { Range: "bytes=100000-100999" } });
    assert.equal(part.status, 206);
    assert.equal(part.headers["content-range"], `bytes 100000-100999/${payload.length}`);
    assert.ok(part.body.equals(payload.subarray(100000, 101000)));

    const tail = await request(url("Films/Titre (2019).mkv"), { headers: { Range: "bytes=-100" } });
    assert.equal(tail.status, 206);
    assert.ok(tail.body.equals(payload.subarray(payload.length - 100)));

    const bad = await request(url("Films/Titre (2019).mkv"), { headers: { Range: "bytes=999999-" } });
    assert.equal(bad.status, 416);

    const head = await request(url("Films/Titre (2019).mkv"), { method: "HEAD" });
    assert.equal(head.status, 200);
    assert.equal(Number(head.headers["content-length"]), payload.length);
    assert.equal(head.body.length, 0);

    // Mauvais jeton, fichier non vidéo, sortie de racine, fichier absent, méthode refusée.
    assert.equal((await request(url("Films/Titre (2019).mkv", "mauvais"))).status, 403);
    assert.equal((await request(url("Films/lisez-moi.txt"))).status, 404);
    assert.equal((await request(url("../../etc/passwd.mkv"))).status, 404);
    assert.equal((await request(url("Films/absent.mkv"))).status, 404);
    assert.equal((await request(url("Films/Titre (2019).mkv"), { method: "DELETE" })).status, 405);
  } finally {
    await proxy.stop();
  }
});

test("proxy sans option nas : la route n'existe pas", async () => {
  const proxy = createProxyServer({ allowedOrigin: ORIGIN, token: "tok" });
  await proxy.start();
  try {
    const r = await request(`${proxy.base.replace(/[^/]+\/$/, "")}tok/nas/0123456789ab/${b64("a.mkv")}`);
    assert.ok(r.status === 404 || r.status === 400);
  } finally {
    await proxy.stop();
  }
});
