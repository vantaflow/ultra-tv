import { describe, it, expect } from "vitest";
import { call, newAccount, ORIGIN, freshIp } from "./helpers.js";
import { parsePairQr } from "../src/qr.js";

describe("lecteur de QR : /assets/jsqr.js", () => {
  it("jsqr_servi_avecTypeEtCacheImmuable", async () => {
    const res = await call("/assets/jsqr.js");
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toMatch(/^text\/javascript/);
    expect(res.headers.get("cache-control")).toBe("public, max-age=31536000, immutable");
    expect(res.headers.get("x-content-type-options")).toBe("nosniff");
    const body = await res.text();
    expect(body.startsWith("/*!")).toBe(true);
    expect(body.slice(0, 1500)).toContain("Apache");
    expect(body).toContain("jsQR");
  });
  it("jsqr_post_refuse", async () => {
    expect((await call("/assets/jsqr.js", { method: "POST" })).status).toBe(404);
  });
});

describe("Permissions-Policy", () => {
  it("pagesHtmlDuTableauDeBord_cameraSelf", async () => {
    const acct = await newAccount();
    for (const path of ["/", "/pair?code=ABCDEFGH"]) {
      const res = await call(path, { cookie: acct.cookie, ip: acct.ip });
      expect(res.status).toBe(200);
      const pp = res.headers.get("permissions-policy");
      expect(pp).toBe("camera=(self), microphone=(), geolocation=(), payment=(), usb=()");
    }
  });
  it("apiJson_cameraVide", async () => {
    const res = await call("/api/pair/start", { method: "POST", ip: freshIp(), json: { label: "02:11:22:33:44:55" } });
    expect(res.status).toBe(200);
    expect(res.headers.get("permissions-policy")).toBe("camera=(), microphone=(), geolocation=(), payment=(), usb=()");
    const bad = await call("/api/config");
    expect(bad.headers.get("permissions-policy")).toContain("camera=()");
  });
  it("pageConnexion_cameraVide", async () => {
    const res = await call("/login");
    expect(res.headers.get("permissions-policy")).toContain("camera=()");
  });
});

describe("bouton « Scanner le QR de la TV »", () => {
  it("pageContientBoutonEtScriptAvecNonce", async () => {
    const acct = await newAccount();
    for (const path of ["/", "/pair"]) {
      const res = await call(path, { cookie: acct.cookie, ip: acct.ip });
      const html = await res.text();
      const n = res.headers.get("content-security-policy").match(/script-src 'nonce-([^']+)'/)[1];
      expect(html).toContain('id="scan-btn"');
      expect(html).toContain("Scanner le QR de la TV");
      expect(html).toContain('id="scanner"');
      expect(html).toContain("getUserMedia");
      expect(html).toContain("environment");
      expect(html).toContain("/assets/jsqr.js");
      expect(html).toContain(`<script nonce="${n}">`);
      // le bouton est masqué tant que le script n'a pas confirmé que la caméra existe
      expect(html).toMatch(/id="scan-btn"[^>]*hidden/);
      // aucun gestionnaire en ligne, jsQR jamais inclus dans la page
      expect(html).not.toMatch(/\son[a-z]+=/);
      // le parseur embarqué doit tourner tel quel dans le navigateur (pas de helper de bundler)
      expect(html).not.toContain("__name");
      expect(html).toContain("function parsePairQr(");
      expect(html).not.toContain("webpackUniversalModuleDefinition");
      expect(res.headers.get("content-security-policy")).not.toContain("unsafe-inline");
    }
  });
});

describe("parsePairQr", () => {
  it("urlMemeHote_renvoieLeCode", () => {
    expect(parsePairQr(`${ORIGIN}/pair?code=ABCDEFGH`, ORIGIN)).toBe("ABCDEFGH");
    expect(parsePairQr(`${ORIGIN}/pair?code=abcd-efgh&x=1`, ORIGIN)).toBe("ABCDEFGH");
  });
  it("autreHote_rejete", () => {
    expect(parsePairQr("https://evil.example/pair?code=ABCDEFGH", ORIGIN)).toBeNull();
    expect(parsePairQr("http://config.test/pair?code=ABCDEFGH", ORIGIN)).toBeNull();
    expect(parsePairQr("https://config.test.evil.example/pair?code=ABCDEFGH", ORIGIN)).toBeNull();
    expect(parsePairQr("https://user@evil.example/pair?code=ABCDEFGH", ORIGIN)).toBeNull();
  });
  it("memeHoteAutreChemin_rejete", () => {
    expect(parsePairQr(`${ORIGIN}/login?code=ABCDEFGH`, ORIGIN)).toBeNull();
    expect(parsePairQr(`${ORIGIN}/pair`, ORIGIN)).toBeNull();
  });
  it("codeBrut_accepte", () => {
    expect(parsePairQr("ABCDEFGH", ORIGIN)).toBe("ABCDEFGH");
    expect(parsePairQr("  ABCD-EFGH \n", ORIGIN)).toBe("ABCDEFGH");
  });
  it("minusculesEtTiret_normalises", () => {
    expect(parsePairQr("abcd-efgh", ORIGIN)).toBe("ABCDEFGH");
    expect(parsePairQr("ab2d3fgh", ORIGIN)).toBe("AB2D3FGH");
  });
  it("codeInvalide_rejete", () => {
    for (const bad of ["", "ABC", "ABCDEFG", "ABCDEFGHJ", "ABCDEFG0", "ABCDEFGI", "ABCDEFGO", "ABCDEFG1", "ABCD EFGH", "ABCD_EFGH", "hello world", "WIFI:S:x;;", null, undefined]) {
      expect(parsePairQr(bad, ORIGIN)).toBeNull();
    }
    expect(parsePairQr(`${ORIGIN}/pair?code=ABCDEFG0`, ORIGIN)).toBeNull();
    expect(parsePairQr("javascript:alert(1)", ORIGIN)).toBeNull();
    expect(parsePairQr("A".repeat(5000), ORIGIN)).toBeNull();
  });
});

describe("scripts du tableau de bord", () => {
  it("scriptsInline_syntaxeValide_etImportImagePresent", async () => {
    const acct = await newAccount();
    for (const path of ["/", "/pair?code=ABCDEFGH"]) {
      const html = await (await call(path, { cookie: acct.cookie, ip: acct.ip })).text();
      const scripts = [...html.matchAll(/<script nonce="[^"]+">([\s\S]*?)<\/script>/g)].map((m) => m[1]);
      expect(scripts.some((s) => s.includes("scan-file"))).toBe(true);
      // Une erreur de syntaxe (échappement perdu dans un gabarit) cassait silencieusement scan + import.
      for (const s of scripts) expect(() => new Function(s)).not.toThrow();
      expect(html).toContain('id="scan-file"');
      expect(html).toContain('accept="image/*"');
    }
  });
});
