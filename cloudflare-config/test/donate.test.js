import { describe, it, expect } from "vitest";
import { donatePage } from "../src/pages.js";
import { paypalClientId, donateCsp } from "../src/donate.js";
import { call, freshIp } from "./helpers.js";

const CID = "AWvl-5adHUdHtB1J4LRwn_testClientId0123456789";

describe("dons : PayPal seul", () => {
  it("sans PAYPAL_CLIENT_ID : lien de paiement PayPal seul, traduit, sans Ko-fi, Liberapay, formulaire ni script PayPal", async () => {
    for (const lang of ["fr", "en", "es", "ar", "pl"]) {
      const r = donatePage("n1", { lang });
      const h = await r.text();
      expect(h).toContain('href="https://www.paypal.com/ncp/payment/JHPS8RCN35UWA"');
      expect(h).not.toContain("ko-fi.com");
      expect(h).not.toContain("liberapay.com");
      expect(h).not.toContain("<form");
      expect(h).not.toContain("paypal.com/sdk");
      expect(r.headers.get("content-security-policy")).not.toContain("paypal");
    }
    expect(await donatePage("n1", { lang: "fr" }).text()).toContain("Soutenir Ultra TV");
    expect(await donatePage("n1", { lang: "ar" }).text()).toContain('dir="rtl"');
  });
  it("avec PAYPAL_CLIENT_ID : bouton hébergé PayPal (nonce), lien de paiement de secours, CSP élargie à PayPal seulement", async () => {
    const r = donatePage("n1", { lang: "en", clientId: CID });
    const h = await r.text();
    expect(h).toContain(`https://www.paypal.com/sdk/js?client-id=${CID}&components=hosted-buttons&disable-funding=venmo&currency=EUR`);
    expect(h).toContain('hostedButtonId:"JHPS8RCN35UWA"');
    expect(h).toContain('data-csp-nonce="n1"');
    expect(h).toContain('id="pp-JHPS8RCN35UWA"');
    expect(h).toContain('href="https://www.paypal.com/ncp/payment/JHPS8RCN35UWA"');
    const csp = r.headers.get("content-security-policy");
    expect(csp).toContain("script-src 'nonce-n1' https://www.paypal.com");
    expect(csp).toContain("frame-src https://*.paypal.com");
    expect(csp).toContain("frame-ancestors 'none'");
  });
  it("identifiant client validé (rien d'injecté si invalide)", () => {
    expect(paypalClientId({ PAYPAL_CLIENT_ID: CID })).toBe(CID);
    expect(paypalClientId({})).toBeNull();
    expect(paypalClientId({ PAYPAL_CLIENT_ID: 'x"><script>' })).toBeNull();
    expect(donateCsp("z")).toContain("'nonce-z'");
  });
  it("route : GET /donate = 200 ; /donate/merci redirige vers /donate ; POST non géré", async () => {
    const g = await call("/donate?l=en", { ip: freshIp() });
    expect(g.status).toBe(200);
    expect(await g.text()).toContain("paypal.com/ncp/payment/JHPS8RCN35UWA");
    const old = await call("/donate/merci?l=es", { ip: freshIp() });
    expect(old.status).toBe(302);
    expect(old.headers.get("location")).toBe("/donate");
    expect((await call("/donate", { method: "POST", ip: freshIp(), form: { amount: "50" } })).status).not.toBe(303);
  });
});
