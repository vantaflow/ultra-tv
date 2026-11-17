// Assemble le Worker (../src/index.js) et son pilote Turso en un seul fichier pour la fonction Vercel :
// le déploiement part de backup/ et ne voit pas ../src. Sortie : dist/worker.mjs (ignoré par git).
import { build } from "esbuild";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
await build({
  entryPoints: [resolve(here, "src/entry.js")],
  outfile: resolve(here, "dist/worker.mjs"),
  bundle: true,
  format: "esm",
  platform: "node",
  target: "node20",
  alias: { "cloudflare:workers": resolve(here, "src/stub-cloudflare-workers.js") },
  logLevel: "info",
});
