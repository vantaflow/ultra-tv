// Global Cmd/Ctrl+K palette. Searches navigation actions + content (live channels,
// movies, series) and lets the user jump straight to the best match.

import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { channelRepo, movieRepo, seriesRepo } from "@data/db/repositories";
import { useProviderStore } from "@app/stores/providers";
import type { Channel, Movie, Series } from "@domain/model";

interface Item {
  id: string;
  label: string;
  hint?: string;
  action: () => void;
}

const NAV_ITEMS: Array<Omit<Item, "action"> & { path: string }> = [
  { id: "nav-home", label: "Home", hint: "→", path: "/" },
  { id: "nav-live", label: "Live TV", hint: "→", path: "/live" },
  { id: "nav-movies", label: "Movies", hint: "→", path: "/movies" },
  { id: "nav-series", label: "Series", hint: "→", path: "/series" },
  { id: "nav-guide", label: "Guide", hint: "→", path: "/guide" },
  { id: "nav-recordings", label: "Recordings", hint: "→", path: "/recordings" },
  { id: "nav-multiview", label: "Multi-View", hint: "→", path: "/multiview" },
  { id: "nav-settings", label: "Settings", hint: "→", path: "/settings" },
];

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const inputRef = useRef<HTMLInputElement | null>(null);
  const navigate = useNavigate();

  const providerId = useProviderStore((s) => s.activeProviderId);
  // Defer loading the (potentially huge) catalogs until the palette is opened
  // for the first time — keeps the rest of the app snappy.
  const channels = useLiveQuery<Channel[]>(
    async () => !open || providerId == null ? [] : channelRepo.forProvider(providerId),
    [open, providerId],
  );
  const movies = useLiveQuery<Movie[]>(
    async () => !open || providerId == null ? [] : movieRepo.forProvider(providerId),
    [open, providerId],
  );
  const series = useLiveQuery<Series[]>(
    async () => !open || providerId == null ? [] : seriesRepo.forProvider(providerId),
    [open, providerId],
  );

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if ((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((o) => !o);
      } else if (e.key === "Escape" && open) {
        setOpen(false);
      } else if (e.key === "/" && tag !== "INPUT" && tag !== "TEXTAREA") {
        e.preventDefault();
        setOpen(true);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [open]);

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 0);
    else setQuery("");
  }, [open]);

  const results: Item[] = useMemo(() => {
    const lc = query.toLowerCase().trim();
    const navItems: Item[] = NAV_ITEMS.map((n) => ({
      id: n.id,
      label: n.label,
      hint: n.hint,
      action: () => { navigate(n.path); setOpen(false); },
    }));

    if (!lc) return navItems;

    const match = <T extends { name: string }>(arr: T[] | undefined, mapper: (x: T) => Item, limit = 8) =>
      (arr ?? [])
        .filter((x) => x.name.toLowerCase().includes(lc))
        .slice(0, limit)
        .map(mapper);

    const navMatches = navItems.filter((i) => i.label.toLowerCase().includes(lc));
    const channelMatches = match(channels, (c) => ({
      id: `ch-${c.id}`, label: c.name, hint: "Live TV",
      action: () => { navigate("/live"); setOpen(false); },
    }));
    const movieMatches = match(movies, (m) => ({
      id: `m-${m.id}`, label: m.name, hint: "Movie",
      action: () => { navigate(`/movies/${m.id}`); setOpen(false); },
    }));
    const seriesMatches = match(series, (s) => ({
      id: `s-${s.id}`, label: s.name, hint: "Series",
      action: () => { navigate(`/series/${s.id}`); setOpen(false); },
    }));

    return [...navMatches, ...channelMatches, ...movieMatches, ...seriesMatches];
  }, [query, channels, movies, series, navigate]);

  if (!open) return null;

  return (
    <div className="cmdk-backdrop" onClick={() => setOpen(false)}>
      <div className="cmdk" onClick={(e) => e.stopPropagation()}>
        <input
          ref={inputRef}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Jump to a page, channel, movie or series…"
          onKeyDown={(e) => {
            if (e.key === "Enter" && results[0]) { results[0].action(); }
          }}
        />
        <ul>
          {results.slice(0, 30).map((r) => (
            <li key={r.id}>
              <button onClick={r.action}>
                <span>{r.label}</span>
                {r.hint && <span className="hint">{r.hint}</span>}
              </button>
            </li>
          ))}
          {results.length === 0 && <li style={{ padding: 12, color: "var(--fg-muted)" }}>No matches</li>}
        </ul>
        <div className="cmdk-foot">
          <kbd>Esc</kbd> close · <kbd>↵</kbd> open first · <kbd>⌘K</kbd> toggle
        </div>
      </div>
    </div>
  );
}
