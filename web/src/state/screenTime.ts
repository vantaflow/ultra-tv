// Temps d'écran du profil courant : compte la lecture EFFECTIVE (le lecteur signale `setPlaying`) par profil et par jour local,
// sur CET appareil seulement (jamais synchronisé : seules les règles le sont, avec le profil), et publie la décision `verdict`.
// Un pas par seconde pendant la lecture, un toutes les 15 s sinon (une plage horaire peut se fermer menus ouverts).

import { create } from "zustand";
import { advance, decode, extend, forToday, zoneFor, type Grant, type Usage, type Verdict } from "@/lib/screenTime";
import { usePrefs } from "./prefs";

const KEY = "ultratv.screenTime.v1";
interface Persisted { usage: Record<string, Usage>; grants: Record<string, Grant> }

function load(): Persisted {
  try { const o = JSON.parse(localStorage.getItem(KEY) ?? "null") as Persisted | null; if (o && typeof o === "object") return { usage: o.usage ?? {}, grants: o.grants ?? {} }; } catch { /* repart de zéro */ }
  return { usage: {}, grants: {} };
}
let data: Persisted | null = null;
const store = () => (data ??= load());
const save = () => { try { localStorage.setItem(KEY, JSON.stringify(store())); } catch { /* stockage indisponible : compteur gardé en mémoire */ } };

interface St {
  verdict: Verdict;
  playing: boolean;
  /** Appelé par le lecteur : vrai tant qu'une lecture est réellement en cours. */
  setPlaying: (on: boolean) => void;
  /** Dérogation parentale (le code a été vérifié par l'appelant) : « +30 min » ou « pour aujourd'hui » (jusqu'à minuit). */
  grantExtra: () => void;
  grantToday: () => void;
}

export const useScreenTime = create<St>((set) => ({
  verdict: { kind: "free" },
  playing: false,
  setPlaying: (playing) => set((s) => (s.playing === playing ? s : { playing })),
  grantExtra: () => { const id = usePrefs.getState().profileId; store().grants[id] = extend(store().grants[id], Date.now()); save(); step(true); },
  grantToday: () => { const id = usePrefs.getState().profileId; store().grants[id] = forToday(Date.now(), zoneFor()); save(); step(true); },
}));

let last = 0;
let lastProfile = "";
let lastSaved = 0;
let dirty = false;
let idleTicks = 0;

/** Un pas du compteur (appelé chaque seconde) ; `force` recalcule la décision sans créditer de temps. */
function step(force = false): void {
  const now = Date.now();
  const { profiles, profileId } = usePrefs.getState();
  const rules = decode(profiles.find((p) => p.id === profileId)?.screenTime);
  const playing = useScreenTime.getState().playing;
  // Hors lecture, la décision n'est recalculée que toutes les 15 s (sauf changement de profil ou dérogation).
  if (!force && !playing && profileId === lastProfile && ++idleTicks < 15) return;
  idleTicks = 0;
  if (profileId !== lastProfile || last === 0) { lastProfile = profileId; last = now; }
  const d = store();
  const r = advance(rules, d.usage[profileId] ?? {}, d.grants[profileId], playing && !force, last, now, zoneFor());
  last = now;
  if (r.counted) { d.usage[profileId] = r.usage; dirty = true; }
  useScreenTime.setState((s) => (JSON.stringify(s.verdict) === JSON.stringify(r.verdict) ? s : { verdict: r.verdict }));
  if (dirty && now - lastSaved >= 15_000) { save(); lastSaved = now; dirty = false; }
}

let timer: ReturnType<typeof setInterval> | null = null;
/** Démarre le compteur (une seule fois) ; renvoie l'arrêt. */
export function startScreenTime(): () => void {
  if (timer) return () => undefined;
  step(true);
  timer = setInterval(() => step(), 1000);
  const flush = () => { if (dirty) { save(); dirty = false; } };
  window.addEventListener("beforeunload", flush);
  document.addEventListener("visibilitychange", flush);
  const unsub = usePrefs.subscribe((s, p) => { if (s.profileId !== p.profileId || s.profiles !== p.profiles) step(true); });
  return () => { if (timer) clearInterval(timer); timer = null; flush(); window.removeEventListener("beforeunload", flush); document.removeEventListener("visibilitychange", flush); unsub(); };
}
