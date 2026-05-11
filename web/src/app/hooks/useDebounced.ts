// Debounced state hook. Returns the latest value only after `delay` ms of inactivity.
// Used for search/filter inputs so each keystroke doesn't trigger a 100k-row scan.

import { useEffect, useState } from "react";

export function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}
