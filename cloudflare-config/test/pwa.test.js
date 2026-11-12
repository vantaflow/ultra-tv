import { describe, expect, it } from "vitest";
import { call } from "./helpers.js";
import { MANIFEST, SERVICE_WORKER } from "../src/pwa.js";

describe("application web installable (PWA)", () => {
  it("manifeste : installable (nom, démarrage, plein écran, icônes 192 et 512, maskable)", async () => {
    const r = await call("/manifest.webmanifest");
    expect(r.status).toBe(200);
    expect(r.headers.get("content-type")).toContain("application/manifest+json");
    const m = await r.json();
    expect(m.name).toBe("Ultra TV");
    expect(m.display).toBe("standalone");
    expect(m.start_url.startsWith("/")).toBe(true);
    const sizes = m.icons.map((i) => i.sizes);
    expect(sizes).toContain("192x192");
    expect(sizes).toContain("512x512");
    expect(m.icons.some((i) => i.purpose === "maskable")).toBe(true);
  });

  it("icônes PNG servies", async () => {
    for (const p of ["/pwa/icon-192.png", "/pwa/icon-512.png", "/apple-touch-icon.png"]) {
      const r = await call(p);
      expect(r.status).toBe(200);
      expect(r.headers.get("content-type")).toBe("image/png");
      const b = new Uint8Array(await r.arrayBuffer());
      expect([...b.slice(1, 4)].map((c) => String.fromCharCode(c)).join("")).toBe("PNG");
    }
  });

  it("service worker : JavaScript, toujours revalidé, ne met jamais une page en cache", async () => {
    const r = await call("/sw.js");
    expect(r.status).toBe(200);
    expect(r.headers.get("content-type")).toContain("javascript");
    expect(r.headers.get("cache-control")).toBe("no-cache");
    // Les navigations passent par le réseau ; seul le repli hors ligne vient du cache.
    expect(SERVICE_WORKER).toContain('req.mode === "navigate"');
    expect(SERVICE_WORKER).not.toMatch(/cache\.put/);
  });

  it("page hors ligne sans donnée de compte", async () => {
    const r = await call("/offline");
    expect(r.status).toBe(200);
    expect(await r.text()).toContain("Pas de connexion");
  });

  it("toutes les pages déclarent le manifeste ; la CSP autorise manifeste et service worker", async () => {
    const r = await call("/login");
    const h = await r.text();
    expect(h).toContain('rel="manifest" href="/manifest.webmanifest"');
    expect(h).toContain('serviceWorker.register("/sw.js")');
    const csp = r.headers.get("content-security-policy");
    expect(csp).toContain("manifest-src 'self'");
    expect(csp).toContain("worker-src 'self'");
  });

  it("raccourcis du manifeste vers des pages existantes", () => {
    expect(MANIFEST.shortcuts.map((s) => s.url)).toEqual(["/pair", "/envoyer"]);
  });
});
