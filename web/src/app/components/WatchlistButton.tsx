import { useLiveQuery } from "dexie-react-hooks";
import { watchlistRepo } from "@data/db/repositories";

interface Props {
  providerId: number;
  contentId: number;
  contentType: "MOVIE" | "SERIES" | "LIVE";
}

export function WatchlistButton({ providerId, contentId, contentType }: Props) {
  const entry = useLiveQuery(() => watchlistRepo.byContent(providerId, contentId), [providerId, contentId]);
  const active = !!entry;
  return (
    <button
      className={`fav-btn${active ? " active" : ""}`}
      onClick={(e) => { e.preventDefault(); e.stopPropagation(); void watchlistRepo.toggle(providerId, contentId, contentType); }}
      title={active ? "Remove from watchlist" : "Add to watchlist"}
    >
      <span aria-hidden>{active ? "✓" : "+"}</span>
      <span>{active ? "In watchlist" : "Watchlist"}</span>
    </button>
  );
}
