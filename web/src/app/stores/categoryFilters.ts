// Per-provider, per-content-type category whitelist.
// Persisted to the Dexie settings table. Empty/null means "show everything"
// (the default after first sync). Once the user makes any selection, only the
// chosen categories are rendered in Live TV / Movies / Series.

import { create } from "zustand";
import { settingsRepo } from "@data/db/repositories";

type ContentType = "LIVE" | "MOVIE" | "SERIES";

interface FilterState {
  // key: `${providerId}:${contentType}` -> Set of categoryIds (or null = all)
  allowed: Record<string, number[] | null>;
  load: (providerId: number) => Promise<void>;
  isAllowed: (providerId: number, contentType: ContentType, categoryId: number | null) => boolean;
  getAllowed: (providerId: number, contentType: ContentType) => number[] | null;
  toggleCategory: (providerId: number, contentType: ContentType, categoryId: number) => Promise<void>;
  setAll: (providerId: number, contentType: ContentType, categoryIds: number[] | null) => Promise<void>;
  clear: (providerId: number, contentType: ContentType) => Promise<void>;
}

const key = (providerId: number, contentType: ContentType) => `filters:${providerId}:${contentType}`;

export const useCategoryFilters = create<FilterState>((set, get) => ({
  allowed: {},

  async load(providerId) {
    const next: Record<string, number[] | null> = { ...get().allowed };
    for (const ct of ["LIVE", "MOVIE", "SERIES"] as const) {
      const v = await settingsRepo.get<number[]>(key(providerId, ct));
      next[`${providerId}:${ct}`] = v ?? null;
    }
    set({ allowed: next });
  },

  isAllowed(providerId, contentType, categoryId) {
    const list = get().allowed[`${providerId}:${contentType}`];
    if (!list) return true;
    if (categoryId == null) return false;
    return list.includes(categoryId);
  },

  getAllowed(providerId, contentType) {
    return get().allowed[`${providerId}:${contentType}`] ?? null;
  },

  async toggleCategory(providerId, contentType, categoryId) {
    const k = `${providerId}:${contentType}`;
    const current = get().allowed[k] ?? [];
    const next = current.includes(categoryId)
      ? current.filter((id) => id !== categoryId)
      : [...current, categoryId];
    await settingsRepo.set(key(providerId, contentType), next);
    set((s) => ({ allowed: { ...s.allowed, [k]: next } }));
  },

  async setAll(providerId, contentType, categoryIds) {
    const k = `${providerId}:${contentType}`;
    await settingsRepo.set(key(providerId, contentType), categoryIds);
    set((s) => ({ allowed: { ...s.allowed, [k]: categoryIds } }));
  },

  async clear(providerId, contentType) {
    await get().setAll(providerId, contentType, null);
  },
}));
