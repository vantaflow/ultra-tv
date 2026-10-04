// Sources : les identifiants sont chiffrés au repos (voir net/secrets.ts) et déchiffrés à la lecture.

import { clearCatalog, db } from "./db";
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
  cid: 0, langs: null, createdAt: Date.now(), lastSyncAt: 0, counts: { live: 0, movie: 0, series: 0 }, expDate: null, maxConnections: 1, state: "new",
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
  const src = await db.sources.get(id);
  if (src?.cid) await clearCatalog(src.cid);
  await db.favorites.where("addedAt").above(-1).filter((f) => f.sourceId === id).delete();
  await db.history.where("updatedAt").above(-1).filter((h) => h.sourceId === id).delete();
  await db.sources.delete(id);
}
