import { describe, expect, it } from "vitest";
import { createOsdIdle } from "./osdIdle";

describe("osdIdle", () => {
  it("mousemove sans déplacement : rien", () => {
    const o = createOsdIdle();
    o.move(100, 100, 0, 0, 0);
    expect(o.move(100, 100, 0, 0, 10)).toBe(false);
  });
  it("déplacement de 1 px sous le seuil de 3 : rien", () => {
    const o = createOsdIdle({ thresholdPx: 3 });
    o.move(100, 100, 0, 0, 0);
    expect(o.move(101, 100, 1, 0, 10)).toBe(false);
    expect(o.move(100, 101, 0, 1, 20)).toBe(false);
  });
  it("déplacement réel : affichage, et la dérive lente finit par compter", () => {
    const o = createOsdIdle({ thresholdPx: 3 });
    o.move(100, 100, 0, 0, 0);
    expect(o.move(110, 100, 10, 0, 10)).toBe(true);
    expect(o.move(111, 100, 1, 0, 20)).toBe(false);
    expect(o.move(114, 100, 1, 0, 30)).toBe(true);
  });
  it("masquage après 3 s d'inactivité", () => {
    const o = createOsdIdle();
    o.activity(1000);
    expect(o.shouldHide(3999)).toBe(false);
    expect(o.remaining(3999)).toBe(1);
    expect(o.shouldHide(4000)).toBe(true);
  });
  it("pas de masquage si menu ouvert ou survol/focus de la barre", () => {
    const o = createOsdIdle();
    o.activity(0);
    o.setMenuOpen(true);
    expect(o.shouldHide(10_000)).toBe(false);
    o.setMenuOpen(false);
    o.setHold(true);
    expect(o.shouldHide(10_000)).toBe(false);
    o.setHold(false);
    expect(o.shouldHide(10_000)).toBe(true);
  });
  it("une touche ou un clic relance le délai", () => {
    const o = createOsdIdle();
    o.activity(0);
    o.activity(2500);
    expect(o.shouldHide(5000)).toBe(false);
    expect(o.shouldHide(5500)).toBe(true);
  });
});
