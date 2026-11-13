// Pages publiques : connexion, inscription, envoi d'une source depuis le téléphone, choix de l'APK.
import { escapeHtml as e } from "../http.js";
import { ENVOYER_COOKIE, ENVOYER_DICT, ENVOYER_DIR, ENVOYER_LANGS, ENVOYER_LANG_NAMES } from "../i18n/envoyer.js";
import { ico, jsonForScript, notice } from "../ui/components.js";
import { DONATE_DICT, DONATE_PRESETS, DONATE_MIN, DONATE_MAX } from "../donate.js";
import { shell } from "../ui/layout.js";

const AUTH_ERR = {
  pw: "Identifiant ou mot de passe incorrect.",
  login: "Identifiant invalide (3 à 64 caractères : lettres, chiffres, . _ @ : + -).",
  short: "Mot de passe trop court (10 caractères minimum).",
  mismatch: "Les deux mots de passe ne correspondent pas.",
  taken: "Cet identifiant n'est pas disponible.",
};

const authErr = (err) => (err && AUTH_ERR[err] ? notice("err", AUTH_ERR[err]) : "");

const nextField = (next) => (next && next !== "/" ? `<input type="hidden" name="next" value="${e(next)}"/>` : "");
const nextQ = (next) => (next && next !== "/" ? `?next=${encodeURIComponent(next)}` : "");

export function loginPage(n, err, next = "/") {
  return shell(n, { title: "Ultra TV — connexion", ctx: "public", active: "/login", width: "narrow", body: `
<div class="panel"><h1>Connexion</h1><p class="sub">Retrouve ta configuration et tes appareils.</p>
${authErr(err)}
<form method="post" action="/login">${nextField(next)}
<label for="login">Identifiant <span class="hint">(ou ton ancienne adresse MAC)</span></label>
<input id="login" name="login" required autocomplete="username" autocapitalize="none" spellcheck="false" autofocus />
<label for="pw">Mot de passe</label>
<input id="pw" name="password" type="password" required autocomplete="current-password" />
<div class="row"><button class="block" type="submit">Se connecter</button></div>
</form></div>
<p class="alt">Pas encore de compte ? <a href="/signup${e(nextQ(next))}">Créer un compte</a></p>` });
}


export function signupPage(n, err, next = "/") {
  return shell(n, { title: "Ultra TV — inscription", ctx: "public", active: "/signup", width: "narrow", body: `
<div class="panel"><h1>Créer un compte</h1><p class="sub">Une seule configuration pour toutes tes TV.</p>
${authErr(err)}
<form method="post" action="/signup">${nextField(next)}
<label for="login">Identifiant <span class="hint">(3 à 64 caractères)</span></label>
<input id="login" name="login" required autocomplete="username" autocapitalize="none" spellcheck="false" minlength="3" maxlength="64" />
<label for="pw">Mot de passe <span class="hint">(10 caractères minimum)</span></label>
<input id="pw" name="password" type="password" required minlength="10" maxlength="200" autocomplete="new-password" />
<label for="pw2">Confirmer le mot de passe</label>
<input id="pw2" name="confirm" type="password" required minlength="10" maxlength="200" autocomplete="new-password" />
<div class="note">${ico("info")}<span>L'adresse MAC de ta box n'est plus un identifiant : ton appareil s'associe à ton compte avec un code affiché sur la TV.</span></div>
<div class="row"><button class="block" type="submit">Créer le compte</button></div>
</form></div>
<p class="alt">Déjà inscrit ? <a href="/login${e(nextQ(next))}">J'ai déjà un compte</a></p>` });
}


const envoiJs = (t) => `(function(){
var T=${jsonForScript(t)};
var f=document.getElementById('envoi-form');if(!f)return;
var kind=document.getElementById('kind'),code=document.getElementById('code'),msg=document.getElementById('envoi-msg'),btn=document.getElementById('send');
var groups={XTREAM:['g-url','g-user','g-pass'],M3U:['g-url'],STALKER:['g-url','g-mac'],GUIDE:['g-url']};
var ALL=['g-url','g-user','g-pass','g-mac'];
var LABEL={XTREAM:T.lblXtream,M3U:T.lblM3u,STALKER:T.lblStalker,GUIDE:T.lblGuide};
var HOLD={XTREAM:'http://server:8080',M3U:'http://example.com/list.m3u',STALKER:'http://portal:8080/c/',GUIDE:'https://example.com/guide.xml.gz'};
function apply(){var k=kind.value,on=groups[k]||[];
ALL.forEach(function(id){var g=document.getElementById(id),show=on.indexOf(id)>=0;g.hidden=!show;g.querySelectorAll('input').forEach(function(i){i.disabled=!show;});});
document.getElementById('url').placeholder=HOLD[k];document.getElementById('l-url').firstChild.nodeValue=LABEL[k];}
kind.addEventListener('change',apply);apply();
document.getElementById('show-pw').addEventListener('change',function(e){document.getElementById('pass').type=e.target.checked?'text':'password';});
code.addEventListener('input',function(){var p=code.selectionStart;code.value=code.value.toUpperCase().replace(/[^A-Z0-9]/g,'').slice(0,6);});
function say(cls,t){msg.className='notice '+cls;msg.textContent=t;msg.hidden=false;}
var ERR={code:T.errCode,used:T.errUsed,url:T.errUrl,creds:T.errCreds,mac:T.errMac,kind:T.errKind,guide:T.errGuide};
function okUrl(v){try{var u=new URL(v);return (u.protocol==='http:'||u.protocol==='https:')&&!!u.hostname;}catch(e){return false;}}
f.addEventListener('submit',function(ev){ev.preventDefault();msg.hidden=true;
var k=kind.value,c=code.value.trim(),url=document.getElementById('url').value.trim();
if(c.length!==6){say('err',ERR.code);return;}
if(!okUrl(url)){say('err',ERR.url);return;}
if(k==='GUIDE'&&!/\\.xml(\\.gz)?$/i.test(new URL(url).pathname)){say('err',ERR.guide);return;}
var body={code:c,kind:k,name:document.getElementById('name').value.trim(),url:url};
if(k==='XTREAM'){body.username=document.getElementById('user').value.trim();body.password=document.getElementById('pass').value;if(!body.username||!body.password){say('err',ERR.creds);return;}}
if(k==='STALKER'){body.mac=document.getElementById('mac').value.trim();if(!/^([0-9A-Fa-f]{2}[:-]?){5}[0-9A-Fa-f]{2}$/.test(body.mac)){say('err',ERR.mac);return;}}
btn.disabled=true;
fetch('/api/envoi/send',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body),credentials:'omit'})
.then(function(r){return r.json().catch(function(){return {};}).then(function(j){return {s:r.status,j:j};});})
.then(function(x){btn.disabled=false;
if(x.s===200&&x.j.ok){f.hidden=true;document.getElementById('envoi-done').hidden=false;msg.hidden=true;return;}
if(x.s===429){say('err',T.errRate);return;}
say('err',ERR[x.j.error]||T.errFail);})
.catch(function(){btn.disabled=false;say('err',T.errNet);});});
})();`;

// Sélecteur de langue : mémorise le choix dans un cookie (1 an, limité à /envoyer) puis laisse le lien recharger la page.
const ENVOI_LANG_JS = `(function(){var C=${jsonForScript(ENVOYER_COOKIE)};
document.querySelectorAll('a[data-lang]').forEach(function(a){a.addEventListener('click',function(){
try{document.cookie=C+'='+a.getAttribute('data-lang')+'; Path=/envoyer; Max-Age=31536000; SameSite=Lax; Secure';}catch(e){}});});})();`;

/**
 * « Envoyer une playlist à ma TV » : formulaire public (code de la TV + source), dans la langue `lang`
 * (en/fr/es/ar/pl, déjà résolue par pickEnvoyerLang ; inconnue → anglais). `code` : celui du QR, déjà normalisé ou null.
 */
export function envoyerPage(n, { code = null, lang = "en" } = {}) {
  if (!ENVOYER_LANGS.includes(lang)) lang = "en";
  const t = ENVOYER_DICT[lang];
  const dir = ENVOYER_DIR[lang];
  const langLinks = ENVOYER_LANGS.map((l) => {
    const href = `/envoyer?${code ? `c=${encodeURIComponent(code)}&` : ""}l=${l}`;
    return `<a href="${e(href)}" data-lang="${l}" lang="${l}" hreflang="${l}"${l === lang ? ` aria-current="true"` : ""}>${e(ENVOYER_LANG_NAMES[l])}</a>`;
  }).join("");
  const res = shell(n, { title: t.title, ctx: "public", width: "narrow", body: `
<div class="panel"><h1>${e(t.h1)}</h1><p class="sub">${e(t.sub)}</p>
<ol class="steps" aria-label="${e(t.stepsLabel)}"><li>${e(t.step1)}</li><li>${e(t.step2)}</li><li>${e(t.step3)}</li></ol>
<div id="envoi-msg" class="notice err mt16" role="alert" hidden></div>
<div id="envoi-done" class="mt16" hidden>${notice("ok", t.done)}</div>
<form id="envoi-form" novalidate autocomplete="off">
<label for="code">${e(t.codeLabel)} <span class="hint">${e(t.codeHint)}</span></label>
<input id="code" name="code" dir="ltr" required maxlength="6" value="${e(code || "")}" autocapitalize="characters" autocomplete="off" spellcheck="false" inputmode="text" placeholder="K7Q2M9"/>
<label for="kind">${e(t.kindLabel)}</label>
<select id="kind" name="kind"><option value="XTREAM">${e(t.kindXtream)}</option><option value="M3U">${e(t.kindM3u)}</option><option value="STALKER">${e(t.kindStalker)}</option><option value="GUIDE">${e(t.kindGuide)}</option></select>
<label for="name">${e(t.nameLabel)} <span class="hint">${e(t.optional)}</span></label>
<input id="name" name="name" maxlength="64" placeholder="${e(t.namePh)}"/>
<div id="g-url"><label for="url" id="l-url">${e(t.urlLabel)}</label>
<input id="url" name="url" dir="ltr" type="url" required maxlength="2048" autocapitalize="none" autocomplete="off" spellcheck="false" placeholder="http://server:8080"/></div>
<div id="g-user"><label for="user">${e(t.userLabel)}</label>
<input id="user" name="username" dir="ltr" maxlength="256" autocapitalize="none" autocomplete="off" spellcheck="false"/></div>
<div id="g-pass"><label for="pass">${e(t.passLabel)}</label>
<input id="pass" name="password" dir="ltr" type="password" maxlength="256" autocapitalize="none" autocomplete="off" spellcheck="false"/>
<label class="row" for="show-pw"><input id="show-pw" type="checkbox"/> <span>${e(t.showPw)}</span></label></div>
<div id="g-mac" hidden><label for="mac">${e(t.macLabel)}</label>
<input id="mac" name="mac" dir="ltr" maxlength="17" autocapitalize="characters" autocomplete="off" spellcheck="false" placeholder="00:1A:79:XX:XX:XX"/></div>
<div class="row"><button id="send" class="block" type="submit">${e(t.send)}</button></div>
</form>
<noscript><p class="note">${e(t.noscript)}</p></noscript>
<p class="note">${e(t.note)}</p>
</div>`,
    extra: `<script nonce="${n}">${envoiJs(t)}</script><script nonce="${n}">${ENVOI_LANG_JS}</script>`,
    loc: { lang, dir, skip: t.skip, themeBtn: t.themeBtn, themeLabels: { auto: t.themeAuto, light: t.themeLight, dark: t.themeDark }, themeChange: t.themeChange,
      foot: { github: t.footGithub, privacy: t.footPrivacy, navLabel: t.langLabel, langs: `<nav class="langs" aria-label="${e(t.langLabel)}">${langLinks}</nav>` } } });
  // La page dépend de l'URL, du cookie de préférence et d'Accept-Language : les caches doivent le savoir.
  res.headers.append("vary", "Cookie, Accept-Language");
  return res;
}


export function apkPage(n) {
  const opt = (href, title, size, note, primary = false) =>
    `<a class="${primary ? "button" : "button alt-btn"} block" href="${e(href)}"${primary ? " autofocus" : ""}><span><strong>${e(title)}</strong> · ${e(size)}<br/><small>${e(note)}</small></span></a>`;
  return shell(n, { title: "Ultra TV — install", ctx: "public", width: "narrow", body: `
<div class="panel"><h1>Install Ultra TV</h1><p class="sub">Pick the version for your device. Later updates from inside the app choose the right one automatically.</p>
<div class="apk-list">
${opt("/download/android-arm64", "64-bit (arm64)", "≈ 65 MB", "Recommended: Fire TV Stick 4K / 4K Max, Shield, Google TV Streamer, most recent boxes and phones", true)}
${opt("/download/android-32", "32-bit (armv7)", "≈ 50 MB", "Chromecast with Google TV (HD), older or budget boxes, or if the 64-bit install fails (“package appears to be invalid”)")}
${opt("/download/android-universal", "Universal", "≈ 220 MB", "Last resort: works everywhere, but much larger")}
</div>
<div class="note">${ico("info")}<span>Not sure? Try 64-bit first. If Android refuses to install it, come back and take 32-bit.</span></div>
</div>
<p class="alt"><a href="https://github.com/vantaflow/ultra-tv/releases/latest">All files on GitHub</a></p>`,
    loc: { lang: "en", skip: "Skip to content", themeBtn: "Theme", themeLabels: { auto: "Theme: auto", light: "Theme: light", dark: "Theme: dark" }, themeChange: "change", foot: { github: "Source code (GitHub)", privacy: "Privacy" } } });
}

/** Page hors ligne (PWA) : seule page mise en cache par le service worker, sans aucune donnée du compte. */
export function offlinePage(n) {
  return shell(n, { title: "Ultra TV — hors ligne", ctx: "public", width: "narrow", body: `
<div class="panel"><h1>Pas de connexion</h1><p class="sub">Ultra TV n'arrive pas à joindre le serveur. Vérifiez le Wi-Fi ou les données mobiles, puis réessayez.</p>
<div class="row"><a class="button block" href="/">Réessayer</a></div>
<div class="note">${ico("info")}<span>Vos sources et vos réglages ne sont jamais gardés hors ligne sur ce téléphone : ils restent sur votre compte.</span></div>
</div>` });
}

/**
 * Page de dons (/donate) : montants proposés + montant libre, en dirhams ; [state] = "form" | "thanks",
 * [err] = "declined" | "upstream" | "invalid" ; [enabled] = clé ChariPay configurée.
 */
export function donatePage(n, { lang = "en", enabled = false, err = null, state = "form" } = {}) {
  if (!ENVOYER_LANGS.includes(lang)) lang = "en";
  const t = DONATE_DICT[lang];
  const c = ENVOYER_DICT[lang];
  const dir = ENVOYER_DIR[lang];
  const langLinks = ENVOYER_LANGS.map((l) => `<a href="${e(`${state === "thanks" ? "/donate/merci" : "/donate"}?l=${l}`)}" lang="${l}" hreflang="${l}"${l === lang ? ` aria-current="true"` : ""}>${e(ENVOYER_LANG_NAMES[l])}</a>`).join("");
  const msg = err && t[err] ? notice(err === "upstream" ? "info" : "err", t[err]) : "";
  let body;
  if (state === "thanks") {
    body = `<div class="panel"><h1>${e(t.thanks)}</h1><p class="sub">${e(t.thanksSub)}</p>
<div class="row"><a class="button block" href="https://github.com/vantaflow/ultra-tv">${e(t.github)}</a></div></div>`;
  } else if (!enabled) {
    body = `<div class="panel"><h1>${e(t.title)}</h1><p class="sub">${e(t.sub)}</p>${notice("info", t.off)}</div>`;
  } else {
    const presets = DONATE_PRESETS.map((a, i) => `<button class="${i === 1 ? "" : "alt-btn "}block" type="submit" name="amount" value="${a}"${i === 1 ? " autofocus" : ""}>${a} MAD</button>`).join("");
    body = `<div class="panel"><h1>${e(t.title)}</h1><p class="sub">${e(t.sub)}</p>${msg}
<form method="post" action="/donate?l=${lang}">
<p class="hint">${e(t.pick)}</p>
<div class="apk-list">${presets}</div>
<label for="custom">${e(t.other)}</label>
<input id="custom" name="custom" dir="ltr" inputmode="numeric" pattern="[0-9]*" min="${DONATE_MIN}" max="${DONATE_MAX}" maxlength="5" autocomplete="off" placeholder="${DONATE_MIN} – ${DONATE_MAX}"/>
<div class="row"><button class="block" type="submit">${e(t.give)}</button></div>
</form>
<div class="note">${ico("info")}<span>${e(t.secure)}</span></div></div>`;
  }
  const res = shell(n, { title: t.title, ctx: "public", width: "narrow", body,
    loc: { lang, dir, skip: c.skip, themeBtn: c.themeBtn, themeLabels: { auto: c.themeAuto, light: c.themeLight, dark: c.themeDark }, themeChange: c.themeChange,
      foot: { github: c.footGithub, privacy: c.footPrivacy, navLabel: c.langLabel, langs: `<nav class="langs" aria-label="${e(c.langLabel)}">${langLinks}</nav>` } } });
  res.headers.append("vary", "Cookie, Accept-Language");
  return res;
}
