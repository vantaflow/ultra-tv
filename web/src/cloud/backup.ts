// Serveur de secours (Vercel) : utilisé UNIQUEMENT quand le Worker Cloudflare est en panne, pour lire la configuration et
// les annonces. Même logique que WorkerFallback.kt (Android) ; voir docs/plan-secours-vercel.md.

export const BACKUP_HOST = "utv-backup.vercel.app";

/** Chemins lisibles en secours (liste blanche identique à celle du serveur de secours). */
const BACKUP_PATHS = new Set(["/api/config", "/api/announcements"]);

/** Seules les lectures (GET) de la liste blanche peuvent aller au secours : jamais une écriture, jamais le jeton ailleurs. */
export function backupEligible(method: string | undefined, url: string): boolean {
  if ((method ?? "GET").toUpperCase() !== "GET") return false;
  try { return BACKUP_PATHS.has(new URL(url).pathname); } catch { return false; }
}

/** Statut qui signale une panne du principal : 500, 502, 503, 504 et les 52x de Cloudflare. Jamais un 4xx, 304 ni 429. */
export function isOutageStatus(status: number): boolean {
  return status === 500 || status === 502 || status === 503 || status === 504 || (status >= 520 && status <= 526);
}

/** Une réponse du secours n'est retenue que si elle est bonne : un 401 du secours ne doit jamais délier un appareil. */
export function backupAccepted(status: number): boolean {
  return status === 200 || status === 304;
}

/** Même requête, adressée au secours (le jeton part seulement vers cette adresse fixe). */
export function toBackupUrl(url: string): string {
  const u = new URL(url);
  u.protocol = "https:"; u.host = BACKUP_HOST; u.port = "";
  return u.toString();
}

/**
 * Disjoncteur : 2 échecs consécutifs du principal ouvrent le secours pendant 60 s (les lectures vont d'emblée au secours),
 * puis une requête d'essai repart vers le principal ; un nouvel échec double la durée (jusqu'à 5 min), un succès referme.
 */
export class BackupBreaker {
  static readonly BASE_MS = 60_000;
  static readonly MAX_MS = 300_000;
  static readonly THRESHOLD = 2;
  private failures = 0;
  private openUntil = 0;
  private cooldownMs = BackupBreaker.BASE_MS;
  constructor(private now: () => number = () => Date.now()) {}

  /** Vrai tant que le principal est tenu pour en panne (essayer le secours d'abord). */
  preferBackup(): boolean { return this.now() < this.openUntil; }

  success(): void { this.failures = 0; this.openUntil = 0; this.cooldownMs = BackupBreaker.BASE_MS; }

  failure(): void {
    this.failures++;
    if (this.failures < BackupBreaker.THRESHOLD) return;
    // Échec d'un essai après ouverture : on double la pause.
    if (this.openUntil > 0) this.cooldownMs = Math.min(this.cooldownMs * 2, BackupBreaker.MAX_MS);
    this.openUntil = this.now() + this.cooldownMs;
  }

  reset(): void { this.success(); }
}
