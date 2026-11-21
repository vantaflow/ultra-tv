# Interface « façon streaming » — document de conception

Date : 2026-11-21. Périmètre : application Android (`android-native`), TV et téléphone.

## Intention

Une personne habituée aux grandes applis de streaming doit se sentir chez elle dès le premier écran d'Ultra TV, pour que
l'appli attire et garde plus de monde. Aujourd'hui l'accueil ressemble à un tableau de bord (carte « À la une », grille de
six raccourcis, cartes sombres) ; les habitudes attendues sont une image plein écran, des rangées d'affiches, un focus très
visible, une fiche qui donne envie, un lecteur aux commandes peu nombreuses et nommées.

On reprend ces **habitudes de navigation**, pas l'apparence ni la marque d'une autre appli : l'identité d'Ultra TV reste
(Sora pour les titres, Manrope pour le reste, et les thèmes existants avec leurs couleurs ; le rouge `#FF3B47` et le
fond `#0A0C10` des maquettes sont ceux du thème Sombre).

## Référence visuelle

Maquettes validées par le propriétaire le 2026-11-21 : canevas « Ultra TV — parcours façon streaming », 24 écrans
(23 en 1920 × 1080, unités = unités `design` de l'appli ; un accueil téléphone 390 × 844). Les numéros d'écran ci-dessous
renvoient à ce canevas. Les affiches et fonds y sont des aplats : l'appli garde ses vraies images (source, TMDB, Fanart).
Les équipes, ligues, le QR code et le code d'envoi y sont des emplacements.

## Objectifs

1. Accueil : image « À la une » en fond plein écran, rangées d'affiches, le Direct en première rangée (écrans 1, 2, 6).
2. Fiches film et série avec fond plein écran, reprise, épisodes par saison (écrans 3, 4).
3. Lecteur : commandes peu nombreuses et nommées, bandeau de zapping, récentes et soirée, panneau audio / sous-titres /
   qualité, écran d'erreur (écrans 7, 8, 9, 16, 17, 18, 19).
4. Parcours : Direct par catégories avec aperçu, guide, recherche, films et séries par genre, « Ma liste », sport,
   enregistrements (écrans 10 à 14, 20, 21).
5. Entrée dans l'appli : profils, ajout de source, première synchronisation, code parental (écrans 5, 22, 23, 24).
6. Réglages : rubriques simples, réglages techniques repliés sous « Avancé » (écran 15).

## Hors périmètre

- L'application de bureau (Windows, Mac, Linux) et les paquets TV Samsung / LG.
- Tout changement du modèle de données, de la synchronisation, des moteurs de lecture ou du Worker.
- Lecture automatique d'une bande-annonce dans l'image à la une.
- Les écrans non maquettés : bibliothèques (Jellyfin, Plex, NAS), fiche d'une personne, mode simple, temps d'écran,
  compte et appareils, écrans téléphone autres que l'accueil. Ils gardent leur aspect actuel et héritent seulement des
  composants communs là où ils les utilisent déjà.

## Ce qui ferait de ce chantier un échec

- Une régression sur ce qui a été corrigé : 4K en Auto et « Qualité maximale », mode d'affichage lu sur TV seulement,
  rangées cochées de l'accueil tactile (`MOBILE_SECTIONS`), focus rendu au bon élément après une modale, clés de listes
  départagées, messages d'erreur du lecteur et de la synchronisation.
- Une fonction existante qui disparaît. Tout ce qui est accessible aujourd'hui le reste, au pire derrière « Plus ».
- Une interface plus lente ou moins fluide qu'aujourd'hui, sur n'importe quel appareil, et en particulier sur les box
  modestes (1 Go de mémoire, Android 7).
- Un focus ambigu à la télécommande : à tout moment un seul élément focalisé, visible à trois mètres.

## Contraintes

- **Télécommande d'abord.** Haut / bas change de rangée, gauche / droite parcourt, Retour remonte d'un niveau. Aucune
  cible atteignable seulement en diagonale. Le focus restauré au retour sur un écran est celui qu'on a quitté.
- **Tous les thèmes sont conservés** (demande explicite du propriétaire) : Sombre, Clair, Automatique, OLED, et les
  thèmes de couleur Minuit, Forêt, Prune, Sable (clair), Contraste et Sarcelle, chacun avec son fond, ses surfaces, son
  accent et sa couleur de focus. Les maquettes sont dessinées dans le thème Sombre ; elles fixent la mise en page, pas
  les couleurs. On passe toujours par les jetons `Ux.*`, jamais par des couleurs écrites en dur : l'accent, l'anneau de
  focus et les surfaces sont ceux du thème choisi. Seule exception, ce qui est posé sur une image ou sur la vidéo : voile
  sombre et texte clair, boutons blancs, identiques dans tous les thèmes (comme l'OSD aujourd'hui). Chaque lot vérifie
  son écran dans tous les thèmes (captures) et ne retire ni ne renomme aucun thème.
- **Appareils modestes.** Pas de flou ni d'animation coûteuse quand `LocalLowRam` est vrai ou sous Android 12 ; images
  demandées à la taille affichée ; listes paresseuses avec `key` et `contentType` ; l'image à la une ne change qu'une
  fois le focus arrêté.
- **Langues.** Tout nouveau texte entre dans `strings.xml` en anglais, français, espagnol et arabe, écrit par le
  constructeur dans la PR du lot, avec sa ligne dans les fichiers de référence `i18n-golden` (complétés à la main, comme
  pour les lots précédents) ; les autres langues passent par Weblate et affichent l'anglais en attendant. Mise en page correcte en arabe
  (droite à gauche).
- **Dépôt public.** Aucune mention d'une appli concurrente dans le code, les commits, le README ou le CHANGELOG.
- **Livraison.** Une PR par lot, CI verte, CHANGELOG et README (fr et en) à jour quand le comportement visible change.
  Aucune version publiée sans accord explicite du propriétaire.

## Approches envisagées

1. **Écran par écran, en place** (retenue). Un premier lot pose les composants communs ; chaque lot suivant remplace un
   écran existant et se livre seul. Avantage : petites PR relisibles, chaque lot testable et réversible par un simple
   retour arrière. Inconvénient : pendant quelques versions, des écrans refaits côtoient des écrans anciens. C'est
   acceptable parce que l'identité (couleurs, polices, jetons) ne change pas.
2. **Derrière un réglage « Nouvelle interface ».** Les deux interfaces coexistent, on bascule par défaut à la fin.
   Écartée : deux chemins de code à maintenir et à tester pour chaque écran, et un réglage de plus alors que le retour
   des utilisateurs est « trop de réglages ».
3. **Une seule grande branche.** Écartée : une PR impossible à relire, des conflits permanents avec les correctifs
   courants, et un risque de régression maximal le jour de la fusion.

Le propriétaire a délégué ce choix ; la première approche est celle de l'auteur du document.

## Décisions

Décidé par le propriétaire :
- Le périmètre « tout le parcours » plutôt que l'accueil seul, et la validation des 24 écrans.
- Les réglages techniques repliés sous « Avancé » (demandé après un retour d'utilisateur : « trop de réglages »).
- Zéro régression, et pas de publication sans son accord.

Décidé par l'auteur du document, à corriger si besoin :
- **Identité actuelle conservée** (aucun système de design externe repris).
- **Le Direct reste la première rangée de l'accueil**, sous l'image à la une.
- **« Ma liste » = les favoris existants** (chaînes, films, séries), sans nouvelle table. Le libellé « Favoris » devient
  « Ma liste » dans le menu ; l'écran donne aussi accès aux enregistrements, téléchargements et rappels (écran 13).
- **Raccourcis de l'accueil TV.** Le bloc `SHORTCUTS` reste proposé dans Réglages › Accueil, mais il est masqué par
  défaut sur TV : le menu latéral donne déjà ces accès. Un utilisateur qui a personnalisé son accueil garde son choix ;
  celui qui n'y a jamais touché reçoit le nouveau défaut. Le téléphone garde ses filtres rapides (écran 6).
  Sans nouvelle donnée : la disposition est déjà « par défaut » quand les deux préférences `home_order` et `home_hidden`
  sont vides. Le défaut devient dépendant de l'appareil (sur TV, `SHORTCUTS` masqué ; sur téléphone, inchangé) et
  l'éditeur de Réglages › Accueil part de ce défaut, si bien que la première personnalisation enregistre `SHORTCUTS`
  parmi les blocs masqués. Une préférence non vide n'est jamais réinterprétée.
- **Image à la une.** Même source qu'aujourd'hui (`heroFlow` : films et séries ayant une image paysage, celle de la source
  ou de TMDB, avec l'affiche seule en dernier recours), même rotation, mêmes réglages d'animation. Elle ne suit pas
  l'affiche focalisée dans les rangées. Sans aucun titre, l'accueil commence directement par les rangées, comme
  aujourd'hui.
- **Lecteur en différé ou en retour arrière.** La barre garde la logique actuelle de `OsdActions.build` : en différé,
  les actions de saut et de programme précédent / suivant prennent la place qu'elles ont aujourd'hui. La règle nouvelle
  est seulement celle-ci : les sept premières actions de la barre sont affichées avec leur nom, les suivantes sous
  « Plus ». « Depuis le début » et « Retour au direct » restent un seul bouton.
- **Commandes du lecteur direct** : sept boutons nommés (Pause, Depuis le début, Chaînes, Guide, Enregistrer, Audio et
  sous-titres, Favori) puis « Plus », qui ouvre toutes les autres actions de `OsdActionId` existantes. Aucune action
  n'est supprimée. « Depuis le début » n'apparaît que si la chaîne le permet, comme aujourd'hui.
- **Affiche ou carte focalisée** : agrandissement de 10 % (6 % pour les vignettes larges) et l'anneau de focus du thème,
  sans bandeau d'infos supplémentaire. L'anneau blanc des maquettes est celui du thème Sombre ; il n'est imposé que sur
  une image.
- **Top 10** : la rangée existante `TOP10`, avec le rang dessiné en grand derrière l'affiche.

## Ce qui existe déjà et sert tel quel

- **Soirée de la chaîne** (écran 17) : c'est le ruban existant de `ui/player/osd/OsdRibbon.kt` ; les chaînes récentes
  viennent de `LiveOsdLogic.recents`. Aucune nouvelle requête sur le guide.
- **Ma liste** : `ui/favorites/FavoritesScreen.kt` affiche déjà chaînes, films et séries favoris. Enregistrements
  (`ui/recordings`), téléchargements (`ui/downloads`) et rappels (`data/reminders`) existent : l'écran 13 n'ajoute que
  des accès.
- **Top 10, reprise, « Parce que vous avez regardé »** : rangées existantes de `HomeSectionId`.
- **Messages d'erreur du lecteur** (écran 19) : `PlayErrorText` et `ErrorPanel`, livrés en 1.3.72 ; le lot 5 ne change
  que la mise en page (chaîne nommée au-dessus du titre).

## Navigation à la télécommande : logique et stable, exigence de premier rang

Demande explicite du propriétaire pendant l'exécution du premier lot. Comme la performance, c'est une condition
d'acceptation de chaque lot, vérifiée par des tests qui envoient de vraies touches.

Logique :
- ▲ ▼ changent de rangée ou de bloc, ◀ ▶ parcourent ; l'ordre du focus suit l'ordre visuel, y compris en arabe (droite à
  gauche) ;
- depuis n'importe quel élément, chaque direction mène à un élément visible ou ne fait rien : jamais de focus perdu, hors
  écran, ou sur un élément décoratif (image, voile, pastille, heure, chiffre du Top 10) ;
- ◀ au début d'une rangée ouvre le menu latéral, et ▶ depuis le menu revient sur l'élément quitté ;
- Retour remonte d'un niveau : il ferme un panneau, puis quitte l'écran ; il ne ferme jamais deux niveaux d'un coup.

Stable :
- le focus ne bouge jamais sans touche : ni quand l'image à la une change, ni quand une rangée se met à jour (synchro,
  programme qui change, image qui arrive) ;
- en revenant sur un écran, le focus est sur l'élément quitté ; en revenant sur une rangée, sur la carte quittée ;
- la position de défilement suit le focus sans saut : l'élément focalisé est entièrement visible, la rangée ne se recale
  pas toute seule ;
- le focus initial d'un écran est toujours le même élément, donné une seule fois par visite.

Chaque lot ajoute, pour son écran, un test de parcours : focus initial, les quatre directions depuis les éléments de
bord, aller-retour avec le menu latéral, retour après ouverture d'une fiche ou d'un panneau, et mise à jour des données
pendant que le focus est posé.

## Performance et fluidité : exigence de premier rang

Demande explicite du propriétaire à la validation de ce document : les performances et la fluidité doivent rester au
meilleur niveau. Ce n'est pas réservé aux appareils modestes ; c'est une condition d'acceptation de chaque lot.

Mesure, avant et après chaque lot, sur le même appareil ou émulateur, pour l'écran refait :
- les mesures que l'appli journalise déjà : lignes `perf <écran> frames=… janky=… slow=… p95≈…` et `perf stall … route=…` ;
- le délai jusqu'au premier écran utilisable au démarrage à froid quand le lot touche l'accueil ou les profils.

Un lot n'est pas fusionné si, pour son écran, le taux d'images lentes ou le p95 se dégrade, ou si de nouvelles lignes
`perf stall` apparaissent. Les chiffres avant / après figurent dans la PR.

Règles de construction :
- listes paresseuses partout, avec `key` stable et `contentType` ; aucune liste composée d'un bloc ;
- images demandées à la taille affichée ; l'image de fond plein écran est une seule image, décodée à la taille de
  l'écran, jamais un flou calculé pendant un défilement ;
- pas de recomposition de tout l'écran quand le focus bouge : l'état de focus reste local à la carte ;
- l'agrandissement au focus passe par `graphicsLayer` (pas de nouvelle mesure de la rangée) ;
- les voiles sur image sont des dégradés dessinés une fois, pas des calques empilés qui se redessinent ;
- l'image à la une ne change qu'une fois le focus arrêté et jamais pendant un défilement rapide ;
- le travail de données reste hors du fil principal, comme aujourd'hui (`flowOn`, formatage hors composition).

Appareils modestes (`LocalLowRam`, ou Android antérieur à 12) : image à la une fixe, sans rotation ni fondu, sans flou ;
animations réduites selon `LocalAnimations`. Le profil de démarrage livré avec l'APK (`.dm`) est régénéré quand un lot
change l'écran d'accueil.

## Lots et ordre

Chaque lot est une PR indépendante. L'ordre met d'abord ce que voit un nouvel arrivant.

| Lot | Contenu | Écrans | Code existant concerné |
|---|---|---|---|
| 0 | Composants communs : affiche avec focus, vignette large avec progression, image de fond avec voiles, en-tête de rangée, pastille de filtre, bouton pilule principal / secondaire | — | `ui/design`, `ui/components` |
| 1 | Accueil TV : image à la une plein écran, rangées, Top 10, défaut du bloc raccourcis | 1, 2 | `ui/home/HomeScreen.kt`, `HomeLayout.kt`, `HomeLogic.kt` |
| 2 | Accueil téléphone | 6 | `ui/mobile/MobileHome.kt` |
| 3 | Fiches film et série | 3, 4 | `ui/movies/MovieDetailScreen.kt`, `ui/detail/*`, `ui/series/SeriesDetailScreen.kt` |
| 4 | Lecteur direct : commandes, « Plus », bandeau de zapping, récentes et soirée | 7, 16, 17 | `ui/player/osd/*`, `ui/player/PlayerScreen.kt` |
| 5 | Lecteur : liste des chaînes, audio / sous-titres / qualité, erreur | 8, 18, 19 | `ui/player/LiveDrawer.kt`, `PlayerScreen.kt` (`ErrorPanel`) |
| 6 | Lecteur film et série : barre de lecture, épisode suivant | 9 | `ui/player/PlayerScreen.kt`, `ui/player/osd/OsdTv.kt` |
| 7 | Direct par catégories avec aperçu, guide | 20, 10 | `ui/live/LiveScreen.kt`, `ui/guide/GuideGridScreen.kt` |
| 8 | Films et séries par genre, recherche | 12, 11 | `ui/catalog/*`, `ui/search/SearchScreen.kt` |
| 9 | Ma liste, enregistrements et téléchargements | 13, 21 | `ui/favorites/FavoritesScreen.kt`, `ui/recordings`, `ui/downloads` |
| 10 | Sport | 14 | `ui/sport/*` |
| 11 | Réglages et « Avancé » | 15 | `ui/settings/SettingsScreen.kt` |
| 12 | Profils, ajout de source, première synchronisation, code parental | 5, 22, 23, 24 | `ui/profile`, `ui/onboarding`, `ui/sync`, `ui/parental` |

Le lot 0 ne change aucun écran : il ajoute les composants et leurs tests. Les lots 1 à 12 ne dépendent que du lot 0 et
peuvent être réordonnés.

## Règles de chaque lot

- La logique (ViewModel, repository, DAO) ne change pas, sauf ajout strictement nécessaire à l'affichage ; les fonctions
  pures existantes (`HomeLogic`, `LiveOsdLogic`, `PlayErrorText`, `DetailLogic`…) gardent leurs tests.
- Avant de modifier un écran : lire ses tests et les commentaires qui racontent un correctif passé, et lister dans la PR
  les comportements à préserver.
- Tests : un test par comportement nouveau (ordre des rangées, défaut des raccourcis, actions sous « Plus », focus
  initial et focus restauré), plus les suites existantes de l'écran. `AdaptiveTest` et `HomeLogicTest` relancés à chaque
  lot qui touche la lecture ou l'accueil.
- Vérification visuelle : captures de l'écran refait en sombre, en clair et en arabe, sur TV et, quand l'écran existe sur
  téléphone, en portrait et en paysage. Les captures de `docs/screenshots` sont mises à jour par le lot.
- Si un lot révèle qu'une maquette ne peut pas tenir une fonction existante, on s'arrête et on le signale au propriétaire
  plutôt que de retirer la fonction.

## Laissé au constructeur

- Le découpage interne des composants du lot 0 et leurs noms.
- Les durées et courbes d'animation, dans l'esprit actuel de `LocalAnimations`.
- La manière de dessiner le rang du Top 10 et les voiles sur image.
- L'ordre des lots 2 à 12 si une dépendance pratique apparaît.
