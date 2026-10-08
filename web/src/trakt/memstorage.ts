// Stockage mémoire pour les tests (environnement node : pas de localStorage).
export function installMemStorage(): void {
  const m = new Map<string, string>();
  (globalThis as unknown as { localStorage: Storage }).localStorage = {
    getItem: (k) => m.get(k) ?? null, setItem: (k, v) => void m.set(k, String(v)), removeItem: (k) => void m.delete(k),
    clear: () => m.clear(), key: (i) => [...m.keys()][i] ?? null, get length() { return m.size; },
  } as Storage;
}
