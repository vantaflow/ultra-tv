import { useEffect, useRef, useState } from "react";

export function useDebounced<T>(v: T, ms = 250): T {
  const [d, setD] = useState(v);
  useEffect(() => { const t = setTimeout(() => setD(v), ms); return () => clearTimeout(t); }, [v, ms]);
  return d;
}

/** Horloge qui bat toutes les `ms` (par défaut 30 s) pour les programmes « en cours ». */
export function useNow(ms = 30_000): number {
  const [n, setN] = useState(() => Date.now());
  useEffect(() => { const t = setInterval(() => setN(Date.now()), ms); return () => clearInterval(t); }, [ms]);
  return n;
}

export function useElementSize<T extends HTMLElement>(): [React.RefObject<T>, { w: number; h: number }] {
  const ref = useRef<T>(null);
  const [size, setSize] = useState({ w: 0, h: 0 });
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => { if (e) setSize({ w: Math.round(e.contentRect.width), h: Math.round(e.contentRect.height) }); });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return [ref, size];
}

/** Raccourci clavier global, ignoré quand on tape dans un champ. */
export function useHotkey(handler: (e: KeyboardEvent) => void, deps: unknown[] = []) {
  useEffect(() => {
    const fn = (e: KeyboardEvent) => handler(e);
    window.addEventListener("keydown", fn);
    return () => window.removeEventListener("keydown", fn);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
export const isTyping = (t: EventTarget | null) => {
  const el = t as HTMLElement | null;
  return !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
};
