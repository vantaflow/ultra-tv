// Compte cloud : appairage, synchronisation périodique des fournisseurs, partage d'une source locale.
// Le jeton d'appareil est chiffré avec safeStorage (via encryptSecret) ; il n'est jamais journalisé.

import { create } from "zustand";
import { getSetting, setSetting, db } from "@/db/db";
import { deleteSource, emptySource, listSources, saveSource } from "@/db/sources";
import type { Source } from "@/db/types";
import { usePrefs } from "@/state/prefs";
import { useSync } from "@/state/sync";
import { decryptSecret, encryptSecret } from "@/net/secrets";
import { detectSourceLanguages } from "@/sync/client";
import { OTHER_LANG } from "@/sync/core";
import {
  DEFAULT_WORKER, InvalidFieldError, NotFoundError, RateLimitedError, TokenRejectedError,
  deleteProvider, fetchConfig, normalizeWorkerUrl, putProvider, renameDevice, rotateToken,
  type CloudDevice, type CloudProvider, type ProviderInput,
} from "./client";
import { reconcile } from "./reconcile";

const K = {
  worker: "cloud.worker", token: "cloud.token", deviceId: "cloud.deviceId", tokenAt: "cloud.tokenAt", etag: "cloud.etag",
  lastSync: "cloud.lastSync", deviceName: "cloud.deviceName", push: "cloud.pushAvailable", devices: "cloud.devices",
} as const;
const ROTATE_AFTER_MS = 90 * 86400_000;
export const SYNC_EVERY_MS = 6 * 3600_000;

interface CloudState {
  loaded: boolean;
  paired: boolean;
  worker: string;
  deviceId: string;
  deviceName: string;
  lastSyncAt: number;
  syncing: boolean;
  error: string | null;
  /** Liste des appareils du compte quand le Worker la fournit ; sinon leur nombre. */
  devices: CloudDevice[] | number;
  /** false = le Worker n'a pas l'endpoint de partage : l'option est masquée. null = inconnu. */
  pushAvailable: boolean | null;
}

export const useCloud = create<CloudState>(() => ({
  loaded: false, paired: false, worker: DEFAULT_WORKER, deviceId: "", deviceName: "", lastSyncAt: 0, syncing: false, error: null, devices: 0, pushAvailable: null,
}));
const patch = (p: Partial<CloudState>) => useCloud.setState(p);

export function defaultDeviceName(): string {
  const p = (typeof window !== "undefined" ? window.ultratv?.platform : "") ?? "";
  return p === "darwin" ? "Mac" : p === "win32" ? "PC Windows" : p === "linux" ? "PC Linux" : "Navigateur";
}

export async function loadCloud(): Promise<void> {
  const [worker, deviceId, lastSyncAt, deviceName, push, devices, token] = await Promise.all([
    getSetting<string>(K.worker, DEFAULT_WORKER), getSetting<string>(K.deviceId, ""), getSetting<number>(K.lastSync, 0),
    getSetting<string>(K.deviceName, ""), getSetting<boolean | null>(K.push, null), getSetting<CloudDevice[] | number>(K.devices, 0),
    getSetting<string>(K.token, ""),
  ]);
  patch({ loaded: true, paired: !!token, worker, deviceId, lastSyncAt, deviceName: deviceName || defaultDeviceName(), pushAvailable: push, devices });
}

async function getToken(): Promise<string> {
  return decryptSecret(await getSetting<string>(K.token, ""));
}

export async function saveWorker(raw: string, allowLoopback = false): Promise<boolean> {
  const n = normalizeWorkerUrl(raw || DEFAULT_WORKER, allowLoopback);
  if (!n) return false;
  await setSetting(K.worker, n);
  patch({ worker: n });
  return true;
}

export async function saveDeviceName(name: string, remote = true): Promise<void> {
  const n = name.trim().slice(0, 64) || defaultDeviceName();
  await setSetting(K.deviceName, n);
  patch({ deviceName: n });
  // Renommage dans le compte (PATCH /api/device) : sans effet si le Worker ne l'a pas encore.
  if (remote && useCloud.getState().paired) {
    const token = await getToken();
    if (token) try { await renameDevice(useCloud.getState().worker, token, n); await setSetting(K.etag, ""); } catch { /* local seulement */ }
  }
}

export async function storeToken(token: string, deviceId: string): Promise<void> {
  await setSetting(K.token, await encryptSecret(token));
  await setSetting(K.deviceId, deviceId);
  await setSetting(K.tokenAt, Date.now());
  await setSetting(K.etag, "");
  patch({ paired: true, deviceId, error: null });
}

/** Dissocie cet appareil : jeton effacé ; les sources restent mais ne sont plus liées au compte. */
export async function unpair(): Promise<void> {
  await db.settings.bulkDelete([K.token, K.deviceId, K.tokenAt, K.etag, K.lastSync, K.devices]);
  for (const s of await listSources()) if (s.cloudId) await saveSource({ ...s, cloudId: undefined, cloudOrigin: undefined, cloudShared: undefined, cloudOriginName: undefined });
  patch({ paired: false, deviceId: "", lastSyncAt: 0, devices: 0 });
}

export interface SyncSummary { added: number; updated: number; removed: number; skipped: number; unchanged: boolean }

async function autoLangs(source: Source): Promise<string[] | null> {
  try {
    const d = await detectSourceLanguages(source);
    const ui = usePrefs.getState().lang.toUpperCase();
    const pre = d.languages.filter((l) => l.code === ui || l.code === OTHER_LANG).map((l) => l.code);
    return pre.length && pre.length < d.languages.length ? pre : null;
  } catch { return null; }
}

async function syncNewSources(ids: number[]): Promise<void> {
  for (const id of ids) {
    const rows = await listSources();
    const s = rows.find((x) => x.id === id);
    if (!s) continue;
    const langs = s.type === "xtream" ? await autoLangs(s) : null;
    const withLangs = { ...s, langs };
    await saveSource(withLangs);
    while (useSync.getState().running) await new Promise((r) => setTimeout(r, 500));
    await useSync.getState().start(withLangs, { silent: true });
  }
}

let running: Promise<SyncSummary> | null = null;

/** Récupère la configuration du compte et la fusionne (ajout, modification, retrait). */
export function syncCloud(opts: { force?: boolean; awaitSync?: boolean } = {}): Promise<SyncSummary> {
  running ??= doSync(opts).finally(() => { running = null; });
  return running;
}

async function doSync({ force, awaitSync }: { force?: boolean; awaitSync?: boolean }): Promise<SyncSummary> {
  const { worker } = useCloud.getState();
  let token = await getToken();
  if (!token) throw new Error("not-paired");
  patch({ syncing: true, error: null });
  try {
    if (Date.now() - (await getSetting<number>(K.tokenAt, 0)) > ROTATE_AFTER_MS) {
      try { const r = await rotateToken(worker, token); await storeToken(r.token, r.deviceId); token = r.token; } catch (e) { if (e instanceof TokenRejectedError) throw e; /* on réessaiera */ }
    }
    const etag = force ? "" : await getSetting<string>(K.etag, "");
    const res = await fetchConfig(worker, token, etag);
    const now = Date.now();
    await setSetting(K.lastSync, now);
    patch({ lastSyncAt: now });
    if (res.unchanged) return { added: 0, updated: 0, removed: 0, skipped: 0, unchanged: true };
    await setSetting(K.etag, res.etag);
    const devs = res.config.devices;
    await setSetting(K.devices, devs);
    patch({ devices: devs });
    const me = Array.isArray(devs) ? devs.find((d) => d.isCurrent || d.id === res.config.self) : undefined;
    if (res.config.self) { await setSetting(K.deviceId, res.config.self); patch({ deviceId: res.config.self }); }
    if (me) { await saveDeviceName(me.name, false); if (me.id) { await setSetting(K.deviceId, me.id); patch({ deviceId: me.id }); } }
    else if (res.config.deviceName) await saveDeviceName(res.config.deviceName, false);
    return await applyProviders(res.config.providers, !!awaitSync);
  } catch (e) {
    if (e instanceof TokenRejectedError) {
      await db.settings.bulkDelete([K.token, K.etag]);
      patch({ paired: false, error: "token-rejected" });
    } else patch({ error: e instanceof RateLimitedError ? "rate-limited" : "network" });
    throw e;
  } finally {
    patch({ syncing: false });
  }
}

export async function applyProviders(remote: CloudProvider[], awaitSync = false): Promise<SyncSummary> {
  const local = await listSources();
  const plan = reconcile(local, remote, emptySource);
  const newIds: number[] = [];
  for (const s of plan.add) newIds.push(await saveSource(s));
  const resync: number[] = [];
  for (const u of plan.update) {
    const cur = local.find((s) => s.id === u.id);
    if (!cur) continue;
    await saveSource({ ...cur, ...u.patch });
    if (u.resync) resync.push(u.id);
  }
  const active = usePrefs.getState().activeSourceId;
  for (const id of plan.remove) { await deleteSource(id); }
  if (active != null && plan.remove.includes(active)) usePrefs.getState().set({ activeSourceId: null });
  for (const id of plan.detach) {
    const cur = local.find((s) => s.id === id);
    if (cur) await saveSource({ ...cur, cloudId: undefined, cloudOrigin: undefined, cloudShared: undefined });
  }
  const todo = [...newIds, ...resync];
  const p = syncNewSources(todo);
  if (awaitSync) await p; else void p.catch(() => undefined);
  return { added: plan.add.length, updated: plan.update.length, removed: plan.remove.length, skipped: plan.skipped, unchanged: false };
}

export type ShareTarget = "all" | string[];

export type ShareResult = { ok: true } | { ok: false; reason: "unavailable" | "limit" | "unsupported" | "error" };

/** Partage une source locale avec le compte (et choisit les appareils destinataires). */
export async function shareSource(source: Source, target: ShareTarget): Promise<ShareResult> {
  const { worker } = useCloud.getState();
  const token = await getToken();
  if (!token) return { ok: false, reason: "error" };
  const input: ProviderInput = {
    id: source.cloudId,
    kind: source.type === "xtream" ? "XTREAM" : "M3U",
    name: source.name,
    url: source.type === "xtream" ? source.server : source.m3uUrl,
    username: source.type === "xtream" ? source.username : undefined,
    password: source.type === "xtream" ? source.password : undefined,
    shareWith: target,
  };
  if (source.type === "m3u" && source.m3uUrl.startsWith("file:")) return { ok: false, reason: "unsupported" };
  try {
    let p: CloudProvider;
    try {
      p = await putProvider(worker, token, input);
    } catch (e) {
      // Worker plus ancien : il ne connaît pas encore `shareWith` -> partage avec tous les appareils.
      if (e instanceof InvalidFieldError && e.field === "shareWith") p = await putProvider(worker, token, { ...input, shareWith: undefined });
      else throw e;
    }
    await saveSource({ ...source, cloudId: p.id, cloudOrigin: source.cloudOrigin ?? "local", cloudShared: target === "all" ? "all" : target.length });
    await setSetting(K.push, true);
    patch({ pushAvailable: true });
    await setSetting(K.etag, "");
    return { ok: true };
  } catch (e) {
    if (e instanceof NotFoundError) { await setSetting(K.push, false); patch({ pushAvailable: false }); return { ok: false, reason: "unavailable" }; }
    if (e instanceof TokenRejectedError) { patch({ paired: false, error: "token-rejected" }); }
    return { ok: false, reason: e instanceof Error && e.message === "limit" ? "limit" : "error" };
  }
}

/** Retire une source du compte (et du lien local). */
export async function unshareSource(source: Source): Promise<boolean> {
  const { worker } = useCloud.getState();
  const token = await getToken();
  if (!token || !source.cloudId) return false;
  try { await deleteProvider(worker, token, source.cloudId); } catch (e) { if (!(e instanceof NotFoundError)) return false; }
  await saveSource({ ...source, cloudId: undefined, cloudOrigin: undefined, cloudShared: undefined });
  return true;
}

let timer: ReturnType<typeof setInterval> | null = null;
/** Synchro au lancement puis toutes les 6 h tant que l'application est ouverte. */
export function startCloudSchedule(): () => void {
  if (timer) return () => undefined;
  const tick = () => { if (useCloud.getState().paired) void syncCloud().catch(() => undefined); };
  tick();
  timer = setInterval(tick, SYNC_EVERY_MS);
  return () => { if (timer) clearInterval(timer); timer = null; };
}
