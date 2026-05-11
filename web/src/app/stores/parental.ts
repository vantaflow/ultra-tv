// Parental control state.
// PIN is hashed with SubtleCrypto before persistence — never stored in clear text.

import { create } from "zustand";
import { settingsRepo, parentalLockRepo } from "@data/db/repositories";

const PIN_KEY = "parental.pinHash";
const HIDE_LOCKED_KEY = "parental.hideLocked";
const SESSION_TTL_MS = 15 * 60 * 1000;

async function sha256(input: string): Promise<string> {
  const enc = new TextEncoder().encode(input);
  const buf = await crypto.subtle.digest("SHA-256", enc);
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

interface ParentalState {
  unlocked: boolean;
  hideLocked: boolean;
  hasPin: boolean;
  init: () => Promise<void>;
  setPin: (pin: string) => Promise<void>;
  clearPin: () => Promise<void>;
  unlock: (pin: string) => Promise<boolean>;
  lock: () => void;
  setHideLocked: (hide: boolean) => Promise<void>;
  lockCategory: (providerId: number, categoryId: number, contentType: "LIVE" | "MOVIE" | "SERIES") => Promise<void>;
  unlockCategory: (providerId: number, categoryId: number) => Promise<void>;
}

let unlockTimer: ReturnType<typeof setTimeout> | null = null;

export const useParentalStore = create<ParentalState>((set, get) => ({
  unlocked: false,
  hideLocked: false,
  hasPin: false,

  async init() {
    const [hash, hide] = await Promise.all([
      settingsRepo.get<string>(PIN_KEY),
      settingsRepo.get<boolean>(HIDE_LOCKED_KEY),
    ]);
    set({ hasPin: !!hash, hideLocked: !!hide });
  },

  async setPin(pin) {
    const hash = await sha256(pin);
    await settingsRepo.set(PIN_KEY, hash);
    set({ hasPin: true });
  },

  async clearPin() {
    await settingsRepo.set(PIN_KEY, null);
    set({ hasPin: false, unlocked: false });
  },

  async unlock(pin) {
    const stored = await settingsRepo.get<string>(PIN_KEY);
    if (!stored) return false;
    const ok = (await sha256(pin)) === stored;
    if (ok) {
      set({ unlocked: true });
      if (unlockTimer) clearTimeout(unlockTimer);
      unlockTimer = setTimeout(() => get().lock(), SESSION_TTL_MS);
    }
    return ok;
  },

  lock() {
    if (unlockTimer) clearTimeout(unlockTimer);
    unlockTimer = null;
    set({ unlocked: false });
  },

  async setHideLocked(hide) {
    await settingsRepo.set(HIDE_LOCKED_KEY, hide);
    set({ hideLocked: hide });
  },

  async lockCategory(providerId, categoryId, contentType) {
    await parentalLockRepo.add({
      providerId,
      categoryId,
      contentType,
      createdAt: Date.now(),
    });
  },

  async unlockCategory(providerId, categoryId) {
    const locks = await parentalLockRepo.list(providerId);
    const lock = locks.find((l) => l.categoryId === categoryId);
    if (lock) await parentalLockRepo.delete(lock.id);
  },
}));
