import type { ChannelRow, MovieRow, Source } from "@/db/types";
import type { PlayTarget } from "./resolve";
import { viewOf } from "@/state/channelView";

export const channelTarget = (s: Source, c: ChannelRow): PlayTarget => ({
  kind: "live", sourceId: s.id!, cid: s.cid, refId: c.streamId, title: c.display, image: c.logo, url: c.url, protectedStream: !!c.drm,
  // Numéro AFFICHÉ pour le profil courant (chaînes qu'il masque déduites de la numérotation de sa catégorie).
  channel: { ord: c.ord, catExt: c.catExt, num: viewOf(s.id).num(c), epg: c.epg, archive: !!c.archive, q: c.q, logo: c.logo },
});

export const movieTarget = (s: Source, m: Pick<MovieRow, "streamId" | "title" | "poster" | "ext"> & { url?: string }, startAt?: number): PlayTarget => ({
  kind: "movie", sourceId: s.id!, cid: s.cid, refId: m.streamId, title: m.title, image: m.poster, ext: m.ext, startAt, url: m.url,
});
