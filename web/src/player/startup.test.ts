import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { STARTUP_MAX_MS, STARTUP_MS, StartupWatchdog, type StartupProbe } from "./startup";

describe("StartupWatchdog", () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  function setup(initial: StartupProbe = { ready: false, progress: 0 }) {
    const state = { ...initial };
    const onStall = vi.fn();
    const w = new StartupWatchdog(() => ({ ...state }), onStall);
    return { state, onStall, w };
  }

  it("flux qui ne démarre jamais : signalé après le délai, pas avant", () => {
    const { onStall, w } = setup();
    w.start();
    vi.advanceTimersByTime(STARTUP_MS - 1);
    expect(onStall).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(onStall).toHaveBeenCalledTimes(1);
  });

  it("lecture démarrée avant le délai (stop) : aucun signalement", () => {
    const { onStall, w } = setup();
    w.start();
    vi.advanceTimersByTime(5_000);
    w.stop();
    vi.advanceTimersByTime(120_000);
    expect(onStall).not.toHaveBeenCalled();
  });

  it("données suffisantes à l'échéance (lecture auto refusée) : pas une erreur", () => {
    const { state, onStall, w } = setup();
    w.start();
    state.ready = true;
    vi.advanceTimersByTime(STARTUP_MS);
    expect(onStall).not.toHaveBeenCalled();
  });

  it("démarrage lent mais qui progresse : prolongé, puis figé dès qu'il ne progresse plus", () => {
    const { state, onStall, w } = setup();
    w.start();
    for (let i = 1; i <= 3; i++) { state.progress = i; vi.advanceTimersByTime(STARTUP_MS); }
    expect(onStall).not.toHaveBeenCalled();
    vi.advanceTimersByTime(STARTUP_MS);
    expect(onStall).toHaveBeenCalledTimes(1);
  });

  it("plafond absolu : un chargement qui progresse sans fin finit par être signalé", () => {
    const { state, onStall, w } = setup();
    w.start();
    for (let t = 0; t < STARTUP_MAX_MS + STARTUP_MS && onStall.mock.calls.length === 0; t += STARTUP_MS) { state.progress += 1; vi.advanceTimersByTime(STARTUP_MS); }
    expect(onStall).toHaveBeenCalledTimes(1);
  });

  it("start() réarme : l'ancien délai ne se déclenche pas en double", () => {
    const { onStall, w } = setup();
    w.start();
    vi.advanceTimersByTime(10_000);
    w.start();
    vi.advanceTimersByTime(STARTUP_MS);
    expect(onStall).toHaveBeenCalledTimes(1);
  });
});
