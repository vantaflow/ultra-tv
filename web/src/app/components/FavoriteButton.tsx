// Toggle favorite button. Uses the favorites store, surfaces current state via Dexie live query.

import { useLiveQuery } from "dexie-react-hooks";
import { favoriteRepo } from "@data/db/repositories";
import { useFavoritesStore } from "@app/stores/favorites";
import type { ContentType } from "@domain/model";

interface Props {
  providerId: number;
  contentId: number;
  contentType: ContentType;
  size?: "small" | "large";
}

export function FavoriteButton({ providerId, contentId, contentType, size = "large" }: Props) {
  const fav = useLiveQuery(() => favoriteRepo.byContent(providerId, contentId), [providerId, contentId]);
  const toggle = useFavoritesStore((s) => s.toggle);

  const active = !!fav;

  return (
    <button
      className={`fav-btn fav-${size}${active ? " active" : ""}`}
      onClick={(e) => { e.stopPropagation(); e.preventDefault(); void toggle(providerId, contentId, contentType); }}
      title={active ? "Remove from favorites" : "Add to favorites"}
      aria-pressed={active}
    >
      <span aria-hidden>{active ? "★" : "☆"}</span>
      {size === "large" && <span>{active ? "Favorited" : "Favorite"}</span>}
    </button>
  );
}
