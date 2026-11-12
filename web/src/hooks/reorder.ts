import { useRef, useState } from "react";
import { insertBefore, nextOf } from "@/lib/channelOrder";

/**
 * Glisser-déposer pour réordonner des lignes (HTML5 natif : un clic simple et un double-clic ne démarrent aucun glissement).
 * `props(id)` s'étale sur la ligne ; `cls(id)` donne les classes de l'indicateur d'insertion (avant / après la ligne survolée).
 * `axis` : "y" pour une liste verticale, "x" pour une grille (la moitié de la ligne survolée décide avant / après).
 */
export function useReorderDnd(opts: { enabled: boolean; axis: "x" | "y"; getIds: () => Promise<number[]> | number[]; onReorder: (ids: number[], movedId: number) => void }) {
  const drag = useRef<number | null>(null);
  const [dragId, setDragId] = useState<number | null>(null);
  const [over, setOver] = useState<{ id: number; after: boolean } | null>(null);
  const end = () => { drag.current = null; setDragId(null); setOver(null); };

  const props = (id: number) => (!opts.enabled ? {} : {
    draggable: true,
    onDragStart: (e: React.DragEvent) => {
      drag.current = id; setDragId(id);
      e.dataTransfer.effectAllowed = "move";
      e.dataTransfer.setData("text/plain", String(id));
    },
    onDragOver: (e: React.DragEvent<HTMLElement>) => {
      if (drag.current == null) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = "move";
      const r = e.currentTarget.getBoundingClientRect();
      const rtl = getComputedStyle(e.currentTarget).direction === "rtl";
      const after = opts.axis === "y" ? e.clientY > r.top + r.height / 2 : (e.clientX > r.left + r.width / 2) !== rtl;
      setOver((o) => (o && o.id === id && o.after === after ? o : { id, after }));
    },
    onDrop: async (e: React.DragEvent<HTMLElement>) => {
      const from = drag.current;
      const o = over;
      e.preventDefault();
      end();
      if (from == null) return;
      const after = o && o.id === id ? o.after : false;
      const ids = await opts.getIds();
      const target = after ? nextOf(ids, id) : id;
      const next = insertBefore(ids, from, target);
      if (next !== ids) opts.onReorder(next, from);
    },
    onDragEnd: end,
  });
  const cls = (id: number) => (dragId === id ? " dragging" : over && over.id === id && dragId !== id ? (over.after ? " drop-after" : " drop-before") : "");
  return { props, cls, dragging: dragId !== null };
}
