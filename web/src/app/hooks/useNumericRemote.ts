// Watches digit key presses (0-9) and assembles them into a channel number.
// After a 1.5s pause the buffered digits are flushed to the callback as a numeric
// channel input — mimicking a TV remote's "tap 5-0-1 to go to channel 501".

import { useEffect, useRef } from "react";

export function useNumericRemote(onChannel: (num: number) => void, enabled = true) {
  const bufRef = useRef("");
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!enabled) return;
    const flush = () => {
      const n = Number.parseInt(bufRef.current, 10);
      bufRef.current = "";
      if (Number.isFinite(n)) onChannel(n);
    };
    const handler = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return;
      if (!/^[0-9]$/.test(e.key)) return;
      bufRef.current += e.key;
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(flush, 1500);
    };
    window.addEventListener("keydown", handler);
    return () => {
      window.removeEventListener("keydown", handler);
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [onChannel, enabled]);
}
