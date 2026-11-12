// Relevé anonyme d'installation de l'application de bureau (mêmes règles que sur Android : AnonPing.kt).
//  - identifiant aléatoire gardé localement (jamais lié à un compte), au plus un envoi par 20 h ;
//  - seulement si l'appareil n'est pas appairé (il est alors déjà compté avec son compte) ;
//  - seulement si le réglage « Télémétrie anonyme » est activé (par défaut). Désactivé : rien ne part, l'identifiant est effacé ;
//  - contenu : identifiant, version, système (windows / macos / linux). Le pays est déduit par le Worker, jamais l'adresse IP gardée.
import { getSetting, setSetting, db } from "@/db/db";
import { bridge } from "@/net/transport";
import { resolveWorker, cloudAvailable, normalizeWorkerUrl, sendAnonPing, type AnonPingBody } from "./client";
import { useCloud } from "./service";

/** Au plus un relevé par 20 h (marge pour ne pas glisser d'un jour à l'autre), comme sur Android. */
export const ANON_MIN_INTERVAL_MS = 20 * 3600_000;
const CHECK_EVERY_MS = 6 * 3600_000;
const K = { on: "anon.telemetry", install: "anon.install", last: "anon.last" } as const;

/** Dû si jamais envoyé, si 20 h au moins se sont écoulées, ou si l'horloge est revenue en arrière. */
export const anonDue = (lastMs: number, nowMs: number): boolean => lastMs <= 0 || nowMs < lastMs || nowMs - lastMs >= ANON_MIN_INTERVAL_MS;

/** Système déclaré au Worker à partir de `process.platform` d'Electron ; null hors Windows / macOS / Linux. */
export function anonPlatform(raw: string | undefined): AnonPingBody["platform"] | null {
  return raw === "win32" ? "windows" : raw === "darwin" ? "macos" : raw === "linux" ? "linux" : null;
}

/** Version à déclarer (« 1.2.43 ») : uniquement des chiffres, lettres, points, + et - (sinon rien n'est envoyé). */
export const anonVersion = (raw: string | undefined): string | null => (raw && /^[0-9][0-9A-Za-z.+-]{0,31}$/.test(raw) ? raw : null);

/** Dépendances de `reportAnonPing` (remplaçables dans les tests). */
export interface AnonDeps {
  now(): number;
  /** Réglage « Télémétrie anonyme » (activé par défaut). */
  enabled(): Promise<boolean>;
  paired(): boolean;
  worker(): string;
  version(): string | undefined;
  platform(): string | undefined;
  last(): Promise<number>;
  setLast(ms: number): Promise<void>;
  install(): Promise<string>;
  send(base: string, body: AnonPingBody): Promise<boolean>;
}

/** Envoie le relevé s'il est dû. Renvoie « sent » (accepté), « off » (réglage désactivé), « paired », « skipped » (pas dû, ou rien à déclarer) ou « error ». */
export async function reportAnonPing(d: AnonDeps): Promise<"sent" | "off" | "paired" | "skipped" | "error"> {
  if (!(await d.enabled())) return "off";
  if (d.paired()) return "paired";
  const now = d.now();
  if (!anonDue(await d.last(), now)) return "skipped";
  const platform = anonPlatform(d.platform());
  const version = anonVersion(d.version());
  const base = normalizeWorkerUrl(resolveWorker(d.worker()));
  if (!platform || !version || !base) return "skipped";
  const ok = await d.send(base, { install: await d.install(), version, platform });
  if (!ok) return "error";
  await d.setLast(now);
  return "sent";
}

/** Réglage « Télémétrie anonyme » : activé par défaut. */
export const readAnonTelemetry = async (): Promise<boolean> => (await getSetting<boolean>(K.on, true)) !== false;

/** Active / désactive. Désactivé : l'identifiant d'installation et la date du dernier envoi sont effacés. */
export async function writeAnonTelemetry(on: boolean): Promise<void> {
  await setSetting(K.on, on);
  if (!on) await db.settings.bulkDelete([K.install, K.last]);
}

async function installId(): Promise<string> {
  const cur = await getSetting<string>(K.install, "");
  if (/^[A-Za-z0-9-]{16,64}$/.test(cur)) return cur;
  const id = crypto.randomUUID();
  await setSetting(K.install, id);
  return id;
}

const realDeps = (): AnonDeps => ({
  now: () => Date.now(),
  enabled: readAnonTelemetry,
  paired: () => useCloud.getState().paired,
  worker: () => useCloud.getState().worker,
  version: () => bridge()?.version,
  platform: () => bridge()?.platform,
  last: () => getSetting<number>(K.last, 0),
  setLast: async (ms) => { await setSetting(K.last, ms); },
  install: installId,
  send: sendAnonPing,
});

/** Au lancement puis toutes les 6 h tant que l'application est ouverte (le relevé lui-même : au plus un par 20 h). Application de bureau seulement. */
export function startAnonPing(deps: AnonDeps = realDeps()): () => void {
  if (!cloudAvailable()) return () => undefined;
  const tick = () => { void reportAnonPing(deps).catch(() => undefined); };
  tick();
  const timer = setInterval(tick, CHECK_EVERY_MS);
  return () => clearInterval(timer);
}
