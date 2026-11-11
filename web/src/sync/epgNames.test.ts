import "fake-indexeddb/auto";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { db, setSetting } from "@/db/db";
import { emptySource } from "@/db/sources";
import type { ChannelRow } from "@/db/types";
import { EXTRA_EPG_KEY, scanXmltv, syncEpg } from "./epg";
import { matchByName, normEpgName } from "./epgNames";

// Guide d'un fournisseur polonais : identifiants avec espaces, plusieurs noms par chaîne, décalage +0100.
const FEEDS = [
  { id: "TVP 1", names: ["TVP 1", "TVP1", "PL: TVP 1 HD", "PL| TVP 1", "TVP 1 HD PL", "TVP 1 FHD PL"] },
  { id: "TVP 2", names: ["TVP 2", "TVP2", "PL: TVP 2 HD", "TVP 2 FHD PL"] },
  { id: "Polsat", names: ["Polsat", "POLSAT HD", "PL | POLSAT"] },
  { id: "TVN", names: ["TVN", "TVN HD PL"] },
  { id: "TVN 7", names: ["TVN 7", "TVN7", "PL: TVN 7 HD"] },
  { id: "TVN24", names: ["TVN24", "TVN 24", "TVN24 FHD PL"] },
];

const pad = (n: number) => String(n).padStart(2, "0");
/** Heure locale XMLTV à +0100 pour un instant UTC donné. */
const xt1 = (ms: number) => { const d = new Date(ms + 3600_000); return `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}00 +0100`; };

const feedXml = (feeds = FEEDS, perChannel = 3) => {
  const now = Date.now();
  let s = `<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="fixture">\n`;
  for (const f of feeds) s += `<channel id="${f.id}">${f.names.map((n) => `<display-name lang="pl">${n}</display-name>`).join("")}</channel>\n`;
  for (const f of feeds) for (let k = 0; k < perChannel; k++) {
    s += `<programme start="${xt1(now + (k + 1) * 3600_000)}" stop="${xt1(now + (k + 2) * 3600_000)}" channel="${f.id}"><title lang="pl">Program ${f.id} ${k}</title><desc lang="pl">Opis</desc></programme>\n`;
  }
  return s + "</tv>\n";
};

describe("normEpgName", () => {
  it("préfixe de pays, suffixe de pays, qualité, casse, espaces", () => {
    for (const n of ["TVP 1", "TVP1", "PL: TVP 1 HD", "PL| TVP 1", "PL | TVP 1", "TVP 1 HD PL", "TVP 1 FHD PL", "[PL] tvp 1 4K", "tvp 1 pl uhd"]) expect(normEpgName(n), n).toBe("tvp1");
    expect(normEpgName("PL | POLSAT HD")).toBe("polsat");
    expect(normEpgName("TVN FHD")).toBe("tvn");
    expect(normEpgName("13ème Rue HD")).toBe("13emerue");
  });
  it("garde un nom qui n'est qu'un code pays, et un chiffre significatif", () => {
    expect(normEpgName("PL")).toBe("pl");
    expect(normEpgName("TVN 7")).toBe("tvn7");
    expect(normEpgName("TVN")).not.toBe(normEpgName("TVN 7"));
  });
});

describe("matchByName", () => {
  const targets = [
    { id: 1, name: "PL: TVP 1 HD", epg: null, country: null },
    { id: 2, name: "PL | POLSAT HD", epg: null, country: null },
    { id: 3, name: "TVN FHD", epg: null, country: null },
    { id: 4, name: "Telewizja Polska 1", epg: "TVP1.pl", country: null },
    { id: 5, name: "TVN 7 HD", epg: null, country: "PL" },
    { id: 6, name: "BBC One HD", epg: null, country: "GB" },
  ];
  const m = matchByName(FEEDS, targets);
  it("rapproche par nom, sans confondre TVN et TVN 7", () => {
    expect(m.assign.get(1)).toBe("TVP 1");
    expect(m.assign.get(2)).toBe("Polsat");
    expect(m.assign.get(3)).toBe("TVN");
    expect(m.assign.get(5)).toBe("TVN 7");
    expect(m.assign.has(6)).toBe(false);
  });
  it("identifiant de playlist différent (TVP1.pl) : écrit sous l'identifiant de la chaîne, sans le modifier", () => {
    expect(m.assign.has(4)).toBe(false);
    expect(m.index.get("tvp 1")).toContain("TVP1.pl");
  });
  it("l'identifiant EPG déjà présent dans le guide prévaut : pas de rapprochement par nom", () => {
    const r = matchByName(FEEDS, [{ id: 9, name: "Autre nom", epg: "tvp 1", country: null }]);
    expect(r.index.size).toBe(0);
  });
});

describe("scanXmltv : repli par nom", () => {
  it("en flux, même coupé en petits morceaux, avec un guide dont les chaînes précèdent les programmes", () => {
    const targets = [{ id: 1, name: "PL: TVP 1 HD", epg: null, country: null }, { id: 3, name: "TVN FHD", epg: null, country: null }];
    const rows: string[] = [];
    const scan = scanXmltv(new Set(), Date.now() - 3600_000, Date.now() + 48 * 3600_000, (r) => rows.push(...r.map((x) => `${x.epg}:${x.title}`)), (feeds) => matchByName(feeds, targets));
    const xml = feedXml();
    for (let i = 0; i < xml.length; i += 37) scan.push(xml.slice(i, i + 37));
    scan.end();
    expect(rows.filter((r) => r.startsWith("TVP 1:")).length).toBe(3);
    expect(rows.filter((r) => r.startsWith("TVN:")).length).toBe(3);
    expect(rows.some((r) => r.startsWith("TVN 7:") || r.startsWith("TVN24:"))).toBe(false);
    expect(rows.length).toBe(6);
  });

  it("charge : ~110 000 programmes générés en mémoire, seules les chaînes rapprochées sont gardées", () => {
    const channels = 800, per = 137;
    const targets = Array.from({ length: 40 }, (_, i) => ({ id: i + 1, name: `PL: CH ${i * 20} HD`, epg: null, country: "PL" }));
    const now = Date.now();
    let kept = 0, maxBuffered = 0;
    const scan = scanXmltv(new Set(), now - 3600_000, now + 48 * 3600_000, (r) => { kept += r.length; maxBuffered = Math.max(maxBuffered, r.length); }, (feeds) => matchByName(feeds, targets));
    const t0 = performance.now();
    let size = 0;
    let chunk = `<?xml version="1.0"?><tv>`;
    const flush = () => { size += chunk.length; scan.push(chunk); chunk = ""; };
    for (let c = 0; c < channels; c++) { chunk += `<channel id="CH ${c}"><display-name>CH ${c}</display-name><display-name>CH ${c} FHD PL</display-name></channel>\n`; if (chunk.length > 65536) flush(); }
    const desc = "Opis programu o długości zwykłego opisu w przewodniku telewizyjnym. ".repeat(4);
    for (let c = 0; c < channels; c++) for (let k = 0; k < per; k++) {
      const s = now + (k % 40) * 3600_000;
      chunk += `<programme start="${xt1(s)}" stop="${xt1(s + 1800_000)}" channel="CH ${c}"><title lang="pl">Program ${c}-${k}</title><desc lang="pl">${desc}</desc></programme>\n`;
      if (chunk.length > 65536) flush();
    }
    chunk += "</tv>"; flush();
    scan.end();
    const ms = Math.round(performance.now() - t0);
    console.info(`charge guide web : ${Math.round(size / 1e6)} Mo, ${channels * per} programmes, ${kept} gardés, plus gros lot ${maxBuffered}, ${ms} ms`);
    expect(kept).toBe(40 * per);
    expect(maxBuffered).toBeLessThan(2100);
  }, 120_000);
});

// ---- de bout en bout : source M3U sans url-tvg + guide complémentaire global ----
let server: http.Server;
let base = "";
beforeAll(async () => {
  server = http.createServer((req, res) => {
    if ((req.url ?? "").split("?")[0] !== "/pl.xml") { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { "content-type": "text/xml" });
    res.end(feedXml());
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});
afterAll(() => { server.close(); });

const chan = (id: number, name: string, epg: string | null): ChannelRow => ({ sourceId: 7, catExt: "1", ord: id, streamId: id, num: id, name, display: name, norm: name.toLowerCase(), country: null, q: 0, flags: 0, sep: 0, logo: null, epg, archive: 0 });
beforeEach(async () => {
  await db.channels.clear(); await db.programs.clear(); await setSetting(EXTRA_EPG_KEY, `${base}/pl.xml`);
  await db.channels.bulkAdd([
    chan(1, "PL: TVP 1 HD", null), chan(2, "PL | POLSAT HD", null), chan(3, "TVN FHD", null),
    chan(4, "Telewizja Polska 1", "TVP1.pl"), chan(5, "TVN 7 HD", ""), chan(6, "BBC One HD", null),
  ].map((c) => ({ ...c, epg: c.epg || null })));
});

describe("syncEpg : M3U sans url-tvg, guide complémentaire global", () => {
  it("les chaînes sans identifiant, ou à identifiant différent, reçoivent leurs programmes", async () => {
    const src = { ...emptySource(), type: "m3u" as const, m3uUrl: "http://x.test/l.m3u", cid: 7 };
    const n = await syncEpg(src, { mode: "direct" });
    const rows = await db.programs.toArray();
    expect(n).toBe(rows.length);
    const byEpg = (e: string) => rows.filter((r) => r.epg === e).map((r) => r.title);
    expect(byEpg("TVP 1")).toHaveLength(3);
    expect(byEpg("TVP1.pl")).toHaveLength(3);   // chaîne 4 : écrit sous SON identifiant
    expect(byEpg("Polsat")).toHaveLength(3);
    expect(byEpg("TVN").every((t) => t.startsWith("Program TVN "))).toBe(true);
    expect(byEpg("TVN 7").every((t) => t.startsWith("Program TVN 7 "))).toBe(true);
    // Les chaînes sans identifiant prennent celui du guide (c'est lui que lisent le guide et le lecteur).
    const epgOf = async (streamId: number) => (await db.channels.toArray()).find((c) => c.streamId === streamId)?.epg;
    expect(await epgOf(1)).toBe("TVP 1");
    expect(await epgOf(2)).toBe("Polsat");
    expect(await epgOf(3)).toBe("TVN");
    expect(await epgOf(5)).toBe("TVN 7");
    expect(await epgOf(4)).toBe("TVP1.pl");
    expect(await epgOf(6)).toBeNull();
    expect(rows.every((r) => r.start > 0 && r.end > r.start)).toBe(true);
  });
});
