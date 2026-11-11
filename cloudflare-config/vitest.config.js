import { defineConfig } from "vitest/config";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-pool-workers";

// Migrations D1 appliquées à la base locale (miniflare) avant les tests, voir test/setup.js.
const migrations = await readD1Migrations("./migrations");

// Secrets factices : aucun secret réel n'est jamais lu par les tests.
export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.toml" },
      miniflare: {
        bindings: {
          TEST_MIGRATIONS: migrations,
          SESSION_SECRET: "test-session-secret-0123456789abcdef0123456789",
          PROVIDER_ENC_KEY: "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
          OPS_TOKEN: "test-ops-token-0123456789abcdef0123456789",
          TMDB_API_KEY: "test-tmdb-key-not-real",
          ADMIN_TOKEN: "test-admin-token-0123456789abcdef0123456789",
        },
      },
    }),
  ],
  test: { setupFiles: ["./test/setup.js"], include: ["test/**/*.test.js"], testTimeout: 30000 },
});
