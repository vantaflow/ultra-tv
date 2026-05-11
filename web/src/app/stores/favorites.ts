// Favorites + custom groups. Mirrors FavoriteRepository / VirtualGroup actions from the
// "long-press → add to favorites / custom group" Android flows.

import { create } from "zustand";
import { favoriteRepo, virtualGroupRepo } from "@data/db/repositories";
import type { ContentType } from "@domain/model";

interface FavoritesState {
  toggle: (providerId: number, contentId: number, contentType: ContentType) => Promise<boolean>;
  createGroup: (providerId: number, name: string, contentType: ContentType, icon?: string) => Promise<number>;
  deleteGroup: (id: number) => Promise<void>;
}

export const useFavoritesStore = create<FavoritesState>(() => ({
  async toggle(providerId, contentId, contentType) {
    const existing = await favoriteRepo.byContent(providerId, contentId);
    if (existing) {
      await favoriteRepo.remove(existing.id);
      return false;
    }
    await favoriteRepo.add({
      providerId,
      contentId,
      contentType,
      position: 0,
      groupId: null,
      addedAt: Date.now(),
    });
    return true;
  },

  async createGroup(providerId, name, contentType, icon) {
    return (await virtualGroupRepo.add({
      providerId,
      name,
      iconEmoji: icon ?? null,
      position: 0,
      createdAt: Date.now(),
      contentType,
    })) as number;
  },

  async deleteGroup(id) {
    await virtualGroupRepo.remove(id);
  },
}));
