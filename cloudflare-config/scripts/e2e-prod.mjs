// Parcours complet sur la production avec un compte jetable (créé puis supprimé) :
//   node scripts/e2e-prod.mjs [https://ultratv.dpdns.org]
// Lecture, écriture, mise à jour et suppression sur chaque famille de routes. Aucune donnée réelle n'est touchée.

const BASE = process.argv[2] || "https://ultratv.dpdns.org";
const login = `e2e-${Math.random().toString(36).slice(2, 10)}`;
const pw = `E2e-${crypto.randomUUID()}`;
const rows = [];
let failed = 0;

function check(name, cond, info = "") {
  rows.push({ étape: name, ok: cond ? "oui" : "NON", info: String(info).slice(0, 80) });
  if (!cond) failed++;
}

async function call(path, { method = "GET", form, json, cookie, headers = {} } = {}) {
  const h = new Headers({ "user-agent": "Mozilla/5.0 (UltraTV e2e)", ...headers });
  if (cookie) h.set("cookie", cookie);
  if (method !== "GET") h.set("origin", BASE);
  let body;
  if (form) { h.set("content-type", "application/x-www-form-urlencoded"); body = new URLSearchParams(form).toString(); }
  else if (json !== undefined) { h.set("content-type", "application/json"); body = JSON.stringify(json); }
  return fetch(BASE + path, { method, headers: h, body, redirect: "manual" });
}
const cookieOf = (res) => { const m = (res.headers.get("set-cookie") || "").match(/(__Host-utv_sess)=([^;]+)/); return m ? `${m[1]}=${m[2]}` : null; };
async function csrfOf(cookie) { const html = await (await call("/", { cookie })).text(); return html.match(/name="csrf" value="([^"]+)"/)?.[1]; }
const bearer = (t) => ({ authorization: `Bearer ${t}`, "x-ultra-version": "1.3.37", "x-ultra-edition": "standard" });

async function pair(cookie, csrf, name) {
  const start = await (await call("/api/pair/start", { method: "POST", json: { label: "e2e:00:00:00:00:01" } })).json();
  const conf = await call("/pair", { method: "POST", cookie, form: { csrf, code: start.code, name } });
  const poll = await (await call("/api/pair/poll", { method: "POST", json: { code: start.code, pollSecret: start.pollSecret } })).json();
  return { start, conf: conf.status, token: poll.token, deviceId: poll.deviceId };
}

try {
  // ---- compte ----
  const su = await call("/signup", { method: "POST", form: { login, password: pw, confirm: pw } });
  let cookie = cookieOf(su);
  check("inscription (écriture compte)", su.status === 302 && cookie, su.status);
  let csrf = await csrfOf(cookie);
  check("tableau de bord (lecture compte)", !!csrf);
  await call("/logout", { method: "POST", cookie, form: { csrf } });
  const li = await call("/login", { method: "POST", form: { login, password: pw } });
  cookie = cookieOf(li); csrf = await csrfOf(cookie);
  check("déconnexion + connexion", li.status === 302 && !!csrf, li.status);

  // ---- sources ----
  const ap = await call("/providers", { method: "POST", cookie, form: { csrf, kind: "M3U", name: "E2E Liste", url: "http://e2e.invalid/list.m3u" } });
  check("ajout d'une source (écriture)", ap.status === 302, ap.status);

  // ---- appareils ----
  const tv = await pair(cookie, csrf, "E2E TV");
  check("appairage (verrou + jeton)", tv.conf === 302 && !!tv.token, `confirm ${tv.conf}`);
  const tab = await pair(cookie, csrf, "E2E Tablette");
  check("second appareil", !!tab.token);

  const cfg = await call("/api/config", { headers: bearer(tv.token) });
  const cj = await cfg.json();
  const prov = cj.providers?.[0];
  check("configuration de l'appareil (lecture)", cfg.status === 200 && prov?.name === "E2E Liste" && cj.devices?.length === 2, `${cfg.status} ${cj.devices?.length} appareils`);
  const etag = cfg.headers.get("etag");
  console.log("etag reçu :", etag, "| encodage :", cfg.headers.get("content-encoding"));
  const c304 = await call("/api/config", { headers: { ...bearer(tv.token), "if-none-match": etag } });
  check("configuration inchangée (304)", c304.status === 304, c304.status);

  // ---- réglages, profils, état ----
  const now = Date.now();
  const pr = await call(`/api/device/providers/${prov.id}/prefs`, { method: "PUT", headers: bearer(tv.token), json: { langs: ["fr"], disabled: { live: ["1"], movie: [], series: [] }, order: { live: ["2", "1"] }, updatedAt: now } });
  check("réglages partagés (écriture)", pr.status === 200, pr.status);
  const cfg2 = await (await call("/api/config", { headers: bearer(tab.token) })).json();
  const p2 = cfg2.providers.find((p) => p.id === prov.id);
  check("réglages relus sur l'autre appareil", JSON.stringify(p2?.prefs?.disabled?.live) === '["1"]', JSON.stringify(p2?.prefs?.disabled));

  const prof = await (await call("/api/device/profiles", { method: "POST", headers: bearer(tv.token), json: { profiles: [{ n: "E2E", c: 3, k: false, at: now, l: "fr" }] } })).json();
  check("profils (écriture)", prof.profiles?.some((p) => p.n === "E2E"), JSON.stringify(prof).slice(0, 60));
  const prof2 = await (await call("/api/device/profiles", { method: "POST", headers: bearer(tab.token), json: { profiles: [] } })).json();
  check("profils relus sur l'autre appareil", prof2.profiles?.some((p) => p.n === "E2E" && p.l === "fr"));
  const ren = await (await call("/api/device/profiles", { method: "POST", headers: bearer(tab.token), json: { profiles: [{ n: "E2E", c: 5, k: false, at: now + 1000, l: "fr" }] } })).json();
  check("profil modifié (mise à jour)", ren.profiles?.find((p) => p.n === "E2E")?.c === 5);
  const del = await (await call("/api/device/profiles", { method: "POST", headers: bearer(tv.token), json: { profiles: [{ n: "E2E", c: 5, at: now + 2000, on: false }] } })).json();
  check("profil supprimé (suppression)", del.profiles?.find((p) => p.n === "E2E")?.on === false);

  const st = await call(`/api/device/providers/${prov.id}/state`, { method: "POST", headers: bearer(tv.token), json: { fav: [{ p: "E2E Liste", k: "LIVE", r: "42", at: now }], hist: [{ p: "E2E Liste", k: "MOVIE", r: "7", at: now, pos: 60000, dur: 7200000 }] } });
  const stj = await st.json();
  check("favori + reprise (écriture)", st.status === 200 && stj.fav?.some((f) => f.r === "42"), st.status);
  const st2 = await (await call(`/api/device/providers/${prov.id}/state`, { method: "POST", headers: bearer(tab.token), json: { fav: [], hist: [] } })).json();
  check("favori + reprise relus sur l'autre appareil", st2.fav?.some((f) => f.r === "42") && st2.hist?.some((h) => h.r === "7" && h.pos === 60000));
  const st3 = await (await call(`/api/device/providers/${prov.id}/state`, { method: "POST", headers: bearer(tab.token), json: { fav: [{ p: "E2E Liste", k: "LIVE", r: "42", at: now + 1000, on: false }], hist: [] } })).json();
  check("favori retiré (suppression)", st3.fav?.find((f) => f.r === "42")?.on === false);

  const rn = await call("/api/device/self", { method: "POST", headers: bearer(tab.token), json: { name: "E2E Tablette 2" } });
  check("renommer l'appareil (mise à jour)", rn.status === 200 || rn.status === 204, rn.status);

  // ---- télémétrie et lectures publiques ----
  const ev = await call("/api/event", { method: "POST", headers: bearer(tv.token), json: { level: "error", tag: "e2e", message: `e2e ${login}` } });
  check("journal d'erreur (télémétrie)", ev.status === 200 || ev.status === 204, ev.status);
  const an = await call("/api/announcements", { headers: bearer(tv.token) });
  check("annonces (lecture)", an.status === 200, an.status);
  const tm = await call("/api/tmdb/movie/550", { headers: bearer(tv.token) });
  check("fiche TMDB (cache)", tm.status === 200 || tm.status === 503, tm.status);
  const pg = await call("/apk");
  check("page publique /apk", pg.status === 200, pg.status);

  // ---- suppressions ----
  const dp = await call(`/api/device/providers/${prov.id}`, { method: "DELETE", headers: bearer(tv.token) });
  check("suppression de la source (appareil)", dp.status === 200 || dp.status === 204, dp.status);
  const rv = await call(`/devices/${tab.deviceId}/revoke`, { method: "POST", cookie, form: { csrf } });
  const after = await call("/api/config", { headers: bearer(tab.token) });
  check("révocation d'un appareil", rv.status === 302 && after.status === 401, `${rv.status} puis ${after.status}`);

  const da = await call("/account/delete", { method: "POST", cookie, form: { csrf, password: pw } });
  const gone = await call("/api/config", { headers: bearer(tv.token) });
  const relog = await call("/login", { method: "POST", form: { login, password: pw } });
  check("suppression du compte", da.status === 302 && gone.status === 401 && !cookieOf(relog), `${da.status} ${gone.status}`);
} catch (e) {
  check("exception", false, e?.message);
}

console.table(rows);
console.log(failed ? `ÉCHECS : ${failed}` : `TOUT EST VERT (${rows.length} étapes) — compte jetable ${login} supprimé`);
process.exit(failed ? 1 : 0);
