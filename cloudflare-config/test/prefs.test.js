import { describe, it, expect } from "vitest";
import { call, newAccount, pairDevice, addProvider, bearer, freshIp } from "./helpers.js";
import { parsePrefs } from "../src/store.js";

const xt = { kind: "XTREAM", name: "Démo", url: "http://iptv.example.test", username: "demo", password: "s3cret" };
async function cfg(dev) { return (await call("/api/config", { ip: freshIp(), headers: bearer(dev.token) })).json(); }
async function putPrefs(dev, id, json) {
  return call(`/api/device/providers/${id}/prefs`, { method: "PUT", ip: freshIp(), headers: bearer(dev.token), json });
}
const prefs = (t, extra = {}) => ({ langs: ["fr"], disabled: { live: ["12", "13"], movie: ["7"], series: [] }, updatedAt: t, ...extra });

async function setup() {
  const acct = await newAccount();
  await addProvider(acct, xt);
  const tv = await pairDevice(acct, "TV");
  const mac = await pairDevice(acct, "Mac");
  const id = (await cfg(tv)).providers[0].id;
  return { acct, tv, mac, id };
}

describe("réglages partagés d'une source", () => {
  it("prefs_publieesParUnAppareil_recuesParLAutre", async () => {
    const { tv, mac, id } = await setup();
    expect((await cfg(tv)).providers[0].prefs).toBeNull();
    const r = await putPrefs(mac, id, prefs(1000));
    expect(r.status).toBe(200);
    const p = (await cfg(tv)).providers[0].prefs;
    expect(p.langs).toEqual(["fr"]);
    expect(p.disabled.live).toEqual(["12", "13"]);
    expect(p.updatedAt).toBe(1000);
    expect(p.by).toBe(mac.deviceId);
  });
  it("prefs_plusAncienQueStocke_refuse409_avecLaVersionGagnante", async () => {
    const { tv, mac, id } = await setup();
    await putPrefs(mac, id, prefs(2000));
    const r = await putPrefs(tv, id, prefs(1500, { langs: ["en"] }));
    expect(r.status).toBe(409);
    expect((await r.json()).prefs.langs).toEqual(["fr"]);
  });
  it("prefs_changentLaVersion_EtagInvalide", async () => {
    const { tv, mac, id } = await setup();
    const v1 = (await cfg(tv)).version;
    await putPrefs(mac, id, prefs(3000));
    expect((await cfg(tv)).version).toBeGreaterThan(v1);
  });
  it("prefs_conserveesQuandLaSourceEstModifieeParUnAppareil", async () => {
    const { tv, mac, id } = await setup();
    await putPrefs(mac, id, prefs(4000));
    const r = await call("/api/device/providers", { method: "PUT", ip: freshIp(), headers: bearer(tv.token), json: { ...xt, id, name: "Renommée" } });
    expect(r.status).toBe(200);
    expect((await cfg(mac)).providers[0].prefs.updatedAt).toBe(4000);
  });
  it("prefs_sourceInconnueOuSansJeton", async () => {
    const { tv } = await setup();
    expect((await putPrefs(tv, "deadbeef", prefs(1))).status).toBe(404);
    expect((await call("/api/device/providers/deadbeef/prefs", { method: "PUT", ip: freshIp(), json: prefs(1) })).status).toBe(401);
  });
  it("prefs_corpsInvalides_400", async () => {
    const { tv, id } = await setup();
    for (const bad of [
      { ...prefs(1), langs: "fr" },
      { ...prefs(1), langs: ["<script>"] },
      { ...prefs(1), disabled: [] },
      { ...prefs(1), disabled: { live: [1, 2] } },
      { ...prefs(1), disabled: { live: ["a\u0000b"] } },
      { ...prefs(0) },
    ]) expect((await putPrefs(tv, id, bad)).status).toBe(400);
  });
});

describe("parsePrefs", () => {
  it("horlogeEnAvance_plafonnee", () => {
    const r = parsePrefs(prefs(9_999_999_999_999), 1_000);
    expect(r.prefs.updatedAt).toBe(61_000);
  });
  it("langsNull_toutesLesLangues_doublonsRetires", () => {
    const r = parsePrefs({ langs: null, disabled: { live: ["a", "a"], movie: ["Cinéma FR"] }, updatedAt: 5 });
    expect(r.prefs.langs).toBeNull();
    expect(r.prefs.disabled).toEqual({ live: ["a"], movie: ["Cinéma FR"], series: [] });
  });
  it("tropDIdentifiants_refuse", () => {
    const many = Array.from({ length: 5001 }, (_, i) => String(i));
    expect(parsePrefs({ langs: null, disabled: { live: many }, updatedAt: 1 }).error).toBe("disabled.live");
  });
});
