"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { resolveAppFile, mimeFor } = require("../appfiles.cjs");
const { createSecrets } = require("../secrets.cjs");

const root = fs.mkdtempSync(path.join(os.tmpdir(), "ut-root-"));
fs.mkdirSync(path.join(root, "assets"));
fs.writeFileSync(path.join(root, "index.html"), "<html></html>");
fs.writeFileSync(path.join(root, "assets", "app.js"), "1");
fs.writeFileSync(path.join(path.dirname(root), "secret.txt"), "nope");

test("resolveAppFile : fichiers, racine et fallback SPA", () => {
  assert.equal(resolveAppFile(root, "/"), path.join(root, "index.html"));
  assert.equal(resolveAppFile(root, "/assets/app.js"), path.join(root, "assets", "app.js"));
  assert.equal(resolveAppFile(root, "/channels/42"), path.join(root, "index.html"));
  assert.equal(resolveAppFile(root, "/assets/missing.js"), null);
});

test("resolveAppFile : refuse le path traversal", () => {
  for (const p of ["/../secret.txt", "/%2e%2e/secret.txt", "/assets/../../secret.txt", "/..%2fsecret.txt", "/a%5c..%5csecret.txt", "/x%00.js", "/%E0%A4%A"]) {
    assert.equal(resolveAppFile(root, p), null, p);
  }
});

test("mimeFor", () => {
  assert.equal(mimeFor("a.js"), "text/javascript; charset=utf-8");
  assert.equal(mimeFor("a.unknown"), "application/octet-stream");
});

test("secrets : chiffre via safeStorage, repli plain sinon", () => {
  const fake = {
    isEncryptionAvailable: () => true,
    encryptString: (s) => Buffer.from("X" + s),
    decryptString: (b) => b.toString().slice(1),
  };
  const s = createSecrets(fake);
  assert.equal(s.isSecure(), true);
  const c = s.encrypt("mot-de-passe");
  assert.match(c, /^enc:v1:/);
  assert.equal(s.decrypt(c), "mot-de-passe");

  const none = createSecrets({ isEncryptionAvailable: () => false });
  assert.equal(none.isSecure(), false);
  const p = none.encrypt("abc");
  assert.match(p, /^plain:v1:/);
  assert.equal(none.decrypt(p), "abc");
  assert.throws(() => none.decrypt("enc:v1:AAAA"));
  assert.throws(() => none.decrypt("zzz:v9:AAAA"));

  const basic = createSecrets({ ...fake, getSelectedStorageBackend: () => "basic_text" });
  assert.equal(basic.isSecure(), false);
});
