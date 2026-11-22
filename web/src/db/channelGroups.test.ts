import "fake-indexeddb/auto";
import { beforeEach, describe, expect, it } from "vitest";
import { db } from "./db";
import { addGroup, addToChannelGroup, deleteChannelGroup, groupChannels, listGroups, removeFromChannelGroup, renameChannelGroup } from "./channelGroups";
import { orderedList } from "./channelOrder";
import { deleteProfileData } from "./profileData";
import type { ChannelRow } from "./types";
import { groupScope } from "@/lib/channelGroups";
import { usePlayer } from "@/player/store";
import { usePrefs } from "@/state/prefs";

const CID = 3;
const ch = (streamId: number, ord: number) => ({ sourceId: CID, catExt: "c", ord, streamId, num: ord, name: `c${streamId}`, display: `c${streamId}`, norm: "", country: null, q: 0, flags: 0, sep: 0, logo: null, epg: null, archive: 0 }) as ChannelRow;

beforeEach(async () => {
  await Promise.all([db.settings.clear(), db.channels.clear(), db.profileHiddenChannels.clear()]);
  await db.channels.bulkAdd([ch(1, 1), ch(2, 2), ch(3, 3), ch(4, 4)]);
  usePrefs.setState({ profiles: [{ id: "main", name: "", color: "#000" }, { id: "p2", name: "B", color: "#111" }], profileId: "main" });
});

describe("groupes de chaînes : stockage par profil et par source", () => {
  it("créer, ajouter, renommer, retirer, supprimer", async () => {
    const id = (await addGroup("main", 1, "Sport", [3]))!;
    expect(id).toBeTruthy();
    await addToChannelGroup("main", 1, id, [1, 3]);
    await renameChannelGroup("main", 1, id, "Foot");
    expect(await listGroups("main", 1)).toEqual([{ id, name: "Foot", ids: [3, 1] }]);
    expect((await groupChannels("main", 1, CID, groupScope(id))).map((c) => c.streamId)).toEqual([3, 1]);
    await removeFromChannelGroup("main", 1, id, [3]);
    expect((await listGroups("main", 1))[0]!.ids).toEqual([1]);
    await deleteChannelGroup("main", 1, id);
    expect(await listGroups("main", 1)).toEqual([]);
  });
  it("propres au profil et à la source ; supprimés avec le profil", async () => {
    await addGroup("main", 1, "A", [1]);
    expect(await listGroups("p2", 1)).toEqual([]);
    expect(await listGroups("main", 2)).toEqual([]);
    await addGroup("p2", 1, "B", [2]);
    await deleteProfileData("p2");
    expect(await listGroups("p2", 1)).toEqual([]);
    expect((await listGroups("main", 1)).length).toBe(1);
  });
  it("liste ordonnée et zapping suivent le groupe", async () => {
    const id = (await addGroup("main", 1, "Z", [4, 2]))!;
    expect((await orderedList("main", 1, CID, groupScope(id)))!.map((c) => c.streamId)).toEqual([4, 2]);
    usePlayer.setState({ zapCat: null, zapMix: null });
    usePlayer.getState().open({ kind: "live", sourceId: 1, cid: CID, refId: 4, title: "c4", channel: { ord: 4, catExt: "c", num: 4, epg: null, archive: false, q: 0, logo: null } }, "full", groupScope(id));
    await usePlayer.getState().zap(1);
    expect(usePlayer.getState().target?.refId).toBe(2);
  });
});
