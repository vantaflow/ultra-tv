"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const zlib = require("node:zlib");
const {
  encodeTarget,
  decodeTarget,
  buildProxyUrl,
  parseProxyPath,
  isSelfTarget,
  timeoutPolicy,
  isStreamOpen,
  isTrackedLive,
  rewriteManifest,
  looksLikeHlsManifest,
  createProxyServer,
} = require("../proxy.cjs");

const ORIGIN = "app://ultratv";

test("encodage/decodage d'URL : aller-retour et rejets", () => {
  const url = "http://panel.example:8080/live/u/p/1.m3u8?x=1&y=é";
  const seg = encodeTarget(url);
  assert.match(seg, /^[A-Za-z0-9_-]+$/);
  assert.equal(decodeTarget(seg).toString(), new URL(url).toString());
  assert.equal(decodeTarget(encodeTarget("file:///etc/passwd")), null);
  assert.equal(decodeTarget(encodeTarget("javascript:alert(1)")), null);
  assert.equal(decodeTarget("pas du base64!"), null);
  assert.equal(decodeTarget(""), null);
});

test("parseProxyPath : jeton invalide => 403, forme invalide => 404/400", () => {
  const seg = encodeTarget("http://a.example/x");
  assert.equal(parseProxyPath(`/tok/${seg}`, "tok").target.hostname, "a.example");
  assert.equal(parseProxyPath(`/bad/${seg}`, "tok").error, 403);
  assert.equal(parseProxyPath(`/tok`, "tok").error, 404);
  assert.equal(parseProxyPath(`/tok/${seg}/extra`, "tok").error, 404);
  assert.equal(parseProxyPath(`/tok/%%%`, "tok").error, 400);
});

test("isSelfTarget : refuse uniquement le port du proxy", () => {
  assert.equal(isSelfTarget(new URL("http://127.0.0.1:5555/x"), 5555), true);
  assert.equal(isSelfTarget(new URL("http://localhost:5555/x"), 5555), true);
  assert.equal(isSelfTarget(new URL("http://127.0.0.1:6000/x"), 5555), false);
  assert.equal(isSelfTarget(new URL("http://192.168.1.10:5555/x"), 5555), false);
});

test("rewriteManifest : URL relatives, absolues et URI=\"...\"", () => {
  const wrap = (u) => `P(${u})`;
  const src = [
    "#EXTM3U",
    '#EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x1',
    '#EXT-X-MAP:URI="init.mp4"',
    '#EXT-X-MEDIA:TYPE=AUDIO,URI="/audio/a.m3u8",NAME="x"',
    '#EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://abc"',
    "#EXTINF:4.0,",
    "seg1.ts",
    "http://cdn.example/abs/seg2.ts?t=1",
    "",
    "#EXT-X-ENDLIST",
  ].join("\n");
  const out = rewriteManifest(src, "https://h.example/live/dir/index.m3u8", wrap).split("\n");
  assert.equal(out[1], '#EXT-X-KEY:METHOD=AES-128,URI="P(https://h.example/live/dir/key.bin)",IV=0x1');
  assert.equal(out[2], '#EXT-X-MAP:URI="P(https://h.example/live/dir/init.mp4)"');
  assert.equal(out[3], '#EXT-X-MEDIA:TYPE=AUDIO,URI="P(https://h.example/audio/a.m3u8)",NAME="x"');
  assert.equal(out[4], '#EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://abc"');
  assert.equal(out[6], "P(https://h.example/live/dir/seg1.ts)");
  assert.equal(out[7], "P(http://cdn.example/abs/seg2.ts?t=1)");
  assert.equal(out[9], "#EXT-X-ENDLIST");
});

test("looksLikeHlsManifest distingue un manifeste d'une liste de chaines", () => {
  assert.equal(looksLikeHlsManifest("#EXTM3U\n#EXT-X-VERSION:3\n"), true);
  assert.equal(looksLikeHlsManifest("#EXTM3U\n#EXTINF:-1,Chaine\nhttp://x/1\n"), false);
});

// ---------------------------------------------------------------------------
// Serveur amont factice + proxy reel
// ---------------------------------------------------------------------------

async function withEnv(fn) {
  const seen = [];
  const upstream = http.createServer((req, res) => {
    seen.push({ method: req.method, url: req.url, headers: req.headers });
    const u = new URL(req.url, "http://x");
    if (u.pathname === "/live/index.m3u8") {
      res.writeHead(200, { "content-type": "application/vnd.apple.mpegurl" });
      return res.end("#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:4,\nseg1.ts\n");
    }
    if (u.pathname === "/gz/index.m3u8") {
      res.writeHead(200, { "content-type": "application/x-mpegURL", "content-encoding": "gzip" });
      return res.end(zlib.gzipSync("#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:4,\nseg1.ts\n"));
    }
    if (u.pathname === "/octet/manifest") {
      res.writeHead(200, { "content-type": "application/octet-stream" });
      return res.end("#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:4,\nseg9.ts\n");
    }
    if (u.pathname === "/playlist.m3u") {
      res.writeHead(200, { "content-type": "audio/x-mpegurl" });
      return res.end("#EXTM3U\n#EXTINF:-1,Chaine\nhttp://x/1\n");
    }
    if (u.pathname === "/redir") {
      res.writeHead(302, { location: "/live/index.m3u8" });
      return res.end();
    }
    if (u.pathname === "/seg.bin") {
      const data = Buffer.from("0123456789");
      const range = req.headers.range;
      if (range) {
        const m = /bytes=(\d+)-(\d+)?/.exec(range);
        const start = Number(m[1]);
        const end = m[2] ? Number(m[2]) : data.length - 1;
        res.writeHead(206, {
          "content-type": "video/mp2t",
          "content-range": `bytes ${start}-${end}/${data.length}`,
          "content-length": String(end - start + 1),
          "accept-ranges": "bytes",
        });
        return res.end(data.subarray(start, end + 1));
      }
      res.writeHead(200, { "content-type": "video/mp2t", "content-length": String(data.length) });
      return res.end(data);
    }
    if (u.pathname === "/echo") {
      const chunks = [];
      req.on("data", (c) => chunks.push(c));
      req.on("end", () => {
        res.writeHead(200, { "content-type": "text/plain" });
        res.end(Buffer.concat(chunks));
      });
      return;
    }
    if (u.pathname === "/hang") {
      res.writeHead(200, { "content-type": "video/mp2t" });
      res.write("x");
      req.on("close", () => seen.push({ closed: true }));
      return; // flux infini
    }
    res.writeHead(404);
    res.end();
  });
  await new Promise((r) => upstream.listen(0, "127.0.0.1", r));
  const up = `http://127.0.0.1:${upstream.address().port}`;
  const proxy = createProxyServer({ allowedOrigin: ORIGIN });
  await proxy.start();
  try {
    await fn({ up, proxy, seen, url: (target) => buildProxyUrl(`http://127.0.0.1:${proxy.port}/`, proxy.token, target) });
  } finally {
    await proxy.stop();
    upstream.closeAllConnections();
    await new Promise((r) => upstream.close(r));
  }
}

test("proxy : jeton invalide refuse, jeton valide relaie", async () => {
  await withEnv(async ({ up, proxy, url }) => {
    const bad = `http://127.0.0.1:${proxy.port}/nope/${encodeTarget(up + "/seg.bin")}`;
    assert.equal((await fetch(bad)).status, 403);
    const ok = await fetch(url(up + "/seg.bin"));
    assert.equal(ok.status, 200);
    assert.equal(await ok.text(), "0123456789");
    assert.equal(ok.headers.get("access-control-allow-origin"), ORIGIN);
    assert.match(ok.headers.get("access-control-expose-headers"), /Content-Range/);
  });
});

test("proxy : OPTIONS (preflight) et origine etrangere", async () => {
  await withEnv(async ({ up, url }) => {
    const pre = await fetch(url(up + "/seg.bin"), {
      method: "OPTIONS",
      headers: { origin: ORIGIN, "access-control-request-headers": "x-ut-ua,range" },
    });
    assert.equal(pre.status, 204);
    assert.equal(pre.headers.get("access-control-allow-origin"), ORIGIN);
    assert.equal(pre.headers.get("access-control-allow-headers"), "x-ut-ua,range");
    const evil = await fetch(url(up + "/seg.bin"), { headers: { origin: "https://evil.example" } });
    assert.equal(evil.status, 403);
  });
});

test("proxy : Range, X-UT-UA et X-UT-Referer transmis", async () => {
  await withEnv(async ({ up, url, seen }) => {
    const r = await fetch(url(up + "/seg.bin"), {
      headers: { range: "bytes=2-4", "x-ut-ua": "VLC/3.0", "x-ut-referer": "http://panel.example/" },
    });
    assert.equal(r.status, 206);
    assert.equal(await r.text(), "234");
    assert.equal(r.headers.get("content-range"), "bytes 2-4/10");
    const hit = seen.find((s) => s.url === "/seg.bin");
    assert.equal(hit.headers["user-agent"], "VLC/3.0");
    assert.equal(hit.headers["referer"], "http://panel.example/");
    assert.equal(hit.headers["x-ut-ua"], undefined);
  });
});

test("proxy : manifeste HLS reecrit (direct, gzip, octet-stream, apres redirection)", async () => {
  await withEnv(async ({ up, url, proxy }) => {
    const expected = (path) => buildProxyUrl(`http://127.0.0.1:${proxy.port}/`, proxy.token, `${up}${path}`);
    for (const [path, seg] of [
      ["/live/index.m3u8", "/live/seg1.ts"],
      ["/gz/index.m3u8", "/gz/seg1.ts"],
      ["/octet/manifest", "/octet/seg9.ts"],
      ["/redir", "/live/seg1.ts"],
    ]) {
      const r = await fetch(url(up + path));
      assert.equal(r.status, 200, path);
      assert.match(r.headers.get("content-type"), /mpegurl/i);
      assert.equal(r.headers.get("content-encoding"), null);
      const body = await r.text();
      assert.ok(body.includes(expected(seg)), `${path}: ${body}`);
    }
  });
});

test("proxy : une liste de chaines M3U n'est pas reecrite", async () => {
  await withEnv(async ({ up, url }) => {
    const body = await (await fetch(url(up + "/playlist.m3u"))).text();
    assert.ok(body.includes("http://x/1"));
  });
});

test("proxy : POST relaye le corps", async () => {
  await withEnv(async ({ up, url }) => {
    const r = await fetch(url(up + "/echo"), { method: "POST", body: "hello=1" });
    assert.equal(await r.text(), "hello=1");
  });
});

test("proxy : refuse sa propre adresse (boucle) et les schemas non http", async () => {
  await withEnv(async ({ proxy, url }) => {
    const self = await fetch(url(`http://127.0.0.1:${proxy.port}/x`));
    assert.equal(self.status, 400);
    const file = `http://127.0.0.1:${proxy.port}/${proxy.token}/${encodeTarget("file:///etc/hosts")}`;
    assert.equal((await fetch(file)).status, 400);
  });
});

test("proxy : fermeture du client => amont annule", async () => {
  await withEnv(async ({ up, url, seen }) => {
    const ctrl = new AbortController();
    const r = await fetch(url(up + "/hang"), { signal: ctrl.signal });
    assert.equal(r.status, 200);
    ctrl.abort();
    for (let i = 0; i < 40 && !seen.some((s) => s.closed); i++) await new Promise((r2) => setTimeout(r2, 50));
    assert.ok(seen.some((s) => s.closed), "la connexion amont devrait etre fermee");
  });
});

test("timeoutPolicy : en-têtes longs pour l'API/XMLTV, inactivité du corps seulement pour le direct", () => {
  const api = timeoutPolicy(new URL("http://h.example/player_api.php?username=a"));
  assert.ok(api.headersMs >= 120000 && api.headersMs <= 180000);
  assert.equal(api.bodyMs, 0);
  assert.ok(timeoutPolicy(new URL("http://h.example/xmltv.php")).headersMs >= 120000);
  const live = timeoutPolicy(new URL("http://h.example/live/u/p/1.ts"));
  assert.equal(live.headersMs, 30000);
  assert.equal(live.bodyMs, 30000);
  assert.equal(timeoutPolicy(new URL("http://h.example/index.m3u8")).bodyMs, 30000);
  const vod = timeoutPolicy(new URL("http://h.example/movie/u/p/9.mkv"));
  assert.equal(vod.headersMs, 30000);
  assert.equal(vod.bodyMs, 0);
});

test("isStreamOpen / isTrackedLive : ouvertures de flux, hors manifestes et VOD pour le suivi", () => {
  assert.equal(isStreamOpen(new URL("http://h/live/u/p/1.ts")), true);
  assert.equal(isStreamOpen(new URL("http://h/movie/u/p/1.mkv")), true);
  assert.equal(isStreamOpen(new URL("http://h/live/u/p/1.m3u8")), false);
  assert.equal(isStreamOpen(new URL("http://h/player_api.php")), false);
  assert.equal(isTrackedLive(new URL("http://h/live/u/p/1.ts")), true);
  assert.equal(isTrackedLive(new URL("http://h/movie/u/p/1.mkv")), false);
});

test("proxy : nouvelle ouverture de flux direct => ancien amont fermé avant la nouvelle requête", async () => {
  const events = [];
  const upstream = http.createServer((req, res) => {
    const id = req.url;
    events.push(`open:${id}`);
    res.writeHead(200, { "content-type": "video/mp2t" });
    res.write("x");
    req.on("close", () => events.push(`close:${id}`));
  });
  await new Promise((r) => upstream.listen(0, "127.0.0.1", r));
  const up = `http://127.0.0.1:${upstream.address().port}`;
  const proxy = createProxyServer({ allowedOrigin: ORIGIN });
  await proxy.start();
  try {
    const u = (t) => buildProxyUrl(`http://127.0.0.1:${proxy.port}/`, proxy.token, t);
    const a = await fetch(u(up + "/live/u/p/1.ts"));
    assert.equal(a.status, 200);
    const b = await fetch(u(up + "/live/u/p/2.ts"));
    assert.equal(b.status, 200);
    assert.ok(events.indexOf("close:/live/u/p/1.ts") !== -1, "ancien flux fermé");
    assert.ok(events.indexOf("close:/live/u/p/1.ts") < events.indexOf("open:/live/u/p/2.ts"), "fermeture avant ouverture du suivant");
    await b.body.cancel();
  } finally {
    await proxy.stop();
    upstream.closeAllConnections();
    await new Promise((r) => upstream.close(r));
  }
});
