import { describe, it, expect } from "vitest";
import { pickDesktopAsset, downloadRedirect, ANDROID_URL, ANDROID_URLS } from "../src/download.js";

const releases = [
  { tag_name: "v1.2.86", assets: [{ name: "UltraTV.apk", browser_download_url: "https://x.test/apk" }] },
  { tag_name: "desktop-v1.2.40", draft: true, assets: [{ name: "UltraTV-1.2.40-win-x64.exe", browser_download_url: "https://x.test/draft" }] },
  { tag_name: "desktop-v1.2.39", assets: [
    { name: "UltraTV-1.2.39-win-x64.exe", browser_download_url: "https://x.test/win" },
    { name: "UltraTV-1.2.39-win-x64.exe.blockmap", browser_download_url: "https://x.test/map" },
    { name: "UltraTV-1.2.39-mac-universal.dmg", browser_download_url: "https://x.test/mac" },
    { name: "UltraTV-1.2.39-linux-x86_64.AppImage", browser_download_url: "https://x.test/linux" },
  ] },
];

describe("liens de téléchargement", () => {
  it("dernière release bureau publiée, fichier de la plateforme", () => {
    expect(pickDesktopAsset(releases, "windows")).toBe("https://x.test/win");
    expect(pickDesktopAsset(releases, "mac")).toBe("https://x.test/mac");
    expect(pickDesktopAsset(releases, "linux")).toBe("https://x.test/linux");
    expect(pickDesktopAsset(releases, "ios")).toBeNull();
  });
  it("Android : /download/android = APK arm64-v8a de la dernière release", async () => {
    const r = await downloadRedirect("android");
    expect(r.status).toBe(302);
    expect(r.headers.get("location")).toBe(ANDROID_URL);
    expect(ANDROID_URL).toMatch(/\/releases\/latest\/download\/UltraTV-arm64-v8a\.apk$/);
  });
  it("Android : routes arm64, armv7 (32 bits) et universel", async () => {
    const dest = async (p) => (await downloadRedirect(p)).headers.get("location");
    expect(await dest("android-arm64")).toMatch(/\/latest\/download\/UltraTV-arm64-v8a\.apk$/);
    expect(await dest("android-32")).toMatch(/\/latest\/download\/UltraTV-armeabi-v7a\.apk$/);
    expect(await dest("android-armv7")).toMatch(/\/latest\/download\/UltraTV-armeabi-v7a\.apk$/);
    expect(await dest("android-universal")).toMatch(/\/latest\/download\/UltraTV\.apk$/);
    expect(Object.keys(ANDROID_URLS)).toHaveLength(5);
  });
  it("bureau : redirection, repli sur la page des releases si GitHub échoue", async () => {
    const ok = await downloadRedirect("windows", async () => new Response(JSON.stringify(releases), { status: 200 }));
    expect(ok.headers.get("location")).toBe("https://x.test/win");
    const ko = await downloadRedirect("mac", async () => { throw new Error("réseau"); });
    expect(ko.headers.get("location")).toContain("/releases?q=desktop-v");
  });
});
