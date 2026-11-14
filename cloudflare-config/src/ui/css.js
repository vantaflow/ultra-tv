// Système de design d'Ultra TV : jetons (couleurs, espacements, rayons, typographie), thèmes sombre/clair et composants.
// Tout est inline (CSP à nonce) ; les polices Sora et Manrope sont servies par /assets (déjà autorisées).

// Palette claire, utilisée par la préférence système ET par le choix manuel (data-theme=light).
const LIGHT = "--bg:#F4F4F7;--s1:#FFFFFF;--s2:#F0F0F4;--s3:#E6E6EC;--bd:#DCDCE4;--fg:#121216;--fg2:#34343E;--mut:#575762;--acc-h:#B9141F;--acc-soft:rgba(217,30,43,.08);--acc-fg:#C0111D;--dng:#B4121D;--dng-soft:rgba(180,18,29,.08);--ok:#146C36;--ok-soft:rgba(20,108,54,.09);--warn:#8A5200;--warn-soft:rgba(138,82,0,.1);--ring:#C0111D;--glow:rgba(217,30,43,.06);--shadow:0 1px 2px rgba(18,18,22,.05),0 6px 20px rgba(18,18,22,.06);--hdr-bg:rgba(255,255,255,.86)";

export const CSS = `
@font-face{font-family:Sora;src:url(/assets/sora.woff2) format("woff2");font-weight:100 800;font-display:swap}
@font-face{font-family:Manrope;src:url(/assets/manrope.woff2) format("woff2");font-weight:200 800;font-display:swap}
/* ---- Jetons ---- */
:root{color-scheme:dark light;
--bg:#0A0A0C;--s1:#141418;--s2:#1C1C21;--s3:#26262D;--bd:#2A2A32;--fg:#F5F5F7;--fg2:#C9C9D1;--mut:#A3A3AD;
--acc:#D91E2B;--acc-h:#EB2F3C;--on-acc:#fff;--acc-soft:rgba(217,30,43,.16);--acc-fg:#FF7A82;
--dng:#FF7A82;--dng-soft:rgba(255,122,130,.12);--ok:#4ADE80;--ok-soft:rgba(74,222,128,.12);--warn:#F5B83D;--warn-soft:rgba(245,184,61,.12);
--ring:#FF9AA1;--glow:rgba(217,30,43,.16);--shadow:0 1px 0 rgba(255,255,255,.03) inset,0 8px 24px rgba(0,0,0,.35);--hdr-bg:rgba(10,10,12,.82);
--sp-1:4px;--sp-2:8px;--sp-3:12px;--sp-4:16px;--sp-5:24px;--sp-6:32px;--sp-7:48px;
--r-sm:8px;--r-md:12px;--r-lg:18px;--r-pill:999px;
--t-xs:12px;--t-sm:13px;--t-md:15px;--t-lg:18px;--t-xl:22px;--t-2xl:30px;
--hdr:60px;--bnav:64px;
--ff:Manrope,system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;--fh:Sora,Manrope,system-ui,sans-serif;--mono:ui-monospace,Menlo,Consolas,monospace}
@media (prefers-color-scheme:light){:root:not([data-theme=dark]){${LIGHT}}}
:root[data-theme=light]{color-scheme:light;${LIGHT}}
:root[data-theme=dark]{color-scheme:dark}
/* ---- Base ---- */
*{box-sizing:border-box}
[hidden]{display:none!important}
html{-webkit-text-size-adjust:100%;scroll-behavior:smooth}
body{margin:0;min-height:100vh;display:flex;flex-direction:column;background:var(--bg) radial-gradient(900px 380px at 50% -160px,var(--glow),transparent 70%) no-repeat;color:var(--fg);font:var(--t-md)/1.55 var(--ff);-webkit-font-smoothing:antialiased;overflow-wrap:anywhere}
a{color:var(--acc-fg);text-decoration:none;font-weight:600;border-radius:6px} a:hover{text-decoration:underline}
h1,h2,h3{font-family:var(--fh);margin:0;letter-spacing:-.01em;line-height:1.2;color:var(--fg)}
h1{font-size:var(--t-2xl);font-weight:700} h2{font-size:var(--t-lg);font-weight:650} h3{font-size:var(--t-md);font-weight:600}
p{margin:0}
.sub,.muted{color:var(--mut)} .sub{margin-top:var(--sp-2)} .small{font-size:var(--t-sm)}
:focus-visible{outline:3px solid var(--ring);outline-offset:2px}
.skip{position:absolute;inset-inline-start:16px;top:-80px;background:var(--acc);color:var(--on-acc);padding:10px 14px;border-radius:var(--r-md);z-index:100}
.skip:focus{top:8px}
.vh{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}
.mt0{margin-top:0}.mt6{margin-top:6px}.mt10{margin-top:10px}.mt16{margin-top:16px}.mb16{margin-bottom:16px}
.ico{width:20px;height:20px;flex:none;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.inline{display:inline}
/* ---- Marque ---- */
.brand{white-space:nowrap;flex:none;display:inline-flex;align-items:center;gap:10px;color:var(--fg);font-family:var(--fh);font-weight:700;font-size:var(--t-lg)}
.brand:hover{text-decoration:none}
.logo{width:34px;height:34px;flex:none;display:block}
.logo .lg-bg{fill:#0A0A0C} .logo .lg-fr{stroke:#F5F5F7}
/* ---- Gabarit : en-tête, navigation, pied de page ---- */
.hdr{position:sticky;top:0;z-index:40;background:var(--hdr-bg);-webkit-backdrop-filter:saturate(1.4) blur(12px);backdrop-filter:saturate(1.4) blur(12px);border-bottom:1px solid var(--bd)}
.hdr-in{display:flex;align-items:center;gap:var(--sp-4);max-width:1280px;margin:0 auto;padding:0 16px;min-height:var(--hdr)}
.hdr .brand .kind{margin:0}
.pnav{display:flex;align-items:center;gap:2px;flex:1;min-width:0;overflow-x:auto;scrollbar-width:none;margin-inline-start:var(--sp-2)}
.pnav a{display:inline-flex;align-items:center;gap:8px;min-height:40px;padding:0 12px;border-radius:var(--r-md);color:var(--fg2);font-weight:600;font-size:14px;white-space:nowrap}
.pnav a:hover{background:var(--s2);color:var(--fg);text-decoration:none}
.pnav a.on,.pnav a[aria-current]{background:var(--s2);color:var(--fg);box-shadow:inset 0 -2px 0 var(--acc)}
.pnav .ico{width:18px;height:18px}
.hdr-r{display:flex;align-items:center;gap:var(--sp-2);margin-inline-start:auto;flex:none}
.pnav+.hdr-r{margin-inline-start:0}
.tbtn{display:inline-flex;align-items:center;gap:8px;min-height:40px;padding:0 12px;border-radius:var(--r-pill);border:1px solid var(--bd);background:var(--s1);color:var(--fg2);font:600 13px var(--ff);cursor:pointer}
.tbtn:hover{color:var(--fg);border-color:var(--mut);background:var(--s1)}
.tbtn .ico{width:16px;height:16px}
.acctmenu{position:relative}
.acctmenu>summary{list-style:none;display:inline-flex;align-items:center;gap:8px;min-height:40px;padding:0 6px 0 4px;border-radius:var(--r-pill);border:1px solid var(--bd);background:var(--s1);color:var(--fg);font-weight:600;font-size:13px;cursor:pointer;max-width:200px}
.acctmenu>summary::-webkit-details-marker{display:none}
.acctmenu>summary:hover{border-color:var(--mut)}
.acctmenu[open]>summary{border-color:var(--acc)}
.acctmenu .av{width:30px;height:30px;border-radius:50%;background:var(--acc);color:var(--on-acc);display:grid;place-items:center;font:700 13px var(--fh);text-transform:uppercase;flex:none}
.acctmenu .nm{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding-inline-end:4px}
.acctmenu .menu{position:absolute;inset-inline-end:0;top:calc(100% + 8px);min-width:230px;padding:var(--sp-2);background:var(--s1);border:1px solid var(--bd);border-radius:var(--r-lg);box-shadow:var(--shadow);display:grid;gap:2px;z-index:50}
.acctmenu .menu .who{padding:8px 12px;color:var(--mut);font-size:var(--t-sm);border-bottom:1px solid var(--bd);margin-bottom:4px}
.acctmenu .menu .who strong{display:block;color:var(--fg);font-size:14px;overflow:hidden;text-overflow:ellipsis}
.acctmenu .menu a,.acctmenu .menu button{display:flex;align-items:center;gap:10px;width:100%;min-height:44px;padding:0 12px;border-radius:var(--r-md);background:transparent;color:var(--fg);border:0;font:600 14px var(--ff);text-align:start;cursor:pointer}
.acctmenu .menu a:hover,.acctmenu .menu button:hover{background:var(--s2);text-decoration:none}
.acctmenu .menu form{margin:0}
.tabs{max-width:1280px;margin:0 auto;padding:0 16px;display:flex;gap:4px;overflow-x:auto;scrollbar-width:thin}
.tabs-bar{background:var(--s1);border-bottom:1px solid var(--bd)}
.tabs a{flex:none;display:inline-flex;align-items:center;min-height:46px;padding:0 14px;color:var(--fg2);font-weight:600;font-size:14px;border-bottom:3px solid transparent;text-decoration:none;white-space:nowrap}
.tabs a:hover{color:var(--fg);text-decoration:none;background:var(--s2)}
.tabs a[aria-current=page]{color:var(--fg);border-bottom-color:var(--acc)}
.page{width:100%;max-width:1120px;margin:0 auto;padding:var(--sp-5) 16px var(--sp-7);flex:1}
.page{min-width:0}.cards>*,.two>*,.grid>*,.agrid>*{min-width:0}
.page.wide{max-width:1280px}
.page.narrow{max-width:460px;padding-top:max(var(--sp-5),6vh)}
.page.mid{max-width:620px}
.ftr{border-top:1px solid var(--bd);background:var(--s1);color:var(--mut);font-size:var(--t-sm)}
.ftr-in{max-width:1280px;margin:0 auto;padding:var(--sp-4) 16px;display:flex;flex-wrap:wrap;gap:var(--sp-2) var(--sp-5);align-items:center;justify-content:space-between}
.ftr nav{display:flex;flex-wrap:wrap;gap:var(--sp-1) var(--sp-4)}
.ftr a{color:var(--fg2);font-weight:600;padding:6px 2px;display:inline-flex;align-items:center;gap:6px}
.ftr .ico{width:14px;height:14px}
.langs{display:flex;flex-wrap:wrap;gap:4px 14px;font-size:var(--t-sm)}
.langs a{color:var(--mut);font-weight:500;padding:6px 2px} .langs a[aria-current]{color:var(--fg);font-weight:700;text-decoration:underline}
.bnav{display:none}
/* Menu du haut trop large (écran moyen, zoom) : espacement resserré, puis icônes seules (le nom reste en infobulle et pour les lecteurs d'écran). */
@media (max-width:1400px){.pnav a{padding:0 9px;gap:6px}}
@media (min-width:761px) and (max-width:1180px){.pnav a span{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap}.pnav a{padding:0 11px}}
@media (max-width:760px){
 .pnav.on-user,.pnav.on-admin{display:none}
 .hdr-in{gap:var(--sp-2)}
 .tbtn .tt-l,.acctmenu .nm{display:none}
 .tbtn{padding:0 11px}
 .page{padding-top:var(--sp-4)}
 body.has-bnav{padding-bottom:calc(var(--bnav) + env(safe-area-inset-bottom))}
 .bnav{display:flex;position:fixed;inset-inline:0;bottom:0;z-index:40;background:var(--hdr-bg);-webkit-backdrop-filter:saturate(1.4) blur(12px);backdrop-filter:saturate(1.4) blur(12px);border-top:1px solid var(--bd);padding-bottom:env(safe-area-inset-bottom)}
 .bnav a{flex:1;min-width:0;min-height:var(--bnav);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;color:var(--mut);font-size:11px;font-weight:600;border-radius:0}
 .bnav a span{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:0 2px}
 .bnav a.on,.bnav a[aria-current]{color:var(--acc-fg);box-shadow:inset 0 3px 0 var(--acc)}
 .bnav a:hover{text-decoration:none;color:var(--fg)}
 .ftr-in{padding-bottom:var(--sp-5)}
}
/* ---- Surfaces ---- */
.panel{background:var(--s1);border:1px solid var(--bd);border-radius:var(--r-lg);padding:var(--sp-5);margin-bottom:var(--sp-4);box-shadow:var(--shadow)}
.panel>h2+.sub,.panel>h2+.muted{margin-top:6px}
.panel-h{display:flex;align-items:center;gap:10px;margin-bottom:4px;flex-wrap:wrap}
.count{display:inline-flex;align-items:center;justify-content:center;min-width:26px;height:24px;padding:0 8px;border-radius:var(--r-pill);background:var(--s2);border:1px solid var(--bd);color:var(--fg2);font:600 13px var(--ff);vertical-align:2px}
.grid{display:grid;gap:var(--sp-4);align-items:start}
.two{display:grid;gap:var(--sp-4);align-items:start}
.two>.panel{margin:0}
@media (min-width:1000px){.two{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}}
.stack{display:grid;gap:var(--sp-3);margin-top:var(--sp-4)}
.section-h{display:flex;align-items:flex-end;justify-content:space-between;gap:var(--sp-3);margin:var(--sp-6) 0 var(--sp-3);flex-wrap:wrap;scroll-margin-top:calc(var(--hdr) + 12px)}
.section-h h2{font-size:var(--t-xl)}
.section-h .sh-t{display:grid;gap:2px}
.section-h a.button{margin-inline-start:auto}
.section-h.first{margin-top:var(--sp-5)}
.anchor{display:block;position:relative;top:calc(-1 * (var(--hdr) + 12px));visibility:hidden;height:0}
.hello h1{font-size:var(--t-2xl)}
/* ---- Formulaires ---- */
label,.lbl{display:block;color:var(--fg2);font-size:var(--t-sm);font-weight:600;margin:14px 0 6px}
label .hint,.hint{color:var(--mut);font-weight:500}
input,select,textarea{width:100%;min-height:48px;background:var(--bg);color:var(--fg);border:1.5px solid var(--bd);border-radius:var(--r-md);padding:11px 14px;font:inherit;font-size:16px;transition:border-color .15s,box-shadow .15s}
input::placeholder,textarea::placeholder{color:var(--mut);opacity:.8}
input:hover,select:hover,textarea:hover{border-color:var(--mut)}
input:focus-visible,select:focus-visible,textarea:focus-visible{outline:none;border-color:var(--acc);box-shadow:0 0 0 4px var(--acc-soft),0 0 0 2px var(--acc)}
textarea{min-height:92px;resize:vertical;line-height:1.45}
input[type=checkbox]{appearance:none;width:22px;height:22px;min-height:0;flex:none;margin:0;padding:0;border-radius:7px;display:inline-grid;place-content:center;cursor:pointer;background:var(--bg)}
input[type=checkbox]::after{content:"";width:11px;height:6px;border:solid var(--on-acc);border-width:0 0 2.5px 2.5px;transform:rotate(-45deg) translate(1px,-1px) scale(0);transition:transform .12s}
input[type=checkbox]:checked{background:var(--acc);border-color:var(--acc)} input[type=checkbox]:checked::after{transform:rotate(-45deg) translate(1px,-1px) scale(1)}
input[type=checkbox]:focus-visible{outline:3px solid var(--ring);outline-offset:2px;box-shadow:none}
button,.btn,a.button{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:48px;padding:0 20px;background:var(--acc);color:var(--on-acc);border:1.5px solid transparent;border-radius:var(--r-md);font:700 15px var(--ff);cursor:pointer;text-decoration:none;transition:background .15s,transform .1s,border-color .15s}
button:hover,a.button:hover{background:var(--acc-h);text-decoration:none}
button:active{transform:scale(.98)}
button:disabled{opacity:.55;cursor:not-allowed}
button.secondary,a.button.secondary{background:var(--s2);color:var(--fg);border-color:var(--bd);font-weight:600} button.secondary:hover,a.button.secondary:hover{border-color:var(--mut);background:var(--s3)}
button.danger{background:transparent;color:var(--dng);border-color:var(--bd);font-weight:600} button.danger:hover{border-color:var(--dng);background:var(--dng-soft)}
button.block,.block{width:100%}
a.button{min-height:44px;padding:0 18px;font-size:14px}
.row{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-top:var(--sp-4)}
.row.end{justify-content:flex-end}
.reveal-box .row{margin-top:6px;flex-wrap:nowrap}.reveal-box input{flex:1;min-width:0;font-family:var(--mono);font-size:.85rem}
/* ---- Messages ---- */
.notice{display:flex;gap:10px;align-items:flex-start;padding:12px 14px;border-radius:var(--r-md);font-size:14px;font-weight:600;margin:0 0 var(--sp-4);border:1px solid}
.notice .ico{margin-top:1px}
.notice.err{color:var(--dng);background:var(--dng-soft);border-color:var(--dng)} .notice.ok{color:var(--ok);background:var(--ok-soft);border-color:var(--ok)} .notice.info{color:var(--fg2);background:var(--s2);border-color:var(--bd)}
.note{display:flex;gap:10px;margin-top:var(--sp-4);padding:12px 14px;border-radius:var(--r-md);background:var(--s2);color:var(--fg2);font-size:var(--t-sm)}
.secure{display:flex;gap:8px;align-items:flex-start;margin-top:14px;color:var(--mut);font-size:var(--t-sm)}
.secure .ico{width:16px;height:16px;margin-top:3px}
.hint.bad{color:var(--dng)}
.empty{display:flex;flex-direction:column;align-items:center;text-align:center;gap:var(--sp-2);padding:var(--sp-6) var(--sp-4);border:1.5px dashed var(--bd);border-radius:var(--r-lg);color:var(--mut);background:var(--s1)}
.empty .badge-ico{width:56px;height:56px;border-radius:var(--r-lg);background:var(--s2)} .empty .badge-ico .ico{width:26px;height:26px}
.empty strong{color:var(--fg);font:600 16px var(--fh)}
/* ---- Pages d'authentification et pages publiques ---- */
.auth-brand{display:flex;justify-content:center;margin-bottom:var(--sp-5)}
.auth-brand .brand{font-size:22px}.auth-brand .logo{width:46px;height:46px}
.page.narrow .panel{padding:var(--sp-5) var(--sp-5) var(--sp-6)}
.panel h1{margin-bottom:2px;font-size:26px}
.page.narrow form{margin-top:var(--sp-2)}
.alt{margin-top:var(--sp-4);text-align:center;color:var(--mut)}
.alt-links{display:flex;flex-wrap:wrap;justify-content:center;gap:6px 18px;margin-top:var(--sp-4);color:var(--mut)}
/* ---- Appairage ---- */
.steps{margin:12px 0 0;padding:0;list-style:none;display:grid;gap:10px;counter-reset:s}
.steps li{display:flex;gap:12px;align-items:flex-start;color:var(--fg2);font-size:14px;counter-increment:s}
.steps li::before{content:counter(s);flex:none;width:26px;height:26px;border-radius:50%;background:var(--acc-soft);color:var(--acc-fg);font:700 13px/26px var(--fh);text-align:center}
.steps li.done{color:var(--mut)}
.steps li.done::before{content:"\\2713";background:var(--ok-soft);color:var(--ok)}
.codebox{display:flex;gap:6px;justify-content:center;align-items:center}
.cell{width:100%;max-width:46px;min-width:0;height:60px;padding:0;text-align:center;font:700 26px var(--fh);text-transform:uppercase;border-radius:var(--r-md);background:var(--bg)}
.codebox .sep{width:10px;height:3px;border-radius:2px;background:var(--mut);flex:none;margin:0 2px}
@media (max-width:380px){.codebox{gap:4px}.cell{height:54px;font-size:22px}}
.code-fallback{font-family:var(--fh);font-weight:700;font-size:24px;letter-spacing:.18em;text-align:center;text-transform:uppercase}
.scanner{border:0;padding:0;margin:0;width:100%;max-width:none;height:100%;max-height:none;background:#050507;color:#F5F5F7;overflow:hidden}
.scanner[open]{display:flex;flex-direction:column}
.scanner::backdrop{background:#000}
.scan-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 16px;padding-top:max(12px,env(safe-area-inset-top))}
.scan-bar h2{font-size:17px;color:#F5F5F7}
.scan-x{background:rgba(255,255,255,.12);color:#F5F5F7;border-color:rgba(255,255,255,.28);min-height:48px;min-width:48px}
.scan-x:hover{background:rgba(255,255,255,.2)}
.scan-view{position:relative;flex:1;min-height:0;overflow:hidden;background:#000}
.scan-view video{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.scan-frame{position:absolute;left:50%;top:50%;width:min(68vw,68vh,340px);aspect-ratio:1;transform:translate(-50%,-50%);border:3px solid var(--acc);border-radius:22px;box-shadow:0 0 0 100vmax rgba(0,0,0,.55)}
.scan-msg{padding:14px 16px;padding-bottom:max(14px,env(safe-area-inset-bottom));text-align:center;font-weight:600;color:#E4E4EA;min-height:56px}
.scan-msg.bad{color:#FF8A92}
/* ---- Cartes (sources, appareils, alertes) ---- */
.cards{display:grid;gap:var(--sp-3)}
@media (min-width:1100px){.cards.c2{grid-template-columns:minmax(0,1fr) minmax(0,1fr)}}
.card{background:var(--s1);border:1px solid var(--bd);border-radius:var(--r-lg);padding:var(--sp-4) 18px;box-shadow:var(--shadow);transition:border-color .15s}
.card:hover{border-color:var(--mut)}
.card-top{display:flex;gap:var(--sp-3);align-items:center}
.badge-ico{width:44px;height:44px;flex:none;border-radius:var(--r-md);background:var(--bg);border:1px solid var(--bd);display:grid;place-items:center;color:var(--acc-fg)}
.card-title{font-weight:700;font-size:16px;line-height:1.3}
.card-meta{color:var(--mut);font-size:var(--t-sm)}.acct strong{color:var(--fg)}.acct.warn,.acct.warn strong{color:var(--warn)}.acct.bad,.acct.bad strong{color:var(--dng)}
.grow{min-width:0;flex:1}
.kind{display:inline-flex;align-items:center;min-height:22px;padding:0 8px;border-radius:7px;background:var(--acc-soft);color:var(--acc-fg);font:700 11px var(--ff);letter-spacing:.06em;margin-inline-end:6px;vertical-align:1px}
.kind.pro{background:linear-gradient(135deg,var(--acc),#8f1019);color:#fff;margin:0 0 0 6px}.kind.std{background:var(--s2);color:var(--fg2);margin:0 0 0 6px}
.brand .kind.pro{font-size:10px;min-height:20px}
.kind.adm-b{background:var(--s3);color:var(--fg);margin:0}
.chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:var(--sp-3)}
.chip{display:inline-flex;align-items:center;gap:6px;min-height:26px;padding:0 10px;border-radius:var(--r-pill);border:1px solid var(--bd);background:var(--bg);color:var(--fg2);font-size:var(--t-xs);font-weight:600}
.chip .ico{width:14px;height:14px}
.chip.all{background:var(--ok-soft);border-color:transparent;color:var(--ok)}
.chip.none{color:var(--dng)}
.chip.acc{background:var(--acc-soft);border-color:transparent;color:var(--acc-fg)}
.actions{display:flex;flex-wrap:wrap;gap:var(--sp-2);margin-top:var(--sp-3)}
.actions button,.actions summary{min-height:44px}
.actions form{margin:0}
details>summary{list-style:none;display:inline-flex;align-items:center;gap:8px;min-height:44px;padding:0 16px;border-radius:var(--r-md);background:var(--bg);border:1.5px solid var(--bd);color:var(--fg);font-weight:600;font-size:14px;cursor:pointer;transition:border-color .15s}
details>summary::-webkit-details-marker{display:none}
details>summary:hover{border-color:var(--mut)}
details[open]>summary{border-color:var(--acc);color:var(--acc-fg)}
details>.drop{margin-top:var(--sp-3);padding-top:4px;flex-basis:100%}
.actions details{display:contents}
.actions details[open]{display:block;flex-basis:100%}
.picks{display:flex;flex-wrap:wrap;gap:var(--sp-2);margin-top:10px}
.pick{display:inline-flex;align-items:center;gap:10px;min-height:44px;margin:0;padding:0 14px;border-radius:var(--r-md);background:var(--bg);border:1.5px solid var(--bd);color:var(--fg);font-size:14px;font-weight:600;cursor:pointer}
.pick:has(input:checked){border-color:var(--acc)}
.pick.all{border-style:dashed}
.meter{height:6px;border-radius:var(--r-pill);background:var(--s3);overflow:hidden;margin-top:var(--sp-2)}
.meter i{display:block;height:100%;width:0;background:var(--ok);border-radius:var(--r-pill);transition:width .4s}
.acct.warn .meter i{background:var(--warn)}.acct.bad .meter i{background:var(--dng)}
.listbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin:0 0 var(--sp-3)}
.listbar .lsearch{flex:1 1 240px;min-height:44px}
.pager{display:inline-flex;align-items:center;gap:var(--sp-2)}.pager button{min-height:44px;min-width:44px;padding:0 12px}
.pinfo{color:var(--mut);font-size:var(--t-sm);font-weight:600;white-space:nowrap}
/* ---- Ajout d'une source : onglets CSS seul (fonctionne sans JavaScript) ---- */
.tabbed{position:relative}
.seg{display:flex;gap:4px;padding:4px;background:var(--bg);border:1px solid var(--bd);border-radius:var(--r-lg);margin:var(--sp-4) 0 4px}
.tabbed>input[type=radio]{position:absolute;opacity:0;width:1px;height:1px;min-height:0;pointer-events:none}
.seg label{flex:1;margin:0;min-height:44px;display:grid;place-items:center;border-radius:var(--r-md);color:var(--fg2);font-size:14px;text-align:center;cursor:pointer;transition:background .15s,color .15s}
#t-xtream:checked~.seg label[for=t-xtream],#t-m3u:checked~.seg label[for=t-m3u],#t-stalker:checked~.seg label[for=t-stalker]{background:var(--acc);color:var(--on-acc)}
#t-xtream:focus-visible~.seg label[for=t-xtream],#t-m3u:focus-visible~.seg label[for=t-m3u],#t-stalker:focus-visible~.seg label[for=t-stalker]{outline:3px solid var(--ring);outline-offset:2px}
.tabbed form{display:none} #t-xtream:checked~#form-xtream,#t-m3u:checked~#form-m3u,#t-stalker:checked~#form-stalker{display:block}
.danger-zone{border-color:color-mix(in srgb,var(--dng) 45%,var(--bd))}
.danger-zone h2{color:var(--dng)}
/* ---- Chiffres clés ---- */
.tiles{display:grid;gap:var(--sp-3);grid-template-columns:repeat(auto-fit,minmax(170px,1fr));margin:var(--sp-5) 0 var(--sp-2)}
.tile{background:var(--s1);border:1px solid var(--bd);border-radius:var(--r-lg);padding:var(--sp-4) 18px;display:flex;flex-direction:column;gap:2px;box-shadow:var(--shadow)}
.tile b{font:700 28px var(--fh);color:var(--fg);line-height:1.15}
.tile span{color:var(--mut);font-size:var(--t-sm);font-weight:600}
.tile.hero{background:linear-gradient(135deg,var(--acc),#8f1019);border-color:transparent}
.tile.hero b,.tile.hero span{color:#fff}
.tile.warn b{color:var(--warn)}.tile.bad b{color:var(--dng)}
.page.wide .tiles{margin-top:0}
/* ---- Tableaux ---- */
.tablewrap{overflow-x:auto;border:1px solid var(--bd);border-radius:var(--r-lg);background:var(--s1);max-width:100%}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{padding:10px 12px;border-bottom:1px solid var(--bd);text-align:start;vertical-align:top}
tbody tr:last-child td{border-bottom:0}
tbody tr:hover td{background:var(--s2)}
th{color:var(--mut);text-transform:uppercase;letter-spacing:.06em;font-size:11px;background:var(--s1);white-space:nowrap}
pre{white-space:pre-wrap;word-break:break-word;margin:8px 0 0;font:12px var(--mono);color:var(--fg2)}
.lvl-error td.level,.lvl-warn td.level{color:var(--dng)}
.atable th a{color:inherit;text-decoration:none;white-space:nowrap}.atable th a:hover{color:var(--fg);text-decoration:none}
.atable th[aria-sort] a{color:var(--fg)}
.atable{overflow-wrap:normal}.atable td strong,.atable td time,.kind{white-space:nowrap}
.atable td time{border-bottom:1px dotted var(--mut);cursor:help}
.atable td.num,.atable th.num{text-align:end;font-variant-numeric:tabular-nums}
.filters{display:flex;flex-wrap:wrap;gap:10px;align-items:flex-end;margin:0 0 14px;padding:var(--sp-3);background:var(--s1);border:1px solid var(--bd);border-radius:var(--r-lg)}
.filters .fl{display:flex;flex-direction:column;gap:4px;min-width:130px}.filters .fl.grow{flex:1 1 220px}
.filters label{margin:0;font-size:var(--t-xs);font-weight:600;color:var(--mut)}
.filters select,.filters input{min-height:44px;margin:0}
.filters .reset{align-self:center;font-size:var(--t-sm)}
.pgr{display:flex;flex-wrap:wrap;gap:10px;align-items:center;justify-content:space-between;margin-top:var(--sp-3)}
.pgr .pages{display:flex;gap:6px;flex-wrap:wrap}
.pgr a,.pgr .cur{min-width:44px;min-height:44px;padding:0 12px;display:inline-grid;place-items:center;border:1px solid var(--bd);border-radius:var(--r-md);background:var(--s1);color:var(--fg2);font-size:14px;text-decoration:none}
.pgr .cur{color:var(--fg);border-color:var(--acc)}.pgr a[aria-disabled=true]{opacity:.4;pointer-events:none}
/* ---- Répartitions (administration) ---- */
.agrid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--sp-4);margin-top:var(--sp-4)}.agrid .panel{margin:0}.agrid .wide{grid-column:1/-1}
.agrid h3{margin:18px 0 4px;font-size:var(--t-sm);text-transform:uppercase;letter-spacing:.06em;color:var(--mut)}
@media (max-width:899px){.agrid{grid-template-columns:1fr}}
.kv{display:grid;grid-template-columns:1fr auto;gap:var(--sp-2) var(--sp-4);margin:var(--sp-3) 0 0}.kv dt{color:var(--fg2);font-size:14px}.kv dd{margin:0;font-weight:700;text-align:end;font-variant-numeric:tabular-nums}
.bars{display:flex;flex-direction:column;gap:var(--sp-2);margin-top:10px}
.bar{display:grid;grid-template-columns:minmax(90px,38%) 1fr 72px;align-items:center;gap:10px;font-size:var(--t-sm)}
.bar .bl{color:var(--fg2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.bar .bt{height:10px;border-radius:var(--r-pill);background:var(--s3);overflow:hidden}
.bar .bt i{display:block;height:100%;width:0;background:var(--acc);border-radius:var(--r-pill);transition:width .4s}
.bar .bv{text-align:end;font-weight:700;color:var(--fg)}.bar .bv small{color:var(--mut);font-weight:600}
/* ---- Alertes (administration) ---- */
.ann-msg{white-space:pre-wrap;margin:10px 0 0;color:var(--fg)}
.ann.ann-urgent{border-color:var(--acc)} .ann.ann-warning{border-color:var(--warn)}
.kind.ann-st{background:var(--s2);color:var(--fg2)} .kind.ann-live{background:var(--ok-soft);color:var(--ok)} .kind.ann-ended{opacity:.65}
.ann-form .row{gap:12px;align-items:flex-end}
.ann-form .row .grow{min-width:200px}
/* ---- Choix de l'APK (/apk) : gros boutons pour la télécommande ---- */
.apk-list{display:grid;gap:var(--sp-3);margin:var(--sp-4) 0}
.apk-list a.button{min-height:68px;padding:12px 18px;line-height:1.35;justify-content:flex-start;text-align:start;font-size:15px}
.apk-list a.alt-btn{background:var(--s2);color:var(--fg);border:1.5px solid var(--bd)}
.apk-list a.alt-btn:hover{border-color:var(--mut);background:var(--s3)}
.apk-list small{font-weight:500;opacity:.9}
.apk-list a.button:focus-visible{outline:3px solid var(--ring);outline-offset:3px}
@media (prefers-reduced-motion:reduce){*,*::before,*::after{transition:none!important;animation:none!important;scroll-behavior:auto!important}}
`;
