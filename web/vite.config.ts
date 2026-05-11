import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@domain": fileURLToPath(new URL("./src/domain", import.meta.url)),
      "@data": fileURLToPath(new URL("./src/data", import.meta.url)),
      "@player": fileURLToPath(new URL("./src/player", import.meta.url)),
      "@app": fileURLToPath(new URL("./src/app", import.meta.url)),
    },
  },
  server: { port: 5173, host: true },
  build: {
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        // Split heavy player libs into their own chunks so users that never play
        // a DASH stream don't have to download shaka-player.
        manualChunks: {
          hls: ["hls.js"],
          shaka: ["shaka-player"],
          react: ["react", "react-dom", "react-router-dom"],
          dexie: ["dexie", "dexie-react-hooks"],
        },
      },
    },
  },
});
