// Icônes du design (tracés des maquettes Android TV), trait 2 px arrondi.
const P = {
  home: "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z",
  live: "M2 8h20v12H2zM7 3l5 5 5-5",
  guide: "M3 5h18v14H3zM3 10h18M9 10v9",
  movies: "M4 4h16v16H4zM8 4v16M16 4v16M4 9h4M4 15h4M16 9h4M16 15h4",
  series: "M3 8h18v12H3zM8 4h8",
  search: "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4",
  heart: "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z",
  settings: "M4 6h16M4 12h16M4 18h16M8 4v4M16 10v4M10 16v4",
  source: "M3 5h18v12H3zM8 21h8M12 17v4",
  list: "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01",
  play: "M7 4v16l13-8z",
  pause: "M7 4h3.5v16H7zM13.5 4H17v16h-3.5z",
  back: "M15 6l-6 6 6 6",
  chevron: "M9 6l6 6-6 6",
  rew: "M11 5L4 12l7 7M20 5l-7 7 7 7",
  fwd: "M13 5l7 7-7 7M4 5l7 7-7 7",
  link: "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7",
  file: "M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9zM14 3v6h6M9 14h6M9 17h4",
  globe: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18",
  info: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7h.01",
  lock: "M6 10V8a6 6 0 0 1 12 0v2M5 10h14v11H5z",
  plus: "M12 5v14M5 12h14",
  check: "M5 12l5 5 9-10",
  x: "M6 6l12 12M18 6L6 18",
  pip: "M3 5h18v14H3zM12 12h7v5h-7z",
  full: "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5",
  volume: "M4 9v6h4l5 4V5L8 9zM16 9a4 4 0 0 1 0 6",
  mute: "M4 9v6h4l5 4V5L8 9zM17 9l4 6M21 9l-4 6",
  sliders: "M4 6h16M4 12h10M4 18h6",
  display: "M3 5h18v14H3zM8 9h8v6H8z",
  download: "M12 3v12M7 10l5 5 5-5M5 21h14",
  alert: "M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z",
  wifiOff: "M2 9a16 16 0 0 1 20 0M5 13a11 11 0 0 1 14 0M9 17a5 5 0 0 1 6 0M12 21h.01M3 3l18 18",
  refresh: "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7",
  trash: "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13",
  user: "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0",
  star: "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z",
  tv: "M3 5h18v12H3zM8 21h8",
  rec: "M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12z",
  up: "M8 10l4-4 4 4M8 14l4 4 4-4",
} as const;
export type IconName = keyof typeof P;

export function Icon({ name, size = 22, stroke = 2, fill = false, className }: { name: IconName; size?: number; stroke?: number; fill?: boolean; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={fill ? "currentColor" : "none"} stroke="currentColor" strokeWidth={stroke}
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={className}>
      <path d={P[name]} />
    </svg>
  );
}

/** Icône de l'application (maître : ultratv-icon.svg). */
export function AppMark({ size = 44, className = "logo" }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 512 512" aria-hidden="true" className={className}>
      <rect width="512" height="512" rx="116" fill="#0A0A0C" />
      <rect x="96" y="120" width="320" height="216" rx="40" fill="none" stroke="#F5F5F7" strokeWidth="28" />
      <path d="M224 188v80l70-40z" fill="#D91E2B" />
      <path d="M196 392h120" stroke="#F5F5F7" strokeWidth="28" strokeLinecap="round" />
    </svg>
  );
}
