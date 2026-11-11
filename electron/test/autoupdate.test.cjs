"use strict";
const test = require("node:test");
const assert = require("node:assert");
const { loadAutoUpdate, saveAutoUpdate, scheduleAutoChecks, FIRST_CHECK_MS, EVERY_MS } = require("../autoupdate.cjs");

test("activé par défaut : fichier absent ou invalide", () => {
  assert.strictEqual(loadAutoUpdate("/x", () => { throw new Error("ENOENT"); }), true);
  assert.strictEqual(loadAutoUpdate("/x", () => "pas du json"), true);
  assert.strictEqual(loadAutoUpdate("/x", () => "{}"), true);
});

test("coupé seulement si enabled vaut false, et mémorisé", () => {
  assert.strictEqual(loadAutoUpdate("/x", () => '{"enabled":false}'), false);
  let written = null;
  assert.strictEqual(saveAutoUpdate("/x", false, (_f, d) => { written = d; }), true);
  assert.strictEqual(loadAutoUpdate("/x", () => written), false);
  assert.strictEqual(saveAutoUpdate("/x", true, () => { throw new Error("disque plein"); }), false);
});

function harness(enabled) {
  const calls = { check: 0 };
  const state = { enabled, timer: null, interval: null };
  const stop = scheduleAutoChecks({
    check: () => { calls.check++; },
    isEnabled: () => state.enabled,
    setTimeoutFn: (fn, ms) => { state.timer = { fn, ms }; return 1; },
    setIntervalFn: (fn, ms) => { state.interval = { fn, ms }; return 2; },
    clearTimeoutFn: () => {}, clearIntervalFn: () => {},
  });
  return { calls, state, stop };
}

test("réglage coupé : aucune vérification automatique (5 s puis 6 h)", () => {
  const h = harness(false);
  assert.strictEqual(h.state.timer.ms, FIRST_CHECK_MS);
  assert.strictEqual(h.state.interval.ms, EVERY_MS);
  h.state.timer.fn(); h.state.interval.fn(); h.state.interval.fn();
  assert.strictEqual(h.calls.check, 0);
});

test("réglage activé, ou réactivé à chaud : les vérifications ont lieu", () => {
  const h = harness(true);
  h.state.timer.fn();
  assert.strictEqual(h.calls.check, 1);
  h.state.enabled = false; h.state.interval.fn();
  assert.strictEqual(h.calls.check, 1);
  h.state.enabled = true; h.state.interval.fn();
  assert.strictEqual(h.calls.check, 2);
});
