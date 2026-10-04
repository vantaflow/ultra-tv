"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const { cloudRequest, checkUrl } = require("../cloudfetch.cjs");

function server(handler) {
  return new Promise((resolve) => {
    const s = http.createServer(handler);
    s.listen(0, "127.0.0.1", () => resolve({ s, base: `http://127.0.0.1:${s.address().port}` }));
  });
}

test("checkUrl : https oui, http boucle locale seulement, pas d'identifiants dans l'URL", () => {
  assert.ok(checkUrl("https://w.example.workers.dev/api/config"));
  assert.ok(checkUrl("http://127.0.0.1:8787/x"));
  assert.equal(checkUrl("http://example.com/x"), null);
  assert.equal(checkUrl("https://user:pw@example.com/x"), null);
  assert.equal(checkUrl("file:///etc/passwd"), null);
  assert.equal(checkUrl("pas une url"), null);
});

test("cloudRequest : transmet Authorization, filtre les autres en-tetes, lit le corps", async () => {
  const seen = {};
  const { s, base } = await server((req, res) => {
    seen.auth = req.headers.authorization;
    seen.cookie = req.headers.cookie;
    seen.method = req.method;
    let b = "";
    req.on("data", (c) => (b += c));
    req.on("end", () => {
      seen.body = b;
      res.writeHead(200, { "content-type": "application/json", etag: '"v3"', "retry-after": "7" });
      res.end('{"ok":true}');
    });
  });
  try {
    const r = await cloudRequest(
      { url: base + "/p", method: "post", headers: { Authorization: "Bearer t", Cookie: "x=1", "Content-Type": "application/json" }, body: "{}" },
      fetch,
    );
    assert.equal(r.status, 200);
    assert.equal(r.text, '{"ok":true}');
    assert.equal(r.etag, '"v3"');
    assert.equal(r.retryAfter, "7");
    assert.equal(seen.auth, "Bearer t");
    assert.equal(seen.cookie, undefined);
    assert.equal(seen.method, "POST");
    assert.equal(seen.body, "{}");
  } finally {
    s.close();
  }
});

test("cloudRequest : ne suit jamais une redirection", async () => {
  const { s, base } = await server((req, res) => {
    res.writeHead(302, { location: "http://evil.example/steal" });
    res.end();
  });
  try {
    const r = await cloudRequest({ url: base + "/r" }, fetch);
    assert.ok(r.status === 302 || r.status === 0, `statut ${r.status}`);
  } finally {
    s.close();
  }
});

test("cloudRequest : refuse URL et methode invalides, corps trop gros", async () => {
  await assert.rejects(cloudRequest({ url: "http://example.com" }, fetch), /invalid-url/);
  await assert.rejects(cloudRequest({ url: "http://127.0.0.1:1", method: "TRACE" }, fetch), /invalid-method/);
  await assert.rejects(cloudRequest({ url: "http://127.0.0.1:1", method: "POST", body: "x".repeat(70000) }, fetch), /invalid-body/);
});
