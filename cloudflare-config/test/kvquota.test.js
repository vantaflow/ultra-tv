import { describe, it, expect } from "vitest";
import { env } from "cloudflare:workers";
import { getAccount } from "../src/store.js";
import { call, newAccount, pairDevice, addProvider, bearer, freshIp, d1Rows } from "./helpers.js";

// Quota gratuit KV (1 000 écritures/jour) : les écritures fréquentes (activité des appareils, état partagé) vont dans D1,
// sinon l'appairage échoue pour tout le monde une fois le quota atteint (« error: internal »).
const cfg = (dev) => call("/api/config", { ip: freshIp(), headers: bearer(dev.token) });

describe("écritures fréquentes hors KV", () => {
  it("activite_appareil_dansD1_compteKvInchange", async () => {
    const acct = await newAccount();
    const tv = await pairDevice(acct, "TV");
    const before = JSON.stringify(await getAccount(env, acct.login));
    const b = await (await cfg(tv)).json();
    await cfg(tv);
    expect(JSON.stringify(await getAccount(env, acct.login))).toBe(before);
    const row = (await d1Rows("device_activity")).find((r) => r.device_id === tv.deviceId);
    expect(row.last_seen).toBeGreaterThan(0);
    // La liste des appareils renvoyée à l'appli garde la dernière activité.
    const again = await (await cfg(tv)).json();
    expect(again.devices.find((d) => d.id === tv.deviceId).lastSeen).toBe(row.last_seen);
    expect(b.self).toBe(tv.deviceId);
  });

  it("etat_partage_dansD1_pasKv_luParLesAutresAppareils", async () => {
    const acct = await newAccount();
    const tv = await pairDevice(acct, "TV");
    const phone = await pairDevice(acct, "Téléphone");
    await addProvider(acct, { kind: "M3U", name: "Liste", url: "https://m3u.example/l.m3u" });
    const pid = (await (await cfg(tv)).json()).providers[0].id;
    const at = Date.now();
    const post = (dev, json) => call(`/api/device/providers/${pid}/state`, { method: "POST", ip: freshIp(), headers: bearer(dev.token), json });
    expect((await post(tv, { fav: [{ p: "P", k: "LIVE", r: "12", at }] })).status).toBe(200);
    expect(await env.CONFIG.get(`st:${acct.login}:${pid}`)).toBeNull();
    expect((await d1Rows("shared_state")).some((r) => r.k === `st:${acct.login}:${pid}`)).toBe(true);
    const got = await (await post(phone, { fav: [] })).json();
    expect(got.fav.map((e) => e.r)).toEqual(["12"]);
  });

  it("etat_ancienKv_toujoursLu", async () => {
    const acct = await newAccount();
    const tv = await pairDevice(acct, "TV");
    await addProvider(acct, { kind: "M3U", name: "Liste", url: "https://m3u.example/l.m3u" });
    const pid = (await (await cfg(tv)).json()).providers[0].id;
    const { saveState } = await import("../src/store.js");
    // Valeur écrite dans KV avant la migration : simulée en écrivant sans D1.
    await saveState({ ...env, DB: undefined }, acct.login, pid, { fav: [{ p: "P", k: "LIVE", r: "7", at: Date.now(), on: true }], hist: [] });
    const got = await (await call(`/api/device/providers/${pid}/state`, { method: "POST", ip: freshIp(), headers: bearer(tv.token), json: { fav: [] } })).json();
    expect(got.fav.map((e) => e.r)).toEqual(["7"]);
  });
});
