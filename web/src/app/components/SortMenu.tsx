// Reusable sort/order menu for Movies and Series shelves.

import { useState } from "react";

export type SortKey = "added" | "alpha" | "rating" | "year" | "modified";
export type SortDir = "asc" | "desc";

interface Props {
  value: { key: SortKey; dir: SortDir };
  onChange: (next: { key: SortKey; dir: SortDir }) => void;
  options?: SortKey[];
}

const LABELS: Record<SortKey, string> = {
  added: "Recently added",
  alpha: "Alphabetical",
  rating: "Rating",
  year: "Year",
  modified: "Recently modified",
};

export function SortMenu({ value, onChange, options = ["added", "alpha", "rating", "year"] }: Props) {
  const [open, setOpen] = useState(false);
  return (
    <div style={{ position: "relative", display: "inline-block" }}>
      <button className="filter-button" onClick={() => setOpen((o) => !o)} title="Sort">
        ↕ {LABELS[value.key]} {value.dir === "asc" ? "↑" : "↓"}
      </button>
      {open && (
        <div className="filter-popover" onClick={(e) => e.stopPropagation()}>
          {options.map((k) => (
            <button
              key={k}
              className={value.key === k ? "filter-chip on" : "filter-chip"}
              style={{ width: "100%", justifyContent: "space-between", marginBottom: 4 }}
              onClick={() => { onChange({ key: k, dir: value.key === k && value.dir === "desc" ? "asc" : "desc" }); setOpen(false); }}
            >
              <span>{LABELS[k]}</span>
              <span style={{ opacity: 0.6 }}>{value.key === k ? (value.dir === "asc" ? "↑" : "↓") : ""}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
