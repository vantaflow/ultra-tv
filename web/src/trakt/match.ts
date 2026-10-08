// Clé de rapprochement des titres Trakt ↔ playlist. RÈGLE PARTAGÉE avec le Worker (cloudflare-config/test/traktlib.test.js)
// et Android (TraktMatchTest) : mêmes vecteurs dans cloudflare-config/test/fixtures/trakt-match-vectors.json.
// Ne pas modifier ici seul : changer la règle partout, vecteurs d'abord.

const QUALITY = /(?<![\p{L}\p{N}])(4k|uhd|fhd|hd|sd|hevc|h26[45]|x26[45]|multi|vostfr|vost|vff|vf|vo|truefrench|subfrench|french)(?![\p{L}\p{N}])/giu;

export function matchKey(raw: unknown): string {
  let s = String(raw ?? "");
  s = s.replace(/^\s*(\|[^|]{1,12}\||\[[^\]]{1,12}\]|[A-Z0-9]{2,4}\s*[-:|]\s*)/u, "");
  s = s.normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase();
  s = s.replace(QUALITY, " ");
  s = s.replace(/\(\s*(19|20)\d{2}\s*\)/g, " ");
  s = s.replace(/&/g, " and ");
  s = s.replace(/[^\p{L}\p{N}]+/gu, " ").replace(/\s+/g, " ").trim();
  const noYear = s.replace(/\s(19|20)\d{2}$/, ""); if (noYear) s = noYear;
  s = s.replace(/^(the|le|la|les|l|el|los|las|a|an)\s+(?=\S)/, "");
  return s;
}

/** Années compatibles : tolérance ±1 quand les deux sont connues. */
export const yearsClose = (a: number | null | undefined, b: number | null | undefined) => !(a && b && Math.abs(a - b) > 1);

export function titleMatches(catalogTitle: string, catalogYear: number | null | undefined, titles: string[], year: number | null | undefined): boolean {
  const k = matchKey(catalogTitle);
  if (!k) return false;
  if (!yearsClose(catalogYear, year)) return false;
  return titles.some((t) => matchKey(t) === k);
}
