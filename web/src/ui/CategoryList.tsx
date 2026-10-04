import { useMemo, useState } from "react";
import type { CategoryRow } from "@/db/types";
import { useT } from "@/i18n";
import { normText } from "@/lib/text";
import { Icon } from "./Icon";
import { VList, arrayRows } from "./Virtual";

export interface CatEntry { id: string; label: string; n?: number; badge?: string | null }

export function CategoryList({ cats, value, onPick, extra = [], searchable = true }: {
  cats: CategoryRow[]; value: string; onPick: (id: string) => void; extra?: CatEntry[]; searchable?: boolean;
}) {
  const t = useT();
  const [q, setQ] = useState("");
  const entries = useMemo<CatEntry[]>(() => {
    const nq = normText(q);
    const all = cats.map((c) => ({ id: c.extId, label: c.label, n: c.count, badge: c.badge }));
    return [...(nq ? [] : extra), ...(nq ? all.filter((c) => normText(c.label).includes(nq) || (c.badge ?? "").toLowerCase() === nq) : all)];
  }, [cats, q, extra]);
  return (
    <>
      {searchable && (
        <div className="search-box" style={{ padding: "0 0.75rem 0.5rem" }}>
          <Icon name="search" size={16} />
          <input className="input" placeholder={t("common.filter")} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t("common.categories")} />
        </div>
      )}
      <VList
        rows={arrayRows(entries)}
        rowH={50}
        label={t("common.categories")}
        render={(c) => c && (
          <button className={`cat-item${value === c.id ? " on" : ""}`} onClick={() => onPick(c.id)} role="option" aria-selected={value === c.id}>
            <span className="ellipsis">{c.badge && <span className="cbadge" style={{ marginInlineEnd: 8 }}>{c.badge}</span>}{c.label}</span>
            {c.n != null && <span className="n mono">{c.n}</span>}
          </button>
        )}
      />
    </>
  );
}
