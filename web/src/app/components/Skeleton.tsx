// Skeleton shell that mimics the loaded layout. Used by Home / Movies / Series in
// place of "Loading…" so the page doesn't pop in.

interface Props {
  variant?: "shelf" | "grid" | "row" | "block";
  count?: number;
}

export function Skeleton({ variant = "shelf", count = 6 }: Props) {
  if (variant === "shelf") {
    return (
      <div className="shelf">
        {Array.from({ length: count }, (_, i) => (
          <div key={i} className="poster-card">
            <div className="skeleton" style={{ width: 160, height: 240, borderRadius: 10 }} />
            <div className="skeleton" style={{ height: 14, marginTop: 8, width: "70%" }} />
          </div>
        ))}
      </div>
    );
  }
  if (variant === "grid") {
    return (
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))", gap: 12 }}>
        {Array.from({ length: count }, (_, i) => (
          <div key={i}>
            <div className="skeleton" style={{ height: 240, borderRadius: 10 }} />
            <div className="skeleton" style={{ height: 14, marginTop: 8 }} />
          </div>
        ))}
      </div>
    );
  }
  if (variant === "row") {
    return (
      <div style={{ display: "grid", gap: 6 }}>
        {Array.from({ length: count }, (_, i) => (
          <div key={i} className="skeleton" style={{ height: 56, borderRadius: 8 }} />
        ))}
      </div>
    );
  }
  return <div className="skeleton" style={{ height: 200, borderRadius: 10 }} />;
}
