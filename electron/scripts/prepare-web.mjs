// Copie web/dist vers electron/web-dist (le chemin ../web/dist ne fonctionne pas
// dans `build.files` d'electron-builder). WEB_DIST permet de pointer ailleurs.
import { cpSync, existsSync, rmSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const src = resolve(process.env.WEB_DIST || resolve(here, "../../web/dist"));
const dest = resolve(here, "../web-dist");

if (!existsSync(resolve(src, "index.html"))) {
  console.error(`web dist introuvable (index.html manquant) : ${src}\nLancez d'abord le build web (cd web && npm ci && npm run build).`);
  process.exit(1);
}
rmSync(dest, { recursive: true, force: true });
cpSync(src, dest, { recursive: true });
console.log(`web-dist pret depuis ${src}`);
