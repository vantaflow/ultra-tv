# Changelog

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions suivant `VERSION`.

## [Non publié]

### Modifié
- Worker : l'ancienne adresse workers.dev ne sert plus le site (réponse 410 avec la nouvelle adresse) ; seules l'API des applis installées, les pages ouvertes par leur QR code (/pair, /envoyer, redirigées) et le retour Trakt restent servis. Trakt revient toujours par l'adresse déclarée chez Trakt puis poursuit sur tv.weedy.dpdns.org : aucune modification de l'application Trakt nécessaire.

### Modifié
- Bureau : nouvelle interface (design v2). Thèmes sombre et clair redessinés (Réglages › Affichage › Thème : Automatique, Sombre ou Clair), survol à la souris et focus clavier au même rendu, animation d'ouverture de 700 ms qui ne retarde jamais le chargement.
  - Surcouche du Direct : identité de la chaîne en haut (grand numéro, logo, nom, pastilles En direct, qualité, HDR si annoncé, Favori) et horloge ; en bas, cinq chaînes récentes, le programme en cours avec le temps restant, une carte « Ensuite », le ruban de la soirée (précédent, en cours, suivants) et une rangée de boutons ronds dont seul celui qui a le focus ou le survol affiche son libellé. Mêmes touches et mêmes étages qu'avant.
  - Liste des chaînes du lecteur : feuille à droite avec onglets Chaînes / Favoris, catégories et lignes (numéro, logo, nom, programme en cours, progression, « En cours »).
  - Guide : aperçu et programme choisi en haut avec « Regarder », puces des jours et puces de catégories (Favoris, Tout et chaque catégorie avec son nombre) qui filtrent la grille, ligne « maintenant » avec l'heure.
  - Accueil (salutation, raccourcis, « En direct maintenant », « Reprendre »), Films et Séries (« À la une » et « Reprendre »), fiches film et série, lecteurs film et série (bulle de temps, reste et heure de fin, carte « Épisode suivant ») et Réglages (rubriques avec résumé, lignes regroupées en une carte).
  - Outils : `web/scripts/screens.mjs` produit des captures 1920×1080 en sombre et en clair à partir d'un catalogue fictif (build avec `VITE_DEMO=1`, jamais livré).
- Android : nouvelle interface, première partie (thèmes, lecteur du Direct, animation d'ouverture). Les autres écrans suivront.
  - Thèmes sombre et clair redessinés (nouvelles couleurs, focus en fond clair sur thème sombre et inversement). Réglages › Affichage › Thème : Automatique (suit le système ; sur une TV, Sombre), Sombre ou Clair. Le lecteur reste toujours sombre, posé sur la vidéo.
  - Surcouche du Direct : identité de la chaîne en haut à gauche (numéro, logo, nom, pastilles En direct, qualité, HDR10 / HLG / Dolby Vision, Favori) et horloge à droite ; en bas, la rangée « Récentes » (Gauche / Droite pour parcourir, Haut depuis la barre, Bas ou Retour pour revenir), le programme en cours, une carte « Ensuite » avec « Me rappeler » et « Enregistrer », le ruban de la soirée (précédent, en cours avec sa progression, suivants) et une barre de boutons ronds dont seul celui qui a le focus affiche son libellé. Le ruban est masqué sans guide. Mêmes touches et mêmes comportements qu'avant.
  - Liste des chaînes du lecteur : feuille flottante à droite avec onglets Chaînes / Favoris, colonne des catégories et lignes de chaînes (numéro, logo, nom, programme en cours, progression, « En cours »). Le bloc « Sélection » disparaît : la ligne de la chaîne affiche déjà son programme.
  - Animation d'ouverture de 700 ms (le logo apparaît, le « play » s'illumine, puis s'efface vers l'accueil). Elle ne retarde jamais le chargement ; simple fondu de 200 ms sur les box à faible mémoire ; aucune animation si l'appareil les désactive.
  - Réglages redessinés : à gauche le grand titre et la liste des rubriques, chacune avec un résumé d'état d'une ligne (nombre de sources et types, dernière synchronisation, thème et Sport, langue, version…), la rubrique ouverte en couleurs de focus ; à droite le titre de la rubrique, sa description et les réglages regroupés dans des cartes arrondies (libellé et aide à gauche, interrupteur à l'accent ou valeur et chevron à droite, ligne focalisée en couleurs de focus). Sur téléphone : même style, liste puis détail. Thèmes sombre et clair. Mêmes rubriques, mêmes options et mêmes comportements qu'avant.
  - Films et Séries (télécommande) : onglets de catégories, grande vignette « À la une » (titre, année, note, Regarder) puis rangées d'affiches ; l'affiche focalisée est cerclée et montre son titre dessous. Même rendu en thème clair. La liste de catégories + grille, les filtres et la navigation restent inchangés.
  - Fiche film : bouton retour rond, pastilles (année, genres, qualité, langues), grand titre, notes (IMDb, critiques, durée), synopsis, barre de reprise, actions (Reprendre, Depuis le début, Bande-annonce, Favori) et Distribution juste dessous. Fiche série : colonne de gauche (titre, infos, synopsis, actions, Distribution), saisons en onglets et liste d'épisodes à droite (vignette, titre, « Vu » / temps restant, résumé, progression).
  - Lecteur des films et séries : titre et pastilles (qualité, HDR10 / HLG / Dolby Vision, codec, audio) en haut avec l'horloge ; barre de lecture avec partie déjà chargée, repères d'intro et de générique, bulle de temps après un saut ; « reste X · fin à HH:MM » ; boutons ronds à gauche (seul celui qui a le focus montre son libellé) et pastilles à droite (Pistes, Sous-titres, Vitesse, Format, Veille). « Passer l'intro » redessiné, et carte « Épisode suivant dans N s » pendant les 15 dernières secondes d'un épisode (le passage à la suite reste celui de la fin de lecture). Le lecteur reste toujours sombre.
- Android : nouvelle interface, deuxième partie (Accueil, Guide TV, Sport), thèmes sombre et clair.
  - Accueil TV : salutation selon l'heure avec le nombre de sources, de chaînes, de films et de séries, et l'heure ; « À la une » en carte arrondie (Regarder / Plus d'infos) avec, à droite, six raccourcis en grille (Direct, Guide TV, Films, Séries, Favoris, et Sport quand la rubrique est activée, avec son nombre de directs) ; rangée « En direct maintenant » (numéro, nom, horaire, programme en cours et sa progression) ; rangée « Reprendre » en cartes avec le titre sur l'image. Les chaînes de l'accueil se lancent toujours avec le contexte Direct (zapping, fiche Info, pause en différé).
  - Guide TV : aperçu de la chaîne à gauche, programme sélectionné à droite avec « Regarder », « Me rappeler » et « Enregistrer » (les deux derniers seulement quand le programme le permet) ; jours, puis **catégories de chaînes** (Favoris, Toutes et les catégories réelles de votre source avec leur nombre de chaînes) qui filtrent la grille ; colonne des chaînes en cartes (numéro, logo, nom), programme en cours teinté, ligne et pastille « maintenant ». Le délai de l'aperçu, le dialogue d'actions (appui sur un programme) et le défilement du temps sont inchangés. La barre d'aide des touches est retirée de l'écran.
  - Sport : onglets de discipline (Tout, puis Football, Basket, Tennis, Combat, Moteur, Rugby selon ce que le guide contient), cartes « En direct maintenant » (compétition, équipes, minute de jeu, chaîne, progression), liste « À venir · 48 h » avec « Me rappeler » (qui devient « Rappel activé ») et « Enregistrer », grille « Chaînes sport » avec le programme en cours.
  - Rendu fidèle sur le thème clair ; aucun flou, animations minimales, listes paresseuses (box à faible mémoire inchangées). Disposition tactile adaptée au même style.

## [1.2.97] — 2026-11-12 (Android)

### Corrigé
- Android : les vidéos HDR (HDR10, HLG, Dolby Vision) ne paraissent plus ternes sur NVIDIA Shield, téléphones et tablettes, ni après un repli sur VLC. La TextureView, utilisée par défaut sur ces appareils, ne sait pas sortir de HDR : sur un écran HDR, avec le réglage Surface vidéo sur « Auto », la lecture HDR est relancée automatiquement en SurfaceView (position conservée en VOD). Si la SurfaceView n'affiche aucune image, l'appli revient à la TextureView et s'en souvient pour l'appareil. Pour un contenu HDR, le repli privilégie le décodage matériel avant le décodage logiciel.

### Ajouté
- Android (TV et tactile) : rubrique **Sport** facultative, désactivée par défaut (Réglages › Affichage › Rubrique Sport). Une fois activée, une entrée « Sport » apparaît dans le menu (rail TV, rail et feuille « Plus » tactiles) et l'accueil TV affiche une rangée « Sport en direct ». L'écran regroupe « En direct maintenant » (programmes de sport en cours, chaînes de sport d'abord ; OK lance la chaîne avec le zapping habituel), « À venir (48 h) » par jour avec « Me rappeler » et « Enregistrer », et « Chaînes sport ». Tout vient du guide et des catégories de votre propre source (aucun service externe) ; la détection est insensible à la casse et aux accents (français, anglais, espagnol, arabe, polonais). Sans guide, l'écran l'explique et renvoie vers les réglages de synchronisation.
- Android : les anomalies de lecture (bascule de moteur ou de décodeur, échec final) sont remontées dans les journaux du serveur, avec le type d'erreur, le moteur, la résolution et le codec (jamais d'adresse), pour repérer les problèmes que personne ne signale. Appareils appairés avec la télémétrie active seulement.

## [1.2.96] — 2026-11-12 (Android) · Bureau 1.2.46 · Worker

### Modifié
- Nouvelle adresse principale du Worker : **https://tv.weedy.dpdns.org** (tableau de bord, `/apk`, `/envoyer`, `/download/*`), car `workers.dev` est bloqué sur certains réseaux et box. Android et bureau basculent automatiquement sur l'autre adresse en cas de panne réseau (DNS, connexion, TLS, délai) et retiennent celle qui répond ; l'ancienne adresse `ultratv-config.khalilbenaz.workers.dev` reste active et l'ancien défaut enregistré dans les Réglages est traité comme « défaut ». Nouveau code Downloader **5189921** : il ouvre `tv.weedy.dpdns.org/apk` (remplace 5447141). README, site et messages de l'appli mis à jour.

## [1.2.95] — 2026-11-12 (Android)

### Corrigé
- Android (TV, box, Fire TV) : plus de bascule à tort en « décodage logiciel » au démarrage d'une chaîne, ni de boucle entre moteurs sur les flux 4K. La vérification « image noire » copiait la surface vidéo, que les box Amlogic et Fire TV affichent sur un plan séparé : la copie revenait noire alors que l'image s'affichait. Elle ne s'applique plus qu'aux téléphones (TextureView).

## [1.2.94] — 2026-11-12 (Android) · Worker

### Corrigé
- Android (TV), Direct : après « Déplacer » (Valider ou Annuler), le focus reste sur la chaîne déplacée au lieu de revenir sur la catégorie « Favoris ».

### Modifié
- Site : l'appairage passe en premier (tableau de bord : Appareils et formulaire d'appairage avant les sources ; navigation ; « Pour bien démarrer » ; raccourci de l'appli installée). Captures du site (lecteur TV à trois étages, espace cloud) mises à jour.
- Nouveau code Downloader **5447141** : il ouvre la page `/apk` (choix 64 bits, 32 bits ou universel). README, site et messages de mise à jour de l'appli renvoient vers `/apk` au lieu de l'APK universel de 217 Mo.

## [1.2.93] — 2026-11-12 (Android)

### Corrigé
- Android (TV), guide : l'aperçu vidéo attend 1,5 s sans bouger avant de lire la chaîne survolée ; faire défiler le guide ne « zappe » plus à chaque ligne.
- Android : l'appli ne restait plus bloquée au lancement après l'installation d'une version plus ancienne par-dessus une plus récente (« A migration from 22 to 21 was required ») ; la base locale est reconstruite et le catalogue se resynchronise.
- Android : un rapport de plantage envoyé au démarrage suivant porte la version qui a planté, et non plus la version installée au moment de l'envoi.

## [1.2.92] — 2026-11-12 (Android) · Bureau 1.2.45 · Worker

### Ajouté
- Site : application web installable (PWA) sur téléphone et ordinateur — manifeste, icônes Android et iPhone, raccourcis « Envoyer une source » et « Associer un appareil », page hors ligne. Le service worker ne garde jamais une page du compte en cache.

### Ajouté
- Administration du Worker refondue : onglets *Vue d'ensemble*, *Comptes*, *Sans compte*, *Appareils*, *Journaux*, *Plantages* et *Alertes* (une URL chacun), chiffres clés, répartitions par application, version et pays, tableaux paginés (50 par page) avec tri, recherche et filtres (application, version, pays, période), dates relatives avec date exacte en info-bulle, pays en drapeau + code. Les anciennes adresses `/logs` et `/crashes` restent valables.
- Installations sans compte : l'application utilisée (Android TV, Android mobile, Windows, macOS, Linux) et le modèle de l'appareil sont relevés et visibles dans l'administration, avec version, pays, premier et dernier relevé (migration D1 `0002_anon_app.sql`).
- Bureau (Windows, macOS, Linux) : comptage anonyme d'installation (identifiant aléatoire local, version, système ; au plus une fois par 20 h, appareil non appairé), désactivable dans Réglages › À propos › *Télémétrie anonyme* (activée par défaut ; en, fr, es, ar, pl).
- Android : le relevé anonyme indique l'application (TV ou mobile) et le modèle de l'appareil.
- Bureau : le lecteur du Direct reprend l'OSD à trois étages d'Android TV : programme en cours (description, horaires, « Ensuite »), « Chaînes récentes » (10 au plus) et barre de commandes (avec Favori). Au clavier : Entrée ouvre la liste des chaînes, ← / → chaîne précédente / suivante, ↑ / ↓ OSD complet (flèches pour se déplacer entre étages et boutons), 0-9 saisie d'un numéro de chaîne, Échap referme les étages dans l'ordre puis quitte. Simple bandeau 5 s après un zap, OSD complet 8 s au clavier (3 s à la souris, inchangé). Le lecteur des films et séries ne change pas.
- Android : ordre personnel des chaînes, propre à chaque profil. Dans le Direct, appui long sur une chaîne › « Déplacer » (Haut / Bas pour la déplacer, OK pour valider, Retour pour annuler ; boutons au toucher) dans une catégorie ou dans les favoris, et « Réinitialiser l’ordre » pour revenir à l’ordre naturel. Le zapping (Direct, tiroir du lecteur, accueil) suit l’ordre affiché. Nouvelle table locale `channel_order` (base en version 22) ; l’ordre n’est pas synchronisé avec le cloud.

### Corrigé
- Bureau : limite de connexions simultanées du fournisseur (comptes à 1 connexion). Toute ouverture de flux du lecteur passe par un « emplacement de connexion » unique : l'ancien flux est entièrement libéré (hls.js / mpegts.js détruits, source du `<video>` détachée) avant d'ouvrir le suivant, avec un délai de grâce configurable (0 par défaut). Une erreur de limite (HTTP 403/429/458/509 ou message « max connections » / « too many connections ») stoppe la chaîne de repli de format, retente une seule fois après 1,5 s, puis affiche « Limite de connexions atteinte (x/y) : fermez l'autre lecteur ou appareil, puis réessayez » (compteurs lus via `player_api.php`) avec le bouton Réessayer, dans les 5 langues du bureau. L'ancien message générique reste pour les autres échecs. Android : déjà une seule session partagée (aperçu + plein écran), inchangé.
- Android TV, OSD du Direct : la rangée « Chaînes récentes » n'affiche plus deux fois la même chaîne (ex. « TF1 1 » en double) ni la chaîne en cours sous une autre variante ; dédoublonnage par identifiant et par nom affiché, plus récente d'abord, 10 au plus.

### Modifié
- Site du Worker (Cloudflare) refondu avec un seul système de design : gabarit commun (en-tête, navigation selon le contexte public, espace utilisateur ou administration, menu du compte, pied de page avec GitHub et confidentialité), thèmes sombre et clair à jetons CSS, navigation mobile en bas, tableaux défilant dans leur cadre. Tableau de bord réorganisé en *Vue d'ensemble*, *Sources*, *Appareils*, *Intégrations* (Trakt, OpenSubtitles) et *Compte et sécurité*, avec étapes de démarrage et états vides ; pages de connexion, inscription, appairage, `/envoyer` (en, fr, es, ar, pl, avec étapes) et `/apk` harmonisées ; administration alignée sur le même système (onglets, filtres, pagination, graphiques et export CSV conservés). Aucune route, champ de formulaire ni route `/api/*` modifiés.
- Bande-annonce (Android et bureau) : plus de sous-titres automatiques de YouTube par-dessus la vidéo.

## [Bureau 1.2.44] — 2026-11-12

Windows, macOS, Linux. Détail dans la section [1.2.91] (lignes « Bureau : »).

### Corrigé
- Catégories qui disparaissaient après une mise à jour (reconstruction automatique du catalogue au démarrage).
- Fenêtre bornée à l'écran (petits écrans, mise à l'échelle Windows) et écrans qui défilent.
- Contrôles du lecteur qui réapparaissaient sans mouvement de souris (Windows).
- Catalogue visible pendant la synchro, avec la progression.
- Lecteur : plus de chargement sans fin quand un flux ne démarre pas.
- Guide complémentaire rapproché aussi par nom de chaîne.

### Ajouté
- Guide ouvert directement, chargé progressivement, chaînes triées par numéro (favoris compris).
- Bande-annonce lue dans l'appli (lecteur intégré, nos commandes).

## [1.2.91] - 2026-11-12

### Ajouté
- Alertes de l'administrateur dans la langue de l'application : l'appli envoie sa langue, le Worker renvoie la traduction si elle existe (champ `i18n`), sinon le texte par défaut.
- Page `/apk` du Worker : choix de l'APK Android (64 bits recommandé, 32 bits, universel en dernier recours) pour l'appli Downloader, au lieu du seul APK universel d'environ 220 Mo. Les liens `/download/*` répondent aussi aux requêtes HEAD.

- Langues : polonais (premier jet, relecture bienvenue sur Weblate)
- Lien « Aider à traduire » dans Réglages › Langue (TV avec QR code, mobile, bureau)
- Android : la bande-annonce des films et des séries se lit DANS l'appli, dans une fenêtre plein écran et un lecteur à nous, au lieu d'ouvrir l'appli ou le site YouTube. Le lecteur officiel YouTube (IFrame Player API, hôte youtube-nocookie) joue la vidéo dans une WebView sans ses commandes ; les commandes sont les nôtres : lecture / pause, −10 s / +10 s, barre de progression avec temps, titre, Fermer, masquées après 3 s. Pilotage complet à la télécommande (focus initial sur Lecture / Pause, Gauche / Droite = −10 / +10 s, touches média, Retour revient à la fiche) et au toucher. Erreurs claires dans les 5 langues (vidéo privée ou supprimée, intégration interdite par le propriétaire, WebView absente sur certaines box) avec Fermer ; le lien en QR code n'est proposé que si l'utilisateur le choisit. La clé YouTube se lit dans `watch?v=`, `youtu.be/`, `embed/`, `shorts/` ou en clé brute (fonction commune `YoutubeKey`). La WebView est libérée à la fermeture et mise en pause en arrière-plan.
- Bureau : bouton « Bande-annonce » sur les fiches des films et des séries (quand la source donne un lien, sinon quand TMDB, via le Worker, connaît la bande-annonce du titre). Elle se lit dans l'appli, en plein écran : lecteur YouTube intégré (youtube-nocookie) piloté par nos commandes (lecture / pause, −10 s / +10 s, progression, Fermer), clavier Espace, ← / →, Échap, commandes masquées après 3 s sans mouvement réel de la souris. La CSP d'Electron n'autorise que `frame-src` vers youtube-nocookie.com et youtube.com.

### Modifié
- Android (TV), direct sans OSD : Gauche / Droite passent à la chaîne précédente / suivante, Haut et Bas affichent l'OSD, OK ouvre la liste des chaînes (en différé masqué, Gauche / Droite reculent ou avancent de 30 s).
- Android (TV) : l'OSD complet du direct reste affiché 8 s sans touche (5 s pour le simple bandeau), le temps de lire le programme et de choisir.

### Corrigé
- Android : une chaîne lancée depuis la rangée « Direct » de l'accueil ou depuis la recherche s'ouvre désormais comme un direct (OSD du direct, OK ouvre la liste des chaînes, haut / bas / gauche / droite zappent dans sa catégorie ou ses favoris). Avant, le lecteur gardait le contexte précédent : commandes d'un film, OK mettait en pause.
- Worker : la page `/envoyer` (celle du QR « Depuis le téléphone ») n'existait qu'en français : un utilisateur non francophone croyait lire une autre langue. Elle est maintenant disponible en anglais, français, espagnol, arabe (sens d'écriture droite à gauche) et polonais, messages de validation et d'erreur compris. Langue choisie dans l'ordre : paramètre `l` de l'adresse (`/envoyer?c=CODE&l=en`), cookie du sélecteur, en-tête `Accept-Language` (qualités `q=` respectées), sinon anglais. Un petit sélecteur de langue (qui garde le code et mémorise le choix 1 an, `SameSite=Lax`, `Secure`) est ajouté sous le formulaire. Le fonctionnement de l'envoi (code, chiffrement, types, validation) est inchangé ; les autres pages restent en français.
- Android : le QR et le lien « Depuis le téléphone » ajoutent `&l=<langue de l'appli>` (en, fr, es, ar, pl ; « Système » résolu) : la page d'envoi s'ouvre dans la langue de l'appli.
- Android (TV) : pendant une chaîne, OK ouvre la liste des chaînes (comme avant) ; Bas, Droite ou Menu ouvrent l'OSD complet ; Haut zappe, Gauche revient à la chaîne précédente. Bandeau d'aide mis à jour.

- Android (mode simple) : « Direct » relance la dernière chaîne avec une file de zapping, même si elle n'est pas dans les favoris (sa catégorie, dans l'ordre du Direct). Avant, haut / bas ne changeaient plus de chaîne.

- Android (TV) : dans la liste des chaînes du lecteur, la catégorie qui reçoit le focus charge et affiche ses chaînes tout de suite (anti-rebond d'environ 250 ms), sans appuyer sur OK ; le compteur montre le vrai nombre de chaînes au lieu de 0 pendant le chargement. Droite (ou OK) entre dans la liste, Gauche revient à la catégorie. « Toutes les chaînes » (avec le total, dans l'ordre du Direct) est la première entrée, puis Favoris, puis les catégories.
- Android (TV) : pendant le direct, avec l'OSD fermé et sans pause du direct, Gauche revient à la chaîne précédente (comme la touche « dernière chaîne », code parental respecté) au lieu d'ouvrir l'OSD, comme Droite ; sans chaîne précédente, un message l'indique. En pause du direct, Gauche/Droite gardent les sauts de −30/+30 s.
- Android : certaines chaînes (playlists M3U, ex. TVP1, ESKA) chargeaient sans fin alors que d'autres marchaient : sans première image et sans erreur, le lecteur n'avait ni délai, ni repli, ni message. Un délai de démarrage (15 s, 25 s avec le tampon « Stable ») est maintenant armé à chaque lancement de moteur : sans image ni progrès de chargement, le lecteur passe à VLC, puis affiche « Le flux ne démarre pas » (5 langues) au lieu de charger indéfiniment. Un démarrage lent mais qui progresse n'est pas coupé ; flux protégés, Stalker (VLC exclu) et moteur imposé gardent leurs règles
- Bureau : un flux qui ne démarre jamais (MPEG-TS ou lecture native sans réponse du serveur) laissait le lecteur en chargement sans fin ; après 20 s sans lecture ni progrès, il passe au format suivant puis affiche l'erreur
- Android (TV) : sur un panneau d'erreur de lecture du direct (« ce format ne peut pas être lu »…), les boutons Réessayer, Chaîne suivante et Fermer sont de nouveau sélectionnables à la télécommande (le direct interceptait les touches : Bas zappait).
- Android (TV) : après un zap, Droite affiche les commandes ET y place le focus du premier coup (le focus était demandé avant que la barre n'existe : aucune option n'était sélectionnable jusqu'au masquage).

- Bureau : les contrôles du lecteur réapparaissaient sans arrêt pendant la lecture (Windows émet des `mousemove` sans déplacement). Ils ne s'affichent plus qu'au vrai mouvement de la souris (seuil de 3 px), à une touche ou à un clic, et se masquent après 3 s ; ils restent affichés tant que la barre est survolée ou focalisée ou qu'un panneau est ouvert
- Bureau : les catégories restaient invisibles pendant toute la synchro quand le catalogue local était vide (première synchro, ou reconstruction au démarrage après une perte) : leurs effectifs n'étaient écrits qu'à la toute fin, parfois ~15 minutes plus tard, et la reconstruction ne branchait pas le direct avant la fin. Le direct et ses catégories apparaissent désormais dès la fin de sa phase, les catégories Films et Séries à la fin de la leur, et une source déjà synchronisée garde son ancien catalogue visible jusqu'à une seule bascule finale. Une pastille « Mise à jour du catalogue… x % » avec une barre fine indique l'avancement sur tous les écrans, sans rien masquer
- Android : une playlist M3U sans `url-tvg` (ou un portail Stalker sans guide de cette source) n'affichait « aucune information de programme » même avec un guide complémentaire dans Réglages › Synchronisation (adresse personnalisée comprise) : la synchro du guide était sautée faute de guide propre, donc le guide complémentaire n'était jamais lu. Il est désormais téléchargé et appliqué à toutes les chaînes de ces sources (premier ajout, synchro planifiée, retour sur l'application, playlist locale). Une playlist M3U relit aussi son guide à la synchro quand il est périmé. Le rapprochement par nom ignore les préfixes et suffixes de pays (« PL: », « PL | », « … PL »), la qualité (HD, FHD, UHD, 4K) et la casse, rapproche « TVP 1 » et « TVP1 » et utilise le radical de l'identifiant de la playlist (« TVP1.pl ») ; « TVN » ne correspond pas à « TVN 7 »
- Bureau : le guide complémentaire ne rapprochait les chaînes que par identifiant EPG ; une playlist M3U sans `tvg-id` (ou à identifiant différent de celui du guide) restait sans programme. Repli par nom, avec la même normalisation qu'Android ; les chaînes sans identifiant prennent celui de la chaîne du guide. Lecture du guide toujours en flux, seuls les programmes des chaînes rapprochées sont écrits
- Bureau : le guide TV s'ouvre directement, sans la page « Charger le guide » : la grille s'affiche tout de suite avec les chaînes du groupe, le guide se charge en arrière-plan s'il est absent ou périmé (plus de 6 h), avec un bandeau discret (Mo reçus) et les programmes qui apparaissent au fil des écritures, sans redessiner toute la grille. En cas d'échec, un message et « Réessayer » s'affichent et la grille reste visible
- Bureau : chaînes du guide triées par numéro croissant dans tous les groupes (Tout, catégories, Favoris, qui suivaient l'ordre d'ajout) ; les chaînes sans numéro vont à la fin, par nom
- Bureau : chargement du guide plus robuste sur les gros fichiers lents : premiers lots d'écriture petits puis jusqu'à 4 000 programmes avec contre-pression, progression aussi pour les guides .gz, fenêtre utile de −3 h à +3 jours (le guide d'après-demain était vide), durée totale bornée à 30 min, jusqu'à 2 nouvelles tentatives sur erreur réseau ou serveur (5xx, 408, 429) et repli sur le guide court Xtream si xmltv.php échoue
- Bureau : une synchro dont le serveur ne répond pas (liste des films, des séries ou du direct en erreur, playlist M3U vide ou page d'erreur) « réussissait » avec une partie vide et remplaçait le bon catalogue : toutes les catégories disparaissaient. Elle échoue désormais franchement et l'ancien catalogue reste en place
- Bureau : catégories (Séries) qui disparaissaient après la mise à jour d'un portail Stalker (le préfixe « s: » des catégories du module séries faisait perdre les choix d'activation, puis le filtre de langues les masquait toutes) ; les choix sont retrouvés par nom, et un catalogue local vide pour une source déjà synchronisée est reconstruit automatiquement au démarrage (message « Catalogue reconstruit après la mise à jour »)

- Bureau : fenêtre coupée en bas sur les petits écrans (portable 1366×768 avec zoom Windows 125 % : zone utile d'environ 614 px, fenêtre fixée à 1280×800 avec hauteur minimale de 640). La taille initiale est désormais bornée à la zone de travail de l'écran où s'ouvre la fenêtre (1280×800 au plus, centrée, avec une marge), la hauteur minimale passe à 540 px (largeur minimale 900 px) et ne dépasse jamais la zone de travail, même à 150 % de zoom. Le plein écran reste un choix de l'utilisateur.
- Bureau : les écrans défilent de nouveau quand leur contenu dépasse la hauteur de la fenêtre. La grille de la coquille (et celles de Réglages et des listes en deux colonnes) n'avait pas de hauteur de ligne bornée : le contenu agrandissait la page au lieu de défiler, si bien que le bas des Réglages, du Détail et des autres écrans restait hors de la fenêtre. Les boîtes de dialogue sont limitées à la hauteur de la fenêtre avec défilement interne, et leurs boutons d'action restent toujours visibles en bas.

### Documentation
- Documentation : guide de traduction (TRANSLATING.md) et modèle d'issue pour les traductions

## [Bureau 1.2.43] — 2026-11-11

### Corrigé
- Bureau : portail Stalker, séries des portails Ministra récents servies par le module `type=series` (catégories préfixées `s:`, listes paginées, saisons puis épisodes, épisodes sans saison listés directement). Lien d'épisode de type `series` : `create_link` essaie `vod`, puis `series` si la VOD échoue ou ne donne rien (les deux en échec : la première erreur). Sans catégorie du module, les séries restent tirées de la VOD comme avant.
- Bureau : portail Stalker, gros catalogues entièrement lus. Plafond de 3 000 pages porté à 20 000 (14 titres par page : 50 000 titres = 3 572 pages), jusqu'à 3 tentatives par page (délai dépassé, coupure, 429, 5xx, délai croissant), lecture page par page jusqu'à une page vide sans `total_items`, taille de la page lue si `max_page_items` est absent ou 0.
- Bureau : portail Stalker, jeton expiré en pleine synchro (« Authorization failed. », 401/403, réponse qui n'est pas du JSON du portail) : une seule nouvelle poignée de main, partagée par les requêtes en parallèle, puis nouvel essai sans perte de pages.
- Bureau : portail Stalker, synchro des films et séries tolérante. Une catégorie en échec est journalisée et sautée, son contenu précédent est recopié dans le nouveau catalogue (rien n'est supprimé) ; une liste de catégories illisible ne saute que la partie concernée (le direct reste). Fin de synchro : « Synchronisation terminée · X catégories ignorées » (Réglages › Synchronisation et notification), au lieu d'un échec global.
- Bureau : portail Stalker, journaux de synchro nettoyés (étape, type `vod`/`series`, code HTTP, classe d'erreur ; jamais d'adresse, de MAC ni de nom de catégorie), au plus 20 lignes détaillées par synchro, plus un bilan par partie.

## [1.2.90] — 2026-11-11 (Android) · Bureau 1.2.42 · Worker

### Nouveau
- EPG personnalisé (Android) : Réglages › Synchronisation › Guide complémentaire › « Adresse personnalisée… ». Adresse `http://` ou `https://` finissant par `.xml` ou `.xml.gz` (query string tolérée), saisie à la télécommande ou envoyée depuis le téléphone (page `/envoyer`, nouveau type « Guide XMLTV »).
- L'adresse est vérifiée AVANT l'enregistrement : requête réseau lisant les premiers Ko (gzip détecté par ses octets magiques) qui doivent contenir une balise `<tv` ; messages clairs (adresse invalide, injoignable, code HTTP, pas un guide XMLTV) en français, anglais, espagnol et arabe. Le guide complémentaire accepte désormais `http://` (avant : `https://` seulement).
- Guide par source (Android) : Réglages › Sources › menu de la source › « Guide de cette source », avec le mode « remplacer » (seule cette adresse est lue) ou « compléter » (guide du fournisseur, puis cette adresse pour les chaînes sans programme). Pour une playlist M3U sans `url-tvg` et pour un portail Stalker, qui a maintenant un guide dès qu'une adresse est définie (chaînes rattachées par `epgChannelId`, sinon par nom). Stocké par identifiant de source, sans migration Room.
- Bureau : « Guide complémentaire (adresse) » dans Réglages › Synchronisation, et, dans les options avancées d'une source, le rôle de l'adresse du guide (remplacer ou compléter) avec un bouton « Vérifier l'adresse » ; même vérification avant l'enregistrement.
- Worker : la page `/envoyer` accepte le type « Guide XMLTV » (adresse validée, remise à la TV une seule fois comme une source).

### Corrigé
- Portail Stalker (Android) : séries des portails Ministra récents, servies par le module `type=series` (catégories, listes paginées, saisons puis épisodes ; lien demandé en `vod/create_link`, puis `series/create_link` si le portail refuse). Avant : seule la VOD (`type=vod`) était lue, et les portails qui n'y mettent rien n'avaient aucune série.
- Portail Stalker (Android) : synchro des films et séries tolérante. Une catégorie en échec (erreur 5xx, délai dépassé, réponse vide) est journalisée et sautée, son contenu précédent reste en place, les autres continuent ; une liste de catégories illisible ne saute que la partie concernée (le direct reste). Fin de synchro : « Synchronisation terminée · X catégories ignorées » au lieu d'un échec global.
- Portail Stalker (Android) : gros catalogues entièrement lus. Le plafond de 3 000 pages coupait les catégories de plus de 42 000 titres (14 par page), il passe à 20 000 pages. Chaque page a jusqu'à 3 tentatives (délai, coupure, 429, 5xx) et un portail sans `total_items` est lu page par page jusqu'à une page vide. Le jeton expiré en pleine synchro est renouvelé (nouvelle poignée de main) sans rien perdre.
- Portail Stalker (Android) : journaux de synchro nettoyés (étape, type `vod`/`series`, code HTTP, classe d'erreur ; jamais d'adresse, de MAC ni de nom de catégorie), au plus 20 lignes détaillées par synchro, plus un bilan par partie.

## [1.2.89] — 2026-11-11 (Android) · Bureau 1.2.41 · Worker

### Nouveau
- Lecteur : le code parental est demandé avant de passer à une chaîne verrouillée (ou d'une catégorie verrouillée), par le zapping, la chaîne précédente, la saisie du numéro, la liste des chaînes, la télécommande du téléphone et le glissement tactile ; refus ou annulation = on reste sur la chaîne actuelle, code accepté = valable pour la session de lecture.
- Lecteur (télécommande) : touches média Lecture/Pause, Avance/Retour rapide (±10 s, ±30 s en différé) et Suivant/Précédent (épisode ou chaîne).
- Lecteur : touche Info = fiche du programme en surimpression (en cours + « Ensuite » en Direct ; année, durée, résumé en VOD).
- Direct : « Ensuite HH:MM · Titre » sous le programme en cours (bandeau TV, surcouche tactile paysage et portrait).
- Films et séries (TV) : bouton « Audio » (ouvre directement les pistes) et libellés sous les boutons Sous-titres, Veille et Réglages.
- Guide TV : sélecteur de groupe de chaînes dans l'en-tête (Favoris, Tout et catégories du Direct), mémorisé par profil ; la grille ne charge que les chaînes du groupe.
- APK plus léger par défaut : `/download/android`, le README et le site proposent l'APK arm64 (≈ 67 Mo), l'APK 32 bits (armv7) et l'universel en dernier recours ; la release publie `UltraTV-arm64-v8a.apk`, `UltraTV-armeabi-v7a.apk` et `UltraTV-x86_64.apk` (liens stables) avec leur `.sha256`.

### Corrigé
- Invite du code parental : elle pouvait déverrouiller avant d'avoir lu le code enregistré (valeur initiale « aucun code »).
- Android : lecture des flux protégés Widevine et ClearKey (DASH et HLS) d'une playlist M3U : `#KODIPROP` (`license_type`, `license_key`, `manifest_type`) et `#EXTVLCOPT` (`http-user-agent`, `http-referrer`).
- Android : propriétés de lecture stockées avec la chaîne (colonne `channel.props`, migration Room 20 → 21), jamais journalisées ni envoyées au cloud.
- Android : lecteur Media3 avec configuration DRM (licence Widevine et en-têtes, clés ClearKey locales converties en réponse de licence JSON).
- Android : message clair (français, anglais, espagnol, arabe) quand l'appareil ne prend pas en charge le système ou le niveau de sécurité, ou quand la licence est refusée.
- Android : un flux protégé ne bascule jamais vers VLC (repli automatique, choix manuel et lecteur externe exclus) ; l'erreur est affichée.
- Bureau : un flux protégé n'est pas lu ; message « Flux protégé non pris en charge sur ordinateur ».
- Android : l'assistant de premier lancement propose enfin « Portail Stalker (MAC) » (TV et smartphone, même saisie et même vérification du portail que Réglages › Sources) et « Depuis le téléphone » (TV / box).
- Guide TV (Android) : un guide lent ou qui « cale » (connexion ouverte sans données) ne charge plus indéfiniment : délai maximal sans donnée (30 s), débit minimal, durée totale bornée, et jusqu'à 3 tentatives avec attente croissante (réseau, 5xx, 408, 429 ; jamais sur 401/403/404 ni XML invalide).
- Guide TV (Android) : le guide est écrit par lots commités au fil de l'eau (avant : une seule transaction à la fin, guide vide pendant tout le téléchargement) ; l'ancien guide d'une chaîne n'est remplacé qu'au moment où ses nouveaux programmes arrivent.
- Guide TV (Android) : le gzip est détecté et décompressé en flux aussi pour le guide de la source Xtream et son pré-téléchargement (avant : seul le guide complémentaire).
- Guide TV (Android) : progression visible (octets lus / taille, programmes écrits) dans l'écran de première synchro et dans le guide ; un échec affiche une raison courte au lieu d'un chargement sans fin.
- Guide TV (Android) : nouvel état « Guide indisponible » (TV et tactile, FR/EN/ES/AR) avec la raison et un bouton « Réessayer » qui ne relance que le guide.
- Guide TV (Android) : les chaînes des catégories désactivées ne reçoivent plus de programmes (requête de rattachement filtrée).
- Playlists M3U (Android) : `url-tvg` / `x-tvg-url` de l'en-tête `#EXTM3U` sont pris en compte (plusieurs adresses séparées par des virgules ; un guide en échec n'empêche pas les suivants), y compris pour une playlist locale.
- Journaux (Android) : l'échec du guide n'écrit que le type d'erreur, le code HTTP et le nombre de tentatives (jamais d'adresse ni d'identifiants) ; RemoteLog nettoie en plus via LogSanitizer.
- Direct : les sous-titres codés (CEA-608) des flux MPEG-TS sont déclarés au lecteur ExoPlayer, et une piste CC n'est proposée dans le panneau Sous-titres que si la chaîne en diffuse réellement (plus de « CC1 » vide sur TF1) ; en Direct, le panneau n'affiche plus la recherche en ligne (réservée aux films) mais une indication quand la chaîne n'en diffuse pas.
- Guide TV et Direct : réglage « Logos des chaînes » (Afficher / Masquer, par profil ; numéro et nom à la place du logo, y compris dans l'aperçu) et colonne des noms du guide plus large par défaut et réglable (Étroite / Normale / Large), noms sur deux lignes ; sur TV, tactile et bureau.
- Guide TV : le titre « Guide » n'est plus coupé quand la pastille du groupe est longue (la pastille s'abrège avec une ellipse).
- Source Stalker : les chaînes en direct se lisent comme les films. Le lecteur (ExoPlayer, VLC, aperçu du Direct, aperçu du guide) envoie maintenant le User-Agent MAG, le cookie `mac` et le jeton du portail ; le lien de lecture (à usage unique) est redemandé à chaque ouverture (nouvelle tentative, reconnexion, changement de moteur, aperçu vers plein écran) ; une réponse `create_link` avec `stream=` vide est complétée avec l'identifiant de la chaîne ; un refus du portail (403 / 458) affiche « Le portail refuse la lecture : la MAC est peut-être utilisée sur un autre appareil » (FR/EN/ES/AR) et le code HTTP et le type d'erreur sont journalisés sans MAC ni hôte. Pendant la lecture d'une chaîne Stalker, un signal de présence de boîtier MAG (`watchdog/get_events`, toutes les 2 min) évite que le portail coupe le flux (« Reconnecting… » puis « Network problem ») ; chaque reconnexion journalise code HTTP et durée de lecture ; ExoPlayer est utilisé d'office pour Stalker (LibVLC n'envoie pas le cookie) ; `itv/set_played` est envoyé à l'ouverture d'une chaîne ; le relais de différé, les téléchargements et les enregistrements d'une source Stalker envoient aussi les en-têtes MAG et demandent un lien neuf à chaque ouverture. Si le portail ne donne pas de lien de lecture (réponse vide, sans `cmd`…), le lecteur affiche « Le portail n'a pas fourni le lien de la chaîne » (FR/EN/ES/AR) au lieu de « problème réseau » : une adresse `stalker://` n'est plus jamais passée au moteur.

## [1.2.88] — 2026-11-11 (Android)

### Corrigé
- Lecture : le décodeur FFmpeg LGPL de la 1.2.87 contenait aussi un décodeur vidéo, chargé automatiquement par le lecteur ;
  sans codec vidéo, il pouvait bloquer la lecture (ni image ni son). FFmpeg ne sert plus qu'à l'audio (AC-3, E-AC-3,
  DTS…), la vidéo reste aux décodeurs de l'appareil.

## [1.2.87] — 2026-11-11 (Android) · Bureau 1.2.40 · Worker

### Nouveau
- Android : décodeur audio FFmpeg sous licence LGPL (AC-3, E-AC-3, DTS, TrueHD…) à la place du décodeur GPL-3.0

### Corrigé (confidentialité)
- Android : les statistiques d'alertes ne sont plus envoyées (donc plus d'identifiant d'installation) quand la télémétrie est coupée ; la lecture des alertes continue.
- Android : l'ajout d'un portail Stalker n'envoie plus le nom d'hôte du portail dans l'événement de diagnostic.
- Worker : les journaux et rapports de plantage ne stockent plus l'identifiant du compte (qui peut être une adresse e-mail) mais une empreinte HMAC courte et non réversible ; les pages `/logs` et `/crashes` affichent cette empreinte. Les relevés déjà stockés gardent l'ancien champ jusqu'à leur expiration (7 jours pour les journaux, 30 jours pour les plantages).
- Android : le réglage « Rapports de plantage anonymes » devient **« Télémétrie et rapports de plantage »** (FR/EN/ES/AR), avec un sous-texte qui indique ce qu'il coupe : journaux de diagnostic, rapports de plantage, comptage anonyme et statistiques d'alertes.
- Bureau : nouveau réglage « Vérifier les mises à jour automatiquement » (Réglages › À propos, activé par défaut, mémorisé) qui coupe les vérifications automatiques ; « Rechercher une mise à jour » reste utilisable.
- Android : le message de clé de signature n'indique plus le lien court tinyurl mais l'adresse directe `github.com/vantaflow/ultra-tv/releases/latest/download/UltraTV.apk`.
- README et site : documentation de confidentialité mise à jour en conséquence.

## [1.2.86] — 2026-11-11 (Android)

### Nouveau
- Release : l'APK universel est aussi publié sous le nom **`UltraTV.apk`** (avec son `.sha256`) ; tous les liens publics
  y mènent. `UltraTV-debug.apk` reste publié pour la mise à jour intégrée.
- README et site : nouvelle présentation, section **Confidentialité** (ce que l'appli envoie, quand, où, ce qui est
  conservé, comment couper la télémétrie).

### Technique
- Script du décodeur FFmpeg LGPL : décodeurs Opus et Vorbis ajoutés (le décodeur LGPL n'est pas encore embarqué).

## [1.2.85] — 2026-11-11 (Android)

### Corrigé
- Fiche série : « Aller à l'épisode » et « Télécharger la saison » sont atteignables à la télécommande (Haut depuis le
  premier épisode y mène ; avant, le focus les sautait).
- Fiche série : Haut et Bas vont à l'élément juste au-dessus ou au-dessous, sans sauter vers « Lecture » ni vers
  l'épisode à reprendre.
- Fiche série : aide « Appui long sur un épisode : le télécharger ».

## [1.2.84] — 2026-11-11 (Android) · Worker

### Nouveau
- **Nouvel accueil** : « À la une » en plein écran (image de fond, notes IMDb et Rotten Tomatoes, synopsis, Regarder /
  Plus d'infos), cinq raccourcis avec compteurs (Direct, Films, Séries, Favoris, Guide TV), rangée Direct avec le
  programme en cours, « Top 10 aujourd'hui ».
- **Nouvelle fiche film / série** : image de fond plein écran, ligne d'infos, Reprendre S1É3, distribution cliquable ;
  séries avec saisons à gauche et grandes cartes d'épisodes, « Aller à l'épisode » ; films avec distribution et titres
  similaires.
- **Films et Séries par catégories** : liste des catégories du fournisseur avec leur nombre de titres et grille
  d'affiches ; au choix, **genres automatiques** (Action, Comédie, Science-fiction, Anime…) détectés en arrière-plan ;
  tri, note, période, colonnes.
- **Direct et Guide TV** : programme en cours avec barre de progression et programme suivant sur chaque chaîne,
  panneau « En direct » et cartes « Ensuite » ; guide avec le programme sélectionné et l'aperçu vidéo en haut,
  bouton « Maintenant », ligne de l'heure courante.
- **Lecteur** : badges qualité / codec / audio, temps restant, « Se termine à », épisode précédent / suivant.
- **Page acteur / réalisateur** : photo, naissance, biographie, titres présents dans la playlist.
- **Télécommande et recherche depuis le téléphone** (Réglages › Sources) : QR code vers une page servie par la box sur
  le réseau local ; recherche dans Films, Séries et Direct, lecture, pause, ±10 s / ±30 s, flèches, OK, Retour.
  Désactivée par défaut, protégée par une clé.
- **Mode simple** (Réglages › Affichage ou par profil) : six grandes tuiles (Télé en direct, Reprendre, Films, Séries,
  Favoris, Rechercher) pour les personnes peu à l'aise.
- **Mémoire et stockage** (Réglages) : espace utilisé détaillé, mémoire vive, vider le cache d'images, compacter la base,
  effacer l'historique ou les reprises ; taille du cache, nettoyage automatique, mode économie de mémoire.

### Amélioré
- **Catégories masquées jamais chargées** : une catégorie désactivée (par sa langue ou à la main) n'est plus écrite en
  base ni indexée ; Direct, Films et Séries sont traités chacun avec leurs propres catégories. Synchro plus rapide et
  moins de mémoire.
- **« Parce que vous avez regardé »** : recommandations TMDB du film regardé, gardées si elles sont dans la playlist
  (après Captain Marvel : The Marvels, Avengers, Les Gardiens de la Galaxie…), complétées par un classement local
  (genres, réalisateur, acteurs, époque) ; plus de films hors sujet.
- « À la une » ne montre que des titres avec une image de fond (de la source ou de TMDB), plus d'affiche seule.
- Films et Séries : Gauche depuis la grille revient sur la catégorie choisie (elle sautait sur la voisine).
- Affiches demandées à la taille du cadre et décodées en arrière-plan sur les box faibles.

### Worker
- Proxy TMDB : fiche personne (`person/{id}`) pour la page acteur, recommandations et titres similaires
  (`movie|tv/{id}/recommendations|similar`).

## [1.2.83] — 2026-11-11 (Android) · Bureau 1.2.39 · Worker

### Nouveau
- **« Depuis le téléphone »** (Réglages › Sources › Ajouter une source, et accueil sans source) : la TV affiche un QR
  code et un code ; la playlist (Xtream, M3U ou Stalker) se saisit sur le téléphone ou l'ordinateur, sur la page
  `/envoyer`, **sans compte**, puis la TV l'ajoute et la synchronise. L'envoi est chiffré, gardé 15 min au plus et remis
  une seule fois.
- **Synchro plus rapide** : à la première synchronisation, films et séries sont écrits en masse (index et recherche
  reconstruits une fois à la fin), l'analyse et l'écriture se font en parallèle, films, séries et guide se téléchargent
  pendant l'écriture du direct, et la synchro tourne en priorité basse pour ne pas faire saccader l'écran.
- Accueil : les rangées « Parce que vous avez regardé… », « Films les mieux notés » et « Séries les mieux notées »
  s'affichent juste sous « Reprendre ».
- Mises à jour : l'invite affiche un **résumé des nouveautés** de la version proposée.
- Tableau de bord › Administration : **installations sans compte** (total, actives 7 jours, nouvelles) et sources
  envoyées depuis le web, comptées par un relevé anonyme quotidien (identifiant aléatoire et version ; rien si la
  télémétrie est coupée).
- Lecteur : bouton **« Passer l'intro »** pendant le générique de début d'un film ou d'un épisode, **« Passer le résumé »** pendant le « précédemment dans… », et **« Épisode suivant »** pendant le générique de fin d'un épisode (« Passer le générique » quand une scène suit le générique d'un film). Télécommande : OK, commandes masquées ; tactile : un appui. Rien n'est sauté sans action de l'utilisateur.
- Bureau (Windows, macOS) : même bouton en plein écran, au clic ou avec la touche S (ou Entrée).
- Les passages viennent de TheIntroDB (base communautaire, sans clé) : seuls l'identifiant TMDB, la saison, l'épisode et la durée sont envoyés, jamais d'identifiant ni d'adresse IPTV. Titre inconnu, TMDB non relié ou réseau absent : aucun bouton.
- Fiches film et série : notes **IMDb** et **Rotten Tomatoes** (« IMDb 8.8 · RT 87% ») via OMDb ; sans clé OMDb sur le Worker, la note habituelle reste affichée.
- Accueil : rangées **« Parce que vous avez regardé… »** (films proches du dernier film regardé), **« Films les mieux notés »** et **« Séries les mieux notées »**, calculées en local (sans Trakt), réglables dans Rangées de l'accueil.
- **« À la une » défile** : 6 titres (séries et films les mieux notés), un toutes les 9 s, avec fondu et points ; le bureau alterne la chaîne mise en avant et les séries les mieux notées (pause au survol).
- Recherche **tolérante aux fautes de frappe** (« brekin bad » → Breaking Bad) quand la recherche exacte ne trouve aucun film ni série.
- Films et séries : **tri** (fournisseur, ajouts récents, mieux notés ; chaque tri suit un index de la base, sans retri de tout le catalogue), **note minimale** (6+, 7+, 8+) et **période** (décennies) ; un filtre actif sur « Tous » affiche une grille filtrée.
- **Pages acteurs et réalisateurs** : un nom de la distribution ouvre ses films et séries présents dans la playlist (filmographie TMDB rapprochée par titre et année, plus les fiches qui le citent).
- Réglages › Affichage › **Animations** : Automatique (selon l'appareil ; sur le bureau, selon le système), Désactivées, Réduites, Complètes.
- Bureau : **son AC-3 / E-AC-3 / DTS** — quand un film ou une chaîne joue sans son, l'audio est converti en AAC par ffmpeg (version minimale LGPL d'environ 3 Mo, embarquée sous Windows et Linux ; macOS : ffmpeg installé, sinon un message explique comment l'installer).

### Worker
- `/envoyer`, `POST /api/envoi/start|send|poll` (envoi sans compte, Durable Object, limites de débit) et
  `POST /api/anon/ping` (relevé anonyme).
- `GET /api/ratings` (notes OMDb, appareil appairé, cache 7 jours) : secret facultatif `OMDB_API_KEY`.
- Proxy TMDB : `search/person` et `person/{id}/combined_credits` autorisés (pages acteurs).

### Signature
- Nouvelle clé de signature des releases, avec chaîne de rotation depuis l'ancienne : les appareils existants
  acceptent la mise à jour sans désinstaller.

### Licences
- Le décodeur FFmpeg Android `org.jellyfin.media3:media3-ffmpeg-decoder` est publié sous GPL-3.0 : `android-native/tools/ffmpeg-lgpl/build.sh` (workflow « FFmpeg LGPL ») construit une version LGPL, utilisée automatiquement une fois déposée dans `android-native/app/libs/`. Voir `THIRD_PARTY_NOTICES.md`.

## [1.2.82] — 2026-11-11 (Android)

### Corrigé
- Bouton « Mettre à jour » d'une alerte : la fenêtre de mise à jour se rouvre à chaque appui (elle ne s'ouvrait qu'une
  fois), et une seule fenêtre s'affiche même si À propos en demande une en même temps.
- Alerte urgente avec bouton reçue pendant la lecture : le bandeau s'efface sans la clore, la fenêtre et son bouton
  s'affichent à la fin de la lecture (le bouton n'était proposé qu'au lancement suivant).
- Mise à jour avec profil de démarrage : une installation refusée en cours de route est abandonnée, ses fichiers
  temporaires ne restent plus sur l'appareil.

## [1.2.81] — 2026-11-05 (Android)

### Maintenance
- Dépôt : `gradlew` et les scripts `.sh` gardent des fins de ligne Unix même extraits sous Windows (`.gitattributes`) ; un build lancé sous Linux depuis une archive faite sous Windows échouait (« ./gradlew: No such file or directory »).

## [1.2.80] — 2026-11-05 (Android)

### Performances
- Mise à jour intégrée : le profil de démarrage (.dm) publié avec chaque APK est désormais téléchargé, vérifié (SHA256SUMS.txt) et installé avec l'APK, par une session PackageInstaller. Android compile l'application d'avance dès l'installation : démarrage d'environ 1,1 s à 0,6 s sur box, au lieu de perdre ce gain à chaque mise à jour depuis l'application. Si la box refuse ce mode d'installation, l'installeur classique prend le relais (APK seul) et l'appareil ne retente plus la session.

## [1.2.79] — 2026-11-05 (Android) · Worker

### Nouveau
- Alertes, ciblage par version : une alerte peut ne viser que les versions de l'application antérieures à une version donnée (« Versions antérieures à 1.2.79 »).
- Alertes, bouton d'action : « Mettre à jour » ouvre la mise à jour intégrée de l'application (ou indique qu'elle est déjà à jour).
- Alertes, statistiques : pour chaque alerte, le tableau de bord indique sur combien d'appareils elle a été affichée, fermée et sur combien le bouton a servi (compté une fois par appareil, identifiant d'installation aléatoire stocké haché).
- Alertes, affichage selon le niveau : « Information » sans bouton s'affiche en bandeau discret qui disparaît seul, sans prendre le focus ; « Avertissement », « Urgent » et les alertes avec bouton restent des fenêtres à valider. Pendant la lecture plein écran, une alerte « Urgent » s'affiche en bandeau au lieu d'attendre la fin.
- Réglages › Messages : les alertes déjà reçues sur l'appareil (20 au plus), pour les relire.

### Changements
- Alertes : cible « Ordinateurs » retirée (aucune application ne la lisait).

## [1.2.78] — 2026-10-31 (Android) · Worker

### Nouveau
- Alertes : l'administrateur programme depuis le tableau de bord (Administration › Alertes) un message avec titre, niveau (Information, Avertissement, Urgent), cible (tous, TV, téléphones, ordinateurs), début et fin. Les applications Android le vérifient au lancement puis toutes les 15 minutes et l'affichent par-dessus l'écran, jamais pendant la lecture plein écran. « Information » et « Avertissement » : une fois par appareil ; « Urgent » : à chaque lancement jusqu'à la fin. Arrêt et suppression depuis le même panneau.

## [1.2.77] — 2026-10-30 (Android)

### Corrections
- Fiches film et série : synopsis dans la langue de l'application (TMDB) quand il existe, au lieu du résumé anglais fourni par la source ; celui de la source reste le repli.
- Crash « Key … was already used » (Chromecast HD, journaux en ligne) : les listes paginées (Direct, Guide, Films, Séries) ne plantent plus quand la synchro présente un instant le même élément deux fois ; listes des Favoris dédoublonnées.
- Langue : « Système (auto) » traduit (au lieu de « System (auto) »).

## [1.2.76] — 2026-10-30 (Android)

### Corrections
- Réglages : → depuis une rubrique entre sur la première ligne du volet (et le volet revient en haut), et non sur la ligne située en face de la rubrique (« Numéros de chaînes » depuis « Affichage », titre du volet hors de l'écran).

## [1.2.75] — 2026-10-30 (Android)

### Corrections (passe de bout en bout sur une box Android TV)
- Fiches film : photos de la distribution et du réalisateur (TMDB) au lieu des seules initiales.
- Direct : en revenant du plein écran, le focus est sur la chaîne regardée (il restait sur la première : OK relançait une autre chaîne).
- Fenêtres de choix : le focus part de l'option cochée (« ↓ puis OK » depuis Cloudflare choisissait Google) ; à la fermeture de n'importe quelle fenêtre ouverte depuis un bouton ou une ligne (menu d'une chaîne, Noter…), le focus revient sur ce bouton.
- Guide : ↓ depuis les jours entre dans la grille au programme en cours (et non sur la case la plus à droite) ; au bord droit, → fait défiler le temps au lieu de sauter sur l'onglet « Samedi » ; jour à venir sans programme : message explicite au lieu de « se charge encore ».
- Trakt : le texte de « Marquer vu automatiquement à la fin » décrit l'état réel (il disait « Désactivé » une fois activé).
- Enregistrements et Téléchargements : « 0 Mo sur 5,2 Go » au lieu de « — sur 5,2 Go ».
- Abonnement : polices de l'application.

## [1.2.74] — 2026-10-29 (Android)

### Corrections
- Réglages : en fermant une fenêtre de choix (ex. « Aperçu vidéo dans le Direct »), le focus revient sur la ligne qui l'a ouverte. Avant, le volet défilait et le focus tombait sur une autre ligne (« Épisode suivant automatique »).

## [1.2.73] — 2026-10-29 (Android)

### Corrections
- Réglages : la flèche gauche depuis le volet ramène le focus sur la rubrique ouverte. Avant, elle allait sur la rubrique en face de la ligne focalisée (« À propos » depuis le bas d'« Affichage ») et le volet changeait de rubrique.
- Recherche : les « Récents » ne gardent plus les étapes intermédiaires de la frappe (« spide », « spt »…), seulement la saisie finale.

### Direct
- Aperçu vidéo de la chaîne sélectionnée : avec le son par défaut (Réglages › Lecture › Son de l'aperçu pour le couper ; un choix déjà fait est conservé) et lancé plus tôt, 0,4 s au lieu de 0,7 s. Nouveau réglage « Délai avant l'aperçu » (0,3 à 3 s).

### Documentation
- README : captures TV françaises prises sur une vraie box Android TV (Accueil, Direct, Guide, fiches film et série, Recherche, rangées Ultra TV dans le lanceur Kairo) ; installation adb avec le profil `.dm`.

## [1.2.72] — 2026-10-29 (Android)

### Corrections
- Recherche : jusqu'à ~500 fois plus rapide. Les requêtes plein texte parcouraient toutes les lignes de la source et évaluaient la recherche pour chacune (55 s pour « spider » sur une box 2 Go) ; l'index est maintenant lu une seule fois.
- « Reprendre la lecture » et « À suivre » (Google TV, Kairo…) : un film présent dans plusieurs catégories de la source (4K, Bluray…) n'apparaît plus qu'une fois.
- Journal du lecteur : la durée « première image » n'est plus faussée quand le lecteur passe de l'aperçu au plein écran.

### Fluidité
- Releases : un profil de démarrage précompilé (`.dm`, Android 12+) accompagne chaque APK par processeur. Installé avec l'APK (`adb install-multiple UltraTV-X-abi.apk UltraTV-X-abi.dm`), il est appliqué dès l'installation. Mesuré sur une box 2 Go : démarrage 1,1 s → 0,6 s, images en retard au défilement 84 % → 61 %, pire cas 550 → 150 ms.
- Affiches : plus d'initiales composées pendant le chargement (seulement sans affiche ou en cas d'échec), un texte de moins par carte au défilement.

### Finitions
- Textes français harmonisés au vouvoiement.
- Chaîne d'accueil « Nouveautés » (au lieu de « Ultra TV · Nouveautés ») : Google TV et les lanceurs affichent déjà le nom de l'appli.

## [1.2.71] — 2026-10-29 (Android)

### Corrections
- Séries : titres d'épisodes propres quand la source abrège le nom de la série (« Daily Dose -  - The Darkest Hour… » devient « The Darkest Hour… »). Le titre affiché dans le lecteur et dans « Reprendre la lecture » est aussi nettoyé.
- Google TV, Android TV et lanceurs tiers (Kairo…) : titres propres dans « À suivre » (« Captain Marvel » au lieu de « TOP - Captain Marvel (2019) ») et plus de doublons dans la chaîne « Nouveautés » quand un film figure dans plusieurs catégories.
- Fiche film : un réalisateur listé plusieurs fois par la source n'apparaît plus en une seule bulle « Ridley Scott, R… ».
- Fiche série : le bouton Trakt devient un bouton rond (étoile), il n'est plus tronqué en « Tra… » à côté de « Reprendre ».
- Guide TV : l'horaire n'est plus coupé sous un titre trop long.
- Menu latéral : « Téléchargements » n'est plus coupé.
- Recherche : « Recherche… » s'affiche pendant la recherche (au lieu d'un panneau vide) ; les touches d'un clavier physique ou d'une télécommande à clavier sont prises en compte.

## [1.2.70] — 2026-10-29 (Android) · Bureau 1.2.37

### Corrections
- Lecteur (film, épisode) : la touche Retour quitte enfin le lecteur sur Android 11 et antérieurs (issue #30, Xiaomi MiTV). Commandes masquées, Retour servait à les réafficher et n'était jamais transmis : la touche suivante les masquait, et ainsi de suite, sans pouvoir sortir. Retour referme maintenant ce qui est ouvert, puis quitte le lecteur.
- Tableau de bord : le menu de gauche suit de nouveau la section affichée (un ancien mécanisme resté en place contredisait le nouveau).

### Maintenance
- Mises à jour de dépendances (Android, bureau, Worker).

## [1.2.69] — 2026-10-21 (Android)

### Corrigé
- Sélection qui saute encore (télémétrie 1.2.68 : aucun appui en double, mais des **répétitions automatiques** pour des
  appuis brefs et des touches traitées jusqu'à 0,5 s en retard) : une flèche ne se répète plus qu'après 0,7 s d'appui
  réel, et une répétition restée en attente pendant un ralentissement est ignorée. L'appui long fait toujours défiler.
- Clavier qui masque le champ en cours de saisie sur les box qui n'annoncent pas la hauteur de leur clavier : OK sur un
  champ passe la fenêtre en mode compact, en haut de l'écran, et fait défiler jusqu'au champ.

## [1.2.68] — 2026-10-21 (Android)

### Corrigé
- Fenêtres d'ajout de source trop petites sans clavier (il fallait défiler pour voir les autres champs) : la fenêtre
  ne se réduit plus que lorsque le clavier est réellement affiché.
- Sélection qui saute de deux éléments ou plus pour un seul appui : les appuis envoyés en double par certaines
  télécommandes Bluetooth (deux « bas » sans relâchement entre les deux) sont ignorés ; l'appui long continue de défiler.

### Technique
- Télémétrie « keys » : appuis en double ignorés et retard de traitement des touches (box surchargée).

## [Bureau 1.2.36] — 2026-10-21

### Ajouté
- **DNS personnalisé sur le bureau** (Réglages › Lecture › Réseau) : mêmes choix qu'Android (Système, Google,
  Cloudflare, Quad9, DNS sur HTTPS ou adresses IP au choix). Appliqué au catalogue, au guide, aux images et aux flux ;
  si le serveur ne répond pas en 2 s, le DNS du système prend le relais.

### Technique
- React 19, Vite 8, TypeScript 7 ; mises à jour Dependabot regroupées et fusionnées automatiquement quand les tests passent.

## [1.2.67] — 2026-10-21 (Android)

### Corrigé
- Fenêtres d'ajout de source (Xtream, M3U, Stalker…) écrasées par le clavier à l'écran sur TV (champs et boutons qui se
  chevauchaient) : tant que le clavier est affiché, la fenêtre ne garde que le titre et les champs ; « OK » du clavier
  ou ▼ sur le dernier champ ferment le clavier et mènent au bouton « Ajouter ».

### Technique
- Mise à jour complète des technologies (plus d'un an de retard rattrapé) : Kotlin 2.4, Gradle 9.8, AGP 9.4,
  Compose 2026.09 (UI 1.12) et tv-material 1.1, **Media3 1.11** (lecteur ExoPlayer : flux TS / HLS, décodeurs, HDR,
  reprise réseau), **libVLC 3.7.7**, **Coil 3** (images), OkHttp 5, Room 2.8, Paging 3.5, Hilt 2.60, WorkManager 2.12,
  Navigation 2.10. Comportement inchangé (targetSdk 35 conservé). Mises à jour suivies chaque semaine par Dependabot.

## [1.2.66] — 2026-10-21 (Android)

### Corrigé
- **Sélection qui saute** à la télécommande (Réglages, À propos, autres écrans) et fenêtre **« Portail Stalker » qui se
  refermait** dès son ouverture (clavier qui sortait puis se cachait). Quand l'élément sélectionné disparaissait
  (fenêtre fermée, liste remplacée par un formulaire, clavier), Android rendait la sélection au menu latéral, qui la
  renvoyait au hasard vers la droite — jusqu'à changer de rubrique dans les Réglages. La sélection revient désormais à
  l'élément qui l'avait (ou à celui qui a ouvert la fenêtre), et une fenêtre ouverte garde son champ actif.
- Journal de diagnostic pour l'ajout et la synchronisation d'un portail Stalker (sans adresse MAC ni identifiants).

## [1.2.65] — 2026-10-21 (Android) · [Bureau 1.2.35]

### Ajouté
- **Portails Stalker / Ministra (adresse MAC)** : ajout depuis l'appli (Réglages › Sources), le bureau (application
  Electron) ou le tableau de bord ; direct, films, séries, guide court et date d'expiration (écran Abonnement). Lien de
  lecture demandé au portail au moment de la lecture. Replay non disponible pour ces portails.
- **Téléchargements** (Android) : films, épisodes, saison entière ; file d'attente (2 à la fois) avec pause, reprise,
  annulation et nouvel essai ; reprise après coupure ; écran « Téléchargements » utilisable hors ligne ; Wi-Fi
  uniquement et emplacement dans les réglages. Un film téléchargé se lit depuis le fichier local.
- **Aperçu vidéo dans le Direct** (Android TV) : la chaîne sélectionnée joue dans le panneau d'aperçu (muet par
  défaut) ; OK la passe en plein écran **sans recharger le flux**, Retour revient à la liste avec l'aperçu en cours.
  Réglable dans Réglages › Lecture.
- **Trakt complet** (Android et bureau) : ajouter / retirer de la watchlist, marquer vu / non vu (film, épisode,
  saison), noter de 1 à 10, rangées des listes personnelles, rangée « À suivre », positions de reprise importées.
- **DNS personnalisé** (Android) : Google, Cloudflare, Quad9, DNS-over-HTTPS ou serveur au choix, avec test ;
  appliqué au catalogue, au guide, aux images et aux flux ExoPlayer (pas à VLC). Repli automatique sur le DNS du
  système si le serveur ne répond pas.
- **Personnalisation** (Réglages › Affichage) : couleur d'accent (6 choix), taille du texte, rangées de l'accueil
  (afficher / masquer / ordre), écran d'ouverture (Accueil, Direct, Films, Séries, Guide, dernière chaîne).

### Amélioré (lisibilité)
- Barre du lecteur plus contrastée, libellés blancs plus grands, bouton actif (Veille, Replay) mis en évidence.
- Badges des chaînes (langue, qualité) lisibles (contraste AA), qualité légèrement colorée.
- En-tête du Direct simplifié : « Catégorie · N chaînes ».
- Listes de choix : l'option appliquée porte une coche ✓, distincte de la sélection à la télécommande.

## [1.2.64] — 2026-10-20 (Android TV)

### Corrigé
- Lecteur, direct : après un zap, la barre de commandes (Chaînes, Pistes, Sous-titres, Enregistrer, Veille, Lecteur)
  s'affichait alors que la télécommande zappait ou ouvrait la liste — boutons sans effet en apparence. Le bandeau ne
  montre plus que la chaîne et le programme, avec l'indication « ◀ ▶ Commandes · OK Chaînes · ▲ ▼ Zapper » ;
  Gauche / Droite affichent les commandes, cette fois actives.
- Panneau Affichage : la minuterie n'y figure plus (elle a son bouton « Veille » dans la barre du lecteur).

## [1.2.63] — 2026-10-20 (Android)

### Corrigé
- Direct : l'en-tête de section collé en haut de la liste masquait la première chaîne (agrandie quand elle est
  sélectionnée). Il a maintenant sa propre place au-dessus de la liste ; un libellé de section vide n'est plus affiché.

## [1.2.62] — 2026-10-20 (Android)

### Amélioré (fluidité, mesurée en production : Direct 20 % d'images saccadées, Accueil 15 %, démarrage ~3,2 s)
- **Direct** : la liste ne trie plus les ~55 000 chaînes à chaque page. Rang de catégorie précalculé et indexé,
  pagination par clé (une recherche dans l'index par page), liste rafraîchie une seule fois à la fin d'une
  synchronisation, zapping sans chargement de toute la liste. Ordre identique à avant ; calcul des rangs en
  arrière-plan après la mise à jour (aucun blocage au premier lancement).
- **Direct** : libellé de catégorie, badges de variante et chaînes visibles calculés une fois par page au lieu de
  boucles à chaque image de défilement.
- **Accueil** : seules les rangées visibles sont construites ; textes préparés hors du fil principal ; images
  décodées à la taille des cartes ; chaque rangée retient sa dernière carte. En revenant à l'accueil, la sélection
  revient sur la carte quittée (et non plus sur « Regarder »).
- **Démarrage** : 250 ms d'attente supprimées, déchiffrement des sources, détection des décodeurs, réseau et services
  préparés hors du fil principal.
- **Lecteur** : zapping sans blocage lors de la libération d'ExoPlayer (jusqu'à ~2,5 s auparavant).

### Corrigé
- Google TV sur NVIDIA Shield : « Unknown URL content://android.media.tv » à chaque lancement — fournisseur TV et
  invite « ajouter à l'accueil » déclarés pour Android 11+ ; si le fournisseur est absent, un seul message par jour.

### Diagnostic
- Relevé de fluidité : nouveau compteur `slow` (images au-delà de 1,5 × la durée d'image).

## [1.2.61] — 2026-10-12 (Android)

### Corrections
- Son sans image (smartphone Samsung Galaxy A30s, NVIDIA Shield…) :
  - rendu vidéo de compatibilité (TextureView) activé d'office sur smartphone et tablette, en plus de NVIDIA ;
  - l'image est réellement vérifiée après le démarrage : si elle reste entièrement noire, le lecteur passe tout seul au décodage suivant (logiciel, puis VLC), comme pour une absence d'image ;
  - le conteneur vidéo, masqué en quittant le lecteur, est toujours réaffiché au lancement suivant.

## [1.2.60] — 2026-10-12 (Android)

### Corrections
- Smartphone, premier lancement : après l'appairage « Depuis le cloud », l'assistant passe aux langues puis à la préparation du catalogue au lieu de revenir au choix de la source (la synchro partait en arrière-plan, écran resté sur Xtream / M3U / Cloud).

## [1.2.59] — 2026-10-12 (Android TV)

### Corrections
- NVIDIA Shield : son sans image avec les lecteurs intégrés (ExoPlayer et VLC) — la vidéo est désormais rendue dans une TextureView sur les appareils NVIDIA (issue #7). Nouveau réglage « Rendu vidéo de compatibilité » (Réglages › Lecture) pour l'activer sur d'autres box ou le désactiver.

## [1.2.58] — 2026-10-09 (Android TV)

### Corrigé
- Mise à jour : « La mise à jour n'a pas pu être installée » / « package non valide ». La mise à jour automatique
  (30 s après le lancement) et le bouton « Mettre à jour » téléchargeaient en même temps dans le même fichier ; le
  second vidait le dossier pendant que le premier écrivait (APK tronqué, empreinte différente). Un seul téléchargement
  à la fois désormais, écrit dans un fichier temporaire, contrôlé en longueur, recommencé une fois s'il est corrompu ;
  un APK déjà vérifié est réutilisé.

## [1.2.57] — 2026-10-09 (Android TV) · [Bureau 1.2.34]

### Corrigé
- **Pistes audio** : un film à plusieurs langues démarrait toujours en anglais. La langue choisie est désormais, dans
  l'ordre : la dernière choisie pour ce titre, le réglage Langues, sinon la langue de l'interface (fr / fra / fre
  reconnus, ainsi que les pistes nommées « French », « VFF », « TRUEFRENCH »…). ExoPlayer et VLC (choix relancé quand
  les pistes apparaissent). Un choix manuel n'est jamais écrasé.
- **Listes de pistes** : relues en continu tant que le panneau est ouvert (elles restaient vides si le panneau était
  ouvert avant l'arrivée des pistes), libellés lisibles (« Français (AC3 5.1) », « Anglais »). Choisir une piste
  audio ne réactive plus les sous-titres.
- Bureau : même choix automatique de la langue audio (flux HLS) et libellés lisibles.

### Amélioré (fluidité, Android)
- Pendant une synchronisation, l'accueil, le direct et les grilles ne relancent plus leurs requêtes sur tout le
  catalogue à chaque lot inséré : une lecture pendant la synchro, puis une à la fin. Idem pour la chaîne Google TV
  Nouveautés.
- Plus de fondu à l'apparition des vignettes sur TV ; profil de démarrage enrichi.
- Relevé de fluidité par écran (images saccadées, pire blocage, temps de démarrage) dans le journal de diagnostic.

## [1.2.56] — 2026-10-09 (Android TV)

### Corrigé
- Google TV : les chaînes « Favoris » et « Nouveautés » n'étaient jamais créées (relevé par le journal de diagnostic
  de la 1.2.55). La vérification « la chaîne existe-t-elle ? » interrogeait le système avec l'identifiant -1 (erreur
  « Unknown URI » sur Xiaomi) ou lisait une fiche de chaîne incomplète (plantage sur Google TV). Vérification
  remplacée par une requête directe protégée ; un refus de création n'est plus enregistré comme une chaîne.

## [1.2.55] — 2026-10-08 (Android TV)

### Corrigé
- Lenteurs sur les box modestes : pendant une synchronisation, les rangées Trakt reparcouraient tout le catalogue
  toutes les 3 s, sur tous les cœurs. Elles ne sont plus recalculées qu'à la fin d'une synchronisation, sur un seul
  fil de basse priorité.
- Google TV : la chaîne « Ultra TV · Nouveautés » restait invisible — publiée comme chaîne ordinaire alors que seule la
  première chaîne d'une application (« par défaut ») s'affiche d'office. La première chaîne publiée est désormais la
  chaîne par défaut ; sinon l'application propose une fois de l'ajouter à l'écran d'accueil. Une chaîne supprimée
  par le système est recréée (Nouveautés et Favoris).
- Google TV : journal de diagnostic (création des chaînes, programmes publiés, « Continuer à regarder », erreurs).

## [1.2.54] — 2026-10-08 (Android TV)

### Ajouté
- Google TV / Android TV : chaîne d'accueil **« Ultra TV · Nouveautés »** — derniers films et séries ajoutés par la
  source active (même ordre que l'accueil de l'application, affiche obligatoire), mise à jour après chaque
  synchronisation ; un programme ouvre la fiche du film ou de la série. À ajouter depuis « Personnaliser les chaînes »
  de l'écran d'accueil.

## [1.2.53] — 2026-10-08 (Android TV) · [Bureau 1.2.33]

### Ajouté
- Trakt : rangées **« Tendances (Trakt) »** et **« Populaires (Trakt) »** à l'accueil, avec la même règle que la
  watchlist et les recommandations : uniquement les titres disponibles dans la playlist active.

### Amélioré
- Trakt s'affiche bien plus vite : dernière bibliothèque et dernières rangées gardées sur l'appareil (affichées dès le
  lancement, puis mises à jour), recherche dans le catalogue lancée au démarrage et écrémée (seuls les titres qui
  peuvent correspondre sont analysés). Côté site, la bibliothèque est servie tout de suite et rafraîchie en
  arrière-plan.

### Corrigé
- Android : « Derniers films / séries ajoutés » triés par numéro du fournisseur au lieu de la date d'ajout (le Mac
  triait par date). Un appui sur Synchroniser remet l'ordre.
- Google TV : « Continuer à regarder » ne recevait souvent rien (publication retardée de 45 s après la dernière
  écriture) ; première publication immédiate. La dernière position d'un film pouvait aussi être perdue en quittant
  le lecteur.
- Android : blocage possible en quittant un film (écran « chargement », appli impossible à relancer sans arrêt
  forcé) — libération de VLC hors du fil principal, état d'appairage lu hors du fil principal pendant la lecture.
- Bureau : film sans son (MKV en AC3 / E-AC3 / DTS, que le lecteur ne décode pas) — détection après ~3 s et
  bascule automatique vers un autre format du fournisseur (HLS), sinon bandeau explicatif.

## [1.2.52] — 2026-10-08 (Android TV)

### Corrigé
- Menu : choisir **Accueil** depuis une fiche ou une autre page ne faisait rien (il fallait appuyer sur Retour). La
  navigation vers l'accueil restaurait la pile qu'elle venait de sauvegarder ; elle revient désormais réellement à
  l'accueil, et chaque onglet garde sa propre pile. TV et mobile.

## [1.2.51] — 2026-10-08 (Android TV) · [Bureau 1.2.32]

### Ajouté
- **Trakt dans les applications** (compte relié depuis l'espace client) :
  - Accueil : rangées « Ma watchlist Trakt » et « Recommandé pour toi (Trakt) ». Seuls les films et séries
    **disponibles dans la playlist chargée** y figurent ; un titre absent n'est jamais affiché, une rangée vide est
    masquée.
  - Marque « Vu » sur les films vus (fiche et affiches) et sur les épisodes vus d'une série.
  - Rapprochement par titre et année (±1 an), titres anglais Trakt complétés des titres localisés et originaux TMDB
    par le site ; règle identique sur le site, Android et le bureau (vecteurs de test partagés).

## [1.2.50] — 2026-10-07 (Android TV) · [Bureau 1.2.31]

### Ajouté
- **Trakt** : films et épisodes regardés sont envoyés à Trakt (en cours, pause, puis vu à la fin). La connexion se
  fait une fois depuis le tableau de bord du compte (Compte › Trakt › *Connecter Trakt*) ; rien à régler sur la TV ni
  sur l'ordinateur. Les appareils signalent seulement ce qu'ils lisent au site, qui relaie à Trakt : aucun jeton
  Trakt sur les appareils. Le direct et le replay ne sont jamais envoyés.
- Tableau de bord du compte :
  - **Abonnement de chaque fournisseur** (statut, expiration, jours restants, connexions), y compris les listes M3U
    de type Xtream ; cause précise quand il est illisible ; relais hors Cloudflare pour les fournisseurs qui
    bloquent Cloudflare.
  - **Compte OpenSubtitles personnel** (facultatif) : les sous-titres téléchargés par les appareils sont décomptés
    sur le quota du client plutôt que sur le quota commun.
  - Recherche et pagination des fournisseurs et appareils ; jusqu'à 100 fournisseurs et 50 appareils par compte.

## [1.2.49] — 2026-10-06 (Android TV) · [Bureau 1.2.30]

### Ajouté
- Tableau de bord du compte (ultratv-config) : distinction utilisateurs standard / Pro.
  - Chaque appareil affiche son édition (STANDARD / PRO) ; un compte avec au moins un appareil Pro est marqué
    « Ultra TV Pro ».
  - Licence Pro : statut, échéance, appareils, revendeur et contacts WhatsApp / Telegram. Le statut est signé par le
    panneau revendeur et sa signature est vérifiée par le site.
  - Sources posées par le revendeur : marquées comme telles, sans lien IPTV ni suppression (leurs identifiants
    appartiennent au revendeur).
- Applications Android et bureau : elles déclarent leur édition au site (en-tête X-Ultra-Edition) ; points d'entrée
  postLicense et `managed: "reseller"` prêts pour l'édition Pro.

## [1.2.48] — 2026-10-06 (Android TV)

### Corrigé
- Lecteur VLC : la lecture saccadait.
  - L'affichage direct du décodeur matériel était désactivé en mode Auto : chaque image était recopiée par le
    processeur. Il n'est désormais désactivé qu'en mode Logiciel.
  - VLC peut de nouveau sauter une image en retard au lieu d'accumuler le retard.
  - Le tampon réseau du direct passe à 2 s (1 s depuis la 1.2.37 : trop peu de marge).

## [1.2.47] — 2026-10-06 (Android TV)

### Corrigé
- Lecteur : en appuyant sur Retour, l'image restait affichée un moment avant le menu. La vidéo (couche à part) est
  maintenant mise en pause et masquée immédiatement, puis le lecteur se ferme. Même chose pour la fermeture par la
  minuterie de sommeil et par l'écran d'erreur.
- Abonnement : il se recharge tout seul quand on change de source par défaut (ou ses identifiants), sans avoir à
  appuyer sur « Actualiser ».

## [1.2.46] — 2026-10-06 (Android TV)

### Corrigé
- Paramètres › Sources : « Définir par défaut » ou « Synchroniser » ouvraient le menu latéral. À la fermeture de la
  fenêtre de choix, le focus perdu partait vers le menu, qui croyait à une navigation (une touche venait d'être
  pressée). Le menu ne s'ouvre plus que sur une touche de déplacement (flèches, Menu) ; sinon, le focus revient à la
  ligne de la page.

## [1.2.45] — 2026-10-06 (Android TV)

### Corrigé
- Menu : la pastille de synchro sous « Paramètres » affichait « Synchronisation · 25 % » coupé. Le pourcentage étant
  déjà dans la pastille, le libellé n'affiche plus que « Synchronisation » (points de suspension si nécessaire).

## [Bureau 1.2.29] — 2026-10-06

### Corrigé
- Guide : un identifiant de guide numérique (certains panels) faisait échouer tout le guide.
- Guide : une coupure pendant son téléchargement ne vide plus le guide existant (remplacé seulement après réception).
- Première synchro qui échoue après le direct : la source n'est plus laissée sur un catalogue vide.
- Synchro interrompue (fermeture, plantage) : les catalogues orphelins (jusqu'à 180 000 films) sont supprimés.
- Lecteur : hls.js ne boucle plus indéfiniment sur un flux au codec cassé ; une reprise de position ne s'applique plus
  à la vidéo suivante.
- Reconnexion du direct : un rechargement resté muet ne bloque plus sur « Reconnexion… » ; un flux rétabli seul n'est
  plus rechargé.
- Mise à jour Mac : erreur d'écriture gérée, pas de double téléchargement, fichiers temporaires supprimés en cas
  d'échec.
- Proxy : les API lentes (player_api, xmltv, get.php) ont 150 s pour répondre ; une VOD en pause n'est plus coupée.
- Suppression d'une source : sa playlist M3U et ses fiches en cache sont supprimées aussi.

### Amélioré
- Comptes à connexion unique : au zap, l'ancien flux est fermé côté serveur avant l'ouverture du nouveau ; les zaps
  rapprochés sont regroupés.
- Liste des chaînes du lecteur : virtualisée (elle rendait jusqu'à 5 000 boutons d'un coup).
- Recherche : par index de mots au lieu de parcourir tout le catalogue à chaque frappe. La recherche porte désormais
  sur le début des mots.
- Lecteur : il n'est plus redessiné en permanence ; l'aperçu n'est plus remesuré toutes les 400 ms.
- Films / Séries, vue « Tout » : seules les rangées proches de l'écran sont montées ; tri par note servi par un index.
- Zapping par index ; délais d'inactivité sur les téléchargements ; divers allègements.

## [1.2.44] — 2026-10-06 (Android TV)

### Corrigé (relecture des versions 1.2.28 à 1.2.43)
- Replay « Depuis le début » et différé : à leur fin, l'application se « reconnectait » huit fois puis affichait une
  erreur. La reconnexion automatique ne s'applique plus qu'au vrai direct.
- Reconnexion :
  - une reconnexion en attente est annulée par un changement de moteur ou de tampon ;
  - « Réessayer » repart de zéro ;
  - une nouvelle tentative après erreur est annulée par un zap (plus de double connexion) ;
  - une erreur VLC survenue après un rechargement compte comme une coupure ;
  - aucun rechargement quand l'application est en arrière-plan (pas de son qui repart).
- Zap avec moteur réutilisé :
  - un sous-titre ou une piste audio choisis à la main ne suivent plus sur la chaîne suivante ;
  - le moteur est recréé si les réglages de sous-titres ont changé.
- Guide au retour sur l'appli : jamais pendant une synchro, sous le même verrou, et retéléchargé au plus une fois par
  heure quand le fournisseur publie un guide court.
- Images : elles ne faussent plus la mesure de débit du lecteur et ne passent plus derrière les téléchargements de la
  synchro.
- « Reprendre au lancement » n'attend plus les 3 s du démarrage différé.
- Planification de la synchro : décidée une seule fois par mode, mémorisée hors des sauvegardes Android.
- Liste des chaînes du lecteur : les programmes sont rafraîchis chaque minute ; plus de 900 favoris sont gérés.

## [1.2.43] — 2026-10-06 (Android TV)

### Corrigé
- Enregistrements : appuyer sur un enregistrement en cours l'arrête, et sur un enregistrement programmé l'annule. Avant,
  la ligne était supprimée pendant que l'enregistrement continuait.
- Playlists M3U : les chaînes partageant un même tvg-id (variantes HD / SD, plusieurs groupes) ne s'écrasent plus.
- Fiche série : plus de clignotement ni de perte du focus à l'ouverture. Les épisodes sont remplacés en une transaction,
  et seulement s'ils ont changé ; la fiche est retéléchargée au plus toutes les 30 min.
- Direct : la liste ne se recharge plus en boucle pendant une synchro (filtre de dédoublonnage inopérant sur un tableau).
- Contrôle parental : « Hero », « Heroes », « Zero »… ne sont plus pris pour des catégories adultes.

### Amélioré
- Recherche :
  - chaînes, films et séries s'affichent tout de suite, les programmes du guide (plus lents) ensuite et à partir de
    3 caractères ;
  - les suggestions Google TV ne bloquent plus le lanceur (0,8 s au plus, sans le guide).
- Accueil :
  - rangées relues au plus une fois par seconde pendant la synchro ;
  - chaînes de repli observées seulement sans favori ;
  - seul le pourcentage de synchro est suivi.
- Affiches TMDB : une erreur réseau n'est pas réessayée pendant 2 min pour un même titre.
- Divers : fuseau horaire et requêtes d'images mis en cache ; expressions régulières des titres créées une seule fois.

## [1.2.42] — 2026-10-06 (Android TV)

### Amélioré
- Mémoire :
  - le cache des affiches TMDB est borné (1 500 titres) et vidé sous pression mémoire, alors qu'il grossissait à
    chaque titre vu ;
  - les images utilisent le client réseau partagé, avec au plus 2 décodages simultanés sur l'entrée de gamme.
- Veille et batterie :
  - la surveillance des blocages se réveille toutes les 5 s (et plus du tout sans télémétrie) au lieu de toutes les
    2 s en permanence ;
  - les changements des autres appareils ne sont récupérés toutes les 10 min que lorsque l'appli est affichée ;
  - Google TV « Continuer à regarder » est mis à jour à l'arrêt de la lecture, et non toutes les 30 s pendant.
- Démarrage :
  - les tâches périodiques ne sont plus réécrites à chaque lancement (configuration mémorisée), ce qui ne décale plus
    l'heure de la synchro quotidienne ;
  - le rapport d'un éventuel plantage précédent est envoyé 20 s après le démarrage.

## [1.2.41] — 2026-10-06 (Android TV)

### Amélioré
- Compteurs (catégories, langues, totaux) : au plus une mise à jour par seconde pendant une synchro, au lieu d'une à
  chaque écriture en base. CategoryManager ne lance plus sa requête deux fois.
- Films / Séries : rangées de la vue « Tous » mises en cache ; une rangée qui revient à l'écran s'affiche tout de suite,
  sans squelette ni clignotement.
- Direct : un déplacement du D-pad ne recompose plus tout l'écran (seul l'aperçu suit, après 300 ms).
- Guide : le panneau du programme focalisé est isolé, les cases de chaque ligne sont calculées une fois, et les lignes
  visibles sont suivies sans copier toute la liste.
- Lecteur :
  - le programme en cours est relu à la fin du programme et non toutes les 20 s ;
  - le guide court du fournisseur est demandé 2 s après le zap, pas pendant l'ouverture du flux ;
  - la fréquence d'écran ne bascule plus quand celle de l'écran convient déjà (ex. 50 Hz pour du 25 i/s), ce qui
    évite 1 à 3 s d'écran noir au zap ;
  - l'enregistrement de progression en double est retiré.
- Menu et bannière de synchro : la progression ne recompose plus tout le menu.

## [1.2.40] — 2026-10-06 (Android TV)

### Amélioré
- Synchro du catalogue incrémentale (films, séries) :
  - les nouveaux éléments sont insérés, et seules les lignes réellement modifiées sont réécrites ;
  - les disparus sont supprimés en fin de passe ;
  - avant, les 180 000 films étaient supprimés puis réinsérés à chaque synchro, avec leurs index et leur index de
    recherche ;
  - les identifiants sont conservés, et les informations enrichies (fond, genre, distribution, durée) ne sont plus
    effacées ;
  - compatible avec le SQLite d'Android 9.
- Base de données (version 17) :
  - « Derniers ajouts » et les rangées de l'accueil sont servis par un index (colonne addedKey) au lieu d'un calcul sur
    toute la table ;
  - index sur la note (élément à la une) et sur la langue (compteurs) ;
  - index des chaînes alignés sur leur tri réel ;
  - un index d'épisodes redondant et trois anciennes recherches inutilisées sont retirés.
- Guide : la liste des chaînes ayant des programmes ne parcourt plus tout le guide.
- Synchro : identifiants de la source encodés une fois par passe (au lieu de deux fois par élément) ; langue calculée
  une seule fois par élément.

## [Bureau 1.2.28] — 2026-10-06

### Corrigé
- Direct : reconnexion automatique quand le serveur ferme la session (serveurs Xtream faibles), que le réseau coupe ou
  que le flux reste figé plus de 12 s (délais croissants, 8 tentatives, message « Reconnexion… »). Avant, l'image
  s'arrêtait avec un bouton « Réessayer » à cliquer soi-même.

## [1.2.39] — 2026-10-06 (Android TV)

### Amélioré
- Synchro du catalogue : les grosses listes (direct, films, séries) sont téléchargées dans un fichier temporaire avant
  l'écriture en base ; la transaction ne reste plus ouverte pendant tout le téléchargement (reprise, favoris et
  historique ne sont plus bloqués des minutes sur une box lente).
- Démarrage :
  - l'intégration Google TV, la migration des secrets et l'import des anciennes catégories ne sont plus construits avant
    le premier écran ;
  - la synchro cloud et du catalogue attend 3 s quand l'appli est déjà remplie ;
  - la version est lue sans appel système.
- Images : le mode mémoire réduite (RGB_565) s'applique enfin sur les box modestes ; il était neutralisé par les bitmaps
  matériels.
- Direct : l'aperçu et l'en-tête de section collant ne recopient plus toute la liste à chaque mise à jour (défilement
  fluide sur les longues listes) ; expressions régulières créées une fois, titre de « Reprendre » nettoyé une fois.

## [1.2.38] — 2026-10-06 (Android TV)

### Amélioré
- Base de données (version 16) :
  - les pages Films / Séries ne retrient plus toute la catégorie à chaque page chargée, grâce à des index alignés sur
    leur ordre ;
  - le mode WAL est imposé : la synchro ne bloque plus les lectures sur les box « peu de RAM ».
- Guide des programmes, beaucoup moins gourmand en stockage :
  - les programmes terminés depuis plus de 3 h sont supprimés après chaque synchro et au retour sur l'application ;
  - les doublons sont interdits (un programme par chaîne et par heure de début) et ceux existants sont supprimés à la
    mise à jour ;
  - les descriptions du guide court sont limitées comme celles du guide complet ;
  - la vérification « le guide couvre-t-il les heures à venir ? » ne parcourt plus toute la table.

## [1.2.37] — 2026-10-06 (Android TV)

### Corrigé
- Lecteur VLC : l'application plantait dès qu'on passait sur VLC dans les versions publiées. R8 renommait les classes
  que la couche native de libVLC retrouve par leur nom (règles de conservation ajoutées, test de garde).
- Direct : reconnexion automatique quand le serveur ferme la session, que le réseau coupe ou que le flux reste figé
  plus de 12 s (délais croissants, 8 tentatives) ; avant, le flux s'arrêtait net.
- Favoris : l'écran ne charge plus tout le catalogue (180 000 films) pour n'afficher que les favoris.

### Amélioré
- VLC : démarrage d'une chaîne avec le tampon de démarrage (1 à 1,5 s) au lieu du tampon minimum (5 s), et moteur
  réutilisé au zap comme Media3.
- Lecteur : plus de flou plein écran à chaque zap sur les box modestes ; l'écran n'est plus redessiné deux fois par
  seconde en direct ; la liste des chaînes se prépare après la première image ; délais réseau du direct raccourcis.

## [1.2.36] — 2026-10-06 (Android TV)

### Amélioré
- Zapping plus rapide : le lecteur (Media3) enchaîne la chaîne suivante sur le moteur en place au lieu d'être détruit
  et recréé à chaque appui (lecteur, vue vidéo, décodeurs) ; l'ancien flux est libéré avant d'ouvrir le suivant
  (connexion unique). Recréé seulement si le moteur, le décodage ou le tampon changent, ou après une erreur.
- La liste des chaînes du lecteur n'est plus recalculée (chaînes + guide) à chaque zap.

## [1.2.35] — 2026-10-06 (Android TV)

### Corrigé
- Direct › Tout : la liste n'est plus remplie d'en-têtes de sections vides (les séparateurs de la source, sans numéro,
  remontaient tous en tête) ; « Tout » n'affiche que les chaînes.

### Modifié
- Direct › Tout : chaînes rangées dans l'ordre des catégories (celui choisi dans Paramètres › Catégories), puis dans
  l'ordre de la playlist ; Haut/Bas suivent exactement cet ordre en lecture.

## [1.2.34] — 2026-10-06 (Android TV)

### Corrigé
- Paramètres : y revenir par le menu ouvre toujours l'accueil des paramètres, plus la sous-page quittée (ex. gestion
  des catégories).

## [1.2.33] — 2026-10-06 (Android TV)

### Modifié
- Lecteur (direct) : Retour ferme le panneau ouvert puis revient au menu ; il ne ramène plus à la chaîne précédente
  (touche « chaîne précédente » de la télécommande pour cela). Le bandeau d'information affiché après un zap ne retient
  plus Retour.

## [1.2.32] — 2026-10-06 (Android TV)

### Corrigé
- Lecteur : la liste des chaînes (OK pendant une chaîne en direct) s'ouvre tout de suite ; elle est tenue à jour
  pendant la lecture au lieu d'être recalculée (file, guide, compteurs de catégories) à chaque ouverture.

## [1.2.31] — 2026-10-06 (Android TV)

### Corrigé
- Réglages › Catégories : en déplaçant une catégorie au-delà du haut (ou du bas) de l'écran, la liste défile avec
  elle ; la sélection et la position ne disparaissent plus.

## [1.2.30] — 2026-10-06 (Android TV)

### Corrigé
- Direct : le programme en cours s'affiche dès l'ouverture de la page et au changement de catégorie (il n'arrivait
  qu'au premier défilement ou après une minute).
- Guide rechargé en arrière-plan au retour sur l'application (sortie de veille comprise) s'il a plus de 12 h ou ne
  couvre plus les 6 prochaines heures : sur une box, l'appli reste en mémoire des jours sans redémarrer.
- Chaînes en double (même nom, même qualité) : les flux de secours sont numérotés « #2 », « #3 »… en Direct et dans
  la liste des chaînes du lecteur.

## [1.2.29] — 2026-10-06 (Android TV)

### Corrigé
- Films / Séries et accueil : la première carte d'une rangée n'est plus rognée à gauche quand elle prend le focus
  (agrandissement et liseré coupés par le bord de la rangée).

## [1.2.28] — 2026-10-06 (Android TV)

### Corrigé
- Menu latéral : ouverture de nouveau fluide (les neuf entrées étaient recomposées à chaque image de l'animation
  depuis la 1.2.25) et libellés affichés sans temps mort.
- Titres coupés sur une ligne : carte « Reprendre la lecture », « Derniers films / séries ajoutés » et grilles
  Films / Séries affichent désormais le titre sur deux lignes.

## [1.2.27] — 2026-10-05 (Android TV)

### Corrections
- Fluidité en changeant d'écran : plus de superposition de l'ancien écran sous le nouveau (fond opaque), menu refermé
  dès le choix d'une page, Films et Séries sans message « aucun film » ni grille à plat transitoires (squelettes de
  rangées à taille fixe pendant le chargement).

## [Bureau 1.2.27] — 2026-10-05

### Corrections
- macOS : mise à jour automatique sans signature Apple. L'application télécharge elle-même la nouvelle version, puis « Installer » remplace l'application et la relance (l'ancienne est restaurée en cas d'échec). À installer une dernière fois à la main ; les suivantes sont automatiques.

## [1.2.26] — 2026-10-05 (Android TV)

### Corrections
- Menu latéral : neuf entrées + synchro + profil tiennent à l'écran (Paramètres n'est plus écrasé en une barre).
- Page série : → depuis « Lecture » va sur l'épisode à reprendre (sinon le premier), plus sur celui du bas.
- Sous-titres : plus activés d'office à la reprise d'un film ou d'une série ; ils suivent le dernier choix (piste choisie = activés, « Désactivés » = désactivés).
- Épisode suivant automatique : enfin effectif en fin d'épisode (Réglages › Lecture), saison suivante comprise ; l'épisode terminé est marqué vu.

## [Bureau 1.2.26] — 2026-10-05 (Mac, Windows, Linux)

### Corrections
- Direct, programme en cours : la description n'est plus coupée au milieu d'une ligne ; « Voir plus » pour la lire en entier.

## [1.2.25] — 2026-10-05 (Android TV) · Bureau 1.2.25

### Nouveautés
- Favoris : onglet **Tout** (ouvert par défaut) qui regroupe chaînes, films et séries, classés par section avec leur nombre.

## [Bureau 1.2.24] — 2026-10-05 (Mac, Windows, Linux)

### Corrections
- Menu latéral : libellés longs (« Abonnement », « Suscripción ») plus tronqués.

## [1.2.24] — 2026-10-05 (Android TV) · Bureau 1.2.23

### Nouveautés
- Menu **Abonnement** (Android, Windows, macOS) : état de l'abonnement IPTV de la source active tel que l'indique le fournisseur — statut, date d'expiration et jours restants, connexions utilisées / autorisées, date de création, compte d'essai, serveur. Lu en direct, bouton Actualiser.

## [Bureau 1.2.22] — 2026-10-05 (Mac, Windows, Linux)

### Corrections
- Favoris : la lecture par type (direct, films, séries) ne renvoyait jamais rien — cœur jamais rempli sur les fiches, onglets de la page Favoris à 0, favoris absents des rangées — alors que les favoris étaient bien enregistrés et synchronisés.

## [Bureau 1.2.21] — 2026-10-05 (Mac, Windows, Linux)

### Corrections
- Favoris partagés : un favori ajouté sur un autre appareil (TV) pour un film ou une série absent du catalogue local était ignoré, puis renvoyé comme « retiré » au prochain échange (et pouvait disparaître de la TV). Il est maintenant appliqué avec la fiche demandée au fournisseur, ou réessayé plus tard, et n'est plus jamais transformé en retrait.
- Favoris et reprises : échange immédiat au retour sur la fenêtre (au plus une fois par minute), en plus du passage toutes les 5 minutes.

## [1.2.23] — 2026-10-05 (Android TV)

### Corrections
- Synchronisation : une réponse coupée en plein téléchargement (JSON tronqué) est réessayée ; si la liste complète d'une partie (direct, films, séries) est coupée et que le serveur filtre par catégorie, les catégories actives sont téléchargées une par une au lieu de faire échouer la synchro. L'ancien catalogue reste en place tant que le nouveau n'est pas complet.

## [Bureau 1.2.20] — 2026-10-05 (Mac, Windows, Linux)

### Corrections
- Direct, films, séries : quand le serveur coupait la liste complète en cours de téléchargement, seules les premières catégories étaient gardées, sans erreur (catégories cochées mais absentes). La coupure est maintenant détectée et les catégories manquantes sont téléchargées une par une, sans doublon.
- Puces de catégories (Direct, Films, Séries) : défilement à la molette et flèches ‹ › au survol ; la catégorie choisie reste visible.
- Rangées horizontales (Accueil, rangées de catégories) : flèches ‹ › au survol pour défiler à la souris.

## [Bureau 1.2.19] — 2026-10-05 (Mac, Windows, Linux)

### Corrections
- Accueil, « Reprendre la lecture » : une affiche de série (image haute) ne fait plus déborder la carte sur toute la largeur ; même correctif pour les vignettes d'épisodes et l'affiche de la fiche série.

## [Bureau 1.2.18] — 2026-10-05 (Mac, Windows, Linux)

### Nouveautés
- Mise à jour prête : bandeau « Redémarrer et installer » dès que la nouvelle version est téléchargée (sinon installation à la fermeture de l'application).
- Application laissée ouverte : nouvelle recherche de mise à jour toutes les 6 heures.

## [Bureau 1.2.17] — 2026-10-05 (Mac, Windows, Linux)

### Corrections
- Films et séries : les tris « Récents », « Ordre du fournisseur » et « Mieux notés » s'appliquent aussi à la vue par rangées de catégories ; « Mieux notés » fonctionne désormais dans une catégorie.
- Mise à jour : la recherche ne trouvait jamais la nouvelle version de bureau (elle lisait la release Android marquée « Latest ») ; elle vise maintenant la dernière release de bureau publiée.
- Mise à jour : une vérification échouée n'affiche plus « Vous avez la dernière version ».

## [1.2.22] — 2026-10-05 (Android TV) · Bureau 1.2.16

### Corrections
- Gestion des catégories : les catégories cochées sont toujours listées en premier (TV, Mac, Windows), ordre du fournisseur conservé dans chaque groupe.
- Bureau : une série regardée sur un autre appareil apparaît avec sa dernière position même si elle n'a jamais été ouverte sur ce poste.
- Bureau : affiches manquantes retrouvées pour les titres longs avec sous-titre (recherche sur le titre principal) ; les anciens « introuvable » sont réessayés.

## [1.2.21] — 2026-10-05 (Android TV)

### Corrections
- Accueil, « Reprendre la lecture » : bas de la carte plus coupé ; affiche de la série pour les épisodes déjà enregistrés sans image.

### Documentation
- README (FR/EN) et site : partage des favoris et reprises, catégories partagées, guide complémentaire, zapping et liste des chaînes, Google TV, lien IPTV du tableau de bord.

## [1.2.20] — 2026-10-05 (Android TV)

### Nouveautés
- Favoris, positions de reprise et derniers vus partagés entre la TV, le Mac et Windows (par source du compte, profils rapprochés par nom ; la modification la plus récente gagne, un favori retiré l'est partout).

### Corrections
- Textes tronqués : la taille de police système de la box n'agrandit plus les textes dans des cadres fixes.
- Épisodes : affiche de la série quand l'épisode n'a pas d'image (carte « Reprendre » vide, absence dans Google TV).
- Google TV : une seule tuile par chaîne (TF1 HD / 4K / UHD).

## [Bureau 1.2.15] — 2026-10-05

### Nouveautés
- Favoris, positions de reprise et derniers vus partagés avec la TV (synchro au lancement, puis toutes les 5 min).

## [1.2.19] — 2026-10-05 (Android TV)

### Corrections
- Lecture : la box ne se met plus en veille pendant un film, un épisode ou le direct.
- Catégories partagées : des réglages reçus pour un type pas encore chargé (séries après le direct) restent en attente au lieu d'être marqués appliqués ; un état local jamais publié (ex. séries activées sur la TV) est publié à la fin de la synchro, et suit sur le Mac.

## [1.2.18] — 2026-10-05 (Android TV)

### Corrections
- Menu : ne s'ouvre plus tout seul (focus récupéré pendant une mise à jour en arrière-plan) ; seulement à la télécommande.
- Page série : la sélection ne saute plus sur un épisode au milieu quand les épisodes se rechargent.
- Films et épisodes : position de reprise enregistrée toutes les 30 s (reprise fiable même après une coupure).

## [1.2.17] — 2026-10-05 (Android TV)

### Nouveautés
- Google TV : les dernières chaînes du direct regardées apparaissent dans « Continuer à regarder » de l'accueil, et la chaîne d'accueil Ultra TV se remplit avec elles quand il n'y a pas (assez) de favoris.

## [Bureau 1.2.14] — 2026-10-05

### Corrections
- « Catalogue vide » : le message indique qu'aucune catégorie n'est active (plus « pour les langues choisies »).

## [1.2.16] — 2026-10-05 (Android TV)

### Corrections
- Guide : les programmes ne disparaissent plus après une resynchronisation des chaînes (identifiants de chaînes conservés) ; une catégorie activée reçoit son guide dans la minute, pas la nuit suivante.
- Lecteur : titre de l'en-tête plus coupé en bas.

### Nouveautés
- Guide complémentaire (Réglages › Synchronisation) : XMLTV France par défaut, ou epgshare01 (beIN Sports, Arabie saoudite, Émirats). Complète uniquement les chaînes sans programme, par identifiant, par nom ou en « +N ».

## [1.2.15] — 2026-10-04 (Android TV)

### Corrections
- Guide : une chaîne sans identifiant EPG reprend le programme de la chaîne du même nom (même pays d'abord) qui en a un — ex. les catégories HEVC, Général, Cinéma dont seule la catégorie VIP RAW porte les identifiants.

## [1.2.14] — 2026-10-04 (Android TV)

### Nouveautés
- Chaînes décalées (« TF1 +1 », « M6 +2 ») : programme de la chaîne de base décalé d'autant d'heures, dans le guide comme dans le Direct.

## [1.2.13] — 2026-10-04 (Android TV)

### Corrections
- Guide : tous les flux d'une même chaîne (HD, 4K, UHD…) reçoivent son programme (avant : un seul).
- Programme court du fournisseur : horaires corrigés du fuseau du serveur (décalage de 2 h avec un serveur à Paris).

## [1.2.12] — 2026-10-04 (Android TV)

### Corrections
- Programme en cours : si le guide complet n'est pas (encore) téléchargé, il est demandé chaîne par chaîne au fournisseur (écran Direct, liste des chaînes du lecteur, bandeau).
- Chaînes en plusieurs exemplaires : pastille de qualité (SD, HD, FHD, 4K) et mentions RAW / HEVC / 50 FPS sur chaque ligne, dans le Direct et dans la liste du lecteur.

## [1.2.11] — 2026-10-04 (Android TV)

### Corrections
- Direct : Haut/Bas zappent toujours (le bandeau de la chaîne s'affiche sans voler le focus), OK ouvre la liste des chaînes, Gauche/Droite ou Menu donnent accès aux commandes. Touches chaîne +/- et TV prises en charge (télécommandes Google TV type Mecool G10).
- Liste des chaînes du lecteur : seulement les catégories actives.
- Le son ne continue plus après avoir quitté l'application (pause en arrière-plan, plus d'image dans l'image sur TV) ; reprise au retour.
- Horloge du lecteur : n'applique plus le décalage du guide (elle avançait d'une heure). Nouveau réglage Affichage › Fuseau horaire.
- Catégories : noms du fournisseur tels quels (plus de doublons « SPORT »), ordre de la playlist conservé (catégories, chaînes, films, séries) ; filtre court « FR » = mot entier.

## [Bureau 1.2.13] — 2026-10-04

### Corrections
- Catégories : noms du fournisseur tels quels (plus de doublons « SPORT ») ; filtre court « FR » = mot entier.

### Tableau de bord
- « Afficher le lien IPTV » sur chaque source (à la demande, avec bouton Copier).

## [1.2.10] — 2026-10-04 (Android TV)

### Corrections
- Catégories : une catégorie activée se charge tout de suite, sans attendre la fin de la synchro complète.
- Lecteur : nouvel écran de chargement (fond flouté, logo, barre de progression) ; options sur une seule ligne.
- Sous-titres en ligne : panneau plus tronqué, plus de faux « Appairez l'appareil » quand le Worker par défaut est utilisé.

## [1.2.9] — 2026-10-04 (Android TV)

### Nouveautés
- Films et Séries : vue « Tous » en rangées par catégorie (comme Netflix), « Voir tout » ouvre la grille de la catégorie.

## [1.2.8] — 2026-10-04 (Android TV)

### Nouveautés
- Accueil : derniers films et séries ajoutés, dernières chaînes regardées.
- Recherche : programmes du Guide TV (« Au programme »), une entrée par œuvre (doublons retirés).
- Affiches manquantes complétées par TMDB (via le Worker).

### Corrections
- Catégories : ordre conservé pendant une synchro, activer/désactiver/tout activer immédiats, « Chargement… » au lieu de « 0 chaîne » avant téléchargement, écran fluide pendant une synchro.
- Plus de filtre ni d'étiquette de langue (Direct, Films/Séries, Catégories).
- Menu : le focus va sur la page ouverte (et non sur Rechercher).
- « Qui regarde ? » : Retour garde le profil courant au lieu de quitter l'application.

## [Bureau 1.2.12] — 2026-10-04

### Nouveautés
- Films et Séries : vue « Tout » en rangées par catégorie (comme sur la TV), « Voir tout » ouvre la grille.

## [Bureau 1.2.11] — 2026-10-04

### Corrections
- Lecteur : panneau Chaînes et zapping ▲▼ limités à la catégorie de la chaîne en cours (même lancée depuis l'accueil ou la recherche) ; titre du panneau = catégorie.
- Direct / Films / Séries : plus de bouton « Langues : … ».

## [Bureau 1.2.10] — 2026-10-04

### Modifications
- Réglages › Catégories : plus de filtre ni d'étiquette de langue (détection peu fiable selon les fournisseurs) ; onglets Direct/Films/Séries, recherche dans le nom, « Tout activer / Tout désactiver » sur la vue filtrée.

## [Bureau 1.2.9] — 2026-10-04

### Nouveautés
- Recherche dans le Guide TV : les chaînes qui diffusent (ou vont diffuser) le programme cherché.
- Réglages › Catégories : langues regroupées par langue (et non par pays), noms lisibles, 12 principales puis « Plus de langues », case partielle visible.

## [Bureau 1.2.8] — 2026-10-04

### Corrections
- Affiches manquantes complétées par TMDB (via le Worker), doublons retirés de la recherche.
- Lecteur, panneau Chaînes : logos, liste centrée sur la chaîne regardée, onglets superposés corrigés.

## [1.2.7] — 2026-10-04

### Nouveautés
- **Catégories et langues partagées entre appareils** (cloud) : masquer/afficher des catégories ou changer les langues sur un appareil s'applique aux autres (le plus récent l'emporte). Interrupteur « Synchroniser catégories et langues » dans Réglages › Cloud, activé par défaut. Au premier lancement de cette version, la configuration déjà faite sur le bureau est publiée pour servir de référence aux autres appareils.
- **Bureau, Réglages › Catégories** : toutes les langues présentes, sans doublon (alias, drapeaux, préfixes reconnus), une case par langue pour tout activer/désactiver d'un coup, « Tout activer / Tout désactiver » sur la vue filtrée.

## [1.2.6] — 2026-10-04

### Corrections
- **Mise à jour intégrée** : installe toujours la version la plus récente (revérification au clic sur « Installer ») ; avant, la version vue au démarrage était téléchargée même si une plus récente était sortie entre-temps.

## [1.2.5] — 2026-10-04

### Nouveautés
- **TV, lecteur en direct** : OK ouvre la liste des chaînes (focus sur la chaîne regardée) ; ▲▼ zappent toujours ; Info/Menu affichent les commandes.
- **Tableau de bord** : « Importer une image du QR » (capture ou photo, ou Ctrl/Cmd+V) pour appairer depuis un ordinateur sans caméra ; décodage local, rien n'est envoyé.

## [1.2.4] — 2026-10-04

### Corrections
- **TV, source reçue du cloud en lien M3U `get.php`** (« Le fournisseur n'autorise pas ce type d'accès ») : le fournisseur cloud est normalisé en Xtream Codes avant la fusion. Avant, la fusion jugeait la version cloud (M3U) différente de la locale déjà convertie (Xtream) et réécrivait l'adresse get.php avec des identifiants vides à chaque synchro cloud. Une source déjà abîmée est réparée automatiquement à la synchro suivante.

## [1.2.3] — 2026-10-04

### Performances (TV)
- Retour au niveau de la 1.1.1 mesuré sur émulateur bas de gamme (CPU du fil principal à moins de 8 %, démarrage identique, mémoire 113 → 102 Mo) : mode tactile lu sans suivi par chaque élément, rail qui ne se recompose plus à chaque image de son animation, profil de démarrage étendu aux nouveaux écrans.

### Corrections
- Grille Films/Séries : la note sous l'affiche n'est plus rognée.

## [1.2.2] — 2026-10-04

### Corrections
- **TV, appairage cloud depuis l'assistant** : la source Xtream reçue passe par le choix des langues avant toute synchro (avant : tout le catalogue d'un coup, box écrasée) ; « Regarder la TV » affiche un écran de chargement au lieu d'un écran noir.
- **TV** : choisir une page referme le menu ; le Guide ne bloque plus GAUCHE vers le menu ; l'invite de mise à jour se ferme toujours et explique la désinstallation unique si la signature change.
- **Bureau** : l'assistant accepte une adresse `get.php` saisie en M3U (conversion en Xtream, plus de « champs obligatoires ») ; première synchro lancée automatiquement après conversion ; choix des langues respecté (catalogue Films/Séries) et reprise d'une synchro interrompue ; Guide TV : correspondance des chaînes sans casse (TF1, France 2… avaient un guide vide) ; textes arabes complets (test de complétude) ; loupe des champs de recherche alignée ; bande de titre réservée sous les boutons de fenêtre macOS.
- **Release** : APK aussi publiés sous un nom stable (`releases/latest/download/UltraTV-<abi>.apk`).

## [1.2.1] — 2026-10-04

### Corrections
- **Mise à jour Android impossible** (« conflit avec un package existant ») : depuis les APK par processeur, la re-signature ne trouvait plus de fichier et les releases partaient signées avec une clé de debug jetable. Tous les APK sont désormais signés avec la clé de release, et la publication échoue si un APK reste signé en debug. *Une désinstallation unique est nécessaire pour quitter la 1.1.0/1.2.0 ; ensuite la mise à jour intégrée fonctionne.*
- **Code d'appairage tronqué** sur box (6 caractères visibles sur 8) : les cases s'adaptent à la largeur disponible.
- **Écran d'appairage de l'assistant** : plein écran (plus de logo en double ni de boutons écrasés).
- **Bureau** : une adresse `get.php` saisie en lien M3U devient une source Xtream Codes (plus d'« HTTP 884 »), y compris pour une source déjà enregistrée ; zone de titre réservée sous les boutons de fenêtre macOS.

### Nouveautés
- **Appairage en un scan** : le QR de la TV ouvre la page d'appairage du tableau de bord avec le code pré-rempli.
- **Nouveau tableau de bord cloud** : identité Ultra TV, clair et sombre, pensé pour le téléphone ; code saisi avec ou sans tiret.

## [1.2.0] — 2026-10-04

### Nouveautés
- **Smartphone et tablette Android** : interface tactile (barre de navigation basse sur téléphone, menu latéral et volets côte à côte sur tablette), lecteur à gestes, formulaires calés au-dessus du clavier.
- **Application de bureau Windows et macOS** (`web/` React + Vite + Dexie, `electron/`) : même design que la TV, catalogue local, identifiants chiffrés par le trousseau système, lecture directe, mise à jour automatique ; publication par tag `desktop-vX.Y.Z`.
- **Synchronisation multi-appareils** : même fournisseur sur tous les appareils d'un compte, source propre à un appareil tant qu'elle n'est pas partagée, choix des appareils destinataires source par source (application et tableau de bord). Worker : `GET /api/config` (`self`, `devices`, `providers` avec `sharedWith`), `POST/PUT/DELETE /api/device/providers`, `PATCH /api/device`, mutations d'un compte sérialisées par un verrou Durable Object.
- **Bureau** : la Recherche sort du rail (bouton rond sous le logo) avec le raccourci `Ctrl/Cmd + K` ou `/` depuis tout écran ; cases à cocher aux couleurs de la charte (accent `#D91E2B`, coche blanche) en clair et sombre.
- Site et READMEs (FR/EN) : une section par appareil, captures téléphone, tablette, bureau et tableau de bord cloud.

### Corrections
- **Accueil Android, source en erreur** : message lisible, bouton « Ouvrir les Réglages des sources » (au lieu de « Corriger la source »), focus initial laissé au menu et non au bouton.
- **Tableau de bord** : les cases d'affectation des sources s'affichent en ligne avec leur libellé (elles prenaient toute la largeur) et reprennent la couleur d'accent.
- Inscription au tableau de bord depuis un navigateur sans en-tête `Referer` (politique `same-origin`, contrôle d'origine tolérant), conservé à la fusion.

## [1.1.1] — à publier

### Corrections
- **Formulaires de source** : le dialogue tient toujours dans l'écran (contenu défilant, boutons « Annuler » / « Ajouter et synchroniser » épinglés et toujours atteignables) ; ▼ depuis le dernier champ et l'action « OK » du clavier mènent à « Ajouter » ; plus de « Champ requis » à l'ouverture.
- **Adresse `get.php` collée comme lien M3U** : détectée et ajoutée comme source Xtream Codes (note « Adresse reconnue »). Une source M3U déjà enregistrée ainsi est convertie au prochain lancement ou à « Réessayer » ; l'erreur du fournisseur (884) propose « Passer en Xtream Codes ».
- **Menu latéral** : animation d'ouverture propre (libellés et logotype après 70 % de l'élargissement, icônes à position fixe, rail rogné, bascule instantanée sans animations ou en mode économe) ; routes explicites et test de correspondance item/route.
- **Arabe (RTL)** : mise en miroir effective avec la langue système arabe, dégradés des visuels, logotype « ULTRA TV », durées, plages horaires et noms de jours dans la langue choisie, tuiles de profils.
- **Traductions** : écrans « Profils » complétés en espagnol et en arabe, « Resynchroniser », badges d'épisodes en arabe ; un test échoue si une clé manque ou si une valeur reste identique à l'anglais.

### Nouveautés
- **Nouvelle icône** « écran » (adaptative avec couche monochrome, bannière TV, logo des écrans).
- **Recherche** accessible depuis n'importe quel écran : bouton sous le logo du menu latéral, touches Recherche et micro de la télécommande.
- **Depuis le cloud** : 4e option de l'étape Source et de l'accueil vide ; l'adresse du tableau de bord s'affiche en clair avec un QR code (écran d'appairage et Réglages › Sources) ; la box porte une étiquette hachée « UTV-XXXXXX ».
- **Mise à jour intégrée** : télécharge l'APK adapté au processeur (`UltraTV-<version>-<abi>.apk`, vérifié par `SHA256SUMS.txt`), repli sur l'universel.
- Documentation : README en français et en anglais, captures en français, anglais et arabe, site FR/EN.

### Retraits
- **Support Stalker** retiré (jamais validé). Une source Stalker déjà enregistrée s'affiche « Type de source non pris en charge » et peut être supprimée.

## [1.1.0]

Refonte complète de l'interface, deux moteurs de lecture, réglages automatiques selon l'appareil, profils, replay, pause du direct et fiches enrichies.

### Design
- Interface reprise à la maquette sur tous les écrans : accueil, Direct, guide, Films, Séries, fiches, recherche, favoris, enregistrements, réglages, appairage, formulaires d'ajout, états vides, hors ligne et erreur de source.
- Thèmes Sombre, Clair et Automatique ; jetons de couleur sensibles au thème. Le lecteur reste toujours sombre : l'état dérive désormais de la navigation (plus de compteur d'entrées/sorties).
- Polices Sora et Manrope embarquées (licence OFL) ; échelle d'écran normalisée sur la hauteur (720p, 1080p et 4K identiques).
- Retrait de MultiView et de l'ancien code éditorial (Instrument Serif, Geist, jetons `UltraTokens`).

### Performance et appareils
- Réglages automatiques : l'`AdaptiveProfile` classe l'appareil (Bas, Moyen, Haut) et dimensionne tampon, parallélisme de synchro, taille des lots, plafond de résolution et débit ; box à peu de mémoire : 720p / 6 Mb/s maximum.
- Synchronisation incrémentale (durée de vie par partie du catalogue), téléchargement par catégorie, pic mémoire de synchro divisé par plus de deux sur un catalogue de 54 000 chaînes.
- Index plein texte (FTS4) pour la recherche instantanée ; listes paginées ; images dimensionnées au besoin.

### Sécurité
- Appairage au cloud par code à 8 caractères : jeton d'appareil aléatoire de 256 bits, haché côté serveur, chiffré dans le Keystore sur l'appareil, révocable. La MAC n'est plus une clé.
- Identifiants des sources chiffrés au repos (Worker : AES-GCM). Aucune URL de flux ni identifiant affiché ou journalisé (`UserText` masque URL et identifiants dans les messages).
- Un nom de source laissé vide ne reprend plus l'adresse du serveur.

### Lecteurs
- Deux moteurs : ExoPlayer (Media3) et LibVLC, choix Auto / ExoPlayer / VLC et décodage Auto / Matériel / Logiciel, avec repli automatique et mémorisation par chaîne.
- Préréglages de tampon (Faible latence, Auto, Équilibré, Stable, Personnalisé) et adaptation en cas de rebuffering.
- Panneau de réglages à onglets (Pistes, Affichage, Lecteur, Statistiques) ; qualité préférée et « Autres qualités ».

### Langues, catégories, profils
- Choix des langues à la première source, avant la synchro ; filtre SQL par langue (chaînes, films, séries) ; langues par profil.
- Catégories actives (un seul interrupteur), séparateurs en en-têtes de section, badges de qualité, nettoyage des noms décoratifs.
- Profils : écran « Qui regarde ? », gestion dans les réglages, profil Enfants, favoris et historique par profil.

### Replay, pause du direct, rappels
- Replay (catch-up Xtream) depuis le guide ; pause du direct par tampon disque circulaire.
- Rappels et enregistrements programmés depuis le guide, notifications au démarrage du programme.
- Zapping : saisie du numéro, Retour = chaîne précédente, 20 chaînes récentes ; minuterie de veille 30 / 60 / 90 min ou fin de programme.

### Fiches, sous-titres, Google TV
- Fiches enrichies via TMDB (affiche, synopsis, distribution, bande-annonce) par le Worker ; détails film et série en cache.
- Sous-titres : recherche en ligne OpenSubtitles par le Worker, style avancé (taille, couleur, fond, contour, position, décalage).
- Google TV : Watch Next, chaîne Favoris, recherche vocale et globale, liens profonds `ultratv://`.

### Worker
- Proxy TMDB en liste blanche (jeton v4 Bearer, repli clé v3) et proxy OpenSubtitles ; secrets `TMDB_READ_TOKEN`, `TMDB_API_KEY`, `OPENSUBTITLES_API_KEY`.

### Corrections notables
- Installation neuve : écran noir permanent (aucun profil créé par Room hors migration).
- Lecteur : pilules devenues claires après un lien profond ouvert pendant la lecture.
- Migrations Room : chaîne unique `ALL_MIGRATIONS` ; base de la 1.0.30 migrée sans perte.

## [1.0.30]
Dernière version de la série 1.0 (voir les notes de version GitHub).
