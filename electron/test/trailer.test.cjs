"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { TRAILER_FRAME_SRC, TRAILER_EMBED_URLS, TRAILER_REFERER, withTrailerReferer } = require("../trailer.cjs");

test("frame-src : uniquement YouTube (nocookie et youtube.com), en https", () => {
  assert.deepEqual(TRAILER_FRAME_SRC, ["https://www.youtube-nocookie.com", "https://www.youtube.com"]);
});

test("les requetes visees sont celles de l'iframe integree", () => {
  for (const u of TRAILER_EMBED_URLS) assert.match(u, /^https:\/\/www\.youtube(-nocookie)?\.com\/embed\/\*$/);
});

test("Referer ajoute quand il manque (page app://)", () => {
  const h = withTrailerReferer({ "User-Agent": "x" });
  assert.equal(h.Referer, TRAILER_REFERER);
  assert.equal(h["User-Agent"], "x");
  assert.match(TRAILER_REFERER, /^https:\/\//);
});

test("Referer existant conserve, quelle que soit la casse", () => {
  assert.equal(withTrailerReferer({ Referer: "http://localhost:5173/" }).Referer, "http://localhost:5173/");
  const h = withTrailerReferer({ referer: "http://localhost:5173/" });
  assert.equal(h.referer, "http://localhost:5173/");
  assert.equal(h.Referer, undefined);
});

test("Referer vide : remplace", () => {
  assert.equal(withTrailerReferer({ Referer: "" }).Referer, TRAILER_REFERER);
});

test("n'altere pas l'objet d'origine", () => {
  const src = { A: "1" };
  withTrailerReferer(src);
  assert.deepEqual(src, { A: "1" });
});
