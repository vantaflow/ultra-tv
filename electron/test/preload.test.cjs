"use strict";
// Pont du renderer : setZoomFactor (taille de l'interface) passe par webFrame, borné à 50–200 %.
const test = require("node:test");
const assert = require("node:assert/strict");
const Module = require("node:module");
const path = require("node:path");

function loadPreload() {
  let api = null;
  const zooms = [];
  const fake = {
    contextBridge: { exposeInMainWorld: (_name, value) => { api = value; } },
    ipcRenderer: { sendSync: () => ({}), invoke: async () => undefined, send: () => {}, on: () => {}, removeListener: () => {} },
    webFrame: { setZoomFactor: (f) => zooms.push(f) },
  };
  const origLoad = Module._load;
  const g = globalThis;
  const hadDoc = "document" in g;
  g.document = { documentElement: { classList: { add: () => {} } } };
  Module._load = function (req, ...rest) { return req === "electron" ? fake : origLoad.call(this, req, ...rest); };
  try {
    const file = path.join(__dirname, "..", "preload.cjs");
    delete require.cache[file];
    require(file);
  } finally {
    Module._load = origLoad;
    if (!hadDoc) delete g.document;
  }
  return { api, zooms };
}

test("setZoomFactor : valeurs valides transmises à webFrame, le reste ignoré", () => {
  const { api, zooms } = loadPreload();
  assert.equal(typeof api.setZoomFactor, "function");
  api.setZoomFactor(1.25);
  api.setZoomFactor(0.9);
  api.setZoomFactor("1.5");
  api.setZoomFactor(5);
  api.setZoomFactor(0.1);
  api.setZoomFactor("abc");
  api.setZoomFactor(Number.NaN);
  assert.deepEqual(zooms, [1.25, 0.9, 1.5]);
});
