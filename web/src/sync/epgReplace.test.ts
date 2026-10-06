import "fake-indexeddb/auto";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { db } from "@/db/db";
import { emptySource } from "@/db/sources";
import { syncEpg } from "./epg";

// Heures XMLTV relatives à « maintenant » : le guide ne garde que [-3 h ; +48 h].
const xt = (ms: number) => new Date(ms).toISOString().replace(/[-:T]/g, "").slice(0, 14) + " +0000";
const prog = (ch: string, h: number) => `<programme start="${xt(Date.now() + h * 3600_000)}" stop="${xt(Date.now() + (h + 1) * 3600_000)}" channel="${ch}"><title>Nouveau ${h}</title></programme>`;
let server: http.Server;
let mode: "ok" | "cut" = "ok";
let base = "";
beforeAll(async () => {
  server = http.createServer((_req, res) => {
    const body = `<tv>${prog("a", 1)}${prog("a", 2)}</tv>`;
    if (mode === "cut") {
      // Annonce plus d'octets qu'il n'en envoie, puis coupe : lecture du flux en échec après un premier lot.
      res.writeHead(200, { "content-type": "text/xml", "content-length": String(body.length + 500) });
      res.write(body.slice(0, body.indexOf("</programme>") + 12));
      setTimeout(() => res.destroy(), 30);
      return;
    }
    res.writeHead(200, { "content-type": "text/xml" });
    res.end(body);
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}/xmltv.php`;
});
afterAll(() => { server.close(); });

const source = () => ({ ...emptySource(), id: 1, cid: 9, epgUrl: base });

describe("syncEpg : remplacement du guide", () => {
  it("flux coupé : l'ancien guide est conservé, rien de partiel n'est laissé", async () => {
    await db.channels.add({ sourceId: 9, catExt: "1", ord: 0, streamId: 1, num: 1, name: "A", display: "A", norm: "a", country: null, q: 0, flags: 0, sep: 0, logo: null, epg: "a", archive: 0 });
    await db.programs.add({ sourceId: 9, epg: "a", start: Date.now(), end: Date.now() + 3600_000, title: "Ancien", desc: "" });
    mode = "cut";
    await expect(syncEpg(source(), { mode: "direct" })).rejects.toThrow();
    expect((await db.programs.toArray()).map((p) => p.title)).toEqual(["Ancien"]);
  });

  it("flux complet : l'ancien guide est remplacé par le nouveau", async () => {
    mode = "ok";
    const n = await syncEpg(source(), { mode: "direct" });
    expect(n).toBe(2);
    expect((await db.programs.toArray()).map((p) => p.title).sort()).toEqual(["Nouveau 1", "Nouveau 2"]);
  });
});
