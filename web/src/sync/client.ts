// Client du worker de synchronisation (thread principal).

import type { Source, SyncProgress } from "@/db/types";
import { currentTransport } from "@/net/transport";
import type { DetectedLanguage } from "./core";
import type { WorkerRequest, WorkerResponse } from "./worker";

let worker: Worker | null = null;
let seq = 0;
const pending = new Map<number, { resolve: (v: unknown) => void; reject: (e: Error) => void; onProgress?: (p: SyncProgress) => void }>();

function getWorker(): Worker {
  if (worker) return worker;
  worker = new Worker(new URL("./worker.ts", import.meta.url), { type: "module" });
  worker.onmessage = (ev: MessageEvent<WorkerResponse>) => {
    const m = ev.data;
    const p = pending.get(m.id);
    if (!p) return;
    if (m.type === "progress") p.onProgress?.(m.progress);
    else {
      pending.delete(m.id);
      if (m.type === "result") p.resolve(m.value);
      else { const e = new Error(m.message); e.name = m.name; p.reject(e); }
    }
  };
  return worker;
}

type Distribute<T> = T extends unknown ? Omit<T, "id" | "transport"> : never;
type Req = Distribute<Exclude<WorkerRequest, { type: "cancel" }>>;

async function call<T>(req: Req, onProgress?: (p: SyncProgress) => void): Promise<T> {
  const id = ++seq;
  const transport = await currentTransport();
  return new Promise<T>((resolve, reject) => {
    pending.set(id, { resolve: resolve as (v: unknown) => void, reject, onProgress });
    getWorker().postMessage({ ...req, id, transport } as WorkerRequest);
  });
}

export const cancelWork = () => worker?.postMessage({ id: 0, type: "cancel" } satisfies WorkerRequest);

export const syncSource = (source: Source, onProgress: (p: SyncProgress) => void, epg = true) =>
  call<{ live: number; movie: number; series: number }>({ type: "sync", source, epg }, onProgress);
export const syncSourceEpg = (source: Source) => call<number>({ type: "epg", source });
export const detectSourceLanguages = (source: Source) =>
  call<{ languages: DetectedLanguage[]; counts: Record<"live" | "movie" | "series", number> }>({ type: "detect", source });
export const testSource = (source: Source) =>
  call<{ ok: true; expDate: number | null; maxConnections: number }>({ type: "test", source });
