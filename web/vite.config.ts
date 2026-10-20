import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";

// Version de l'appli bureau (package.json), envoyée au site dans l'en-tête x-ultra-version.
const pkg = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf8")) as { version: string };

export default defineConfig({
  plugins: [react()],
  // Chemins relatifs : l'appli est servie telle quelle depuis app://, un hébergement
  // statique ou un sous-dossier.
  base: "./",
  define: { "import.meta.env.VITE_APP_VERSION": JSON.stringify(pkg.version) },
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  worker: { format: "es" },
  server: { port: 5173, host: true },
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks: {
          hls: ["hls.js"],
          mpegts: ["mpegts.js"],
          react: ["react", "react-dom", "react-router-dom"],
          dexie: ["dexie", "dexie-react-hooks"],
        },
      },
    },
  },
  test: { environment: "node", include: ["src/**/*.test.ts"] },
});
