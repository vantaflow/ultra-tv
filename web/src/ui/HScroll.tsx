import { useCallback, useEffect, useRef, useState, type HTMLAttributes, type ReactNode } from "react";
import { useT } from "@/i18n";
import { Icon } from "./Icon";

/**
 * Bande défilante horizontale utilisable à la souris : flèches ‹ › au survol quand il reste du contenu,
 * et (option `wheel`) molette verticale convertie en défilement horizontal — pour les bandes fines
 * (puces de catégories) où la molette ne sert à rien d'autre. `follow` : clé dont le changement ramène
 * l'élément sélectionné (aria-selected) dans la vue.
 */
export function HScroll({ children, wheel = false, follow, ...rest }: HTMLAttributes<HTMLDivElement> & { children: ReactNode; wheel?: boolean; follow?: unknown }) {
  const t = useT();
  const ref = useRef<HTMLDivElement>(null);
  const [edge, setEdge] = useState({ start: false, end: false });

  const update = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    const s = Math.abs(el.scrollLeft); // négatif en RTL
    const start = s > 2, end = s + el.clientWidth < el.scrollWidth - 2;
    setEdge((p) => (p.start === start && p.end === end ? p : { start, end }));
  }, []);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    const mo = new MutationObserver(update);
    mo.observe(el, { childList: true });
    el.addEventListener("scroll", update, { passive: true });
    const onWheel = (e: WheelEvent) => {
      if (!wheel || e.ctrlKey || Math.abs(e.deltaY) <= Math.abs(e.deltaX) || el.scrollWidth <= el.clientWidth) return;
      e.preventDefault();
      el.scrollLeft += (getComputedStyle(el).direction === "rtl" ? -1 : 1) * e.deltaY;
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => { ro.disconnect(); mo.disconnect(); el.removeEventListener("scroll", update); el.removeEventListener("wheel", onWheel); };
  }, [update, wheel]);

  useEffect(() => {
    if (follow === undefined) return;
    ref.current?.querySelector<HTMLElement>('[aria-selected="true"]')?.scrollIntoView({ block: "nearest", inline: "nearest" });
  }, [follow]);

  const by = (d: -1 | 1) => {
    const el = ref.current;
    if (!el) return;
    const rtl = getComputedStyle(el).direction === "rtl" ? -1 : 1;
    el.scrollBy({ left: d * rtl * el.clientWidth * 0.8, behavior: "smooth" });
  };

  return (
    <div className="hscroll">
      {edge.start && <button type="button" className="hs-btn start" tabIndex={-1} aria-label={t("a11y.scrollPrev")} onClick={() => by(-1)}><Icon name="back" size={18} /></button>}
      <div ref={ref} {...rest}>{children}</div>
      {edge.end && <button type="button" className="hs-btn end" tabIndex={-1} aria-label={t("a11y.scrollNext")} onClick={() => by(1)}><Icon name="chevron" size={18} /></button>}
    </div>
  );
}
