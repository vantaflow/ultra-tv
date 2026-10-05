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
