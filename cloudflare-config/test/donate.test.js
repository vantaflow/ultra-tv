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
    // Dons mensuels : SDK abonnements dans son propre espace de noms, un choix par plan.
    expect(h).toContain('data-namespace="paypalSub"');
    expect(h).toContain("vault=true&intent=subscription");
    for (const id of ["P-0VY777380G390052WNMCJY6A", "P-73H946429U469760TNMCJZXQ", "P-70934607BP2820041NMCJ2RI"]) expect(h).toContain(`value="${id}"`);
    expect(h).toContain('name="pp-mode" value="monthly"');
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
  it("route : GET /donate = 200 ; /donate/merci = page de remerciement ; POST non géré", async () => {
    const g = await call("/donate?l=en", { ip: freshIp() });
    expect(g.status).toBe(200);
    const gh = await g.text();
    expect(gh).toContain("paypal.com/ncp/payment/JHPS8RCN35UWA");
    expect(gh).toContain("/donate/merci?m=1&l=en");
    expect(gh).toContain('href="https://paypal.me/pitivi"');
    const once = await call("/donate/merci?l=es", { ip: freshIp() });
    expect(once.status).toBe(200);
    expect(once.headers.get("x-robots-tag")).toBe("noindex");
    const oh = await once.text();
    expect(oh).toContain("¡Gracias por tu donación!");
    expect(oh).toContain("https://vantaflow.github.io/ultra-tv/");
    expect(oh).toContain('href="/donate?l=es"');
    expect(oh).not.toContain("Tu donación mensual está activa");
    const mon = await (await call("/donate/merci?l=fr&m=1", { ip: freshIp() })).text();
    expect(mon).toContain("Merci pour votre don !");
    expect(mon).toContain("Votre don mensuel est actif");
    expect(mon).toContain("/donate/merci?l=ar&amp;m=1");
    expect((await call("/donate", { method: "POST", ip: freshIp(), form: { amount: "50" } })).status).not.toBe(303);
  });
});
