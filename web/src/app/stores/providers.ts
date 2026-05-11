// Provider lifecycle: add, switch, sync.
// Delegates the heavy work to data/sync/* — the store is just orchestration + state.

import { create } from "zustand";
import type { Channel, Provider, ProviderType } from "@domain/model";
import { parseM3u } from "@data/parsers/m3u";
import { syncXtreamCatalog } from "@data/sync/syncCatalog";
import { syncStalkerCatalog } from "@data/sync/syncStalker";
import { fetchEpg } from "@data/sync/syncEpg";
import { proxiedText } from "@data/net/proxy";
import { channelRepo, providerRepo } from "@data/db/repositories";

interface AddProviderInput {
  name: string;
  type: ProviderType;
  serverUrl?: string;
  username?: string;
  password?: string;
  m3uUrl?: string;
  epgUrl?: string;
  userAgent?: string;
  httpReferer?: string;
}

interface ProviderState {
  activeProviderId: number | null;
  syncing: boolean;
  syncMessage: string | null;
  syncProgress: { current: number; total: number } | null;
  syncSummary: string | null;
  lastError: string | null;
  setActive: (id: number | null) => void;
  addProvider: (input: AddProviderInput) => Promise<number>;
  updateProvider: (id: number, patch: Partial<Provider>) => Promise<void>;
  syncProvider: (id: number) => Promise<void>;
  cancelSync: () => void;
}

let syncAbortController: AbortController | null = null;

function makeProvider(input: AddProviderInput): Omit<Provider, "id"> {
  const now = Date.now();
  return {
    name: input.name,
    type: input.type,
    serverUrl: input.serverUrl ?? "",
    username: input.username ?? "",
    password: input.password ?? "",
    m3uUrl: input.m3uUrl ?? "",
    epgUrl: input.epgUrl ?? "",
    stalkerMacAddress: "",
    stalkerDeviceProfile: "",
    stalkerDeviceTimezone: "",
    stalkerDeviceLocale: "",
    userAgent: input.userAgent ?? "",
    httpReferer: input.httpReferer ?? "",
    isActive: true,
    maxConnections: 1,
    expirationDate: null,
    apiVersion: null,
    allowedOutputFormats: [],
    epgSyncMode: "BACKGROUND",
    xtreamFastSyncEnabled: true,
    m3uVodClassificationEnabled: false,
    status: "UNKNOWN",
    lastSyncedAt: 0,
    createdAt: now,
  };
}

export const useProviderStore = create<ProviderState>((set, get) => ({
  activeProviderId: null,
  syncing: false,
  syncMessage: null,
  syncProgress: null,
  syncSummary: null,
  lastError: null,

  setActive: (id) => set({ activeProviderId: id }),

  async addProvider(input) {
    const id = (await providerRepo.upsert({ ...makeProvider(input), id: 0 as never })) as number;
    set({ activeProviderId: id });
    await get().syncProvider(id);
    return id;
  },

  async updateProvider(id, patch) {
    const existing = await providerRepo.get(id);
    if (!existing) throw new Error(`Provider ${id} not found`);
    await providerRepo.upsert({ ...existing, ...patch });
  },

  cancelSync() {
    syncAbortController?.abort();
  },

  async syncProvider(id) {
    syncAbortController?.abort();
    syncAbortController = new AbortController();
    const signal = syncAbortController.signal;
    set({ syncing: true, lastError: null, syncMessage: "Starting sync…", syncProgress: null, syncSummary: null });
    try {
      const provider = await providerRepo.get(id);
      if (!provider) throw new Error(`Provider ${id} not found`);

      if (provider.type === "M3U") {
        set({ syncMessage: "Fetching M3U…" });
        const text = await proxiedText(provider.m3uUrl, {
          userAgent: provider.userAgent || null,
          referer: provider.httpReferer || null,
        });
        const entries = parseM3u(text);
        await channelRepo.clearProvider(id);
        const channels: Channel[] = entries.map((e, idx) => ({
          id: 0,
          name: e.name,
          canonicalName: e.tvgName ?? e.name,
          logoUrl: e.tvgLogo,
          groupTitle: e.groupTitle,
          categoryId: null,
          categoryName: e.groupTitle,
          streamUrl: e.url,
          epgChannelId: e.tvgId,
          number: e.tvgChno ?? idx + 1,
          isFavorite: false,
          catchUpSupported: e.catchUp != null,
          catchUpDays: e.catchUpDays,
          catchUpSource: e.catchUpSource,
          providerId: id,
          currentProgram: null,
          nextProgram: null,
          isAdult: false,
          isUserProtected: false,
          logicalGroupId: e.tvgName ?? e.name,
          selectedVariantId: 0,
          errorCount: 0,
          qualityOptions: [],
          alternativeStreams: [],
          variants: [],
          streamId: 0,
        }));
        await channelRepo.bulkAdd(channels);
      } else if (provider.type === "XTREAM_CODES") {
        const result = await syncXtreamCatalog(provider, (message, current, total) => {
          set({
            syncMessage: message,
            syncProgress: current != null && total != null ? { current, total } : null,
          });
        }, signal);
        set({
          syncSummary: `✓ ${result.channelsCount} channels · ${result.moviesCount} movies · ${result.seriesCount} series` +
            (result.movieCategoriesSkipped + result.seriesCategoriesSkipped > 0
              ? ` (skipped ${result.movieCategoriesSkipped + result.seriesCategoriesSkipped} filtered categories)`
              : ""),
        });
      } else if (provider.type === "STALKER_PORTAL") {
        await syncStalkerCatalog(provider, (message, current, total) => {
          const detail = current != null && total != null ? `${message} (${current}/${total})` : message;
          set({ syncMessage: detail });
        });
      } else {
        throw new Error(`Provider type ${provider.type} not implemented yet`);
      }

      if (provider.epgUrl) {
        set({ syncMessage: "Fetching EPG…" });
        try {
          await fetchEpg(provider.epgUrl, id, provider.userAgent || null, provider.httpReferer || null);
        } catch (e) {
          console.warn("EPG fetch failed:", e);
        }
      }

      await providerRepo.upsert({ ...provider, lastSyncedAt: Date.now(), status: "ACTIVE" });
      set({ syncMessage: "Done" });
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") {
        set({ lastError: null, syncMessage: "Cancelled", syncSummary: "Sync cancelled by user" });
      } else {
        set({ lastError: e instanceof Error ? e.message : String(e) });
      }
    } finally {
      set({ syncing: false, syncProgress: null });
      syncAbortController = null;
    }
  },
}));
