#!/usr/bin/env node
"use strict";
// Télécharge un ffmpeg STATIQUE en variante LGPL (BtbN/FFmpeg-Builds, sans libx264/libx265 ni autre composant GPL)
// dans vendor/ffmpeg/, embarqué par electron-builder (extraResources) sous Windows et Linux. Il sert à convertir
// l'audio AC-3 / E-AC-3 / DTS en AAC (transcode.cjs). macOS : pas de build LGPL fiable, ffmpeg du système (Homebrew).
//
// Usage : node scripts/fetch-ffmpeg.cjs win|linux     (FFMPEG_ASSET pour forcer un autre fichier de la release)
const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

const target = process.argv[2];
const ASSETS = { win: "ffmpeg-master-latest-win64-lgpl.zip", linux: "ffmpeg-master-latest-linux64-lgpl.tar.xz" };
if (!ASSETS[target]) { console.error("usage : fetch-ffmpeg.cjs win|linux"); process.exit(2); }
const asset = process.env.FFMPEG_ASSET || ASSETS[target];
if (!/-lgpl[.-]/.test(asset)) { console.error("refusé : seule une variante LGPL peut être embarquée"); process.exit(1); }
const url = `https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/${asset}`;

const root = path.join(__dirname, "..");
const outDir = path.join(root, "vendor", "ffmpeg");
const tmp = path.join(root, "vendor", ".ffmpeg-dl");
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp, { recursive: true });

(async () => {
  const res = await fetch(url, { redirect: "follow" });
  if (!res.ok) throw new Error(`téléchargement ${res.status}`);
  const archive = path.join(tmp, asset);
  fs.writeFileSync(archive, Buffer.from(await res.arrayBuffer()));
  // bsdtar (Windows, macOS) et GNU tar (Linux, .tar.xz) savent extraire.
  if (asset.endsWith(".zip") && process.platform !== "win32") execFileSync("unzip", ["-q", archive, "-d", tmp]);
  else execFileSync("tar", ["-xf", archive, "-C", tmp]);
  const dir = fs.readdirSync(tmp).map((d) => path.join(tmp, d)).find((d) => fs.statSync(d).isDirectory());
  if (!dir) throw new Error("archive inattendue");
  const exe = target === "win" ? "ffmpeg.exe" : "ffmpeg";
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(outDir, { recursive: true });
  fs.copyFileSync(path.join(dir, "bin", exe), path.join(outDir, exe));
  if (target !== "win") fs.chmodSync(path.join(outDir, exe), 0o755);
  // Garde-fou : le binaire ne doit pas avoir été configuré avec --enable-gpl / --enable-nonfree.
  const runnable = (target === "win") === (process.platform === "win32");
  if (runnable) {
    const conf = execFileSync(path.join(outDir, exe), ["-hide_banner", "-buildconf"]).toString();
    if (/--enable-(gpl|nonfree)\b/.test(conf)) { fs.rmSync(outDir, { recursive: true, force: true }); throw new Error("build GPL/nonfree refusé"); }
  }
  for (const f of ["LICENSE.txt", "LICENSE"]) { const p = path.join(dir, f); if (fs.existsSync(p)) fs.copyFileSync(p, path.join(outDir, "LICENSE.txt")); }
  fs.writeFileSync(path.join(outDir, "SOURCE.txt"), `FFmpeg (LGPL), build statique BtbN/FFmpeg-Builds\n${url}\nSources : https://github.com/BtbN/FFmpeg-Builds et https://git.ffmpeg.org/ffmpeg.git\n`);
  fs.rmSync(tmp, { recursive: true, force: true });
  console.log(`ffmpeg LGPL prêt : ${path.join(outDir, exe)}`);
})().catch((e) => { console.error(`fetch-ffmpeg : ${e.message}`); process.exit(1); });
