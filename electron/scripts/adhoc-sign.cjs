"use strict";
// Hook electron-builder `afterSign` (macOS uniquement).
//
// Sans identite de signature, electron-builder laisse un bundle dont les scellés
// sont invalides (fuses modifies, helpers renommes) : macOS sur Apple Silicon le
// tue au lancement (SIGKILL). On le signe donc "ad hoc" (`codesign -s -`) : ce
// n'est PAS une vraie signature (Gatekeeper avertit toujours, pas de mise a jour
// auto) mais le binaire demarre. Si une vraie identite est configuree
// (CSC_LINK / CSC_NAME), ce hook ne fait rien.
const { execFileSync } = require("node:child_process");
const path = require("node:path");

exports.default = async function adhocSign(context) {
  if (context.electronPlatformName !== "darwin") return;
  if (process.env.CSC_LINK || process.env.CSC_NAME) return;
  const productFilename = context.packager.appInfo.productFilename;
  const appPath = path.join(context.appOutDir, `${productFilename}.app`);
  const entitlements = path.join(context.packager.projectDir, "build", "entitlements.mac.plist");
  console.log("  • signature ad hoc (aucune identite configuree)");
  execFileSync("codesign", ["--force", "--deep", "--sign", "-", "--entitlements", entitlements, appPath], {
    stdio: "inherit",
  });
};
