"use strict";
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { maskSecrets, formatLine, createLogger } = require("../logger.cjs");

test("maskSecrets : identifiants Xtream, paramètres, userinfo, proxy local", () => {
  const cases = [
    ["http://h:8080/live/jean/MotDePasse1/123.ts", "jean", "MotDePasse1"],
    ["http://h/movie/jean/MotDePasse1/9.mkv?x=1", "jean", "MotDePasse1"],
    ["http://h/series/jean/MotDePasse1/9.mp4", "jean", "MotDePasse1"],
    ["http://h:8080/jean/MotDePasse1/123", "jean", "MotDePasse1"],
    ["http://h/get.php?username=jean&password=MotDePasse1&type=m3u", "jean", "MotDePasse1"],
    ["http://h/player_api.php?action=x&token=abcDEF123&password=MotDePasse1", "abcDEF123", "MotDePasse1"],
    ["https://jean:MotDePasse1@h/x.m3u8", "jean", "MotDePasse1"],
    ["http://127.0.0.1:5555/TOKENPROXYSECRET/aHR0cDovL2gvbGl2ZS9qZWFuL01vdERlUGFzc2UvMS50cw", "TOKENPROXYSECRET", "aHR0cDovL2g"],
    ["http://127.0.0.1:5555/TOKENPROXYSECRET/tx/aHR0cDovL2gvbGl2ZS9qZWFuLzEudHM?ss=3", "TOKENPROXYSECRET", "aHR0cDovL2g"],
    ["erreur sur /txd/aHR0cDovL2gvbGl2ZS9qZWFuLzEudHM fin", "aHR0cDovL2g", "aHR0cDovL2g"],
  ];
  for (const [input, a, b] of cases) {
    const out = maskSecrets(input);
    assert.ok(!out.includes(a), `${a} dans ${out}`);
    assert.ok(!out.includes(b), `${b} dans ${out}`);
  }
  assert.strictEqual(maskSecrets("http://h:8080/live/jean/Pass/123.ts"), "http://h:8080/live/***/***/123.ts");
  // L'hôte et l'extension restent lisibles (utiles au diagnostic).
  assert.match(maskSecrets("http://fournisseur.tld/movie/u/p/5.mkv"), /fournisseur\.tld\/movie\/\*\*\*\/\*\*\*\/5\.mkv/);
});

test("formatLine : champs imbriqués masqués, une ligne JSON", () => {
  const line = formatLine("play.error", { url: "http://h/live/u/pw/1.ts", nested: { u: "http://h/x?password=zz" }, n: 3, ok: true, "bad key!": "v\nw" }, new Date(0));
  assert.ok(!line.includes("\n"));
  assert.ok(!/pw|zz/.test(line.replace("password", "")));
  const o = JSON.parse(line);
  assert.strictEqual(o.t, "1970-01-01T00:00:00.000Z");
  assert.strictEqual(o.event, "play.error");
  assert.strictEqual(o.n, 3);
  assert.strictEqual(o.bad_key_, "v w");
});

test("createLogger : rotation à la taille limite, 3 fichiers au plus", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "ut-log-"));
  try {
    const lg = createLogger({ dir, maxBytes: 300, files: 3 });
    for (let i = 0; i < 60; i++) lg.log("e", { i, pad: "x".repeat(40) });
    const names = fs.readdirSync(dir).sort();
    assert.deepStrictEqual(names, ["playback.1.log", "playback.2.log", "playback.log"]);
    for (const n of names) assert.ok(fs.statSync(path.join(dir, n)).size <= 300);
    assert.match(fs.readFileSync(path.join(dir, "playback.log"), "utf8"), /"i":59/);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test("createLogger : une erreur d'écriture ne remonte jamais", () => {
  const lg = createLogger({ dir: "/nonexistent-root-zz/x", fsImpl: { ...fs, mkdirSync() { throw new Error("no"); } } });
  assert.doesNotThrow(() => lg.log("e", {}));
});
