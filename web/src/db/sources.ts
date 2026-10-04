// Sources : les identifiants sont chiffrés au repos (voir net/secrets.ts) et déchiffrés à la lecture.

import { db } from "./db";
import type { Source } from "./types";
import { decryptSecret, encryptSecret } from "@/net/secrets";

async function open(s: Source): Promise<Source> {
  return { ...s, username: await decryptSecret(s.username), password: await decryptSecret(s.password), m3uUrl: await decryptSecret(s.m3uUrl) };
}
async function seal(s: Source): Promise<Source> {
  return { ...s, username: await encryptSecret(s.username), password: await encryptSecret(s.password), m3uUrl: await encryptSecret(s.m3uUrl) };
}

export const emptySource = (): Source => ({
  name: "", type: "xtream", server: "", username: "", password: "", m3uUrl: "", epgUrl: "", userAgent: "", referer: "",
  langs: null, createdAt: Date.now(), lastSyncAt: 0, counts: { live: 0, movie: 0, series: 0 }, expDate: null, maxConnections: 1, state: "new",
});

export async function listSources(): Promise<Source[]> {
  const rows = await db.sources.toArray();
  return Promise.all(rows.map(open));
}
export async function getSource(id: number): Promise<Source | undefined> {
  const r = await db.sources.get(id);
  return r ? open(r) : undefined;
}
export async function saveSource(s: Source): Promise<number> {
  return db.sources.put(await seal(s));
}
export async function deleteSource(id: number): Promise<void> {
  const tables = [db.sources, db.categories, db.channels, db.movies, db.series, db.programs, db.favorites, db.history];
  await db.transaction("rw", tables, async () => {
    await db.categories.where("[sourceId+kind]").between([id, ""], [id, "￿"]).delete();
    await db.channels.where("[sourceId+ord]").between([id, -1], [id, Infinity]).delete();
    await db.movies.where("[sourceId+ord]").between([id, -1], [id, Infinity]).delete();
    await db.series.where("[sourceId+ord]").between([id, -1], [id, Infinity]).delete();
    await db.programs.where("[sourceId+end]").between([id, 0], [id, Infinity]).delete();
    await db.favorites.where("[sourceId+kind]").between([id, ""], [id, "￿"]).delete();
    await db.history.where("[sourceId+kind]").between([id, ""], [id, "￿"]).delete();
    await db.sources.delete(id);
  });
}
