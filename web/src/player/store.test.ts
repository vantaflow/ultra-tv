import "fake-indexeddb/auto";
import { describe, expect, it } from "vitest";
import { sameRect, usePlayer } from "./store";

describe("sameRect / setSlot", () => {
  it("mesures identiques (à moins d'un demi-pixel) : même emplacement", () => {
    const a = { x: 10, y: 20, w: 300, h: 170 };
    expect(sameRect(a, { ...a, x: 10.3 })).toBe(true);
    expect(sameRect(a, { ...a, w: 301 })).toBe(false);
    expect(sameRect(null, null)).toBe(true);
    expect(sameRect(a, null)).toBe(false);
  });

  it("setSlot ne remplace pas l'objet quand la mesure ne change pas", () => {
    usePlayer.getState().setSlot({ x: 1, y: 2, w: 3, h: 4 });
    const first = usePlayer.getState().slot;
    usePlayer.getState().setSlot({ x: 1, y: 2, w: 3, h: 4 });
    expect(usePlayer.getState().slot).toBe(first);
    usePlayer.getState().setSlot({ x: 1, y: 2, w: 30, h: 4 });
    expect(usePlayer.getState().slot).not.toBe(first);
  });
});
