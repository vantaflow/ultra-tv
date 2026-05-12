// Global search across live channels, movies, and series.
// Mirrors SearchContent use case behaviour. Persists recent queries to searchHistory.

import { useLiveQuery } from "dexie-react-hooks";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { channelRepo, movieRepo, seriesRepo, searchHistoryRepo } from "@data/db/repositories";
import { useProviderStore } from "@app/stores/providers";
import { useDebounced } from "@app/hooks/useDebounced";
import type { Channel, Movie, Series } from "@domain/model";

type Scope = "ALL" | "LIVE" | "MOVIE" | "SERIES";

export function SearchScreen() {
  const providerId = useProviderStore((s) => s.activeProviderId);
  const [query, setQuery] = useState("");
  const [scope, setScope] = useState<Scope>("ALL");

  const channels = useLiveQuery<Channel[]>(async () => providerId == null ? [] : channelRepo.forProvider(providerId), [providerId]);
  const movies = useLiveQuery<Movie[]>(async () => providerId == null ? [] : movieRepo.forProvider(providerId), [providerId]);
  const series = useLiveQuery<Series[]>(async () => providerId == null ? [] : seriesRepo.forProvider(providerId), [providerId]);
  const history = useLiveQuery(() => searchHistoryRepo.recent(scope, 10), [scope]);

  const debouncedQuery = useDebounced(query, 250);
  const results = useMemo(() => {
    const lc = debouncedQuery.toLowerCase().trim();
    if (!lc) return { channels: [], movies: [], series: [] };
    const match = <T extends { name: string }>(arr: T[] | undefined) =>
      (arr ?? []).filter((x) => x.name.toLowerCase().includes(lc)).slice(0, 50);
    return {
      channels: scope === "ALL" || scope === "LIVE" ? match(channels) : [],
      movies: scope === "ALL" || scope === "MOVIE" ? match(movies) : [],
      series: scope === "ALL" || scope === "SERIES" ? match(series) : [],
    };
  }, [debouncedQuery, scope, channels, movies, series]);

  const commit = async () => {
    if (!query.trim()) return;
    await searchHistoryRepo.add({ query, scope, timestamp: Date.now() });
  };

  return (
    <div>
      <h2>Search</h2>
      <div style={{ display: "flex", gap: 8 }}>
        <input autoFocus value={query} onChange={(e) => setQuery(e.target.value)} onBlur={commit} placeholder="Search…" type="search" inputMode="search" enterKeyHint="search" />
        <select value={scope} onChange={(e) => setScope(e.target.value as Scope)}>
          <option value="ALL">All</option>
          <option value="LIVE">Live</option>
          <option value="MOVIE">Movies</option>
          <option value="SERIES">Series</option>
        </select>
      </div>
      {!query && history && history.length > 0 && (
        <div style={{ marginTop: 12 }}>
          <h4>Recent</h4>
          {history.map((h) => (
            <button key={h.id} style={{ marginRight: 8 }} onClick={() => setQuery(h.query)}>{h.query}</button>
          ))}
        </div>
      )}

      {results.channels.length > 0 && (
        <section><h3>Live</h3>
          <ul>{results.channels.map((c) => <li key={c.id}>{c.name}</li>)}</ul>
        </section>
      )}
      {results.movies.length > 0 && (
        <section><h3>Movies</h3>
          <ul>{results.movies.map((m) => <li key={m.id}><Link to={`/movies/${m.id}`}>{m.name}</Link></li>)}</ul>
        </section>
      )}
      {results.series.length > 0 && (
        <section><h3>Series</h3>
          <ul>{results.series.map((s) => <li key={s.id}><Link to={`/series/${s.id}`}>{s.name}</Link></li>)}</ul>
        </section>
      )}
    </div>
  );
}
