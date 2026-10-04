// Lecture du QR affiché par la TV : `https://<worker>/pair?code=ABCDEFGH`, ou simplement le code.
// Fonction PURE et autonome (aucune référence externe, aucune fonction imbriquée : le bundler
// y injecterait un helper `__name` absent du navigateur) : la page l'embarque via toString(),
// les tests la lancent côté Node. On ne navigue JAMAIS vers l'URL lue, on n'en extrait que le code.

/** Renvoie le code d'appairage (8 caractères, majuscules) ou null si le contenu n'est pas un QR Ultra TV. */
export function parsePairQr(text, origin) {
  var t = String(text == null ? "" : text).trim();
  if (!t || t.length > 2048) return null;
  var raw = t;
  if (!/^[A-Za-z0-9-]+$/.test(t)) {
    var u;
    try { u = new URL(t); } catch (err) { return null; }
    if (u.origin !== origin || u.pathname !== "/pair") return null;
    raw = String(u.searchParams.get("code") || "");
  }
  var c = raw.toUpperCase().replace(/[^A-Z0-9]/g, "");
  return /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/.test(c) ? c : null;
}
