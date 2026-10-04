// Pages HTML. Règles : tout texte dynamique passe par escapeHtml ; aucun
// attribut on*= ni style="" (la CSP n'autorise que les balises portant le nonce).

import { escapeHtml as e, html } from "./http.js";
import { displayUrl } from "./store.js";

const CSS = `
:root{color-scheme:dark;--bg:#0b1020;--bg2:#131a30;--bg3:#1b2240;--fg:#e6e9f2;--muted:#8a93ac;--accent:#6ea8ff;--danger:#ff6b6b;--ok:#5fd19a;--border:rgba(255,255,255,.08)}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;padding:24px}
a{color:var(--accent);text-decoration:none}
h1{margin:0 0 4px;font-size:22px} h2{margin:0 0 8px;font-size:16px}
.sub,.muted{color:var(--muted)} .sub{margin-bottom:20px} .small{font-size:12px}
.panel{background:var(--bg2);border:1px solid var(--border);border-radius:12px;padding:16px;margin-bottom:14px}
label{display:block;color:var(--muted);font-size:12px;margin:10px 0 4px}
input,select{width:100%;background:var(--bg);color:var(--fg);border:1px solid var(--border);border-radius:8px;padding:9px 11px;font:inherit}
button{background:var(--accent);color:#0b1020;border:0;border-radius:8px;padding:9px 14px;font:inherit;font-weight:600;cursor:pointer}
button.secondary{background:transparent;color:var(--fg);border:1px solid var(--border);font-weight:400}
button.danger{background:transparent;color:var(--danger);border:1px solid var(--danger)}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-top:10px}
.item{display:flex;gap:10px;align-items:center;padding:10px 12px;background:var(--bg3);border-radius:10px;margin-top:8px}
.item form{margin-left:auto}
.kind{background:var(--accent);color:#0b1020;padding:2px 8px;border-radius:6px;font-size:11px;font-weight:700}
.tabs{display:flex;gap:6px;border-bottom:1px solid var(--border);margin:12px 0}
.tabs button{background:transparent;color:var(--muted);border-radius:0;font-weight:500}
.tabs button.on{color:var(--accent);border-bottom:2px solid var(--accent)}
.topbar{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px}
.card{max-width:400px;margin:80px auto;padding:24px;background:var(--bg2);border-radius:14px;border:1px solid var(--border)}
.layout{max-width:880px;margin:0 auto}
.notice{padding:10px 12px;border-radius:8px;background:var(--bg3);font-size:13px;margin-bottom:14px}
.notice.err{color:var(--danger);border:1px solid var(--danger)} .notice.ok{color:var(--ok);border:1px solid var(--ok)}
.pill{font-family:ui-monospace,monospace;background:var(--bg3);padding:6px 10px;border-radius:8px;color:var(--accent)}
.code{font-family:ui-monospace,monospace;letter-spacing:.15em}
table{width:100%;border-collapse:collapse;font-size:12px}
th,td{padding:8px 10px;border-bottom:1px solid var(--border);text-align:left;vertical-align:top}
th{color:var(--muted);text-transform:uppercase;letter-spacing:.06em;font-size:10px}
pre{white-space:pre-wrap;word-break:break-word;margin:6px 0 0;font-size:12px}
.lvl-error td.level,.lvl-warn td.level{color:var(--danger)}
[hidden]{display:none!important}
.inline{display:inline}
h2.mt{margin-top:20px}
`;

function layout(title, body, n, extra = "") {
  return html(`<!doctype html><html lang="fr"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><meta name="robots" content="noindex"/><title>${e(title)}</title><style nonce="${n}">${CSS}</style></head><body>${body}${extra}</body></html>`, n);
}

const AUTH_ERR = {
  pw: "Identifiant ou mot de passe incorrect.",
  login: "Identifiant invalide (3 à 64 caractères : lettres, chiffres, . _ @ : + -).",
  short: "Mot de passe trop court (10 caractères minimum).",
  mismatch: "Les deux mots de passe ne correspondent pas.",
  taken: "Cet identifiant n'est pas disponible.",
};

export function loginPage(n, err) {
  return layout("Ultra TV — connexion", `
<div class="card"><h1>Ultra TV</h1><div class="sub">Connexion à ta configuration</div>
${err && AUTH_ERR[err] ? `<div class="notice err">${e(AUTH_ERR[err])}</div>` : ""}
<form method="post" action="/login">
<label for="login">Identifiant <span class="small">(ou ton ancienne adresse MAC)</span></label>
<input id="login" name="login" required autocomplete="username" autofocus />
<label for="pw">Mot de passe</label>
<input id="pw" name="password" type="password" required autocomplete="current-password" />
<div class="row"><button type="submit">Se connecter</button><a href="/signup">Créer un compte</a></div>
</form></div>`, n);
}

export function signupPage(n, err) {
  return layout("Ultra TV — inscription", `
<div class="card"><h1>Ultra TV</h1><div class="sub">Créer un compte</div>
${err && AUTH_ERR[err] ? `<div class="notice err">${e(AUTH_ERR[err])}</div>` : ""}
<form method="post" action="/signup">
<label for="login">Identifiant</label>
<input id="login" name="login" required autocomplete="username" minlength="3" maxlength="64" />
<label for="pw">Mot de passe <span class="small">(10 caractères minimum)</span></label>
<input id="pw" name="password" type="password" required minlength="10" maxlength="200" autocomplete="new-password" />
<label for="pw2">Confirmer le mot de passe</label>
<input id="pw2" name="confirm" type="password" required minlength="10" maxlength="200" autocomplete="new-password" />
<p class="muted small">L'adresse MAC de ta box n'est plus un identifiant : ton appareil s'associe à ton compte avec un code affiché sur la TV.</p>
<div class="row"><button type="submit">Créer le compte</button><a href="/login">J'ai déjà un compte</a></div>
</form></div>`, n);
}

const DASH_MSG = {
  err: {
    code: "Code d'appairage invalide, expiré ou déjà utilisé.",
    limit: "Limite atteinte (20 fournisseurs, 10 appareils).",
    kind: "Type de fournisseur inconnu.",
    url: "URL invalide : seules les URL http:// et https:// sont acceptées.",
    creds: "Utilisateur et mot de passe requis pour Xtream.",
    mac: "Adresse MAC invalide (AA:BB:CC:DD:EE:FF).",
    short: "Nouveau mot de passe trop court (10 caractères minimum).",
    pw: "Mot de passe actuel incorrect.",
    assign: "Choisis au moins un appareil de ton compte.",
  },
  ok: { paired: "Appareil appairé.", added: "Fournisseur ajouté.", revoked: "Appareil révoqué.", renamed: "Appareil renommé.", assigned: "Affectation enregistrée.", pw: "Mot de passe mis à jour." },
};

/** Matrice fournisseur × appareils : cases à cocher + raccourci « Tous ». */
function assignForm(p, devices, csrfInput) {
  const a = p.assign === undefined || p.assign === "all" ? "all" : Array.isArray(p.assign) ? p.assign : "all";
  const boxes = devices.map((d) => `<label class="small"><input type="checkbox" name="d" value="${e(d.id)}"${a === "all" || a.includes(d.id) ? " checked" : ""}/> ${e(d.name)}</label>`).join(" ");
  return `<form method="post" action="/providers/${e(p.id)}/assign" class="small">${csrfInput}<span class="muted">Reçu par :</span> ${boxes || "—"}
<label class="small"><input type="checkbox" name="all" value="1"${a === "all" ? " checked" : ""}/> Tous</label> <button class="secondary" type="submit">Enregistrer</button></form>`;
}

export function dashboardPage(n, { acct, providers, csrf, err, ok }) {
  const csrfInput = `<input type="hidden" name="csrf" value="${e(csrf)}"/>`;
  const devices = acct.devices || [];
  const when = (t) => (t ? new Date(t).toISOString().slice(0, 10) : "");
  const provRows = providers.map((p) => `
<div class="item"><span class="kind">${e(p.kind)}</span>
<div><div><strong>${e(p.name)}</strong></div><div class="muted small">${e(displayUrl(p.url))}</div>
<div class="muted small">${p.originName ? `Ajouté depuis ${e(p.originName === "dashboard" ? "le tableau de bord" : p.originName)}` : "Origine inconnue"}${p.createdAt ? ` · le ${e(when(p.createdAt))}` : ""}${p.updatedAt && p.updatedAt > (p.createdAt || 0) + 60000 ? ` · modifié le ${e(when(p.updatedAt))}` : ""}</div>
${assignForm(p, devices, csrfInput)}</div>
<form method="post" action="/providers/${e(p.id)}/delete" data-confirm="Supprimer ce fournisseur ?">${csrfInput}<button class="danger" type="submit">Supprimer</button></form></div>`).join("");
  const devRows = devices.map((d) => `
<div class="item"><span class="kind">TV</span>
<div><div><strong>${e(d.name)}</strong></div><div class="muted small">${e(d.label || "")} · appairé le ${e(when(d.createdAt))}</div></div>
<form method="post" action="/devices/${e(d.id)}/rename" class="row">${csrfInput}<input name="name" maxlength="40" value="${e(d.name)}" aria-label="Nom de l'appareil"/><button class="secondary" type="submit">Renommer</button></form>
<form method="post" action="/devices/${e(d.id)}/revoke" data-confirm="Révoquer cet appareil ? Il ne pourra plus lire ta configuration.">${csrfInput}<button class="danger" type="submit">Révoquer</button></form></div>`).join("");
  const msg = (err && DASH_MSG.err[err] && `<div class="notice err">${e(DASH_MSG.err[err])}</div>`)
    || (ok && DASH_MSG.ok[ok] && `<div class="notice ok">${e(DASH_MSG.ok[ok])}</div>`) || "";
  const script = `<script nonce="${n}">
document.querySelectorAll('form[data-confirm]').forEach(function(f){f.addEventListener('submit',function(ev){if(!confirm(f.dataset.confirm))ev.preventDefault();});});
var ids=['xtream','m3u','stalker'];
document.querySelectorAll('.tabs button').forEach(function(b){b.addEventListener('click',function(){
 ids.forEach(function(k){document.getElementById('form-'+k).hidden=(k!==b.dataset.tab);});
 document.querySelectorAll('.tabs button').forEach(function(x){x.classList.toggle('on',x===b);});});});
</script>`;
  return layout(`Ultra TV — ${acct.login}`, `
<div class="layout">
<div class="topbar"><div><h1 class="inline">Ultra TV</h1> <span class="pill">${e(acct.login)}</span></div>
<form method="post" action="/logout">${csrfInput}<button class="secondary" type="submit">Se déconnecter</button></form></div>
${msg}
<div class="panel"><h2>Appairer un appareil</h2>
<div class="muted small">Ouvre Ultra TV sur ta TV : Réglages, Synchronisation cloud. Un code à 8 caractères s'affiche. Saisis-le ici.</div>
<form method="post" action="/pair">${csrfInput}
<label for="code">Code affiché sur la TV</label><input id="code" class="code" name="code" required maxlength="12" autocomplete="off" placeholder="ABCD-EFGH"/>
<label for="dname">Nom de l'appareil</label><input id="dname" name="name" maxlength="40" placeholder="Salon"/>
<div class="row"><button type="submit">Appairer</button></div></form>
<h2 class="mt">Appareils appairés (${devices.length})</h2>
${devRows || `<div class="muted small">Aucun appareil.</div>`}</div>
<div class="panel"><h2>Fournisseurs (${providers.length})</h2>${provRows || `<div class="muted small">Aucun fournisseur.</div>`}
<h2 class="mt">Ajouter un fournisseur</h2>
<div class="tabs"><button type="button" class="on" data-tab="xtream">Xtream Codes</button><button type="button" data-tab="m3u">M3U URL</button><button type="button" data-tab="stalker">Stalker</button></div>
<form method="post" action="/providers" id="form-xtream">${csrfInput}<input type="hidden" name="kind" value="XTREAM"/>
<label>Nom</label><input name="name" maxlength="64"/><label>URL du serveur</label><input name="url" required maxlength="2048" placeholder="http://provider.com:8080"/>
<label>Utilisateur</label><input name="username" required maxlength="256" autocomplete="off"/><label>Mot de passe</label><input name="password" type="password" required maxlength="256" autocomplete="off"/>
<div class="row"><button type="submit">Ajouter</button></div></form>
<form method="post" action="/providers" id="form-m3u" hidden>${csrfInput}<input type="hidden" name="kind" value="M3U"/>
<label>Nom</label><input name="name" maxlength="64"/><label>URL de la playlist</label><input name="url" required maxlength="2048"/>
<div class="row"><button type="submit">Ajouter</button></div></form>
<form method="post" action="/providers" id="form-stalker" hidden>${csrfInput}<input type="hidden" name="kind" value="STALKER"/>
<label>Nom</label><input name="name" maxlength="64"/><label>URL du portail</label><input name="url" required maxlength="2048"/>
<label>MAC du portail</label><input name="mac" required maxlength="17" placeholder="00:1A:79:XX:XX:XX"/>
<div class="row"><button type="submit">Ajouter</button></div></form>
<p class="muted small">Les identifiants sont chiffrés au repos et ne sont plus jamais réaffichés.</p></div>
<div class="panel"><h2>Mot de passe</h2>
<form method="post" action="/password">${csrfInput}
<label>Mot de passe actuel</label><input name="current" type="password" required autocomplete="current-password"/>
<label>Nouveau mot de passe (10 caractères minimum)</label><input name="password" type="password" required minlength="10" maxlength="200" autocomplete="new-password"/>
<div class="row"><button type="submit">Mettre à jour</button></div></form></div>
<div class="panel"><h2>Zone dangereuse</h2><div class="muted small">Supprime le compte, ses fournisseurs et révoque tous ses appareils.</div>
<form method="post" action="/account/delete" data-confirm="Supprimer définitivement ce compte ?">${csrfInput}
<label>Mot de passe</label><input name="password" type="password" required autocomplete="current-password"/>
<div class="row"><button class="danger" type="submit">Supprimer mon compte</button></div></form></div>
</div>`, n, script);
}

const fmtTime = (ts) => (ts ? new Date(ts).toISOString().replace("T", " ").slice(0, 19) : "");

export function eventsPage(n, items) {
  const rows = items.map((it) => {
    const lvl = String(it.level || "info").toLowerCase();
    return `<tr class="lvl-${e(lvl)}"><td>${e(fmtTime(it.ts))}</td><td class="level">${e(lvl.toUpperCase())}</td><td>${e(it.tag || "")}</td><td>${e(it.message || "")}</td><td>${e(it.device || "")}</td><td>${e(it.version || "")} (${e(it.versionCode ?? "?")})</td><td>${e(it.deviceId || "")}</td></tr>`;
  }).join("");
  return layout("Ultra TV — journaux", `<h1>Journaux — ${items.length}</h1><div class="sub">Fenêtre glissante de 7 jours. Messages nettoyés côté serveur.</div>
<table><thead><tr><th>Date</th><th>Niveau</th><th>Tag</th><th>Message</th><th>Appareil</th><th>Version</th><th>ID</th></tr></thead><tbody>${rows}</tbody></table>
${rows ? "" : `<div class="muted">Aucun événement.</div>`}`, n);
}

export function crashesPage(n, items) {
  const rows = items.map((it) => `<div class="panel"><div><strong>${e(it.version || "?")} (${e(it.versionCode ?? "?")})</strong> · ${e(it.device || "")} · SDK ${e(it.androidSdk ?? "?")} · ${e(fmtTime(it.ts))} · ${e(it.deviceId || "")}</div><pre>${e(it.stack || "")}</pre></div>`).join("");
  return layout("Ultra TV — crashs", `<h1>Crashs — ${items.length}</h1><div class="sub">Fenêtre glissante de 30 jours.</div>${rows || `<div class="muted">Aucun crash.</div>`}`, n);
}
