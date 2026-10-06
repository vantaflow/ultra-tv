# Changelog

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions suivant `VERSION`.

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
