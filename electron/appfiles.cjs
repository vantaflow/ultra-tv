"use strict";
// Resolution sure des fichiers servis par le protocole app://ultratv/.
// Refuse tout path traversal ; fallback SPA vers index.html pour les routes
// (chemins sans extension). Un fichier avec extension absent => null (404).

const path = require("node:path");
const fs = require("node:fs");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".webp": "image/webp",
  ".avif": "image/avif",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
  ".otf": "font/otf",
  ".txt": "text/plain; charset=utf-8",
  ".map": "application/json; charset=utf-8",
  ".wasm": "application/wasm",
};

function mimeFor(file) {
  return MIME[path.extname(file).toLowerCase()] || "application/octet-stream";
}

/**
 * @param {string} root dossier racine (absolu)
 * @param {string} pathname chemin d'URL (encode)
 * @returns {string|null} chemin absolu du fichier a servir, ou null
 */
function resolveAppFile(root, pathname) {
  let decoded;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    return null;
  }
  if (decoded.includes("\0") || decoded.includes("\\")) return null;
  const segments = decoded.split("/").filter((s) => s !== "");
  if (segments.some((s) => s === ".." || s === ".")) return null;

  const rootResolved = path.resolve(root);
  const candidate = path.resolve(rootResolved, ...segments);
  if (candidate !== rootResolved && !candidate.startsWith(rootResolved + path.sep)) return null;

  const isFile = (p) => {
    try {
      return fs.statSync(p).isFile();
    } catch {
      return false;
    }
  };
  if (segments.length > 0 && isFile(candidate)) return candidate;
  // Fallback SPA : uniquement pour les routes (pas d'extension) ou la racine.
  if (segments.length === 0 || path.extname(segments[segments.length - 1]) === "") {
    const index = path.join(rootResolved, "index.html");
    return isFile(index) ? index : null;
  }
  return null;
}

module.exports = { resolveAppFile, mimeFor };
