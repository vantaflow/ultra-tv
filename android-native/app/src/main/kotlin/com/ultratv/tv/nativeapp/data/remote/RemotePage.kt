package com.ultratv.tv.nativeapp.data.remote

/**
 * Page servie à `/r` : HTML, CSS et JS en un seul document, sans ressource externe. Thème sombre, gros boutons, pour
 * un téléphone. Textes en FR / EN / ES / AR (langue de l'appli, injectée par le serveur). Les titres sont insérés avec
 * `textContent` (jamais en HTML) ; le jeton est lu dans l'adresse et renvoyé en en-tête `X-Key`.
 */
object RemotePage {
    fun html(lang: String): String {
        val l = if (lang in setOf("fr", "en", "es", "ar")) lang else "en"
        return TEMPLATE.replace("__LANG__", l).replace("__DIR__", if (l == "ar") "rtl" else "ltr")
    }

    private val TEMPLATE = """<!doctype html>
<html lang="__LANG__" dir="__DIR__">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="color-scheme" content="dark">
<meta name="referrer" content="no-referrer">
<title>Ultra TV</title>
<style>
:root{--bg:#0b0b0f;--surf:#16161d;--surf2:#20202a;--line:#2b2b38;--text:#f2f2f6;--mut:#9a9aae;--acc:#7c5cff;--acc2:#a18bff}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{margin:0;background:var(--bg);color:var(--text);font:16px/1.35 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
body{min-height:100vh;padding-bottom:calc(168px + env(safe-area-inset-bottom))}
header{position:sticky;top:0;z-index:5;background:var(--bg);padding:14px 16px 8px;border-bottom:1px solid var(--line)}
.brand{font-weight:800;letter-spacing:.12em;font-size:13px;color:var(--acc2);margin-bottom:10px}
#q{width:100%;font:inherit;font-size:18px;padding:14px 16px;border-radius:14px;border:1px solid var(--line);background:var(--surf);color:var(--text);outline:none}
#q:focus{border-color:var(--acc)}
.tabs{display:flex;gap:8px;margin-top:10px}
.tab{flex:1;padding:12px 6px;border-radius:12px;border:1px solid var(--line);background:var(--surf);color:var(--mut);font:inherit;font-weight:700}
.tab[aria-selected=true]{background:var(--acc);border-color:var(--acc);color:#fff}
main{padding:10px 16px}
.hint{color:var(--mut);text-align:center;margin:36px 8px}
.item{display:flex;align-items:center;gap:12px;width:100%;text-align:start;padding:16px;margin:8px 0;border-radius:14px;border:1px solid var(--line);background:var(--surf);color:var(--text);font:inherit}
.item:active{background:var(--surf2)}
.item b{flex:1;font-weight:600;overflow:hidden;text-overflow:ellipsis}
.item i{color:var(--mut);font-style:normal;font-size:14px}
.dock{position:fixed;left:0;right:0;bottom:0;z-index:10;background:var(--surf);border-top:1px solid var(--line);padding:10px 12px calc(10px + env(safe-area-inset-bottom))}
.now{display:flex;align-items:center;gap:10px;margin:0 4px 8px}
.now span{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:600}
.now span.off{color:var(--mut);font-weight:400}
#padbtn{padding:8px 14px;border-radius:10px;border:1px solid var(--line);background:var(--surf2);color:var(--text);font:inherit;font-size:14px}
.row{display:grid;grid-template-columns:repeat(7,1fr);gap:6px}
.b{min-height:58px;border-radius:14px;border:1px solid var(--line);background:var(--surf2);color:var(--text);font:inherit;font-size:16px;font-weight:700;padding:0}
.b:active{background:var(--acc);border-color:var(--acc)}
.b.main{background:var(--acc);border-color:var(--acc);font-size:22px}
.pad{display:none;grid-template-columns:repeat(3,1fr);gap:8px;max-width:340px;margin:0 auto 10px}
.pad.open{display:grid}
.pad .b{min-height:64px;font-size:24px}
.pad .ok{background:var(--acc);border-color:var(--acc)}
#toast{position:fixed;left:50%;top:14px;transform:translateX(-50%);background:#3a1620;color:#ffd7de;padding:10px 16px;border-radius:12px;z-index:30;display:none;max-width:90%}
</style>
</head>
<body>
<header>
<div class="brand">ULTRA TV</div>
<input id="q" type="search" enterkeyhint="search" autocomplete="off" autocapitalize="off" spellcheck="false">
<div class="tabs" role="tablist">
<button class="tab" role="tab" data-t="movie" aria-selected="true"></button>
<button class="tab" role="tab" data-t="series" aria-selected="false"></button>
<button class="tab" role="tab" data-t="live" aria-selected="false"></button>
</div>
</header>
<main><div id="list"></div><div id="hint" class="hint"></div></main>
<div class="dock">
<div id="pad" class="pad">
<span></span><button class="b" data-c="up" aria-label="up">&#9650;</button><span></span>
<button class="b" data-c="left" aria-label="left">&#9664;</button><button class="b ok" data-c="ok">OK</button><button class="b" data-c="right" aria-label="right">&#9654;</button>
<span></span><button class="b" data-c="down" aria-label="down">&#9660;</button><button class="b" data-c="back" id="backb"></button>
</div>
<div class="now"><span id="now" class="off"></span><button id="padbtn" aria-expanded="false"></button></div>
<div class="row">
<button class="b" data-c="prev" aria-label="prev">&#9198;</button>
<button class="b" data-c="back30">&minus;30</button>
<button class="b" data-c="back10">&minus;10</button>
<button class="b main" data-c="playpause" aria-label="play/pause">&#9199;</button>
<button class="b" data-c="fwd10">+10</button>
<button class="b" data-c="fwd30">+30</button>
<button class="b" data-c="next" aria-label="next">&#9197;</button>
</div>
</div>
<div id="toast"></div>
<script>
(function(){
var L='__LANG__';
var T={
fr:{ph:'Rechercher sur la TV…',movie:'Films',series:'Séries',live:'Direct',type:'Tapez au moins 2 lettres.',none:'Aucun résultat.',idle:'Rien en lecture',pad:'Flèches',back:'Retour',opened:'Ouvert sur la TV',err:'Action impossible.',auth:'Lien expiré : scannez de nouveau le code sur la TV.',off:'Connexion perdue avec la TV.'},
en:{ph:'Search on the TV…',movie:'Movies',series:'Series',live:'Live',type:'Type at least 2 letters.',none:'No results.',idle:'Nothing playing',pad:'D-pad',back:'Back',opened:'Opened on the TV',err:'Could not do that.',auth:'Link expired: scan the code on the TV again.',off:'Lost connection to the TV.'},
es:{ph:'Buscar en la TV…',movie:'Películas',series:'Series',live:'Directo',type:'Escribe al menos 2 letras.',none:'Sin resultados.',idle:'Nada en reproducción',pad:'Flechas',back:'Atrás',opened:'Abierto en la TV',err:'No se pudo.',auth:'Enlace caducado: escanea de nuevo el código en la TV.',off:'Conexión perdida con la TV.'},
ar:{ph:'ابحث على التلفاز…',movie:'أفلام',series:'مسلسلات',live:'مباشر',type:'اكتب حرفين على الأقل.',none:'لا نتائج.',idle:'لا شيء قيد التشغيل',pad:'الأسهم',back:'رجوع',opened:'تم الفتح على التلفاز',err:'تعذّر التنفيذ.',auth:'انتهت صلاحية الرابط: امسح الرمز على التلفاز مجددًا.',off:'انقطع الاتصال بالتلفاز.'}
};
var S=T[L]||T.en;
var K=new URLSearchParams(location.search).get('k')||'';
var kind='movie',timer=0,seq=0;
function el(id){return document.getElementById(id)}
function toast(m){var t=el('toast');t.textContent=m;t.style.display='block';clearTimeout(toast.h);toast.h=setTimeout(function(){t.style.display='none'},2600)}
function api(path,body){
  var o={headers:{'X-Key':K}};
  if(body){o.method='POST';o.headers['Content-Type']='application/json';o.body=JSON.stringify(body)}
  return fetch(path,o).then(function(r){
    if(r.status===401){toast(S.auth);throw 0}
    return r.json().then(function(j){return {ok:r.ok,j:j}});
  });
}
el('q').placeholder=S.ph;
el('padbtn').textContent=S.pad;
el('backb').textContent=S.back;
var tabs=document.querySelectorAll('.tab');
for(var i=0;i<tabs.length;i++){(function(b){
  b.textContent=S[b.getAttribute('data-t')];
  b.onclick=function(){kind=b.getAttribute('data-t');for(var j=0;j<tabs.length;j++)tabs[j].setAttribute('aria-selected',tabs[j]===b?'true':'false');run()};
})(tabs[i])}
function hint(m){el('hint').textContent=m||''}
function render(res){
  var list=el('list');list.textContent='';
  for(var i=0;i<res.length;i++){(function(r){
    var b=document.createElement('button');b.className='item';
    var t=document.createElement('b');t.textContent=r.title;b.appendChild(t);
    if(r.year){var y=document.createElement('i');y.textContent=String(r.year);b.appendChild(y)}
    b.onclick=function(){api('/api/open',{kind:r.kind,id:r.id}).then(function(x){toast(x.ok?S.opened:S.err);if(x.ok&&navigator.vibrate)navigator.vibrate(20)}).catch(function(){})};
    list.appendChild(b);
  })(res[i])}
}
function run(){
  var q=el('q').value.trim();var n=++seq;
  if(q.length<2){render([]);hint(S.type);return}
  api('/api/search?t='+kind+'&q='+encodeURIComponent(q)).then(function(x){
    if(n!==seq)return;
    var res=(x.j&&x.j.results)||[];render(res);hint(res.length?'':S.none);
  }).catch(function(){})
}
el('q').oninput=function(){clearTimeout(timer);timer=setTimeout(run,250)};
hint(S.type);
var btns=document.querySelectorAll('[data-c]');
for(var i=0;i<btns.length;i++){(function(b){
  b.onclick=function(){api('/api/control',{cmd:b.getAttribute('data-c')}).then(function(x){if(!x.ok)toast(S.err);else if(navigator.vibrate)navigator.vibrate(12)}).catch(function(e){if(e!==0)toast(S.off)})};
})(btns[i])}
el('padbtn').onclick=function(){var p=el('pad');var o=p.classList.toggle('open');el('padbtn').setAttribute('aria-expanded',o?'true':'false')};
function poll(){
  api('/api/now').then(function(x){
    var n=el('now'),j=x.j||{};
    if(j.player&&j.title){n.textContent=(j.playing===false?'⏸ ':'▶ ')+j.title;n.className=''}
    else{n.textContent=S.idle;n.className='off'}
  }).catch(function(){}).then(function(){setTimeout(poll,2000)});
}
poll();
})();
</script>
</body>
</html>
"""
}
