import { describe, expect, it } from "vitest";
import { cancelMove, commitMove, insertBefore, mergeOrder, moveBy, neighborOf, nextOf, startMove, stepMove } from "./channelOrder";

const id = (x: number) => x;

describe("mergeOrder", () => {
  it("sans ordre enregistré : ordre de la source", () => {
    expect(mergeOrder([1, 2, 3], undefined, id)).toEqual([1, 2, 3]);
    expect(mergeOrder([1, 2, 3], [], id)).toEqual([1, 2, 3]);
  });
  it("applique l'ordre choisi", () => { expect(mergeOrder([1, 2, 3, 4], [3, 1, 2, 4], id)).toEqual([3, 1, 2, 4]); });
  it("chaînes nouvelles : après les placées, ordre de la source conservé", () => {
    expect(mergeOrder([1, 5, 2, 6, 3], [3, 1, 2], id)).toEqual([3, 1, 2, 5, 6]);
  });
  it("chaînes disparues : ignorées", () => { expect(mergeOrder([1, 3], [9, 3, 8, 1], id)).toEqual([3, 1]); });
  it("doublons dans l'ordre enregistré : première place retenue", () => { expect(mergeOrder([1, 2], [2, 1, 2], id)).toEqual([2, 1]); });
});

describe("moveBy", () => {
  it("monte et descend", () => {
    expect(moveBy([1, 2, 3], 3, -1)).toEqual([1, 3, 2]);
    expect(moveBy([1, 2, 3], 1, 1)).toEqual([2, 1, 3]);
  });
  it("borné aux extrémités, id absent ou delta nul : inchangé (même référence)", () => {
    const a = [1, 2, 3];
    expect(moveBy(a, 1, -1)).toBe(a);
    expect(moveBy(a, 3, 5)).toBe(a);
    expect(moveBy(a, 9, 1)).toBe(a);
    expect(moveBy(a, 2, 0)).toBe(a);
    expect(moveBy(a, 1, 10)).toEqual([2, 3, 1]);
  });
});

describe("insertBefore", () => {
  it("insère avant la cible, vers le haut et vers le bas", () => {
    expect(insertBefore([1, 2, 3, 4], 4, 2)).toEqual([1, 4, 2, 3]);
    expect(insertBefore([1, 2, 3, 4], 1, 4)).toEqual([2, 3, 1, 4]);
  });
  it("cible nulle : à la fin", () => { expect(insertBefore([1, 2, 3], 1, null)).toEqual([2, 3, 1]); });
  it("sur soi-même ou devant sa suivante : inchangé", () => {
    const a = [1, 2, 3];
    expect(insertBefore(a, 2, 2)).toBe(a);
    expect(insertBefore(a, 2, 3)).toBe(a);
    expect(insertBefore(a, 3, null)).toBe(a);
  });
  it("id inconnu : inchangé ; cible inconnue : à la fin", () => {
    const a = [1, 2, 3];
    expect(insertBefore(a, 9, 1)).toBe(a);
    expect(insertBefore(a, 1, 9)).toEqual([2, 3, 1]);
  });
  it("nextOf donne la cible d'un dépôt « après »", () => {
    expect(nextOf([1, 2, 3], 1)).toBe(2);
    expect(nextOf([1, 2, 3], 3)).toBeNull();
    expect(insertBefore([1, 2, 3], 1, nextOf([1, 2, 3], 2))).toEqual([2, 1, 3]);
    expect(insertBefore([1, 2, 3], 1, nextOf([1, 2, 3], 3))).toEqual([2, 3, 1]);
  });
});

describe("déplacement au clavier", () => {
  it("annulation : retrouve l'ordre d'avant, sans rien écrire", () => {
    const m = stepMove(stepMove(startMove("c", 2, [1, 2, 3], null), 1), 1);
    expect(m.ids).toEqual([1, 3, 2]);
    expect(cancelMove(m)).toBeNull();
    expect(cancelMove(startMove("c", 2, [3, 2, 1], [3, 2, 1]))).toEqual([3, 2, 1]);
  });
  it("validation : ordre à enregistrer, ou null si rien n'a changé", () => {
    const natural = [1, 2, 3];
    expect(commitMove(stepMove(startMove("c", 3, natural, null), -1), natural)).toEqual([1, 3, 2]);
    expect(commitMove(startMove("c", 3, natural, null), natural)).toBeNull();
    expect(commitMove(stepMove(stepMove(startMove("c", 3, natural, null), -1), 1), natural)).toBeNull();
  });
  it("ordre déjà enregistré avec des disparues : rien de changé = rien à écrire", () => {
    expect(commitMove(startMove("c", 1, [3, 1, 2], [3, 9, 1]), [1, 2, 3])).toBeNull();
  });
});

describe("neighborOf (file de zapping)", () => {
  it("suit l'ordre fusionné, pas celui de la source", () => {
    const queue = mergeOrder([1, 2, 3, 4], [3, 1], id); // 3, 1, 2, 4
    expect(neighborOf(queue, 3, 1, id)).toBe(1);
    expect(neighborOf(queue, 1, 1, id)).toBe(2);
    expect(neighborOf(queue, 1, -1, id)).toBe(3);
    expect(neighborOf(queue, 4, -1, id)).toBe(2);
  });
  it("extrémités et chaîne absente : null", () => {
    expect(neighborOf([1, 2], 1, -1, id)).toBeNull();
    expect(neighborOf([1, 2], 2, 1, id)).toBeNull();
    expect(neighborOf([1, 2], 9, 1, id)).toBeNull();
  });
});
