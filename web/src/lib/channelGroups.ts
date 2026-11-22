// Groupes de chaînes personnalisés (par profil et par source) : opérations pures, testées.
// Une chaîne est désignée par son identifiant de flux (stable d'une synchro à l'autre) ; un groupe garde l'ordre d'ajout.

export interface ChannelGroup { id: string; name: string; ids: number[] }

/** Portée d'un groupe dans les listes du Direct, le guide et le zapping (distincte d'un identifiant de catégorie du fournisseur). */
export const GROUP_PREFIX = "__grp:";
export const groupScope = (id: string) => `${GROUP_PREFIX}${id}`;
export const isGroupScope = (scope: string | null | undefined): scope is string => !!scope && scope.startsWith(GROUP_PREFIX);
export const groupIdOf = (scope: string) => scope.slice(GROUP_PREFIX.length);

export const MAX_GROUPS = 50;
export const MAX_GROUP_NAME = 40;
export const MAX_GROUP_SIZE = 2000;

export const cleanGroupName = (n: string) => n.trim().replace(/\s+/g, " ").slice(0, MAX_GROUP_NAME);

/** Nouveau groupe (nom vide ou liste pleine : liste inchangée). */
export function createGroup(list: ChannelGroup[], name: string, id: string, ids: number[] = []): ChannelGroup[] {
  const n = cleanGroupName(name);
  if (!n || list.length >= MAX_GROUPS || list.some((g) => g.id === id)) return list;
  return [...list, { id, name: n, ids: [...new Set(ids)].slice(0, MAX_GROUP_SIZE) }];
}

export function renameGroup(list: ChannelGroup[], id: string, name: string): ChannelGroup[] {
  const n = cleanGroupName(name);
  if (!n) return list;
  return list.map((g) => (g.id === id ? { ...g, name: n } : g));
}

export const deleteGroup = (list: ChannelGroup[], id: string): ChannelGroup[] => list.filter((g) => g.id !== id);

/** Ajoute des chaînes à la fin du groupe (déjà présentes : ignorées). */
export function addToGroup(list: ChannelGroup[], id: string, streamIds: number[]): ChannelGroup[] {
  return list.map((g) => {
    if (g.id !== id) return g;
    const have = new Set(g.ids);
    const add = streamIds.filter((s) => !have.has(s) && have.add(s));
    return add.length ? { ...g, ids: [...g.ids, ...add].slice(0, MAX_GROUP_SIZE) } : g;
  });
}

export function removeFromGroup(list: ChannelGroup[], id: string, streamIds: number[]): ChannelGroup[] {
  const drop = new Set(streamIds);
  return list.map((g) => (g.id === id && g.ids.some((s) => drop.has(s)) ? { ...g, ids: g.ids.filter((s) => !drop.has(s)) } : g));
}

/** Groupes lus du stockage : entrées invalides ignorées, jamais d'exception. */
export function parseGroups(v: unknown): ChannelGroup[] {
  if (!Array.isArray(v)) return [];
  const out: ChannelGroup[] = [];
  for (const g of v) {
    if (!g || typeof g !== "object") continue;
    const { id, name, ids } = g as Record<string, unknown>;
    const n = typeof name === "string" ? cleanGroupName(name) : "";
    if (typeof id !== "string" || !id || !n || out.some((x) => x.id === id)) continue;
    const nums = Array.isArray(ids) ? [...new Set(ids.filter((x): x is number => Number.isInteger(x)))] : [];
    out.push({ id, name: n, ids: nums.slice(0, MAX_GROUP_SIZE) });
    if (out.length >= MAX_GROUPS) break;
  }
  return out;
}
