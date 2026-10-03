# Ultra TV — Worker de configuration (appairage par jeton d'appareil)

Un Worker Cloudflare qui laisse l'utilisateur gérer ses fournisseurs IPTV depuis un
navigateur et les faire récupérer par la TV. Version 2 : **aucune lecture anonyme,
aucun secret partagé dans l'APK**.

```
cloudflare-config/
├── src/index.js      routeur et handlers
├── src/guard.js      Durable Object : débit, verrouillage, codes d'appairage
├── src/crypto.js     AES-GCM, PBKDF2, jetons, comparaison en temps constant
├── src/sanitize.js   nettoyage des identifiants dans les journaux
├── src/http.js       en-têtes de sécurité, cookies signés, CSRF, lecture bornée
├── src/pages.js      pages HTML (CSP à nonce)
├── src/store.js      comptes, appareils, validation des fournisseurs
├── test/             113+ tests vitest (attaques rejouées, parcours, migration)
└── scripts/e2e.sh    rejeu des attaques contre `wrangler dev`
```

## Modèle de menace (résumé)

| Menace | Contre-mesure |
|---|---|
| Lire la config d'autrui en devinant une MAC | La MAC n'est plus une clé. Lecture = jeton d'appareil de 256 bits (`Authorization: Bearer`) ; `GET /api/config/:mac` répond `410`. |
| Fuite de la base KV | Identifiants des fournisseurs chiffrés AES-256-GCM (clé dans un secret Wrangler, AAD = compte) ; mots de passe PBKDF2 ; jetons d'appareil stockés hachés (SHA-256). |
| Force brute du mot de passe | PBKDF2-SHA256 100 000 itérations + sel ; limite par IP et par compte ; verrouillage progressif 60 s, doublé à chaque échec, plafonné (15 min compte, 1 h IP). Compteurs dans un Durable Object (cohérence forte). |
| Squatter le compte d'une MAC | Plus de compte « par MAC » : on s'inscrit avec un identifiant et un mot de passe ; l'appareil se lie par code. Un ancien compte-MAC non migré ne peut pas être inscrit par un tiers. |
| Voler le jeton d'un appareil | Révocation depuis le tableau de bord (effet immédiat sur le Worker, jusqu'à ~60 s ailleurs : cache KV), rotation `POST /api/device/rotate`, effacement automatique côté app si 401. |
| XSS / clickjacking | CSP `default-src 'none'` + nonce (aucun `unsafe-inline`), échappement systématique, `X-Frame-Options: DENY`, pas d'attribut `on*=` ni `style=`. |
| CSRF | Jeton lié à la session (HMAC) + contrôle de l'en-tête `Origin` + cookie `__Host-` `SameSite=Strict`. Aucun CORS ouvert. |
| Lire les crashs/logs d'autrui | `/crashes` et `/logs` : secret serveur `OPS_TOKEN` (Basic ou Bearer), jamais en query string, jamais dans l'APK, verrouillage par IP. |
| Spam d'ingestion | Réservée aux appareils appairés ; 60 événements/h et 20 crashs/h par appareil, 300/h par IP ; corps ≤ 8 Ko (événement) / 48 Ko (crash). |
| Identifiants dans les logs | Nettoyés côté app **et** côté serveur (`username=`, `password=`, `token=`, `user:pass@`, `/live/u/p/`, Bearer). |

## Déploiement

```bash
cd cloudflare-config
npm ci

# 1) Namespace KV (coller les ids dans wrangler.toml)
wrangler kv namespace create CONFIG
wrangler kv namespace create CONFIG --preview

# 2) Secrets (JAMAIS dans un fichier versionné)
wrangler secret put SESSION_SECRET     # >= 32 caractères aléatoires : openssl rand -base64 48
wrangler secret put PROVIDER_ENC_KEY   # clé AES-256 en base64      : openssl rand -base64 32
wrangler secret put OPS_TOKEN          # >= 32 caractères : mot de passe de /crashes et /logs
wrangler secret put ADMIN_TOKEN        # >= 32 caractères, uniquement pour la migration (à supprimer ensuite)

# 3) Vérifier puis déployer
npm test
wrangler deploy --dry-run              # validation seule
wrangler deploy                        # déploiement réel (le Durable Object `Guard` est créé par la migration v1)
```

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
| GET | `/api/config` | `Bearer` jeton d'appareil | Fournisseurs déchiffrés pour cet appareil |
| POST | `/api/device/rotate` | `Bearer` | Nouveau jeton, l'ancien meurt |
| POST | `/api/event`, `/api/crash` | `Bearer` | Télémétrie nettoyée et bornée |
| GET | `/api/config/:mac` | — | **410 Gone** (ancienne lecture anonyme supprimée) |
| GET | `/crashes`, `/logs` | Basic/Bearer `OPS_TOKEN` | Tableaux de bord d'exploitation |
| POST | `/api/admin/migrate` | Bearer `ADMIN_TOKEN` | Migration de l'ancien format (voir ci-dessous) |
| GET/POST | `/login`, `/signup` | — | Comptes |
| GET | `/` | session | Tableau de bord |
| POST | `/pair`, `/providers`, `/providers/:id/delete`, `/devices/:id/revoke`, `/password`, `/account/delete`, `/logout` | session + CSRF + Origin | Mutations |

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
- KV est éventuellement cohérent : une révocation peut mettre jusqu'à ~60 s à se propager hors du point de
  présence qui l'a reçue.
- Le jeton d'appareil transite en clair dans le Durable Object entre la saisie du code et la remise à la TV
  (au plus 10 minutes, supprimé à la remise).
