import { describe, expect, it } from "vitest";
import { env } from "cloudflare:workers";
import { call, freshIp } from "./helpers.js";

const post = (path, body, ip = freshIp()) => call(path, { method: "POST", ip, json: body });
const rows = async (where = "1=1") => (await env.DB.prepare(`SELECT * FROM events WHERE ${where}`).all()).results;

describe("télémétrie d'un appareil sans compte", () => {
  it("plantage : enregistré avec modèle, version et Android, sans compte ni identifiant d'installation", async () => {
    const install = crypto.randomUUID();
    const r = await post("/api/anon/crash", { install, version: "9.1.0", versionCode: 90100, device: "Amazon AFTKM", androidSdk: 28, stack: "main: java.lang.IllegalStateException: boom\n\tat a.b(c:1)" });
    expect(r.status).toBe(200);
    const [row] = await rows("version = '9.1.0'");
    expect(row).toMatchObject({ kind: "crash", device: "Amazon AFTKM", version_code: 90100, android_sdk: 28, acct: null, mac: null });
    expect(row.stack).toContain("IllegalStateException");
    expect(row.device_id).toMatch(/^anon-[0-9a-f]{12}$/);
    expect(JSON.stringify(row)).not.toContain(install);
  });

  it("lot de journaux : les erreurs sont gardées, le même appareil garde le même identifiant", async () => {
    const install = crypto.randomUUID();
    const body = { install, version: "9.2.0", versionCode: 90200, device: "Amazon AFTSSS", events: [
      { level: "error", tag: "UtvSync", message: "échec STALKER après 200 ms: HTTP 403" },
      { level: "warn", tag: "play", message: "fallback kind=FORMAT" },
    ] };
    const r = await post("/api/anon/events", body);
    expect(r.status).toBe(200);
    await post("/api/anon/crash", { install, version: "9.2.0", device: "Amazon AFTSSS", stack: "x" });
    const mine = await rows("version = '9.2.0'");
    expect(mine.filter((x) => x.kind === "event").map((x) => x.tag).sort()).toEqual(["UtvSync", "play"]);
    expect(new Set(mine.map((x) => x.device_id)).size).toBe(1);
    expect(mine.every((x) => x.acct === null && x.device === "Amazon AFTSSS")).toBe(true);
  });

  it("identifiant d'installation absent ou invalide : refusé, rien d'enregistré", async () => {
    for (const install of [undefined, "", "court", "a b c d e f g h i j k l m n o p", 42]) {
      expect((await post("/api/anon/crash", { install, version: "9.3.0", stack: "x" })).status).toBe(400);
      expect((await post("/api/anon/events", { install, version: "9.3.0", events: [{ level: "error", tag: "t", message: "m" }] })).status).toBe(400);
    }
    expect(await rows("version = '9.3.0'")).toEqual([]);
  });

  it("lot invalide ou trop gros : refusé", async () => {
    const install = crypto.randomUUID();
    expect((await post("/api/anon/events", { install, version: "9.4.0" })).status).toBe(400);
    const many = Array.from({ length: 51 }, (_, i) => ({ level: "error", tag: "t", message: `m${i}` }));
    expect((await post("/api/anon/events", { install, version: "9.4.0", events: many })).status).toBe(413);
    expect(await rows("version = '9.4.0'")).toEqual([]);
  });

  it("identifiants de fournisseur nettoyés comme pour un appareil appairé", async () => {
    const install = crypto.randomUUID();
    await post("/api/anon/events", { install, version: "9.5.0", events: [{ level: "error", tag: "UtvPlay", message: "url=http://h.example/live/jean/secret123/1.ts?username=jean&password=secret123" }] });
    const [row] = await rows("version = '9.5.0'");
    expect(row.message).not.toContain("secret123");
  });

  it("débit borné par installation", async () => {
    const install = crypto.randomUUID();
    let refused = 0;
    for (let i = 0; i < 14; i++) {
      const r = await post("/api/anon/crash", { install, version: "9.6.0", stack: `s${i}` });
      if (r.status === 429) refused++;
    }
    expect(refused).toBeGreaterThan(0);
    expect((await rows("version = '9.6.0'")).length).toBeLessThanOrEqual(10);
  });
});
