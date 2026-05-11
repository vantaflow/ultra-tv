// Lookup the currently-airing (and next) program for a given set of EPG channel ids.
// Returns a Map<epgChannelId, { current, next }> driven by Dexie.live so it auto-refreshes
// when the EPG is reloaded.

import { useLiveQuery } from "dexie-react-hooks";
import { useMemo } from "react";
import { programRepo } from "@data/db/repositories";
import type { Program } from "@domain/model";

const WINDOW_MS = 24 * 60 * 60 * 1000; // pull a 24h window once, slice in memory

export interface CurrentSlot {
  current: Program | null;
  next: Program | null;
}

export function useCurrentPrograms(epgIds: string[]): Map<string, CurrentSlot> {
  const ids = useMemo(() => Array.from(new Set(epgIds.filter(Boolean))), [epgIds]);
  const now = Date.now();

  const programs = useLiveQuery<Program[]>(
    async () => {
      if (ids.length === 0) return [];
      // Window: 2h in the past → 22h in the future
      return programRepo.inWindow(now - 2 * 60 * 60 * 1000, now + WINDOW_MS);
    },
    [ids.length, Math.floor(now / (60 * 1000))], // re-query every minute
  );

  return useMemo(() => {
    const out = new Map<string, CurrentSlot>();
    if (!programs || ids.length === 0) return out;
    const idSet = new Set(ids);
    const byChannel = new Map<string, Program[]>();
    for (const p of programs) {
      if (!idSet.has(p.channelId)) continue;
      const list = byChannel.get(p.channelId) ?? [];
      list.push(p);
      byChannel.set(p.channelId, list);
    }
    for (const [cid, list] of byChannel) {
      list.sort((a, b) => a.startTime - b.startTime);
      const current = list.find((p) => p.startTime <= now && p.endTime > now) ?? null;
      const next = list.find((p) => p.startTime > now) ?? null;
      out.set(cid, { current, next });
    }
    return out;
  }, [programs, ids, now]);
}
