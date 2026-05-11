// UI preferences (sidebar collapsed state, multi-view layout). Persisted to
// the Dexie settings table so it survives reloads.

import { create } from "zustand";
import { settingsRepo } from "@data/db/repositories";

export type MultiViewLayout = "2x2" | "1+3" | "1+2" | "1x2";

interface UiState {
  sidebarCollapsed: boolean;
  multiViewLayout: MultiViewLayout;
  init: () => Promise<void>;
  toggleSidebar: () => Promise<void>;
  setMultiViewLayout: (layout: MultiViewLayout) => Promise<void>;
}

export const useUiStore = create<UiState>((set, get) => ({
  sidebarCollapsed: false,
  multiViewLayout: "2x2",

  async init() {
    const [collapsed, layout] = await Promise.all([
      settingsRepo.get<boolean>("ui.sidebarCollapsed"),
      settingsRepo.get<MultiViewLayout>("ui.multiViewLayout"),
    ]);
    set({
      sidebarCollapsed: !!collapsed,
      multiViewLayout: layout ?? "2x2",
    });
  },

  async toggleSidebar() {
    const next = !get().sidebarCollapsed;
    await settingsRepo.set("ui.sidebarCollapsed", next);
    set({ sidebarCollapsed: next });
  },

  async setMultiViewLayout(layout) {
    await settingsRepo.set("ui.multiViewLayout", layout);
    set({ multiViewLayout: layout });
  },
}));
