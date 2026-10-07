// Compte cloud : appairage, synchronisation périodique des fournisseurs, partage d'une source locale.
// Le jeton d'appareil est chiffré avec safeStorage (via encryptSecret) ; il n'est jamais journalisé.

import { create } from "zustand";
import { getSetting, setSetting, db } from "@/db/db";
import { deleteSource, emptySource, getSource, listSources, saveSource } from "@/db/sources";
import type { CategoryRow, Source } from "@/db/types";
import { usePrefs } from "@/state/prefs";
import { useSync } from "@/state/sync";
import { decryptSecret, encryptSecret } from "@/net/secrets";
import { detectSourceLanguages } from "@/sync/client";
import { OTHER_LANG } from "@/sync/core";
import {
  DEFAULT_WORKER, InvalidFieldError, NotFoundError, RateLimitedError, TokenRejectedError,
  deleteProvider, fetchConfig, normalizeWorkerUrl, putPrefs, putProvider, renameDevice, rotateToken, tmdbPosterPath,
  type CloudDevice, type CloudPrefs, type CloudProvider, type ProviderInput,
} from "./client";
import { reconcile } from "./reconcile";
import { syncSourceState } from "./sharedState";
import { setPosterFinder } from "@/lib/posterFallback";
import { createBatcher, fromPrefs, shouldApply, toPrefs } from "./prefs";

const K = {
  worker: "cloud.worker", token: "cloud.token", deviceId: "cloud.deviceId", tokenAt: "cloud.tokenAt", etag: "cloud.etag",
  lastSync: "cloud.lastSync", deviceName: "cloud.deviceName", push: "cloud.pushAvailable", devices: "cloud.devices", prefsSync: "cloud.prefsSync", prefsBoot: "cloud.prefsBoot",
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
  /** Partage des langues et catégories désactivées entre appareils (activé par défaut). */
  prefsSync: boolean;
}

export const useCloud = create<CloudState>(() => ({
  loaded: false, paired: false, worker: DEFAULT_WORKER, deviceId: "", deviceName: "", lastSyncAt: 0, syncing: false, error: null, devices: 0, pushAvailable: null, prefsSync: true,
}));
const patch = (p: Partial<CloudState>) => useCloud.setState(p);

export function defaultDeviceName(): string {
  const p = (typeof window !== "undefined" ? window.ultratv?.platform : "") ?? "";
  return p === "darwin" ? "Mac" : p === "win32" ? "PC Windows" : p === "linux" ? "PC Linux" : "Navigateur";
}

export async function loadCloud(): Promise<void> {
  const [worker, deviceId, lastSyncAt, deviceName, push, devices, token, prefsSync] = await Promise.all([
    getSetting<string>(K.worker, DEFAULT_WORKER), getSetting<string>(K.deviceId, ""), getSetting<number>(K.lastSync, 0),
    getSetting<string>(K.deviceName, ""), getSetting<boolean | null>(K.push, null), getSetting<CloudDevice[] | number>(K.devices, 0),
    getSetting<string>(K.token, ""), getSetting<boolean>(K.prefsSync, true),
  ]);
  patch({ loaded: true, paired: !!token, worker, deviceId, lastSyncAt, deviceName: deviceName || defaultDeviceName(), pushAvailable: push, devices, prefsSync });
  // Affiches manquantes : recherche TMDB via le Worker (clé côté serveur), seulement si l'appareil est appairé.
  setPosterFinder(token ? async (kind, query, year) => {
    const t = await getToken();
    return t ? tmdbPosterPath(useCloud.getState().worker, t, kind, query, year, usePrefs.getState().lang === "en" ? "en-US" : "fr-FR") : null;
  } : null);
}

export async function getToken(): Promise<string> {
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
    // Première synchro avec cette fonction : lecture complète (même si rien n'a changé) pour amorcer/appliquer les réglages.
    const booted = await getSetting<boolean>(K.prefsBoot, false);
    const etag = force || !booted ? "" : await getSetting<string>(K.etag, "");
    const res = await fetchConfig(worker, token, etag);
    const now = Date.now();
    await setSetting(K.lastSync, now);
    patch({ lastSyncAt: now });
    if (res.unchanged) return { added: 0, updated: 0, removed: 0, skipped: 0, unchanged: true };
    await setSetting(K.etag, res.etag);
    await setSetting(K.prefsBoot, true);
    const devs = res.config.devices;
    await setSetting(K.devices, devs);
    patch({ devices: devs });
    const me = Array.isArray(devs) ? devs.find((d) => d.isCurrent || d.id === res.config.self) : undefined;
    if (res.config.self) { await setSetting(K.deviceId, res.config.self); patch({ deviceId: res.config.self }); }
    if (me) { await saveDeviceName(me.name, false); if (me.id) { await setSetting(K.deviceId, me.id); patch({ deviceId: me.id }); } }
    else if (res.config.deviceName) await saveDeviceName(res.config.deviceName, false);
    const summary = await applyProviders(res.config.providers, !!awaitSync);
    void syncSharedState();
    return summary;
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
  const p = syncNewSources(todo).then(() => applyRemotePrefs(remote, new Set(todo))).then(resyncAfterPrefs);
  if (awaitSync) await p; else void p.catch(() => undefined);
  return { added: plan.add.length, updated: plan.update.length, removed: plan.remove.length, skipped: plan.skipped, unchanged: false };
}

// ---------------- Réglages d'affichage partagés (langues + catégories désactivées) ----------------

const prefsAtKey = (cloudId: string) => `cloud.prefsAt.${cloudId}`;
export const getPrefsAt = (cloudId: string) => getSetting<number>(prefsAtKey(cloudId), 0);
const PUBLISH_DELAY_MS = 1500;
/** Sources dont un envoi est en attente : on n'écrase pas leurs changements locaux par une version reçue. */
const pendingPublish = new Set<number>();

export async function setPrefsSync(on: boolean): Promise<void> {
  await setSetting(K.prefsSync, on);
  patch({ prefsSync: on });
  if (on) await setSetting(K.etag, ""); // prochaine synchro : relit les réglages du compte
}

async function catsOf(cid: number): Promise<CategoryRow[]> {
  return cid ? db.categories.where("[sourceId+kind]").between([cid, ""], [cid, "\uffff"]).toArray() : [];
}

async function relaunchSync(id: number): Promise<void> {
  while (useSync.getState().running) await new Promise((r) => setTimeout(r, 500));
  const s = (await listSources()).find((x) => x.id === id);
  if (s) await useSync.getState().start(s, { preserveFlags: true, silent: true });
}
async function resyncAfterPrefs(ids: number[]): Promise<void> {
  for (const id of ids) await relaunchSync(id);
}

/** Applique des réglages reçus à une source (sans jamais republier). Retourne true si le catalogue chargé doit être relu. */
async function applyPrefsTo(s: Source, prefs: CloudPrefs): Promise<boolean> {
  const cats = await catsOf(s.cid);
  if (!cats.length) return false; // catalogue pas encore là : réessayé après la première synchro
  const plan = fromPrefs(prefs, cats);
  if (plan.changes.length) await db.categories.bulkUpdate(plan.changes.map((c) => ({ key: c.id, changes: { enabled: c.enabled } })));
  await saveSource({ ...(await getSource(s.id!) ?? s), langs: plan.langs });
  await setSetting(prefsAtKey(s.cloudId!), prefs.updatedAt);
  return plan.changes.length > 0;
}

/** Réception : pour chaque source liée dont les réglages du compte sont plus récents que ceux appliqués ici. */
export async function applyRemotePrefs(remote: CloudProvider[], force: Set<number> = new Set()): Promise<number[]> {
  if (!useCloud.getState().prefsSync) return [];
  const resync: number[] = [];
  const rows = await listSources();
  for (const p of remote) {
    const s = rows.find((x) => x.cloudId === p.id);
    if (!s || pendingPublish.has(s.id!)) continue;
    if (!p.prefs) {
      // Amorçage : le compte n'a rien pour cette source, l'état local (déjà personnalisé) devient la valeur par défaut des autres appareils.
      if (s.cid) await publishPrefs(s.id!);
      continue;
    }
    if (!force.has(s.id!) && !shouldApply(p.prefs, await getPrefsAt(p.id))) continue;
    if (await applyPrefsTo(s, p.prefs)) resync.push(s.id!);
  }
  return resync;
}

export type PublishOutcome = "sent" | "applied" | "skipped" | "error";

/** Publie les réglages d'une source (langues + catégories désactivées). 409 : la version du compte est plus récente, on l'applique. */
export async function publishPrefs(sourceId: number): Promise<PublishOutcome> {
  const st = useCloud.getState();
  if (!st.paired || !st.prefsSync) return "skipped";
  const s = (await listSources()).find((x) => x.id === sourceId);
  if (!s?.cloudId || !s.cid) return "skipped";
  const token = await getToken();
  if (!token) return "skipped";
  try {
    const prefs = toPrefs(s.langs, await catsOf(s.cid), Date.now());
    const r = await putPrefs(st.worker, token, s.cloudId, prefs);
    if (r.status === "ok") { await setSetting(prefsAtKey(s.cloudId), prefs.updatedAt); return "sent"; }
    if (r.status === "stale") {
      if (await applyPrefsTo(s, r.prefs)) void resyncAfterPrefs([sourceId]).catch(() => undefined);
      return "applied";
    }
    return "skipped";
  } catch (e) {
    if (e instanceof TokenRejectedError) patch({ paired: false, error: "token-rejected" });
    return "error";
  }
}

const batcher = createBatcher<number>(PUBLISH_DELAY_MS, (ids) => {
  for (const id of ids) pendingPublish.delete(id);
  for (const id of ids) void publishPrefs(id).catch(() => undefined);
});
/** À appeler quand l'utilisateur change les langues ou les catégories d'une source : un seul envoi après 1,5 s de calme. */
export function notePrefsChanged(sourceId: number): void {
  if (!useCloud.getState().prefsSync) return;
  pendingPublish.add(sourceId);
  batcher.note(sourceId);
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

let stateRunning: Promise<void> | null = null;
/** Favoris, reprises et derniers vus de chaque source du compte (en arrière-plan, n'échoue jamais). */
export function syncSharedState(): Promise<void> {
  stateRunning ??= (async () => {
    const { worker, paired } = useCloud.getState();
    if (!paired) return;
    const token = await getToken();
    if (!token) return;
    for (const s of await listSources()) {
      if (!s.cloudId) continue;
      try { await syncSourceState(worker, token, s); } catch { /* réessayé au prochain passage */ }
    }
  })().finally(() => { stateRunning = null; });
  return stateRunning;
}

let timer: ReturnType<typeof setInterval> | null = null;
let stateTimer: ReturnType<typeof setInterval> | null = null;
/** Synchro au lancement puis toutes les 6 h tant que l'application est ouverte. */
export function startCloudSchedule(): () => void {
  if (timer) return () => undefined;
  const tick = () => { if (useCloud.getState().paired) void syncCloud().catch(() => undefined); };
  tick();
  timer = setInterval(tick, SYNC_EVERY_MS);
  // Favoris et reprises : plus souvent que le reste (changent pendant l'usage), sans relire la configuration.
  stateTimer = setInterval(() => { void syncSharedState(); }, 5 * 60_000);
  // Retour sur la fenêtre (après avoir utilisé la TV, par exemple) : échange immédiat, au plus une fois par minute.
  let lastFocus = 0;
  const onFocus = () => { if (Date.now() - lastFocus > 60_000) { lastFocus = Date.now(); void syncSharedState(); } };
  window.addEventListener("focus", onFocus);
  return () => {
    if (timer) clearInterval(timer); timer = null; if (stateTimer) clearInterval(stateTimer); stateTimer = null;
    window.removeEventListener("focus", onFocus);
  };
}
