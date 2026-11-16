import { defineConfig } from "vitest/config";

// Mode Turso simulé (`npm run test:turso`) : même suite, stockage passé par l'adaptateur Turso (src/turso.js).
const turso = process.env.UTV_TURSO_SIM === "1";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-pool-workers";

// Migrations D1 appliquées à la base locale (miniflare) avant les tests, voir test/setup.js.
const migrations = await readD1Migrations("./migrations");

// Secrets factices : aucun secret réel n'est jamais lu par les tests.
export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.toml" },
      miniflare: {
        // Bases locales des tests (les liaisons de production D1 / KV ont quitté wrangler.toml : stockage Turso).
        d1Databases: ["DB"],
        kvNamespaces: ["CONFIG"],
        bindings: {
          TEST_MIGRATIONS: migrations,
          TELEMETRY_FILTER: "off",
          ...(turso ? { STORAGE_MODE: "turso", TURSO_URL: "libsql://simule.test", TURSO_AUTH_TOKEN: "simule" } : {}),
          SESSION_SECRET: "test-session-secret-0123456789abcdef0123456789",
          PROVIDER_ENC_KEY: "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
          OPS_TOKEN: "test-ops-token-0123456789abcdef0123456789",
          TMDB_API_KEY: "test-tmdb-key-not-real",
          SIMKL_CLIENT_ID: "test-simkl-id-not-real",
          SIMKL_CLIENT_SECRET: "test-simkl-secret-not-real",
          ADMIN_TOKEN: "test-admin-token-0123456789abcdef0123456789",
        },
      },
    }),
  ],
  test: { setupFiles: turso ? ["./test/setup.js", "./test/setup-turso.js"] : ["./test/setup.js"], include: ["test/**/*.test.js"], testTimeout: 30000 },
});
