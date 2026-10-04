import { useEffect, useRef } from "react";
import { usePlayer } from "./store";

/** Emplacement de l'aperçu : le lecteur unique s'y cale (position mesurée en continu). */
export function PreviewSlot({ children }: { children?: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current!;
    const measure = () => {
      const r = el.getBoundingClientRect();
      usePlayer.getState().setSlot({ x: r.left, y: r.top, w: r.width, h: r.height });
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    window.addEventListener("resize", measure);
    const scroller = el.closest(".preview, .page");
    scroller?.addEventListener("scroll", measure, { passive: true });
    const iv = setInterval(measure, 400);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", measure);
      scroller?.removeEventListener("scroll", measure);
      clearInterval(iv);
      const s = usePlayer.getState();
      s.setSlot(null);
      if (s.mode === "inline") s.close();
    };
  }, []);
  return <div ref={ref} className="preview-slot">{children}</div>;
}
