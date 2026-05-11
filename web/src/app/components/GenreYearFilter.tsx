// Multi-select genre filter + year-range picker. Reads genre/year off the catalog
// items themselves (no extra API call needed). Emits a filter function the parent
// applies to its list.

import { useMemo, useState } from "react";

interface Item {
  genre: string | null;
  year: string | null;
  releaseDate: string | null;
}

interface Props {
  items: Item[];
  selectedGenres: Set<string>;
  yearMin: number | null;
  yearMax: number | null;
  onChange: (next: { selectedGenres: Set<string>; yearMin: number | null; yearMax: number | null }) => void;
}

function parseYear(raw: string | null): number | null {
  if (!raw) return null;
  const m = raw.match(/(\d{4})/);
  if (!m) return null;
  const y = Number.parseInt(m[1]!, 10);
  return y >= 1900 && y <= 2100 ? y : null;
}

export function GenreYearFilter({ items, selectedGenres, yearMin, yearMax, onChange }: Props) {
  const [open, setOpen] = useState(false);

  const { genres, allYears } = useMemo(() => {
    const gset = new Set<string>();
    const years: number[] = [];
    for (const it of items) {
      if (it.genre) {
        for (const g of it.genre.split(/[,/|;]/).map((s) => s.trim()).filter(Boolean)) {
          gset.add(g);
        }
      }
      const y = parseYear(it.year ?? it.releaseDate);
      if (y != null) years.push(y);
    }
    return { genres: Array.from(gset).sort((a, b) => a.localeCompare(b)), allYears: years };
  }, [items]);

  const minPossible = allYears.length > 0 ? Math.min(...allYears) : 1900;
  const maxPossible = allYears.length > 0 ? Math.max(...allYears) : new Date().getFullYear();

  const toggleGenre = (g: string) => {
    const next = new Set(selectedGenres);
    if (next.has(g)) next.delete(g); else next.add(g);
    onChange({ selectedGenres: next, yearMin, yearMax });
  };

  const activeCount = selectedGenres.size + (yearMin != null ? 1 : 0) + (yearMax != null ? 1 : 0);
  const active = activeCount > 0;

  return (
    <div style={{ position: "relative", display: "inline-block" }}>
      <button
        className={`filter-button${active ? " active" : ""}`}
        onClick={() => setOpen((o) => !o)}
        title="Genre / Year filter"
      >
        <span>Genre / Year</span>
        {active && <span className="filter-badge active">{activeCount}</span>}
      </button>
      {open && (
        <div className="filter-popover filter-popover-large" onClick={(e) => e.stopPropagation()}>
          <h4 style={{ margin: "4px 0 8px" }}>Year</h4>
          <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
            <input type="number" placeholder={String(minPossible)} value={yearMin ?? ""} onChange={(e) => onChange({ selectedGenres, yearMin: e.target.value ? Number(e.target.value) : null, yearMax })} style={{ width: 90, minWidth: 0 }} />
            <span style={{ color: "var(--fg-muted)" }}>→</span>
            <input type="number" placeholder={String(maxPossible)} value={yearMax ?? ""} onChange={(e) => onChange({ selectedGenres, yearMin, yearMax: e.target.value ? Number(e.target.value) : null })} style={{ width: 90, minWidth: 0 }} />
            <button onClick={() => onChange({ selectedGenres, yearMin: null, yearMax: null })}>Clear</button>
          </div>
          <h4 style={{ margin: "4px 0 8px" }}>Genres ({genres.length})</h4>
          <div className="filter-chip-grid" style={{ padding: 0, maxHeight: 320, overflowY: "auto" }}>
            {genres.length === 0 && <div style={{ color: "var(--fg-muted)" }}>No genre info available</div>}
            {genres.map((g) => (
              <button
                key={g}
                className={`filter-chip${selectedGenres.has(g) ? " on" : ""}`}
                onClick={() => toggleGenre(g)}
              >
                <span className="filter-chip-check" aria-hidden>✓</span>
                <span className="filter-chip-label">{g}</span>
              </button>
            ))}
          </div>
          <div style={{ marginTop: 8, display: "flex", justifyContent: "space-between" }}>
            <button onClick={() => onChange({ selectedGenres: new Set(), yearMin: null, yearMax: null })}>Reset all</button>
            <button onClick={() => setOpen(false)}>Close</button>
          </div>
        </div>
      )}
    </div>
  );
}

export function filterByGenreYear<T extends Item>(items: T[], selectedGenres: Set<string>, yearMin: number | null, yearMax: number | null): T[] {
  if (selectedGenres.size === 0 && yearMin == null && yearMax == null) return items;
  return items.filter((it) => {
    if (selectedGenres.size > 0) {
      if (!it.genre) return false;
      const has = it.genre.split(/[,/|;]/).map((s) => s.trim()).some((g) => selectedGenres.has(g));
      if (!has) return false;
    }
    const y = parseYear(it.year ?? it.releaseDate);
    if (yearMin != null && (y == null || y < yearMin)) return false;
    if (yearMax != null && (y == null || y > yearMax)) return false;
    return true;
  });
}
