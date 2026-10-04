// Listes et grilles virtualisées (seules les lignes visibles existent dans le DOM).
import { useVirtualizer } from "@tanstack/react-virtual";
import { forwardRef, useEffect, useImperativeHandle, useRef, type ReactNode } from "react";
import { useElementSize } from "@/hooks/misc";

export interface Rows<T> { count: number | null; get: (i: number) => T | undefined; want: (from: number, to: number) => void }

export const arrayRows = <T,>(a: T[]): Rows<T> => ({ count: a.length, get: (i) => a[i], want: () => undefined });

export interface VListHandle { scrollTo: (i: number) => void; el: HTMLDivElement | null }

interface VListProps<T> {
  rows: Rows<T>;
  rowH: number;
  render: (row: T | undefined, i: number) => ReactNode;
  className?: string;
  overscan?: number;
  onKeyDown?: (e: React.KeyboardEvent<HTMLDivElement>) => void;
  label?: string;
}

function VListInner<T>({ rows, rowH, render, className = "vlist", overscan = 8, onKeyDown, label }: VListProps<T>, ref: React.Ref<VListHandle>) {
  const el = useRef<HTMLDivElement>(null);
  const count = rows.count ?? 0;
  const v = useVirtualizer({ count, getScrollElement: () => el.current, estimateSize: () => rowH, overscan });
  useImperativeHandle(ref, () => ({ scrollTo: (i) => v.scrollToIndex(i, { align: "auto" }), el: el.current }), [v]);
  const items = v.getVirtualItems();
  const first = items[0]?.index ?? 0;
  const last = items[items.length - 1]?.index ?? 0;
  useEffect(() => { rows.want(first, last); }, [first, last, rows, count]);
  return (
    <div ref={el} className={className} tabIndex={0} onKeyDown={onKeyDown} role="listbox" aria-label={label}>
      <div style={{ height: v.getTotalSize(), position: "relative", width: "100%" }}>
        {items.map((it) => (
          <div key={it.key} style={{ position: "absolute", top: 0, insetInline: 0, height: it.size, transform: `translateY(${it.start}px)`, paddingBottom: 6 }}>
            {render(rows.get(it.index), it.index)}
          </div>
        ))}
      </div>
    </div>
  );
}
export const VList = forwardRef(VListInner) as <T>(p: VListProps<T> & { ref?: React.Ref<VListHandle> }) => ReactNode;

interface VGridProps<T> {
  rows: Rows<T>;
  /** Largeur minimale d'une cellule ; la grille calcule le nombre de colonnes. */
  minW: number;
  gap?: number;
  /** Hauteur de la cellule pour une largeur donnée. */
  cellH: (w: number) => number;
  render: (row: T | undefined, i: number) => ReactNode;
  className?: string;
  resetKey?: unknown;
}

export function VGrid<T>({ rows, minW, gap = 20, cellH, render, className = "grid-wrap", resetKey }: VGridProps<T>) {
  const [ref, size] = useElementSize<HTMLDivElement>();
  const inner = Math.max(0, size.w - 48);
  const cols = Math.max(1, Math.floor((inner + gap) / (minW + gap)));
  const cellW = cols > 0 ? (inner - gap * (cols - 1)) / cols : minW;
  const rowH = cellH(cellW) + gap;
  const count = rows.count ?? 0;
  const nRows = Math.ceil(count / cols);
  const v = useVirtualizer({ count: nRows, getScrollElement: () => ref.current, estimateSize: () => rowH, overscan: 3 });
  useEffect(() => { v.scrollToOffset(0); }, [resetKey, v]);
  useEffect(() => { v.measure(); }, [rowH, v]);
  const items = v.getVirtualItems();
  const first = (items[0]?.index ?? 0) * cols;
  const last = ((items[items.length - 1]?.index ?? 0) + 1) * cols - 1;
  useEffect(() => { rows.want(first, last); }, [first, last, rows, count]);
  return (
    <div ref={ref} className={className}>
      <div style={{ height: v.getTotalSize(), position: "relative" }}>
        {items.map((it) => (
          <div key={it.key} style={{ position: "absolute", top: 0, insetInline: 0, height: rowH, transform: `translateY(${it.start}px)`, display: "grid", gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`, columnGap: gap, alignItems: "start" }}>
            {Array.from({ length: cols }, (_, c) => {
              const i = it.index * cols + c;
              return i < count ? <div key={i} style={{ minWidth: 0 }}>{render(rows.get(i), i)}</div> : null;
            })}
          </div>
        ))}
      </div>
    </div>
  );
}
