import { describe, expect, it } from "vitest";
import { nativeLimiter } from "../src/index.js";

const rl = (n) => ({ n, limit: async () => ({ success: true }) });
const env = { RL_10: rl(10), RL_30: rl(30), RL_60: rl(60), RL_120: rl(120) };

describe("nativeLimiter", () => {
  it("choisit le plus petit palier qui couvre la limite ramenée à la minute (marge ×4)", () => {
    expect(nativeLimiter(env, 60, 600, true).n).toBe(30);    // 6 / min → 24
    expect(nativeLimiter(env, 120, 600, true).n).toBe(60);   // 12 / min → 48 (TMDB par appareil)
    expect(nativeLimiter(env, 300, 600, true).n).toBe(120);  // 30 / min → 120 (TMDB par IP)
    expect(nativeLimiter(env, 60, 3600, true).n).toBe(10);   // télémétrie : 1 / min → 4, plancher 10
  });
  it("garde le Durable Object pour la connexion / l'inscription du site (fenêtre longue, HTML)", () => {
    expect(nativeLimiter(env, 5, 3600, false)).toBeNull();
    expect(nativeLimiter(env, 20, 900, false)).toBeNull();
  });
  it("sans liaison native (tests, auto-hébergement ancien) : Durable Object", () => {
    expect(nativeLimiter({}, 60, 600, true)).toBeNull();
  });
});

import { etagMatches } from "../src/index.js";
describe("etagMatches", () => {
  it("accepte la forme forte, la forme faible (compression Cloudflare) et les listes", () => {
    expect(etagMatches('"v2"', '"v2"')).toBe(true);
    expect(etagMatches('W/"v2"', '"v2"')).toBe(true);
    expect(etagMatches('"v1", W/"v2"', '"v2"')).toBe(true);
    expect(etagMatches('W/"v1"', '"v2"')).toBe(false);
    expect(etagMatches(null, '"v2"')).toBe(false);
  });
});
