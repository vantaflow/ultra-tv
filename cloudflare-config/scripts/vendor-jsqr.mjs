// Régénère src/jsqr-bundle.js depuis vendor/jsqr/ (jsQR 1.4.0, Apache-2.0, https://github.com/cozmo/jsQR).
// Usage : node scripts/vendor-jsqr.mjs
import { readFileSync, writeFileSync } from "node:fs";

const lib = readFileSync(new URL("../vendor/jsqr/jsQR.js", import.meta.url), "utf8");
const license = readFileSync(new URL("../vendor/jsqr/LICENSE", import.meta.url), "utf8");
if (license.includes("*/")) throw new Error("la licence contient la fin d'un commentaire");
const banner = `/*!\n * jsQR 1.4.0 — https://github.com/cozmo/jsQR\n * Copyright (c) Cosmo Wolfe. Distribué sous licence Apache-2.0 (texte complet ci-dessous).\n * Version vendorisée sans modification.\n *\n${license.split("\n").map((l) => ` * ${l}`.trimEnd()).join("\n")}\n */\n`;
writeFileSync(new URL("../src/jsqr-bundle.js", import.meta.url),
  `// GÉNÉRÉ par scripts/vendor-jsqr.mjs — ne pas éditer.\nexport const JSQR_SOURCE = ${JSON.stringify(banner + lib)};\n`);
