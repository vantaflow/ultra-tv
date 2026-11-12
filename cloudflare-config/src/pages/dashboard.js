// Tableau de bord utilisateur (/) : vue d'ensemble, sources, appareils, intégrations, compte.
import { escapeHtml as e } from "../http.js";
import { displayUrl, maskMac, xtreamCredsOf } from "../store.js";
import { ico, notice } from "../ui/components.js";
import { ago, frDate } from "../ui/format.js";
import { shell } from "../ui/layout.js";
import { PAIR_JS, SCAN_JS, pairForm } from "./pair.js";

const DASH_MSG = {
  err: {
    code: "Code d'appairage invalide, expiré ou déjà utilisé.",
    limit: "Limite atteinte (100 fournisseurs, 50 appareils).",
    os_denied: "OpenSubtitles a refusé ces identifiants.",
    trakt: "Connexion à Trakt annulée ou refusée : réessaie.",
    trakt_na: "Trakt n'est pas encore disponible sur ce site.",
    os_busy: "OpenSubtitles est saturé : réessaie dans une minute.",
    os_upstream: "OpenSubtitles ne répond pas : réessaie plus tard.",
    kind: "Type de fournisseur inconnu.",
    url: "URL invalide : seules les URL http:// et https:// sont acceptées.",
    creds: "Utilisateur et mot de passe requis pour Xtream.",
    mac: "Adresse MAC invalide : 12 caractères hexadécimaux (ex. 00:1A:79:12:34:56).",
    short: "Nouveau mot de passe trop court (10 caractères minimum).",
    pw: "Mot de passe actuel incorrect.",
    assign: "Choisis au moins un appareil de ton compte.",
    ann_empty: "Alerte : le message est obligatoire.",
    ann_dates: "Alerte : la fin doit être après le début.",
    ann_past: "Alerte : la date de fin est déjà passée.",
    ann_missing: "Alerte introuvable (déjà supprimée ?).",
    ann_version: "Alerte : version invalide (exemple : 1.2.79).",
  },
  ok: { trakt: "Trakt connecté : tes appareils y envoient ce que tu regardes.", trakt_off: "Trakt déconnecté.", os: "Compte OpenSubtitles relié : tes téléchargements de sous-titres utilisent ton quota.", os_off: "Compte OpenSubtitles délié.", paired: "Appareil appairé.", added: "Fournisseur ajouté.", revoked: "Appareil révoqué.", renamed: "Appareil renommé.", assigned: "Affectation enregistrée.", pw: "Mot de passe mis à jour.", ann_added: "Alerte programmée.", ann_ended: "Alerte arrêtée.", ann_deleted: "Alerte supprimée." },
};

/** « il y a 3 jours » : durée relative lisible, calculée côté serveur. */

function sharing(p, devices, csrfInput) {
  const a = p.assign === undefined || p.assign === "all" ? "all" : Array.isArray(p.assign) ? p.assign : "all";
  const shown = a === "all" ? [] : devices.filter((d) => a.includes(d.id));
  const chips = a === "all"
    ? `<span class="chip all">${ico("share")}Tous les appareils</span>`
    : shown.length ? shown.map((d) => `<span class="chip">${e(d.name)}</span>`).join("") : `<span class="chip none">Aucun appareil</span>`;
  const picks = devices.map((d) => `<label class="pick"><input type="checkbox" name="d" value="${e(d.id)}"${a === "all" || a.includes(d.id) ? " checked" : ""}/>${e(d.name)}</label>`).join("");
  return { chips: `<div class="chips" aria-label="Partagé avec">${chips}</div>`,
    form: `<details><summary>${ico("share")}Partage</summary><form method="post" action="/providers/${e(p.id)}/assign" class="drop share-form">${csrfInput}
<div class="lbl mt0">Reçu par</div>
<div class="picks"><label class="pick all"><input type="checkbox" name="all" value="1"${a === "all" ? " checked" : ""}/>Tous les appareils</label>${picks}</div>
<div class="row"><button type="submit">Enregistrer le partage</button></div></form></details>` };
}


const LIC_STATUS = { active: "Active", trial: "Essai", expired: "Expirée", suspended: "Suspendue", revoked: "Révoquée" };

/** Licence d'un appareil Pro (statut signé par le revendeur, vérifié à la réception). */
function licenseLine(lic, now = Date.now()) {
  if (!lic) return `<div class="card-meta mt10 muted">Licence Pro : en attente de la prochaine synchronisation de l'appareil.</div>`;
  const left = lic.until ? Math.ceil((lic.until - now) / 86_400_000) : null;
  const expired = lic.status === "expired" || (left !== null && left < 0);
  const cls = expired || lic.status === "suspended" || lic.status === "revoked" ? " bad" : left !== null && left <= 15 ? " warn" : "";
  const parts = [`<strong>${e(LIC_STATUS[lic.status] || lic.status || "?")}</strong>`];
  if (lic.until) parts.push(expired ? `expirée le ${e(frDate(lic.until))}` : `jusqu'au ${e(frDate(lic.until))} (${left === 0 ? "aujourd'hui" : `dans ${left} j`})`);
  if (lic.devices) parts.push(`${lic.devices.used}/${lic.devices.max} appareil${lic.devices.max > 1 ? "s" : ""}`);
  const r = lic.reseller;
  const contact = r ? [
    r.name ? `revendeur <strong>${e(r.name)}</strong>` : "",
    r.whatsapp && /^\+?[0-9 ]{6,20}$/.test(r.whatsapp) ? `<a href="https://wa.me/${e(r.whatsapp.replace(/[^0-9]/g, ""))}" target="_blank" rel="noopener noreferrer">WhatsApp</a>` : "",
    r.telegram && /^@?[A-Za-z0-9_]{4,32}$/.test(r.telegram) ? `<a href="https://t.me/${e(r.telegram.replace(/^@/, ""))}" target="_blank" rel="noopener noreferrer">Telegram</a>` : "",
  ].filter(Boolean).join(" · ") : "";
  const stale = now - lic.issuedAt > 2 * 86_400_000 ? ` <span class="muted">(vérifiée le ${e(frDate(lic.issuedAt))})</span>` : "";
  return `<div class="acct card-meta mt10${cls}">Licence Pro : ${parts.join(" · ")}${stale}${contact ? `<br/>${contact}` : ""}</div>`;
}

export function dashboardPage(n, { acct, providers, csrf, err, ok, admin = false }) {
  const csrfInput = `<input type="hidden" name="csrf" value="${e(csrf)}"/>`;
  const devices = acct.devices || [];
  const when = (t) => (t ? new Date(t).toISOString().slice(0, 10) : "");
  const provRows = providers.map((p) => {
    const sh = sharing(p, devices, csrfInput);
    const origin = p.originName ? `Ajouté depuis ${e(p.originName === "dashboard" ? "le tableau de bord" : p.originName)}` : "Origine inconnue";
    return `
<article class="card" data-q="${e(`${p.name} ${displayUrl(p.url)} ${p.kind}`.toLowerCase())}"><div class="card-top"><div class="badge-ico">${ico("list")}</div>
<div class="grow"><div class="card-title"><span class="kind">${e(p.kind)}</span>${e(p.name)}</div><div class="card-meta">${e(displayUrl(p.url))}${p.kind === "STALKER" ? ` · MAC ${e(maskMac(p.mac))}` : ""}</div></div></div>
${sh.chips}${p.managed === "reseller" ? `<div class="chips"><span class="chip acc">Gérée par ton revendeur (Pro)</span></div>` : ""}
<div class="card-meta mt10">${origin}${p.createdAt ? ` · le ${e(when(p.createdAt))}` : ""}${p.updatedAt && p.updatedAt > (p.createdAt || 0) + 60000 ? ` · modifié le ${e(when(p.updatedAt))}` : ""}</div>
${xtreamCredsOf(p) ? `<div class="acct card-meta mt10" data-id="${e(p.id)}" aria-live="polite"><span class="muted">Abonnement : chargement…</span></div>` : ""}
${p.kind === "STALKER" ? `<div class="card-meta mt10"><span class="muted">Abonnement visible dans l'application (le portail n'accepte que la MAC de l'appareil).</span></div>` : ""}
<div class="reveal-box mt10" id="lk-${e(p.id)}" hidden><label class="mt0" for="lki-${e(p.id)}">Lien IPTV</label>
<div class="row"><input id="lki-${e(p.id)}" readonly spellcheck="false" autocomplete="off"/><button type="button" class="secondary copy-link" data-for="lki-${e(p.id)}">Copier</button></div></div>
<div class="actions">${sh.form}
${p.managed === "reseller"
    ? `<span class="muted small">Ses identifiants appartiennent à ton revendeur : ni affichés ni supprimables ici.</span>`
    : `${p.kind === "STALKER"
    ? `<span class="muted small">Lien IPTV non disponible pour un portail Stalker : il s'ouvre avec la MAC, dans l'application.</span>`
    : `<button type="button" class="secondary reveal-link" data-id="${e(p.id)}" aria-controls="lk-${e(p.id)}" aria-expanded="false">Afficher le lien IPTV</button>`}
<form method="post" action="/providers/${e(p.id)}/delete" data-confirm="Supprimer ce fournisseur ?">${csrfInput}<button class="danger" type="submit">Supprimer</button></form>`}</div></article>`;
  }).join("");
  const devRows = devices.map((d) => `
<article class="card" data-q="${e(`${d.name} ${d.label || ""} ${d.edition || ""}`.toLowerCase())}"><div class="card-top"><div class="badge-ico">${ico("tv")}</div>
<div class="grow"><div class="card-title">${e(d.name)} ${d.edition === "pro" ? `<span class="kind pro">PRO</span>` : d.edition === "standard" ? `<span class="kind std">STANDARD</span>` : ""}</div><div class="card-meta">${d.label ? `${e(d.label)} · ` : ""}${d.version ? `version ${e(d.version)} · ` : ""}appairé ${e(ago(d.createdAt))}</div></div></div>
${d.edition === "pro" ? licenseLine(d.license) : ""}
<div class="actions"><details><summary>${ico("edit")}Renommer</summary><form method="post" action="/devices/${e(d.id)}/rename" class="drop">${csrfInput}
<label class="mt0" for="rn-${e(d.id)}">Nom de l'appareil</label><input id="rn-${e(d.id)}" name="name" maxlength="40" value="${e(d.name)}"/>
<div class="row"><button type="submit">Renommer</button></div></form></details>
<form method="post" action="/devices/${e(d.id)}/revoke" data-confirm="Révoquer cet appareil ? Il ne pourra plus lire ta configuration.">${csrfInput}<button class="danger" type="submit">Révoquer</button></form></div></article>`).join("");
  const msg = (err && DASH_MSG.err[err] && notice("err", DASH_MSG.err[err])) || (ok && DASH_MSG.ok[ok] && notice("ok", DASH_MSG.ok[ok])) || "";
  const script = `<script nonce="${n}">
document.querySelectorAll('form[data-confirm]').forEach(function(f){f.addEventListener('submit',function(ev){if(!confirm(f.dataset.confirm))ev.preventDefault();});});
document.querySelectorAll('.share-form').forEach(function(f){var all=f.querySelector('input[name=all]'),ds=f.querySelectorAll('input[name=d]');
 all.addEventListener('change',function(){ds.forEach(function(d){d.checked=all.checked;});});
 ds.forEach(function(d){d.addEventListener('change',function(){all.checked=Array.prototype.every.call(ds,function(x){return x.checked;});});});});
document.querySelectorAll('.reveal-link').forEach(function(b){b.addEventListener('click',function(){
 var box=document.getElementById('lk-'+b.dataset.id);
 if(!box.hidden){box.hidden=true;box.querySelector('input').value='';b.setAttribute('aria-expanded','false');b.textContent='Afficher le lien IPTV';return;}
 b.disabled=true;
 fetch('/providers/'+b.dataset.id+'/link',{method:'POST',credentials:'same-origin',headers:{'content-type':'application/x-www-form-urlencoded'},body:'csrf='+encodeURIComponent(${JSON.stringify(csrf)})})
  .then(function(r){if(!r.ok)throw new Error(String(r.status));return r.json();})
  .then(function(j){var i=box.querySelector('input');i.value=j.link||'';box.hidden=false;b.setAttribute('aria-expanded','true');b.textContent='Masquer le lien';i.focus();i.select();})
  .catch(function(){alert('Impossible de récupérer le lien.');})
  .then(function(){b.disabled=false;});
});});
// Abonnement de chaque fournisseur Xtream : lu par le serveur (identifiants jamais exposés), un appel par carte.
(function(){
 var MOIS=['janv.','févr.','mars','avr.','mai','juin','juil.','août','sept.','oct.','nov.','déc.'];
 var dt=function(ms){var d=new Date(ms);return d.getDate()+' '+MOIS[d.getMonth()]+' '+d.getFullYear();};
 var ERR={unsupported:'non disponible pour une liste M3U',port:'port non joignable depuis Cloudflare : informations visibles seulement dans l\\'application',denied:'identifiants refusés par le serveur',unreachable:'le serveur ne répond pas (essaie plus tard)'};
 // Le statut vient du serveur du FOURNISSEUR (non fiable) : toujours échappé avant insertion.
 var x=function(v){return String(v).replace(/[&<>"']/g,function(c){return '&#'+c.charCodeAt(0)+';';});};
 var set=function(el,html,cls){el.innerHTML=html;el.className='acct card-meta mt10'+(cls?' '+cls:'');};
 // Chargé à la DEMANDE : seulement pour les cartes affichées (pagination) — un compte avec beaucoup de fournisseurs ne
 // lance pas des dizaines de requêtes d'un coup. La tuile résume ce qui a été vérifié.
 var total=document.querySelectorAll('.acct[data-id]').length,done=0,soon=0,expired=0,ok=0;
 var tally=function(){done++;var n=document.getElementById('sub-n'),l=document.getElementById('sub-l'),t=document.getElementById('sub-tile');if(!n)return;
  var of=done<total?' (sur '+done+' vérifié'+(done>1?'s':'')+')':'';t.classList.remove('bad','warn');
  if(expired){n.textContent=String(expired);l.textContent='abonnement'+(expired>1?'s':'')+' expiré'+(expired>1?'s':'')+of;t.classList.add('bad');}
  else if(soon){n.textContent=String(soon);l.textContent='expire'+(soon>1?'nt':'')+' dans moins de 15 jours'+of;t.classList.add('warn');}
  else if(ok){n.textContent=String(ok);l.textContent='abonnement'+(ok>1?'s':'')+' actif'+(ok>1?'s':'')+of;}
  else if(done>=total){n.textContent='—';l.textContent='abonnements : infos indisponibles';}};
 window.utvLoadAccounts=function(root){(root||document).querySelectorAll('.acct[data-id]:not([data-loaded])').forEach(function(el){
  if(el.closest('[hidden]'))return;el.setAttribute('data-loaded','1');
  fetch('/providers/'+el.dataset.id+'/account',{method:'POST',credentials:'same-origin',headers:{'content-type':'application/x-www-form-urlencoded'},body:'csrf='+encodeURIComponent(${JSON.stringify(csrf)})})
   .then(function(r){if(!r.ok)throw new Error(String(r.status));return r.json();})
   .then(function(a){
    if(a.error){set(el,'<span class="muted">Abonnement : '+(a.error==='port'&&a.port?'port '+x(a.port)+' non joignable depuis Cloudflare : informations visibles seulement dans l\\'application':a.error==='unreachable'&&a.detail?x(a.detail):(ERR[a.error]||ERR.unreachable))+'</span>');tally();return;}
    var parts=[],cls='';
    var st=(a.status||'').toLowerCase();
    var active=st==='active'||st==='';
    if(a.expiresAt){
     var days=Math.ceil((a.expiresAt-Date.now())/86400000);
     if(days<0){parts.push('<strong>Expiré</strong> le '+dt(a.expiresAt));cls='bad';}
     else{parts.push((active?'<strong>Actif</strong>':'<strong>'+x(a.status||'')+'</strong>')+' · expire le '+dt(a.expiresAt)+' ('+(days===0?'aujourd\\'hui':'dans '+days+' j')+')');if(days<=15)cls='warn';}
    }else parts.push((active?'<strong>Actif</strong>':'<strong>'+x(a.status||'')+'</strong>')+' · sans date d\\'expiration');
    if(!active&&cls!=='bad')cls='bad';
    if(a.maxCons!=null)parts.push((a.activeCons!=null?a.activeCons+'/':'')+a.maxCons+' connexion'+(a.maxCons>1?'s':''));
    if(a.trial)parts.push('essai');
    if(a.createdAt)parts.push('créé le '+dt(a.createdAt));
    set(el,'Abonnement : '+parts.join(' · '),cls);
    if(cls==='bad')expired++;else if(cls==='warn')soon++;else ok++;
    // Jauge : part de la période (création → expiration) déjà écoulée ; largeur posée par le CSSOM (pas d'attribut style).
    if(a.expiresAt&&a.createdAt&&a.expiresAt>a.createdAt){var m=document.createElement('div');m.className='meter';var i=document.createElement('i');m.appendChild(i);el.appendChild(m);
     var f=Math.min(1,Math.max(0,(Date.now()-a.createdAt)/(a.expiresAt-a.createdAt)));requestAnimationFrame(function(){i.style.width=Math.round(f*100)+'%';});}
    tally();
   })
   .catch(function(e){var c=e&&/^\\d+$/.test(e.message)?e.message:'';set(el,'<span class="muted">Abonnement : '+(c==='429'?'trop de vérifications, réessaie dans un moment':c?'vérification impossible (erreur '+c+' du site)':ERR.unreachable)+'</span>');tally();});
 });};
})();
// Recherche + pagination des listes (utile au-delà de quelques éléments ; sans JavaScript, tout reste affiché).
(function(){var SIZE=10;
 document.querySelectorAll('.listbar[data-list]').forEach(function(bar){
  var list=document.getElementById(bar.dataset.list);if(!list)return;
  var cards=Array.prototype.slice.call(list.querySelectorAll(':scope>article.card'));
  var q=bar.querySelector('.lsearch'),pg=bar.querySelector('.pager'),info=bar.querySelector('.pinfo'),prev=bar.querySelector('.pprev'),next=bar.querySelector('.pnext');
  if(cards.length<=6){if(window.utvLoadAccounts)window.utvLoadAccounts(list);return;}
  bar.hidden=false;var page=0;
  var render=function(){var term=(q.value||'').trim().toLowerCase();
   var hits=cards.filter(function(c){return !term||(c.dataset.q||'').indexOf(term)>=0;});
   var pages=Math.max(1,Math.ceil(hits.length/SIZE));if(page>=pages)page=pages-1;
   cards.forEach(function(c){c.hidden=true;});
   hits.slice(page*SIZE,page*SIZE+SIZE).forEach(function(c){c.hidden=false;});
   pg.hidden=hits.length<=SIZE;info.textContent=hits.length?('Page '+(page+1)+' / '+pages+' · '+hits.length+' élément'+(hits.length>1?'s':'')):'Aucun résultat';
   prev.disabled=page===0;next.disabled=page>=pages-1;
   if(window.utvLoadAccounts)window.utvLoadAccounts(list);};
  q.addEventListener('input',function(){page=0;render();});
  prev.addEventListener('click',function(){page--;render();});
  next.addEventListener('click',function(){page++;render();});
  render();
 });
 if(window.utvLoadAccounts)window.utvLoadAccounts();
})();
document.querySelectorAll('.copy-link').forEach(function(c){c.addEventListener('click',function(){
 var i=document.getElementById(c.dataset.for);i.select();
 var done=function(){c.textContent='Copié';setTimeout(function(){c.textContent='Copier';},1500);};
 if(navigator.clipboard){navigator.clipboard.writeText(i.value).then(done,function(){document.execCommand('copy');done();});}else{document.execCommand('copy');done();}
});});
${PAIR_JS}
${SCAN_JS}
</script>`;
  const xtreamCount = providers.filter((p) => xtreamCredsOf(p)).length;
  // Compte « Pro » : au moins un appareil de l'édition Pro (déclarée par l'appli, licence vérifiée à la réception).
  const proDevices = devices.filter((d) => d.edition === "pro");
  const isPro = proDevices.length > 0;
  const nextLic = proDevices.map((d) => d.license?.until).filter((u) => typeof u === "number").sort((a, b) => a - b)[0];
  const listbar = (id, what) => `<div class="listbar" data-list="${id}" hidden><input type="search" class="lsearch" placeholder="Rechercher ${what}" aria-label="Rechercher ${what}" autocomplete="off"/><span class="pager"><button type="button" class="secondary pprev" aria-label="Page précédente">‹</button><span class="pinfo" aria-live="polite"></span><button type="button" class="secondary pnext" aria-label="Page suivante">›</button></span></div>`;
  const step = (done, text) => `<li class="${done ? "done" : ""}">${done ? `<span class="vh">Fait : </span>` : ""}${text}</li>`;
  // Premiers pas : visibles tant que le compte n'a ni appareil ni source.
  const gettingStarted = !devices.length || !providers.length
    ? `<section class="panel" aria-labelledby="h-start"><h2 id="h-start">Pour bien démarrer</h2><p class="sub">Deux étapes suffisent pour regarder tes chaînes.</p>
<ol class="steps">${step(devices.length > 0, `<span><a href="#appairer">Appaire ta TV</a> avec le code affiché à l'écran : elle reçoit tes sources toute seule.</span>`)}${step(providers.length > 0, `<span>Ajoute une <a href="#ajouter">source</a> (Xtream Codes, playlist M3U ou portail Stalker).</span>`)}</ol></section>`
    : "";
  return shell(n, { title: `Ultra TV — ${acct.login}`, ctx: "user", spy: true, acct, admin, csrfInput, camera: true, extra: script, body: `
${msg}
<section id="apercu" class="hello" aria-labelledby="h-hello"><h1 id="h-hello">Bonjour ${e(acct.login)}${isPro ? ` <span class="kind pro">PRO</span>` : ""}</h1><p class="sub">${isPro
    ? `Compte <strong>Ultra TV Pro</strong> : ${proDevices.length} appareil${proDevices.length > 1 ? "s" : ""} sous licence revendeur. Sources, appareils, abonnements et licences.`
    : "Ta configuration Ultra TV : sources, appareils et abonnements."}</p>
<div class="tiles">
<div class="tile hero"><b>${devices.length}</b><span>appareil${devices.length > 1 ? "s" : ""} appairé${devices.length > 1 ? "s" : ""}</span></div>
<div class="tile"><b>${providers.length}</b><span>source${providers.length > 1 ? "s" : ""}</span></div>
${isPro ? `<div class="tile${nextLic && nextLic < Date.now() ? " bad" : nextLic && nextLic - Date.now() < 15 * 86_400_000 ? " warn" : ""}"><b>${nextLic ? e(frDate(nextLic)) : "—"}</b><span>${nextLic ? "prochaine échéance de licence Pro" : "licence Pro : en attente de l'appareil"}</span></div>` : ""}
<div class="tile" id="sub-tile"><b id="sub-n">${xtreamCount ? "…" : "—"}</b><span id="sub-l">${xtreamCount ? "abonnements : vérification" : "aucun abonnement Xtream"}</span></div>
</div>
${gettingStarted}</section>

<div class="section-h" id="appareils"><div class="sh-t"><h2 id="h-dev">Appareils <span class="count">${devices.length}</span></h2><p class="muted small">Les TV qui lisent ta configuration.</p></div></div>
<section class="panel" id="appairer" aria-labelledby="h-pair"><h2 id="h-pair">Appairer un appareil</h2><p class="sub">Relie une TV à ton compte en quelques secondes.</p>
<ol class="steps"><li>Ouvre Ultra TV sur ta TV.</li><li>Va dans Réglages, puis Synchronisation cloud.</li><li>Saisis ici le code à 8 caractères affiché à l'écran.</li></ol>
${pairForm(csrfInput)}</section>
${listbar("dev-list", "un appareil")}
<div class="cards c2 mt16" id="dev-list">${devRows || `<div class="empty"><div class="badge-ico">${ico("tv")}</div><strong>Aucun appareil</strong><span class="small">Appaire ta première TV avec le code affiché à l'écran.</span></div>`}</div>

<div class="section-h" id="sources"><div class="sh-t"><h2 id="h-src">Sources <span class="count">${providers.length}</span></h2><p class="muted small">Tes fournisseurs de chaînes, leur abonnement, et les appareils qui les reçoivent.</p></div><a class="button secondary" href="#ajouter">${ico("plus")}Ajouter une source</a></div>
${listbar("prov-list", "une source")}
<div class="cards c2" id="prov-list">${provRows || `<div class="empty"><div class="badge-ico">${ico("list")}</div><strong>Aucune source</strong><span class="small">Ajoute une source Xtream Codes, une playlist M3U ou un portail Stalker avec le formulaire ci-dessous.</span></div>`}</div>
<section class="panel mt16" id="ajouter" aria-labelledby="h-add"><h2 id="h-add">Ajouter une source</h2><p class="sub">Choisis le type de ton fournisseur, puis recopie les informations qu'il t'a données.</p>
<div class="tabbed"><input type="radio" name="tab" id="t-xtream" checked aria-label="Xtream Codes"/><input type="radio" name="tab" id="t-m3u" aria-label="Playlist M3U"/><input type="radio" name="tab" id="t-stalker" aria-label="Portail Stalker (MAC)"/><div class="seg" aria-hidden="true"><label for="t-xtream">Xtream Codes</label><label for="t-m3u">Playlist M3U</label><label for="t-stalker">Portail Stalker</label></div>
<form method="post" action="/providers" id="form-xtream">${csrfInput}<input type="hidden" name="kind" value="XTREAM"/>
<label for="xn">Nom <span class="hint">(facultatif)</span></label><input id="xn" name="name" maxlength="64" placeholder="Mon fournisseur"/>
<label for="xu">URL du serveur</label><input id="xu" name="url" type="url" required maxlength="2048" placeholder="http://provider.com:8080" autocapitalize="none" spellcheck="false"/>
<label for="xl">Utilisateur</label><input id="xl" name="username" required maxlength="256" autocomplete="off" autocapitalize="none" spellcheck="false"/>
<label for="xp">Mot de passe</label><input id="xp" name="password" type="password" required maxlength="256" autocomplete="off"/>
<div class="row"><button type="submit">${ico("plus")}Ajouter</button></div></form>
<form method="post" action="/providers" id="form-m3u">${csrfInput}<input type="hidden" name="kind" value="M3U"/>
<label for="mn">Nom <span class="hint">(facultatif)</span></label><input id="mn" name="name" maxlength="64" placeholder="Ma playlist"/>
<label for="mu">URL de la playlist</label><input id="mu" name="url" type="url" required maxlength="2048" placeholder="https://exemple.com/liste.m3u" autocapitalize="none" spellcheck="false"/>
<div class="row"><button type="submit">${ico("plus")}Ajouter</button></div></form>
<form method="post" action="/providers" id="form-stalker">${csrfInput}<input type="hidden" name="kind" value="STALKER"/>
<label for="sn">Nom <span class="hint">(facultatif)</span></label><input id="sn" name="name" maxlength="64" placeholder="Mon portail"/>
<label for="su">Adresse du portail</label><input id="su" name="url" type="url" required maxlength="2048" placeholder="http://portail.tv:8080/c/" autocapitalize="none" spellcheck="false"/>
<label for="sm">Adresse MAC</label><input id="sm" name="mac" required maxlength="17" pattern="[0-9A-Fa-f]{2}([:\-]?[0-9A-Fa-f]{2}){5}" placeholder="00:1A:79:XX:XX:XX" autocomplete="off" autocapitalize="characters" spellcheck="false"/>
<label for="sl">Identifiant du portail <span class="hint">(facultatif)</span></label><input id="sl" name="username" maxlength="256" autocomplete="off" autocapitalize="none" spellcheck="false"/>
<label for="sp">Mot de passe du portail <span class="hint">(facultatif)</span></label><input id="sp" name="password" type="password" maxlength="256" autocomplete="off"/>
<div class="row"><button type="submit">${ico("plus")}Ajouter</button></div></form></div>
<p class="secure">${ico("lock")}<span>Les identifiants sont chiffrés au repos et ne sont plus jamais réaffichés.</span></p></section>

<div class="section-h" id="integrations"><span id="compte" class="anchor"></span><div class="sh-t"><h2>Intégrations</h2><p class="muted small">Services facultatifs reliés à ton compte : ils fonctionnent sur tous tes appareils, sans rien régler sur la TV.</p></div></div>
<div class="two">
${traktPanel(acct.trakt, csrfInput)}
${osPanel(acct.os, csrfInput)}
</div>

<div class="section-h" id="securite"><div class="sh-t"><h2>Compte et sécurité</h2><p class="muted small">Mot de passe et suppression du compte.</p></div></div>
<div class="two">
<section class="panel" aria-labelledby="h-pw"><h2 id="h-pw">Mot de passe</h2>
<form method="post" action="/password">${csrfInput}
<label for="cur">Mot de passe actuel</label><input id="cur" name="current" type="password" required autocomplete="current-password"/>
<label for="npw">Nouveau mot de passe <span class="hint">(10 caractères minimum)</span></label><input id="npw" name="password" type="password" required minlength="10" maxlength="200" autocomplete="new-password"/>
<div class="row"><button type="submit">Mettre à jour</button></div></form></section>
<section class="panel danger-zone" aria-labelledby="h-dz"><h2 id="h-dz">Zone dangereuse</h2><p class="muted small mt6">Supprime le compte, ses fournisseurs et révoque tous ses appareils. Cette action est définitive.</p>
<form method="post" action="/account/delete" data-confirm="Supprimer définitivement ce compte ?">${csrfInput}
<label for="dpw">Mot de passe</label><input id="dpw" name="password" type="password" required autocomplete="current-password"/>
<div class="row"><button class="danger" type="submit">Supprimer mon compte</button></div></form></section>
</div>` });
}

/** Trakt (facultatif) : connexion OAuth depuis l'espace client ; les appareils n'ont rien à configurer. */
function traktPanel(t, csrfInput) {
  const linked = t && !t.revoked;
  return `<section class="panel" aria-labelledby="h-trakt"><h2 id="h-trakt">Trakt <span class="hint">(facultatif)</span></h2>
${linked ? `<p class="mt6">Connecté${t.user ? ` au compte <strong>${e(t.user)}</strong>` : ""}.</p>
<p class="muted small mt6">Films et épisodes regardés sur tes appareils sont envoyés à Trakt (en cours, puis vu à la fin). Depuis les fiches, tes appareils peuvent aussi gérer ta watchlist, marquer vu / non vu et noter ; l'accueil montre « À suivre », tes listes personnelles et reprend là où tu t'étais arrêté — uniquement avec ce que contient ta playlist. Rien à régler sur la TV.</p>
<form method="post" action="/trakt/disconnect">${csrfInput}<div class="row"><button class="secondary" type="submit">Déconnecter Trakt</button></div></form>`
    : `<p class="muted small mt6">${t && t.revoked ? "L'accès a été retiré chez Trakt : reconnecte-toi pour reprendre le suivi. " : ""}Relie ton compte Trakt : ce que tu regardes sur tes appareils (films, épisodes) y est enregistré automatiquement, sans rien régler sur la TV.</p>
<form method="post" action="/trakt/connect">${csrfInput}<div class="row"><button type="submit">Connecter Trakt</button></div></form>
<p class="secure">${ico("lock")}<span>Connexion faite chez Trakt : ton mot de passe Trakt ne passe jamais par ce site.</span></p>`}</section>`;
}

/**
 * Compte OpenSubtitles du client (facultatif) : sans lui, les sous-titres passent par la clé commune (quota partagé) ;
 * avec lui, par SON quota. Le mot de passe n'est jamais réaffiché.
 */
function osPanel(os, csrfInput) {
  const quota = (o) => [o.vip ? "VIP" : o.level ? e(o.level) : "", o.allowed != null ? `${e(o.allowed)} téléchargements par jour` : ""].filter(Boolean).join(" · ");
  return `<section class="panel" aria-labelledby="h-os"><h2 id="h-os">Sous-titres · OpenSubtitles <span class="hint">(facultatif)</span></h2>
${os ? `<p class="mt6">Relié au compte <strong>${e(os.user)}</strong>${quota(os) ? ` · ${quota(os)}` : ""}.</p>
<p class="muted small mt6">Les sous-titres téléchargés par tes appareils sont décomptés sur ce compte, pas sur le quota commun.</p>
<form method="post" action="/subtitles/unlink">${csrfInput}<div class="row"><button class="secondary" type="submit">Délier ce compte</button></div></form>`
    : `<p class="muted small mt6">Les sous-titres fonctionnent déjà sur tes appareils, avec un quota commun limité. Relie ton compte OpenSubtitles (gratuit ou VIP) pour utiliser ton propre quota. Pas de compte ? <a href="https://www.opensubtitles.com/fr/users/sign_up" target="_blank" rel="noopener noreferrer">En créer un</a>.</p>
<form method="post" action="/subtitles/link">${csrfInput}
<label for="osu">Identifiant OpenSubtitles</label><input id="osu" name="os_user" required maxlength="100" autocomplete="off"/>
<label for="osp">Mot de passe OpenSubtitles</label><input id="osp" name="os_pass" type="password" required maxlength="200" autocomplete="off"/>
<div class="row"><button type="submit">Relier</button></div></form>
<p class="secure">${ico("lock")}<span>Chiffré au repos, jamais réaffiché ni envoyé aux appareils.</span></p>`}</section>`;
}

