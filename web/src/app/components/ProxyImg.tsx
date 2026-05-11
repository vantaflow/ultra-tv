// Image element that routes through the configured proxy. Solves mixed-content
// (http:// images on an https:// page) and any provider-side CORS quirks for hot-linking.

import { proxify } from "@data/net/proxy";

interface Props extends Omit<React.ImgHTMLAttributes<HTMLImageElement>, "src"> {
  src: string | null | undefined;
  fallback?: React.ReactNode;
}

export function ProxyImg({ src, fallback, ...rest }: Props) {
  if (!src) return <>{fallback ?? null}</>;
  // Only proxy http(s) URLs — leave data:/blob: untouched
  const url = /^https?:\/\//i.test(src) ? proxify(src) : src;
  return <img src={url} loading="lazy" {...rest} />;
}
