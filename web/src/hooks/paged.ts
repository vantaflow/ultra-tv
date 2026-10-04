// Accès paginé à une collection Dexie ordonnée : seules les pages visibles sont lues dans IndexedDB.
import type { Collection } from "dexie";
import { useCallback, useEffect, useRef, useState } from "react";

export interface Paged<T> {
  count: number | null;
  get: (i: number) => T | undefined;
  /** À appeler pour les indices visibles : déclenche le chargement des pages manquantes. */
  want: (from: number, to: number) => void;
}

export function usePagedQuery<T>(make: () => Collection<T, unknown> | null, deps: unknown[], pageSize = 96): Paged<T> {
  const [count, setCount] = useState<number | null>(null);
  const [, bump] = useState(0);
  const pages = useRef(new Map<number, T[]>());
  const loading = useRef(new Set<number>());
  const gen = useRef(0);
  const col = useRef<Collection<T, unknown> | null>(null);

  useEffect(() => {
    const g = ++gen.current;
    pages.current = new Map();
    loading.current = new Set();
    const c = make();
    col.current = c;
    if (!c) { setCount(0); return; }
    setCount(null);
    void c.clone().count().then((n) => { if (g === gen.current) setCount(n); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  const want = useCallback((from: number, to: number) => {
    const c = col.current;
    if (!c) return;
    const g = gen.current;
    for (let p = Math.floor(Math.max(0, from) / pageSize); p <= Math.floor(Math.max(0, to) / pageSize); p++) {
      if (pages.current.has(p) || loading.current.has(p)) continue;
      loading.current.add(p);
      void c.clone().offset(p * pageSize).limit(pageSize).toArray().then((rows) => {
        if (g !== gen.current) return;
        pages.current.set(p, rows);
        loading.current.delete(p);
        bump((x) => x + 1);
      });
    }
  }, [pageSize]);

  const get = useCallback((i: number) => pages.current.get(Math.floor(i / pageSize))?.[i % pageSize], [pageSize]);
  return { count, get, want };
}
