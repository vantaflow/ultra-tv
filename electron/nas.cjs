"use strict";
// Partages réseau (NAS) montés par le système, comme source VOD du bureau : dossier local, chemin UNC (\\hôte\partage) sous
// Windows, /Volumes/… sous macOS, point de montage sous Linux. Le processus principal parcourt le dossier (borné) et sert les
// fichiers vidéo au lecteur par le proxy local (route /<jeton>/nas/<racine>/<chemin>), avec lecture par plages.
//
// Sécurité : seules les racines déclarées par le renderer (parcours explicite d'un dossier) sont servies ; un chemin demandé ne
// sort jamais de sa racine (pas de « .. », pas de séparateur ni de lecteur dans un segment) et seuls les fichiers vidéo sont
// servis. Aucun chemin n'est journalisé. La reconnaissance des films et des épisodes se fait côté renderer (web/src/lib/nasNames.ts).

const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const VIDEO_EXTENSIONS = new Set(["mkv", "mp4", "m4v", "avi", "mov", "wmv", "ts", "m2ts", "mpg", "mpeg", "webm", "flv", "divx", "ogv"]);
const IGNORED_FOLDERS = new Set(["lost+found", "system volume information", "extras", "extra", "featurettes", "behind the scenes", "sample", "samples", "trailers", "subs", "subtitles"]);
const SAMPLE = /(^|[\s._\-[(])(sample|trailer|teaser)([\s._\-\])]|$)/;
const DEFAULT_LIMITS = Object.freeze({ maxDepth: 8, maxFiles: 50000, maxFolders: 8000 });
const CONTENT_TYPES = {
  mkv: "video/x-matroska", mp4: "video/mp4", m4v: "video/mp4", avi: "video/x-msvideo", mov: "video/quicktime", wmv: "video/x-ms-wmv",
  webm: "video/webm", ts: "video/mp2t", m2ts: "video/mp2t", mpg: "video/mpeg", mpeg: "video/mpeg", flv: "video/x-flv", ogv: "video/ogg",
};

const extensionOf = (name) => {
  const i = String(name).lastIndexOf(".");
  return i < 0 ? "" : String(name).slice(i + 1).toLowerCase();
};
const isVideo = (name) => VIDEO_EXTENSIONS.has(extensionOf(name));

function isIgnoredFile(name) {
  const n = String(name).toLowerCase();
  if (n.startsWith(".")) return true;
  const dot = n.lastIndexOf(".");
  return SAMPLE.test(dot < 0 ? n : n.slice(0, dot));
}

function isIgnoredFolder(name) {
  const n = String(name).toLowerCase();
  return n.startsWith(".") || n.startsWith("@") || n.startsWith("#") || n.startsWith("$") || IGNORED_FOLDERS.has(n);
}

/** Racine acceptable : chemin absolu (local, UNC ou /Volumes/…) sans octet nul ni segment « .. ». Rend le chemin normalisé ou null. */
function validRoot(p) {
  if (typeof p !== "string" || p.length === 0 || p.length > 1024 || p.includes("\0")) return null;
  const isUnc = /^\\\\[^\\/]+[\\/][^\\/]+/.test(p) || /^\/\/[^/]+\/[^/]+/.test(p);
  if (!isUnc && !path.isAbsolute(p) && !path.win32.isAbsolute(p)) return null;
  if (p.split(/[\\/]/).includes("..")) return null;
  return path.normalize(p);
}

/**
 * Parcours en largeur, borné (profondeur, fichiers, dossiers), qui ne garde que les vidéos. Un dossier illisible est sauté ; la
 * racine illisible lève l'erreur. Rend { files: [{ path: "a/b.mkv", size, mtime }], truncated }.
 */
async function scanRoot(root, limits = DEFAULT_LIMITS, isCancelled = () => false) {
  const lim = { ...DEFAULT_LIMITS, ...limits };
  const files = [];
  const queue = [[]];
  let folders = 0;
  let truncated = false;
  while (queue.length) {
    if (isCancelled()) break;
    if (files.length >= lim.maxFiles || folders >= lim.maxFolders) { truncated = true; break; }
    const dir = queue.shift();
    folders++;
    let entries;
    try {
      entries = await fs.promises.readdir(path.join(root, ...dir), { withFileTypes: true });
    } catch (e) {
      if (dir.length === 0) throw e;
      continue;
    }
    entries.sort((a, b) => (a.name.toLowerCase() < b.name.toLowerCase() ? -1 : a.name.toLowerCase() > b.name.toLowerCase() ? 1 : 0));
    for (const e of entries) {
      const name = e.name;
      if (!name || name === "." || name === ".." || /[\\/\0]/.test(name)) continue;
      let isDir = e.isDirectory();
      let isFile = e.isFile();
      if (e.isSymbolicLink()) {
        try {
          const st = await fs.promises.stat(path.join(root, ...dir, name));
          isDir = st.isDirectory();
          isFile = st.isFile();
        } catch { continue; }
      }
      if (isDir) {
        if (dir.length + 1 <= lim.maxDepth && !isIgnoredFolder(name)) queue.push([...dir, name]);
      } else if (isFile && isVideo(name) && !isIgnoredFile(name)) {
        if (files.length >= lim.maxFiles) { truncated = true; break; }
        let size = 0;
        let mtime = 0;
        try {
          const st = await fs.promises.stat(path.join(root, ...dir, name));
          size = st.size;
          mtime = Math.floor(st.mtimeMs / 1000);
        } catch { /* fichier disparu entre-temps : gardé sans taille */ }
        files.push({ path: [...dir, name].join("/"), size, mtime });
      }
    }
  }
  return { files, truncated };
}

/** Segments d'un chemin relatif « a/b/c.mkv » ; null s'il tente de sortir de sa racine ou contient un caractère interdit. */
function safeSegments(rel, platform = process.platform) {
  if (typeof rel !== "string" || rel.length === 0 || rel.length > 4096 || rel.includes("\0")) return null;
  const segs = rel.split("/").filter((s) => s.length > 0);
  if (segs.length === 0) return null;
  // Windows : « : » (lecteur, flux de données alternatif) et caractères réservés interdits ; ailleurs, seul « \ » (séparateur forgé).
  const bad = platform === "win32" ? /[\\:*?"<>|]/ : /\\/;
  for (const s of segs) {
    if (s === "." || s === ".." || bad.test(s)) return null;
  }
  return segs;
}

/** Racines déclarées par le renderer : identifiant stable (empreinte du chemin) → chemin. */
function createRegistry() {
  const roots = new Map();
  return {
    register(root) {
      const r = validRoot(root);
      if (!r) return null;
      const id = crypto.createHash("sha1").update(r).digest("hex").slice(0, 12);
      roots.set(id, r);
      return id;
    },
    /** Chemin absolu d'un fichier vidéo de la racine [id], ou null. */
    resolve(id, rel, platform = process.platform) {
      const root = roots.get(id);
      if (!root) return null;
      const segs = safeSegments(rel, platform);
      if (!segs || !isVideo(segs[segs.length - 1])) return null;
      const abs = path.join(root, ...segs);
      const relCheck = path.relative(root, abs);
      if (relCheck.startsWith("..") || path.isAbsolute(relCheck)) return null;
      return abs;
    },
  };
}

/** `bytes=a-b`, `bytes=a-`, `bytes=-n` ; plusieurs plages : la première. Rend { full } | { start, end } | { unsatisfiable }. */
function parseRange(header, size) {
  const h = typeof header === "string" ? header.trim() : "";
  if (!/^bytes=/i.test(h)) return { full: true };
  const spec = h.slice(6).split(",")[0].trim();
  const dash = spec.indexOf("-");
  if (dash < 0) return { full: true };
  const a = spec.slice(0, dash).trim();
  const b = spec.slice(dash + 1).trim();
  if (!a && !b) return { full: true };
  if (size <= 0) return { unsatisfiable: true };
  if (!a) {
    const n = Number(b);
    if (!/^\d+$/.test(b)) return { full: true };
    if (n <= 0) return { unsatisfiable: true };
    return { start: Math.max(0, size - n), end: size - 1 };
  }
  if (!/^\d+$/.test(a)) return { full: true };
  const start = Number(a);
  let end = size - 1;
  if (b) {
    if (!/^\d+$/.test(b)) return { full: true };
    end = Math.min(Number(b), size - 1);
  }
  if (start >= size || end < start) return { unsatisfiable: true };
  return { start, end };
}

/** Sert un fichier local (GET / HEAD) avec lecture par plages. [headers] : en-têtes CORS du proxy. */
async function serveFile(req, res, abs, headers = {}) {
  let st;
  try {
    st = await fs.promises.stat(abs);
  } catch {
    res.writeHead(404, { ...headers, "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" });
    return res.end("404");
  }
  if (!st.isFile()) {
    res.writeHead(404, { ...headers, "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" });
    return res.end("404");
  }
  const size = st.size;
  const r = parseRange(req.headers.range, size);
  if (r.unsatisfiable) {
    res.writeHead(416, { ...headers, "Content-Range": `bytes */${size}`, "Content-Length": "0" });
    return res.end();
  }
  const base = {
    ...headers,
    "Content-Type": CONTENT_TYPES[extensionOf(abs)] || "application/octet-stream",
    "Accept-Ranges": "bytes",
    "Cache-Control": "no-store",
  };
  const start = r.full ? 0 : r.start;
  const end = r.full ? size - 1 : r.end;
  const length = size === 0 ? 0 : end - start + 1;
  if (r.full) res.writeHead(200, { ...base, "Content-Length": String(length) });
  else res.writeHead(206, { ...base, "Content-Range": `bytes ${start}-${end}/${size}`, "Content-Length": String(length) });
  if (req.method === "HEAD" || length === 0) return res.end();
  const stream = fs.createReadStream(abs, { start, end });
  res.on("close", () => stream.destroy());
  stream.on("error", () => res.destroy());
  stream.pipe(res);
}

/** `/<jeton>/nas/<racine>/<b64 du chemin>[.ext]` → { token, id, rel } ou null (b64 = base64url UTF-8 de « a/b/c.mkv »). */
function parseNasPath(pathname) {
  const segs = pathname.split("/");
  if (segs.length !== 5 || segs[0] !== "" || segs[2] !== "nas") return null;
  const id = segs[3];
  if (!/^[0-9a-f]{12}$/.test(id)) return null;
  const b64 = segs[4].replace(/\.[A-Za-z0-9]{1,5}$/, "");
  if (!/^[A-Za-z0-9_-]+$/.test(b64) || b64.length > 6000) return null;
  let rel;
  try { rel = Buffer.from(b64, "base64url").toString("utf8"); } catch { return null; }
  return { token: segs[1], id, rel };
}

module.exports = { VIDEO_EXTENSIONS, DEFAULT_LIMITS, isVideo, isIgnoredFile, isIgnoredFolder, validRoot, scanRoot, safeSegments, createRegistry, parseRange, serveFile, parseNasPath };
