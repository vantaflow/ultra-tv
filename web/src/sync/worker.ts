/// <reference lib="webworker" />
// Worker de synchronisation : tout le réseau et toute l'écriture IndexedDB se font hors du thread UI.

import { db } from "@/db/db";
import type { Source, SyncProgress } from "@/db/types";
import type { Transport } from "@/net/transport";
import { detectLanguages, runSync, testConnection } from "./core";
import { syncEpg } from "./epg";

export type WorkerRequest =
  | { id: number; type: "sync"; source: Source; transport: Transport; epg: boolean; preserveFlags?: boolean }
  | { id: number; type: "epg"; source: Source; transport: Transport }
  | { id: number; type: "detect"; source: Source; transport: Transport }
  | { id: number; type: "test"; source: Source; transport: Transport }
  | { id: number; type: "cancel" };

export type WorkerResponse =
  | { id: number; type: "progress"; progress: SyncProgress }
  | { id: number; type: "result"; value: unknown }
  | { id: number; type: "error"; name: string; message: string };

const ctrls = new Map<number, AbortController>();
const post = (m: WorkerResponse) => (self as unknown as Worker).postMessage(m);

self.onmessage = async (ev: MessageEvent<WorkerRequest>) => {
  const req = ev.data;
  if (req.type === "cancel") {
    ctrls.forEach((c) => c.abort());
    return;
  }
  const ctrl = new AbortController();
  ctrls.set(req.id, ctrl);
  try {
    switch (req.type) {
      case "test":
        post({ id: req.id, type: "result", value: await testConnection(req.transport, req.source, ctrl.signal) });
        break;
      case "detect":
        post({ id: req.id, type: "result", value: await detectLanguages(req.transport, req.source, ctrl.signal) });
        break;
      case "epg": {
        const n = await syncEpg(req.source, req.transport, ctrl.signal);
        post({ id: req.id, type: "result", value: n });
        break;
      }
      case "sync": {
        await db.sources.update(req.source.id!, { state: "syncing", error: undefined });
        const counts = await runSync({
          source: req.source, transport: req.transport, signal: ctrl.signal, preserveFlags: req.preserveFlags,
          onProgress: (progress) => post({ id: req.id, type: "progress", progress }),
        });
        if (req.epg) {
          post({ id: req.id, type: "progress", progress: { phase: "epg", ratio: 0, counts } });
          const fresh = await db.sources.get(req.source.id!);
          const withCid = { ...req.source, cid: fresh?.cid ?? req.source.cid };
          try { await syncEpg(withCid, req.transport, ctrl.signal, (ratio) => post({ id: req.id, type: "progress", progress: { phase: "epg", ratio, counts } })); }
          catch (e) { if (e instanceof DOMException && e.name === "AbortError") throw e; /* le guide est facultatif */ }
        }
        post({ id: req.id, type: "result", value: counts });
        break;
      }
    }
  } catch (e) {
    const err = e as Error;
    if (req.type === "sync" && err.name !== "AbortError") {
      await db.sources.update(req.source.id!, { state: "error", error: err.message.slice(0, 200) }).catch(() => undefined);
    } else if (req.type === "sync") {
      await db.sources.update(req.source.id!, { state: "ready" }).catch(() => undefined);
    }
    post({ id: req.id, type: "error", name: err.name, message: err.message });
  } finally {
    ctrls.delete(req.id);
  }
};
