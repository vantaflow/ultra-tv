// Fichiers fixes du site → public/ (Workers Static Assets : servis par Cloudflare SANS lancer le Worker, donc hors
// quota des 100 000 requêtes/jour). Seule source de vérité : les modules src/ (pwa.js, fonts.js, jsqr-bundle.js).
//   node scripts/build-assets.mjs           régénère public/
//   node scripts/build-assets.mjs --check   échoue si public/ n'est plus à jour (lancé avant les tests)
import { mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { MANIFEST, SERVICE_WORKER, ICONS } from "../src/pwa.js";
import { SORA, MANROPE } from "../src/fonts.js";
import { JSQR_SOURCE } from "../src/jsqr-bundle.js";

const OUT = new URL("../public/", import.meta.url).pathname;
const b64 = (s) => Buffer.from(s, "base64");

// Mêmes en-têtes que les routes du Worker (src/pwa.js, src/fonts.js, src/index.js).
const HEADERS = `# Généré par scripts/build-assets.mjs : ne pas modifier à la main.
/*
  X-Content-Type-Options: nosniff

/manifest.webmanifest
  Content-Type: application/manifest+json; charset=utf-8
  Cache-Control: public, max-age=3600

/sw.js
  Content-Type: text/javascript; charset=utf-8
  Cache-Control: no-cache
  Service-Worker-Allowed: /

/pwa/*
  Cache-Control: public, max-age=604800

/apple-touch-icon.png
  Cache-Control: public, max-age=604800

/assets/*
  Cache-Control: public, max-age=31536000, immutable
`;

const files = {
  "manifest.webmanifest": Buffer.from(JSON.stringify(MANIFEST)),
  "sw.js": Buffer.from(SERVICE_WORKER),
  "assets/sora.woff2": b64(SORA),
  "assets/manrope.woff2": b64(MANROPE),
  "assets/jsqr.js": Buffer.from(JSQR_SOURCE),
  "_headers": Buffer.from(HEADERS),
  ...Object.fromEntries(Object.entries(ICONS).map(([p, v]) => [p.slice(1), b64(v)])),
};

const check = process.argv.includes("--check");
const stale = [];
for (const [rel, buf] of Object.entries(files)) {
  const p = join(OUT, rel);
  if (check) { if (!existsSync(p) || !readFileSync(p).equals(buf)) stale.push(rel); continue; }
  mkdirSync(dirname(p), { recursive: true });
  writeFileSync(p, buf);
}
if (check && stale.length) { console.error("public/ n'est plus à jour (node scripts/build-assets.mjs) :", stale.join(", ")); process.exit(1); }
console.log(check ? "public/ à jour" : `public/ : ${Object.keys(files).length} fichiers`);
