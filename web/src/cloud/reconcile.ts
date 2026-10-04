// Fusion de la configuration du compte cloud avec les sources locales : ajout, modification, retrait.
// Clé de fusion : l'identifiant stable du fournisseur côté Worker (`cloudId`).

import type { Source } from "@/db/types";
import type { CloudProvider } from "./client";
import { parseXtreamUrl } from "@/lib/xtreamUrl";

export interface Plan {
  add: Source[];
  update: { id: number; patch: Partial<Source>; resync: boolean }[];
  /** Sources issues du cloud et retirées du compte : à supprimer. */
  remove: number[];
  /** Sources locales qui étaient partagées et ne le sont plus : on les garde, sans lien cloud. */
  detach: number[];
  /** Fournisseurs ignorés (type non pris en charge, ex. Stalker). */
  skipped: number;
}

type Fields = Pick<Source, "name" | "type" | "server" | "username" | "password" | "m3uUrl">;

/** Convertit un fournisseur cloud en champs de source ; null si le type n'est pas pris en charge. */
export function toSourceFields(p: CloudProvider): Fields | null {
  const kind = String(p.kind || "").toUpperCase();
  const name = (p.name || "").trim() || p.url;
  if (kind === "XTREAM") return { name, type: "xtream", server: p.url, username: p.username ?? "", password: p.password ?? "", m3uUrl: "" };
  if (kind === "M3U") {
    // Adresse get.php / player_api.php : suit la même conversion Xtream que les sources locales.
    const x = parseXtreamUrl(p.url);
    if (x) return { name, type: "xtream", server: x.server, username: x.username, password: x.password, m3uUrl: "" };
    return { name, type: "m3u", server: "", username: "", password: "", m3uUrl: p.url };
  }
  return null;
}

const norm = (u: string) => u.trim().replace(/\/+$/, "").toLowerCase();

/** Une source locale identique (même serveur et identifiant, ou même lien) : adoptée au lieu d'être dupliquée. */
function sameProvider(s: Source, f: Fields): boolean {
  if (s.type !== f.type) return false;
  return s.type === "xtream" ? norm(s.server) === norm(f.server) && s.username === f.username : norm(s.m3uUrl) === norm(f.m3uUrl);
}

export function reconcile(local: Source[], remote: CloudProvider[], emptySource: () => Source): Plan {
  const plan: Plan = { add: [], update: [], remove: [], detach: [], skipped: 0 };
  const byCloud = new Map(local.filter((s) => s.cloudId).map((s) => [s.cloudId!, s]));
  const seen = new Set<string>();

  for (const p of remote) {
    const f = toSourceFields(p);
    if (!f) { plan.skipped++; continue; }
    seen.add(p.id);
    const shared = p.sharedWith;
    const sharedCount = shared === "all" ? "all" : Array.isArray(shared) ? shared.length : undefined;
    const cur = byCloud.get(p.id);
    if (!cur) {
      const twin = local.find((s) => !s.cloudId && sameProvider(s, f));
      if (twin) {
        plan.update.push({ id: twin.id!, patch: { cloudId: p.id, cloudOrigin: "local", cloudShared: sharedCount }, resync: false });
        continue;
      }
      plan.add.push({ ...emptySource(), ...f, cloudId: p.id, cloudOrigin: "cloud", cloudShared: sharedCount, cloudOriginName: p.originName || undefined });
      continue;
    }
    // Source locale partagée : le local fait foi (on ne réécrit pas ses identifiants), seule l'étiquette de partage suit le cloud.
    if (cur.cloudOrigin === "local") {
      if (cur.cloudShared !== sharedCount) plan.update.push({ id: cur.id!, patch: { cloudShared: sharedCount }, resync: false });
      continue;
    }
    const patch: Partial<Source> = {};
    let resync = false;
    if (cur.name !== f.name) patch.name = f.name;
    for (const k of ["server", "username", "password", "m3uUrl"] as const) {
      if (cur[k] !== f[k]) { patch[k] = f[k]; resync = true; }
    }
    if (cur.cloudShared !== sharedCount) patch.cloudShared = sharedCount;
    if (cur.cloudOriginName !== (p.originName || undefined)) patch.cloudOriginName = p.originName || undefined;
    if (Object.keys(patch).length) plan.update.push({ id: cur.id!, patch, resync });
  }

  for (const s of local) {
    if (!s.cloudId || seen.has(s.cloudId)) continue;
    if (s.cloudOrigin === "cloud") plan.remove.push(s.id!);
    else plan.detach.push(s.id!);
  }
  return plan;
}
