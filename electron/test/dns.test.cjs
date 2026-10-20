"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const dgram = require("node:dgram");
const {
  normalizeSettings,
  validDohUrl,
  effectiveResolver,
  chromiumResolverConfig,
  buildQuery,
  parseResponse,
  dohQuery,
  resolvePlain,
  createResolver,
} = require("../dns.cjs");

// ---------------------------------------------------------------------------
// Serveur DNS factice : construit une réponse binaire (A / AAAA) à une requête donnée.
// ---------------------------------------------------------------------------

function answerFor(query, records, { rcode = 0 } = {}) {
  // Question recopiée telle quelle, puis réponses pointant sur le nom (compression 0xC00C).
  let off = 12;
  while (query[off] !== 0) off += 1 + query[off];
  const qEnd = off + 5;
  const qtype = query.readUInt16BE(off + 1);
  const rrs = records.filter((r) => r.type === qtype);
  const header = Buffer.from(query.subarray(0, 12));
  header[2] = 0x81;
  header[3] = 0x80 | rcode;
  header.writeUInt16BE(rcode ? 0 : rrs.length, 6);
  const parts = [header, query.subarray(12, qEnd)];
  if (!rcode) {
    for (const r of rrs) {
      const data = r.type === 1 ? Buffer.from(r.address.split(".").map(Number)) : ipv6Bytes(r.address);
      const rr = Buffer.alloc(12);
      rr.writeUInt16BE(0xc00c, 0);
      rr.writeUInt16BE(r.type, 2);
      rr.writeUInt16BE(1, 4);
      rr.writeUInt32BE(r.ttl ?? 300, 6);
      rr.writeUInt16BE(data.length, 10);
      parts.push(rr, data);
    }
  }
  return Buffer.concat(parts);
}

function ipv6Bytes(ip) {
  const [head, tail = ""] = ip.split("::");
  const h = head ? head.split(":") : [];
  const t = tail ? tail.split(":") : [];
  const groups = [...h, ...Array(8 - h.length - t.length).fill("0"), ...t];
  const b = Buffer.alloc(16);
  groups.forEach((g, i) => b.writeUInt16BE(parseInt(g, 16), i * 2));
  return b;
}

function fakeDohServer(handler) {
  const server = http.createServer((req, res) => {
    const u = new URL(req.url, "http://x");
    const q = Buffer.from(u.searchParams.get("dns") || "", "base64url");
    handler(q, req, res);
  });
  return new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve({ server, url: `http://127.0.0.1:${server.address().port}/dns-query` })));
}

const sysFake = (list) => (host, opts, cb) => cb(null, list);
const settingsDoh = { mode: "doh", dohPreset: "cloudflare" };

// ---------------------------------------------------------------------------

test("normalizeSettings : tolérant, valeurs inconnues => Système", () => {
  assert.deepEqual(normalizeSettings(null), { mode: "system", dohPreset: "cloudflare", dohUrl: "", primary: "", secondary: "" });
  assert.equal(normalizeSettings({ mode: "GOOGLE" }).mode, "google");
  assert.equal(normalizeSettings({ mode: "evil" }).mode, "system");
  assert.equal(normalizeSettings({ mode: "doh", dohPreset: "x" }).dohPreset, "cloudflare");
});

test("validDohUrl : https uniquement, sans identifiants", () => {
  assert.equal(validDohUrl("https://dns.example/dns-query"), "https://dns.example/dns-query");
  assert.equal(validDohUrl("http://dns.example/dns-query"), null);
  assert.equal(validDohUrl("https://u:p@dns.example/"), null);
  assert.equal(validDohUrl("pas une url"), null);
});

test("effectiveResolver : sélection selon le mode", () => {
  assert.deepEqual(effectiveResolver({ mode: "system" }), { kind: "system" });
  assert.deepEqual(effectiveResolver({ mode: "google" }), { kind: "plain", servers: ["8.8.8.8", "8.8.4.4"] });
  assert.deepEqual(effectiveResolver({ mode: "custom", primary: "10.0.0.1", secondary: "bad" }), { kind: "plain", servers: ["10.0.0.1"] });
  assert.deepEqual(effectiveResolver({ mode: "custom", primary: "", secondary: "" }), { kind: "system" });
  assert.equal(effectiveResolver({ mode: "doh", dohPreset: "quad9" }).url, "https://dns.quad9.net/dns-query");
  assert.deepEqual(effectiveResolver({ mode: "doh", dohPreset: "custom", dohUrl: "http://x/" }), { kind: "system" });
  assert.equal(effectiveResolver({ mode: "doh", dohPreset: "custom", dohUrl: "https://x.example/q" }).url, "https://x.example/q");
});

test("chromiumResolverConfig : DoH pour les préréglages, IP inconnues => non appliqué", () => {
  assert.deepEqual(chromiumResolverConfig({ mode: "system" }), { secureDnsMode: "automatic", secureDnsServers: [], appliesToChromium: true });
  assert.deepEqual(chromiumResolverConfig({ mode: "google" }).secureDnsServers, ["https://dns.google/dns-query"]);
  assert.deepEqual(chromiumResolverConfig({ mode: "doh", dohPreset: "custom", dohUrl: "https://x.example/q" }).secureDnsServers, ["https://x.example/q"]);
  const known = chromiumResolverConfig({ mode: "custom", primary: "1.1.1.1" });
  assert.deepEqual(known.secureDnsServers, ["https://cloudflare-dns.com/dns-query"]);
  assert.equal(known.appliesToChromium, true);
  const unknown = chromiumResolverConfig({ mode: "custom", primary: "192.168.1.1" });
  assert.deepEqual(unknown.secureDnsServers, []);
  assert.equal(unknown.appliesToChromium, false);
});

test("buildQuery / parseResponse : A, AAAA, TTL minimal, NXDOMAIN", () => {
  const q = buildQuery("panel.example.com", 1);
  assert.equal(q.readUInt16BE(4), 1);
  const res = parseResponse(answerFor(q, [{ type: 1, address: "203.0.113.5", ttl: 120 }, { type: 1, address: "203.0.113.6", ttl: 30 }]));
  assert.deepEqual(res.addresses.map((a) => a.address), ["203.0.113.5", "203.0.113.6"]);
  assert.equal(res.ttl, 30);
  const q6 = buildQuery("panel.example.com", 28);
  assert.equal(parseResponse(answerFor(q6, [{ type: 28, address: "2001:db8::1" }])).addresses[0].address, "2001:db8::1");
  assert.throws(() => parseResponse(answerFor(q, [], { rcode: 3 })), /rcode-3/);
  assert.throws(() => parseResponse(Buffer.from([1, 2, 3])), /truncated/);
});

test("dohQuery : serveur DoH local factice (GET ?dns=, accept dns-message)", async () => {
  let accept = "";
  const { server, url } = await fakeDohServer((q, req, res) => {
    accept = req.headers.accept;
    res.writeHead(200, { "content-type": "application/dns-message" });
    res.end(answerFor(q, [{ type: 1, address: "198.51.100.7", ttl: 90 }]));
  });
  try {
    const r = await dohQuery(url, "iptv.example.net", 1);
    assert.equal(accept, "application/dns-message");
    assert.deepEqual(r.addresses, [{ address: "198.51.100.7", family: 4 }]);
    assert.equal(r.ttl, 90);
  } finally {
    server.close();
  }
});

test("dohQuery : adresse d'amorçage utilisée pour joindre le serveur DoH", async () => {
  const { server, url } = await fakeDohServer((q, _req, res) => res.end(answerFor(q, [{ type: 1, address: "198.51.100.8" }])));
  try {
    // Nom inexistant : seule l'adresse d'amorçage permet de joindre le serveur.
    const port = new URL(url).port;
    const r = await dohQuery(`http://doh.invalid:${port}/dns-query`, "a.example", 1, { bootstrap: ["127.0.0.1"] });
    assert.equal(r.addresses[0].address, "198.51.100.8");
  } finally {
    server.close();
  }
});

test("resolvePlain : serveur DNS UDP local factice", async () => {
  const sock = dgram.createSocket("udp4");
  sock.on("message", (msg, rinfo) => {
    sock.send(answerFor(msg, [{ type: 1, address: "192.0.2.44", ttl: 500 }]), rinfo.port, rinfo.address);
  });
  await new Promise((r) => sock.bind(0, "127.0.0.1", r));
  try {
    const r = await resolvePlain([`127.0.0.1:${sock.address().port}`], "plain.example");
    assert.deepEqual(r.addresses, [{ address: "192.0.2.44", family: 4 }]);
    assert.equal(r.ttl, 500);
  } finally {
    sock.close();
  }
});

test("createResolver : mode Système => dns.lookup du système, résolveur personnalisé jamais appelé", async () => {
  let called = 0;
  const r = createResolver({ getSettings: () => ({ mode: "system" }), resolve: () => { called++; }, systemLookup: sysFake([{ address: "10.1.1.1", family: 4 }]) });
  const out = await r.resolveHost("a.example");
  assert.equal(out.via, "system");
  assert.equal(out.addresses[0].address, "10.1.1.1");
  assert.equal(called, 0);
});

test("createResolver : résolution personnalisée puis cache (TTL minimal 60 s)", async () => {
  let t = 1_000_000;
  let calls = 0;
  const r = createResolver({
    getSettings: () => settingsDoh,
    resolve: async () => { calls++; return { addresses: [{ address: "203.0.113.9", family: 4 }], ttl: 5 }; },
    systemLookup: sysFake([{ address: "10.0.0.9", family: 4 }]),
    now: () => t,
  });
  assert.equal((await r.resolveHost("x.example")).via, "custom");
  t += 30_000; // TTL 5 s relevé à 60 s : toujours en cache
  assert.equal((await r.resolveHost("x.example")).via, "cache");
  assert.equal(calls, 1);
  t += 31_000;
  assert.equal((await r.resolveHost("x.example")).via, "custom");
  assert.equal(calls, 2);
  r.clearCache();
  await r.resolveHost("x.example");
  assert.equal(calls, 3);
});

test("createResolver : changer de serveur n'utilise pas le cache de l'ancien", async () => {
  let settings = { mode: "google" };
  const seen = [];
  const r = createResolver({
    getSettings: () => settings,
    resolve: async (res) => { seen.push(res.kind); return { addresses: [{ address: "203.0.113.1", family: 4 }], ttl: 300 }; },
    systemLookup: sysFake([]),
  });
  await r.resolveHost("y.example");
  settings = settingsDoh;
  await r.resolveHost("y.example");
  assert.deepEqual(seen, ["plain", "doh"]);
});

test("createResolver : échec, réponse vide ou délai dépassé => repli sur le DNS du système", async () => {
  const sys = sysFake([{ address: "10.9.9.9", family: 4 }]);
  const failing = createResolver({ getSettings: () => settingsDoh, resolve: async () => { throw Object.assign(new Error("x"), { code: "ECONNREFUSED" }); }, systemLookup: sys });
  const a = await failing.resolveHost("z.example");
  assert.deepEqual([a.via, a.reason, a.addresses[0].address], ["system", "ECONNREFUSED", "10.9.9.9"]);

  const empty = createResolver({ getSettings: () => settingsDoh, resolve: async () => ({ addresses: [], ttl: 0 }), systemLookup: sys });
  assert.equal((await empty.resolveHost("z.example")).reason, "empty");

  const slow = createResolver({ getSettings: () => settingsDoh, resolve: () => new Promise(() => {}), systemLookup: sys, timeoutMs: 50 });
  const t0 = Date.now();
  const c = await slow.resolveHost("z.example");
  assert.equal(c.reason, "ETIMEDOUT");
  assert.ok(Date.now() - t0 < 1000);
});

test("createResolver : IP littérale et localhost ne passent jamais par le serveur personnalisé", async () => {
  let called = 0;
  const r = createResolver({ getSettings: () => settingsDoh, resolve: async () => { called++; return { addresses: [], ttl: 0 }; }, systemLookup: sysFake([{ address: "127.0.0.1", family: 4 }]) });
  await r.resolveHost("127.0.0.1");
  await r.resolveHost("localhost");
  assert.equal(called, 0);
});

test("lookup : signature dns.lookup (all / adresse simple, IPv4 d'abord, famille filtrée)", async () => {
  const r = createResolver({
    getSettings: () => settingsDoh,
    resolve: async () => ({ addresses: [{ address: "2001:db8::5", family: 6 }, { address: "203.0.113.5", family: 4 }], ttl: 300 }),
    systemLookup: sysFake([]),
  });
  const one = await new Promise((ok, ko) => r.lookup("w.example", {}, (e, addr, fam) => (e ? ko(e) : ok([addr, fam]))));
  assert.deepEqual(one, ["203.0.113.5", 4]);
  const all = await new Promise((ok, ko) => r.lookup("w.example", { all: true }, (e, list) => (e ? ko(e) : ok(list))));
  assert.deepEqual(all.map((a) => a.family), [4, 6]);
  const v6 = await new Promise((ok, ko) => r.lookup("w.example", { family: 6 }, (e, addr) => (e ? ko(e) : ok(addr))));
  assert.equal(v6, "2001:db8::5");
});

test("lookup branché sur un agent http : la requête atteint l'adresse résolue", async () => {
  const server = http.createServer((_req, res) => res.end("ok"));
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const r = createResolver({ getSettings: () => settingsDoh, resolve: async () => ({ addresses: [{ address: "127.0.0.1", family: 4 }], ttl: 60 }), systemLookup: sysFake([]) });
  const agent = new http.Agent({ lookup: r.lookup });
  try {
    const body = await new Promise((ok, ko) => {
      http.get(`http://panel.invalid:${server.address().port}/`, { agent }, (res) => {
        let s = "";
        res.on("data", (c) => (s += c));
        res.on("end", () => ok(s));
      }).on("error", ko);
    });
    assert.equal(body, "ok");
  } finally {
    agent.destroy();
    server.close();
  }
});

test("proxy loopback : le DNS personnalisé résout l'hôte du fournisseur", async () => {
  const { createProxyServer, buildProxyUrl } = require("../proxy.cjs");
  const upstream = http.createServer((_req, res) => res.end("catalogue"));
  await new Promise((r) => upstream.listen(0, "127.0.0.1", r));
  const resolved = [];
  const r = createResolver({
    getSettings: () => settingsDoh,
    resolve: async (_res, host) => { resolved.push(host); return { addresses: [{ address: "127.0.0.1", family: 4 }], ttl: 60 }; },
    systemLookup: sysFake([]),
  });
  const proxy = createProxyServer({ allowedOrigin: "app://ultratv", token: "tok", lookup: r.lookup });
  const { port } = await proxy.start();
  try {
    const url = buildProxyUrl(`http://127.0.0.1:${port}/`, "tok", `http://provider.invalid:${upstream.address().port}/player_api.php`);
    const res = await fetch(url);
    assert.equal(res.status, 200);
    assert.equal(await res.text(), "catalogue");
    assert.deepEqual(resolved, ["provider.invalid"]);
  } finally {
    await proxy.stop();
    upstream.close();
  }
});
