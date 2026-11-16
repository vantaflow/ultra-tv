// Code parental du bureau (4 chiffres) : sert à protéger les règles de temps d'écran et à déverrouiller un blocage.
// Local à l'appareil, jamais synchronisé ; on ne garde qu'un hachage SHA-256 salé (jamais le code en clair).

const KEY = "ultratv.parental.v1";
interface Stored { salt: string; hash: string }

const bytesToHex = (b: ArrayBuffer) => [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");

export async function hashCode(code: string, salt: string): Promise<string> {
  return bytesToHex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${salt}:${code}`)));
}

/** Un code valide = exactement 4 chiffres. */
export const validCode = (c: string) => /^\d{4}$/.test(c);

function read(): Stored | null {
  try {
    const o = JSON.parse(localStorage.getItem(KEY) ?? "null") as Stored | null;
    return o && typeof o.salt === "string" && typeof o.hash === "string" ? o : null;
  } catch { return null; }
}

export const hasParentalCode = (): boolean => read() !== null;

export async function setParentalCode(code: string): Promise<boolean> {
  if (!validCode(code)) return false;
  const salt = bytesToHex(crypto.getRandomValues(new Uint8Array(8)).buffer);
  try { localStorage.setItem(KEY, JSON.stringify({ salt, hash: await hashCode(code, salt) } satisfies Stored)); return true; } catch { return false; }
}

export function clearParentalCode(): void { try { localStorage.removeItem(KEY); } catch { /* stockage indisponible */ } }

let failed = 0;
/** Vérifie un code (vrai si aucun code n'est défini). Après 3 erreurs de suite, réponse retardée (1 s, 4 s, 16 s…) contre l'essai de toutes les combinaisons. */
export async function checkParentalCode(code: string): Promise<boolean> {
  const s = read();
  if (!s) return true;
  if (failed >= 3) await new Promise((r) => setTimeout(r, 1000 << (Math.min(failed - 3, 4) * 2)));
  const ok = (await hashCode(code, s.salt)) === s.hash;
  failed = ok ? 0 : failed + 1;
  return ok;
}
