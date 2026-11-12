// Alertes (administration) : programmation des messages affichés par les applications.
import { escapeHtml as e } from "../http.js";

export function announcementsPanel(list, csrfInput, now = Date.now(), stats = {}) {
  const d = (t) => (t ? new Date(t).toISOString().slice(0, 16).replace("T", " ") + " UTC" : "");
  const LEVEL = { info: "Information", warning: "Avertissement", urgent: "Urgent" };
  const AUD = { all: "Tous les appareils", tv: "TV et box", mobile: "Téléphones et tablettes" };
  const ACTION = { none: "Aucun bouton", update: "« Mettre à jour » (ouvre la mise à jour de l'application)" };
  const statLine = (a) => {
    const st = stats[a.id] || { shown: 0, dismissed: 0, action: 0 };
    const parts = [`affichée sur ${st.shown} appareil${st.shown > 1 ? "s" : ""}`, `fermée sur ${st.dismissed}`];
    if (a.action && a.action !== "none") parts.push(`bouton utilisé sur ${st.action}`);
    return `<div class="card-meta ann-stats">${e(parts.join(" · "))}</div>`;
  };
  const state = (a) => (a.startAt && now < a.startAt ? ["scheduled", "Programmée"] : a.endAt && now >= a.endAt ? ["ended", "Terminée"] : ["live", "En cours"]);
  const rows = [...list].sort((x, y) => (y.startAt || 0) - (x.startAt || 0)).map((a) => {
    const [st, label] = state(a);
    return `<article class="card ann ann-${e(a.level)}"><div class="card-title"><span class="kind ann-st ann-${st}">${e(label)}</span><span class="kind">${e(LEVEL[a.level] || a.level)}</span>${e(a.title || "(sans titre)")}</div>
<p class="ann-msg">${e(a.message)}</p>
<div class="card-meta">${e(AUD[a.audience || "all"] || a.audience)}${a.below ? ` · versions antérieures à ${e(a.below)}` : ""}${a.action === "update" ? " · bouton « Mettre à jour »" : ""} · du ${e(d(a.startAt))}${a.endAt ? ` au ${e(d(a.endAt))}` : " · sans fin"}${a.by ? ` · par ${e(a.by)}` : ""}</div>
${statLine(a)}
<div class="actions">${st !== "ended" ? `<form method="post" action="/admin/announcements/${e(a.id)}/end" data-confirm="Arrêter la diffusion de cette alerte maintenant ?">${csrfInput}<button class="secondary" type="submit">Arrêter</button></form>` : ""}
<form method="post" action="/admin/announcements/${e(a.id)}/delete" data-confirm="Supprimer cette alerte ?">${csrfInput}<button class="danger" type="submit">Supprimer</button></form></div></article>`;
  }).join("");
  const opts = (map, sel) => Object.entries(map).map(([k, v]) => `<option value="${e(k)}"${k === sel ? " selected" : ""}>${e(v)}</option>`).join("");
  return `<section class="panel wide" id="alertes"><h2>Alertes</h2>
<p class="muted small">Message affiché par les applications pendant la période choisie (vérifié au lancement puis toutes les 15 minutes). « Information » s'affiche en bandeau discret qui disparaît seul ; « Avertissement » et « Urgent » en fenêtre à valider. Chaque alerte s'affiche une fois par appareil, sauf « Urgent » : à chaque lancement jusqu'à la fin, et en bandeau pendant la lecture d'une vidéo.</p>
<form method="post" action="/admin/announcements" class="ann-form">${csrfInput}
<label for="ann-title">Titre</label><input id="ann-title" name="title" maxlength="80" placeholder="Maintenance du service"/>
<label for="ann-msg">Message</label><textarea id="ann-msg" name="message" maxlength="600" rows="3" required placeholder="Le service sera indisponible dimanche de 2 h à 4 h."></textarea>
<div class="row"><div class="grow"><label for="ann-level">Niveau</label><select id="ann-level" name="level">${opts(LEVEL, "info")}</select></div>
<div class="grow"><label for="ann-aud">Appareils</label><select id="ann-aud" name="audience">${opts(AUD, "all")}</select></div></div>
<div class="row"><div class="grow"><label for="ann-below">Versions antérieures à <span class="muted">(vide : toutes)</span></label><input id="ann-below" name="below" maxlength="20" pattern="[0-9]{1,4}([.][0-9]{1,4}){0,3}" placeholder="1.2.79"/></div>
<div class="grow"><label for="ann-action">Bouton</label><select id="ann-action" name="action">${opts(ACTION, "none")}</select></div></div>
<div class="row"><div class="grow"><label for="ann-start">Début <span class="muted">(vide : tout de suite)</span></label><input id="ann-start" name="start" type="datetime-local"/></div>
<div class="grow"><label for="ann-end">Fin <span class="muted">(vide : jusqu'à arrêt)</span></label><input id="ann-end" name="end" type="datetime-local"/></div></div>
<input type="hidden" name="startMs"/><input type="hidden" name="endMs"/>
<p class="muted small">Heures saisies dans le fuseau de ce navigateur.</p>
<div class="row"><button type="submit">Programmer l'alerte</button></div></form>
<div class="section-h mt16"><h3>Alertes <span class="count">${list.length}</span></h3></div>
${rows || `<p class="muted small">Aucune alerte.</p>`}</section>`;
}

/** Heures locales du formulaire d'alerte → millisecondes (le serveur ne connaît pas le fuseau du navigateur). */
export const ANN_SCRIPT = `document.querySelectorAll('.ann-form').forEach(function(f){f.addEventListener('submit',function(){
[['start','startMs'],['end','endMs']].forEach(function(p){var v=f.querySelector('[name='+p[0]+']').value;f.querySelector('[name='+p[1]+']').value=v?String(new Date(v).getTime()):'';});});});`;

// Page de choix de l'APK Android (/apk), pensée pour l'appli Downloader : liens par processeur au lieu du seul
