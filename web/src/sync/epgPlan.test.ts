import "fake-indexeddb/auto";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { db, setSetting } from "@/db/db";
import { emptySource } from "@/db/sources";
import type { Source } from "@/db/types";
import { EPG_LIMITS, EXTRA_EPG_KEY, planEpg, syncEpg } from "./epg";

const xtream = (extra: Partial<Source> = {}): Source => ({ ...emptySource(), type: "xtream", server: "http://prov.test", username: "u", password: "p", ...extra });
const m3u = (extra: Partial<Source> = {}): Source => ({ ...emptySource(), type: "m3u", m3uUrl: "http://x.test/l.m3u", ...extra });
const CUSTOM = "https://guide.test/perso.xml.gz";

describe("planEpg : guide de cette source (remplacer / compléter)", () => {
  it("sans adresse perso : guide du fournisseur seulement (Xtream) ou rien (M3U, Stalker)", () => {
    expect(planEpg(xtream())).toEqual({ main: expect.stringContaining("prov.test/xmltv.php"), complements: [] });
    expect(planEpg(m3u())).toEqual({ main: "", complements: [] });
    expect(planEpg({ ...emptySource(), type: "stalker" })).toEqual({ main: "", complements: [] });
  });
  it("remplacer (défaut, comportement historique) : seule l'adresse perso est lue", () => {
    expect(planEpg(xtream({ epgUrl: CUSTOM }))).toEqual({ main: CUSTOM, complements: [] });
    expect(planEpg(xtream({ epgUrl: CUSTOM, epgMode: "replace" }))).toEqual({ main: CUSTOM, complements: [] });
  });
  it("compléter : guide du fournisseur puis adresse perso pour les chaînes sans programme", () => {
    const p = planEpg(xtream({ epgUrl: CUSTOM, epgMode: "complete" }));
    expect(p.main).toContain("prov.test/xmltv.php");
    expect(p.complements).toEqual([CUSTOM]);
  });
  it("M3U / Stalker n'ont pas de guide de fournisseur : l'adresse perso est le guide, même en mode compléter", () => {
    expect(planEpg(m3u({ epgUrl: CUSTOM, epgMode: "complete" }))).toEqual({ main: CUSTOM, complements: [] });
    expect(planEpg({ ...emptySource(), type: "stalker", epgUrl: CUSTOM, epgMode: "complete" })).toEqual({ main: CUSTOM, complements: [] });
  });
  it("guide complémentaire global : en dernier, http(s) seulement, sans doublon", () => {
    expect(planEpg(m3u({ epgUrl: CUSTOM }), "https://extra.test/e.xml").complements).toEqual(["https://extra.test/e.xml"]);
    expect(planEpg(m3u({ epgUrl: CUSTOM }), CUSTOM).complements).toEqual([]);
    expect(planEpg(m3u(), "ftp://extra.test/e.xml").complements).toEqual([]);
    expect(planEpg(xtream({ epgUrl: CUSTOM, epgMode: "complete" }), "https://extra.test/e.xml").complements).toEqual([CUSTOM, "https://extra.test/e.xml"]);
  });
});

// Heures XMLTV relatives à « maintenant » : le guide ne garde que [-3 h ; +48 h].
const xt = (ms: number) => new Date(ms).toISOString().replace(/[-:T]/g, "").slice(0, 14) + " +0000";
const prog = (ch: string, label: string) => `<programme start="${xt(Date.now() + 3600_000)}" stop="${xt(Date.now() + 2 * 3600_000)}" channel="${ch}"><title>${label}</title></programme>`;
let server: http.Server;
let base = "";
beforeAll(async () => {
  EPG_LIMITS.retryDelays = []; // ces tests ne visent pas les nouvelles tentatives (voir epgStream.test.ts)
  server = http.createServer((req, res) => {
    const path = (req.url ?? "").split("?")[0]!;
    const pages: Record<string, string> = {
      "/main.xml": `<tv>${prog("a", "principal-a")}</tv>`,
      "/perso.xml": `<tv>${prog("a", "perso-a")}${prog("b", "perso-b")}</tv>`,
      "/extra.xml": `<tv>${prog("c", "extra-c")}</tv>`,
    };
    const page = pages[path];
    if (page === undefined) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { "content-type": "text/xml" });
    res.end(page);
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});
afterAll(() => { server.close(); });

const chan = (epg: string, ord: number) => ({ sourceId: 7, catExt: "1", ord, streamId: ord, num: ord, name: epg, display: epg, norm: epg, country: null, q: 0, flags: 0, sep: 0 as const, logo: null, epg, archive: 0 as const });
beforeEach(async () => {
  await db.channels.clear(); await db.programs.clear(); await setSetting(EXTRA_EPG_KEY, "");
  await db.channels.bulkAdd([chan("a", 0), chan("b", 1), chan("c", 2)]);
});
const titles = async () => (await db.programs.toArray()).map((p) => `${p.epg}:${p.title}`).sort();
const src = (extra: Partial<Source>) => ({ ...m3u(), cid: 7, ...extra });

describe("syncEpg : remplacer / compléter / guide global", () => {
  it("remplacer sur une source M3U sans guide : l'adresse perso fournit tout", async () => {
    expect(await syncEpg(src({ epgUrl: `${base}/perso.xml` }), { mode: "direct" })).toBe(2);
    expect(await titles()).toEqual(["a:perso-a", "b:perso-b"]);
  });
  it("compléter (Xtream sans réponse du fournisseur) : le guide perso comble quand même les chaînes, sans effacer l'existant", async () => {
    await db.programs.add({ sourceId: 7, epg: "a", start: Date.now(), end: Date.now() + 3600_000, title: "déjà-là", desc: "" });
    // Xtream dont le serveur ne répond pas : le complément est tenté malgré l'échec du principal.
    const s = { ...xtream({ epgUrl: `${base}/perso.xml`, epgMode: "complete", server: "http://127.0.0.1:1" }), cid: 7 };
    expect(await syncEpg(s, { mode: "direct" })).toBe(1);   // seule « b » (sans programme) est écrite
    expect(await titles()).toEqual(["a:déjà-là", "b:perso-b"]);
  });
  it("guide complémentaire global : écrit pour les chaînes restées sans programme", async () => {
    await setSetting(EXTRA_EPG_KEY, `${base}/extra.xml`);
    expect(await syncEpg(src({ epgUrl: `${base}/perso.xml` }), { mode: "direct" })).toBe(3);
    expect(await titles()).toEqual(["a:perso-a", "b:perso-b", "c:extra-c"]);
  });
  it("guide principal en échec et aucun complément : l'erreur remonte", async () => {
    await expect(syncEpg(src({ epgUrl: `${base}/absent.xml` }), { mode: "direct" })).rejects.toThrow();
  });
});
