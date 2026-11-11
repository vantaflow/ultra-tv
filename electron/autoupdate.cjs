"use strict";
// Réglage « Vérifier les mises à jour automatiquement » (activé par défaut), mémorisé dans userData/autoupdate.json.
// Coupé : aucune vérification automatique (5 s après le lancement, puis toutes les 6 h) ; le bouton
// « Rechercher une mise à jour » des Réglages reste utilisable.

const fs = require("node:fs");

const FIRST_CHECK_MS = 5000;
const EVERY_MS = 6 * 3600_000;

/** Lecture tolérante : fichier absent, illisible ou invalide => activé (valeur par défaut). */
function loadAutoUpdate(file, readFile = fs.readFileSync) {
  try {
    return JSON.parse(readFile(file, "utf8")).enabled !== false;
  } catch {
    return true;
  }
}

function saveAutoUpdate(file, enabled, writeFile = fs.writeFileSync) {
  try {
    writeFile(file, JSON.stringify({ enabled: !!enabled }));
    return true;
  } catch {
    return false;
  }
}

/**
 * Programme les vérifications automatiques. Le réglage est relu à chaque échéance : l'activer ou le couper
 * prend effet tout de suite, sans redémarrage. Renvoie une fonction d'arrêt.
 */
function scheduleAutoChecks({ check, isEnabled, setTimeoutFn = setTimeout, setIntervalFn = setInterval, clearTimeoutFn = clearTimeout, clearIntervalFn = clearInterval }) {
  const run = () => { if (isEnabled()) void check(); };
  const t = setTimeoutFn(run, FIRST_CHECK_MS);
  const i = setIntervalFn(run, EVERY_MS);
  return () => { clearTimeoutFn(t); clearIntervalFn(i); };
}

module.exports = { loadAutoUpdate, saveAutoUpdate, scheduleAutoChecks, FIRST_CHECK_MS, EVERY_MS };
