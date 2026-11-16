// Export des chaînes favorites en M3U standard (#EXTM3U, #EXTINF avec tvg-id, tvg-logo, group-title, puis l'adresse du flux).
// Même format que l'appli Android. ATTENTION : les adresses Xtream contiennent l'identifiant et le mot de passe de
// l'abonnement (l'interface prévient avant le téléchargement). Aucune propriété de lecture (clés, en-têtes) n'est exportée.

export interface M3uEntry {
  name: string;
  url: string;
  /** Identifiant XMLTV (tvg-id) ; vide = attribut omis. */
  tvgId?: string | null;
  logo?: string | null;
  /** Nom de la catégorie (group-title). */
  group?: string | null;
  /** Numéro de chaîne (tvg-chno) ; 0 ou moins = inconnu. */
  num?: number;
}

const SCHEMES = new Set(["http", "https", "rtmp", "rtsp", "udp", "rtp", "mms"]);

/** Seules les adresses lisibles hors de l'appli sont exportées (pas `stalker:…`, dont le lien est à usage unique). */
export function isExportable(url: string | null | undefined): url is string {
  const m = /^([a-z][a-z0-9+.-]*):\/\//i.exec((url ?? "").trim());
  return !!m && SCHEMES.has(m[1]!.toLowerCase());
}

/** Valeur d'attribut : ni guillemet ni saut de ligne (le format n'a pas d'échappement). */
const attr = (v: string) => v.replace(/"/g, "'").replace(/[\r\n\t]+/g, " ").trim();
const line = (v: string) => v.replace(/[\r\n\t]+/g, " ").trim();

/** Nombre de chaînes réellement écrites par buildM3u. */
export const countExportable = (entries: M3uEntry[]) => entries.filter((e) => isExportable(e.url)).length;

/** Contenu du fichier : l'ordre est conservé, les adresses non exportables sont ignorées. */
export function buildM3u(entries: M3uEntry[]): string {
  const out = ["#EXTM3U"];
  for (const e of entries) {
    if (!isExportable(e.url)) continue;
    const name = line(e.name) || "-";
    let extinf = "#EXTINF:-1";
    const id = attr(e.tvgId ?? "");
    if (id) extinf += ` tvg-id="${id}"`;
    extinf += ` tvg-name="${attr(name)}"`;
    const logo = attr(e.logo ?? "");
    if (logo) extinf += ` tvg-logo="${logo}"`;
    if ((e.num ?? 0) > 0) extinf += ` tvg-chno="${e.num}"`;
    const group = attr(e.group ?? "");
    if (group) extinf += ` group-title="${group}"`;
    out.push(`${extinf},${name}`, line(e.url));
  }
  return out.join("\n") + "\n";
}

/** Nom de fichier proposé : ultra-tv-favorites-AAAAMMJJ.m3u */
export function m3uFileName(d: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `ultra-tv-favorites-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}.m3u`;
}
