// Icônes des paquets TV (Tizen Samsung, webOS LG) rendues depuis electron/build/icon.svg.
// Les PNG sont COMMITÉS (convention du dépôt, cf. electron/scripts/make-icons.mjs) : ce script
// ne sert qu'à les regénérer — `node tools/tv-icons.mjs`.
//  - tizen/icons/app_icon.png          512×512 (icône de l'app sur Samsung TV)
//  - webos/icons/icon.png               80×80   (icône du lanceur webOS)
//  - webos/icons/largeIcon.png         160×160
//  - webos/icons/bgImage.png           960×568  (fond de tuile dans le lanceur webOS)
//  - webos/icons/splashBackground.png 1920×1080 (écran d'attente au lancement)
import { readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const require_ = createRequire(resolve(root, "electron/package.json"));
const { Resvg } = require_("@resvg/resvg-js");

const svg = readFileSync(resolve(root, "electron/build/icon.svg"), "utf8");
const inner = svg.replace(/^[\s\S]*?<svg[^>]*>/, "").replace(/<\/svg>\s*$/, "");

const render = (markup, out) => {
  writeFileSync(out, new Resvg(markup, { fitTo: { mode: "width", value: Number(/width="(\d+)"/.exec(markup)?.[1]) } }).render().asPng());
  console.log("→", out);
};

// Carré plein (les lanceurs TV recadrent rarement : icône claire, lisible en 80 px comme en 512).
const square = (size, out) =>
  render(`<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">${inner}</svg>`, out);

// Fond sombre avec logo centré (fond de tuile webOS et écran d'attente), proportions imposées par LG.
const backdrop = (w, h, out, pad) => {
  const s = Math.min(w, h) / (1024 + pad * 2);
  render(`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">
  <rect width="${w}" height="${h}" fill="#0b0f14"/>
  <g transform="translate(${w / 2 - (1024 * s) / 2} ${h / 2 - (1024 * s) / 2}) scale(${s})">${inner}</g>
</svg>`, out);
};

square(512, resolve(root, "tizen/icons/app_icon.png"));
square(80, resolve(root, "webos/icons/icon.png"));
square(160, resolve(root, "webos/icons/largeIcon.png"));
backdrop(960, 568, resolve(root, "webos/icons/bgImage.png"), 160);
backdrop(1920, 1080, resolve(root, "webos/icons/splashBackground.png"), 240);
