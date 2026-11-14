import { describe, it, expect } from "vitest";
import { env } from "cloudflare:workers";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";
import { parseProfiles, mergeProfiles, getAccount } from "../src/store.js";

const sync = (dev, profiles) => call("/api/device/profiles", { method: "POST", ip: freshIp(), headers: bearer(dev.token), json: { profiles } });

describe("profils partagés", () => {
  it("profilCreeSurUneTv_recuParLAutre", async () => {
    const acct = await newAccount();
    const tv = await pairDevice(acct, "TV"); const salon = await pairDevice(acct, "Salon");
    expect((await sync(tv, [{ n: "Principal", c: -1, k: false, at: 100 }, { n: "Enfants", c: 255, k: true, at: 200 }])).status).toBe(200);
    const got = await (await sync(salon, [{ n: "Principal", c: -1, k: false, at: 50 }])).json();
    expect(got.profiles.map((p) => p.n)).toEqual(["Principal", "Enfants"]);
    expect(got.profiles.find((p) => p.n === "Enfants").k).toBe(true);
  });
  it("suppressionPlusRecente_gagne_etAucuneEcritureSiRienNeChange", async () => {
    const acct = await newAccount();
    const tv = await pairDevice(acct, "TV");
    const t = Date.now() - 10_000;
    await sync(tv, [{ n: "Invité", c: 1, at: t }]);
    const r = await (await sync(tv, [{ n: "invité", c: 1, at: t + 300, on: false }])).json();
    expect(r.profiles[0].on).toBe(false);
    const before = JSON.stringify((await getAccount(env, acct.login)).profiles);
    await sync(tv, [{ n: "Invité", c: 1, at: t + 200 }]); // plus ancien que la suppression : ignoré
    expect(JSON.stringify((await getAccount(env, acct.login)).profiles)).toBe(before);
  });
  it("valeursInvalides_refusees_etPinJamaisGarde", () => {
    expect(parseProfiles({ profiles: "x" }).error).toBe("profiles");
    expect(parseProfiles({ profiles: [{ n: "", at: 1 }] }).error).toBe("profiles");
    const ok = parseProfiles({ profiles: [{ n: "Papa", at: 5, pin: "1234", c: "rouge" }] });
    expect(ok.profiles[0]).toEqual({ n: "Papa", c: 0, k: false, at: 5, on: true });
    expect(mergeProfiles([], [{ n: "Vieux", c: 0, k: false, at: 1, on: false }], 1e13)).toEqual([]);
  });
});
