import { describe, expect, it } from "vitest";
import { env } from "cloudflare:workers";
import { call, freshIp } from "./helpers.js";
import { modelOf, platformOf } from "../src/telemetry.js";

const ping = (body) => call("/api/anon/ping", { method: "POST", ip: freshIp(), json: body });
const rows = async () => (await env.DB.prepare("SELECT * FROM anon_pings").all()).results;

describe("relevé anonyme : plateforme et modèle", () => {
  it("stocke plateforme et modèle, sans identifiant ni IP", async () => {
    const id = crypto.randomUUID();
    const r = await ping({ install: id, version: "3.1.0", platform: "android-tv", model: "SKYWORTH 4K" });
    expect(r.status).toBe(200);
    const mine = (await rows()).find((x) => x.version === "3.1.0");
    expect(mine).toMatchObject({ platform: "android-tv", model: "SKYWORTH 4K" });
    expect(JSON.stringify(await rows())).not.toContain(id);
  });
  it("les cinq plateformes sont acceptées", async () => {
    for (const platform of ["android-tv", "android-mobile", "windows", "macos", "linux"]) {
      expect((await ping({ install: crypto.randomUUID(), platform })).status).toBe(200);
    }
  });
  it("plateforme inconnue : refusée, rien d'enregistré", async () => {
    const before = (await rows()).length;
    const r = await ping({ install: crypto.randomUUID(), version: "3.2.0", platform: "playstation" });
    expect(r.status).toBe(400);
    expect((await rows()).length).toBe(before);
  });
  it("sans plateforme (anciennes versions) : accepté, plateforme vide", async () => {
    expect((await ping({ install: crypto.randomUUID(), version: "3.3.0" })).status).toBe(200);
    expect((await rows()).find((x) => x.version === "3.3.0").platform).toBeNull();
  });
  it("modèle nettoyé et limité à 64 caractères", async () => {
    await ping({ install: crypto.randomUUID(), version: "3.4.0", platform: "android-mobile", model: `<b>Pixel</b>  "7"\n${"x".repeat(100)}` });
    const m = (await rows()).find((x) => x.version === "3.4.0").model;
    expect(m.length).toBeLessThanOrEqual(64);
    expect(m).not.toMatch(/[<>"\n]/);
    expect(m.startsWith("b Pixel /b")).toBe(true);
  });
  it("mise à jour à chaque relevé quand la plateforme change (malgré la limite de 6 h), première date conservée", async () => {
    const id = crypto.randomUUID();
    await ping({ install: id, version: "3.5.0" });
    const first = (await rows()).find((x) => x.version === "3.5.0");
    await ping({ install: id, version: "3.5.0", platform: "linux", model: "ThinkPad" });
    const after = (await rows()).find((x) => x.version === "3.5.0");
    expect(after).toMatchObject({ platform: "linux", model: "ThinkPad", first_seen: first.first_seen });
    // même plateforme, même version, moins de 6 h : pas de nouvelle écriture
    await ping({ install: id, version: "3.5.0", platform: "linux", model: "Autre" });
    expect((await rows()).find((x) => x.version === "3.5.0").model).toBe("ThinkPad");
  });
  it("assainisseurs", () => {
    expect(platformOf("macos")).toBe("macos");
    expect(platformOf("MacOS")).toBeNull();
    expect(platformOf(5)).toBeNull();
    expect(modelOf("  ")).toBeNull();
    expect(modelOf(42)).toBeNull();
    expect(modelOf("SKYWORTH   4K")).toBe("SKYWORTH 4K");
  });
});
