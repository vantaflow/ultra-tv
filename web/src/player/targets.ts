import type { ChannelRow, MovieRow, Source } from "@/db/types";
import type { PlayTarget } from "./resolve";

export const channelTarget = (s: Source, c: ChannelRow): PlayTarget => ({
  kind: "live", sourceId: s.id!, cid: s.cid, refId: c.streamId, title: c.display, image: c.logo, url: c.url,
  channel: { ord: c.ord, catExt: c.catExt, num: c.num, epg: c.epg, archive: !!c.archive, q: c.q, logo: c.logo },
});

export const movieTarget = (s: Source, m: Pick<MovieRow, "streamId" | "title" | "poster" | "ext">, startAt?: number): PlayTarget => ({
  kind: "movie", sourceId: s.id!, cid: s.cid, refId: m.streamId, title: m.title, image: m.poster, ext: m.ext, startAt,
});
