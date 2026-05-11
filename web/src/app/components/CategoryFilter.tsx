// Category whitelist popover, redesigned:
//   - Auto-groups categories by detected script/language (Arabic, French, English, Spanish, Other)
//     so multi-language playlists are scannable instead of a giant flat list.
//   - Each row uses dir="auto" so Arabic / Hebrew names render right-to-left properly.
//   - Search is unicode-aware (works for both "Sport" and "رياضة").
//   - Bulk actions per group (All in group / None in group) on top of the global All / None.

import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useRef, useState } from "react";
import { db } from "@data/db/database";
import { useCategoryFilters } from "@app/stores/categoryFilters";
import type { Category } from "@domain/model";

interface Props {
  providerId: number;
  contentType: "LIVE" | "MOVIE" | "SERIES";
  label?: string;
  align?: "left" | "right";
}

type LangBucket = "AR" | "FR" | "EN" | "ES" | "DE" | "IT" | "PT" | "TR" | "OTHER";

const BUCKET_NAMES: Record<LangBucket, string> = {
  AR: "العربية",
  FR: "Français",
  EN: "English",
  ES: "Español",
  DE: "Deutsch",
  IT: "Italiano",
  PT: "Português",
  TR: "Türkçe",
  OTHER: "Other",
};

const BUCKET_ORDER: LangBucket[] = ["AR", "FR", "EN", "ES", "DE", "IT", "PT", "TR", "OTHER"];

function detectLang(text: string): LangBucket {
  // Arabic script
  if (/[؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿]/.test(text)) return "AR";
  const lower = text.toLowerCase();
  // Country/Language tags often used by IPTV providers
  if (/\bfr(ance|ench)?\b|\bfra\b|🇫🇷/i.test(text)) return "FR";
  if (/\b(uk|usa|english|en\s)|🇬🇧|🇺🇸/i.test(lower)) return "EN";
  if (/\b(es|spain|spanish|espana|español)\b|🇪🇸/i.test(lower)) return "ES";
  if (/\b(de|germany|german|deutsch)\b|🇩🇪/i.test(lower)) return "DE";
  if (/\b(it|italy|italian|italia|italiano)\b|🇮🇹/i.test(lower)) return "IT";
  if (/\b(pt|portugal|brazil|brasil|portugues|português)\b|🇵🇹|🇧🇷/i.test(lower)) return "PT";
  if (/\b(tr|turkey|turkish|türk)\b|🇹🇷/i.test(lower)) return "TR";
  // French-specific words frequently in category names
  if (/(série|sport|enfant|cinéma|documentaire|info)/i.test(text)) return "FR";
  return "OTHER";
}

function normalize(s: string): string {
  return s.normalize("NFKD").replace(/\p{Diacritic}/gu, "").toLowerCase();
}

export function CategoryFilter({ providerId, contentType, label, align = "auto" as never }: Props) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [collapsed, setCollapsed] = useState<Set<LangBucket>>(new Set());
  const [computedAlign, setComputedAlign] = useState<"left" | "right">("left");
  const wrapRef = useRef<HTMLDivElement | null>(null);

  // When opening, decide alignment: if the popover (estimated ~460px) would overflow
  // the right viewport edge, anchor to the right instead. Honours an explicit prop.
  useEffect(() => {
    if (!open || !wrapRef.current) return;
    if (align === "left" || align === "right") { setComputedAlign(align); return; }
    const rect = wrapRef.current.getBoundingClientRect();
    const POPOVER_WIDTH = 470;
    const spaceRight = window.innerWidth - rect.left;
    setComputedAlign(spaceRight < POPOVER_WIDTH + 16 ? "right" : "left");
  }, [open, align]);

  const cats = useLiveQuery<Category[]>(
    async () => db.categories.filter((c) => (c as Category & { providerId?: number }).providerId === providerId && c.type === contentType).toArray(),
    [providerId, contentType],
  );

  const allowed = useCategoryFilters((s) => s.allowed[`${providerId}:${contentType}`]);
  const toggle = useCategoryFilters((s) => s.toggleCategory);
  const setAll = useCategoryFilters((s) => s.setAll);
  const clear = useCategoryFilters((s) => s.clear);

  // Group categories by detected language. Filter by search first so empty groups vanish.
  const groups = useMemo(() => {
    if (!cats) return [];
    const q = normalize(search);
    const filtered = q
      ? cats.filter((c) => normalize(c.name).includes(q))
      : cats;

    const buckets = new Map<LangBucket, Category[]>();
    for (const c of filtered) {
      const b = detectLang(c.name);
      const list = buckets.get(b) ?? [];
      list.push(c);
      buckets.set(b, list);
    }
    // Sort categories within each bucket alphabetically (locale-aware)
    for (const list of buckets.values()) {
      list.sort((a, b) => a.name.localeCompare(b.name));
    }
    return BUCKET_ORDER
      .filter((b) => buckets.has(b))
      .map((b) => ({ bucket: b, items: buckets.get(b)! }));
  }, [cats, search]);

  const enabledCount = allowed?.length ?? 0;
  const totalCount = cats?.length ?? 0;
  const active = allowed != null;
  const labelCount = active ? `${enabledCount}/${totalCount}` : `${totalCount}`;

  const allowedSet = useMemo(() => allowed == null ? null : new Set(allowed), [allowed]);

  const isOn = (id: number) => allowedSet == null ? true : allowedSet.has(id);

  const toggleGroup = (b: LangBucket, items: Category[], on: boolean) => {
    const ids = items.map((i) => i.id);
    const existing = allowedSet ?? new Set<number>(cats?.map((c) => c.id) ?? []);
    const next = new Set(existing);
    for (const id of ids) {
      if (on) next.add(id); else next.delete(id);
    }
    void setAll(providerId, contentType, Array.from(next));
    void b; // silence unused
  };

  const toggleCollapsed = (b: LangBucket) =>
    setCollapsed((cur) => {
      const next = new Set(cur);
      if (next.has(b)) next.delete(b); else next.add(b);
      return next;
    });

  return (
    <div ref={wrapRef} style={{ position: "relative", display: "inline-block" }}>
      <button
        className={`filter-button${active ? " active" : ""}`}
        onClick={() => setOpen((o) => !o)}
        title={`Filter categories — ${active ? "Filtered" : "All"}`}
      >
        <span className="filter-icon" aria-hidden>☰</span>
        <span>{label ?? "Categories"}</span>
        <span className={`filter-badge${active ? " active" : ""}`}>{labelCount}</span>
      </button>
      {open && (
        <div className={`filter-popover filter-popover-large${computedAlign === "right" ? " anchor-right" : ""}`} onClick={(e) => e.stopPropagation()}>
          <div style={{ display: "flex", gap: 6, marginBottom: 8 }}>
            <input
              autoFocus
              style={{ flex: 1, minWidth: 0 }}
              placeholder="Search (RTL OK)…"
              dir="auto"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <button onClick={() => clear(providerId, contentType)} title="Show all">All</button>
            <button onClick={() => setAll(providerId, contentType, [])} title="Hide all">None</button>
          </div>
          <div className="filter-list">
            {groups.length === 0 && <div style={{ color: "var(--fg-muted)", padding: 8 }}>No categories</div>}
            {groups.map(({ bucket, items }) => {
              const allOn = items.every((c) => isOn(c.id));
              const noneOn = items.every((c) => !isOn(c.id));
              const isCollapsed = collapsed.has(bucket);
              return (
                <div key={bucket} className="filter-group">
                  <div className="filter-group-header">
                    <button
                      className="filter-group-toggle"
                      onClick={() => toggleCollapsed(bucket)}
                      aria-expanded={!isCollapsed}
                    >
                      <span className="filter-group-caret">{isCollapsed ? "▸" : "▾"}</span>
                      <span dir="auto">{BUCKET_NAMES[bucket]}</span>
                      <span className="filter-group-count">{items.length}</span>
                    </button>
                    <div className="filter-group-actions">
                      <button disabled={allOn} onClick={() => toggleGroup(bucket, items, true)}>All</button>
                      <button disabled={noneOn} onClick={() => toggleGroup(bucket, items, false)}>None</button>
                    </div>
                  </div>
                  {!isCollapsed && (
                    <div className="filter-chip-grid">
                      {/* Cap displayed chips to keep huge providers responsive — search to narrow further. */}
                      {items.slice(0, 200).map((c) => {
                        const on = isOn(c.id);
                        return (
                          <button
                            key={c.id}
                            type="button"
                            dir="auto"
                            className={`filter-chip${on ? " on" : ""}`}
                            onClick={() => toggle(providerId, contentType, c.id)}
                            title={c.name}
                          >
                            <span className="filter-chip-check" aria-hidden>{on ? "✓" : ""}</span>
                            <span className="filter-chip-label">{c.name}</span>
                            {c.isAdult && <span className="filter-chip-adult">18+</span>}
                          </button>
                        );
                      })}
                      {items.length > 200 && (
                        <div style={{ width: "100%", color: "var(--fg-muted)", fontSize: 11, padding: 4 }}>
                          {items.length - 200} more — type to narrow
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
          <div style={{ marginTop: 8, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ color: "var(--fg-muted)", fontSize: 12 }}>
              {active ? `${enabledCount} of ${totalCount} categories shown` : `Showing all ${totalCount}`}
            </span>
            <button onClick={() => setOpen(false)}>Close</button>
          </div>
        </div>
      )}
    </div>
  );
}
