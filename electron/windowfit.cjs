"use strict";
// Dimensionnement de la fenetre principale : calcul PUR (aucun acces a Electron), donc testable.
// Probleme corrige : a 125 % de zoom Windows sur un 1366x768, la zone utile ne fait qu'environ
// 614 px de haut ; une fenetre fixee a 1280x800 (minHeight 640) etait coupee en bas.

const DEFAULT_WIDTH = 1280;
const DEFAULT_HEIGHT = 800;
const PREFERRED_MIN_WIDTH = 900;
const PREFERRED_MIN_HEIGHT = 540;
// Marge laissee autour de la fenetre (barre de titre, bordures, ombre) : en DIP.
const MARGIN = 16;
// Taille minimale absolue de securite si la zone de travail est invraisemblable.
const FLOOR = 320;

const isNum = (n) => typeof n === "number" && Number.isFinite(n);

function validArea(a) {
  return !!a && isNum(a.x) && isNum(a.y) && isNum(a.width) && isNum(a.height) && a.width > 0 && a.height > 0;
}

function clamp(v, lo, hi) {
  return Math.min(Math.max(v, lo), hi);
}

// Les bornes Electron sont des entiers en DIP ; arrondir evite les demi-pixels a 125 %.
const R = (n) => Math.round(n);

/**
 * @param {{x:number,y:number,width:number,height:number}} workArea zone de travail (DIP) de l'ecran cible
 * @param {number} scaleFactor facteur d'echelle de l'ecran (1, 1.25, 1.5...) ; informatif : workArea est deja en DIP
 * @param {{x?:number,y?:number,width?:number,height?:number}|null} [saved] taille/position precedente
 * @returns {{bounds:{x:number,y:number,width:number,height:number}, minWidth:number, minHeight:number}}
 */
function computeWindowFit(workArea, scaleFactor, saved) {
  const wa = validArea(workArea) ? workArea : { x: 0, y: 0, width: DEFAULT_WIDTH, height: DEFAULT_HEIGHT };
  // Marge reduite sur les tout petits ecrans pour ne pas manger la zone utile.
  const margin = wa.height < 600 ? 8 : MARGIN;
  const maxW = Math.max(FLOOR, wa.width);
  const maxH = Math.max(FLOOR, wa.height);

  // Jamais de minimum superieur a la zone de travail : la fenetre ne depasse pas l'ecran.
  const minWidth = R(Math.min(PREFERRED_MIN_WIDTH, maxW));
  const minHeight = R(Math.min(PREFERRED_MIN_HEIGHT, maxH));

  const hasSavedSize = !!saved && isNum(saved.width) && isNum(saved.height) && saved.width > 0 && saved.height > 0;
  const wantW = hasSavedSize ? saved.width : DEFAULT_WIDTH;
  const wantH = hasSavedSize ? saved.height : DEFAULT_HEIGHT;
  // Taille sauvegardee : bornee a la zone entiere ; taille par defaut : bornee avec marge.
  const capW = hasSavedSize ? maxW : Math.max(minWidth, maxW - margin * 2);
  const capH = hasSavedSize ? maxH : Math.max(minHeight, maxH - margin * 2);
  const width = R(clamp(wantW, minWidth, Math.min(capW, maxW)));
  const height = R(clamp(wantH, minHeight, Math.min(capH, maxH)));

  let x;
  let y;
  const hasSavedPos = !!saved && isNum(saved.x) && isNum(saved.y);
  if (hasSavedPos) {
    // Position sauvegardee : on la ramene dans la zone de travail actuelle (ecran debranche, resolution changee).
    x = clamp(saved.x, wa.x, wa.x + wa.width - width);
    y = clamp(saved.y, wa.y, wa.y + wa.height - height);
  } else {
    x = wa.x + (wa.width - width) / 2;
    y = wa.y + (wa.height - height) / 2;
  }
  return { bounds: { x: R(x), y: R(y), width, height }, minWidth, minHeight };
}

module.exports = { computeWindowFit, DEFAULT_WIDTH, DEFAULT_HEIGHT, PREFERRED_MIN_WIDTH, PREFERRED_MIN_HEIGHT };
