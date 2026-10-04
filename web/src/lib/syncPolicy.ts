// Règles de déclenchement de synchro, isolées pour être testées sans composant.
import type { Source } from "@/db/types";

/** Langues à synchroniser à la fin de l'assistant : seulement pour une source Xtream (type EFFECTIF, après conversion get.php) et un choix partiel. */
export function langsToSync(type: Source["type"], chosen: ReadonlySet<string>, detectedCount: number): string[] | null {
  return type === "xtream" && chosen.size < detectedCount ? [...chosen] : null;
}

/** Source jamais synchronisée (ex. M3U get.php convertie au démarrage). */
export const needsFirstSync = (s: Pick<Source, "state" | "lastSyncAt">) => s.state === "new" && s.lastSyncAt === 0;

/** Au lancement, aucune synchro ne tourne : un état « syncing » persisté vient d'une fermeture en cours de route. */
export const isStaleSyncing = (s: Pick<Source, "state">) => s.state === "syncing";
