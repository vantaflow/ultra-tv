import { Icon } from "./Icon";
import { Img } from "./Img";

export function PosterCard({ title, meta, image, rating, fav, onClick, progress }: {
  title: string; meta?: string; image?: string | null; rating?: number; fav?: boolean; onClick: () => void; progress?: number;
}) {
  return (
    <button type="button" className="poster" onClick={onClick} title={title}>
      <span className="p">
        <span className="ph">{title}</span>
        <Img src={image} />
        {rating != null && rating > 0 && <span className="rate">★ {rating.toFixed(1)}</span>}
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
