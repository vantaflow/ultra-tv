"use strict";
const test = require("node:test");
const assert = require("node:assert");
const { pickDesktopTag } = require("../updates.cjs");

test("prend la release de bureau publiée la plus récente, pas la release Android « Latest »", () => {
  const rel = [
    { tag_name: "v1.2.22" },
    { tag_name: "desktop-v1.2.17", draft: true },
    { tag_name: "desktop-v1.2.9" },
    { tag_name: "desktop-v1.2.16" },
    { tag_name: "desktop-v1.3.0-beta", prerelease: true },
  ];
  assert.strictEqual(pickDesktopTag(rel), "desktop-v1.2.16");
});

test("aucune release de bureau : null", () => {
  assert.strictEqual(pickDesktopTag([{ tag_name: "v1.2.22" }]), null);
  assert.strictEqual(pickDesktopTag(null), null);
});

test("compareVersions : ordre numérique, pas alphabétique", () => {
  const { compareVersions } = require("../updates.cjs");
  assert.ok(compareVersions("1.2.26", "1.2.9") > 0);
  assert.ok(compareVersions("1.2.9", "1.2.26") < 0);
  assert.strictEqual(compareVersions("1.2.26", "1.2.26"), 0);
  assert.ok(compareVersions("1.3.0", "1.2.99") > 0);
});

test("détection d'une installation .deb/.rpm : Linux sans APPIMAGE", () => {
  const { isLinuxPackageInstall } = require("../updates.cjs");
  assert.strictEqual(isLinuxPackageInstall("linux", {}), true);
  assert.strictEqual(isLinuxPackageInstall("linux", { APPIMAGE: "/home/u/UltraTV.AppImage" }), false);
  assert.strictEqual(isLinuxPackageInstall("win32", {}), false);
  assert.strictEqual(isLinuxPackageInstall("darwin", {}), false);
});

test("famille de paquet et lien de téléchargement", () => {
  const { linuxPackageKind, manualUpdateUrl } = require("../updates.cjs");
  assert.strictEqual(linuxPackageKind((f) => f === "/etc/debian_version"), "deb");
  assert.strictEqual(linuxPackageKind((f) => f === "/etc/fedora-release"), "rpm");
  assert.strictEqual(linuxPackageKind(() => false), null);
  assert.strictEqual(manualUpdateUrl("deb", "desktop-v1.3.1"), "https://tv.weedy.dpdns.org/download/linux-deb");
  assert.strictEqual(manualUpdateUrl("rpm", "desktop-v1.3.1"), "https://tv.weedy.dpdns.org/download/linux-rpm");
  assert.strictEqual(manualUpdateUrl(null, "desktop-v1.3.1"), "https://github.com/vantaflow/ultra-tv/releases/tag/desktop-v1.3.1");
});
