// Rendu HTML de l'administration (une page par onglet). Serveur seulement : aucun script externe, pas d'attribut
// style= ni on*= (CSP à nonce). Les valeurs viennent de admin.js (liste blanche) ; tout texte dynamique est échappé.

import { escapeHtml as e } from "./http.js";
import { notice as noticeBox } from "./ui/components.js";
import { ago, fmtTime, sourcesLabel } from "./ui/format.js";
import { shell as pageShell } from "./ui/layout.js";
import { announcementsPanel, ANN_SCRIPT } from "./pages/alerts.js";
import { DAY_MS, PERIOD_LABELS, countBy, listUrl, mergeCounts, pct, topWithOthers, flattenDevices } from "./admin.js";
import { PLATFORM_LABELS } from "./telemetry.js";

export const TABS = [
  ["overview", "Vue d'ensemble", "/admin"],
  ["accounts", "Comptes", "/admin/accounts"],
  ["anon", "Sans compte", "/admin/anon"],
  ["devices", "Appareils", "/admin/devices"],
  ["events", "Journaux", "/events"],
  ["crashes", "Plantages", "/crashes"],
  ["alerts", "Alertes", "/admin/alerts"],
];

const platformName = (p) => PLATFORM_LABELS[p] || "Inconnue";
const absTime = (t) => (t ? `${new Date(t).toISOString().slice(0, 16).replace("T", " ")} UTC` : "");

/** « il y a 3 h » avec la date exacte en info-bulle ; « — » si la date est inconnue. */
export function dateCell(t, now = Date.now()) {
  return t ? `<time datetime="${e(new Date(t).toISOString())}" title="${e(absTime(t))}">${e(ago(t, now))}</time>` : "—";
}

/** Nom + code pays (« Maroc · MA »), ou « — ». Pas d'emoji de drapeau : Windows les affiche en lettres. */
export function flagCode(code) {
  if (!code || !/^[A-Z]{2}$/.test(code)) return "—";
  let name = code;
  try { name = new Intl.DisplayNames(["fr"], { type: "region" }).of(code) || code; } catch { /* ICU absent */ }
  return name === code ? code : `${name} · ${code}`;
}

/** Libellé d'un pays pour une barre : drapeau + code, ou « autres ». */
const countryBarLabel = (k) => (/^[A-Z]{2}$/.test(k) ? flagCode(k) : k === "autres" ? "Autres pays" : k === "inconnu" ? "Inconnu" : k);

/** Cadre commun : gabarit du site (contexte « administration »), onglets, compte courant. [nav] faux : accès par jeton d'exploitation (pas de session). */
function shell(n, { active, title, body, csrfInput = "", me = "", nav = true, script = "" }) {
  const sub = nav ? `<div class="tabs-bar"><nav class="tabs" aria-label="Administration">${TABS.map(([k, label, href]) => `<a href="${href}"${k === active ? ' aria-current="page"' : ""}>${e(label)}</a>`).join("")}</nav></div>` : "";
  const bars = `document.querySelectorAll('.bars i[data-w]').forEach(function(i){i.style.width=i.getAttribute('data-w')+'%';});`;
  return pageShell(n, { title: `Ultra TV — ${title}`, ctx: "admin", bare: !nav, acct: nav ? { login: me } : null, admin: true, csrfInput, sub, width: "wide", body, extra: `<script nonce="${n}">${bars}${script}</script>` });
}

/** En-tête de colonne triable : lien qui bascule le sens (aria-sort sur la colonne active). */
function th(base, params, key, label, cls = "") {
  const on = params.sort === key;
  const dir = on && params.dir === "asc" ? "desc" : "asc";
  const arrow = on ? (params.dir === "asc" ? " ▲" : " ▼") : "";
  return `<th${cls ? ` class="${cls}"` : ""}${on ? ` aria-sort="${params.dir === "asc" ? "ascending" : "descending"}"` : ""}><a href="${e(listUrl(base, params, { sort: key, dir }))}">${e(label)}${arrow}</a></th>`;
}

/** Formulaire de filtres (GET : aucun état, aucun CSRF). [selects] : [{ name, label, options: [[valeur, libellé]] }]. */
function filters(base, params, { selects = [], period = true, placeholder = "Rechercher" }) {
  const sel = (s) => {
    const cur = params.f[s.name] || "";
    const opts = [`<option value="">Tous</option>`, ...s.options.map(([v, l]) => `<option value="${e(v)}"${v === cur ? " selected" : ""}>${e(l)}</option>`)];
    if (cur && !s.options.some(([v]) => v === cur)) opts.push(`<option value="${e(cur)}" selected>${e(cur)}</option>`);
    return `<div class="fl"><label for="f-${s.name}">${e(s.label)}</label><select id="f-${s.name}" name="${s.name}">${opts.join("")}</select></div>`;
  };
  const per = period
    ? `<div class="fl"><label for="f-p">Période</label><select id="f-p" name="p"><option value="">Toutes</option>${Object.entries(PERIOD_LABELS).map(([k, l]) => `<option value="${k}"${params.p === k ? " selected" : ""}>${e(l)}</option>`).join("")}</select></div>`
    : "";
  const active = params.q || params.p || Object.values(params.f).some(Boolean);
  return `<form class="filters" method="get" action="${e(base)}" role="search">
<div class="fl grow"><label for="f-q">Recherche</label><input id="f-q" type="search" name="q" value="${e(params.q)}" maxlength="64" placeholder="${e(placeholder)}" autocomplete="off"/></div>
${selects.map(sel).join("")}${per}
<input type="hidden" name="sort" value="${e(params.sort)}"/><input type="hidden" name="dir" value="${e(params.dir)}"/>
<button type="submit">Filtrer</button>${active ? `<a class="reset" href="${e(base)}">Réinitialiser</a>` : ""}</form>`;
}

/** Pagination : précédent / suivant et quelques pages autour de la page courante. */
function pager(base, params, pg) {
  const link = (p, label, extra = "") => `<a href="${e(listUrl(base, params, { page: p }))}"${extra}>${label}</a>`;
  const nums = [];
  for (let p = Math.max(1, pg.page - 2); p <= Math.min(pg.pages, pg.page + 2); p++) nums.push(p === pg.page ? `<span class="cur" aria-current="page">${p}</span>` : link(p, String(p)));
  return `<div class="pgr"><span class="muted small">${pg.total ? `${pg.from}–${pg.to} sur ${pg.total}` : "Aucun résultat"}</span>
<div class="pages">${link(pg.page - 1, "‹", pg.page <= 1 ? ' aria-disabled="true" tabindex="-1"' : ' rel="prev"')}${nums.join("")}${link(pg.page + 1, "›", pg.page >= pg.pages ? ' aria-disabled="true" tabindex="-1"' : ' rel="next"')}</div></div>`;
}

const tile = (v, l, cls = "") => `<div class="tile${cls ? " " + cls : ""}"><b>${e(String(v))}</b><span>${e(l)}</span></div>`;

/** Barres horizontales (largeur posée par le CSSOM : la CSP refuse style=). Relatives au total donné, sinon au maximum. */
function bars(pairs, label = (k) => String(k), total = 0) {
  if (!pairs.length) return `<p class="muted small">Aucune donnée.</p>`;
  const max = total || Math.max(1, ...pairs.map((p) => p[1]));
  return `<div class="bars">${pairs.map(([k, v]) => `<div class="bar"><span class="bl">${e(label(k))}</span><span class="bt"><i data-w="${Math.round((100 * v) / max)}"></i></span><span class="bv">${v}${total ? ` <small>${pct(v, total)} %</small>` : ""}</span></div>`).join("")}</div>`;
}

const kv = (pairs) => `<dl class="kv">${pairs.map(([k, v]) => `<dt>${e(k)}</dt><dd>${e(String(v))}</dd>`).join("")}</dl>`;

/** Mention « données mises à jour il y a… » + lien d'actualisation (les comptes viennent d'un cache de 2 minutes). */
const cacheNote = (href, asOf, now) => `<p class="muted small">Comptes lus dans KV, mis en cache 2 minutes : ${asOf ? `données ${e(ago(asOf, now))}` : "données du jour"} · <a href="${e(href)}">Actualiser</a></p>`;

// ---- Vue d'ensemble -----------------------------------------------------------------------

/** [accounts] : lignes `adminAccounts` ; [anon] : `anonOverview` ; [crashes24] : nombre de plantages sur 24 h. */
export function overviewPage(n, { accounts, anon, crashes24, asOf = 0, now = Date.now(), me, csrfInput }) {
  const rows = accounts.filter((r) => !r.legacy);
  const devices = flattenDevices(accounts);
  const activeSince = (ms) => rows.filter((r) => r.lastSeen && now - r.lastSeen < ms).length;
  const newSince = (ms) => rows.filter((r) => r.createdAt && now - r.createdAt < ms).length;
  const srcN = rows.reduce((t, r) => t + (r.sourcesN || 0), 0);
  const withSrc = rows.filter((r) => r.sourcesN > 0).length;
  const anonPlat = anon.byPlatform.map(([k, v]) => [k || "inconnue", v]);
  const versions = topWithOthers(mergeCounts(countBy(devices.map((d) => d.version || "inconnue")), anon.byVersion.map(([k, v]) => [k || "inconnue", v])), 10);
  const countries = topWithOthers(mergeCounts(
    countBy(rows.flatMap((r) => [...new Set([r.country, ...r.devices.map((d) => d.country)].filter(Boolean))])),
    anon.byCountry.filter(([k]) => k).map(([k, v]) => [k, v]),
  ), 10);
  const body = `<div class="tiles">
${tile(rows.length, "comptes")}${tile(devices.length, "appareils appairés")}
${tile(anon.a24, "installations sans compte actives (24 h)")}${tile(anon.a7, "actives (7 jours)")}${tile(anon.a30, "actives (30 jours)")}
${tile(anon.new1, "nouvelles installations aujourd'hui")}${tile(anon.new7, "nouvelles installations (7 jours)")}
${tile(crashes24, "plantages (24 h)", crashes24 ? "bad" : "")}</div>
${cacheNote("/admin?refresh=1", asOf, now)}
<div class="agrid">
<section class="panel"><h2>Sans compte, par application</h2>
<p class="muted small">Plateforme déclarée par l'appli au relevé anonyme (« inconnue » : ancienne version, pas encore relevée). Total : ${anon.total}.</p>
${bars(anonPlat, (k) => (k === "inconnue" ? "Inconnue" : platformName(k)), anon.total)}
<p class="note"><a href="/admin/anon">Voir les installations ›</a></p></section>
<section class="panel"><h2>Versions de l'appli</h2><p class="muted small">Appareils appairés + installations sans compte.</p>
${bars(versions, (k) => (k === "inconnue" ? "inconnue" : k === "autres" ? "Autres versions" : `v${k}`))}</section>
<section class="panel"><h2>Pays</h2><p class="muted small">10 premiers ; comptes (compte et appareils) + installations sans compte.</p>
${bars(countries, countryBarLabel)}</section>
<section class="panel"><h2>Comptes</h2>
${kv([["Actifs ces 24 h", activeSince(DAY_MS)], ["Actifs ces 7 jours", activeSince(7 * DAY_MS)], ["Actifs ces 30 jours", activeSince(30 * DAY_MS)], ["Nouveaux ces 7 jours", newSince(7 * DAY_MS)], ["Nouveaux ces 30 jours", newSince(30 * DAY_MS)]])}
<h3 class="mt16">Sources</h3>
${kv([["Sources configurées", srcN], ["Comptes avec au moins une source", withSrc], ["Comptes reliés à Trakt", rows.filter((r) => r.trakt).length], ["Comptes OpenSubtitles reliés", rows.filter((r) => r.opensubtitles).length]])}
<p class="note"><a href="/admin/accounts">Voir les comptes ›</a></p></section>
<section class="panel"><h2>Sans compte</h2>
${kv([["Installations (total)", anon.total], ["Sources envoyées depuis le web", anon.web]])}</section>
</div>`;
  return shell(n, { active: "overview", title: "administration", body, me, csrfInput });
}

// ---- Comptes ------------------------------------------------------------------------------

export function accountsPage(n, { rows, pg, params, facets, asOf, now = Date.now(), me, csrfInput }) {
  const base = "/admin/accounts";
  const row = (r) => {
    const cs = [...new Set([r.country, ...r.devices.map((x) => x.country)].filter(Boolean))];
    const names = r.devices.slice(0, 3).map((x) => e(x.name || x.model || "Appareil")).join(", ");
    const more = r.devices.length > 3 ? ` <span class="muted">(+${r.devices.length - 3})</span>` : "";
    return `<tr><td><strong>${e(r.login)}</strong>${r.login === me ? ` <span class="kind pro">ADMIN</span>` : ""}</td>
<td>${dateCell(r.createdAt, now)}</td><td>${dateCell(r.lastLoginAt, now)}</td><td>${dateCell(r.lastSeen, now)}</td>
<td>${cs.length ? cs.map(flagCode).join("<br>") : "—"}</td><td>${r.devices.length ? `${r.devices.length} · ${names}${more}` : "—"}</td><td>${sourcesLabel(r)}</td><td>${e(r.trakt || "—")}</td><td>${e(r.opensubtitles || "—")}</td></tr>`;
  };
  const body = `<div class="section-h"><h2>Comptes <span class="count">${pg.total}</span></h2><a class="button" href="/admin/comptes.csv">Exporter en CSV</a></div>
<p class="muted small mb16">Aucun mot de passe, aucune source IPTV ni jeton n'apparaît ici. Le pays vient de Cloudflare ; l'adresse IP n'est jamais gardée.</p>
${filters(base, params, { selects: [{ name: "country", label: "Pays", options: facets.countries.map((c) => [c, flagCode(c)]) }, { name: "version", label: "Version", options: facets.versions.map((v) => [v, `v${v}`]) }], placeholder: "Identifiant, appareil, pays, version" })}
${cacheNote(listUrl(base, params, { refresh: 1, page: params.page }), asOf, now)}
<div class="tablewrap"><table class="atable"><thead><tr>${th(base, params, "login", "Compte")}${th(base, params, "created", "Créé")}${th(base, params, "logged", "Dernière connexion")}${th(base, params, "seen", "Dernière activité")}${th(base, params, "country", "Pays")}${th(base, params, "devices", "Appareils")}${th(base, params, "sources", "Sources")}<th>Trakt</th><th>OpenSubtitles</th></tr></thead>
<tbody>${rows.map(row).join("") || `<tr><td colspan="9" class="muted">Aucun compte ne correspond.</td></tr>`}</tbody></table></div>
${pager(base, params, pg)}`;
  return shell(n, { active: "accounts", title: "comptes", body, me, csrfInput });
}

// ---- Sans compte --------------------------------------------------------------------------

export function anonPage(n, { rows, pg, params, facets, platforms, now = Date.now(), me, csrfInput }) {
  const base = "/admin/anon";
  const row = (r) => `<tr><td><span class="kind">${e(platformName(r.platform))}</span></td><td>${e(r.model || "—")}</td><td>${r.version ? `v${e(r.version)}` : "—"}</td>
<td>${flagCode(r.country)}</td><td>${dateCell(r.first, now)}</td><td>${dateCell(r.last, now)}</td><td class="num">${r.web}</td></tr>`;
  const body = `<div class="section-h"><h2>Installations sans compte <span class="count">${pg.total}</span></h2></div>
<p class="muted small mb16">Relevé anonyme au démarrage (identifiant aléatoire haché, application, version, pays), seulement si la télémétrie de l'appli est activée et que l'appareil n'est pas appairé.</p>
${filters(base, params, { selects: [{ name: "platform", label: "Application", options: [...platforms.map((p) => [p, platformName(p)]), ["unknown", "Inconnue"]] }, { name: "version", label: "Version", options: facets.versions.map((v) => [v, `v${v}`]) }, { name: "country", label: "Pays", options: facets.countries.map((c) => [c, flagCode(c)]) }], placeholder: "Modèle, version, pays" })}
<div class="tablewrap"><table class="atable"><thead><tr>${th(base, params, "platform", "Application")}${th(base, params, "model", "Modèle")}${th(base, params, "version", "Version")}${th(base, params, "country", "Pays")}${th(base, params, "first", "Premier relevé")}${th(base, params, "last", "Dernier relevé")}${th(base, params, "web", "Envois web", "num")}</tr></thead>
<tbody>${rows.map(row).join("") || `<tr><td colspan="7" class="muted">Aucune installation ne correspond.</td></tr>`}</tbody></table></div>
${pager(base, params, pg)}`;
  return shell(n, { active: "anon", title: "sans compte", body, me, csrfInput });
}

// ---- Appareils ----------------------------------------------------------------------------

export function devicesPage(n, { rows, pg, params, facets, asOf, now = Date.now(), me, csrfInput }) {
  const base = "/admin/devices";
  const row = (d) => `<tr><td><strong>${e(d.name || "Appareil")}</strong></td><td>${e(d.login)}</td><td>${e(d.model || "—")}</td><td>${e(d.edition === "pro" ? "Pro" : "Standard")}</td>
<td>${d.version ? `v${e(d.version)}` : "—"}</td><td>${flagCode(d.country)}</td><td>${dateCell(d.lastSeen, now)}</td><td>${dateCell(d.createdAt, now)}</td></tr>`;
  const body = `<div class="section-h"><h2>Appareils appairés <span class="count">${pg.total}</span></h2></div>
${filters(base, params, { selects: [{ name: "country", label: "Pays", options: facets.countries.map((c) => [c, flagCode(c)]) }, { name: "version", label: "Version", options: facets.versions.map((v) => [v, `v${v}`]) }], placeholder: "Nom, modèle, compte, pays, version" })}
${cacheNote(listUrl(base, params, { refresh: 1, page: params.page }), asOf, now)}
<div class="tablewrap"><table class="atable"><thead><tr>${th(base, params, "name", "Appareil")}${th(base, params, "login", "Compte")}${th(base, params, "model", "Modèle")}${th(base, params, "edition", "Édition")}${th(base, params, "version", "Version")}${th(base, params, "country", "Pays")}${th(base, params, "seen", "Dernière activité")}${th(base, params, "created", "Appairé")}</tr></thead>
<tbody>${rows.map(row).join("") || `<tr><td colspan="8" class="muted">Aucun appareil ne correspond.</td></tr>`}</tbody></table></div>
${pager(base, params, pg)}`;
  return shell(n, { active: "devices", title: "appareils", body, me, csrfInput });
}

// ---- Journaux et plantages ----------------------------------------------------------------

/** [kind] « event » (journaux, 7 jours) ou « crash » (plantages, 30 jours). */
export function eventsAdminPage(n, kind, { rows, pg, params, facets, now = Date.now(), me, csrfInput, nav = true }) {
  const crash = kind === "crash";
  const base = crash ? "/crashes" : "/events";
  const levels = [["error", "Erreur"], ["warn", "Avertissement"], ["info", "Information"], ["debug", "Débogage"]];
  const row = crash
    ? (it) => `<tr><td>${dateCell(it.ts, now)}</td><td>${e(it.version || "?")} (${e(it.versionCode ?? "?")})</td><td>${e(it.device || "")}</td><td>SDK ${e(it.androidSdk ?? "?")}</td><td>${flagCode(it.country)}</td><td>${e(it.acct || "")}</td>
<td><details><summary>Pile</summary><pre>${e(it.stack || "")}</pre></details></td></tr>`
    : (it) => {
      const lvl = String(it.level || "info").toLowerCase();
      return `<tr class="lvl-${e(lvl)}"><td title="${e(fmtTime(it.ts))} UTC">${dateCell(it.ts, now)}</td><td class="level">${e(lvl.toUpperCase())}</td><td>${e(it.tag || "")}</td><td>${e(it.message || "")}</td><td>${e(it.device || "")}</td><td>${e(it.version || "")} (${e(it.versionCode ?? "?")})</td><td>${flagCode(it.country)}</td><td>${e(it.deviceId || "")}</td><td>${e(it.acct || "")}</td></tr>`;
    };
  const head = crash
    ? `${th(base, params, "ts", "Date")}${th(base, params, "version", "Version")}${th(base, params, "device", "Appareil")}<th>Android</th>${th(base, params, "country", "Pays")}<th>Compte (empreinte)</th><th>Détail</th>`
    : `${th(base, params, "ts", "Date")}${th(base, params, "level", "Niveau")}${th(base, params, "tag", "Tag")}<th>Message</th>${th(base, params, "device", "Appareil")}${th(base, params, "version", "Version")}${th(base, params, "country", "Pays")}<th>ID</th><th>Compte (empreinte)</th>`;
  const cols = crash ? 7 : 9;
  const selects = [{ name: "version", label: "Version", options: facets.versions.map((v) => [v, `v${v}`]) }, { name: "country", label: "Pays", options: facets.countries.map((c) => [c, flagCode(c)]) }];
  if (!crash) selects.push({ name: "level", label: "Niveau", options: levels });
  const body = `<div class="section-h"><h2>${crash ? "Plantages" : "Journaux"} <span class="count">${pg.total}</span></h2></div>
<p class="muted small mb16">${crash ? "Fenêtre glissante de 30 jours." : "Fenêtre glissante de 7 jours. Messages nettoyés côté serveur."}</p>
${filters(base, params, { selects, placeholder: crash ? "Appareil, version, pile, pays" : "Message, tag, appareil, version" })}
<div class="tablewrap"><table class="atable"><thead><tr>${head}</tr></thead>
<tbody>${rows.map(row).join("") || `<tr><td colspan="${cols}" class="muted">${crash ? "Aucun plantage" : "Aucun événement"}.</td></tr>`}</tbody></table></div>
${pager(base, params, pg)}`;
  return shell(n, { active: crash ? "crashes" : "events", title: crash ? "plantages" : "journaux", body, me, csrfInput, nav });
}

// ---- Alertes ------------------------------------------------------------------------------

const ALERT_MSG = {
  ok: { ann_added: "Alerte programmée.", ann_ended: "Alerte arrêtée.", ann_deleted: "Alerte supprimée." },
  err: {
    ann_empty: "Alerte : le message est obligatoire.", ann_dates: "Alerte : la fin doit être après le début.", ann_past: "Alerte : la date de fin est déjà passée.",
    ann_missing: "Alerte introuvable (déjà supprimée ?).", ann_version: "Alerte : version invalide (exemple : 1.2.79).",
  },
};

export function alertsPage(n, { announcements, annStats, ok, err, now = Date.now(), me, csrfInput }) {
  const okMsg = ok && ALERT_MSG.ok[ok], errMsg = err && ALERT_MSG.err[err];
  const notice = okMsg ? noticeBox("ok", okMsg) : errMsg ? noticeBox("err", errMsg) : "";
  const confirm = `document.querySelectorAll('form[data-confirm]').forEach(function(f){f.addEventListener('submit',function(ev){if(!window.confirm(f.getAttribute('data-confirm')))ev.preventDefault();});});`;
  const body = `${notice}<div class="agrid">${announcementsPanel(announcements, csrfInput, now, annStats)}</div>`;
  return shell(n, { active: "alerts", title: "alertes", body, me, csrfInput, script: ANN_SCRIPT + confirm });
}

