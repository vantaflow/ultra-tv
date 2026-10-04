"use strict";
// Mise a jour automatique via electron-updater (GitHub Releases).
//
// Etats envoyes au renderer : { state, version?, percent?, message? } avec state
// dans checking | available | not-available | downloading | downloaded |
// unavailable | error. "unavailable" couvre : mode dev, application non signee
// sur macOS (Squirrel.Mac refuse d'installer une mise a jour non signee), absence
// de metadonnees de publication. Rien ne plante : tout est dans des try/catch.

const { execFile } = require("node:child_process");
const path = require("node:path");

function macAppBundle() {
  // .../Ultra TV.app/Contents/MacOS/Ultra TV -> .../Ultra TV.app
  return path.resolve(process.execPath, "..", "..", "..");
}

function isMacSigned() {
  return new Promise((resolve) => {
    execFile("codesign", ["-dvv", macAppBundle()], { timeout: 8000 }, (err, _out, stderr) => {
      if (err) return resolve(false);
      // Signature ad hoc ou sans equipe => pas de mise a jour possible.
      const team = /TeamIdentifier=(.+)/.exec(stderr || "");
      resolve(!!team && team[1].trim() !== "not set");
    });
  });
}

function createUpdater({ app, send }) {
  let autoUpdater = null;
  let last = { state: "unavailable", message: "not-initialised" };
  let initPromise = null;

  function publish(status) {
    last = status;
    try {
      send(status);
    } catch {
      /* fenetre fermee */
    }
  }

  async function init() {
    if (!app.isPackaged) {
      publish({ state: "unavailable", message: "dev" });
      return false;
    }
    if (process.platform === "darwin" && !(await isMacSigned())) {
      publish({ state: "unavailable", message: "unsigned" });
      return false;
    }
    try {
      ({ autoUpdater } = require("electron-updater"));
      autoUpdater.autoDownload = true;
      autoUpdater.autoInstallOnAppQuit = true;
      autoUpdater.allowPrerelease = false;
      autoUpdater.on("checking-for-update", () => publish({ state: "checking" }));
      autoUpdater.on("update-available", (i) => publish({ state: "available", version: i && i.version }));
      autoUpdater.on("update-not-available", () => publish({ state: "not-available" }));
      autoUpdater.on("download-progress", (p) =>
        publish({ state: "downloading", percent: Math.round((p && p.percent) || 0) }),
      );
      autoUpdater.on("update-downloaded", (i) => publish({ state: "downloaded", version: i && i.version }));
      autoUpdater.on("error", () => publish({ state: "error", message: "update-failed" }));
      return true;
    } catch {
      autoUpdater = null;
      publish({ state: "unavailable", message: "updater-missing" });
      return false;
    }
  }

  async function check() {
    if (!initPromise) initPromise = init();
    const ok = await initPromise;
    if (!ok || !autoUpdater) return last;
    try {
      await autoUpdater.checkForUpdates();
    } catch {
      publish({ state: "error", message: "update-failed" });
    }
    return last;
  }

  return { check, status: () => last };
}

module.exports = { createUpdater };
