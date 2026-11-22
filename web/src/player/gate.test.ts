import "fake-indexeddb/auto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { db } from "@/db/db";
import { setCategoryLock } from "@/db/contentGate";
import type { CategoryRow, ChannelRow, MovieRow } from "@/db/types";
import { usePrefs } from "@/state/prefs";
import { useUnlocks } from "@/state/unlocks";
import { makeOpenGate } from "./gate";
import { setOpenGate, usePlayer } from "./store";
import type { PlayTarget } from "./resolve";

const CID = 4;
const cat = (kind: "live" | "movie", extId: string, name: string): CategoryRow => ({ sourceId: CID, kind, extId, name, label: name, badge: null, count: 1, enabled: 1, adult: 0, ord: 0 });
const ch = (streamId: number, catExt: string, ord: number) => ({ sourceId: CID, catExt, ord, streamId, num: ord, name: `c${streamId}`, display: `c${streamId}`, norm: "", country: null, q: 0, flags: 0, sep: 0, logo: null, epg: null, archive: 0 }) as ChannelRow;
const live = (c: ChannelRow): PlayTarget => ({ kind: "live", sourceId: 1, cid: CID, refId: c.streamId, title: c.display, channel: { ord: c.ord, catExt: c.catExt, num: c.num, epg: null, archive: false, q: 0, logo: null } });
const flush = () => new Promise((r) => setTimeout(r, 20));

let ask = vi.fn(async (_label: string) => false);
beforeEach(async () => {
  await Promise.all([db.categories.clear(), db.channels.clear(), db.movies.clear(), db.settings.clear(), db.profileHidden.clear()]);
  await db.categories.bulkAdd([cat("live", "news", "News"), cat("live", "kid", "Kids"), cat("movie", "m", "Films")]);
  await db.channels.bulkAdd([ch(1, "news", 1), ch(2, "kid", 2), ch(3, "news", 3)]);
  await db.movies.add({ sourceId: CID, catExt: "m", ord: 0, streamId: 50, name: "F", title: "F", norm: "", year: null, poster: null, rating: 0, ext: "mp4", added: 0 } as MovieRow);
  await setCategoryLock(1, "live", "kid", true);
  await setCategoryLock(1, "movie", "m", true);
  usePrefs.setState({ profiles: [{ id: "main", name: "", color: "#000" }], profileId: "main" });
  useUnlocks.getState().clear();
  usePlayer.setState({ target: null, mode: "closed", zapCat: null, zapMix: null });
  ask = vi.fn(async () => false);
  setOpenGate(makeOpenGate((l) => ask(l)));
});
afterEach(() => setOpenGate(null));

describe("lecteur : porte du code parental", () => {
  it("chaîne libre : lecture lancée sans demande", async () => {
    usePlayer.getState().open(live(ch(1, "news", 1)));
    await flush();
    expect(usePlayer.getState().target?.refId).toBe(1);
    expect(ask).not.toHaveBeenCalled();
  });
  it("chaîne d'une catégorie verrouillée : rien sans code, lecture après le code", async () => {
    usePlayer.getState().open(live(ch(2, "kid", 2)));
    await flush();
    expect(usePlayer.getState().target).toBeNull();
    expect(ask).toHaveBeenCalledWith("c2");
    ask = vi.fn(async () => true);
    usePlayer.getState().open(live(ch(2, "kid", 2)));
    await flush();
    expect(usePlayer.getState().target?.refId).toBe(2);
  });
  it("film d'une catégorie verrouillée (reprise, favoris, recherche) : code exigé", async () => {
    usePlayer.getState().open({ kind: "movie", sourceId: 1, cid: CID, refId: 50, title: "F" });
    await flush();
    expect(usePlayer.getState().target).toBeNull();
    expect(ask).toHaveBeenCalledOnce();
  });
  it("zapping : la chaîne verrouillée est sautée", async () => {
    usePlayer.getState().open(live(ch(1, "news", 1)));
    await flush();
    await usePlayer.getState().zap(1);
    expect(usePlayer.getState().target?.refId).toBe(3);
    expect(ask).not.toHaveBeenCalled();
  });
});
