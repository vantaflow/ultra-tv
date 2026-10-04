// Chiffrement des identifiants des sources.
//  - Electron : safeStorage (trousseau du système) via le pont `window.ultratv`.
//  - Navigateur : AES-GCM avec une clé WebCrypto NON EXTRACTIBLE gardée dans IndexedDB
//    (protège contre la lecture brute du disque, pas contre un script de la même origine).
// Format : "enc:v1:..." (Electron), "wc:v1:iv.données" (navigateur). Une valeur en clair reste lisible.

import { getSetting, setSetting } from "@/db/db";
import { bridge } from "./transport";

const KEY_ID = "secrets.key";
let keyPromise: Promise<CryptoKey> | null = null;

function webKey(): Promise<CryptoKey> {
  keyPromise ??= (async () => {
    const existing = await getSetting<CryptoKey | null>(KEY_ID, null);
    if (existing) return existing;
    const k = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
    await setSetting(KEY_ID, k);
    return k;
  })();
  return keyPromise;
}

const toB64 = (b: Uint8Array) => btoa(String.fromCharCode(...b));
const fromB64 = (s: string) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));

export async function encryptSecret(plain: string): Promise<string> {
  if (!plain) return "";
  const b = bridge();
  if (b) return b.encrypt(plain);
  try {
    const key = await webKey();
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const data = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(plain)));
    return `wc:v1:${toB64(iv)}.${toB64(data)}`;
  } catch {
    return plain;
  }
}

export async function decryptSecret(value: string): Promise<string> {
  if (!value) return "";
  if (value.startsWith("enc:v1:") || value.startsWith("plain:v1:")) {
    const b = bridge();
    return b ? b.decrypt(value) : "";
  }
  if (value.startsWith("wc:v1:")) {
    try {
      const [iv, data] = value.slice(6).split(".");
      const key = await webKey();
      const out = await crypto.subtle.decrypt({ name: "AES-GCM", iv: fromB64(iv!) }, key, fromB64(data!));
      return new TextDecoder().decode(out);
    } catch {
      return "";
    }
  }
  return value;
}
