# Plan : réduire les requêtes Worker (prochaine mise à jour des clients)

Quota gratuit Cloudflare : **100 000 requêtes Worker par jour**. Mesure du 15/11/2026 (statistiques HTTP des zones
ultratv.dpdns.org et weedy.dpdns.org, par chemin) :

| Famille | 24 h | % | 7 jours | % |
|---|---:|---:|---:|---:|
| Synchro : réglages / état / profils / sources (`/api/device/…`) | 7 954 | 22,1 | 13 056 | 14,5 |
| Appairage / envoi (`/api/pair/*`, `/api/envoi/*`) | 5 091 | 14,1 | 13 815 | 15,3 |
| Télémétrie (`/api/event`, `/api/crash`) | 4 880 | 13,5 | 15 649 | 17,3 |
| Fichiers fixes (icônes, scripts, polices, manifeste) | 4 624 | 12,8 | 10 883 | 12,1 |
| Pages du site / tableau de bord | 3 986 | 11,1 | 12 414 | 13,7 |
| Annonces (`/api/announcements`) | 2 577 | 7,1 | 7 404 | 8,2 |
| Métadonnées (TMDB, notes, fanart, TVmaze) | 1 992 | 5,5 | 6 926 | 7,7 |
| Synchro : `/api/config` | 2 151 | 6,0 | 3 992 | 4,4 |
| Téléchargements (`/download`, `/apk`) | 1 776 | 4,9 | 3 280 | 3,6 |
| Relevé anonyme, Trakt, autres | 1 032 | 2,9 | 2 882 | 3,2 |
| **Total** | **36 063** | | **90 301** | |

**Déjà fait côté Worker (15/11)** : fichiers fixes servis par Workers Static Assets (hors quota, ≈ 13 %) ; 304 sur
`/api/config` enfin actif (l'ETag faible ajouté par la compression de Cloudflare n'était pas reconnu).

---

## 1. Télémétrie par lots — Android TV / Android (le bureau n'envoie pas de journaux)

État actuel : `RemoteLog.kt` fait **un POST `/api/event` par ligne** (`event()`, l. ~150-160).

À faire :
- **Worker d'abord** (compatible avec les anciennes versions) : nouvelle route `POST /api/events`, corps
  `{ "events": [ { ts, level, tag, message }, … ] }`, 50 entrées et 32 Ko au plus, chaque entrée passée par le même
  nettoyage et par `keepEvent` ; `/api/event` reste en place.
- **Client** : file en mémoire dans `RemoteLog` (50 entrées au plus, les plus anciennes écartées), vidée par un seul POST
  `/api/events` **toutes les 5 min**, au passage en arrière-plan (`onStop`) et au-delà de 50 entrées.
  Les erreurs déclenchent un envoi dans les 60 s ; les plantages gardent leur envoi immédiat (`/api/crash`).
- Échantillonnage côté client des lignes `info` (`perf`, `nav`, `googletv`, `keys` : 90 % du volume) : 1 sur 10,
  comme le Worker, pour ne même pas les envoyer.
- Gain attendu : ≈ 12 % du total (une requête par appareil toutes les 5 min au lieu d'une par ligne).

## 2. Synchro adaptative — Android TV / Android, Windows / Mac / Linux

État actuel : toutes les **2 min** appli affichée — Android `CloudSyncManager.kt` (l. ~258,
`delay(2 * 60_000L)` → `syncOnForeground()`), bureau `web/src/cloud/service.ts` (l. ~450,
`setInterval(light, 2 * 60_000)`). Chaque passage envoie `/api/config` **puis** réglages / état / profils par source.

À faire :
- **Cadence adaptative** : 2 min pendant 10 min après un changement (local, ou reçu d'un autre appareil),
  puis **10 min** tant que rien ne bouge ; retour immédiat à 2 min au premier changement. Retour sur l'appli
  (premier plan / focus) : une synchro immédiate, au plus une par minute (déjà le cas sur le bureau).
- **Ne pousser que ce qui a changé** : réglages, état et profils envoyés seulement si leur contenu local a changé
  depuis le dernier envoi réussi (empreinte gardée en mémoire), sinon seul `/api/config` (304 léger) part.
- **Worker (optionnel, compatible)** : ajouter à la réponse de `/api/config` une révision par compte
  (`stateRev`, incrémentée à chaque écriture d'état / profils / réglages) ; le client ne relit l'état que si elle a changé.
- Gain attendu : ≈ 25 à 30 % du total (familles synchro).

## 3. Appairage et envoi — Worker seul, sans mise à jour des clients

Les applis interrogent `/api/pair/poll` et `/api/envoi/poll` à l'intervalle **fixé par le serveur** (`interval: 3`
dans `pairStart` / `envoiStart`, `src/index.js`), jusqu'à 10 min : jusqu'à 200 requêtes par écran resté ouvert.
- Passer `interval` à **5 s** (code saisi en général en moins d'une minute : 2 s d'attente en plus au pire).
- Gain attendu : ≈ 5 % du total, immédiat pour toutes les versions installées.

## 4. Recherches TMDB par vignette — Android TV / Android

Observé le 15/11 : un appareil lance une recherche `/api/tmdb/search/movie` **par vignette affichée** en faisant défiler
une catégorie VOD (rafales de 60+ par minute, limitées en 429). Sur des catalogues dont les titres ne sont pas des films
référencés (packs, dates, numéros), aucune ne trouve de résultat.
- Mémoriser les recherches **sans résultat** (titre normalisé → « rien », 30 jours) et ne plus les relancer.
- Ne chercher que les vignettes **arrêtées à l'écran** (anti-rebond 500 ms au défilement), 4 recherches en parallèle au plus.
- Ne pas chercher les titres qui ne ressemblent pas à un film : moins de 3 lettres, seulement des chiffres ou une date.
- Respecter `retry-after` sur un 429 (pause des recherches au lieu de continuer la rafale).

## Ordre conseillé

1. Point 3 (Worker, sans risque).
2. Route `/api/events` côté Worker, puis client Android (point 1).
3. Synchro adaptative Android et bureau dans la même version (point 2).
4. Recherches TMDB économes (point 4), dans la même version Android que le point 1.

---

# Retours utilisateurs et améliorations (15/11/2026)

Rien n'est codé : liste pour les prochaines versions (une issue GitHub par point).

## Bugs

1. **Recherche Films / Séries : catégories désactivées visibles.** Toutes les catégories désactivées (dont adultes),
   la recherche de droite dans Films ou Séries montre quand même leurs titres. La recherche doit filtrer par
   catégories visibles du profil (téléchargées ET non masquées), comme les grilles.
2. **Qualité très basse (240p-360p) sur tous les flux.** Probablement la sélection de piste adaptative HLS
   (ExoPlayer démarre bas et ne remonte pas, ou plafond de taille d'écran mal détecté). Vérifier la sélection de piste
   (taille de la vue, débit estimé initial), proposer « Qualité : auto / maximale ».
3. **Suppression d'une source incohérente entre appareils.** Supprimée sur un appareil : elle revient (la synchro la
   restaure) ou reste sur les autres. Supprimée depuis le tableau de bord : elle reste sur l'appareil. Il faut une
   suppression propagée (tombe côté Worker, appliquée par tous les appareils à la synchro suivante).
4. **Affectation des playlists aux appareils.** L'affectation existe dans l'appli mais ne fonctionne pas, et manque au
   tableau de bord : choisir quels appareils reçoivent quelle playlist (une playlist sur plusieurs appareils).
5. **Réglages : position de défilement gardée d'un volet à l'autre.** Défiler en bas d'« Affichage » puis ouvrir
   « Lecture » arrive au même endroit ; chaque volet doit repartir en haut (ou garder sa propre position).
6. **Fire TV Stick : appli absente de « Vos applis » après installation.** Bannière et LEANBACK_LAUNCHER sont
   présents : vérifier sur un vrai Fire TV (liste « Voir tout », icône carrée de lanceur, `amazon.hardware.fire_tv`).

## Fonctions

7. **Exporter les chaînes favorites en M3U** (fichier ou lien), depuis l'appli et le tableau de bord.
8. **Plusieurs playlists affichées en même temps** dans l'appli (catalogue fusionné, source indiquée).
9. **Playlist par profil** sur un même appareil (chaque profil choisit sa ou ses sources).
10. **Intégration Simkl** (suivi, comme Trakt).
11. **Nouvelles langues** : roumain, norvégien bokmål, tchèque, finnois, danois, grec, thaï, vietnamien.
12. **Lecture « tunnelisée » (Tunneled Playback)** en option dans Réglages › Lecture (décodage tunnel, utile sur
    certaines box pour la synchro audio / vidéo et le 4K).
13. **Métadonnées MediaSession** (titre, chaîne, image, durée) : affichées par le système, commandes Google / Fire TV.
14. **Rattrapage façon Kodi PVR** (catch-up : programme passé lancé depuis le guide, timeshift des flux compatibles).
15. **AFR et calcul des images par seconde** : détection plus fiable de la fréquence réelle avant le changement de mode.

Déjà fait : **débit dans la fiche d'infos du lecteur** (avec la résolution réelle, depuis la 1.3.35).
