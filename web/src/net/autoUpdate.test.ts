import { describe, expect, it } from "vitest";
import { readAutoUpdate, writeAutoUpdate } from "./autoUpdate";

describe("réglage de vérification automatique des mises à jour", () => {
  it("hors application de bureau : pas de réglage", async () => {
    expect(await readAutoUpdate(undefined)).toBeNull();
    expect(await readAutoUpdate({})).toBeNull();
  });
  it("activé par défaut, même si le processus principal échoue", async () => {
    expect(await readAutoUpdate({ getAutoUpdate: async () => { throw new Error("x"); }, setAutoUpdate: async () => true })).toBe(true);
  });
  it("relit la valeur mémorisée par le processus principal", async () => {
    const b = { getAutoUpdate: async () => false, setAutoUpdate: async (v: boolean) => v };
    expect(await readAutoUpdate(b)).toBe(false);
  });
  it("écrit le réglage dans le processus principal", async () => {
    let got: boolean | null = null;
    const b = { setAutoUpdate: async (v: boolean) => { got = v; return v; } };
    expect(await writeAutoUpdate(false, b)).toBe(false);
    expect(got).toBe(false);
  });
});
