import { useState } from "react";
import { transportSync, wrapUrl } from "@/net/transport";

/** Image distante : passe par le transport (obligatoire sous CSP stricte d'Electron), repli neutre en cas d'échec. */
export function Img({ src, alt = "", contain = false, className = "" }: { src?: string | null; alt?: string; contain?: boolean; className?: string }) {
  const [failed, setFailed] = useState<string | null>(null);
  if (!src || failed === src || !/^https?:/i.test(src)) return null;
  return (
    <img
      className={`img${contain ? " contain" : ""} ${className}`}
      src={wrapUrl(transportSync(), src)}
      alt={alt}
      loading="lazy"
      decoding="async"
      draggable={false}
      referrerPolicy="no-referrer"
      onError={() => setFailed(src)}
    />
  );
}
