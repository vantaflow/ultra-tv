import type { SyncProgress } from "@/db/types";
import { useT } from "@/i18n";
import { useSync } from "@/state/sync";

/** Avancement global (0..1) d'une synchro : direct, films, séries puis guide pèsent chacun une part de la barre. */
export function overallRatio(p: SyncProgress | null): number {
  if (!p) return 0;
  const r = Math.min(1, Math.max(0, p.ratio));
  switch (p.phase) {
    case "categories": return 0;
    case "live": return 0.15 * r;
    case "movie": return 0.15 + 0.5 * r;
    case "series": return 0.65 + 0.2 * r;
    case "epg": return 0.85 + 0.15 * r;
    default: return 1;
  }
}

/**
 * Indicateur discret d'une synchro en cours (barre fine en haut + pastille) : visible sur tous les écrans du catalogue
 * (Direct, Films, Séries…) sans rien masquer, car l'ancien catalogue reste utilisable pendant ce temps.
 */
export function SyncPill() {
  const t = useT();
  const running = useSync((s) => s.running);
  const progress = useSync((s) => s.progress);
  if (!running) return null;
  const pct = Math.round(overallRatio(progress) * 100);
  return (
    <div className="sync-pill" role="status" aria-live="off" aria-label={t("sync.catalogUpdate", { p: pct })}>
      <div className="bar"><i style={{ width: `${pct}%` }} /></div>
      <div className="tag"><span className="sync-dot" />{t("sync.catalogUpdate", { p: pct })}</div>
    </div>
  );
}
