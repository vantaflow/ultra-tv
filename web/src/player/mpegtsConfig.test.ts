import { describe, expect, it } from "vitest";
import { mpegtsConfig } from "./engine";

describe("mpegtsConfig", () => {
  it("direct : rattrapage doux (saut au-delà de 30 s, 6 s de réserve gardées)", () => {
    const c = mpegtsConfig({ live: true });
    expect(c.liveBufferLatencyChasing).toBe(true);
    expect(c.liveBufferLatencyMaxLatency).toBeGreaterThanOrEqual(20);
    expect(c.liveBufferLatencyMinRemain).toBeGreaterThanOrEqual(4);
    expect(c.liveBufferLatencyMinRemain).toBeLessThan(c.liveBufferLatencyMaxLatency);
  });
  it("flux continu (conversion du bureau) et VOD : jamais de rattrapage", () => {
    expect(mpegtsConfig({ live: true, continuous: true }).liveBufferLatencyChasing).toBe(false);
    expect(mpegtsConfig({ live: false }).liveBufferLatencyChasing).toBe(false);
  });
});
