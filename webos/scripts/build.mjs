// Empaquetage webOS (LG TV) : `node scripts/build.mjs`
// 1. build web en mode TV (web/package.json → build:tv : cible ES2017, worker classique),
// 2. assemblage du dossier d'application (appinfo.json + icônes + dist) dans build/app/,
// 3. .ipk dans dist/ via ares-package (CLI webOS, exécutée en version épinglée via npx :
//    @webos-tools/cli@3.2.6 — Node 20+ requis).
// L'installation sur un vrai TV passe par le mode développeur (50 h par session) : ares-install.
import { copyFileSync, cpSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { execSync } from "node:child_process";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const webos = resolve(here, "..");
const root = resolve(webos, "..");
const version = readFileSync(join(root, "VERSION"), "utf8").trim();

const dist = join(webos, "dist");
const app = join(webos, "build", "app");
execSync(`rm -rf "${app}" "${dist}"`, { shell: "/bin/bash" });

console.log("→ build web (mode TV)…");
execSync("npm run build:tv", { cwd: join(root, "web"), stdio: "inherit" });

console.log("→ assemblage de l'application…");
mkdirSync(app, { recursive: true });
cpSync(join(root, "web", "dist"), app, { recursive: true });
for (const icon of ["icon.png", "largeIcon.png", "bgImage.png", "splashBackground.png"]) {
  copyFileSync(join(webos, "icons", icon), join(app, icon));
}
// appinfo.json avec la version du dépôt (une seule source : VERSION à la racine).
writeFileSync(
  join(app, "appinfo.json"),
  readFileSync(join(webos, "appinfo.json"), "utf8").replace(/("version":\s*)"[^"]*"/, `$1"${version}"`),
);

console.log("→ ares-package (@webos-tools/cli)…");
mkdirSync(dist, { recursive: true });
const id = JSON.parse(readFileSync(join(webos, "appinfo.json"), "utf8")).id;
execSync(`npx -y -p @webos-tools/cli@3.2.6 ares-package "${app}" -o "${dist}"`, { stdio: "inherit", shell: "/bin/bash" });

const produced = join(dist, `${id}_${version}_all.ipk`);
const artifact = join(dist, `UltraTV-webOS-${version}.ipk`);
execSync(`mv "${produced}" "${artifact}"`, { shell: "/bin/bash" });
console.log(`✓ ${artifact}`);
