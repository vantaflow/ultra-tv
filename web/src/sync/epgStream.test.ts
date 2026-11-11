import "fake-indexeddb/auto";
import http from "node:http";
import { gzipSync } from "node:zlib";
import type { AddressInfo } from "node:net";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { db } from "@/db/db";
import { emptySource } from "@/db/sources";
import { EPG_LIMITS, scanXmltv, syncEpg } from "./epg";

// Heures XMLTV relatives à « maintenant » : le guide ne garde que [-3 h ; +3 jours].
const xt = (ms: number) => new Date(ms).toISOString().replace(/[-:T]/g, "").slice(0, 14) + " +0000";

/** XMLTV synthétique : `progs` programmes répartis sur `days` jours (centrés sur maintenant) pour `chans` chaînes. */
function gen(chans: number, progs: number, days: number, descLen: number): string {
  const t0 = Date.now() - (days * 24 * 3600_000) / 2;
  const per = Math.ceil(progs / chans);
  const step = (days * 24 * 3600_000) / per;
  const desc = "Résumé détaillé &amp; très long ".repeat(Math.ceil(descLen / 32)).slice(0, descLen);
  const parts: string[] = ['<?xml version="1.0" encoding="UTF-8"?><tv>'];
  for (let c = 0; c < chans; c++) parts.push(`<channel id="ch${c}"><display-name>Chaîne ${c}</display-name></channel>`);
  for (let c = 0; c < chans; c++) for (let i = 0; i < per; i++) {
    const s = t0 + i * step;
    parts.push(`<programme start="${xt(s)}" stop="${xt(s + step)}" channel="ch${c}"><title lang="fr">Programme ${c}-${i}</title><desc lang="fr">${desc}</desc></programme>\n`);
  }
  parts.push("</tv>");
  return parts.join("");
}

describe("analyse en flux d'un gros guide", () => {
  // ~110 000 programmes, ~85 Mo, 1 000 chaînes dans le guide dont 40 dans le catalogue.
  const xml = gen(1000, 110_000, 14, 600);
  const wanted = new Set(Array.from({ length: 40 }, (_, c) => `ch${c}`));
  const now = Date.now();
  const inWindow = (xml.match(/<programme /g) ?? []).length; // contrôle : le gros fichier contient bien ~110k programmes

  it("lit un fichier de ~85 Mo par morceaux de 16 Ko, sans le garder en entier, en quelques secondes", () => {
    expect(xml.length).toBeGreaterThan(70e6);
    expect(inWindow).toBeGreaterThan(100_000);
    const batches: number[] = [];
    let total = 0;
    const t0 = performance.now();
    const s = scanXmltv(wanted, now - 3 * 3600_000, now + 72 * 3600_000, (rows) => { batches.push(rows.length); total += rows.length; });
    for (let i = 0; i < xml.length; i += 16384) s.push(xml.slice(i, i + 16384));
    s.end();
    const ms = performance.now() - t0;
    // Seules les chaînes du catalogue et la fenêtre utile sont gardées : 40 chaînes × ~(75 h / 336 h) des 110 programmes.
    expect(total).toBeGreaterThan(500);
    expect(total).toBeLessThan(1500);
    expect(ms).toBeLessThan(20_000);
    console.info(`flux ${(xml.length / 1e6).toFixed(0)} Mo : ${total} programmes retenus en ${Math.round(ms)} ms`);
  });

  it("les lots sont petits d'abord (affichage rapide) puis plafonnés à 4 000", () => {
    const sizes: number[] = [];
    const big = new Set(Array.from({ length: 1000 }, (_, c) => `ch${c}`));
    const s = scanXmltv(big, now - 3 * 3600_000, now + 72 * 3600_000, (rows) => sizes.push(rows.length));
    for (let i = 0; i < xml.length; i += 65536) s.push(xml.slice(i, i + 65536));
    s.end();
    expect(sizes[0]!).toBeLessThan(1500);
    expect(Math.max(...sizes)).toBeLessThan(4000 + 2000);
    expect(sizes.length).toBeGreaterThan(3);
  });
});

describe("syncEpg : réseau lent, gzip, reprises, délais", () => {
  let server: http.Server;
  let base = "";
  let handler: (req: http.IncomingMessage, res: http.ServerResponse) => void = () => undefined;
  beforeAll(async () => {
    server = http.createServer((q, r) => handler(q, r));
    await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}/x.xml`;
  });
  afterAll(() => { server.close(); });
  const saved = { ...EPG_LIMITS, retryDelays: [...EPG_LIMITS.retryDelays] };
  beforeEach(async () => {
    Object.assign(EPG_LIMITS, saved, { retryDelays: [20, 20] });
    await db.programs.clear(); await db.channels.clear();
    await db.channels.bulkAdd(Array.from({ length: 5 }, (_, c) => ({ sourceId: 9, catExt: "1", ord: c, streamId: c, num: c + 1, name: `C${c}`, display: `C${c}`, norm: "c", country: null, q: 0, flags: 0, sep: 0, logo: null, epg: `ch${c}`, archive: 0 })));
  });
  const source = () => ({ ...emptySource(), id: 1, cid: 9, epgUrl: base });
  // 5 chaînes × 40 programmes d'une heure autour de maintenant : tout est dans la fenêtre (200 lignes), ~60 Ko.
  const doc = () => gen(5, 200, 1.6, 100);
  const bigDoc = () => gen(5, 3000, 4, 100); // ~1 600 lignes dans la fenêtre : au moins un lot est écrit avant la fin

  it("gzip détecté (sans en-tête ni extension) et programmes écrits par lots", async () => {
    const body = gzipSync(doc());
    handler = (_q, res) => { res.writeHead(200, { "content-type": "text/plain" }); res.end(body); };
    const seen: { bytes: number; programs: number }[] = [];
    const n = await syncEpg(source(), { mode: "direct" }, undefined, (_r, st) => { if (st) seen.push(st); });
    expect(n).toBeGreaterThan(100);
    expect(await db.programs.count()).toBe(n);
  });

  it("flux lent : les programmes arrivent en base AVANT la fin du téléchargement, avec progression en octets", async () => {
    const body = Buffer.from(bigDoc());
    let sent = 0;
    let finish: () => void = () => undefined;
    const release = new Promise<void>((r) => { finish = r; });
    handler = (_q, res) => {
      res.writeHead(200, { "content-length": String(body.length) });
      // Réseau lent : 8 Ko tous les 5 ms ; la dernière partie n'est envoyée qu'après vérification.
      const cut = Math.floor(body.length * 0.8);
      const tick = () => {
        if (sent < cut) { res.write(body.subarray(sent, Math.min(cut, sent + 8192))); sent += 8192; setTimeout(tick, 5); }
        else void release.then(() => res.end(body.subarray(cut)));
      };
      tick();
    };
    const reports: number[] = [];
    let midCount = -1;
    const p = syncEpg(source(), { mode: "direct" }, undefined, (_r, st) => { if (st) reports.push(st.bytes); });
    for (let i = 0; i < 300 && midCount <= 0; i++) { await new Promise((r) => setTimeout(r, 20)); midCount = await db.programs.count(); }
    expect(midCount).toBeGreaterThan(0); // déjà lisible, le téléchargement n'est pas fini
    finish();
    const n = await p;
    expect(await db.programs.count()).toBe(n);
    expect(reports.length).toBeGreaterThan(0);
    expect(Math.max(...reports)).toBeGreaterThan(0);
  });

  it("nouvelle tentative après une erreur 503, puis succès", async () => {
    let hits = 0;
    const body = doc();
    handler = (_q, res) => { hits++; if (hits === 1) { res.writeHead(503); res.end(); } else { res.writeHead(200); res.end(body); } };
    const n = await syncEpg(source(), { mode: "direct" });
    expect(hits).toBe(2);
    expect(n).toBeGreaterThan(100);
  });

  it("pas de nouvelle tentative sur une erreur 404", async () => {
    let hits = 0;
    handler = (_q, res) => { hits++; res.writeHead(404); res.end(); };
    await expect(syncEpg(source(), { mode: "direct" })).rejects.toThrow();
    expect(hits).toBe(1);
  });

  it("durée totale bornée : un flux qui goutte-à-goutte sans fin est coupé (TimeoutError), rien de partiel n'est gardé", async () => {
    EPG_LIMITS.totalMs = 400;
    EPG_LIMITS.retryDelays = [];
    const body = Buffer.from(doc());
    handler = (_q, res) => {
      res.writeHead(200);
      let i = 0;
      const t = setInterval(() => { if (res.destroyed) { clearInterval(t); return; } res.write(body.subarray(i, i + 2048)); i = (i + 2048) % (body.length - 2048); }, 20);
      res.on("close", () => clearInterval(t));
    };
    const t0 = Date.now();
    await expect(syncEpg(source(), { mode: "direct" })).rejects.toMatchObject({ name: "TimeoutError" });
    expect(Date.now() - t0).toBeLessThan(3000);
    expect(await db.programs.count()).toBe(0);
  });

  it("l'annulation de l'appelant reste une AbortError (jamais relancée)", async () => {
    let hits = 0;
    handler = (_q, res) => { hits++; res.writeHead(200); res.write("<tv>"); /* ne finit jamais */ };
    const ctl = new AbortController();
    const p = syncEpg(source(), { mode: "direct" }, ctl.signal);
    setTimeout(() => ctl.abort(), 100);
    await expect(p).rejects.toMatchObject({ name: "AbortError" });
    expect(hits).toBe(1);
  });
});
