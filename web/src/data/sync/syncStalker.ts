// Stalker Portal catalog sync (live channels only — VOD/series API varies by portal).
// Wraps StalkerClient and writes into the same channelRepo/categoryRepo as Xtream so
// the rest of the UI works transparently.

import type { Category, Channel, Provider } from "@domain/model";
import { StalkerClient } from "@data/providers/stalker";
import { categoryRepo, channelRepo } from "@data/db/repositories";
import type { SyncProgress } from "@data/sync/syncCatalog";

export async function syncStalkerCatalog(provider: Provider, onProgress: SyncProgress = () => {}): Promise<void> {
  onProgress("Stalker — connecting…");
  const client = new StalkerClient({
    portalUrl: provider.serverUrl,
    macAddress: provider.username, // we re-use the username field for the MAC
    timezone: provider.stalkerDeviceTimezone || "Europe/Paris",
  });
  await client.handshake();
  await client.getProfile();

  onProgress("Stalker — categories…");
  const cats = await client.itvCategories();
  await channelRepo.clearProvider(provider.id);
  await categoryRepo.clearProvider(provider.id);

  const catRows: (Category & { providerId: number })[] = cats.map((c) => ({
    id: Number.parseInt(c.id, 10) || 0,
    roomId: Number.parseInt(c.id, 10) || 0,
    name: c.title,
    parentId: null,
    type: "LIVE" as const,
    isVirtual: false,
    count: 0,
    isAdult: /xxx|adult|18\+/i.test(c.title),
    isUserProtected: false,
    providerId: provider.id,
  }));
  await categoryRepo.bulkAdd(catRows);

  onProgress("Stalker — channels…");
  const list = await client.itvChannels();
  const rows = list.data ?? [];

  // Stalker `cmd` is something like "ffmpeg http://.../live/...". The real URL must
  // be obtained by create_link; we resolve lazily on first play to avoid hammering
  // the portal during sync — store the original cmd as a placeholder.
  const channels: Channel[] = rows.map((s, i) => ({
    id: Number.parseInt(s.id, 10) || i + 1,
    name: s.name,
    canonicalName: s.name,
    logoUrl: s.logo || null,
    groupTitle: null,
    categoryId: Number.parseInt(s.tv_genre_id, 10) || null,
    categoryName: null,
    streamUrl: s.cmd.replace(/^ffmpeg\s+/, ""),
    epgChannelId: s.xmltv_id || null,
    number: i + 1,
    isFavorite: false,
    catchUpSupported: false,
    catchUpDays: 0,
    catchUpSource: null,
    providerId: provider.id,
    currentProgram: null,
    nextProgram: null,
    isAdult: false,
    isUserProtected: false,
    logicalGroupId: s.name,
    selectedVariantId: Number.parseInt(s.id, 10) || i + 1,
    errorCount: 0,
    qualityOptions: [],
    alternativeStreams: [],
    variants: [],
    streamId: Number.parseInt(s.id, 10) || i + 1,
  }));
  await channelRepo.bulkAdd(channels);

  onProgress(`Stalker — done. ${channels.length} channels.`);
}
