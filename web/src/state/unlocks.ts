// Catégories déverrouillées par le code parental pendant la session (mémoire seulement : tout se reverrouille au
// redémarrage et au changement de profil). Et la demande de code en cours (fenêtre affichée par ui/ParentalPrompt).
import { create } from "zustand";
import { usePrefs } from "./prefs";

export interface CodeRequest { label: string; resolve: (ok: boolean) => void }

interface UnlockStore {
  keys: ReadonlySet<string>;
  /** Incrémenté à chaque changement (dépendance des requêtes Dexie). */
  v: number;
  request: CodeRequest | null;
  unlock: (key: string) => void;
  clear: () => void;
  setRequest: (r: CodeRequest | null) => void;
}

export const useUnlocks = create<UnlockStore>((set) => ({
  keys: new Set(), v: 0, request: null,
  unlock: (key) => set((s) => ({ keys: new Set([...s.keys, key]), v: s.v + 1 })),
  clear: () => set((s) => (s.keys.size ? { keys: new Set(), v: s.v + 1 } : s)),
  setRequest: (request) => set({ request }),
}));

// Changement de profil : tout se reverrouille.
let lastProfile = usePrefs.getState().profileId;
usePrefs.subscribe((p) => { if (p.profileId !== lastProfile) { lastProfile = p.profileId; useUnlocks.getState().clear(); } });

/** Ouvre la fenêtre du code parental ; résout vrai quand le code est accepté. */
export function askParentalCode(label: string): Promise<boolean> {
  return new Promise((resolve) => {
    useUnlocks.getState().request?.resolve(false);
    useUnlocks.getState().setRequest({ label, resolve: (ok) => { useUnlocks.getState().setRequest(null); resolve(ok); } });
  });
}
