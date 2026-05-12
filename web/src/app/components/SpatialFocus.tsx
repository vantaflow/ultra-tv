// Spatial-navigation provider. Installs the custom DOM-walking spatial nav
// (spatial/spatialNav.ts) which is the only one wired to the Android-TV native
// bridge via `window.__ultratv_remote`. Init is idempotent.

import { useEffect } from "react";
import { installSpatialNav } from "@app/spatial/spatialNav";

export function SpatialFocusBootstrap({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    installSpatialNav();
  }, []);
  return <>{children}</>;
}
