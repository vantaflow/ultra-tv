import { env } from "cloudflare:workers";
import { describe, it, expect } from "vitest";
import worker from "../src/index.js";
import { freshIp, ORIGIN } from "./helpers.js";

const req = (path, init = {}) => new Request(ORIGIN + path, { ...init, headers: { "cf-connecting-ip": freshIp(), ...(init.headers || {}) } });

describe("échec fermé si un secret manque ou est faible", () => {
  it("SESSION_SECRET_absent_500_generique", async () => {
    const r = await worker.fetch(req("/"), { ...env, SESSION_SECRET: undefined });
    expect(r.status).toBe(500);
    expect(await r.text()).not.toContain("SESSION_SECRET");
  });
  it("SESSION_SECRET_tropCourt_500", async () => {
    const r = await worker.fetch(req("/"), { ...env, SESSION_SECRET: "court" });
    expect(r.status).toBe(500);
  });
  it("OPS_TOKEN_absent_logs_500_pasDeRepliSurUnMotDePasseParDefaut", async () => {
    const r = await worker.fetch(req("/logs", { headers: { authorization: "Basic " + btoa("ops:") } }), { ...env, OPS_TOKEN: undefined });
    expect(r.status).toBe(500);
  });
  it("PROVIDER_ENC_KEY_absente_ajoutDeFournisseurImpossible", async () => {
    const r = await worker.fetch(req("/signup", { method: "POST", headers: { origin: ORIGIN, "content-type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ login: "nokey-user", password: "correct-horse-battery", confirm: "correct-horse-battery" }).toString() }), { ...env, PROVIDER_ENC_KEY: undefined });
    expect(r.status).toBe(500);
  });
  it("ADMIN_TOKEN_absent_migration_404", async () => {
    const r = await worker.fetch(req("/api/admin/migrate", { method: "POST" }), { ...env, ADMIN_TOKEN: undefined });
    expect(r.status).toBe(404);
  });
});

describe("corps de requête", () => {
  it("POST_login_contentTypeInattendu_415", async () => {
    const r = await worker.fetch(req("/login", { method: "POST", headers: { origin: ORIGIN, "content-type": "text/plain" }, body: "x" }), env);
    expect(r.status).toBe(415);
  });
  it("POST_login_corpsGeant_413", async () => {
    const r = await worker.fetch(req("/login", { method: "POST", headers: { origin: ORIGIN, "content-type": "application/x-www-form-urlencoded" }, body: "login=" + "a".repeat(100_000) }), env);
    expect(r.status).toBe(413);
  });
});
