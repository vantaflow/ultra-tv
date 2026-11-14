// Appairage : saisie du code TV (8 cases), scan du QR, page /pair.
import { escapeHtml as e } from "../http.js";
import { parsePairQr } from "../qr.js";
import { PRIMARY_HOST, LEGACY_HOSTS } from "../legacyhost.js";
import { SITE_DICT } from "../i18n/site.js";
import { ico, notice } from "../ui/components.js";
import { shell } from "../ui/layout.js";

export const PAIR_JS = `(function(){var box=document.getElementById('codecells'),fb=document.getElementById('code');if(!box||!fb)return;
 var cs=Array.prototype.slice.call(box.querySelectorAll('.cell'));box.hidden=false;fb.hidden=true;fb.required=false;fb.disabled=true;
 var hid=document.createElement('input');hid.type='hidden';hid.name='code';fb.parentNode.appendChild(hid);
 function sync(){hid.value=cs.map(function(c){return c.value;}).join('');}
 function fill(txt,from){var t=txt.toUpperCase().replace(/[^A-Z0-9]/g,'');for(var i=0;i<t.length&&from+i<cs.length;i++)cs[from+i].value=t[i];sync();var k=Math.min(from+t.length,cs.length-1);cs[k].focus();}
 cs.forEach(function(c,i){
  c.addEventListener('input',function(){var v=c.value;c.value='';if(v)fill(v,i);else sync();});
  c.addEventListener('keydown',function(ev){if(ev.key==='Backspace'&&!c.value&&i>0){cs[i-1].value='';cs[i-1].focus();sync();}
   else if(ev.key==='ArrowLeft'&&i>0){cs[i-1].focus();}else if(ev.key==='ArrowRight'&&i<cs.length-1){cs[i+1].focus();}});
  c.addEventListener('paste',function(ev){ev.preventDefault();fill((ev.clipboardData||window.clipboardData).getData('text'),i);});
  c.addEventListener('focus',function(){c.select();});});sync();
 box.closest('form').addEventListener('submit',function(){sync();});})();
`;

// Scanner de QR : caméra arrière, décodage sur le thread principal (~8 images/s), aucun worker/blob.
// BarcodeDetector natif si présent, sinon jsQR (/assets/jsqr.js, chargé à l'ouverture seulement).
// Le contenu lu n'est jamais ouvert : parsePairQr n'en extrait que le code, l'utilisateur confirme avec « Appairer ».
export const scanJs = (lang = "fr") => {
  const sc = (SITE_DICT[lang] || SITE_DICT.fr).pair.scanner;
  return `(function(){var nonce=document.currentScript&&document.currentScript.nonce||'';
 var btn=document.getElementById('scan-btn'),dlg=document.getElementById('scanner');
 var parse=${parsePairQr.toString()};
 var ORIGINS=[location.origin].concat(${JSON.stringify([PRIMARY_HOST, ...LEGACY_HOSTS].map((h) => `https://${h}`))});
 var imp=document.getElementById('scan-file-btn'),file=document.getElementById('scan-file'),fmsg=document.getElementById('scan-file-msg');
 if(!btn||!dlg)return;
 var cam=!!(navigator.mediaDevices&&navigator.mediaDevices.getUserMedia);
 var video=document.getElementById('scan-video'),msg=document.getElementById('scan-msg'),shut=document.getElementById('scan-close');
 var stream=null,timer=0,det=null,canvas=null,ctx=null,running=false,loading=null;
 var BAD=${JSON.stringify(sc.bad)},DENIED=${JSON.stringify(sc.denied)};
 btn.hidden=!cam;
 function say(t,bad){msg.textContent=t;msg.className=bad?'scan-msg bad':'scan-msg';}
 function stop(){running=false;clearTimeout(timer);if(stream){stream.getTracks().forEach(function(t){t.stop();});stream=null;}video.srcObject=null;}
 function loadJsqr(){if(window.jsQR)return Promise.resolve();if(loading)return loading;
  loading=new Promise(function(ok,ko){var s=document.createElement('script');s.src='/assets/jsqr.js';s.nonce=nonce;s.onload=function(){window.jsQR?ok():ko();};s.onerror=function(){loading=null;ko();};document.head.appendChild(s);});return loading;}
 function decode(){
  if(det)return det.detect(video).then(function(r){return r.length?r[0].rawValue:null;});
  var w=video.videoWidth,h=video.videoHeight;if(!w||!h)return Promise.resolve(null);
  var k=Math.min(1,640/Math.max(w,h));w=Math.round(w*k);h=Math.round(h*k);
  if(!canvas){canvas=document.createElement('canvas');ctx=canvas.getContext('2d',{willReadFrequently:true});}
  canvas.width=w;canvas.height=h;ctx.drawImage(video,0,0,w,h);
  var r=window.jsQR(ctx.getImageData(0,0,w,h).data,w,h,{inversionAttempts:'dontInvert'});return Promise.resolve(r?r.data:null);}
 function tick(){if(!running)return;
  decode().then(function(text){if(!running)return;
   if(text){var code=parse(text,ORIGINS);
    if(code){found(code);return;}
    say(BAD,true);}
   timer=setTimeout(tick,125);},function(){if(running)timer=setTimeout(tick,250);});}
 function found(code){stop();if(dlg.open)dlg.close();
  var cs=document.querySelectorAll('#codecells .cell');for(var i=0;i<cs.length&&i<8;i++)cs[i].value=code.charAt(i);
  var fb=document.getElementById('code');if(fb)fb.value=code;
  var h=document.querySelector('input[name=code][type=hidden]');if(h)h.value=code;
  var d=document.getElementById('dname');if(d)d.focus();}
 function open(){say(${JSON.stringify(sc.place)});
  if(!dlg.open)dlg.showModal();
  navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'},width:{ideal:1280},height:{ideal:720}},audio:false}).then(function(s){
   if(!dlg.open){s.getTracks().forEach(function(t){t.stop();});return;}
   stream=s;video.srcObject=s;return video.play().catch(function(){}).then(function(){
    if(window.BarcodeDetector){try{det=new BarcodeDetector({formats:['qr_code']});}catch(x){det=null;}}
    return det?null:loadJsqr();}).then(function(){running=true;tick();},function(){stop();say(${JSON.stringify(sc.unavail)},true);});
  },function(){say(DENIED,true);});}
 function fsay(t,bad){if(fmsg){fmsg.textContent=t;fmsg.className=bad?'hint small mt6 bad':'hint small mt6';}}
 function fromImage(blob){
  if(!blob||String(blob.type||'').indexOf('image/')!==0){fsay(${JSON.stringify(sc.chooseImg)},true);return;}
  fsay(${JSON.stringify(sc.reading)});
  createImageBitmap(blob).then(function(bmp){
   var nat=null;if(window.BarcodeDetector){try{nat=new BarcodeDetector({formats:['qr_code']});}catch(x){nat=null;}}
   var viaNative=nat?nat.detect(bmp).then(function(r){return r.length?r[0].rawValue:null;},function(){return null;}):Promise.resolve(null);
   return viaNative.then(function(t){if(t)return t;return loadJsqr().then(function(){
     var w=bmp.width,h=bmp.height,k=Math.min(1,1600/Math.max(w,h));w=Math.max(1,Math.round(w*k));h=Math.max(1,Math.round(h*k));
     var c=document.createElement('canvas');c.width=w;c.height=h;var x=c.getContext('2d',{willReadFrequently:true});x.drawImage(bmp,0,0,w,h);
     var r=window.jsQR(x.getImageData(0,0,w,h).data,w,h,{inversionAttempts:'attemptBoth'});return r?r.data:null;});});
  }).then(function(text){
   if(!text){fsay(${JSON.stringify(sc.noQr)},true);return;}
   var code=parse(text,ORIGINS);
   if(!code){fsay(BAD,true);return;}
   var msgOk=${JSON.stringify(sc.readOk)}.replace('{code}', code.slice(0,4)+'-'+code.slice(4));
   fsay(msgOk);found(code);
  },function(){fsay(${JSON.stringify(sc.unreadable)},true);});}
 if(imp&&file){imp.hidden=false;
  imp.addEventListener('click',function(){file.value='';file.click();});
  file.addEventListener('change',function(){if(file.files&&file.files[0])fromImage(file.files[0]);});
  document.addEventListener('paste',function(ev){var it=ev.clipboardData&&ev.clipboardData.items;if(!it)return;
   for(var i=0;i<it.length;i++){if(it[i].kind==='file'&&String(it[i].type||'').indexOf('image/')===0){ev.preventDefault();fromImage(it[i].getAsFile());return;}}});}
 btn.addEventListener('click',open);
 shut.addEventListener('click',function(){dlg.close();});
 dlg.addEventListener('close',stop);dlg.addEventListener('cancel',stop);
})();`;
};

export const SCAN_JS = scanJs("fr");

export function pairForm(csrfInput, code = "", lang = "fr") {
  const dict = SITE_DICT[lang] || SITE_DICT.fr;
  const t = dict.pair;
  const c = code || "";
  const cells = Array.from({ length: 8 }, (_, i) => `${i === 4 ? '<span class="sep" aria-hidden="true"></span>' : ""}<input class="cell" maxlength="1" inputmode="text" autocapitalize="characters" autocomplete="off" spellcheck="false" value="${e(c[i] || "")}" aria-label="${e(t.charAria(i + 1))}"/>`).join("");
  return `<form method="post" action="/pair">${csrfInput}
<div class="lbl mt0" id="l-code">${e(t.codeLabel)}</div>
<div id="codecells" class="codebox" role="group" aria-labelledby="l-code" hidden>${cells}</div>
<input id="code" class="code-fallback" name="code" required maxlength="12" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="ABCD-EFGH" aria-labelledby="l-code" aria-describedby="code-hint" value="${e(c ? `${c.slice(0, 4)}-${c.slice(4)}` : "")}"/>
<p class="hint small mt6" id="code-hint">${e(t.codeHint)}</p>
<button type="button" id="scan-btn" class="secondary block mt10" hidden>${ico("scan")}${e(t.scanBtn)}</button>
<button type="button" id="scan-file-btn" class="secondary block mt10" hidden>${ico("scan")}${e(t.scanFileBtn)}</button>
<input type="file" id="scan-file" accept="image/*" hidden/>
<p class="hint small mt6" id="scan-file-msg" role="status" aria-live="polite">${e(t.scanFileMsg)}</p>
<label for="dname">${e(t.devNameLabel)} <span class="hint">${e(t.optional)}</span></label><input id="dname" name="name" maxlength="40" placeholder="${e(t.devNamePh)}"/>
<div class="row"><button class="block" type="submit">${e(t.submit)}</button></div></form>
<dialog id="scanner" class="scanner" aria-labelledby="scan-title"><div class="scan-bar"><h2 id="scan-title">${e(t.scanModalTitle)}</h2><button type="button" id="scan-close" class="scan-x">${ico("close")}${e(t.close)}</button></div>
<div class="scan-view"><video id="scan-video" playsinline muted></video><div class="scan-frame" aria-hidden="true"></div></div>
<p id="scan-msg" class="scan-msg" role="status" aria-live="polite"></p></dialog>`;
}

export function pairPage(n, { acct, csrf, code, invalid, admin = false, lang = "fr", url = "/pair" }) {
  const dict = SITE_DICT[lang] || SITE_DICT.fr;
  const t = dict.pair;
  const csrfInput = `<input type="hidden" name="csrf" value="${e(csrf)}"/>`;
  const msg = invalid ? `<div class="notice err" role="alert">${ico("info")}<span>${e(t.invalidCodeMsg)}</span></div>` : "";
  const h1 = code ? t.h1Confirm : t.h1New;
  const sub = code ? t.subConfirm : t.subNew;
  return shell(n, {
    title: t.title,
    ctx: "user",
    width: "narrow",
    acct,
    admin,
    csrfInput,
    camera: true,
    loc: { lang, url },
    extra: `<script nonce="${n}">${PAIR_JS}${scanJs(lang)}</script>`,
    body: `
<div class="panel"><h1>${e(h1)}</h1>
<p class="sub">${e(sub)}</p>
${msg}<p class="muted small mt10">${e(t.accountLabel)} <strong>${e(acct.login)}</strong></p>
${pairForm(csrfInput, code || "", lang)}
<p class="alt"><a href="/">${e(t.cancel)}</a></p></div>`
  });
}
