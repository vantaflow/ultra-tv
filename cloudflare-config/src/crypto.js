// Primitives WebCrypto : AES-GCM (identifiants au repos), PBKDF2 (mots de passe),
// jetons aléatoires, comparaison en temps constant.

const enc = new TextEncoder();
const dec = new TextDecoder();

// ---- encodages -------------------------------------------------------------

export function b64uEncode(buf) {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function b64uDecode(str) {
  const s = str.replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(s + "=".repeat((4 - (s.length % 4)) % 4));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

export function toHex(buf) {
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function sha256Hex(input) {
  return toHex(await crypto.subtle.digest("SHA-256", enc.encode(input)));
}

/** 256 bits d'entropie, base64url sans padding (43 caractères). */
export function randomToken(bytes = 32) {
  return b64uEncode(crypto.getRandomValues(new Uint8Array(bytes)));
}

/** Comparaison en temps constant, indépendante de la longueur de l'entrée. */
export function timingSafeEqual(a, b) {
  const x = enc.encode(String(a ?? ""));
  const y = enc.encode(String(b ?? ""));
  let diff = x.length ^ y.length;
  const n = Math.max(x.length, y.length);
  for (let i = 0; i < n; i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

// ---- AES-GCM ---------------------------------------------------------------

function rawKey(b64) {
  if (!b64) throw new Error("PROVIDER_ENC_KEY non configurée");
  let bytes;
  try { bytes = Uint8Array.from(atob(b64.trim()), (c) => c.charCodeAt(0)); }
  catch { throw new Error("PROVIDER_ENC_KEY n'est pas du base64 valide"); }
  if (bytes.length !== 32) throw new Error("PROVIDER_ENC_KEY doit faire 32 octets (AES-256)");
  return bytes;
}

async function importKey(bytes) {
  return crypto.subtle.importKey("raw", bytes, "AES-GCM", false, ["encrypt", "decrypt"]);
}

async function keyId(bytes) {
  return toHex(await crypto.subtle.digest("SHA-256", bytes)).slice(0, 8);
}

/** Construit {KEY, KEY_PREVIOUS} depuis l'environnement du Worker. */
export function keysFromEnv(env) {
  return { KEY: env.PROVIDER_ENC_KEY, KEY_PREVIOUS: env.PROVIDER_ENC_KEY_PREVIOUS };
}

/**
 * Chiffre `value` (JSON) → `v1.<kid>.<iv>.<ciphertext>`.
 * `aad` (l'identifiant du compte) est authentifié : un blob copié d'un compte
 * à un autre ne se déchiffre plus.
 */
export async function encryptJson(keys, value, aad) {
  const raw = rawKey(keys.KEY);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv, additionalData: enc.encode(aad) },
    await importKey(raw),
    enc.encode(JSON.stringify(value)),
  );
  return `v1.${await keyId(raw)}.${b64uEncode(iv)}.${b64uEncode(ct)}`;
}

export async function decryptJson(keys, blob, aad) {
  const [ver, kid, iv, ct] = String(blob).split(".");
  if (ver !== "v1" || !kid || !iv || !ct) throw new Error("blob chiffré invalide");
  const candidates = [keys.KEY, keys.KEY_PREVIOUS].filter(Boolean).map(rawKey);
  for (const raw of candidates) {
    if ((await keyId(raw)) !== kid) continue;
    const pt = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: b64uDecode(iv), additionalData: enc.encode(aad) },
      await importKey(raw),
      b64uDecode(ct),
    );
    return JSON.parse(dec.decode(pt));
  }
  throw new Error("aucune clé ne correspond à ce blob (rotation incomplète ?)");
}

// ---- Mots de passe ---------------------------------------------------------

// Cloudflare Workers plafonne PBKDF2 à 100 000 itérations : c'est le maximum
// autorisé ET le minimum OWASP-compatible demandé. Le coût réel est borné
// par la limitation de débit (voir ratelimit) plutôt que par l'algorithme.
export const PBKDF2_ITERATIONS = 100_000;

async function pbkdf2(passwordBytes, saltBytes, iterations) {
  const key = await crypto.subtle.importKey("raw", passwordBytes, "PBKDF2", false, ["deriveBits"]);
  return new Uint8Array(await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt: saltBytes, iterations, hash: "SHA-256" }, key, 256));
}

/** `pbkdf2$sha256$<iters>$<sel b64u>$<hash b64u>` — auto-descriptif. */
export async function hashPassword(plain) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const hash = await pbkdf2(enc.encode(plain), salt, PBKDF2_ITERATIONS);
  return `pbkdf2$sha256$${PBKDF2_ITERATIONS}$${b64uEncode(salt)}$${b64uEncode(hash)}`;
}

// Hachage factice : un compte inconnu coûte autant de temps qu'un compte réel
// (pas d'oracle d'énumération par le temps de réponse).
let dummyHash;
async function dummyVerify(plain) {
  dummyHash ??= await hashPassword("dummy-password-for-timing");
  await verifyNew(dummyHash, plain);
}

async function verifyNew(stored, plain) {
  const [, , iters, salt, hash] = stored.split("$");
  const got = await pbkdf2(enc.encode(plain), b64uDecode(salt), parseInt(iters, 10));
  return timingSafeEqual(b64uEncode(got), hash);
}

/**
 * Vérifie un mot de passe contre un compte. Gère trois formats :
 *   - `pbkdf2$sha256$…` (actuel) ;
 *   - `pbkdf2$<iters>$<hex>` + `salt` (ancien Worker, clé = "sel:mdp") ;
 *   - 64 hex + `salt` (tout premier format : SHA-256 simple).
 * `needsRehash` vaut true pour les deux anciens formats.
 */
export async function verifyPassword(account, plain) {
  if (!account?.passwordHash || typeof plain !== "string") {
    await dummyVerify(String(plain ?? ""));
    return { ok: false, needsRehash: false };
  }
  const h = account.passwordHash;
  if (h.startsWith("pbkdf2$sha256$")) {
    return { ok: await verifyNew(h, plain), needsRehash: false };
  }
  if (h.startsWith("pbkdf2$")) {
    const [, iters, hex] = h.split("$");
    const salt = account.salt || "";
    const got = await pbkdf2(enc.encode(`${salt}:${plain}`), enc.encode(salt), parseInt(iters, 10) || PBKDF2_ITERATIONS);
    return { ok: timingSafeEqual(toHex(got), hex || ""), needsRehash: true };
  }
  const got = await sha256Hex(`${account.salt}:${plain}`);
  return { ok: timingSafeEqual(got, h), needsRehash: true };
}
