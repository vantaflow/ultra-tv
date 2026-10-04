import { create } from "zustand";

export interface Toast { id: number; text: string }
interface UiStore {
  toasts: Toast[];
  toast: (text: string) => void;
  online: boolean;
  setOnline: (b: boolean) => void;
  update: { state: string; version?: string; percent?: number } | null;
  setUpdate: (u: UiStore["update"]) => void;
}
let n = 0;
export const useUi = create<UiStore>((set) => ({
  toasts: [],
  toast: (text) => {
    const id = ++n;
    set((s) => ({ toasts: [...s.toasts, { id, text }] }));
    setTimeout(() => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), 2600);
  },
  online: typeof navigator === "undefined" ? true : navigator.onLine,
  setOnline: (online) => set({ online }),
  update: null,
  setUpdate: (update) => set({ update }),
}));
