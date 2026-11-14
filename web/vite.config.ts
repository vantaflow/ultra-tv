import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";

// Version de l'appli bureau (package.json), envoyée au site dans l'en-tête x-ultra-version.
const pkg = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf8")) as { version: string };

// Build TV (Tizen Samsung, webOS LG) : VITE_TV=1 → cible ES2017 (Chromium 76+, Tizen 2020 / webOS 6)
// et Web Worker classique : les module workers exigent Chromium 80+, absent des TV 2020.
const tv = process.env.VITE_TV === "1";

export default defineConfig({
  plugins: [react()],
  // Chemins relatifs : l'appli est servie telle quelle depuis app://, un hébergement
  // statique ou un sous-dossier.
  base: "./",
  define: {
    "import.meta.env.VITE_APP_VERSION": JSON.stringify(pkg.version),
    // « 1 » seulement pour les rendus de contrôle (scripts/screens.mjs) : sinon le chargeur de données fictives est éliminé du build.
    "import.meta.env.VITE_DEMO": JSON.stringify(process.env.VITE_DEMO === "1" ? "1" : ""),
    // « 1 » dans le paquet TV (tizen/, webos/) : le code peut adapter l'interface si besoin.
    "import.meta.env.VITE_TV": JSON.stringify(tv ? "1" : ""),
  },
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  worker: { format: tv ? "iife" : "es" },
  server: { port: 5173, host: true },
  build: {
    chunkSizeWarningLimit: 900,
    // TV uniquement : le build standard garde la cible par défaut de Vite.
    ...(tv ? { target: "es2017" as const } : {}),
    // Vite 8 (Rolldown) : `manualChunks` sous forme d'objet n'existe plus, on décrit les mêmes paquets
    // avec `codeSplitting.groups` (un module de node_modules rejoint le premier groupe qui le reconnaît).
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: "hls", test: /[\\/]node_modules[\\/]hls\.js[\\/]/ },
            { name: "mpegts", test: /[\\/]node_modules[\\/]mpegts\.js[\\/]/ },
            { name: "react", test: /[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/ },
            { name: "dexie", test: /[\\/]node_modules[\\/](dexie|dexie-react-hooks)[\\/]/ },
          ],
        },
      },
    },
  },
  test: { environment: "node", include: ["src/**/*.test.{ts,tsx}"] },
});
