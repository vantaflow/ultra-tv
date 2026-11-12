import { useState, type ReactNode } from "react";
import { transportSync, wrapUrl } from "@/net/transport";
import { useOptimizedSrc } from "./useOptimizedSrc";

/**
 * Logo officiel du titre (Fanart.tv) à la place du titre texte ; le texte reste en `alt` et sert de repli
 * (pas de logo, image cassée). `children` : éléments ajoutés après le titre (badge « Vu »…).
 */
export function TitleLogo({ logo, title, children }: { logo: string | null | undefined; title: string; children?: ReactNode }) {
  const [failed, setFailed] = useState<string | null>(null);
  const opt = useOptimizedSrc(logo, "logo");
  const ok = !!logo && failed !== logo && (/^https:/i.test(logo) || logo.startsWith("data:image/svg+xml"));
  if (!ok) return <>{title}{children}</>;
  const src = logo.startsWith("data:") ? logo : wrapUrl(transportSync(), opt.src ?? logo);
  return (
    <>
      <img className="title-logo" src={src} alt={title} decoding="async" draggable={false} referrerPolicy="no-referrer" onLoad={opt.onLoad} onError={() => { if (!opt.fallback()) setFailed(logo); }} />
      {children}
    </>
  );
}
