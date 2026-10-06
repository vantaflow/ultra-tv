import { useEffect, useRef } from "react";
import { usePlayer } from "./store";

/** Emplacement de l'aperçu : le lecteur unique s'y cale (position remesurée au redimensionnement et au défilement). */
export function PreviewSlot({ children }: { children?: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current!;
    const doMeasure = () => {
      const r = el.getBoundingClientRect();
      usePlayer.getState().setSlot({ x: r.left, y: r.top, w: r.width, h: r.height });
    };
    // Défilement / redimensionnement : au plus une mesure par image (le setSlot ignore les valeurs identiques).
    let raf = 0;
    const measure = () => { if (!raf) raf = requestAnimationFrame(() => { raf = 0; doMeasure(); }); };
    doMeasure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    // Le conteneur et la page peuvent déplacer l'emplacement sans le redimensionner (barre latérale, panneau).
    const host = el.closest(".preview, .page") ?? el.parentElement;
    if (host) ro.observe(host);
    window.addEventListener("resize", measure);
    // En capture : le scroll ne remonte pas, et n'importe quel conteneur défilant peut décaler l'aperçu.
    window.addEventListener("scroll", measure, { capture: true, passive: true });
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", measure);
      window.removeEventListener("scroll", measure, { capture: true });
      if (raf) cancelAnimationFrame(raf);
      const s = usePlayer.getState();
      s.setSlot(null);
      if (s.mode === "inline") s.close();
    };
  }, []);
  return <div ref={ref} className="preview-slot">{children}</div>;
}
