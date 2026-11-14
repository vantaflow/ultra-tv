# Feuille de route · Roadmap

Ce qui est prévu ou à l'étude pour Ultra TV. Rien n'est promis à une date : l'ordre peut changer selon les retours.
*What is planned or being studied. No dates are promised: the order may change with feedback.*

---

## Français

### En préparation

**TV Samsung (Tizen) et LG (webOS)** — paquets prêts (`tizen/`, `webos/`, [docs/tv.md](docs/tv.md)), pas encore testés sur un vrai téléviseur.
- Les deux systèmes font tourner une application web dans un Chromium intégré au téléviseur : on réutilise l'application web/bureau existante (React), empaquetée en `.wgt` (Samsung) et `.ipk` (LG). Pas de réécriture.
- Cibles réalistes : Samsung 2020+ (Tizen 5.5 à 8) et LG 2021+ (webOS 6+). Les modèles plus anciens (Chromium 38 à 66) sont trop limités.
- Lecture : HLS et MPEG-TS via les lecteurs web (hls.js, mpegts.js) ou le lecteur natif HLS du téléviseur. Le DNS personnalisé, le proxy local et l'enregistrement du bureau ne sont pas disponibles sur ces TV.
- Le vrai frein est la **distribution**, pas la technique :
  - installation manuelle : mode développeur obligatoire (Samsung : certificat lié au téléviseur ; LG : session développeur à renouveler toutes les 50 h) — réservé aux utilisateurs avancés ;
  - boutiques officielles (Samsung Seller Office, LG Seller Lounge) : compte éditeur et validation à chaque version ; d'autres lecteurs IPTV « sans contenu » y sont acceptés, donc envisageable.
- Reste à faire : tests sur de vrais téléviseurs (télécommande, lecture, performances), puis choix du mode de distribution.

### À l'étude

**iPhone, iPad et Apple TV (iOS / tvOS)** — faisable techniquement, coûteux à distribuer.
- Deux voies : envelopper l'application web dans une app native (WebView) ou écrire une app native (Swift). La première réutilise l'existant ; la seconde offre le meilleur lecteur (AVPlayer) mais représente un projet à part entière.
- Lecture : HLS natif excellent ; le MPEG-TS (fréquent en IPTV) demande une conversion côté appareil (possible depuis iOS 17.1) ou un lecteur dédié.
- Distribution : compte Apple Developer payant (99 $/an) et validation App Store ; sans compte, seule l'installation par AltStore/Sideloadly est possible, avec une signature à refaire tous les 7 jours — peu praticable pour le grand public.
- Décision en attente : pas de développement tant que la question du compte Apple n'est pas tranchée.

### Idées en attente
- Filtrage par profil pour les rangées « Parce que vous avez regardé » et « Sport en direct » (Android) et pour la recherche de programmes, les favoris et le Sport (bureau).
- Renommer un profil depuis l'application de bureau.

---

## English

### In preparation

**Samsung (Tizen) and LG (webOS) TVs** — packages ready (`tizen/`, `webos/`, [docs/tv.md](docs/tv.md)), not yet tested on a real TV.
- Both run a web app inside the TV's built-in Chromium: the existing web/desktop app (React) is reused and packaged as `.wgt` (Samsung) and `.ipk` (LG). No rewrite.
- Realistic targets: Samsung 2020+ (Tizen 5.5 to 8) and LG 2021+ (webOS 6+). Older models (Chromium 38 to 66) are too limited.
- Playback: HLS and MPEG-TS through web players (hls.js, mpegts.js) or the TV's native HLS player. Custom DNS, the local proxy and desktop recording are not available on these TVs.
- The real hurdle is **distribution**, not technology:
  - manual install: developer mode required (Samsung: certificate tied to the TV; LG: developer session to renew every 50 h) — for advanced users only;
  - official stores (Samsung Seller Office, LG Seller Lounge): publisher account and review for every release; other "no content" IPTV players are accepted there, so it is an option.
- Next: testing on real TVs (remote, playback, performance), then choosing how to distribute.

### Being studied

**iPhone, iPad and Apple TV (iOS / tvOS)** — technically feasible, costly to distribute.
- Two routes: wrap the web app in a native app (WebView) or write a native app (Swift). The first reuses what exists; the second gives the best player (AVPlayer) but is a project of its own.
- Playback: native HLS is excellent; MPEG-TS (common in IPTV) needs on-device remuxing (possible since iOS 17.1) or a dedicated player.
- Distribution: paid Apple Developer account ($99/year) and App Store review; without one, only AltStore/Sideloadly installs are possible, re-signed every 7 days — not practical for most people.
- On hold: no development until the Apple account question is settled.

### Ideas on hold
- Per-profile filtering for the "Because you watched" and "Live sport" rows (Android), and for programme search, favourites and Sport (desktop).
- Renaming a profile from the desktop app.
