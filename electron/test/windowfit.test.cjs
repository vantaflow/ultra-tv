"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { computeWindowFit } = require("../windowfit.cjs");

// Verifie qu'une fenetre tient entierement dans la zone de travail.
function inside(b, wa) {
  return b.x >= wa.x && b.y >= wa.y && b.x + b.width <= wa.x + wa.width && b.y + b.height <= wa.y + wa.height;
}

test("1366x768 a 125 % : la fenetre tient dans ~1093x582 DIP", () => {
  const wa = { x: 0, y: 0, width: 1093, height: 582 };
  const r = computeWindowFit(wa, 1.25, null);
  assert.ok(inside(r.bounds, wa));
  assert.equal(r.bounds.height, 582 - 8 * 2);
  assert.equal(r.minHeight, 540);
  assert.ok(r.bounds.height >= r.minHeight);
  assert.equal(r.bounds.width, 1093 - 8 * 2);
});

test("1366x768 a 150 % : minHeight = hauteur disponible, jamais plus haut que l'ecran", () => {
  const wa = { x: 0, y: 0, width: 910, height: 485 };
  const r = computeWindowFit(wa, 1.5, null);
  assert.ok(inside(r.bounds, wa));
  assert.equal(r.minHeight, 485);
  assert.equal(r.bounds.height, 485);
  assert.ok(r.minWidth <= wa.width);
  assert.ok(r.bounds.width <= wa.width);
});

test("1920x1080 a 100 % : taille par defaut 1280x800, centree", () => {
  const wa = { x: 0, y: 0, width: 1920, height: 1040 };
  const r = computeWindowFit(wa, 1, null);
  assert.deepEqual(r.bounds, { x: 320, y: 120, width: 1280, height: 800 });
  assert.equal(r.minWidth, 900);
  assert.equal(r.minHeight, 540);
});

test("ecran secondaire decale : centrage relatif a la zone de travail", () => {
  const wa = { x: 1920, y: 0, width: 1920, height: 1040 };
  const r = computeWindowFit(wa, 1, null);
  assert.equal(r.bounds.x, 1920 + 320);
  assert.ok(inside(r.bounds, wa));
});

test("etat sauvegarde hors ecran : ramene dans la zone de travail", () => {
  const wa = { x: 0, y: 0, width: 1093, height: 582 };
  const r = computeWindowFit(wa, 1.25, { x: 3000, y: -900, width: 1600, height: 1200 });
  assert.ok(inside(r.bounds, wa));
  assert.equal(r.bounds.width, 1093);
  assert.equal(r.bounds.height, 582);
});

test("etat sauvegarde valide et plus petit : conserve", () => {
  const wa = { x: 0, y: 0, width: 1920, height: 1040 };
  const r = computeWindowFit(wa, 1, { x: 100, y: 80, width: 1000, height: 700 });
  assert.deepEqual(r.bounds, { x: 100, y: 80, width: 1000, height: 700 });
});

test("etat sauvegarde sous le minimum : releve au minimum", () => {
  const wa = { x: 0, y: 0, width: 1920, height: 1040 };
  const r = computeWindowFit(wa, 1, { x: 10, y: 10, width: 300, height: 200 });
  assert.equal(r.bounds.width, 900);
  assert.equal(r.bounds.height, 540);
});

test("entrees invalides : repli sans exception", () => {
  const r = computeWindowFit(null, NaN, { x: NaN, width: "a" });
  assert.ok(r.bounds.width > 0 && r.bounds.height > 0);
  assert.ok(r.minHeight <= r.bounds.height);
});
