// Backup export/import — mirrors BackupManagerImpl.kt.
// Dumps the full Dexie state to a JSON envelope, with a version field for forward
// compatibility, and restores it transactionally.

import { db } from "@data/db/database";

const VERSION = 1;

export async function exportBackup(): Promise<Blob> {
  const tables = db.tables;
  const payload: Record<string, unknown[]> = {};
  for (const t of tables) {
    payload[t.name] = await t.toArray();
  }
  const envelope = { version: VERSION, exportedAt: Date.now(), data: payload };
  return new Blob([JSON.stringify(envelope, null, 2)], { type: "application/json" });
}

export async function importBackup(file: File): Promise<void> {
  const text = await file.text();
  const envelope = JSON.parse(text) as { version: number; data: Record<string, unknown[]> };
  if (!envelope || typeof envelope.version !== "number") throw new Error("Invalid backup envelope");
  if (envelope.version > VERSION) throw new Error(`Unsupported backup version ${envelope.version}`);

  await db.transaction("rw", db.tables, async () => {
    for (const t of db.tables) {
      await t.clear();
      const rows = envelope.data[t.name];
      if (Array.isArray(rows) && rows.length > 0) {
        await t.bulkAdd(rows as never[]);
      }
    }
  });
}
