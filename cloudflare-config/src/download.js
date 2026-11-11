// Liens de téléchargement stables (README, site, posts) : /download/android (+ -arm64, -32, -armv7, -universal), /windows, /mac, /linux.
// Android : l'APK universel de la dernière release (« Latest » = Android). Bureau : les releases « desktop-v* » ne sont
// pas « Latest » et leurs fichiers portent le numéro de version ; on lit la dernière release bureau via l'API GitHub
// (cache 1 h) et on redirige vers le fichier de la plateforme.

export const REPO = "vantaflow/ultra-tv";
const RELEASES = `https://github.com/${REPO}/releases`;
const LATEST = `${RELEASES}/latest/download`;
// /download/android = APK arm64-v8a (≈ 67 Mo, la plupart des box et téléphones récents) ; le universel (≈ 227 Mo) en dernier recours.
export const ANDROID_URL = `${LATEST}/UltraTV-arm64-v8a.apk`;
export const ANDROID_URLS = {
  android: ANDROID_URL,
  "android-arm64": ANDROID_URL,
  "android-32": `${LATEST}/UltraTV-armeabi-v7a.apk`, // 32 bits : Chromecast avec Google TV HD, vieilles box
  "android-armv7": `${LATEST}/UltraTV-armeabi-v7a.apk`, // alias de android-32
  "android-universal": `${LATEST}/UltraTV.apk`,
};
const PATTERNS = {
  windows: /-win-x64\.exe$/,
  mac: /-mac-universal\.dmg$/,
  linux: /\.AppImage$/,
};

/** Fichier de la plateforme dans la liste des releases GitHub (la plus récente « desktop-v* » publiée d'abord). */
export function pickDesktopAsset(releases, platform) {
  const re = PATTERNS[platform];
  if (!re || !Array.isArray(releases)) return null;
  for (const r of releases) {
    if (r?.draft || r?.prerelease || !String(r?.tag_name || "").startsWith("desktop-v")) continue;
    const a = (r.assets || []).find((x) => re.test(String(x?.name || "")));
    if (a?.browser_download_url) return a.browser_download_url;
  }
  return null;
}

export async function downloadRedirect(platform, fetchImpl = fetch) {
  if (Object.hasOwn(ANDROID_URLS, platform)) return Response.redirect(ANDROID_URLS[platform], 302);
  if (!PATTERNS[platform]) return null;
  let target = null;
  try {
    const r = await fetchImpl(`https://api.github.com/repos/${REPO}/releases?per_page=20`, {
      headers: { "user-agent": "ultratv-config", accept: "application/vnd.github+json" },
      cf: { cacheTtl: 3600, cacheEverything: true },
    });
    if (r.ok) target = pickDesktopAsset(await r.json(), platform);
  } catch { /* repli ci-dessous */ }
  // Repli : la page des releases bureau (jamais une erreur pour l'utilisateur).
  return Response.redirect(target || `${RELEASES}?q=desktop-v&expanded=true`, 302);
}
