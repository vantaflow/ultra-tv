import { describe, expect, it } from "vitest";
import { parseStartScreen, rememberablePath, startPath } from "./startScreen";

describe("écran d'ouverture", () => {
  it("rubrique choisie", () => {
    expect(startPath("guide", null)).toBe("/guide");
    expect(startPath("live", "/movies")).toBe("/live");
    expect(startPath("home", "/guide")).toBeNull();
  });
  it("dernier écran", () => {
    expect(startPath("last", "/guide")).toBe("/guide");
    expect(startPath("last", "/sport")).toBe("/sport");
    expect(startPath("last", "/")).toBeNull();
    expect(startPath("last", null)).toBeNull();
    expect(startPath("last", "/settings/display")).toBeNull();
    expect(startPath("last", "/movie/12")).toBeNull();
  });
  it("chemins mémorisables et valeur inconnue", () => {
    expect(rememberablePath("/search")).toBeNull();
    expect(rememberablePath("/favorites")).toBe("/favorites");
    expect(parseStartScreen("nimporte")).toBe("home");
    expect(parseStartScreen("last")).toBe("last");
  });
});
