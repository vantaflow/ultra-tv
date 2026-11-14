// Empaquetage Tizen (Samsung TV) : `node scripts/build.mjs`
// 1. build web en mode TV (web/package.json → build:tv : cible ES2017, worker classique),
// 2. assemblage du widget (config.xml + icônes + dist) dans build/stage/,
// 3. .wgt dans dist/ :
//    - CLI Tizen Studio détectée (env TIZEN_STUDIO_HOME ou chemins usuels) → `tizen package`,
//      signé si TIZEN_SECURITY_PROFILE (profil de certificats du Certificate Manager) est défini ;
//    - sinon repli zip pur : un .wgt EST un zip (config.xml à la racine). Le .wgt non signé ne
//      s'installe pas sur un TV : signer d'abord (tizen sign) — voir README.md.
import { copyFileSync, cpSync, mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { execSync } from "node:child_process";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const tizen = resolve(here, "..");
const root = resolve(tizen, "..");
const version = readFileSync(join(root, "VERSION"), "utf8").trim();

const dist = join(tizen, "dist");
const stage = join(tizen, "build", "stage");
rmStage();

console.log(`→ build web (mode TV)…`);
execSync("npm run build:tv", { cwd: join(root, "web"), stdio: "inherit" });

console.log("→ assemblage du widget…");
mkdirSync(stage, { recursive: true });
cpSync(join(root, "web", "dist"), stage, { recursive: true });
mkdirSync(join(stage, "icons"), { recursive: true });
copyFileSync(join(tizen, "icons", "app_icon.png"), join(stage, "icons", "app_icon.png"));
// config.xml avec la version du dépôt (une seule source : VERSION à la racine). L'ancre sur
// `<widget` évite la déclaration XML (…<?xml version="1.0"?…).
writeFileSync(
  join(stage, "config.xml"),
  readFileSync(join(tizen, "config.xml"), "utf8").replace(/(<widget[^>]*\sversion=")[^"]*(")/, `$1${version}$2`),
);

const artifact = join(dist, `UltraTV-Tizen-${version}.wgt`);
const cli = findTizenCli();

if (cli) {
  console.log(`→ tizen package (${cli})…`);
  mkdirSync(dist, { recursive: true });
  const sign = process.env.TIZEN_SECURITY_PROFILE ? ["-s", process.env.TIZEN_SECURITY_PROFILE] : [];
  execSync([`"${cli}"`, "package", "-t", "wgt", ...sign, "-o", `"${dist}"`, `"${stage}"`].join(" "), { stdio: "inherit", shell: "/bin/bash" });
  const produced = join(dist, `UltraTV.wgt`);
  if (existsSync(produced)) execSync(`mv "${produced}" "${artifact}"`, { shell: "/bin/bash" });
} else {
  console.log("→ CLI Tizen Studio absente : .wgt non signé (zip). Installez Tizen Studio pour signer, voir README.md");
  mkdirSync(dist, { recursive: true });
  execSync(`cd "${stage}" && zip -r -X -q "${artifact}" .`, { shell: "/bin/bash" });
}

console.log(`✓ ${artifact}${cli && !process.env.TIZEN_SECURITY_PROFILE ? " (NON SIGNÉ : pas de TIZEN_SECURITY_PROFILE)" : ""}`);

function rmStage() {
  execSync(`rm -rf "${stage}"`, { shell: "/bin/bash" });
}

/** CLI Tizen Studio : env TIZEN_STUDIO_HOME, puis les chemins d'installation usuels. */
function findTizenCli() {
  const bases = [
    process.env.TIZEN_STUDIO_HOME,
    join(process.env.HOME ?? "", "tizen-studio"),
    join(process.env.HOME ?? "", "TizenStudio"),
    "/opt/tizen-studio",
    "/Applications/TizenStudio",
  ].filter(Boolean);
  for (const base of bases) {
    for (const rel of ["tools/ide/bin/tizen", "tools/ide/bin/tizen.bat"]) {
      const p = join(base, rel);
      if (existsSync(p)) return p;
    }
  }
  const p = execSync("command -v tizen 2>/dev/null || true", { encoding: "utf8", shell: "/bin/bash" }).trim();
  return p || null;
}
