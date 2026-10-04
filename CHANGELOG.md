# Changelog

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Versions suivant `VERSION`.

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
