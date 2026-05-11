// Spatial-navigation provider. Wraps @noriginmedia/norigin-spatial-navigation so D-pad /
// arrow-key navigation works on TV-style remotes. The init is idempotent.

import { useEffect } from "react";
import { init, setKeyMap } from "@noriginmedia/norigin-spatial-navigation";

let initialised = false;

export function SpatialFocusBootstrap({ children }: { children: React.ReactNode }) {
  useEffect(() => {
    if (initialised) return;
    initialised = true;
    init({ debug: false, visualDebug: false });
    setKeyMap({
      left: [37],
      up: [38],
      right: [39],
      down: [40],
      enter: [13],
    });
  }, []);
  return <>{children}</>;
}
