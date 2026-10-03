import { describe, it, expect } from "vitest";
import {
  encryptJson, decryptJson, hashPassword, verifyPassword, randomToken, sha256Hex, timingSafeEqual,
} from "../src/crypto.js";

const KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
const KEY2 = "HxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo=";

describe("chiffrement AES-GCM des identifiants", () => {
  it("encryptJson_valeurSimple_neContientPasLeClair", async () => {
    const blob = await encryptJson({ KEY }, [{ password: "s3cret-mot-de-passe" }], "alice");
    expect(blob).not.toContain("s3cret");
    expect(blob.startsWith("v1.")).toBe(true);
  });
  it("decryptJson_allerRetour_restitueLaValeur", async () => {
    const data = [{ kind: "XTREAM", username: "u", password: "p" }];
    const blob = await encryptJson({ KEY }, data, "alice");
    expect(await decryptJson({ KEY }, blob, "alice")).toEqual(data);
  });
  it("encryptJson_deuxFois_ivDifferent", async () => {
    const a = await encryptJson({ KEY }, { a: 1 }, "alice");
    const b = await encryptJson({ KEY }, { a: 1 }, "alice");
    expect(a).not.toBe(b);
  });
  it("decryptJson_autreCompte_refuse", async () => {
    const blob = await encryptJson({ KEY }, { a: 1 }, "alice");
    await expect(decryptJson({ KEY }, blob, "bob")).rejects.toThrow();
  });
  it("decryptJson_donneesAlterees_refuse", async () => {
    const blob = await encryptJson({ KEY }, { a: 1 }, "alice");
    const tampered = blob.slice(0, -4) + (blob.endsWith("AAAA") ? "BBBB" : "AAAA");
    await expect(decryptJson({ KEY }, tampered, "alice")).rejects.toThrow();
  });
  it("decryptJson_rotationDeCle_ancienneCleAcceptee", async () => {
    const blob = await encryptJson({ KEY }, { a: 1 }, "alice");
    expect(await decryptJson({ KEY: KEY2, KEY_PREVIOUS: KEY }, blob, "alice")).toEqual({ a: 1 });
  });
  it("encryptJson_cleManquante_refuse", async () => {
    await expect(encryptJson({}, { a: 1 }, "alice")).rejects.toThrow(/PROVIDER_ENC_KEY/);
  });
  it("encryptJson_cleDeMauvaiseTaille_refuse", async () => {
    await expect(encryptJson({ KEY: "c2hvcnQ=" }, { a: 1 }, "alice")).rejects.toThrow(/32/);
  });
});

describe("mot de passe PBKDF2", () => {
  it("hashPassword_format_pbkdf2Sha256Avec100kIterations", async () => {
    const h = await hashPassword("correct horse");
    const [scheme, algo, iters] = h.split("$");
    expect([scheme, algo, Number(iters)]).toEqual(["pbkdf2", "sha256", 100000]);
  });
  it("hashPassword_memeMotDePasse_selDifferent", async () => {
    expect(await hashPassword("abcdefgh12")).not.toBe(await hashPassword("abcdefgh12"));
  });
  it("verifyPassword_bonMotDePasse_ok", async () => {
    const h = await hashPassword("correct horse");
    expect((await verifyPassword({ passwordHash: h }, "correct horse")).ok).toBe(true);
  });
  it("verifyPassword_mauvaisMotDePasse_refuse", async () => {
    const h = await hashPassword("correct horse");
    expect((await verifyPassword({ passwordHash: h }, "wrong")).ok).toBe(false);
  });
  it("verifyPassword_formatHeritePbkdf2_accepteEtDemandeMigration", async () => {
    // ancien format : pbkdf2$<iters>$<hex> sur "<sel>:<mdp>"
    const salt = "00112233445566778899aabbccddeeff";
    const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(`${salt}:pw-legacy-1`), "PBKDF2", false, ["deriveBits"]);
    const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", salt: new TextEncoder().encode(salt), iterations: 100000, hash: "SHA-256" }, key, 256);
    const hex = [...new Uint8Array(bits)].map((b) => b.toString(16).padStart(2, "0")).join("");
    const v = await verifyPassword({ passwordHash: `pbkdf2$100000$${hex}`, salt }, "pw-legacy-1");
    expect(v).toEqual({ ok: true, needsRehash: true });
  });
  it("verifyPassword_formatHeriteSha256_accepteEtDemandeMigration", async () => {
    const salt = "aabbccddeeff00112233445566778899";
    const hash = await sha256Hex(`${salt}:pw-legacy-2`);
    const v = await verifyPassword({ passwordHash: hash, salt }, "pw-legacy-2");
    expect(v).toEqual({ ok: true, needsRehash: true });
    expect((await verifyPassword({ passwordHash: hash, salt }, "nope")).ok).toBe(false);
  });
  it("verifyPassword_compteInconnu_refuseSansErreur", async () => {
    expect((await verifyPassword(null, "x")).ok).toBe(false);
  });
});

describe("primitives", () => {
  it("randomToken_256bits_base64urlUnique", () => {
    const a = randomToken(); const b = randomToken();
    expect(a).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(a).not.toBe(b);
  });
  it("timingSafeEqual_egalesEtDifferentes", () => {
    expect(timingSafeEqual("abc", "abc")).toBe(true);
    expect(timingSafeEqual("abc", "abd")).toBe(false);
    expect(timingSafeEqual("abc", "abcd")).toBe(false);
  });
});
