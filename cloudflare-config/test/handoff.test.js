import { describe, it, expect } from "vitest";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";
import { parseHandoff, liveHandoffs, handoffHeader, HANDOFF_TTL_MS } from "../src/store.js";

const send = (dev, body) => call("/api/device/handoff", { method: "POST", ip: freshIp(), headers: bearer(dev.token), json: body });
const cfg = (dev, etag) => call("/api/config", { ip: freshIp(), headers: { ...bearer(dev.token), ...(etag ? { "if-none-match": etag } : {}) } });
const decode = (h) => JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(h.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0))));
const ref = { src: "0a1b2c3d", t: "movie", id: "1234", pos: 90_000, n: "Un film é" };

describe("reprise d'un appareil à l'autre", () => {
  it("emetteur_envoie_cible_recoit_dans_len_tete_de_config_y_compris_304", async () => {
    const acct = await newAccount();
    const tv = await pairDevice(acct, "TV"); const tel = await pairDevice(acct, "Téléphone");
    const first = await cfg(tel);
    expect(first.headers.get("x-utv-handoff")).toBeNull();
    const etag = first.headers.get("etag");
    const r = await send(tv, { to: tel.deviceId, ref });
    expect(r.status).toBe(200);
    const again = await cfg(tel, etag);
    expect(again.status).toBe(304);
    const items = decode(again.headers.get("x-utv-handoff"));
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({ s: "0a1b2c3d", t: "movie", r: "1234", o: 90_000, n: "Un film é", f: "TV" });
    // l'émetteur ne reçoit rien
    expect((await cfg(tv)).headers.get("x-utv-handoff")).toBeNull();
    // jamais d'URL ni d'identifiants dans ce qui circule
    expect(JSON.stringify(items)).not.toMatch(/https?:|password|username/i);
  });
  it("refus: soi-meme, appareil inconnu, autre compte, reference invalide", async () => {
    const acct = await newAccount(); const other = await newAccount();
    const tv = await pairDevice(acct, "TV"); const stranger = await pairDevice(other, "Autre");
    expect((await send(tv, { to: tv.deviceId, ref })).status).toBe(400);
    expect((await send(tv, { to: stranger.deviceId, ref })).status).toBe(400);
    const tel = await pairDevice(acct, "Tel");
    expect((await send(tv, { to: tel.deviceId, ref: { ...ref, t: "stream" } })).status).toBe(400);
    expect((await send(tv, { to: tel.deviceId, ref: { ...ref, src: "http://x" } })).status).toBe(400);
    expect((await call("/api/device/handoff", { method: "POST", ip: freshIp(), json: { to: tel.deviceId, ref } })).status).toBe(401);
  });
  it("file_bornee_a_3_et_expiration_2_minutes", () => {
    const now = 1_000_000;
    const items = [1, 2, 3, 4].map((k) => ({ i: String(k), a: now - k * 1000 }));
    expect(liveHandoffs(items, now).map((x) => x.i)).toEqual(["1", "2", "3"]);
    expect(liveHandoffs([{ i: "x", a: now - HANDOFF_TTL_MS - 1 }], now)).toEqual([]);
    expect(handoffHeader([])).toBe("");
  });
  it("direct: position ignoree ; titre nettoye", () => {
    const r = parseHandoff({ to: "abcdef01", ref: { src: "0a1b2c3d", t: "live", id: "5", pos: 999, n: "A\u0000B" } }, 5);
    expect(r.item.o).toBe(0);
    expect(r.item.n).toBe("A B");
  });
});
