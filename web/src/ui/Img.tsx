import { useState } from "react";
import { transportSync, wrapUrl } from "@/net/transport";
import { useOptimizedSrc } from "./useOptimizedSrc";

/** Image distante : passe par le transport (obligatoire sous CSP stricte d'Electron), repli neutre en cas d'échec. */
export function Img({ src, alt = "", contain = false, className = "", onFail }: { src?: string | null; alt?: string; contain?: boolean; className?: string; onFail?: () => void }) {
  const [failed, setFailed] = useState<string | null>(null);
  // Fanart.tv / TVmaze : via wsrv.nl (repli sur l'originale en cas d'échec ou après 4 s).
  const opt = useOptimizedSrc(src);
  if (!src || failed === src || !/^https?:/i.test(src)) return null;
  return (
    <img
      className={`img${contain ? " contain" : ""} ${className}`}
      src={wrapUrl(transportSync(), opt.src ?? src)}
      alt={alt}
      loading={opt.src !== src ? "eager" : "lazy"}
      decoding="async"
      draggable={false}
      referrerPolicy="no-referrer"
      onLoad={opt.onLoad}
      onError={() => { if (opt.fallback()) return; setFailed(src); onFail?.(); }}
    />
  );
}
