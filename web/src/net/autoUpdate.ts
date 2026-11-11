// Réglage « Vérifier les mises à jour automatiquement » (application de bureau). Le processus principal le mémorise
// et coupe les vérifications automatiques (5 s après le lancement, puis toutes les 6 h) quand il est désactivé.
import { bridge } from "./transport";

type AutoUpdateBridge = { getAutoUpdate?(): Promise<boolean>; setAutoUpdate?(on: boolean): Promise<boolean> };

/** null : hors application de bureau (le réglage n'a pas lieu d'être affiché). Activé par défaut en cas d'échec. */
export async function readAutoUpdate(b: AutoUpdateBridge | undefined = bridge()): Promise<boolean | null> {
  if (!b?.getAutoUpdate || !b.setAutoUpdate) return null;
  try { return (await b.getAutoUpdate()) !== false; } catch { return true; }
}

/** Renvoie la valeur effectivement enregistrée (celle demandée si le processus principal ne répond pas). */
export async function writeAutoUpdate(on: boolean, b: AutoUpdateBridge | undefined = bridge()): Promise<boolean> {
  try { return (await b?.setAutoUpdate?.(on)) ?? on; } catch { return on; }
}
