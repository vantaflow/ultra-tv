import { describe, it, expect } from "vitest";
import { apkPage } from "../src/pages.js";
import { downloadRedirect } from "../src/download.js";

describe("page /apk (choix de l'APK pour Downloader)", () => {
  it("propose 64 bits, 32 bits et universel, 64 bits en premier", async () => {
    const r = apkPage("nonce123");
    expect(r.status).toBe(200);
    const h = await r.text();
    const i64 = h.indexOf('href="/download/android-arm64"');
    const i32 = h.indexOf('href="/download/android-32"');
    const iU = h.indexOf('href="/download/android-universal"');
    expect(i64).toBeGreaterThan(0);
    expect(i32).toBeGreaterThan(i64);
    expect(iU).toBeGreaterThan(i32);
    expect(h).toContain('<html lang="en"');
  });
  it("chaque lien mène bien à un APK de la dernière release", async () => {
    for (const p of ["android-arm64", "android-32", "android-universal"]) {
      expect((await downloadRedirect(p)).headers.get("location")).toMatch(/\/releases\/latest\/download\/UltraTV[-\w]*\.apk$/);
    }
  });
});
