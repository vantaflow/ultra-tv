import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { db } from "./db";
import { emptySource, getSource, listSources, migrateM3uToXtream, saveSource } from "./sources";

const url = "http://iptv.example.test:8080/get.php?username=alice&password=secret&type=m3u_plus&output=ts";

describe("migrateM3uToXtream", () => {
  beforeEach(async () => { await db.sources.clear(); });

  it("convertit une source M3U get.php existante, une seule fois", async () => {
    const id = await saveSource({ ...emptySource(), name: "STRONG", type: "m3u", m3uUrl: url, state: "error", error: "HTTP 884" });
    expect(await migrateM3uToXtream()).toBe(1);
    const s = (await getSource(id))!;
    expect(s).toMatchObject({ type: "xtream", server: "http://iptv.example.test:8080", username: "alice", password: "secret", m3uUrl: "", state: "new" });
    expect(await migrateM3uToXtream()).toBe(0);
  });

  it("ne touche ni les M3U ordinaires ni les sources Xtream", async () => {
    await saveSource({ ...emptySource(), name: "liste", type: "m3u", m3uUrl: "https://h.example.test/liste.m3u" });
    await saveSource({ ...emptySource(), name: "x", server: "http://h.example.test", username: "u", password: "p" });
    expect(await migrateM3uToXtream()).toBe(0);
    expect((await listSources()).map((s) => s.type).sort()).toEqual(["m3u", "xtream"]);
  });
});
