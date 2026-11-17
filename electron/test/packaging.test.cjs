// Chaque module local chargé par le processus principal doit être dans « build.files » : sinon l'installeur le laisse
// de côté et l'application ne démarre pas (« Cannot find module './discovery.cjs' », bureau 1.2.66, issue #187).
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const files = new Set(JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8")).build.files);

function localRequires(file, seen = new Set()) {
  if (seen.has(file)) return seen;
  seen.add(file);
  const src = fs.readFileSync(path.join(root, file), "utf8");
  for (const m of src.matchAll(/require\(\s*["']\.\/([^"']+)["']\s*\)/g)) localRequires(m[1], seen);
  return seen;
}

test("tous les modules chargés depuis main.cjs et preload.cjs sont empaquetés", () => {
  const needed = new Set([...localRequires("main.cjs"), ...localRequires("preload.cjs")]);
  const missing = [...needed].filter((f) => !files.has(f));
  assert.deepStrictEqual(missing, []);
});
