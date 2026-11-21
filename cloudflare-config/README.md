# Ultra TV — Worker de configuration (appairage par jeton d'appareil)

Un Worker Cloudflare qui laisse l'utilisateur gérer ses fournisseurs IPTV depuis un
navigateur et les faire récupérer par la TV. Version 2 : **aucune lecture anonyme,
aucun secret partagé dans l'APK**.

Site : **<https://ultratv.dpdns.org>** (tableau de bord, `/apk`, `/envoyer`, `/download/*`, `/donate`) ; API des applis : `tv.weedy.dpdns.org` (ses pages redirigent vers le site). L'ancienne adresse `ultratv-config.khalilbenaz.workers.dev` reste active (même Worker, mêmes jetons) ; les applis basculent automatiquement de l'une à l'autre si une est injoignable. Les cookies de session sont liés à l'hôte : une reconnexion est nécessaire une fois sur le nouveau domaine. Trakt : ajouter `https://tv.weedy.dpdns.org/trakt/callback` aux adresses de retour de l'application Trakt.

```
cloudflare-config/
├── src/index.js      routeur et handlers
├── src/guard.js      Durable Object : débit, verrouillage, codes d'appairage
├── src/crypto.js     AES-GCM, PBKDF2, jetons, comparaison en temps constant
├── src/sanitize.js   nettoyage des identifiants dans les journaux
├── src/http.js       en-têtes de sécurité, cookies signés, CSRF, lecture bornée
├── src/pages.js      pages HTML (CSP à nonce)
├── src/store.js      comptes, appareils, validation des fournisseurs, réglages partagés
├── src/turso.js      stockage Turso présenté avec les interfaces D1 et KV (STORAGE_MODE : d1 / tee / turso)
├── src/docs.js       stockage des documents (comptes, appareils, jetons, caches), table kv_docs
├── scripts/turso-migrate.mjs  migration D1 + KV → Turso (schema / sync / verify) ; scripts/e2e-prod.mjs : parcours complet en production
├── src/donate.js     page /donate (liens Ko-fi et Liberapay)
├── migrations/       schéma D1 (télémétrie, activité, état partagé, kv_docs)
├── test/             113+ tests vitest (attaques rejouées, parcours, migration)
└── scripts/e2e.sh    rejeu des attaques contre `wrangler dev`
```

## Modèle de menace (résumé)

| Menace | Contre-mesure |
|---|---|
| Lire la config d'autrui en devinant une MAC | La MAC n'est plus une clé. Lecture = jeton d'appareil de 256 bits (`Authorization: Bearer`) ; `GET /api/config/:mac` répond `410`. |
| Fuite de la base (D1, KV) | Identifiants des fournisseurs chiffrés AES-256-GCM (clé dans un secret Wrangler, AAD = compte) ; mots de passe PBKDF2 ; jetons d'appareil stockés hachés (SHA-256). |
| Force brute du mot de passe | PBKDF2-SHA256 100 000 itérations + sel ; limite par IP et par compte ; verrouillage progressif 60 s, doublé à chaque échec, plafonné (15 min compte, 1 h IP). Compteurs dans un Durable Object (cohérence forte). |
| Squatter le compte d'une MAC | Plus de compte « par MAC » : on s'inscrit avec un identifiant et un mot de passe ; l'appareil se lie par code. Un ancien compte-MAC non migré ne peut pas être inscrit par un tiers. |
| Voler le jeton d'un appareil | Révocation depuis le tableau de bord (effet immédiat sur le Worker, jusqu'à ~60 s ailleurs : cache), rotation `POST /api/device/rotate`, effacement automatique côté app si 401. |
| XSS / clickjacking | CSP `default-src 'none'` + nonce (aucun `unsafe-inline`), échappement systématique, `X-Frame-Options: DENY`, pas d'attribut `on*=` ni `style=`. |
| CSRF | Jeton lié à la session (HMAC) + contrôle de l'en-tête `Origin` + cookie `__Host-` `SameSite=Strict`. Aucun CORS ouvert. |
| Lire les crashs/logs d'autrui | `/crashes` et `/logs` : secret serveur `OPS_TOKEN` (Basic ou Bearer), jamais en query string, jamais dans l'APK, verrouillage par IP. |
| Spam d'ingestion | Réservée aux appareils appairés ; 60 événements/h et 20 crashs/h par appareil, 300/h par IP ; corps ≤ 8 Ko (événement) / 48 Ko (crash). |
| Identifiants dans les logs | Nettoyés côté app **et** côté serveur (`username=`, `password=`, `token=`, `user:pass@`, `/live/u/p/`, Bearer). |

## Synchro multi-appareils et affectations

Chaque fournisseur porte `assign` : `"all"` (défaut, aussi pour les données antérieures) ou une liste d'identifiants d'appareils du compte.
Un appareil ne reçoit, ne modifie et ne supprime QUE les fournisseurs qui lui sont affectés (un identifiant d'une autre source répond `404`, comme un inconnu).

| Route (jeton d'appareil) | Rôle |
|---|---|
| `GET /api/config` | `{version, self, devices[{id,name,model,lastSeen,isCurrent}], providers[{…, sharedWith, originDeviceId, updatedAt}]}` filtré par affectation ; `ETag: "v<version>"`, `If-None-Match` → `304`. |
| `POST` ou `PUT /api/device/providers` | Crée (privé à l'appareil, sauf `shareWith: "all" \| [deviceIds]`) ou met à jour (`id`). Même validation que le tableau de bord, chiffrement AES-GCM, 30 écritures / 10 min / appareil. |
| `DELETE /api/device/providers/<id>` | Retire l'appareil de l'affectation (supprime la source si plus personne) ; `?all=1` la supprime du compte. |
| `PATCH /api/device` (ou `POST /api/device/self`) | `{name}` : renomme l'appareil. |

**Réglages partagés entre appareils** (tous chiffrés, liés au compte) :

| Route (jeton d'appareil) | Rôle |
|---|---|
| `POST /api/device/profiles` | Profils du compte : nom, couleur, enfants, langue de l'interface ; fusion renvoyée à l'appareil. Jamais de code PIN. |
| `POST /api/device/handoff` | « Envoyer vers… » : `{to, ref:{src,t,id,par?,pos,n}}`, dépose une demande dans la file de 2 min (3 au plus) de l'appareil cible. Jamais d'URL de flux ni d'identifiants. La cible la lit dans l'en-tête `x-utv-handoff` (base64url) de `GET /api/config`, y compris sur un 304 : aucune interrogation de plus. |
| `PUT /api/device/providers/<id>/prefs` | Réglages d'une source : langues, catégories désactivées par profil, `order` (ordre des catégories), `chOrder` (ordre des chaînes), `hidden` (chaînes masquées par TOUS les profils), `pHidden` (catégories masquées par profil), `pHiddenCh` (chaînes masquées par un seul profil, par nom) et `pOrder` (ordre des catégories par profil, par nom). Le plus récent gagne, un envoi plus ancien répond `409`. |
| `POST /api/device/providers/<id>/state` | Favoris, reprises et derniers vus de la source (table D1 `shared_state`). |

Les applis vérifient ces changements toutes les 2 min (appli ouverte) et au changement de profil.

Tableau de bord : matrice fournisseurs × appareils (cases + « Tous »), appareils renommables, origine et date de chaque fournisseur. Révoquer un appareil le retire des listes explicites.
Toutes les mutations d'un compte (fournisseurs, affectations, appareils, mot de passe, rotation) sont sérialisées par un verrou Durable Object `mut:<login>` ; le compte est relu SOUS le verrou et `version` n'est incrémentée que dans ce critique (aucune écriture perdue entre appareils et tableau de bord). Portail Stalker (`kind: STALKER`) : `mac` est obligatoire et normalisé (`00:1A:79:XX:XX:XX`), `username` / `password` facultatifs ; la MAC est envoyée aux appareils mais jamais réaffichée en entier sur le tableau de bord (masquée), aucun lien IPTV n'est proposé et l'abonnement se lit dans l'application. Pour les autres types, `mac` est ignoré.

## Déploiement

```bash
cd cloudflare-config
npm ci

# 1) Base Turso (offre gratuite, région européenne) : tout le stockage (STORAGE_MODE = "turso" dans wrangler.toml).
#    Schéma = migrations/ (0001 à 0004) + table kv_store (remplace KV). TURSO_URL dans wrangler.toml, jeton en secret.
TURSO_URL=libsql://… TURSO_AUTH_TOKEN=… node scripts/turso-migrate.mjs schema
wrangler secret put TURSO_AUTH_TOKEN
#    Sans Turso : STORAGE_MODE = "d1", remettre les blocs [[d1_databases]] / [[kv_namespaces]] (wrangler d1 create,
#    wrangler d1 migrations apply --remote, wrangler kv namespace create CONFIG).

# 2) Secrets (JAMAIS dans un fichier versionné)
wrangler secret put SESSION_SECRET     # >= 32 caractères aléatoires : openssl rand -base64 48
wrangler secret put PROVIDER_ENC_KEY   # clé AES-256 en base64      : openssl rand -base64 32
wrangler secret put OPS_TOKEN          # >= 32 caractères : mot de passe de /crashes et /logs
wrangler secret put TMDB_READ_TOKEN   # facultatif : jeton de lecture TMDB v4 (fiches enrichies)
wrangler secret put TMDB_API_KEY       # facultatif : clé TMDB v3, repli si pas de jeton v4
wrangler secret put SIMKL_CLIENT_ID       # facultatif : identifiant de l'application Simkl (https://simkl.com/settings/developer/) ; avec SIMKL_CLIENT_SECRET, active Simkl
wrangler secret put SIMKL_CLIENT_SECRET   # facultatif : secret de l'application Simkl (exigé par l'échange du code d'autorisation). Sans les DEUX, Simkl est masqué (tableau de bord et applis)
wrangler secret put OPENSUBTITLES_API_KEY  # facultatif : sous-titres en ligne (clé commune ; chaque client peut relier son compte)
wrangler secret put OMDB_API_KEY           # facultatif : notes IMDb / Rotten Tomatoes (clé gratuite omdbapi.com)
wrangler secret put FOOTBALL_DATA_KEY      # facultatif : football-data.org v4 (10 req/min) — calendriers, classements, direct des 5 grands championnats + C1
wrangler secret put API_FOOTBALL_KEY       # facultatif : api-sports.io v3 (100 req/JOUR) — Botola, compositions, statistiques des joueurs
wrangler secret put SPORTSDB_API_KEY       # facultatif : TheSportsDB (offre payante) ; sans lui, clé publique gratuite « 123 »
wrangler secret put ACCOUNT_RELAY_KEY      # facultatif : clé partagée avec le relais d'abonnement (voir « Relais d'abonnement »)
wrangler secret put ADMIN_TOKEN        # >= 32 caractères, uniquement pour la migration (à supprimer ensuite)

# 3) Vérifier puis déployer
npm test
wrangler deploy --dry-run              # validation seule
wrangler deploy                        # déploiement réel (le Durable Object `Guard` est créé par la migration v1)
```

Variable `SITE_HOST` (`wrangler.toml`, `ultratv.dpdns.org`) : adresse du site. Les pages HTML demandées sur une autre adresse (API `tv.weedy.dpdns.org`, `workers.dev`) y sont redirigées ; l'API reste servie partout. Le site est en cinq langues (anglais, français, espagnol, arabe, polonais).

Le Worker **échoue fermé** : sans `SESSION_SECRET` (≥ 32 car.), `PROVIDER_ENC_KEY` valide (32 octets) ou
`OPS_TOKEN`, il répond 500 générique plutôt que de se rabattre sur une valeur par défaut.
Les anciens `ADMIN_PASSWORD` et `CRASH_TOKEN` ne sont **plus lus**.

## Parcours

1. **Inscription** sur `/signup` : identifiant + mot de passe (10 caractères minimum).
2. Sur la TV, **Réglages → Pair** affiche un code `ABCD-EFGH` (valable 10 minutes, usage unique).
3. Dans le tableau de bord, panneau **Appairer un appareil** : saisir le code, nommer l'appareil.
4. La TV (qui interroge `POST /api/pair/poll`) reçoit **une seule fois** un jeton `utv_…` de 256 bits,
   rangé chiffré dans le Keystore Android. Le serveur ne garde que son empreinte SHA-256.
5. Ajouter des fournisseurs dans le tableau de bord ; la TV les lit avec `GET /api/config`.
6. Perte ou vol de la TV : **Révoquer** dans la liste des appareils.

## Routes

| Méthode | Chemin | Authentification | Rôle |
|---|---|---|---|
| POST | `/api/pair/start` | aucune (limité par IP : 10/h) | La TV demande un code et un secret de sondage |
| POST | `/api/pair/poll` | secret de sondage | La TV récupère son jeton (une seule fois) |
| POST | `/api/envoi/start` | aucune (débit par IP) | « Depuis le téléphone » : la TV obtient un code de 6 caractères et un secret |
| POST | `/api/envoi/send` | code (débit et verrouillage par IP) | Le téléphone envoie la source (Xtream, M3U, Stalker), chiffrée et gardée 15 min au plus |
| POST | `/api/envoi/poll` | code + secret | La TV récupère la source (une seule fois, puis supprimée) |
| GET | `/envoyer?c=CODE` | aucune | Page publique d'envoi (sans compte) |
| POST | `/api/anon/ping` | aucune (débit par IP) | Relevé anonyme d'une installation sans compte (table D1 `anon_pings`, conservation 180 jours) |
| POST | `/api/anon/crash`, `/api/anon/events` | aucune (60/h par IP ; par installation : 10 plantages et 30 lots par heure) | Plantages et lignes d'erreur d'un appareil sans compte : modèle, version, pays, empreinte courte de l'identifiant d'installation (table `events`, mêmes durées) |
| GET | `/api/config` | `Bearer` jeton d'appareil | Fournisseurs déchiffrés pour cet appareil |
| POST | `/api/device/rotate` | `Bearer` | Nouveau jeton, l'ancien meurt |
| POST | `/api/event`, `/api/crash` | `Bearer` | Télémétrie nettoyée et bornée (table D1 `events` : 7 jours, crashs 30 jours) |
| GET | `/api/announcements?audience=…&version=…` (tv, mobile ; version de l'appli) | — | Alertes diffusées maintenant (lecture publique, cache 60 s) |
| POST | `/api/announcements/{id}/stat` `{kind: shown\|dismissed\|action, device}` | — (120/h par IP) | Statistique d'une alerte, comptée une fois par appareil (identifiant d'installation haché) |
| POST | `/admin/announcements`, `/admin/announcements/{id}/end`, `…/delete` | session admin + CSRF | Programmer, arrêter, supprimer une alerte (ADMIN_LOGINS) |
| GET | `/api/config/:mac` | — | **410 Gone** (ancienne lecture anonyme supprimée) |
| GET | `/admin`, `/admin/accounts`, `/admin/anon`, `/admin/devices`, `/admin/alerts` | session admin (ADMIN_LOGINS, sinon 404) | Administration : vue d'ensemble, comptes, installations sans compte (application, version, pays), appareils, alertes ; tableaux paginés, triables, filtrables |
| GET | `/events` (alias `/logs`), `/crashes` | session admin, ou Basic/Bearer `OPS_TOKEN` | Journaux et plantages (pagination, tri, filtres) |
| POST | `/api/admin/migrate` | Bearer `ADMIN_TOKEN` | Migration de l'ancien format (voir ci-dessous) |
| GET/POST | `/login`, `/signup` | — | Comptes |
| GET | `/` | session | Tableau de bord |
| GET | `/donate`, `/donate/merci` | — | Dons : Ko-fi (<https://ko-fi.com/ultratv>), Liberapay (<https://liberapay.com/ultratv/>) |
| POST | `/api/device/profiles`, `/api/device/providers/:id/state` ; PUT `/api/device/providers/:id/prefs` | `Bearer` | Synchro des profils, de l'état partagé et des réglages (voir « Synchro multi-appareils ») |
| POST | `/pair`, `/providers`, `/providers/:id/delete`, `/devices/:id/revoke`, `/password`, `/account/delete`, `/logout` | session + CSRF + Origin | Mutations |
| POST | `/providers/:id/account` | session + CSRF | Abonnement du fournisseur (cache 10 min, limite propre 600/h) |
| POST | `/subtitles/link`, `/subtitles/unlink` | session + CSRF | Compte OpenSubtitles du client (chiffré ; jeton 20 h, stocké comme les autres documents) |
| POST | `/trakt/connect`, `/trakt/disconnect` | session + CSRF | Connexion Trakt (OAuth PKCE, état lié au compte, 10 min) |
| GET | `/trakt/callback` | session | Retour de Trakt : jetons chiffrés dans le compte |
| POST | `/api/device/trakt/scrobble` | `Bearer` | L'appareil signale sa lecture ; le Worker relaie à Trakt |
| GET | `/api/device/trakt/library?lang=` | `Bearer` | Watchlist, recommandations et vus, avec clés de rapprochement (cache chiffré 15 min) |
| POST | `/simkl/connect`, `/simkl/disconnect`, `/tracking/service` | session + CSRF | Simkl (OAuth, code d'autorisation, état lié au compte, 10 min) et choix du service de suivi : `trakt`, `simkl` ou `none` |
| GET | `/simkl/callback` | cookie de liaison + état | Retour de Simkl : jeton chiffré dans le compte (n'expire pas) ; adresse de retour à déclarer chez Simkl : `https://<site>/simkl/callback` |
| GET, POST | `/api/device/tracking` | `Bearer` | Service de suivi choisi, services proposés et reliés ; POST `{service}` change le choix |
| POST, GET | `/api/device/tracking/{scrobble,library,watchlist,history,rating}` | `Bearer` | Interface commune de suivi : aiguillée vers le service actif du compte (identique à `/api/device/trakt/*`, qui reste servi pour les applis existantes) |
| GET | `/api/subtitles/search`, `/api/subtitles/download` | `Bearer` | Sous-titres (au nom du compte OpenSubtitles relié s'il y en a un) |

### Service de suivi : Trakt ou Simkl

Le compte choisit UN service de suivi (`acct.tracking` : `trakt`, `simkl`, `none` ; sans choix, Trakt s'il est relié, sinon
Simkl). Les applis n'ont qu'une interface (`/api/device/tracking/*`, ou l'ancien `/api/device/trakt/*`) ; le Worker aiguille
vers le service actif (`src/tracking.js`). Simkl (`src/simkl.js`) renvoie la même bibliothèque que Trakt (watchlist, vus,
notes), sans recommandations, tendances, listes ni reprises (Simkl n'en a pas) ; le retrait d'un titre de la watchlist
n'existe pas côté Simkl (réponse `error: "unsupported"`). Le cache de bibliothèque est propre à chaque service.
Configuration : créer une application sur Simkl, y déclarer l'adresse de retour `https://<site>/simkl/callback`, puis
`wrangler secret put SIMKL_CLIENT_ID` et `SIMKL_CLIENT_SECRET` (aucune valeur dans le dépôt). Sans ces deux variables,
Simkl est masqué.

## Données sportives : plusieurs fournisseurs gratuits croisés

`/api/sports/league` (direct, calendrier, classement) et `/api/sports/match` (feuille de match) sont servis par
`src/sportsdata.js`. Les appareils n'appellent jamais un fournisseur ; la réponse est toujours au même format. Les clés
sont des secrets Wrangler (aucune valeur dans le dépôt, les réponses, les clés de cache ni les journaux).

| Fournisseur | Secret | Quota réel → budget suivi | Ligues | Capacités |
|---|---|---|---|---|
| football-data.org v4 | `FOOTBALL_DATA_KEY` (en-tête `X-Auth-Token`) | 10/min → 8/min | ligue1, premier, liga, seriea, bundesliga, ucl | direct, calendrier, classement, feuille de match |
| Jolpica-F1 (Ergast) | aucun | 4/s → 60/min | f1 | calendrier, classement pilotes, résultats d'un GP |
| API-Football v3 | `API_FOOTBALL_KEY` (en-tête `x-apisports-key`) | 100/jour → 90/jour (direct 25, feuille 40, calendrier 15, classement 10) et 8/min | Botola en premier choix ; les 6 autres ligues football en dernier recours | + compositions, statistiques d'équipe et de joueurs |
| TheSportsDB | `SPORTSDB_API_KEY` (sinon clé publique `123`) | ≈ 30/min → 25/min (90/min avec clé) | toute la liste blanche (nba, nfl, nhl, mlb, top14, premrugby : seule source) | direct, calendrier, classement, feuille de match |

**Choix par requête** : pour (ligue, capacité), le fournisseur de plus petit rang qui couvre la ligue, a sa clé et du
budget ; sinon le suivant ; sinon la dernière copie en cache (jusqu'à 24 h), renvoyée avec `"stale": true`. Rangs :
football-data et Jolpica 1, TheSportsDB 5 (6 pour la Botola), API-Football 9 (1 pour la Botola). Un 429 met le
fournisseur en pause 1 min (6 h si API-Football annonce son quota atteint).

**Budget** : compteurs par minute / jour / capacité dans l'API Cache (par centre de données, donc approximatifs : les
budgets gardent une marge) ou dans le KV Turso quand `STORAGE=turso` (compteur global). État lu une fois, au premier
besoin amont, et écrit une fois au plus par appel du Worker. Chaque `fetch` et chaque lecture/écriture de cache compte
comme une sous-requête ; plafond de 50 par appel.

**Caches partagés par tous les utilisateurs** : direct 60 s (un seul jeu d'appels pour toutes les ligues), calendrier
1 h, classement 6 h, feuille de match 2 min (en cours) ou 6 h (terminée). Les données venues d'API-Football ont un cache
plus long : calendrier 3 h, classement 12 h, direct 5 min, feuille 5 min / 24 h. Le direct de la Botola n'appelle
API-Football que si un match est plausible (calendrier en cache avec un match à ±3 h).

**Croisement** : les matchs de deux sources sont rapprochés par date ±3 h et noms d'équipes normalisés (accents, sigles,
alias). Le direct fusionne football-data et TheSportsDB (progression, matchs manquants) ; le calendrier reçoit les scores
en direct déjà connus (aucun appel de plus) ; la feuille de match football-data est enrichie par API-Football
(statistiques, compositions, joueurs) tant qu'il reste plus de 40 % du budget « feuille ».

**Identifiants de match** (champ `i`) : numérique = TheSportsDB, `fd-N` football-data, `af-N` API-Football,
`jo-AAAA-N` Jolpica. Les champs ajoutés (`src`, `lineups`, `players`, `results`) sont facultatifs pour les écrans.

## Relais d'abonnement

Certains fournisseurs refusent toute requête venant de Cloudflare. Le Worker délègue alors la lecture de
`player_api.php` à `relay/` (fonction Vercel, projet `utv-relay`) : une seule requête, uniquement ce chemin, sans
redirection suivie, hôtes locaux et privés refusés, rien de conservé. Il n'est appelé que si `ACCOUNT_RELAY_URL`
(`wrangler.toml`) et le secret `ACCOUNT_RELAY_KEY` (même valeur que `RELAY_KEY` sur Vercel) sont présents.

```bash
cd relay && npx vercel deploy --prod
KEY=$(openssl rand -hex 32)
printf %s "$KEY" | npx vercel env add RELAY_KEY production
(cd .. && printf %s "$KEY" | npx wrangler secret put ACCOUNT_RELAY_KEY); unset KEY
npx vercel deploy --prod
```

## Serveur de secours (Vercel)

`backup/` (projet Vercel `utv-backup`) rejoue le même Worker, en lecture seule, quand Cloudflare est en panne : lecture
de `/api/config`, des annonces et de l'état partagé. Les applis y basculent seulement sur panne ou 5xx. Plan, sécurité,
limites et mise en service : `docs/plan-secours-vercel.md`.

## Rotation des secrets et des jetons

| Élément | Comment | Effet |
|---|---|---|
| Jeton d'un appareil | Révoquer dans le tableau de bord, ou `POST /api/device/rotate` (l'app le fait seule après 90 jours) | L'appareil doit se ré-appairer (révocation) ou continue (rotation) |
| `SESSION_SECRET` | `wrangler secret put SESSION_SECRET` | Toutes les sessions web meurent ; les appareils ne sont pas touchés |
| `OPS_TOKEN` | `wrangler secret put OPS_TOKEN` | Les anciens accès `/crashes` `/logs` sont coupés |
| `PROVIDER_ENC_KEY` | 1) `wrangler secret put PROVIDER_ENC_KEY_PREVIOUS` avec l'**ancienne** valeur ; 2) `wrangler secret put PROVIDER_ENC_KEY` avec la nouvelle ; 3) chaque compte est ré-chiffré à sa prochaine écriture (ajout/suppression de fournisseur) ; 4) retirer `…_PREVIOUS` quand plus rien ne l'utilise | Aucune interruption |
| Mot de passe d'un compte | Tableau de bord → Mot de passe | Les sessions existantes de ce compte meurent |

Un blob chiffré avec une clé qui n'est plus ni `PROVIDER_ENC_KEY` ni `…_PREVIOUS` est illisible : ne retirez
jamais l'ancienne clé avant d'avoir vérifié.

## Migration des données existantes

L'ancien format stocke, sous la clé `<mac>` en clair, `{ providers: [...], passwordHash, salt }` avec les
identifiants Xtream **en clair**, lisibles sans authentification tant que `protectReads` n'était pas activé.
Il faut donc considérer **tous les identifiants IPTV déjà stockés comme exposés** : invitez les utilisateurs à
changer leur mot de passe chez leur fournisseur.

```bash
# après déploiement du nouveau Worker, avec ADMIN_TOKEN configuré
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" "https://<worker>/api/admin/migrate?limit=500"
# répéter avec ?cursor=<cursor renvoyé> tant que "done" vaut false
```

L'endpoint est idempotent et paginé. Pour chaque entrée :

- **compte protégé par mot de passe** : recopié en `acct:<mac>` avec les fournisseurs **chiffrés** ; le
  clair est supprimé après l'écriture. L'utilisateur se connecte avec **son ancienne MAC comme identifiant et son
  ancien mot de passe** ; le hachage SHA-256 ou PBKDF2 hérité est remplacé par le nouveau format à la première
  connexion. Il doit ensuite **appairer** sa TV (l'ancienne app ne fonctionne plus).
- **entrée sans mot de passe** : **supprimée** (elle était lisible par n'importe qui). L'utilisateur crée un
  compte et ressaisit ses fournisseurs.
- crashs et journaux hérités (non nettoyés, lisibles avec le jeton public) et compteurs `lk:*` : supprimés.

Une fois terminé : `wrangler secret delete ADMIN_TOKEN` (l'endpoint répond alors 404). Tant qu'une entrée
héritée n'est pas migrée, son identifiant de type MAC ne peut pas être inscrit par un tiers.

## Tests

```bash
npm test          # 113+ tests dans workerd (@cloudflare/vitest-pool-workers), secrets factices
```

Rejouer les attaques et le parcours contre un vrai `wrangler dev` (secrets de **test** uniquement) :

```bash
printf 'SESSION_SECRET=%s\nPROVIDER_ENC_KEY=%s\nOPS_TOKEN=%s\nADMIN_TOKEN=%s\n' \
  "$(openssl rand -base64 48)" "$(openssl rand -base64 32)" "$(openssl rand -hex 32)" "$(openssl rand -hex 32)" > /tmp/test.vars
wrangler dev --local --port 8787 --env-file /tmp/test.vars &
OPS_TOKEN=… ADMIN_TOKEN=… ./scripts/e2e.sh
```

## Limites connues

- PBKDF2 est plafonné à 100 000 itérations par la plateforme Workers ; la protection repose donc aussi sur la
  limitation de débit et les verrouillages.
- Le verrouillage par compte permet à un tiers de bloquer temporairement la connexion d'un compte dont il
  connaît l'identifiant (15 min maximum). Compromis assumé face à la force brute.
- Les caches de lecture (jetons d'appareil, KV de repli) sont éventuellement cohérents : une révocation peut mettre
  jusqu'à ~60 s à se propager hors du point de présence qui l'a reçue.
- Turso indisponible : les écritures de télémétrie sont perdues sans erreur, les requêtes de compte échouent (500) ;
  retour arrière possible vers D1 + KV (conservés intacts) en remettant leurs liaisons et STORAGE_MODE = "d1".
- Le jeton d'appareil transite en clair dans le Durable Object entre la saisie du code et la remise à la TV
  (au plus 10 minutes, supprimé à la remise).
