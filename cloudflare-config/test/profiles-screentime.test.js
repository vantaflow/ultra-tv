import { describe, it, expect } from "vitest";
import { call, newAccount, pairDevice, bearer, freshIp } from "./helpers.js";
import { parseProfiles, mergeProfiles, cleanScreenTime } from "../src/store.js";

const sync = (dev, profiles) => call("/api/device/profiles", { method: "POST", ip: freshIp(), headers: bearer(dev.token), json: { profiles } });

describe("temps d'écran du profil (champ t)", () => {
  it("cleanScreenTime : normalise, borne et refuse le texte invalide", () => {
    expect(cleanScreenTime("L90;1=480-1200,1260-1380;2=;7=0-1440")).toBe("L90;1=480-1200,1260-1380;2=;7=0-1440");
    // Plages triées, fusionnées, hors bornes ignorées, jours dans l'ordre.
    expect(cleanScreenTime("3=600-700,650-720,900-800;1=0-0,1500-1600")).toBe("1=;3=600-720");
    expect(cleanScreenTime("L0;L5000")).toBe("");
    expect(cleanScreenTime("")).toBe("");
    expect(cleanScreenTime("<script>")).toBeNull();
    expect(cleanScreenTime("8=1-2")).toBeNull();
    expect(cleanScreenTime("1=a-b")).toBeNull();
    expect(cleanScreenTime("x".repeat(401))).toBeNull();
    expect(cleanScreenTime(5)).toBeNull();
  });

  it("parseProfiles : t transmis nettoyé, absent reste absent, invalide refuse la requête", () => {
    expect(parseProfiles({ profiles: [{ n: "Léa", at: 5, t: "L60;6=" }] }).profiles[0].t).toBe("L60;6=");
    expect(parseProfiles({ profiles: [{ n: "Léa", at: 5, t: "" }] }).profiles[0].t).toBe("");
    expect("t" in parseProfiles({ profiles: [{ n: "Léa", at: 5 }] }).profiles[0]).toBe(false);
    expect(parseProfiles({ profiles: [{ n: "Léa", at: 5, t: "pas bon" }] }).error).toBe("profiles");
  });

  it("mergeProfiles : une ancienne version (sans t) n'efface pas les règles, un t vide les efface", () => {
    const cur = [{ n: "Léa", c: 1, k: true, at: 100, on: true, t: "L60" }];
    const old = mergeProfiles(cur, [{ n: "Léa", c: 2, k: true, at: 200, on: true }]);
    expect(old[0].t).toBe("L60");
    expect(old[0].c).toBe(2);
    const cleared = mergeProfiles(cur, [{ n: "Léa", c: 1, k: true, at: 300, on: true, t: "" }]);
    expect(cleared[0].t).toBe("");
    // Plus ancien : ignoré.
    expect(mergeProfiles(cur, [{ n: "Léa", c: 1, k: true, at: 50, on: true, t: "L10" }])[0].t).toBe("L60");
  });

  it("de bout en bout : règles créées sur une TV, reçues par l'autre appareil", async () => {
    const acct = await newAccount();
    const tv = await pairDevice(acct, "TV"); const salon = await pairDevice(acct, "Salon");
    const t = Date.now() - 10_000;
    expect((await sync(tv, [{ n: "Léa", c: 1, k: true, at: t, t: "L60;1=480-1200" }])).status).toBe(200);
    const got = await (await sync(salon, [{ n: "Léa", c: 1, k: true, at: t - 5000 }])).json();
    expect(got.profiles.find((p) => p.n === "Léa").t).toBe("L60;1=480-1200");
    expect((await sync(salon, [{ n: "Léa", c: 1, k: true, at: t, t: "pas bon" }])).status).toBe(400);
  });
});
