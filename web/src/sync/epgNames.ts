// Rapprochement par NOM entre les chaînes d'un guide XMLTV et celles du catalogue (repli quand l'identifiant EPG manque
// ou diffère). Même règles que l'application Android (ExtraEpg.norm / ExtraEpg.Resolver) : les deux doivent évoluer ensemble.

const QUALITY = new Set(["hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "raw", "vip", "tv"]);
/** Codes pays des préfixes et suffixes des listes IPTV (liste courte : seuls les bords du nom sont touchés). */
const COUNTRIES = new Set(["pl", "fr", "uk", "gb", "us", "de", "es", "pt", "it", "nl", "tr", "ro", "ru", "gr"]);
/** Début du nom : « PL: », « PL | », « PL - », « [PL] », « (PL) ». */
const COUNTRY_PREFIX = /^\s*(?:[[(]\s*([a-z]{2})\s*[\])]|([a-z]{2})\s*[:|/-])\s*(?=\S)/;

/**
 * Nom comparable : minuscules, sans accents ni ponctuation, sans mention de qualité (HD, FHD, 4K…), sans préfixe de pays
 * (« PL: », « PL | ») ni suffixe de pays (« … PL »). Les espaces sont supprimés : « TVP 1 » = « TVP1 ». Un chiffre reste
 * significatif : « TVN » ≠ « TVN 7 ».
 */
export function normEpgName(name: string): string {
  let s = name.normalize("NFKD").replace(/\p{M}+/gu, "").toLowerCase();
  s = s.replace(COUNTRY_PREFIX, (m, a: string | undefined, b: string | undefined) => (COUNTRIES.has(a || b || "") ? "" : m));
  const tokens = s.split(/[^a-z0-9+]+/).filter((t) => t && !QUALITY.has(t));
  // Suffixe de pays : seulement s'il reste un nom devant (« TVP 1 PL » → « tvp1 », mais « PL » seul reste).
  while (tokens.length >= 2 && COUNTRIES.has(tokens[tokens.length - 1]!)) tokens.pop();
  return tokens.join("");
}

export interface NameTarget {
  id: number;
  name: string;
  /** Identifiant EPG de la chaîne dans le catalogue (peut ne figurer dans aucun guide). */
  epg: string | null;
  country: string | null;
}

export interface FeedChannel { id: string; names: string[] }

export interface NameMatch {
  /** Identifiant de chaîne du guide (en minuscules) → identifiants sous lesquels écrire ses programmes (identifiants du catalogue). */
  index: Map<string, string[]>;
  /** Chaînes du catalogue SANS identifiant EPG rattachées à une chaîne du guide : identifiant du guide à leur attribuer. */
  assign: Map<number, string>;
}

/**
 * Rattache les chaînes du guide aux chaînes [targets] du catalogue : même nom normalisé (noms affichés, ou identifiant du guide
 * privé de son suffixe pays), pays compatible. Les chaînes du catalogue dont l'identifiant EPG figure déjà dans le guide ne sont
 * pas concernées (le rapprochement par identifiant prévaut).
 */
export function matchByName(feeds: FeedChannel[], targets: NameTarget[]): NameMatch {
  const index = new Map<string, string[]>();
  const assign = new Map<number, string>();
  const feedIds = new Set(feeds.map((f) => f.id.toLowerCase()));
  const byName = new Map<string, NameTarget[]>();
  const add = (k: string, t: NameTarget) => {
    if (k.length < 2) return;
    const l = byName.get(k);
    if (!l) byName.set(k, [t]); else if (!l.includes(t)) l.push(t);
  };
  for (const t of targets) {
    if (t.epg && feedIds.has(t.epg.toLowerCase())) continue;
    add(normEpgName(t.name), t);
    // Identifiant de la playlist (« TVP1.pl ») différent de celui du guide (« TVP 1 ») : son radical sert aussi de nom.
    if (t.epg) add(normEpgName(t.epg.replace(/\.[^.]*$/, "")), t);
  }
  for (const f of feeds) {
    const dot = f.id.lastIndexOf(".");
    const cc = dot >= 0 && f.id.length - dot - 1 === 2 ? f.id.slice(dot + 1).toUpperCase() : null;
    const hits = new Set<NameTarget>();
    for (const n of [...f.names, dot >= 0 ? f.id.slice(0, dot) : f.id]) {
      for (const t of byName.get(normEpgName(n)) ?? []) {
        const c = t.country?.toUpperCase();
        if (!c || !cc || c === cc) hits.add(t);
      }
    }
    for (const t of hits) {
      if (!t.epg && assign.has(t.id)) continue;   // une chaîne sans identifiant n'en reçoit qu'un : celui de la première chaîne du guide
      const own = t.epg || f.id;
      const key = f.id.toLowerCase();
      const l = index.get(key) ?? [];
      if (!l.includes(own)) l.push(own);
      index.set(key, l);
      if (!t.epg) assign.set(t.id, f.id);
    }
  }
  return { index, assign };
}
