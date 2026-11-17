import { describe, expect, it } from "vitest";
import { urlInfo } from "./playlog";

describe("urlInfo : jamais d'identifiants dans le journal", () => {
  it("garde hôte, extension et genre de chemin seulement", () => {
    const i = urlInfo("http://h.tld:8080/movie/jean/secret/123.mkv?token=abc");
    expect(i).toEqual({ host: "h.tld:8080", ext: "mkv", path: "movie" });
    expect(JSON.stringify(i)).not.toMatch(/jean|secret|abc/);
    expect(urlInfo("http://h/live/u/p/1.ts").path).toBe("live");
    expect(urlInfo("http://h/x/y.m3u8").path).toBe("stream");
    expect(urlInfo("pas une adresse")).toEqual({ host: "", ext: "", path: "other" });
  });
});
