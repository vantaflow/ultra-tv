import { describe, it, expect } from "vitest";
import { donatePage } from "../src/pages.js";
import { call, freshIp } from "./helpers.js";

describe("dons : pages", () => {
  it("page traduite avec Ko-fi et Liberapay, sans formulaire ni passerelle de paiement", async () => {
    for (const lang of ["fr", "en", "es", "ar", "pl"]) {
      const h = await donatePage("n1", { lang }).text();
      expect(h).toContain('href="https://ko-fi.com/ultratv"');
      expect(h).toContain('href="https://liberapay.com/ultratv/"');
      expect(h).not.toContain("<form");
      expect(h.toLowerCase()).not.toContain("chari" + "pay");
    }
    expect(await donatePage("n1", { lang: "fr" }).text()).toContain("Soutenir Ultra TV");
    expect(await donatePage("n1", { lang: "ar" }).text()).toContain('dir="rtl"');
  });
  it("route : GET /donate = 200 ; /donate/merci redirige vers /donate ; POST non géré", async () => {
    const g = await call("/donate?l=en", { ip: freshIp() });
    expect(g.status).toBe(200);
    expect(await g.text()).toContain("Donate on Ko-fi");
    const old = await call("/donate/merci?l=es", { ip: freshIp() });
    expect(old.status).toBe(302);
    expect(old.headers.get("location")).toBe("/donate");
    expect((await call("/donate", { method: "POST", ip: freshIp(), form: { amount: "50" } })).status).not.toBe(303);
  });
});
