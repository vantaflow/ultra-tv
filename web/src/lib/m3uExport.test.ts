import { describe, expect, it } from "vitest";
import { buildM3u, countExportable, isExportable, m3uFileName } from "./m3uExport";

describe("export M3U des favoris", () => {
  it("écrit le format standard", () => {
    expect(buildM3u([{ name: "BBC One", url: "http://s/u/p/1.ts", tvgId: "bbc1", logo: "http://l/x.png", group: "News", num: 12 }])).toBe(
      '#EXTM3U\n#EXTINF:-1 tvg-id="bbc1" tvg-name="BBC One" tvg-logo="http://l/x.png" tvg-chno="12" group-title="News",BBC One\nhttp://s/u/p/1.ts\n',
    );
  });

  it("liste vide : seulement l'entête", () => {
    expect(buildM3u([])).toBe("#EXTM3U\n");
  });

  it("omet les attributs vides", () => {
    const out = buildM3u([{ name: "Arte", url: "https://s/2", tvgId: "", logo: null, group: " ", num: 0 }]);
    for (const a of ["tvg-id", "tvg-logo", "group-title", "tvg-chno"]) expect(out).not.toContain(a);
  });

  it("guillemets et sauts de ligne ne cassent pas le format", () => {
    const out = buildM3u([{ name: 'Canal "Plus"\nSport', url: "http://s/3", group: 'A"B\nC' }]);
    const lines = out.trimEnd().split("\n");
    expect(lines).toHaveLength(3);
    expect(lines[1]).toContain(`group-title="A'B C"`);
    expect(lines[1]!.endsWith(',Canal "Plus" Sport')).toBe(true);
  });

  it("ignore les adresses internes, garde l'ordre", () => {
    const e = [{ name: "A", url: "http://s/a" }, { name: "S", url: "stalker:abc" }, { name: "B", url: "rtmp://s/b" }, { name: "V", url: "" }];
    const out = buildM3u(e);
    expect(countExportable(e)).toBe(2);
    expect(out).not.toContain("stalker");
    expect(out.indexOf("http://s/a")).toBeLessThan(out.indexOf("rtmp://s/b"));
  });

  it("nom vide : tiret", () => {
    expect(buildM3u([{ name: "  ", url: "http://s/x" }])).toContain(",-\n");
  });

  it("isExportable", () => {
    expect(isExportable("HTTPS://x/y")).toBe(true);
    expect(isExportable("stalker:1")).toBe(false);
    expect(isExportable(undefined)).toBe(false);
  });

  it("nom de fichier daté", () => {
    expect(m3uFileName(new Date(2026, 10, 5))).toBe("ultra-tv-favorites-20261105.m3u");
  });
});
