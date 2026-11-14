// Synchro des profils entre appareils : règles pures (journal, conversions, plan). Même logique qu'Android (ProfileSync.kt).
// Jamais de code PIN dans la synchro. Profils rapprochés par NOM sans casse ; le plus récent (`at`) gagne.

import { LANGS } from "@/i18n/lang";
import type { Profile } from "@/state/prefs";

export const DEFAULT_PROFILE_NAME = "Principal";
const NAME_MAX = 24;

/** Profil tel qu'échangé avec le Worker : `on: false` = supprimé, `f` = ancien nom si renommé, `l` = langue (« system » = celle de l'appareil). */
export interface SharedProfile { n: string; c: number; k: boolean; at: number; on: boolean; f?: string; l?: string }
/** Journal local : date de la dernière modification par nom, tombes, ancien nom d'un renommage. Clé = nom sans casse. */
export interface LogEntry { n: string; at: number; on: boolean; from?: string }
export type ProfileLog = Record<string, LogEntry>;

export type ProfileAction =
  | { type: "create"; p: SharedProfile }
  | { type: "update"; id: string; p: SharedProfile }
  | { type: "delete"; id: string; p: SharedProfile }
  | { type: "rename"; id: string; p: SharedProfile };

export const profileKey = (n: string) => n.trim().toLowerCase();
/** Nom échangé : le profil sans nom est « Principal » (comme sur la TV) ; 24 caractères au plus (limite du Worker). */
export const wireName = (p: { name: string }) => (p.name.trim() || DEFAULT_PROFILE_NAME).slice(0, NAME_MAX).trim();

// ---------- Couleurs : « #RRGGBB » <-> ARGB entier signé 32 bits ----------

export function hexToArgb(hex: string): number {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  return m ? (0xff000000 | parseInt(m[1]!, 16)) | 0 : 0xff000000 | 0;
}
/** « #RRGGBB » (majuscules) ; null si la valeur est absente (0) : la couleur locale est alors conservée. */
export function argbToHex(c: number): string | null {
  if (!Number.isFinite(c) || c === 0) return null;
  return `#${(((c >>> 0) & 0xffffff).toString(16)).padStart(6, "0").toUpperCase()}`;
}
const sameColor = (hex: string, c: number) => { const h = argbToHex(c); return h === null || h.toLowerCase() === hex.toLowerCase(); };

// ---------- Langue ----------

const isLangCode = (l: string) => LANGS.some((x) => x.code === l);
/** Langue reçue → langue du profil (undefined = celle de l'appareil) ; null = absente ou inconnue, à ignorer. */
export function langFromWire(l: string | undefined): { lang: Profile["lang"] } | null {
  if (l === undefined) return null;
  if (l === "system") return { lang: undefined };
  return isLangCode(l) ? { lang: l as Profile["lang"] } : null;
}

// ---------- Journal ----------

/** Date de création d'un profil, déduite de son id (« p<ms> ») ; 1 pour le profil d'origine (le Worker refuse 0). */
export const createdAt = (p: { id: string }) => { const m = /^p(\d{10,})/.exec(p.id); return m ? Number(m[1]) : 1; };

export const logSet = (log: ProfileLog, n: string, at: number, on: boolean, from?: string): ProfileLog =>
  ({ ...log, [profileKey(n)]: { n, at, on, ...(from ? { from } : {}) } });

/** Met le journal à jour d'après un changement local de la liste (ajout, renommage, couleur / enfants / langue, suppression). */
export function diffLog(before: Profile[], after: Profile[], log: ProfileLog, now: number): ProfileLog {
  let out = log;
  const afterIds = new Set(after.map((p) => p.id));
  const beforeById = new Map(before.map((p) => [p.id, p]));
  for (const b of before) if (!afterIds.has(b.id)) out = logSet(out, wireName(b), now, false);
  for (const a of after) {
    const b = beforeById.get(a.id);
    const k = profileKey(wireName(a));
    if (!b) { out = logSet(out, wireName(a), now, true); continue; }
    if (k !== profileKey(wireName(b))) {
      out = logSet(out, wireName(b), now, false);
      out = logSet(out, wireName(a), now, true, wireName(b));
    } else if (a.name !== b.name || a.color !== b.color || !!a.kids !== !!b.kids || a.lang !== b.lang) {
      // Un renommage pas encore échangé reste annoncé (ancien nom gardé).
      out = logSet(out, wireName(a), now, true, out[k]?.on ? out[k]?.from : undefined);
    }
  }
  return out;
}

// ---------- Envoi et plan ----------

/** Ce que cet appareil envoie : ses profils (date du journal, sinon de création) et ses suppressions. */
export function localEntries(profiles: Profile[], log: ProfileLog): SharedProfile[] {
  const names = new Set(profiles.map((p) => profileKey(wireName(p))));
  const live = profiles.map((p): SharedProfile => {
    const l = log[profileKey(wireName(p))];
    const e = l?.on ? l : undefined;
    return { n: wireName(p), c: hexToArgb(p.color), k: !!p.kids, at: e?.at ?? createdAt(p), on: true, ...(e?.from ? { f: e.from } : {}), ...(p.lang ? { l: p.lang } : {}) };
  });
  const tombs = Object.values(log).filter((e) => !e.on && !names.has(profileKey(e.n))).map((e): SharedProfile => ({ n: e.n, c: 0, k: false, at: e.at, on: false }));
  return [...live, ...tombs];
}

/** Ce qu'il faut appliquer ici d'après la liste fusionnée reçue : le plus récent gagne, par nom. */
export function plan(profiles: Profile[], log: ProfileLog, remote: SharedProfile[]): ProfileAction[] {
  const out: ProfileAction[] = [];
  const byKey = (n: string) => profiles.find((p) => profileKey(wireName(p)) === profileKey(n));
  // Renommages d'abord : « Papa » devenu « Papa 2 » ailleurs → on renomme le profil local (données gardées)
  // au lieu de le supprimer (tombe « Papa ») et d'en créer un vide.
  const renamed = new Set<string>();
  for (const r of remote) {
    if (!r.f || !r.on || byKey(r.n)) continue;
    const l = byKey(r.f);
    if (!l) continue;
    if (r.at <= (log[profileKey(r.f)]?.at ?? createdAt(l))) continue;
    out.push({ type: "rename", id: l.id, p: r });
    renamed.add(profileKey(r.n)); renamed.add(profileKey(r.f));
  }
  for (const r of remote) {
    const k = profileKey(r.n);
    if (renamed.has(k)) continue;
    const l = byKey(r.n);
    const localAt = log[k]?.at ?? (l ? createdAt(l) : 0);
    if (r.on) {
      if (!l) { if (r.at > localAt || !log[k]) out.push({ type: "create", p: r }); }
      else if (r.at > localAt) {
        const lw = langFromWire(r.l);
        if (!sameColor(l.color, r.c) || !!l.kids !== r.k || (lw && lw.lang !== l.lang)) out.push({ type: "update", id: l.id, p: r });
      }
    } else if (l && r.at > localAt) out.push({ type: "delete", id: l.id, p: r });
  }
  return out;
}

export interface Applied { profiles: Profile[]; profileId: string; log: ProfileLog; deletedIds: string[] }

/** Applique le plan à la liste locale ; le journal reprend les dates reçues (pas « maintenant »). Jamais le dernier profil. */
export function applyActions(profiles: Profile[], profileId: string, log: ProfileLog, actions: ProfileAction[], now: number): Applied {
  let list = [...profiles]; let lg = log; const deletedIds: string[] = [];
  const merge = (p: Profile, r: SharedProfile, name: string): Profile => {
    const lw = langFromWire(r.l);
    const next: Profile = { ...p, name, color: argbToHex(r.c) ?? p.color };
    if (r.k) next.kids = true; else delete next.kids;
    if (lw) { if (lw.lang) next.lang = lw.lang; else delete next.lang; }
    return next;
  };
  let seq = 0;
  for (const a of actions) {
    if (a.type === "create") {
      let id = `p${now + seq++}`;
      while (list.some((p) => p.id === id)) id = `p${now + seq++}`;
      list.push(merge({ id, name: a.p.n, color: "#7C3AED" }, a.p, a.p.n));
      lg = logSet(lg, a.p.n, a.p.at, true);
    } else if (a.type === "update") {
      list = list.map((p) => (p.id === a.id ? merge(p, a.p, p.name) : p));
      lg = logSet(lg, a.p.n, a.p.at, true);
    } else if (a.type === "rename") {
      const old = list.find((p) => p.id === a.id);
      if (!old) continue;
      list = list.map((p) => (p.id === a.id ? merge(p, a.p, a.p.n) : p));
      lg = logSet(lg, wireName(old), a.p.at, false);
      lg = logSet(lg, a.p.n, a.p.at, true, a.p.f);
    } else if (list.length > 1) {
      list = list.filter((p) => p.id !== a.id);
      deletedIds.push(a.id);
      lg = logSet(lg, a.p.n, a.p.at, false);
    }
  }
  const id = list.some((p) => p.id === profileId) ? profileId : list[0]!.id;
  return { profiles: list, profileId: id, log: lg, deletedIds };
}

/** Entrées reçues du Worker, nettoyées. */
export function parseRemote(raw: unknown): SharedProfile[] {
  if (!Array.isArray(raw)) return [];
  const out: SharedProfile[] = [];
  for (const e of raw as Record<string, unknown>[]) {
    if (!e || typeof e !== "object") continue;
    const n = typeof e.n === "string" ? e.n.trim().slice(0, NAME_MAX).trim() : "";
    const at = Number(e.at);
    if (!n || !Number.isFinite(at) || at <= 0) continue;
    const f = typeof e.f === "string" ? e.f.trim().slice(0, NAME_MAX).trim() : "";
    out.push({ n, c: Number.isInteger(e.c) ? (e.c as number) : 0, k: e.k === true, at, on: e.on !== false, ...(f ? { f } : {}), ...(typeof e.l === "string" && e.l ? { l: e.l } : {}) });
  }
  return out;
}
