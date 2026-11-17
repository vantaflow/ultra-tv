"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const d = require("../discovery.cjs");

test("Jellyfin : réponse JSON de l'UDP 7359", () => {
  const r = d.parseMediaReply(Buffer.from('{"Address":"http://192.168.1.20:8096/","Id":"abc","Name":"Salon","EndpointAddress":null}'), "jellyfin", "192.168.1.20");
  assert.deepEqual(r, { kind: "jellyfin", name: "Salon", address: "http://192.168.1.20:8096", id: "abc" });
});

test("Emby : même format, type donné par la sonde", () => {
  const r = d.parseMediaReply(Buffer.from('{"Address":"http://192.168.1.31:8096","Id":"e1","Name":"Bureau"}'), "emby", "192.168.1.31");
  assert.equal(r.kind, "emby");
  assert.equal(r.name, "Bureau");
});

test("réponse sans adresse valide : repli sur l'expéditeur ; sans nom : nom du type", () => {
  const r = d.parseMediaReply(Buffer.from('{"Address":"pas une adresse","Id":""}'), "jellyfin", "10.0.0.5");
  assert.equal(r.address, "http://10.0.0.5:8096");
  assert.equal(r.name, "Jellyfin");
  assert.equal(r.id, "http://10.0.0.5:8096");
});

test("réponse illisible : ignorée", () => {
  assert.equal(d.parseMediaReply(Buffer.from("n'importe quoi"), "jellyfin", "10.0.0.5"), null);
  assert.equal(d.parseMediaReply(Buffer.from("null"), "emby", "10.0.0.5"), null);
  assert.equal(d.parseMediaReply(Buffer.from('{"Id":"x"}'), "emby", ""), null);
});

test("Plex GDM : en-têtes, port annoncé, adresse de l'expéditeur", () => {
  const msg = "HTTP/1.0 200 OK\r\nContent-Type: plex/media-player\r\nResource-Identifier: m-123\r\nName: NAS-Plex\r\nPort: 32400\r\nVersion: 1.40\r\n\r\n";
  assert.deepEqual(d.parseGdmReply(Buffer.from(msg), "192.168.1.40"), { kind: "plex", name: "NAS-Plex", address: "http://192.168.1.40:32400", id: "m-123" });
});

test("Plex GDM : port absent ou absurde = 32400, réponse non 200 ignorée", () => {
  assert.equal(d.parseGdmReply(Buffer.from("HTTP/1.0 200 OK\r\nPort: 99999\r\nName: X\r\n"), "10.0.0.2").address, "http://10.0.0.2:32400");
  assert.equal(d.parseGdmReply(Buffer.from("HTTP/1.0 404 Not Found\r\n"), "10.0.0.2"), null);
  assert.equal(d.parseGdmReply(Buffer.from("HTTP/1.0 200 OK\r\n"), ""), null);
});

test("fusion : doublons retirés, tri par nom", () => {
  const a = { kind: "jellyfin", name: "Zeta", address: "http://a", id: "1" };
  const b = { kind: "plex", name: "Alpha", address: "http://b", id: "2" };
  assert.deepEqual(d.mergeServers([a, null, b, { ...a, address: "http://autre" }]), [b, a]);
});

test("discover ne lève jamais et rend une liste (délai court)", async () => {
  const r = await d.discover({ timeoutMs: 150 });
  assert.ok(Array.isArray(r));
});
