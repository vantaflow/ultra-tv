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

## Ordre conseillé

1. Point 3 (Worker, sans risque).
2. Route `/api/events` côté Worker, puis client Android (point 1).
3. Synchro adaptative Android et bureau dans la même version (point 2).
