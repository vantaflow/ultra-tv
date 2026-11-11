import { describe, expect, it } from "vitest";
import { parseM3u } from "./m3u";
import { candidates } from "@/player/resolve";
import { channelTarget } from "@/player/targets";
import type { ChannelRow, Source } from "@/db/types";

const wv = `#EXTM3U
#EXTINF:-1 tvg-id="a",Protégée
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://lic.invalid/wv
#EXTVLCOPT:http-user-agent=Agent
http://serveur-fictif.invalid/a.mpd
#EXTINF:-1,Ordinaire
http://serveur-fictif.invalid/b.ts
#KODIPROP:inputstream.adaptive.license_type=clearkey
#EXTINF:-1,Propriétés avant
http://serveur-fictif.invalid/c.mpd
#EXTINF:-1,Type vide
#KODIPROP:inputstream.adaptive.license_type=
http://serveur-fictif.invalid/d.ts
`;

describe("M3U : flux protégés", () => {
  it("marque les flux protégés (propriétés après ou avant #EXTINF) et pas les autres", () => {
    const e = parseM3u(wv);
    expect(e.map((x) => x.protected)).toEqual([true, false, true, false]);
    expect(e[0]!.userAgent).toBe("Agent");
  });

  it("aucun candidat de lecture pour un flux protégé", () => {
    const src = { id: 1, cid: 1, type: "m3u" } as unknown as Source;
    const row = { streamId: 1, display: "P", logo: null, url: "http://serveur-fictif.invalid/a.mpd", ord: 0, catExt: null, num: 1, epg: null, archive: 0, q: 0, drm: 1 } as unknown as ChannelRow;
    const t = channelTarget(src, row);
    expect(t.protectedStream).toBe(true);
    expect(candidates(src, t, { liveFormat: "m3u8", preferMp4: false })).toEqual([]);
    expect(channelTarget(src, { ...row, drm: undefined }).protectedStream).toBe(false);
  });
});
