import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LIVE_RECONNECT_MS, LiveReconnect, STALL_MS } from "./reconnect";

function setup() {
  const hooks = { reload: vi.fn(), giveUp: vi.fn(), onReconnecting: vi.fn() };
  return { hooks, r: new LiveReconnect(hooks) };
}

describe("LiveReconnect", () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it("direct qui jouait, serveur qui ferme la session : se reconnecte", () => {
    const { hooks, r } = setup();
    r.onState("playing", true);
    r.onState("ended", true);
    expect(hooks.onReconnecting).toHaveBeenCalledWith(1);
    vi.advanceTimersByTime(LIVE_RECONNECT_MS[0]!);
    expect(hooks.reload).toHaveBeenCalledTimes(1);
  });

  it("direct qui jouait, erreur réseau : prise en charge, pas de repli de format", () => {
    const { hooks, r } = setup();
    r.onState("playing", true);
    expect(r.onError(true)).toBe(true);
    vi.advanceTimersByTime(1_000);
    expect(hooks.reload).toHaveBeenCalledTimes(1);
  });

  it("chaîne qui n'a jamais démarré : erreur laissée au repli habituel", () => {
    const { hooks, r } = setup();
    expect(r.onError(true)).toBe(false);
    vi.advanceTimersByTime(20_000);
    expect(hooks.reload).not.toHaveBeenCalled();
  });

  it("flux figé en chargement : reconnexion au bout de 12 s", () => {
    const { hooks, r } = setup();
    r.onState("playing", true);
    r.onState("buffering", true);
    vi.advanceTimersByTime(STALL_MS - 1_000);
    expect(hooks.onReconnecting).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1_000 + LIVE_RECONNECT_MS[0]!);
    expect(hooks.reload).toHaveBeenCalledTimes(1);
  });

  it("chargement qui repart avant le délai : pas de reconnexion", () => {
    const { hooks, r } = setup();
    r.onState("playing", true);
    r.onState("buffering", true);
    vi.advanceTimersByTime(3_000);
    r.onState("playing", true);
    vi.advanceTimersByTime(60_000);
    expect(hooks.reload).not.toHaveBeenCalled();
  });

  it("coupures répétées : abandon après les tentatives", () => {
    const { hooks, r } = setup();
    r.onState("playing", true);
    for (let i = 0; i < LIVE_RECONNECT_MS.length + 1; i++) {
      r.onState("ended", true);
      vi.advanceTimersByTime(11_000);
    }
    expect(hooks.reload).toHaveBeenCalledTimes(LIVE_RECONNECT_MS.length);
    expect(hooks.giveUp).toHaveBeenCalledTimes(1);
  });

  it("30 s de lecture stable : le compteur repart de zéro", () => {
    const { hooks, r } = setup();
    r.onState("playing", true);
    r.onState("ended", true);
    vi.advanceTimersByTime(1_000);
    r.onState("playing", true);
    vi.advanceTimersByTime(31_000);
    r.onState("ended", true);
    expect(hooks.onReconnecting).toHaveBeenLastCalledWith(1);
  });

  it("vidéo à la demande : jamais de reconnexion", () => {
    const { hooks, r } = setup();
    r.onState("playing", false);
    r.onState("ended", false);
    expect(r.onError(false)).toBe(false);
    vi.advanceTimersByTime(20_000);
    expect(hooks.reload).not.toHaveBeenCalled();
  });

  it("changement de chaîne : la tentative en attente est annulée", () => {
    const { hooks, r } = setup();
    r.onState("playing", true);
    r.onState("ended", true);
    r.reset();
    vi.advanceTimersByTime(20_000);
    expect(hooks.reload).not.toHaveBeenCalled();
  });
});
