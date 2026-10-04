// Genere build/icon.icns, icon.ico, icon.png (1024) et icon-512.png depuis
// build/icon.svg. Reproductible : `npm run icons`. Les icones generees sont
// COMMITEES (le CI ne les regenere pas).
//  - icns (macOS) : le carre arrondi est reduit a 824/1024 avec marge transparente,
//    comme les icones systeme.
//  - ico / png (Windows, Linux) : plein cadre.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { Resvg } from "@resvg/resvg-js";
import png2icons from "png2icons";

const build = resolve(dirname(fileURLToPath(import.meta.url)), "../build");
const svg = readFileSync(resolve(build, "icon.svg"), "utf8");
const inner = svg.replace(/^[\s\S]*?<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "");

const render = (markup, size) =>
  new Resvg(markup, { fitTo: { mode: "width", value: size } }).render().asPng();

const full = (size) => render(svg, size);
const padded = (size) => {
  const scale = 824 / 512;
  const wrapped = `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<g transform="translate(100 100) scale(${scale})">${inner}</g></svg>`;
  return render(wrapped, size);
};

writeFileSync(resolve(build, "icon.png"), full(1024));
writeFileSync(resolve(build, "icon-512.png"), full(512));

const icns = png2icons.createICNS(padded(1024), png2icons.BICUBIC, 0);
if (!icns) throw new Error("echec icns");
writeFileSync(resolve(build, "icon.icns"), icns);

const ico = png2icons.createICO(full(1024), png2icons.BICUBIC, 0, false, true);
if (!ico) throw new Error("echec ico");
writeFileSync(resolve(build, "icon.ico"), ico);
console.log("icones generees dans", build);
