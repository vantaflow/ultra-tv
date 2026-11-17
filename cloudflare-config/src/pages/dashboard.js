// Tableau de bord utilisateur (/) : vue d'ensemble, sources, appareils, intégrations, compte.
import { KOFI_URL, LIBERAPAY_URL } from "../donate.js";
import { escapeHtml as e } from "../http.js";
import { SITE_DICT } from "../i18n/site.js";
import { displayUrl, maskMac, xtreamCredsOf, MEDIA_KINDS } from "../store.js";
import { ico, notice } from "../ui/components.js";
import { ago, frDate } from "../ui/format.js";
import { shell } from "../ui/layout.js";
import { PAIR_JS, scanJs, pairForm } from "./pair.js";

function sharing(p, devices, csrfInput, lang = "fr") {
  const dict = SITE_DICT[lang] || SITE_DICT.fr;
  const s = dict.dash.srcs;
  const a = p.assign === undefined || p.assign === "all" ? "all" : Array.isArray(p.assign) ? p.assign : "all";
  const shown = a === "all" ? [] : devices.filter((d) => a.includes(d.id));
  const chips = a === "all"
    ? `<span class="chip all">${ico("share")}${e(s.allDevs)}</span>`
    : shown.length ? shown.map((d) => `<span class="chip">${e(d.name)}</span>`).join("") : `<span class="chip none">${e(s.noDevs)}</span>`;
  const picks = devices.map((d) => `<label class="pick"><input type="checkbox" name="d" value="${e(d.id)}"${a === "all" || a.includes(d.id) ? " checked" : ""}/>${e(d.name)}</label>`).join("");
  return { chips: `<div class="chips" aria-label="${e(s.sharedWith)}">${chips}</div>`,
    form: `<details><summary>${ico("share")}${e(s.share)}</summary><form method="post" action="/providers/${e(p.id)}/assign" class="drop share-form">${csrfInput}
<div class="lbl mt0">${e(s.receivedBy)}</div>
<div class="picks"><label class="pick all"><input type="checkbox" name="all" value="1"${a === "all" ? " checked" : ""}/>${e(s.allDevs)}</label>${picks}</div>
<div class="row"><button type="submit">${e(s.saveShare)}</button></div></form></details>` };
}

const LIC_STATUS = {
  fr: { active: "Active", trial: "Essai", expired: "Expirée", suspended: "Suspendue", revoked: "Révoquée" },
  en: { active: "Active", trial: "Trial", expired: "Expired", suspended: "Suspended", revoked: "Revoked" },
  es: { active: "Activa", trial: "Prueba", expired: "Caducada", suspended: "Suspendida", revoked: "Revocada" },
  ar: { active: "نشط", trial: "تجريبي", expired: "منتهي", suspended: "معلق", revoked: "ملغى" },
  pl: { active: "Aktywna", trial: "Próbna", expired: "Wygasła", suspended: "Zawieszona", revoked: "Odwołana" },
};

/** Licence d'un appareil Pro (statut signé par le revendeur, vérifié à la réception). */
function licenseLine(lic, now = Date.now(), lang = "fr") {
  const t = (SITE_DICT[lang] || SITE_DICT.fr).dash.lic;
  if (!lic) return `<div class="card-meta mt10 muted">${e(t.wait)}</div>`;
  const left = lic.until ? Math.ceil((lic.until - now) / 86_400_000) : null;
  const expired = lic.status === "expired" || (left !== null && left < 0);
  const cls = expired || lic.status === "suspended" || lic.status === "revoked" ? " bad" : left !== null && left <= 15 ? " warn" : "";
  const statMap = LIC_STATUS[lang] || LIC_STATUS.fr;
  const parts = [`<strong>${e(statMap[lic.status] || lic.status || "?")}</strong>`];
  if (lic.until) {
    const dStr = e(frDate(lic.until, lang));
    const dayLeftStr = left === 0 ? t.today : t.inDays(left);
    parts.push(expired ? t.expiredOn(dStr) : t.until(dStr, dayLeftStr));
  }
  if (lic.devices) parts.push(e(t.devices(lic.devices.used, lic.devices.max)));
  const r = lic.reseller;
  const contact = r ? [
    r.name ? `${e(t.reseller)} <strong>${e(r.name)}</strong>` : "",
    r.whatsapp && /^\+?[0-9 ]{6,20}$/.test(r.whatsapp) ? `<a href="https://wa.me/${e(r.whatsapp.replace(/[^0-9]/g, ""))}" target="_blank" rel="noopener noreferrer">WhatsApp</a>` : "",
    r.telegram && /^@?[A-Za-z0-9_]{4,32}$/.test(r.telegram) ? `<a href="https://t.me/${e(r.telegram.replace(/^@/, ""))}" target="_blank" rel="noopener noreferrer">Telegram</a>` : "",
  ].filter(Boolean).join(" · ") : "";
  const stale = now - lic.issuedAt > 2 * 86_400_000 ? ` <span class="muted">(${e(t.verifiedOn)} ${e(frDate(lic.issuedAt, lang))})</span>` : "";
  return `<div class="acct card-meta mt10${cls}">${e(t.prefix)}${parts.join(" · ")}${stale}${contact ? `<br/>${contact}` : ""}</div>`;
}

export function dashboardPage(n, { acct, providers, csrf, err, ok, admin = false, lang = "fr", url = "/", simkl = false, traktOn = true }) {
  const dict = SITE_DICT[lang] || SITE_DICT.fr;
  const dMsg = dict.dashMsg;
  const t = dict.dash;
  const csrfInput = `<input type="hidden" name="csrf" value="${e(csrf)}"/>`;
  const devices = acct.devices || [];
  const when = (ms) => (ms ? frDate(ms, lang) : "");
  const provRows = providers.map((p) => {
    const sh = sharing(p, devices, csrfInput, lang);
    const origin = p.originName ? t.srcs.addedFrom(p.originName === "dashboard" ? t.srcs.fromDash : e(p.originName)) : t.srcs.unknownOrigin;
    return `
<article class="card" data-q="${e(`${p.name} ${displayUrl(p.url)} ${p.kind}`.toLowerCase())}"><div class="card-top"><div class="badge-ico">${ico("list")}</div>
<div class="grow"><div class="card-title"><span class="kind">${e(p.kind)}</span>${e(p.name)}</div><div class="card-meta">${e(displayUrl(p.url))}${p.kind === "STALKER" ? ` · MAC ${e(maskMac(p.mac))}` : ""}</div></div></div>
${sh.chips}${p.managed === "reseller" ? `<div class="chips"><span class="chip acc">${e(t.srcs.resellerManaged)}</span></div>` : ""}
<div class="card-meta mt10">${origin}${p.createdAt ? t.srcs.onDate(when(p.createdAt)) : ""}${p.updatedAt && p.updatedAt > (p.createdAt || 0) + 60000 ? t.srcs.editedOn(when(p.updatedAt)) : ""}</div>
${xtreamCredsOf(p) ? `<div class="acct card-meta mt10" data-id="${e(p.id)}" aria-live="polite"><span class="muted">${e(t.srcs.subLoading)}</span></div>` : ""}
${p.kind === "STALKER" ? `<div class="card-meta mt10"><span class="muted">${e(t.srcs.stalkerSub)}</span></div>` : ""}
<div class="reveal-box mt10" id="lk-${e(p.id)}" hidden><label class="mt0" for="lki-${e(p.id)}">${e(t.srcs.iptvLink)}</label>
<div class="row"><input id="lki-${e(p.id)}" readonly spellcheck="false" autocomplete="off"/><button type="button" class="secondary copy-link" data-for="lki-${e(p.id)}">${e(t.srcs.copy)}</button></div></div>
<div class="actions">${sh.form}
${p.managed === "reseller"
    ? `<span class="muted small">${e(t.srcs.resellerCredsNote)}</span>`
    : `${MEDIA_KINDS.includes(p.kind)
    ? `<span class="muted small">${e(t.srcs.mediaNoLink)}</span>`
    : p.kind === "STALKER"
    ? `<span class="muted small">${e(t.srcs.stalkerNoLink)}</span>`
    : `<button type="button" class="secondary reveal-link" data-id="${e(p.id)}" aria-controls="lk-${e(p.id)}" aria-expanded="false">${e(t.srcs.showLink)}</button>`}
<form method="post" action="/providers/${e(p.id)}/delete" data-confirm="${e(t.srcs.delProvConfirm)}">${csrfInput}<button class="danger" type="submit">${e(t.srcs.deleteBtn)}</button></form>`}</div></article>`;
  }).join("");
  const devRows = devices.map((d) => `
<article class="card" data-q="${e(`${d.name} ${d.label || ""} ${d.edition || ""}`.toLowerCase())}"><div class="card-top"><div class="badge-ico">${ico("tv")}</div>
<div class="grow"><div class="card-title">${e(d.name)} ${d.edition === "pro" ? `<span class="kind pro">PRO</span>` : d.edition === "standard" ? `<span class="kind std">STANDARD</span>` : ""}</div><div class="card-meta">${d.label ? `${e(d.label)} · ` : ""}${d.version ? t.devs.versionLabel(d.version) : ""}${t.devs.pairedAgo(ago(d.createdAt, Date.now(), lang))}</div></div></div>
${d.edition === "pro" ? licenseLine(d.license, Date.now(), lang) : ""}
<div class="actions"><details><summary>${ico("edit")}${e(t.devs.rename)}</summary><form method="post" action="/devices/${e(d.id)}/rename" class="drop">${csrfInput}
<label class="mt0" for="rn-${e(d.id)}">${e(t.devs.nameLabel)}</label><input id="rn-${e(d.id)}" name="name" maxlength="40" value="${e(d.name)}"/>
<div class="row"><button type="submit">${e(t.devs.rename)}</button></div></form></details>
<form method="post" action="/devices/${e(d.id)}/revoke" data-confirm="${e(t.devs.revokeConfirm)}">${csrfInput}<button class="danger" type="submit">${e(t.devs.revoke)}</button></form></div></article>`).join("");
  const msg = (err && dMsg.err[err] && notice("err", dMsg.err[err])) || (ok && dMsg.ok[ok] && notice("ok", dMsg.ok[ok])) || "";

  const cJs = t.clientJs;
  const plexJs = `(function(){var go=document.getElementById('plex-go');if(!go)return;var st=document.getElementById('plex-st'),list=document.getElementById('plex-list'),add=document.getElementById('plex-add'),timer=null;
function post(p){return fetch(p,{method:'POST',credentials:'same-origin',headers:{'content-type':'application/x-www-form-urlencoded'},body:'csrf='+encodeURIComponent(${JSON.stringify(csrf)})}).then(function(r){if(!r.ok)throw new Error(String(r.status));return r.json();});}
function fail(){clearInterval(timer);timer=null;go.disabled=false;st.textContent=${JSON.stringify(t.add.mPlexFail)};}
function show(servers){clearInterval(timer);timer=null;go.disabled=false;list.querySelectorAll('label').forEach(function(l){l.remove();});
 if(!servers.length){st.textContent=${JSON.stringify(t.add.mPlexNone)};return;}st.textContent='';
 servers.forEach(function(s,i){var l=document.createElement('label'),r=document.createElement('input');r.type='radio';r.name='server';r.value=s.id;r.required=true;if(i===0)r.checked=true;l.appendChild(r);l.appendChild(document.createTextNode(' '+s.name+(s.owned?'':' ('+${JSON.stringify(t.add.mPlexShared)}+')')));list.appendChild(l);});
 list.hidden=false;add.hidden=false;}
go.addEventListener('click',function(){var w=window.open('about:blank','plexauth');go.disabled=true;st.textContent=${JSON.stringify(t.add.mPlexWaiting)};list.hidden=true;add.hidden=true;
 post('/plex/pin').then(function(j){if(!j.auth)throw new Error('pin');if(w)w.location=j.auth;else window.open(j.auth,'plexauth');var n=0;
  timer=setInterval(function(){if(++n>300)return fail();post('/plex/check').then(function(c){if(c.state==='ok')show(c.servers||[]);else if(c.state==='expired')fail();}).catch(function(){});},3000);
 }).catch(function(){if(w)w.close();fail();});});})();`;
  const script = `<script nonce="${n}">
${plexJs}
document.querySelectorAll('form[data-confirm]').forEach(function(f){f.addEventListener('submit',function(ev){if(!confirm(f.dataset.confirm))ev.preventDefault();});});
document.querySelectorAll('.share-form,.prof-form').forEach(function(f){var all=f.querySelector('input[name=all]'),ds=f.querySelectorAll('input[name=d],input[name=src]');
 all.addEventListener('change',function(){ds.forEach(function(d){d.checked=all.checked;});});
 ds.forEach(function(d){d.addEventListener('change',function(){all.checked=Array.prototype.every.call(ds,function(x){return x.checked;});});});});
document.querySelectorAll('.reveal-link').forEach(function(b){b.addEventListener('click',function(){
 var box=document.getElementById('lk-'+b.dataset.id);
 if(!box.hidden){box.hidden=true;box.querySelector('input').value='';b.setAttribute('aria-expanded','false');b.textContent=${JSON.stringify(cJs.showLink)};return;}
 b.disabled=true;
 fetch('/providers/'+b.dataset.id+'/link',{method:'POST',credentials:'same-origin',headers:{'content-type':'application/x-www-form-urlencoded'},body:'csrf='+encodeURIComponent(${JSON.stringify(csrf)})})
  .then(function(r){if(!r.ok)throw new Error(String(r.status));return r.json();})
  .then(function(j){var i=box.querySelector('input');i.value=j.link||'';box.hidden=false;b.setAttribute('aria-expanded','true');b.textContent=${JSON.stringify(cJs.hideLink)};i.focus();i.select();})
  .catch(function(){alert(${JSON.stringify(cJs.linkFail)});})
  .then(function(){b.disabled=false;});
});});
// Abonnement de chaque fournisseur Xtream : lu par le serveur (identifiants jamais exposés), un appel par carte.
(function(){
 var MOIS=${JSON.stringify(lang === "en" ? ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"] : lang === "es" ? ["ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "sep.", "oct.", "nov.", "dic."] : lang === "ar" ? ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"] : lang === "pl" ? ["sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru"] : ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."])};
 var dt=function(ms){var d=new Date(ms);return d.getDate()+' '+MOIS[d.getMonth()]+' '+d.getFullYear();};
 var ERR={unsupported:${JSON.stringify(cJs.errUnsupported)},port:${JSON.stringify(cJs.errPort("{port}"))},denied:${JSON.stringify(cJs.errDenied)},unreachable:${JSON.stringify(cJs.errUnreachable)}};
 var x=function(v){return String(v).replace(/[&<>"']/g,function(c){return '&#'+c.charCodeAt(0)+';';});};
 var set=function(el,html,cls){el.innerHTML=html;el.className='acct card-meta mt10'+(cls?' '+cls:'');};
 var total=document.querySelectorAll('.acct[data-id]').length,done=0,soon=0,expired=0,ok=0;
 var tally=function(){done++;var n=document.getElementById('sub-n'),l=document.getElementById('sub-l'),t=document.getElementById('sub-tile');if(!n)return;
  var of=done<total?(${JSON.stringify(cJs.tallyOf("{done}"))}.replace('{done}', done)):'';t.classList.remove('bad','warn');
  if(expired){n.textContent=String(expired);l.textContent=${JSON.stringify(cJs.tallyExpired("{count}", "{of}"))}.replace('{count}', expired).replace('{of}', of);t.classList.add('bad');}
  else if(soon){n.textContent=String(soon);l.textContent=${JSON.stringify(cJs.tallySoon("{count}", "{of}"))}.replace('{count}', soon).replace('{of}', of);t.classList.add('warn');}
  else if(ok){n.textContent=String(ok);l.textContent=${JSON.stringify(cJs.tallyActive("{count}", "{of}"))}.replace('{count}', ok).replace('{of}', of);}
  else if(done>=total){n.textContent='—';l.textContent=${JSON.stringify(cJs.subUnavail)};}};
 window.utvLoadAccounts=function(root){(root||document).querySelectorAll('.acct[data-id]:not([data-loaded])').forEach(function(el){
  if(el.closest('[hidden]'))return;el.setAttribute('data-loaded','1');
  fetch('/providers/'+el.dataset.id+'/account',{method:'POST',credentials:'same-origin',headers:{'content-type':'application/x-www-form-urlencoded'},body:'csrf='+encodeURIComponent(${JSON.stringify(csrf)})})
   .then(function(r){if(!r.ok)throw new Error(String(r.status));return r.json();})
   .then(function(a){
    if(a.error){var errMsg=a.error==='port'&&a.port?ERR.port.replace('{port}', x(a.port)):a.error==='unreachable'&&a.detail?x(a.detail):(ERR[a.error]||ERR.unreachable);set(el,'<span class="muted">'+${JSON.stringify(cJs.subPrefix)}+errMsg+'</span>');tally();return;}
    var parts=[],cls='';
    var st=(a.status||'').toLowerCase();
    var active=st==='active'||st==='';
    if(a.expiresAt){
     var days=Math.ceil((a.expiresAt-Date.now())/86400000);
     if(days<0){parts.push('<strong>'+${JSON.stringify(cJs.expired)}+'</strong> '+${JSON.stringify(cJs.expiresOn)}+' '+dt(a.expiresAt));cls='bad';}
     else{var dayStr=days===0?${JSON.stringify(cJs.today)}:(${JSON.stringify(cJs.inDays("{d}"))}.replace('{d}', days));parts.push((active?'<strong>'+${JSON.stringify(cJs.active)}+'</strong>':'<strong>'+x(a.status||'')+'</strong>')+' · '+${JSON.stringify(cJs.expiresOn)}+' '+dt(a.expiresAt)+' ('+dayStr+')');if(days<=15)cls='warn';}
    }else parts.push((active?'<strong>'+${JSON.stringify(cJs.active)}+'</strong>':'<strong>'+x(a.status||'')+'</strong>')+' · '+${JSON.stringify(cJs.noExpiry)});
    if(!active&&cls!=='bad')cls='bad';
    if(a.maxCons!=null)parts.push((a.activeCons!=null?a.activeCons+'/':'')+a.maxCons+${JSON.stringify(cJs.connections(2))});
    if(a.trial)parts.push(${JSON.stringify(cJs.trial)});
    if(a.createdAt)parts.push(${JSON.stringify(cJs.createdOn)}+' '+dt(a.createdAt));
    set(el,${JSON.stringify(cJs.subPrefix)}+parts.join(' · '),cls);
    if(cls==='bad')expired++;else if(cls==='warn')soon++;else ok++;
    if(a.expiresAt&&a.createdAt&&a.expiresAt>a.createdAt){var m=document.createElement('div');m.className='meter';var i=document.createElement('i');m.appendChild(i);el.appendChild(m);
     var f=Math.min(1,Math.max(0,(Date.now()-a.createdAt)/(a.expiresAt-a.createdAt)));requestAnimationFrame(function(){i.style.width=Math.round(f*100)+'%';});}
    tally();
   })
   .catch(function(e){var c=e&&/^\\d+$/.test(e.message)?e.message:'';set(el,'<span class="muted">'+${JSON.stringify(cJs.subPrefix)}+(c==='429'?${JSON.stringify(cJs.errRate)}:c?(${JSON.stringify(cJs.errSite("{c}"))}.replace('{c}', c)):ERR.unreachable)+'</span>');tally();});
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
   pg.hidden=hits.length<=SIZE;info.textContent=hits.length?(${JSON.stringify(cJs.pageInfo("{p}", "{max}", "{total}"))}.replace('{p}', page+1).replace('{max}', pages).replace('{total}', hits.length)):${JSON.stringify(cJs.noResults)};
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
 var done=function(){c.textContent=${JSON.stringify(cJs.copied)};setTimeout(function(){c.textContent=${JSON.stringify(cJs.copy)};},1500);};
 if(navigator.clipboard){navigator.clipboard.writeText(i.value).then(done,function(){document.execCommand('copy');done();});}else{document.execCommand('copy');done();}
});});
${PAIR_JS}
${scanJs(lang)}
</script>`;
  const xtreamCount = providers.filter((p) => xtreamCredsOf(p)).length;
  const proDevices = devices.filter((d) => d.edition === "pro");
  const isPro = proDevices.length > 0;
  const nextLic = proDevices.map((d) => d.license?.until).filter((u) => typeof u === "number").sort((a, b) => a - b)[0];
  const listbar = (id, what) => `<div class="listbar" data-list="${id}" hidden><input type="search" class="lsearch" placeholder="${e(what)}" aria-label="${e(what)}" autocomplete="off"/><span class="pager"><button type="button" class="secondary pprev" aria-label="${e(t.prevPage)}">‹</button><span class="pinfo" aria-live="polite"></span><button type="button" class="secondary pnext" aria-label="${e(t.nextPage)}">›</button></span></div>`;
  const step = (done, text) => `<li class="${done ? "done" : ""}">${done ? `<span class="vh">${e(t.doneLbl)}</span>` : ""}${text}</li>`;
  const gettingStarted = !devices.length || !providers.length
    ? `<section class="panel" aria-labelledby="h-start"><h2 id="h-start">${e(t.gettingStarted.title)}</h2><p class="sub">${e(t.gettingStarted.sub)}</p>
<ol class="steps">${step(devices.length > 0, t.gettingStarted.stepDev)}${step(providers.length > 0, t.gettingStarted.stepSrc)}</ol></section>`
    : "";
  return shell(n, { title: t.title(acct.login), ctx: "user", spy: true, acct, admin, csrfInput, camera: true, extra: script, loc: { lang, url }, body: `
${msg}
<section id="apercu" class="hello" aria-labelledby="h-hello"><h1 id="h-hello">${e(t.hello(acct.login))}${isPro ? ` <span class="kind pro">PRO</span>` : ""}</h1><p class="sub">${isPro ? t.subPro(proDevices.length) : e(t.sub)}</p>
<div class="tiles">
<div class="tile hero">${t.tileDevs(devices.length)}</div>
<div class="tile">${t.tileSrcs(providers.length)}</div>
${isPro ? `<div class="tile${nextLic && nextLic < Date.now() ? " bad" : nextLic && nextLic - Date.now() < 15 * 86_400_000 ? " warn" : ""}"><b>${nextLic ? e(frDate(nextLic, lang)) : "—"}</b><span>${nextLic ? e(t.tileLicNext) : e(t.tileLicWait)}</span></div>` : ""}
<div class="tile" id="sub-tile"><b id="sub-n">${xtreamCount ? "…" : "—"}</b><span id="sub-l">${xtreamCount ? e(t.tileSubChecking) : e(t.tileSubNone)}</span></div>
</div>
${gettingStarted}</section>

<section class="panel" id="soutenir" aria-labelledby="h-support"><h2 id="h-support">${e(t.support.title)}</h2><p class="sub">${e(t.support.sub)}</p>
<div class="row"><a class="button" href="${e(KOFI_URL)}" target="_blank" rel="noopener noreferrer">${e(t.support.kofi)}</a><a class="button alt-btn" href="${e(LIBERAPAY_URL)}" target="_blank" rel="noopener noreferrer">${e(t.support.liberapay)}</a></div></section>
<div class="section-h" id="appareils"><div class="sh-t"><h2 id="h-dev">${e(t.devs.title)} <span class="count">${devices.length}</span></h2><p class="muted small">${e(t.devs.sub)}</p></div></div>
<section class="panel" id="appairer" aria-labelledby="h-pair"><h2 id="h-pair">${e(t.devs.pairTitle)}</h2><p class="sub">${e(t.devs.pairSub)}</p>
<ol class="steps"><li>${e(t.devs.pairStep1)}</li><li>${e(t.devs.pairStep2)}</li><li>${e(t.devs.pairStep3)}</li></ol>
${pairForm(csrfInput, "", lang)}</section>
${listbar("dev-list", cJs.searchDevPh)}
<div class="cards c2 mt16" id="dev-list">${devRows || `<div class="empty"><div class="badge-ico">${ico("tv")}</div><strong>${e(t.devs.emptyTitle)}</strong><span class="small">${e(t.devs.emptySub)}</span></div>`}</div>

<div class="section-h" id="sources"><div class="sh-t"><h2 id="h-src">${e(t.srcs.title)} <span class="count">${providers.length}</span></h2><p class="muted small">${e(t.srcs.sub)}</p></div><a class="button secondary" href="#ajouter">${ico("plus")}${e(t.srcs.addBtn)}</a></div>
${listbar("prov-list", cJs.searchSrcPh)}
<div class="cards c2" id="prov-list">${provRows || `<div class="empty"><div class="badge-ico">${ico("list")}</div><strong>${e(t.srcs.emptyTitle)}</strong><span class="small">${e(t.srcs.emptySub)}</span></div>`}</div>
${profilesPanel(acct, providers, csrfInput, lang)}
<section class="panel mt16" id="ajouter"aria-labelledby="h-add"><h2 id="h-add">${e(t.add.title)}</h2><p class="sub">${e(t.add.sub)}</p>
<div class="tabbed"><input type="radio" name="tab" id="t-xtream" checked aria-label="${e(t.add.tabXtream)}"/><input type="radio" name="tab" id="t-m3u" aria-label="${e(t.add.tabM3u)}"/><input type="radio" name="tab" id="t-stalker" aria-label="${e(t.add.tabStalker)}"/><div class="seg" aria-hidden="true"><label for="t-xtream">${e(t.add.tabXtream)}</label><label for="t-m3u">${e(t.add.tabM3u)}</label><label for="t-stalker">${e(t.add.tabStalker)}</label></div>
<form method="post" action="/providers" id="form-xtream">${csrfInput}<input type="hidden" name="kind" value="XTREAM"/>
<label for="xn">${e(t.add.nameLabel)} <span class="hint">${e(t.add.optional)}</span></label><input id="xn" name="name" maxlength="64" placeholder="${e(t.add.namePhXtream)}"/>
<label for="xu">${e(t.add.urlXtream)}</label><input id="xu" name="url" type="url" required maxlength="2048" placeholder="http://provider.com:8080" autocapitalize="none" spellcheck="false"/>
<label for="xl">${e(t.add.userLabel)}</label><input id="xl" name="username" required maxlength="256" autocomplete="off" autocapitalize="none" spellcheck="false"/>
<label for="xp">${e(t.add.passLabel)}</label><input id="xp" name="password" type="password" required maxlength="256" autocomplete="off"/>
<div class="row"><button type="submit">${ico("plus")}${e(t.add.addSubmit)}</button></div></form>
<form method="post" action="/providers" id="form-m3u">${csrfInput}<input type="hidden" name="kind" value="M3U"/>
<label for="mn">${e(t.add.nameLabel)} <span class="hint">${e(t.add.optional)}</span></label><input id="mn" name="name" maxlength="64" placeholder="${e(t.add.namePhM3u)}"/>
<label for="mu">${e(t.add.urlM3u)}</label><input id="mu" name="url" type="url" required maxlength="2048" placeholder="https://exemple.com/liste.m3u" autocapitalize="none" spellcheck="false"/>
<div class="row"><button type="submit">${ico("plus")}${e(t.add.addSubmit)}</button></div></form>
<form method="post" action="/providers" id="form-stalker">${csrfInput}<input type="hidden" name="kind" value="STALKER"/>
<label for="sn">${e(t.add.nameLabel)} <span class="hint">${e(t.add.optional)}</span></label><input id="sn" name="name" maxlength="64" placeholder="${e(t.add.namePhStalker)}"/>
<label for="su">${e(t.add.urlStalker)}</label><input id="su" name="url" type="url" required maxlength="2048" placeholder="http://portail.tv:8080/c/" autocapitalize="none" spellcheck="false"/>
<label for="sm">${e(t.add.macLabel)}</label><input id="sm" name="mac" required maxlength="17" pattern="[0-9A-Fa-f]{2}([:\\-]?[0-9A-Fa-f]{2}){5}" placeholder="00:1A:79:XX:XX:XX" autocomplete="off" autocapitalize="characters" spellcheck="false"/>
<label for="sl">${e(t.add.stalkerUser)} <span class="hint">${e(t.add.optional)}</span></label><input id="sl" name="username" maxlength="256" autocomplete="off" autocapitalize="none" spellcheck="false"/>
<label for="sp">${e(t.add.stalkerPass)} <span class="hint">${e(t.add.optional)}</span></label><input id="sp" name="password" type="password" maxlength="256" autocomplete="off"/>
<div class="row"><button type="submit">${ico("plus")}${e(t.add.addSubmit)}</button></div></form></div>
<p class="secure">${ico("lock")}<span>${e(t.add.secureNote)}</span></p></section>

<section class="panel mt16" id="serveurs" aria-labelledby="h-media"><h2 id="h-media">${e(t.add.mTitle)}</h2><p class="sub">${e(t.add.mSub)}</p>
<div class="tabbed"><input type="radio" name="mtab" id="t-jf" checked aria-label="Jellyfin"/><input type="radio" name="mtab" id="t-emby" aria-label="Emby"/><input type="radio" name="mtab" id="t-plex" aria-label="Plex"/><input type="radio" name="mtab" id="t-nas" aria-label="NAS"/><div class="seg" aria-hidden="true"><label for="t-jf">Jellyfin</label><label for="t-emby">Emby</label><label for="t-plex">Plex</label><label for="t-nas">NAS</label></div>
${["JELLYFIN", "EMBY"].map((k) => { const f = k === "JELLYFIN" ? "jf" : "emby"; return `<form method="post" action="/providers" id="form-${f}">${csrfInput}<input type="hidden" name="kind" value="${k}"/>
<label for="${f}n">${e(t.add.nameLabel)} <span class="hint">${e(t.add.optional)}</span></label><input id="${f}n" name="name" maxlength="64" placeholder="${k === "JELLYFIN" ? "Jellyfin" : "Emby"}"/>
<label for="${f}u">${e(t.add.mServerUrl)}</label><input id="${f}u" name="url" type="url" required maxlength="2048" placeholder="http://192.168.1.20:${k === "JELLYFIN" ? "8096" : "8096"}" autocapitalize="none" spellcheck="false"/>
<label for="${f}l">${e(t.add.userLabel)}</label><input id="${f}l" name="username" maxlength="256" autocomplete="off" autocapitalize="none" spellcheck="false"/>
<label for="${f}p">${e(t.add.passLabel)}</label><input id="${f}p" name="password" type="password" maxlength="256" autocomplete="off"/>
<label for="${f}k">${e(t.add.mApiKey)} <span class="hint">${e(t.add.mApiKeyHint)}</span></label><input id="${f}k" name="apikey" maxlength="256" autocomplete="off" autocapitalize="none" spellcheck="false"/>
<div class="row"><button type="submit">${ico("plus")}${e(t.add.addSubmit)}</button></div></form>`; }).join("")}
<form method="post" action="/providers" id="form-plex">${csrfInput}<input type="hidden" name="kind" value="PLEX"/>
<p class="muted small">${e(t.add.mPlexIntro)}</p>
<div class="row"><button type="button" class="secondary" id="plex-go">${e(t.add.mPlexConnect)}</button></div>
<p class="muted small" id="plex-st" aria-live="polite"></p>
<fieldset class="plex-servers" id="plex-list" hidden><legend>${e(t.add.mPlexPick)}</legend></fieldset>
<div class="row" id="plex-add" hidden><button type="submit">${ico("plus")}${e(t.add.addSubmit)}</button></div></form>
<form method="post" action="/providers" id="form-nas">${csrfInput}<input type="hidden" name="kind" value="NAS"/>
<label for="nn">${e(t.add.nameLabel)} <span class="hint">${e(t.add.optional)}</span></label><input id="nn" name="name" maxlength="64" placeholder="NAS"/>
<label for="nu">${e(t.add.mNasAddr)}</label><input id="nu" name="url" required maxlength="1024" placeholder="smb://192.168.1.10/Films" autocapitalize="none" spellcheck="false"/>
<label for="nl">${e(t.add.userLabel)} <span class="hint">${e(t.add.mNasGuest)}</span></label><input id="nl" name="username" maxlength="256" autocomplete="off" autocapitalize="none" spellcheck="false"/>
<label for="np">${e(t.add.passLabel)}</label><input id="np" name="password" type="password" maxlength="256" autocomplete="off"/>
<div class="row"><button type="submit">${ico("plus")}${e(t.add.addSubmit)}</button></div></form></div>
<p class="secure">${ico("lock")}<span>${e(t.add.mNote)}</span></p></section>

<div class="section-h" id="integrations"><span id="compte" class="anchor"></span><div class="sh-t"><h2>${e(t.integ.title)}</h2><p class="muted small">${e(t.integ.sub)}</p></div></div>
<div class="two">
${trackingPanel(acct, csrfInput, lang, simkl, traktOn)}
${osPanel(acct.os, csrfInput, lang)}
</div>

<div class="section-h" id="securite"><div class="sh-t"><h2>${e(t.sec.title)}</h2><p class="muted small">${e(t.sec.sub)}</p></div></div>
<div class="two">
<section class="panel" aria-labelledby="h-pw"><h2 id="h-pw">${e(t.sec.pwTitle)}</h2>
<form method="post" action="/password">${csrfInput}
<label for="cur">${e(t.sec.curPw)}</label><input id="cur" name="current" type="password" required autocomplete="current-password"/>
<label for="npw">${e(t.sec.newPw)} <span class="hint">${e(t.sec.pwHint)}</span></label><input id="npw" name="password" type="password" required minlength="10" maxlength="200" autocomplete="new-password"/>
<div class="row"><button type="submit">${e(t.sec.updatePw)}</button></div></form></section>
<section class="panel danger-zone" aria-labelledby="h-dz"><h2 id="h-dz">${e(t.sec.dzTitle)}</h2><p class="muted small mt6">${e(t.sec.dzSub)}</p>
<form method="post" action="/account/delete" data-confirm="${e(t.sec.delConfirm)}">${csrfInput}
<label for="dpw">${e(dict.login.passLabel)}</label><input id="dpw" name="password" type="password" required autocomplete="current-password"/>
<div class="row"><button class="danger" type="submit">${e(t.sec.delBtn)}</button></div></form></section>
</div>` });
}

/**
 * Profils partagés du compte (acct.profiles, synchronisés avec les appareils) : pour chacun, les sources visibles.
 * Champ `s` absent = toutes les sources (« Toutes » coché). Rien n'est affiché sans profil ni source.
 */
export function profilesPanel(acct, providers, csrfInput, lang = "fr") {
  const t = (SITE_DICT[lang] || SITE_DICT.fr).dash.profs;
  const profs = (acct.profiles || []).filter((p) => p.on !== false);
  const names = [...new Set(providers.map((p) => p.name))];
  if (!profs.length || !names.length) return "";
  const items = profs.map((p, i) => {
    const all = !Array.isArray(p.s) || !p.s.length;
    const chosen = all ? names : p.s.filter((x) => names.includes(x));
    const summary = all ? t.all : chosen.length ? chosen.map(e).join(", ") : t.none;
    const picks = names.map((nm) => `<label class="pick"><input type="checkbox" name="src" value="${e(nm)}"${all || p.s.includes(nm) ? " checked" : ""}/>${e(nm)}</label>`).join("");
    return `<article class="card"><div class="card-top"><div class="badge-ico">${ico("user")}</div>
<div class="grow"><div class="card-title">${e(p.n)}${p.k ? ` <span class="kind std">${e(t.kids)}</span>` : ""}</div><div class="card-meta">${t.visible} ${summary}</div></div></div>
<div class="actions"><details><summary>${ico("edit")}${e(t.edit)}</summary><form method="post" action="/profiles/sources" class="drop prof-form">${csrfInput}<input type="hidden" name="name" value="${e(p.n)}"/>
<div class="picks"><label class="pick all"><input type="checkbox" name="all" value="1"${all ? " checked" : ""} id="pa-${i}"/>${e(t.allBox)}</label>${picks}</div>
<div class="row"><button type="submit">${e(t.save)}</button></div></form></details></div></article>`;
  }).join("");
  return `<div class="section-h" id="profils"><div class="sh-t"><h2 id="h-prof">${e(t.title)} <span class="count">${profs.length}</span></h2><p class="muted small">${e(t.sub)}</p></div></div>
<div class="cards c2">${items}</div>`;
}

/**
 * Service de suivi : UNE carte. En tête le service actuellement actif (choix explicite, sinon Trakt s'il est relié,
 * sinon Simkl : même règle que activeTracker dans tracking.js) ; une ligne par service (état + Connecter/Déconnecter) ;
 * puis le choix du service actif. Un service non relié reste choisissable (mention « à relier »).
 */
export function trackingPanel(acct, csrfInput, lang, simkl, traktOn) {
  const t = (SITE_DICT[lang] || SITE_DICT.fr).dash.integ;
  const linked = { trakt: !!(traktOn && acct.traktEnc), simkl: !!(simkl && acct.simklEnc) };
  const explicit = acct.tracking === "none" || acct.tracking === "simkl" || acct.tracking === "trakt" ? acct.tracking : null;
  const active = explicit === "none" ? null
    : explicit ? (linked[explicit] ? explicit : null)
      : linked.trakt ? "trakt" : linked.simkl ? "simkl" : null;
  // Choix affiché dans les boutons radio : l'explicite, sinon le service actif par défaut, sinon Aucun.
  const choice = explicit || active || "none";
  const names = { trakt: "Trakt", simkl: "Simkl" };
  const users = { trakt: acct.trakt && acct.trakt.user, simkl: acct.simkl && acct.simkl.user };
  const status = (k) => (linked[k] ? (users[k] ? t.trackLinkedUser(users[k]) : t.trackLinked) : t.trackNotLinked);
  const now = choice === "none" ? `<strong>${e(t.trackNone)}</strong>` : `<strong>${names[choice]}</strong> (${e(status(choice))})`;
  const note = choice !== "none" && !linked[choice] ? t.trackPending(names[choice]) : explicit ? "" : t.trackDefaultNote;
  const row = (k, revoked, desc, descOn) => {
    const l = linked[k];
    return `<div class="svc"><div class="svc-h"><strong>${names[k]}</strong><span class="chip${l ? " all" : ""}">${e(status(k))}</span>
<form method="post" action="/${k}/${l ? "disconnect" : "connect"}">${csrfInput}<button${l ? ' class="secondary"' : ""} type="submit">${e(t[`${k}${l ? "Disconnect" : "Connect"}`])}</button></form></div>
<p class="muted small">${revoked && !l ? e(t[`${k}RevokedMsg`]) : ""}${e(l ? descOn : desc)}</p></div>`;
  };
  const services = [
    ...(traktOn ? [["trakt", acct.trakt]] : []),
    ...(simkl ? [["simkl", acct.simkl]] : []),
  ];
  const radios = [...services.map(([k]) => [k, names[k] + (linked[k] ? "" : ` · ${t.trackToLink}`)]), ["none", t.trackNone]];
  return `<section class="panel track-panel" aria-labelledby="h-track"><h2 id="h-track">${e(t.trackTitle)}</h2>
<p class="track-now" aria-live="polite">${e(t.trackNow)} ${now}</p>${note ? `<p class="muted small">${e(note)}</p>` : ""}
<p class="muted small mt6">${e(t.trackDesc)}</p>
<div class="svcs">${services.map(([k, o]) => row(k, !!(o && o.revoked), t[`${k}DescConnect`], t[`${k}DescConnected`])).join("")}</div>
<form method="post" action="/tracking/service" class="track-form">${csrfInput}
<fieldset class="picks track-picks"><legend class="lbl">${e(t.trackActive)}</legend>${radios.map(([v, l]) => `<label class="pick"><input type="radio" name="service" value="${v}"${v === choice ? " checked" : ""}/>${e(l)}</label>`).join("")}</fieldset>
<div class="row"><button type="submit">${e(t.trackSave)}</button></div></form>
<p class="secure">${ico("lock")}<span>${e(t.trackSecure)}</span></p></section>`;
}

/**
 * Compte OpenSubtitles du client (facultatif) : sans lui, les sous-titres passent par la clé commune (quota partagé) ;
 * avec lui, par SON quota. Le mot de passe n'est jamais réaffiché.
 */
function osPanel(os, csrfInput, lang = "fr") {
  const dict = SITE_DICT[lang] || SITE_DICT.fr;
  const t = dict.dash.integ;
  const quota = (o) => [o.vip ? "VIP" : o.level ? e(o.level) : "", o.allowed != null ? t.osDownloadsDay(e(o.allowed)) : ""].filter(Boolean).join(" · ");
  return `<section class="panel" aria-labelledby="h-os"><h2 id="h-os">${e(t.osTitle)} <span class="hint">${e(t.osOptional)}</span></h2>
${os ? `<p class="mt6">${t.osConnected(os.user, quota(os))}</p>
<p class="muted small mt6">${e(t.osDescConnected)}</p>
<form method="post" action="/subtitles/unlink">${csrfInput}<div class="row"><button class="secondary" type="submit">${e(t.osUnlink)}</button></div></form>`
    : `<p class="muted small mt6">${t.osDescConnect}</p>
<form method="post" action="/subtitles/link">${csrfInput}
<label for="osu">${e(t.osUserLabel)}</label><input id="osu" name="os_user" required maxlength="100" autocomplete="off"/>
<label for="osp">${e(t.osPassLabel)}</label><input id="osp" name="os_pass" type="password" required maxlength="200" autocomplete="off"/>
<div class="row"><button type="submit">${e(t.osLink)}</button></div></form>
<p class="secure">${ico("lock")}<span>${e(t.osSecure)}</span></p>`}</section>`;
}
