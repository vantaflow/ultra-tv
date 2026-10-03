import { defineConfig } from "vitest/config";
import { cloudflareTest } from "@cloudflare/vitest-pool-workers";

// Secrets factices : aucun secret réel n'est jamais lu par les tests.
export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.toml" },
      miniflare: {
        bindings: {
          SESSION_SECRET: "test-session-secret-0123456789abcdef0123456789",
          PROVIDER_ENC_KEY: "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
          OPS_TOKEN: "test-ops-token-0123456789abcdef0123456789",
          ADMIN_TOKEN: "test-admin-token-0123456789abcdef0123456789",
        },
      },
    }),
  ],
  test: { include: ["test/**/*.test.js"] },
});
