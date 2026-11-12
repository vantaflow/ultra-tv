// Gabarit commun à tout le site : document HTML (CSP à nonce), en-tête, navigation selon le contexte
// (pages publiques / espace utilisateur / administration), menu du compte, pied de page et thème.
// Règles : tout texte dynamique passe par escapeHtml ; aucun attribut on*= ni style="" (la CSP n'autorise que les
// balises portant le nonce).

import { escapeHtml as e, html } from "../http.js";
import { CSS } from "./css.js";
import { FAVICON, LOGO, ico, jsonForScript } from "./components.js";
import { PWA_HEAD, PWA_REGISTER } from "../pwa.js";

export const REPO_URL = "https://github.com/vantaflow/ultra-tv";
export const PRIVACY_URL = `${REPO_URL}#confidentialit%C3%A9`;

// Thème choisi (Automatique / Clair / Sombre), gardé dans le navigateur ; appliqué AVANT le premier rendu (pas de flash).
const THEME_HEAD = `try{var t=localStorage.getItem('utv-theme');if(t==='light'||t==='dark')document.documentElement.setAttribute('data-theme',t);}catch(e){}`;

// Libellés en français par défaut ; les pages traduites (/envoyer, /apk) fournissent les leurs via `loc`.
const themeScript = (L = { auto: "Thème : automatique", light: "Thème : clair", dark: "Thème : sombre" }, change = "changer") => `(function(){var b=document.getElementById('theme-toggle');if(!b)return;var l=b.querySelector('.tt-l'),L=${jsonForScript(L)},O=['auto','light','dark'];
function cur(){try{return localStorage.getItem('utv-theme')||'auto';}catch(e){return 'auto';}}
function show(t){if(l)l.textContent=L[t];b.setAttribute('aria-label',L[t]+' ('+${jsonForScript(change)}+')');b.setAttribute('title',L[t]);}
b.addEventListener('click',function(){var t=O[(O.indexOf(cur())+1)%3];try{if(t==='auto')localStorage.removeItem('utv-theme');else localStorage.setItem('utv-theme',t);}catch(e){}
if(t==='auto')document.documentElement.removeAttribute('data-theme');else document.documentElement.setAttribute('data-theme',t);show(t);});show(cur());})();`;

// Menu du compte (<details>) : se ferme au clic à côté ou avec Échap.
const MENU_JS = `(function(){var m=document.querySelector('.acctmenu');if(!m)return;
document.addEventListener('click',function(ev){if(m.open&&!m.contains(ev.target))m.open=false;});
document.addEventListener('keydown',function(ev){if(ev.key==='Escape'&&m.open){m.open=false;m.querySelector('summary').focus();}});})();`;

// La navigation suit la section visible (défilement ET clic) ; seuls les liens d'ancre du tableau de bord sont concernés.
const SPY_JS = `(function(){var links=[].slice.call(document.querySelectorAll('[data-spy] a[href^="#"]'));if(!links.length)return;
var ids=[];links.forEach(function(a){var i=a.getAttribute('href').slice(1);if(ids.indexOf(i)<0)ids.push(i);});
var secs=ids.map(function(i){return document.getElementById(i);});
var mark=function(i){links.forEach(function(a){var on=a.getAttribute('href')==='#'+ids[i];a.classList.toggle('on',on);if(on)a.setAttribute('aria-current','true');else a.removeAttribute('aria-current');});};
var tick=false,pin=-1,pinUntil=0;
var update=function(){tick=false;var now=Date.now();if(pin>=0&&now<pinUntil){mark(pin);return;}pin=-1;
 var line=window.innerHeight*0.35,cur=0;
 for(var k=0;k<secs.length;k++){var el=secs[k];if(el&&el.getBoundingClientRect().top<=line)cur=k;}
 if(window.innerHeight+window.scrollY>=document.documentElement.scrollHeight-2)cur=secs.length-1;
 mark(cur);};
var onScroll=function(){if(!tick){tick=true;requestAnimationFrame(update);}};
links.forEach(function(a){a.addEventListener('click',function(){var k=ids.indexOf(a.getAttribute('href').slice(1));pin=k;pinUntil=Date.now()+700;mark(k);});});
window.addEventListener('scroll',onScroll,{passive:true});window.addEventListener('resize',onScroll);update();})();`;

/** Document complet (sans gabarit) : sert de base à `shell`. `loc` : langue, sens d'écriture et libellés du thème. */
export function layout(title, body, n, extra = "", camera = false, loc = {}) {
  const { lang = "fr", dir = "ltr", themeLabels, themeChange, bodyClass = "" } = loc;
  return html(`<!doctype html><html lang="${lang}" dir="${dir}"><head><script nonce="${n}">${THEME_HEAD}</script><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><meta name="robots" content="noindex"/><meta name="color-scheme" content="dark light"/><meta name="theme-color" content="#0A0A0C"/><link rel="icon" href="${FAVICON}"/>${PWA_HEAD}<title>${e(title)}</title><style nonce="${n}">${CSS}</style></head><body${bodyClass ? ` class="${e(bodyClass)}"` : ""}>${body}<script nonce="${n}">${themeScript(themeLabels, themeChange)}${MENU_JS}${PWA_REGISTER}</script>${extra}</body></html>`, n, 200, { camera });
}

// Navigation de l'espace utilisateur : ancres du tableau de bord.
export const USER_NAV = [
  ["apercu", "Vue d'ensemble", "home"],
  ["sources", "Sources", "list"],
  ["appareils", "Appareils", "tv"],
  ["integrations", "Intégrations", "puzzle"],
  ["securite", "Compte", "shield"],
];

const PUBLIC_NAV = [["/envoyer", "Envoyer une source"], ["/apk", "Installer l'app"], ["/login", "Connexion"], ["/signup", "Créer un compte"]];

const navLink = (href, label, icon, cur) => `<a href="${e(href)}"${cur ? ' aria-current="page"' : ""}>${icon ? ico(icon) : ""}<span>${e(label)}</span></a>`;

/**
 * Page complète dans le gabarit du site.
 *  ctx    : "public" | "user" | "admin" (navigation de l'en-tête)
 *  active : (user) identifiant d'ancre actif ; (public) chemin courant
 *  spy    : (user) la page est le tableau de bord : la navigation suit le défilement
 *  acct   : { login } du compte connecté ; admin : vrai si l'utilisateur est administrateur
 *  sub    : HTML placé sous l'en-tête (onglets de l'administration)
 *  width  : "" | "narrow" | "mid" | "wide"
 *  loc    : langue/sens/libellés (voir `layout`) + foot { github, privacy, langs HTML }
 */
export function shell(n, { title, body, ctx = "public", active = "", spy = false, acct = null, admin = false, csrfInput = "", sub = "", width = "", extra = "", camera = false, bare = false, loc = {} }) {
  const foot = { github: "Code source (GitHub)", privacy: "Confidentialité", langs: "", ...(loc.foot || {}) };
  const translated = loc.lang && loc.lang !== "fr";
  let nav = "";
  if (ctx === "user") {
    const href = (id) => `${spy ? "" : "/"}#${id}`;
    nav = `<nav class="pnav on-user" aria-label="Navigation principale"${spy ? " data-spy" : ""}>${USER_NAV.map(([id, label, icon]) => navLink(href(id), label, icon, false)).join("")}${admin ? navLink("/admin", "Administration", "chart", false) : ""}</nav>`;
  } else if (ctx === "admin") {
    nav = bare ? "" : `<nav class="pnav on-admin" aria-label="Navigation principale">${navLink("/", "Mon espace", "home", false)}${navLink("/admin", "Administration", "chart", true)}</nav>`;
  } else if (!translated) {
    nav = `<nav class="pnav" aria-label="Navigation principale">${PUBLIC_NAV.map(([h, l]) => navLink(h, l, "", h === active)).join("")}</nav>`;
  }
  const initial = acct ? [...String(acct.login || "?")][0] : "";
  const menu = acct && !bare
    ? `<details class="acctmenu"><summary aria-label="Menu du compte"><span class="av" aria-hidden="true">${e(initial)}</span><span class="nm">${e(acct.login)}</span>${ico("down")}</summary><div class="menu"><div class="who">Connecté en tant que<strong>${e(acct.login)}</strong></div>
<a href="/">${ico("home")}Mon espace</a>${admin ? `<a href="/admin">${ico("chart")}Administration</a>` : ""}<a href="/pair">${ico("tv")}Appairer un appareil</a>
<form method="post" action="/logout">${csrfInput}<button type="submit">${ico("logout")}Se déconnecter</button></form></div></details>`
    : "";
  const brandHref = "/";
  const brandBadge = ctx === "admin" ? `<span class="kind adm-b">ADMIN</span>` : "";
  const themeBtn = `<button type="button" class="tbtn" id="theme-toggle">${ico("moon")}<span class="tt-l">${e(loc.themeBtn || "Thème")}</span></button>`;
  const bnav = ctx === "user"
    ? `<nav class="bnav" aria-label="Navigation mobile"${spy ? " data-spy" : ""}>${USER_NAV.map(([id, label, icon]) => `<a href="${spy ? "" : "/"}#${id}">${ico(icon)}<span>${e(label)}</span></a>`).join("")}</nav>`
    : "";
  const footer = `<footer class="ftr"><div class="ftr-in"><nav aria-label="${e(foot.navLabel || "Liens")}"><a href="${REPO_URL}" target="_blank" rel="noopener noreferrer">${ico("ext")}${e(foot.github)}</a><a href="${PRIVACY_URL}" target="_blank" rel="noopener noreferrer">${ico("shield")}${e(foot.privacy)}</a></nav>${foot.langs || ""}</div></footer>`;
  const inner = `<a class="skip" href="#main">${e(loc.skip || "Aller au contenu")}</a>
<header class="hdr"><div class="hdr-in"><a class="brand" href="${brandHref}">${LOGO}<span>Ultra TV</span>${brandBadge}</a>${nav}<div class="hdr-r">${themeBtn}${menu}</div></div></header>
${sub}<main id="main" class="page${width ? " " + width : ""}">${body}</main>${bnav}${footer}`;
  return layout(title, inner, n, (spy ? `<script nonce="${n}">${SPY_JS}</script>` : "") + extra, camera, { ...loc, bodyClass: ctx === "user" ? "has-bnav" : "" });
}
