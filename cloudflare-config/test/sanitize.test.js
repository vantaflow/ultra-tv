import { describe, it, expect } from "vitest";
import { sanitizeText } from "../src/sanitize.js";

describe("nettoyage des identifiants dans les journaux", () => {
  it.each([
    ["http://h.tv:8080/get.php?username=bob&password=hunter2&type=m3u", ["bob", "hunter2"]],
    ["GET http://h.tv/player_api.php?password=hunter2&username=bob", ["bob", "hunter2"]],
    ["http://bob:hunter2@h.tv/list.m3u", ["bob", "hunter2"]],
    ["http://h.tv:80/live/bob/hunter2/1234.ts", ["bob", "hunter2"]],
    ["https://h.tv/movie/bob/hunter2/55.mkv", ["bob", "hunter2"]],
    ["https://h.tv/list.m3u?token=abcdef123456", ["abcdef123456"]],
    ["fail user=bob pass=hunter2", ["bob", "hunter2"]],
    ["Authorization: Bearer utv_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", ["utv_AAAA"]],
  ])("sanitizeText_%s_neContientPlusLesSecrets", (input, secrets) => {
    const out = sanitizeText(input);
    for (const s of secrets) expect(out).not.toContain(s);
  });
  it("sanitizeText_texteBanal_inchange", () => {
    expect(sanitizeText("Player error 404 on channel 12")).toBe("Player error 404 on channel 12");
  });
  it("sanitizeText_tropLong_tronque", () => {
    expect(sanitizeText("a".repeat(10000), 100).length).toBeLessThanOrEqual(100);
  });
  it("sanitizeText_nonChaine_vide", () => {
    expect(sanitizeText(undefined)).toBe("");
  });
  it("sanitizeText_caracteresDeControle_retires", () => {
    expect(sanitizeText("a\u0000b\u001bc")).toBe("abc");
  });
});
