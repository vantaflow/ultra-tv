import { useEffect, useRef, useState } from "react";
import { chooseSrc, lowMemoryDevice, optimizeImageUrl, PROXY_TIMEOUT_MS, type ImageKind } from "@/lib/imageProxy";
import { usePrefs } from "@/state/prefs";

/**
 * Adresse à afficher pour une image distante : via wsrv.nl pour Fanart.tv / TVmaze (préférence « Optimiser les images »),
 * sinon l'originale. Si wsrv.nl échoue (`onError`) ou n'a rien rendu au bout de 4 s (`onLoad` jamais appelé),
 * on retombe sur l'originale. Renvoie l'adresse et les gestionnaires à poser sur l'<img>.
 */
export function useOptimizedSrc(src: string | null | undefined, hint?: ImageKind): { src: string | null | undefined; onLoad: () => void; fallback: () => boolean } {
  const enabled = usePrefs((s) => s.optimizeImages);
  const proxied = optimizeImageUrl(src, { enabled, lowMem: lowMemoryDevice(), hint });
  const [fellBack, setFellBack] = useState<string | null>(null);
  const loaded = useRef<string | null>(null);
  const useProxy = !!proxied && fellBack !== src;
  useEffect(() => {
    if (!useProxy || !src) return;
    const id = setTimeout(() => { if (loaded.current !== src) setFellBack(src); }, PROXY_TIMEOUT_MS);
    return () => clearTimeout(id);
  }, [useProxy, src]);
  return {
    src: chooseSrc(src, proxied, fellBack),
    onLoad: () => { loaded.current = src ?? null; },
    /** À appeler sur `onError` : true si on vient de basculer sur l'originale (rien à signaler à l'appelant). */
    fallback: () => { if (useProxy) { setFellBack(src ?? null); return true; } return false; },
  };
}
