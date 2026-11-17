# Serveur de secours sur Vercel (`utv-backup`)

But : quand le Worker Cloudflare ne répond pas (panne, 5xx, délai dépassé), les applis lisent leur configuration et les
annonces sur un second serveur, hébergé sur Vercel (offre Hobby), qui lit **les mêmes données Turso**. Le secours est en
**lecture seule** : il ne remplace pas le Worker, il évite que les appareils perdent leur configuration pendant une panne.

## 1. Routes doublées

| Route | Secours | Pourquoi |
|---|---|---|
| `GET /api/config` (Bearer) | oui | Synchro des sources : le cœur du besoin. Lecture seule (l'enregistrement d'édition / version est ignoré). |
| `GET /api/announcements` | oui | Lecture publique, aucune écriture. |
| `POST /api/device/providers/:id/state` | oui, **lecture seule** | Corps vide (`fav` et `hist` vides) = lecture de l'état partagé. Corps non vide = écriture : 503 `backup_read_only`, l'appli garde ses changements et réessaie sur le principal. |
| `GET /api/device/tracking` | non (v1) | Peut déclencher un rafraîchissement de jetons Trakt/Simkl, donc des écritures. |
| Tout le reste | 503 | Voir ci-dessous. |

Exclus, répondent `503 {"error":"backup_unavailable"}` : tableau de bord web, pages HTML, admin, appairage
(`/api/pair/*`, `/api/envoi/*`, Durable Object), écritures (fournisseurs, profils, préférences, rotation du jeton,
licence, handoff, scrobble), télémétrie (`/api/event(s)`, `/api/crash`, `/api/anon/ping`), proxys tiers (TMDB, sports,
sous-titres : clés et quotas qui n'ont pas à être dupliqués), jsQR et polices. Sans configuration complète, **toutes** les
routes répondent `503 {"error":"backup_not_configured","missing":[…noms…]}` (jamais de valeur).

Côté applis, seules `GET /api/config` et `GET /api/announcements` basculent en v1 (voir §3).

## 2. Réutiliser le code du Worker sans le dupliquer

Le dossier `cloudflare-config/backup/` ne contient **aucune copie** de la logique : il importe `src/index.js` (le même
`export default { fetch }`) et lui fournit un `env` fabriqué à partir des variables d'environnement Vercel.

- **Runtime** : Node (Fluid compute, par défaut ; l'Edge n'apporte rien ici, et le pilote Turso n'utilise que `fetch`).
  `fetch`, `crypto.subtle`, `Request/Response` y sont natifs, comme dans Workers.
- **Bundle** : `backup/build.mjs` (esbuild, déjà présent via wrangler) assemble `src/index.js` + `@tursodatabase/serverless`
  en un seul fichier `backup/dist/worker.mjs`. Le module `cloudflare:workers` (classe `Guard`, Durable Object) est remplacé par
  un stub vide : `Guard` n'est jamais instanciée. Le dossier `dist/` est ignoré par git mais envoyé par `vercel deploy`
  (le déploiement part de `backup/`, qui n'a pas accès à `../src` côté Vercel : d'où le bundle construit en local).
- **Couche `env`** (`backup/src/env.js`) :
  - `STORAGE_MODE="turso"`, `TURSO_URL`, `TURSO_AUTH_TOKEN`, `PROVIDER_ENC_KEY` (+ `PROVIDER_ENC_KEY_PREVIOUS`) lus dans
    `process.env` ; `SITE_HOST` facultatif. `src/turso.js` (`withStorage`) fabrique ensuite `env.DB` et `env.CONFIG`.
  - **Stockage en lecture seule** : `DB` et `CONFIG` sont enveloppés ; tout `INSERT/UPDATE/DELETE/CREATE…` et tout
    `put/delete/deleteMany` lève `backup_read_only`. Le code du Worker traite déjà ces écritures comme facultatives
    (`touchDeviceActivity`, `touchDeviceSeen`, `noteEdition`, migration `docs.js` : erreurs avalées), donc `/api/config`
    marche et rien n'est écrit sur Turso pendant le secours. Garantit qu'aucun état divergent ne naît pendant la panne.
  - **Limiteurs** `RL_10/30/60/120` : limiteur simple en mémoire (fenêtre glissante 60 s par clé), par instance. Moins strict
    que Cloudflare (instances multiples), suffisant pour un secours de lecture ; les jetons de 256 bits ne se devinent pas.
  - **Durable Objects** : `GUARD` absent. `guardStub` lève, le Worker retombe sur « laisser passer » dans `limited()` et
    écrit sans verrou dans `withAccountLock` (inutile ici : lecture seule). Les routes qui exigent vraiment le DO
    (appairage, envoi, verrouillages de connexion) sont de toute façon refusées en 503 par la liste blanche.
  - **IP cliente** : le Worker lit `cf-connecting-ip` ; la couche le recopie depuis `x-real-ip` / `x-forwarded-for`
    de Vercel (non falsifiable côté Vercel pour `x-real-ip`).
  - `AI`, `ACCOUNT_RELAY_*`, `SESSION_SECRET`, `OPS_TOKEN`, `ADMIN_TOKEN`, clés TMDB, etc. : **non fournis** au secours
    (moins de secrets à exposer ; les routes qui les utilisent sont hors liste blanche).
- **Entrée** (`backup/api/index.js`) : `vercel.json` réécrit `/(.*)` vers cette fonction ; `src/handler.js` applique la
  liste blanche, le contrôle de configuration, puis appelle `worker.fetch(req, env, ctx)`. Réponses marquées
  `x-utv-backup: 1` (les applis peuvent le journaliser) et `cache-control: no-store` conservé.

## 3. Bascule dans les applis (Android, bureau)

Existant : repli domaine ↔ `workers.dev` sur panne réseau (`WorkerFallback.kt`, `web/src/cloud/client.ts`). On ajoute
une **3e adresse**, `https://utv-backup.vercel.app` (constante, remplaçable), jamais utilisée seule d'emblée.

- **Routes concernées** : `GET` sur `/api/config` et `/api/announcements` uniquement (liste blanche identique côté serveur).
  Les écritures ne basculent jamais (elles réessaient plus tard sur le principal, déjà géré par la synchro).
- **Critères de bascule** (après avoir essayé principal puis repli `workers.dev`) : panne réseau (DNS, connexion, TLS, délai),
  ou statut `500, 502, 503, 504`, ou `52x` (Cloudflare). Jamais pour 4xx, 304 ni 429 (un 401 reste un 401 : jeton révoqué).
- **Disjoncteur** : 2 échecs consécutifs du principal → les lectures éligibles vont **directement** au secours pendant 60 s
  (pas d'attente de délai à chaque appel), puis **une** requête d'essai repart vers le principal (retour au principal dès
  qu'elle réussit ; sinon 60 s de plus, jusqu'à 5 min).
- **Délai** : le principal garde son délai habituel ; le secours a 10 s.
- **Cache** : les applis gardent déjà la dernière configuration reçue. Le secours renvoie le même `ETag` (`"v<cfgVersion>"`,
  mêmes données) : un `304` du secours est valable. Aucun nouveau cache à gérer ; `no-store` conservé.
- **Sécurité côté appli** : HTTPS imposé, aucune redirection suivie, le jeton n'est envoyé qu'à cette adresse codée en dur
  (pas de saisie utilisateur). Une adresse de Worker auto-hébergée choisie par l'utilisateur **n'a pas** de secours.

## 4. Sécurité

- Mêmes jetons d'appareil (`utv_…`, empreinte SHA-256 dans Turso) : aucune table, aucun jeton nouveau.
- Même clé de chiffrement des fournisseurs : sans `PROVIDER_ENC_KEY` identique, `/api/config` ne peut pas déchiffrer.
  Les secrets Wrangler ne sont **pas lisibles** : le propriétaire doit les fournir (commandes en fin de document).
- Jeton Turso : créer un jeton **en lecture seule** pour le secours (`turso db tokens create <base> --read-only`) : même si
  la couche applicative a un défaut, Turso refuse toute écriture.
- Secrets Vercel : `vercel env add` (type `sensitive` pour le jeton Turso et la clé), jamais dans le dépôt ni les journaux.
  Le secours ne journalise ni jeton, ni corps.
- Protection de déploiement Vercel (Vercel Authentication) : doit laisser le domaine de production public, sinon les
  applis reçoivent 401. À vérifier après déploiement.
- Rotation : changer `PROVIDER_ENC_KEY` sur Cloudflare impose de la changer aussi ici (garder `…_PREVIOUS` pendant la rotation).

## 5. Offre Hobby : limites (doc Vercel, consultée le 17/11/2026)

- 1 000 000 d'invocations de fonctions / mois, 4 h de CPU actif, 360 Go-h de mémoire, 100 Go de transfert rapide, 1 M de requêtes CDN.
  Au-delà : la fonctionnalité se bloque jusqu'à 30 jours (pas de facturation sur Hobby). Un secours ne sert qu'en panne : marge large,
  mais une panne longue avec tout le parc qui sonde `/api/config` peut consommer l'invocation (le disjoncteur limite ce trafic).
- Durée max 300 s, 2 Go / 1 vCPU. 100 déploiements / jour. Journaux d'exécution : 1 heure seulement (peu d'observabilité).
- **Usage non commercial** : l'offre Hobby est réservée à un usage personnel non commercial (conditions Vercel). Ultra TV
  reçoit des dons et vend une édition Pro : risque de conformité, à trancher par le propriétaire (passer en Pro = 20 $/mois).
- Pas de région garantie proche des utilisateurs : région choisie (`vercel.json` `regions`), à proximité de Turso.

Risques : 1) latence du premier appel (démarrage à froid + connexion Turso) ; 2) Turso est un point commun : si Turso tombe,
le secours tombe aussi (il ne couvre qu'une panne de **Cloudflare**) ; 3) limiteur par instance, donc moins protecteur ;
4) divergence de code si le Worker change d'interface interne (le bundle doit être reconstruit à chaque évolution de `src/` :
test de fumée dans `npm test`) ; 5) quota Hobby ou conditions d'usage ; 6) une panne Vercel en même temps = repli sans effet,
les applis gardent leur dernière configuration locale.
