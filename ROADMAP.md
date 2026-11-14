# Feuille de route · Roadmap

Ce qui est prévu ou à l'étude pour Ultra TV. Rien n'est promis à une date : l'ordre peut changer selon les retours.
*What is planned or being studied. No dates are promised: the order may change with feedback.*

---

## Français

### Disponible, non testé

**TV Samsung (Tizen) et LG (webOS)** — paquets publiés sur [GitHub](https://github.com/vantaflow/ultra-tv/releases) (`.wgt` Samsung, `.ipk` LG), procédure dans [docs/tv.md](docs/tv.md).
- Même application que la version bureau, pilotée à la télécommande. Samsung 2020+ (Tizen 5.5+), LG 2021+ (webOS 6+).
- Pas encore testés sur un vrai téléviseur : les retours sont les bienvenus.
- Pas disponibles dans les boutiques Samsung et LG : installation en mode développeur (Samsung : certificat lié au téléviseur ; LG : session développeur à renouveler toutes les 50 h).

### À l'étude

**iPhone, iPad et Apple TV (iOS / tvOS)** — faisable techniquement, coûteux à distribuer.
- Deux voies : envelopper l'application web dans une app native (WebView) ou écrire une app native (Swift). La première réutilise l'existant ; la seconde offre le meilleur lecteur (AVPlayer) mais représente un projet à part entière.
- Lecture : HLS natif excellent ; le MPEG-TS (fréquent en IPTV) demande une conversion côté appareil (possible depuis iOS 17.1) ou un lecteur dédié.
- Distribution : compte Apple Developer payant (99 $/an) et validation App Store ; sans compte, seule l'installation par AltStore/Sideloadly est possible, avec une signature à refaire tous les 7 jours — peu praticable pour le grand public.
- Décision en attente : pas de développement tant que la question du compte Apple n'est pas tranchée.

**Serveurs multimédias personnels (Jellyfin, Emby) et partages NAS**
- Parcourir en direct la bibliothèque d'un serveur Jellyfin ou Emby, avec sa propre recherche : rien à synchroniser, quelle que soit la taille de la bibliothèque.
- Ajouter des partages réseau (NAS : SMB, NFS, DLNA) comme source de films et de séries.

**Espace Sport**
- Scores en direct et calendriers d'une quarantaine de ligues : football (Premier League, Ligue des champions, MLS…), football américain, basket, baseball, hockey, sports universitaires américains, cricket, rugby, football australien, etc.
- Mes équipes : suivre une équipe pour lui donner ses propres rangées et une étoile partout dans l'appli.
- Feuilles de match, statistiques des joueurs, meilleurs joueurs et classements.
- Aperçu du match dans un coin de l'écran, plein écran en appuyant une fois de plus sur OK.
- Mode sans spoiler : les scores restent masqués jusqu'à ce qu'on choisisse de les voir.

### Idées en attente
- Filtrage par profil pour les rangées « Parce que vous avez regardé » et « Sport en direct » (Android) et pour la recherche de programmes, les favoris et le Sport (bureau).
- Renommer un profil depuis l'application de bureau.

---

## English

### Available, untested

**Samsung (Tizen) and LG (webOS) TVs** — packages published on [GitHub](https://github.com/vantaflow/ultra-tv/releases) (`.wgt` for Samsung, `.ipk` for LG), install guide in [docs/tv.md](docs/tv.md).
- Same app as the desktop version, driven by the remote. Samsung 2020+ (Tizen 5.5+), LG 2021+ (webOS 6+).
- Not yet tested on a real TV: feedback is welcome.
- Not in the Samsung or LG stores: developer-mode install (Samsung: certificate tied to the TV; LG: developer session to renew every 50 h).

### Being studied

**iPhone, iPad and Apple TV (iOS / tvOS)** — technically feasible, costly to distribute.
- Two routes: wrap the web app in a native app (WebView) or write a native app (Swift). The first reuses what exists; the second gives the best player (AVPlayer) but is a project of its own.
- Playback: native HLS is excellent; MPEG-TS (common in IPTV) needs on-device remuxing (possible since iOS 17.1) or a dedicated player.
- Distribution: paid Apple Developer account ($99/year) and App Store review; without one, only AltStore/Sideloadly installs are possible, re-signed every 7 days — not practical for most people.
- On hold: no development until the Apple account question is settled.

**Personal media servers (Jellyfin, Emby) and NAS shares**
- Browse a Jellyfin or Emby server's library live, with its own search: nothing to sync, whatever the library size.
- Add network shares (NAS: SMB, NFS, DLNA) as a source of movies and series.

**Sports hub**
- Live scores and schedules for about 40 leagues: soccer (Premier League, Champions League, MLS…), American football, basketball, baseball, hockey, US college sports, cricket, rugby, Australian football and more.
- My teams: follow a team to get its own rows and a star everywhere in the app.
- Box scores, player stats, leaders and standings.
- Game preview in a corner of the screen, full screen with one more press of OK.
- Spoiler-free mode: scores stay hidden until you choose to see them.

### Ideas on hold
- Per-profile filtering for the "Because you watched" and "Live sport" rows (Android), and for programme search, favourites and Sport (desktop).
- Renaming a profile from the desktop app.
