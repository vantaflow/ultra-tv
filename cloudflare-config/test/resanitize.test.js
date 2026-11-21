// Script de réécriture des anciennes lignes (scripts/resanitize-events.mjs), sur la base locale des tests : jamais la
// production. Identifiants FICTIFS seulement.
import { describe, it, expect } from "vitest";
import { env } from "cloudflare:workers";
import { parseArgs, resanitize, resanitizeRow, hasResidue } from "../scripts/resanitize-events.mjs";
import { sanitizeText } from "../src/sanitize.js";

const KEY = "cle-de-test-fictive-pour-la-reecriture-0123";
const BEFORE = Date.parse("2026-11-01T00:00:00Z");
const SECRETS = ["ZZUSERZZ", "ZZPASSZZ", "ZZFINZZ", "ZZTOKENZZ", "fournisseur.invalid", "00:1A:79:AB:CD:EF", "zzuserzz@example.invalid"];

async function seed(tagVersion) {
  const ins = (kind, ts, f) => env.DB.prepare(
    "INSERT INTO events (kind, ts, device_id, mac, version, device, level, tag, message, stack) VALUES (?,?,?,?,?,?,?,?,?,?)",
  ).bind(kind, ts, "d", f.mac ?? null, tagVersion, f.device ?? null, "error", f.tag ?? null, f.message ?? null, f.stack ?? null).run();
  // Anciennes lignes TELLES QUE LA PRODUCTION LES A ÉCRITES : l'ancien Worker passait chaque champ par `sanitizeText`
  // (masquage partiel : `password=<redacted> ZZFINZZ` pour un mot de passe à espace). Plus une ligne déjà propre.
  const old = (f) => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, k === "mac" ? v : sanitizeText(v, 32768)]));
  await ins("event", BEFORE - 5000, old({ message: "http://fournisseur.invalid/player_api.php?username=ZZUSERZZ&password=ZZPASSZZ ZZFINZZ", mac: "00:1A:79:AB:CD:EF", device: "Box zzuserzz@example.invalid", tag: "UtvPlay" }));
  await ins("event", BEFORE - 4000, old({ message: "code=403http://fournisseur.invalid/ZZUSERZZ/ZZPASSZZ/1.ts", tag: "token=ZZTOKENZZ" }));
  await ins("crash", BEFORE - 3000, old({ stack: "java.io.IOException: http://fournisseur.invalid/ZZUSERZZ\"x/ZZPASSZZ/1.ts\n\tat x.Y.z(Y.kt:1)" }));
  await ins("event", BEFORE - 2000, { message: "Player error 404 on channel 12", tag: "UtvPlay" });
  await ins("event", BEFORE - 1900, old({ message: "connexion refusée password=ZZPASSZZ ZZFINZZ code=401", tag: "UtvPlay" }));
  await ins("event", BEFORE - 1800, old({ message: "UtvPlay échec http://fournisseur.invalid:8080/get.php?username=ZZUSERZZ ZZFINZZ&password=ZZPASSZZ&type=m3u vidéo=- audio=-", tag: "UtvPlay" }));
  await ins("event", BEFORE - 1700, old({ message: "user=ZZUSERZZ ZZFINZZ&password=ZZPASSZZ ZZFINZZ ZZFIN2ZZ ZZFIN3ZZ ZZFIN4ZZ", tag: "UtvPlay" }));
  await ins("crash", BEFORE - 1600, old({ stack: "java.io.IOException: authorization=Basic WlpVU0VSWlo6WlpQQVNTWlo= {\"url\":\"http://ZZUSERZZ:ZZPASSZZ@fournisseur.invalid:8080/list.m3u\"}\n\tat x.Y.z(Y.kt:2)" }));
  // Ligne postérieure au déploiement : jamais relue.
  await ins("event", BEFORE + 1000, { message: "après password=ZZPASSZZ", tag: "UtvPlay" });
}
const SECRETS_OLD = [...SECRETS, "WlpVU0VSWlo6WlpQQVNTWlo"];
// Contrôle indépendant du nettoyage : lignes où un `<redacted>` reste suivi d'une fin d'identifiant.
const residueRows = (list) => list.filter((r) => ["message", "stack", "tag", "device", "version"].some((f) => hasResidue(r[f]))).length;
const rows = async (v) => (await env.DB.prepare("SELECT * FROM events WHERE version = ? ORDER BY id").bind(v).all()).results;

describe("réécriture des anciennes lignes (resanitize-events.mjs)", () => {
  it("refuse de tourner sans --before, avec une date invalide ou future ; compte seul par défaut", () => {
    expect(() => parseArgs([])).toThrow(/--before/);
    expect(() => parseArgs(["--apply"])).toThrow(/--before/);
    expect(() => parseArgs(["--before", "hier"])).toThrow(/ISO/);
    expect(() => parseArgs(["--before", "2099-01-01T00:00:00Z"], Date.parse("2026-11-21T00:00:00Z"))).toThrow(/futur/);
    expect(() => parseArgs(["--before", "2026-11-01", "--batch", "500"])).toThrow(/batch/);
    const o = parseArgs(["--before", "2026-11-01T00:00:00Z"]);
    expect(o).toMatchObject({ apply: false, batch: 50, afterId: 0, beforeMs: BEFORE });
    expect(parseArgs(["--before", "2026-11-01", "--apply"]).apply).toBe(true);
  });

  it("sans --apply : compte seulement, aucune écriture ; avec --apply : lignes antérieures nettoyées, mac vidée", async () => {
    await seed("7.0.1");
    const before = await rows("7.0.1");
    // Les lignes de départ ont bien la forme écrite par la production (sinon ce test ne prouverait rien).
    expect(residueRows(before)).toBeGreaterThanOrEqual(4);
    expect(before.find((r) => r.ts === BEFORE - 1900).message).toBe("connexion refusée password=<redacted> ZZFINZZ code=401");
    const logs = [];
    const dry = await resanitize(env.DB, { beforeMs: BEFORE, apply: false, batch: 2, hostKey: KEY, log: (m) => logs.push(m) });
    expect(await rows("7.0.1")).toEqual(before);
    expect(dry.written).toBe(0);
    expect(dry.changed).toBeGreaterThanOrEqual(7);
    expect(dry.residue).toEqual({ before: residueRows(before), after: 0 });

    const done = await resanitize(env.DB, { beforeMs: BEFORE, apply: true, batch: 2, hostKey: KEY, log: (m) => logs.push(m) });
    expect(done.written).toBe(done.changed);
    expect(done.changed).toBe(dry.changed);
    const after = await rows("7.0.1");
    const old = after.filter((r) => r.ts < BEFORE);
    const dump = JSON.stringify(old).toLowerCase();
    for (const s of SECRETS_OLD) expect(dump, s).not.toContain(s.toLowerCase());
    for (const r of old) expect(r.mac).toBeNull();
    // Critère indépendant (et non `changed: 0`) : plus aucun `<redacted>` suivi d'une fin d'identifiant.
    expect(residueRows(old)).toBe(0);
    expect((await resanitize(env.DB, { beforeMs: BEFORE, hostKey: KEY })).residue).toEqual({ before: 0, after: 0 });
    expect(old[0].message).toMatch(/^http:\/\/<host#[0-9a-f]{12}>\/player_api\.php\?\*$/);
    expect(old[2].stack).toContain("Y.kt:1");
    expect(old[3].message).toBe("Player error 404 on channel 12");
    expect(old[4].message).toBe("connexion refusée password=<redacted> code=401");
    expect(old[5].message).toMatch(/^UtvPlay échec http:\/\/<host#[0-9a-f]{12}>\/get\.php\?\* vidéo=- audio=-$/);
    // La ligne postérieure au déploiement n'est pas touchée.
    expect(after.find((r) => r.ts > BEFORE).message).toBe("après password=ZZPASSZZ");
    // Les journaux du script ne contiennent que des compteurs.
    const logDump = logs.join("\n").toLowerCase();
    for (const s of SECRETS) expect(logDump).not.toContain(s.toLowerCase());
  });

  it("comptage indépendant : reconnaît la fin d'un identifiant après <redacted>, pas un champ ni une marque seule", () => {
    expect(hasResidue("password=<redacted> ZZFINZZ code=401")).toBe(true);
    expect(hasResidue("http://<host>/get.php?*<redacted>&password=<redacted> ZZFINZZ&type=m3u")).toBe(true);
    expect(hasResidue("username=<redacted> ZZFINZZ&password=<redacted>")).toBe(true);
    expect(hasResidue("authorization=<redacted> WlpVU0VSWlo6WlpQQVNTWlo= status=401")).toBe(true);
    expect(hasResidue("password=<redacted> code=401")).toBe(false);
    expect(hasResidue("user=<redacted> status=401 (refusé)")).toBe(false);
    expect(hasResidue("authorization=Basic <redacted> status=401")).toBe(false);
    expect(hasResidue("password=<redacted>")).toBe(false);
    expect(hasResidue(null)).toBe(false);
  });

  it("idempotent et relançable : une seconde passe ne change rien ; reprise après un id", async () => {
    await seed("7.0.2");
    await resanitize(env.DB, { beforeMs: BEFORE, apply: true, hostKey: KEY });
    const snap = await rows("7.0.2");
    const again = await resanitize(env.DB, { beforeMs: BEFORE, apply: true, hostKey: KEY });
    expect(again.changed).toBe(0);
    expect(await rows("7.0.2")).toEqual(snap);
    const lastId = snap[snap.length - 1].id;
    expect((await resanitize(env.DB, { beforeMs: BEFORE, afterId: lastId, hostKey: KEY })).scanned).toBe(0);
  });

  it("une ligne propre n'est pas réécrite ; sans clé, les hôtes deviennent <host>", () => {
    expect(resanitizeRow({ message: "Player error 404", tag: "UtvPlay", stack: null, device: "Box", version: "1.0", mac: null }, KEY).changed).toBe(false);
    const r = resanitizeRow({ message: "http://fournisseur.invalid/a/b/1.ts", tag: null, mac: null }, null);
    expect(r.values.message).toBe("http://<host>/*.ts");
    expect(r.fields).toEqual(["message"]);
  });
});
