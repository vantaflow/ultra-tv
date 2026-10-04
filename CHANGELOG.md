# Changelog

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions suivant `VERSION`.

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
