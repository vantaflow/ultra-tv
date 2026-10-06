import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { idleSignal } from "./transport";

describe("idleSignal", () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it("abandonne après le délai d'inactivité, avec une TimeoutError", () => {
    const s = idleSignal(undefined, 1_000);
    vi.advanceTimersByTime(999);
    expect(s.signal.aborted).toBe(false);
    vi.advanceTimersByTime(1);
    expect(s.signal.aborted).toBe(true);
    expect((s.signal.reason as DOMException).name).toBe("TimeoutError");
  });

  it("touch() réarme le délai ; stop() le désarme", () => {
    const s = idleSignal(undefined, 1_000);
    vi.advanceTimersByTime(800); s.touch();
    vi.advanceTimersByTime(800);
    expect(s.signal.aborted).toBe(false);
    s.stop();
    vi.advanceTimersByTime(10_000);
    expect(s.signal.aborted).toBe(false);
  });

  it("l'annulation de l'appelant se propage telle quelle", () => {
    const parent = new AbortController();
    const s = idleSignal(parent.signal, 60_000);
    parent.abort();
    expect(s.signal.aborted).toBe(true);
    expect((s.signal.reason as DOMException).name).toBe("AbortError");
    expect(idleSignal(parent.signal, 1_000).signal.aborted).toBe(true);
  });
});
