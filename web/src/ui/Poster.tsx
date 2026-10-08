import { useEffect, useState } from "react";
import { Icon } from "./Icon";
import { Img } from "./Img";
import { fallbackPoster } from "@/lib/posterFallback";

export function PosterCard({ title, meta, image, rating, fav, onClick, progress, kind = "movie", year, watched }: {
  title: string; meta?: string; image?: string | null; rating?: number; fav?: boolean; /** Déjà vu d'après Trakt. */ watched?: boolean; onClick: () => void; progress?: number;
  /** Type pour l'affiche de repli TMDB (fournisseur sans image ou image cassée). */
  kind?: "movie" | "tv"; year?: number | null;
}) {
  const [broken, setBroken] = useState(false);
  const [alt, setAlt] = useState<string | null>(null);
  const needFallback = !image || !/^https?:/i.test(image) || broken;
  useEffect(() => {
    if (!needFallback) return;
    let live = true;
    void fallbackPoster(title, kind, year).then((u) => { if (live) setAlt(u); });
    return () => { live = false; };
  }, [needFallback, title, kind, year]);
  const src = needFallback ? alt : image;
  return (
    <button type="button" className="poster" onClick={onClick} title={title}>
      <span className="p">
        <span className="ph">{title}</span>
        <Img key={src ?? ""} src={src} onFail={() => { if (src === image) setBroken(true); }} />
        {rating != null && rating > 0 && <span className="rate">★ {rating.toFixed(1)}</span>}
        {watched && <span className="seen" title="✓"><Icon name="check" size={14} stroke={3} /></span>}
        {fav && <span className="fav"><Icon name="heart" size={18} fill /></span>}
        {progress != null && progress > 0 && <span className="progress" style={{ position: "absolute", insetInline: 0, bottom: 0, borderRadius: 0 }}><i style={{ width: `${progress * 100}%` }} /></span>}
      </span>
      <b className="clamp-2">{title}</b>
      {meta && <span className="m ellipsis">{meta}</span>}
    </button>
  );
}

export function PosterSkeleton() {
  return (
    <div className="poster" aria-hidden="true">
      <span className="p skeleton" />
      <span className="skeleton" style={{ height: 14, width: "80%" }} />
    </div>
  );
}
