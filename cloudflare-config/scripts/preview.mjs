// Aperçu local du site : rend les pages avec de fausses données et écrit des fichiers HTML dans .preview/
// (ignoré par git) que l'on ouvre dans un navigateur pour les capturer. Aucune E/S réseau, aucun secret.
//
//   cd cloudflare-config && node scripts/preview.mjs
//   puis ouvrir .preview/index.html (liens vers chaque page ; thème : bouton de l'en-tête)

import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { loginPage, signupPage, pairPage, dashboardPage, envoyerPage, apkPage } from "../src/pages.js";
import { overviewPage, accountsPage, anonPage, devicesPage, eventsAdminPage, alertsPage } from "../src/adminui.js";
import { paginate, parseListParams } from "../src/admin.js";
import { fontResponse } from "../src/fonts.js";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", ".preview");
mkdirSync(join(root, "assets"), { recursive: true });
for (const f of ["sora.woff2", "manrope.woff2"]) writeFileSync(join(root, "assets", f), new Uint8Array(await fontResponse(f).arrayBuffer()));

const N = "preview-nonce";
const now = Date.now();
const H = 3_600_000, D = 24 * H;
const csrf = "csrf-demo";
const csrfInput = `<input type="hidden" name="csrf" value="${csrf}"/>`;

const out = [];
async function save(name, title, res) {
  // Les polices sont servies par /assets sur le site ; en local, on pointe vers le dossier copié ci-dessus.
  const html = (await res.text()).replaceAll("url(/assets/", "url(assets/");
  writeFileSync(join(root, name + ".html"), html);
  out.push([name, title]);
}

const acct = {
  login: "khalil",
  devices: [
    { id: "d1", name: "Salon", label: "UTV-A1B2C3", version: "1.2.90", edition: "pro", createdAt: now - 12 * D,
      license: { status: "active", until: now + 9 * D, issuedAt: now - H, devices: { used: 2, max: 3 }, reseller: { name: "Atlas IPTV", whatsapp: "+212600000000", telegram: "@atlas_iptv" } } },
    { id: "d2", name: "Chambre", label: "UTV-D4E5F6", version: "1.2.88", edition: "standard", createdAt: now - 40 * D },
    { id: "d3", name: "Pixel 8", version: "1.2.90", edition: "standard", createdAt: now - 2 * D },
  ],
  trakt: { user: "khalil_tv" },
  os: null,
};
const providers = [
  { id: "p1", kind: "XTREAM", name: "Fournisseur principal", url: "http://provider.example:8080", username: "demo", password: "demo", assign: "all", originName: "dashboard", createdAt: now - 30 * D, updatedAt: now - 5 * D },
  { id: "p2", kind: "M3U", name: "Playlist sport", url: "https://exemple.com/sport.m3u", assign: ["d1"], originName: "Salon", createdAt: now - 10 * D },
  { id: "p3", kind: "STALKER", name: "Portail", url: "http://portail.tv:8080/c/", mac: "00:1A:79:12:34:56", assign: [], createdAt: now - 3 * D },
  { id: "p4", kind: "XTREAM", name: "Revendeur", url: "http://reseller.example:80", username: "r", password: "r", managed: "reseller", assign: "all", createdAt: now - 60 * D },
];

await save("login", "Connexion", loginPage(N, null));
await save("login-erreur", "Connexion (erreur)", loginPage(N, "pw"));
await save("signup", "Inscription", signupPage(N, null));
await save("pair", "Appairage", pairPage(N, { acct, csrf, code: "ABCD1234", invalid: false }));
await save("dashboard", "Tableau de bord (Pro, rempli)", dashboardPage(N, { acct, providers, csrf, ok: "added", admin: true }));
await save("dashboard-vide", "Tableau de bord (nouveau compte)", dashboardPage(N, { acct: { login: "nouveau", devices: [] }, providers: [], csrf, err: "code" }));
for (const l of ["fr", "en", "ar"]) await save("envoyer-" + l, "Envoyer une source (" + l + ")", envoyerPage(N, { code: "K7Q2M9", lang: l }));
await save("apk", "Choix de l'APK", apkPage(N));

// ---- Administration ----
const country = ["MA", "FR", "DZ", "TN", "ES", "BE"];
const accounts = Array.from({ length: 30 }, (_, i) => ({
  login: i === 0 ? "khalil" : `utilisateur${i}`, createdAt: now - (i + 1) * 3 * D, lastLoginAt: now - i * 5 * H, lastSeen: now - i * 7 * H, country: country[i % 6],
  devices: [{ name: i % 2 ? "Salon" : "Box TV", model: "Fire TV 4K", country: country[i % 6], version: i % 3 ? "1.2.90" : "1.2.88", edition: "standard", lastSeen: now - i * 7 * H, createdAt: now - i * 3 * D }],
  sourcesN: i % 4, sourcesXtream: i % 4 ? 1 : 0, sourcesM3u: i % 4 > 1 ? i % 4 - 1 : 0, trakt: i % 5 ? "" : "trakt_user", opensubtitles: i % 7 ? "" : "os_user", legacy: false,
}));
const params = (qs, sorts, sort) => parseListParams(new URLSearchParams(qs), { sorts, sort, filters: ["country", "version", "platform", "level"] });
const facets = { countries: country, versions: ["1.2.90", "1.2.88", "1.2.80"] };
const anon = { total: 412, a24: 96, a7: 240, a30: 351, new1: 7, new7: 41, web: 18,
  byPlatform: [["android_tv", 210], ["android_mobile", 120], ["windows", 60], ["macos", 15], ["linux", 7]],
  byVersion: [["1.2.90", 260], ["1.2.88", 100], ["1.2.80", 52]], byCountry: [["MA", 180], ["FR", 120], ["DZ", 60], ["TN", 30]] };
await save("admin", "Admin : vue d'ensemble", overviewPage(N, { accounts, anon, crashes24: 3, asOf: now - 90_000, now, me: "khalil", csrfInput }));
{
  const p = params("", { login: 1, created: 1, logged: 1, seen: 1, country: 1, devices: 1, sources: 1 }, "seen");
  await save("admin-comptes", "Admin : comptes", accountsPage(N, { rows: accounts.slice(0, 15), pg: paginate(30, 1, 15), params: p, facets, asOf: now - 60_000, now, me: "khalil", csrfInput }));
}
{
  const rows = Array.from({ length: 10 }, (_, i) => ({ platform: ["android_tv", "windows", "macos"][i % 3], model: i % 3 ? "" : "Shield TV", version: "1.2.90", country: country[i % 6], first: now - i * D, last: now - i * H, web: i % 2 }));
  const p = params("", { platform: 1, model: 1, version: 1, country: 1, first: 1, last: 1, web: 1 }, "last");
  await save("admin-anon", "Admin : sans compte", anonPage(N, { rows, pg: paginate(10, 1), params: p, facets, platforms: ["android_tv", "windows", "macos"], now, me: "khalil", csrfInput }));
}
{
  const rows = Array.from({ length: 10 }, (_, i) => ({ name: "Salon", login: `utilisateur${i}`, model: "Fire TV", edition: i % 3 ? "standard" : "pro", version: "1.2.90", country: country[i % 6], lastSeen: now - i * H, createdAt: now - i * D }));
  const p = params("", { name: 1, login: 1, model: 1, edition: 1, version: 1, country: 1, seen: 1, created: 1 }, "seen");
  await save("admin-appareils", "Admin : appareils", devicesPage(N, { rows, pg: paginate(10, 1), params: p, facets, asOf: now, now, me: "khalil", csrfInput }));
}
{
  const rows = Array.from({ length: 8 }, (_, i) => ({ ts: now - i * H, level: ["error", "warn", "info"][i % 3], tag: "Sync", message: "Échec de synchronisation (démo)", device: "Fire TV", version: "1.2.90", versionCode: 1290, country: "MA", deviceId: "abc123", acct: "a1b2c3" }));
  const p = params("", { ts: 1, level: 1, tag: 1, device: 1, version: 1, country: 1 }, "ts");
  await save("admin-journaux", "Admin : journaux", eventsAdminPage(N, "event", { rows, pg: paginate(8, 1), params: p, facets, now, me: "khalil", csrfInput }));
}
await save("admin-alertes", "Admin : alertes", alertsPage(N, { announcements: [
  { id: "a1", level: "warning", title: "Maintenance", message: "Le service sera indisponible dimanche de 2 h à 4 h.", audience: "all", action: "none", startAt: now - H, endAt: now + 2 * D, by: "khalil" },
  { id: "a2", level: "urgent", title: "Mise à jour", message: "Une mise à jour importante est disponible.", audience: "tv", action: "update", below: "1.2.88", startAt: now - 3 * D, endAt: now - D, by: "khalil" },
], annStats: { a1: { shown: 12, dismissed: 3, action: 0 } }, ok: "ann_added", now, me: "khalil", csrfInput }));

writeFileSync(join(root, "index.html"), `<!doctype html><meta charset="utf-8"><title>Aperçu Ultra TV</title><body style="font:16px system-ui;max-width:640px;margin:32px auto;line-height:2"><h1>Aperçu des pages</h1><ul>${out.map(([n, t]) => `<li><a href="${n}.html">${t}</a></li>`).join("")}</ul>`);
console.log(`${out.length} pages écrites dans ${root}`);
