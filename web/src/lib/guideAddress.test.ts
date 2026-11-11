import http from "node:http";
import type { AddressInfo } from "node:net";
import { gzipSync } from "node:zlib";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { checkGuideAddress, guideErrorKey, validateGuideAddress, verifyGuideAddress } from "./guideAddress";

const problem = (raw: string) => { const v = validateGuideAddress(raw); return v.ok ? null : v.problem; };

describe("validateGuideAddress : forme", () => {
  it("accepte http et https, .xml et .xml.gz, sans tenir compte de la casse", () => {
    for (const u of ["http://g.test/epg.xml", "https://g.test/a/epg.xml.gz", "HTTPS://g.test/EPG.XML.GZ", "  https://g.test:8080/epg.xml  "]) expect(validateGuideAddress(u).ok, u).toBe(true);
  });
  it("tolère une query string ou un fragment après le chemin", () => {
    expect(validateGuideAddress("https://g.test/epg.xml.gz?token=abc&x=1").ok).toBe(true);
    expect(validateGuideAddress("http://g.test/epg.xml#haut").ok).toBe(true);
  });
  it("refuse ftp et les adresses sans schéma", () => {
    expect(problem("ftp://g.test/epg.xml")).toBe("scheme");
    expect(problem("g.test/epg.xml")).toBe("scheme");
    expect(problem("file:///sdcard/epg.xml")).toBe("scheme");
  });
  it("refuse un chemin sans .xml / .xml.gz (l'extension doit être dans le chemin, pas dans la query)", () => {
    expect(problem("https://g.test/epg")).toBe("extension");
    expect(problem("https://g.test/epg.gz")).toBe("extension");
    expect(problem("https://g.test/xmltv.php?f=epg.xml")).toBe("extension");
    expect(problem("https://g.test/")).toBe("extension");
  });
  it("refuse le vide et l'absence d'hôte", () => {
    expect(problem("   ")).toBe("empty");
    expect(problem("https://epg.xml")).toBe("extension");
    expect(problem("https://")).not.toBeNull();
  });
  it("clé de traduction de l'erreur", () => {
    expect(guideErrorKey({ ok: false, problem: "scheme" })).toBe("guide.err.scheme");
    expect(guideErrorKey({ ok: false, reason: "http", status: 404 })).toBe("guide.err.http");
    expect(guideErrorKey({ ok: true })).toBeNull();
  });
});

const XMLTV = `<?xml version="1.0"?>\n<!DOCTYPE tv SYSTEM "xmltv.dtd">\n<tv generator-info-name="x"><channel id="a"/></tv>`;
let server: http.Server;
let base = "";
beforeAll(async () => {
  server = http.createServer((req, res) => {
    const path = (req.url ?? "").split("?")[0]!;
    if (path === "/ok.xml") { res.writeHead(200, { "content-type": "text/xml" }); res.end(XMLTV); }
    else if (path === "/ok.xml.gz") { res.writeHead(200, { "content-type": "application/octet-stream" }); res.end(gzipSync(Buffer.from(XMLTV))); }
    else if (path === "/gz-but-named.xml") { res.writeHead(200); res.end(gzipSync(Buffer.from(XMLTV))); }
    else if (path === "/html.xml") { res.writeHead(200, { "content-type": "text/html" }); res.end("<!DOCTYPE html><html><body>Connexion requise</body></html>"); }
    else if (path === "/bad.xml.gz") { res.writeHead(200); res.end(Buffer.from([0x1f, 0x8b, 0, 1, 2, 3])); }
    else { res.writeHead(404); res.end(); }
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});
afterAll(() => { server.close(); });

const direct = { mode: "direct" } as const;

describe("verifyGuideAddress : réseau", () => {
  it("XMLTV non compressé : ok", async () => expect(await verifyGuideAddress(direct, `${base}/ok.xml`)).toEqual({ ok: true }));
  it("gzip détecté par les octets magiques, quel que soit le nom ou le type", async () => {
    expect(await verifyGuideAddress(direct, `${base}/ok.xml.gz?t=1`)).toEqual({ ok: true });
    expect(await verifyGuideAddress(direct, `${base}/gz-but-named.xml`)).toEqual({ ok: true });
  });
  it("404 : code HTTP", async () => expect(await verifyGuideAddress(direct, `${base}/absent.xml`)).toEqual({ ok: false, reason: "http", status: 404 }));
  it("page HTML : pas un guide XMLTV", async () => expect(await verifyGuideAddress(direct, `${base}/html.xml`)).toEqual({ ok: false, reason: "notXmltv" }));
  it("gzip illisible : pas un guide XMLTV", async () => expect(await verifyGuideAddress(direct, `${base}/bad.xml.gz`)).toEqual({ ok: false, reason: "notXmltv" }));
  it("serveur injoignable", async () => expect(await verifyGuideAddress(direct, "http://127.0.0.1:1/epg.xml")).toEqual({ ok: false, reason: "unreachable" }));
});

describe("checkGuideAddress : forme puis réseau", () => {
  it("adresse invalide : aucune requête, clé d'erreur", async () => {
    expect(await checkGuideAddress(direct, "ftp://g.test/epg.xml")).toEqual({ key: "guide.err.scheme" });
    expect(await checkGuideAddress(direct, `${base}/epg`)).toEqual({ key: "guide.err.extension" });
  });
  it("adresse valable : null ; 404 : clé http avec le code", async () => {
    expect(await checkGuideAddress(direct, `${base}/ok.xml`)).toEqual({ key: null });
    expect(await checkGuideAddress(direct, `${base}/absent.xml`)).toEqual({ key: "guide.err.http", status: 404 });
  });
});
