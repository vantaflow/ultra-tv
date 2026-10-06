import { describe, expect, it } from "vitest";
import { parseDeviceProvider, syncProvider, verifyProLicense } from "../src/store.js";

const b64u = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
async function keys() {
  const kp = await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"]);
  const spki = btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.exportKey("spki", kp.publicKey))));
  return { kp, spki };
}
async function signed(kp, obj) {
  const payload = b64u(new TextEncoder().encode(JSON.stringify(obj)));
  const sig = b64u(await crypto.subtle.sign({ name: "Ed25519" }, kp.privateKey, new TextEncoder().encode(payload)));
  return { payload, sig };
}
const NOW = 1_800_000_000_000;
const LIC = { v: 1, deviceId: "d", code: "7F3K-92QD", status: "active", until: NOW + 30 * 86_400_000, reseller: { name: "Rabat Digital", whatsapp: "+212600000000", telegram: "rabatdigital", text: "x" }, devices: { used: 1, max: 3 }, unread: 0, issuedAt: NOW - 3_600_000 };

describe("verifyProLicense", () => {
  it("licence signée et récente : champs affichés seulement", async () => {
    const { kp, spki } = await keys();
    const { payload, sig } = await signed(kp, LIC);
    expect(await verifyProLicense(payload, sig, NOW, spki)).toEqual({
      status: "active", until: LIC.until, code: "7F3K-92QD",
      reseller: { name: "Rabat Digital", whatsapp: "+212600000000", telegram: "rabatdigital" },
      devices: { used: 1, max: 3 }, issuedAt: LIC.issuedAt,
    });
  });
  it("charge utile modifiée après signature : refusée", async () => {
    const { kp, spki } = await keys();
    const { sig } = await signed(kp, LIC);
    const forged = b64u(new TextEncoder().encode(JSON.stringify({ ...LIC, until: NOW + 3650 * 86_400_000 })));
    expect(await verifyProLicense(forged, sig, NOW, spki)).toBeNull();
  });
  it("signée par une autre clé : refusée", async () => {
    const a = await keys(); const b = await keys();
    const { payload, sig } = await signed(a.kp, LIC);
    expect(await verifyProLicense(payload, sig, NOW, b.spki)).toBeNull();
  });
  it("statut trop ancien (plus de 8 jours) : refusé", async () => {
    const { kp, spki } = await keys();
    const { payload, sig } = await signed(kp, { ...LIC, issuedAt: NOW - 9 * 86_400_000 });
    expect(await verifyProLicense(payload, sig, NOW, spki)).toBeNull();
  });
  it("entrées invalides : null sans exception", async () => {
    expect(await verifyProLicense(undefined, "x", NOW)).toBeNull();
    expect(await verifyProLicense("abc", "def", NOW)).toBeNull();
  });
});

describe("sources posées par le revendeur", () => {
  const base = { kind: "XTREAM", name: "Pack", url: "http://iptv.example.test:8080", username: "u", password: "p" };
  it("managed=reseller accepté et renvoyé aux appareils", () => {
    const r = parseDeviceProvider({ ...base, managed: "reseller" }, { deviceId: "d1", name: "TV" });
    expect(r.provider.managed).toBe("reseller");
    expect(syncProvider({ ...r.provider, id: "a1" }).managed).toBe("reseller");
  });
  it("toute autre valeur ignorée", () => {
    const r = parseDeviceProvider({ ...base, managed: "admin" }, { deviceId: "d1", name: "TV" });
    expect(r.provider.managed).toBeUndefined();
    expect(syncProvider({ ...r.provider, id: "a1" }).managed).toBeNull();
  });
});
