import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

export default defineConfig({
  plugins: [react()],
  // Chemins relatifs : l'appli est servie telle quelle depuis app://, un hébergement
  // statique ou un sous-dossier.
  base: "./",
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
