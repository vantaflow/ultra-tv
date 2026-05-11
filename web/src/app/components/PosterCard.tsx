import { ProxyImg } from "@app/components/ProxyImg";

interface Props {
  posterUrl: string | null;
  title: string;
  subtitle?: string | null;
  progress?: number;
  onClick?: () => void;
}

export function PosterCard({ posterUrl, title, subtitle, progress, onClick }: Props) {
  return (
    <button className="poster-card" onClick={onClick}>
      <div className="poster-img">
        <ProxyImg src={posterUrl} alt="" fallback={<span className="poster-fallback">{title.slice(0, 2)}</span>} />
        {progress != null && progress > 0 && (
          <div className="poster-progress">
            <div style={{ width: `${Math.min(100, Math.max(0, progress * 100))}%` }} />
          </div>
        )}
      </div>
      <div className="poster-title">{title}</div>
      {subtitle && <div className="poster-subtitle">{subtitle}</div>}
    </button>
  );
}
