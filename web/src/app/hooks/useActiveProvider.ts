import { useLiveQuery } from "dexie-react-hooks";
import { providerRepo } from "@data/db/repositories";
import { useProviderStore } from "@app/stores/providers";

export function useActiveProvider() {
  const id = useProviderStore((s) => s.activeProviderId);
  return useLiveQuery(async () => {
    if (id == null) return undefined;
    return await providerRepo.get(id);
  }, [id]);
}
