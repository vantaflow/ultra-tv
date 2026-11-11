"use strict";
// Bande-annonce intégree : lecteur officiel YouTube (iframe youtube-nocookie) dans une fenetre de l'appli.
// Module PUR (aucun acces a Electron), donc testable.

// Seules origines autorisees en iframe (directive frame-src de la CSP).
const TRAILER_FRAME_SRC = ["https://www.youtube-nocookie.com", "https://www.youtube.com"];

// Requetes du lecteur integre auxquelles on ajoute une origine (voir ci-dessous).
const TRAILER_EMBED_URLS = ["https://www.youtube-nocookie.com/embed/*", "https://www.youtube.com/embed/*"];

// Le protocole app:// n'est pas http(s) : Chromium n'y envoie aucun Referer, et le lecteur YouTube refuse alors
// l'integration (erreurs 152/153). On fournit l'origine stable du site du projet, uniquement si le Referer manque
// (en developpement, la page http://localhost en envoie deja un).
const TRAILER_REFERER = "https://vantaflow.github.io/ultra-tv/";

function withTrailerReferer(headers) {
  const out = { ...headers };
  const has = Object.keys(out).some((k) => k.toLowerCase() === "referer" && out[k]);
  if (!has) out.Referer = TRAILER_REFERER;
  return out;
}

module.exports = { TRAILER_FRAME_SRC, TRAILER_EMBED_URLS, TRAILER_REFERER, withTrailerReferer };
