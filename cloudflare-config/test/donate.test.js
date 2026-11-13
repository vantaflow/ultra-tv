import { describe, it, expect } from "vitest";
import { parseDonateAmount, isCharipayUrl, createDonationLink, CHARIPAY_API } from "../src/donate.js";
import { donatePage } from "../src/pages.js";
import { call, freshIp } from "./helpers.js";

describe("dons : montant", () => {
  it("préréglage ou montant libre, bornés", () => {
    expect(parseDonateAmount("50", "")).toBe(50);
    expect(parseDonateAmount("50", "120")).toBe(120);
    expect(parseDonateAmount("", "9")).toBeNull();
    expect(parseDonateAmount("", "5001")).toBeNull();
    expect(parseDonateAmount("", "12.5")).toBeNull();
    expect(parseDonateAmount("", "-20")).toBeNull();
    expect(parseDonateAmount(null, null)).toBeNull();
  });
  it("ne redirige que vers charipay.ma en https", () => {
    expect(isCharipayUrl("https://pay.charipay.ma/l/pl_1")).toBe(true);
    expect(isCharipayUrl("https://charipay.ma/p")).toBe(true);
    expect(isCharipayUrl("http://pay.charipay.ma/x")).toBe(false);
    expect(isCharipayUrl("https://charipay.ma.evil.com/x")).toBe(false);
    expect(isCharipayUrl("javascript:alert(1)")).toBe(false);
  });
});

describe("dons : lien ChariPay", () => {
  it("sans clé : désactivé, aucun appel", async () => {
    let called = false;
    const r = await createDonationLink({}, 50, { origin: "https://x.test", fetchImpl: () => { called = true; } });
    expect(r).toEqual({ error: "disabled" });
    expect(called).toBe(false);
  });
  it("appel conforme à l'API (clé en en-tête, usage unique, retours https)", async () => {
    let seen;
    const fetchImpl = async (u, init) => { seen = { u, init, body: JSON.parse(init.body) }; return new Response(JSON.stringify({ payUrl: "https://pay.charipay.ma/l/pl_9" }), { status: 201 }); };
    const r = await createDonationLink({ CHARIPAY_API_KEY: "chari_sk_test_x" }, 100, { origin: "https://w.test", lang: "fr", fetchImpl });
    expect(r).toEqual({ url: "https://pay.charipay.ma/l/pl_9" });
    expect(seen.u).toBe(`${CHARIPAY_API}/v1/payment-links`);
    expect(seen.init.headers["X-CHARI-PAY-API-KEY"]).toBe("chari_sk_test_x");
    expect(seen.body).toMatchObject({ amount: 100, singleUse: true, acceptUrl: "https://w.test/donate/merci?l=fr" });
    expect(seen.body.externalId).toMatch(/^don_[0-9a-f-]{36}$/);
    expect(seen.init.headers["Idempotency-Key"]).toBe(seen.body.externalId);
  });
  it("refus ou réponse suspecte : erreur, pas de redirection", async () => {
    const env = { CHARIPAY_API_KEY: "k" };
    expect(await createDonationLink(env, 50, { origin: "https://w.test", fetchImpl: async () => new Response("{}", { status: 422 }) })).toEqual({ error: "upstream" });
    expect(await createDonationLink(env, 50, { origin: "https://w.test", fetchImpl: async () => new Response(JSON.stringify({ payUrl: "https://evil.example/" }), { status: 201 }) })).toEqual({ error: "upstream" });
    expect(await createDonationLink(env, 50, { origin: "https://w.test", fetchImpl: async () => { throw new Error("down"); } })).toEqual({ error: "upstream" });
  });
});

describe("dons : pages", () => {
  it("page traduite, montants proposés quand la clé est là", async () => {
    const h = await donatePage("n1", { lang: "fr", enabled: true }).text();
    expect(h).toContain("Soutenir Ultra TV");
    expect(h).toContain('value="50"');
    expect(h).toContain('action="/donate?l=fr"');
    const off = await donatePage("n1", { lang: "ar", enabled: false }).text();
    expect(off).not.toContain("<form");
    expect(off).toContain('dir="rtl"');
  });
  it("route : GET sans clé = page fermée ; POST d'une autre origine refusé ; montant invalide renvoyé", async () => {
    const g = await call("/donate?l=en", { ip: freshIp() });
    expect(g.status).toBe(200);
    expect(await g.text()).toContain("not open yet");
    expect((await call("/donate", { method: "POST", ip: freshIp(), origin: "https://evil.example", form: { amount: "50" } })).status).toBe(403);
    const bad = await call("/donate?l=en", { method: "POST", ip: freshIp(), form: { custom: "3" } });
    expect(bad.headers.get("location")).toBe("/donate?e=invalid&l=en");
    expect((await call("/donate/merci?l=es", { ip: freshIp() })).status).toBe(200);
  });
});
