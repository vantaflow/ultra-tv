# Déploiement du masquage des journaux (Worker `ultratv-config`)

Procédure pour la personne qui déploie. **Chaque commande est tapée par vous, dans votre propre terminal.** Aucune
étape ne doit être confiée à un agent ni à un outil qui relance les commandes à votre place : sur cette machine, les
commandes lancées par un agent passent automatiquement par une enveloppe (`rtk`). Elle a déjà fait enregistrer un
secret **vide**.

Tout se passe dans `cloudflare-config/` :

```sh
cd ~/Projects/ultra-tv-main/cloudflare-config
```

Repères, vérifiés dans le dépôt :

| Élément | Valeur | Source |
|---|---|---|
| Worker | `ultratv-config` | `wrangler.toml`, ligne 1 |
| Environnement | aucun (pas de section `[env]`) : jamais d'option `--env` | `wrangler.toml` |
| Adresse de l'API | `https://tv.weedy.dpdns.org` (le site est `https://ultratv.dpdns.org`) | `wrangler.toml`, `routes` |
| Stockage | Turso, `TURSO_URL = "libsql://ultratv-vantaflow.aws-eu-central-1.turso.io"` | `wrangler.toml` |
| Base D1 de secours | `ultratv-telemetry`, non liée au Worker | commentaire de `wrangler.toml` |
| Tests | `npm test` (D1 simulée), `npm run test:turso` (Turso simulé) | `package.json` |
| Script de réécriture | `node scripts/resanitize-events.mjs` | `scripts/` |

Ordre : 1. secret, 2. déploiement, 3. vérification, 4. réécriture des anciennes lignes, 5. décision sur la D1 de
secours. Ne passez à une étape que si la précédente a réussi.

---

## Étape 0 : avant de commencer

1. Ouvrez un terminal à vous (Terminal, iTerm…), pas une session d'agent.
2. Vérifiez que vous êtes connecté au bon compte Cloudflare : `npx wrangler whoami`.
3. Vérifiez que la version en production est bien celle de `main` (lecture seule) :
   ```sh
   npx wrangler deployments list
   ```
   Le dernier déploiement doit correspondre à `origin/main`. Si quelqu'un a déployé depuis une autre branche,
   `wrangler deploy` l'écrasera : arrêtez-vous et voyez avec cette personne.
4. Gardez une fenêtre ouverte sur le tableau de bord (`https://ultratv.dpdns.org/logs`, connecté avec le compte
   administrateur) : il sert à l'étape 3.

---

## Étape 1 : créer le secret `LOG_HOST_SALT`

Le secret sert à calculer l'empreinte des hôtes (`<host#` suivi de 12 caractères hexadécimaux). Il doit faire **au
moins 32 caractères**. En dessous, le Worker l'ignore et écrit `<host>` sans empreinte.

**Où le garder.** Dans votre gestionnaire de mots de passe, sous un nom clair (par exemple « Ultra TV —
LOG_HOST_SALT »). Le script de réécriture de l'étape 4 en a besoin, avec exactement la même valeur. Cloudflare ne
permet pas de relire un secret.

### 1.a Commande (recommandée)

```sh
LOG_HOST_SALT=$(openssl rand -hex 32)          # 64 caractères ; la valeur n'entre pas dans l'historique du shell
printf %s "$LOG_HOST_SALT" | wc -c             # doit afficher 64
printf %s "$LOG_HOST_SALT" | pbcopy            # collez-la MAINTENANT dans le gestionnaire de mots de passe
pbcopy < /dev/null                             # puis videz le presse-papiers
printf %s "$LOG_HOST_SALT" | npx wrangler secret put LOG_HOST_SALT
unset LOG_HOST_SALT
```

`wrangler secret put` lit la valeur sur son entrée standard : elle n'apparaît ni à l'écran ni dans l'historique.
Cette commande crée aussi une nouvelle version du Worker **avec le code actuellement en production**. C'est sans
effet, car l'ancien code ignore ce secret.

### 1.b Variante : `wrangler secret bulk` avec un fichier temporaire

À utiliser si la commande `put` ne lit pas l'entrée standard dans votre terminal :

```sh
LOG_HOST_SALT=$(openssl rand -hex 32)
printf %s "$LOG_HOST_SALT" | pbcopy            # dans le gestionnaire de mots de passe, puis :
pbcopy < /dev/null
f=$(mktemp) && chmod 600 "$f"                  # fichier lisible par vous seul
printf '{"LOG_HOST_SALT":"%s"}' "$LOG_HOST_SALT" > "$f"
npx wrangler secret bulk "$f"
rm -f "$f"; unset LOG_HOST_SALT f              # supprimé aussitôt après
```

### Ce qui ne prouve rien

`npx wrangler secret list` n'affiche que le **nom** du secret, jamais sa valeur ni sa longueur. Un secret vide y
apparaît aussi. Ne la prenez pas pour une vérification. La vraie vérification est l'étape 3 (`<host#…>` et non
`<host>`).

### Si quelque chose se passe mal

- **La commande demande la valeur de façon interactive** : vous êtes sans doute dans un terminal où l'entrée
  standard n'est pas transmise. Faites Ctrl-C, puis passez à la variante 1.b. Ne tapez pas la valeur à la main.
- **Vous avez fermé le terminal avant de copier la valeur** : recommencez l'étape 1 en entier, avec une nouvelle
  valeur. Elle remplace l'ancienne. Tant que l'étape 4 n'a pas été lancée, rien n'est perdu.
- **La valeur s'est affichée, ou a été collée dans un fichier ou une discussion** : recommencez l'étape 1 avec une
  nouvelle valeur. Celui qui connaît le secret peut retrouver les hôtes à partir des empreintes.
- **Erreur d'authentification Cloudflare** : `npx wrangler login`, puis recommencez.

---

## Étape 2 : déployer

```sh
npm ci
npm test                    # référence : tous les tests verts
npm run test:turso          # 4 échecs connus et préexistants, pas plus (voir plus bas)
npx wrangler deploy
date -u +%Y-%m-%dT%H:%M:%SZ # NOTEZ cette heure : c'est T, la fin du déploiement, en UTC
```

- `npm test` : un test de limite de débit (`envoi.test.js`, « limite de débit sur la création de code ») échoue
  parfois, à la frontière d'une période du limiteur. Relancez-le seul avec
  `npx vitest run test/envoi.test.js` : s'il passe, ce n'est pas un problème.
- `npm run test:turso` : les 4 échecs connus sont `etat_ancienKv_toujoursLu`,
  `watchlist : écrit chez Trakt…`, `migration_ancienCompteProtege_loginParMac_etClairSupprime` et
  `signup_macHeriteeNonMigree_refuseAuxTiers`. Un cinquième échec : ne déployez pas.
- Notez aussi l'identifiant de version affiché par `wrangler deploy` (« Current Version ID »).

### Retour arrière

```sh
npx wrangler deployments list               # repérer l'identifiant de la version précédente
npx wrangler rollback <identifiant-de-version> -m "retour arrière masquage"
```

Sans identifiant, `npx wrangler rollback` revient à la version précédente. Ce que cela implique :

- **Code** : l'ancien Worker reprend immédiatement.
- **Secret** : `LOG_HOST_SALT` reste posé. L'ancien code l'ignore.
- **Base** : aucune migration, donc la base reste cohérente. Les lignes écrites par le nouveau Worker restent
  (`mac` vide, `<host#…>`).
- **Réécriture** : les lignes déjà réécrites à l'étape 4 le restent. On ne peut revenir dessus que par une
  restauration de Turso à un instant donné, ce qui ramènerait aussi les identifiants.
- **Fuites** : l'ancien Worker se remet à écrire des lignes masquées seulement en partie
  (`password=<redacted> FIN`). Les fuites reprennent jusqu'au prochain déploiement.
- **Pertes** : les journaux perdus pendant une panne de stockage (coupe-circuit de 60 s) ne reviennent pas.

### Si quelque chose se passe mal

- **Un test échoue** (autre que les cas connus ci-dessus) : ne déployez pas, et transmettez la sortie.
- **`wrangler deploy` échoue** : rien n'a changé en production. Lisez le message (connexion, droits). Si
  `npm run check` (essai à blanc, sans envoi) échoue aussi, le problème est dans le dépôt.
- **Erreurs 5xx ou plantages en hausse juste après** : faites le retour arrière ci-dessus, puis transmettez la sortie de
  `npx wrangler tail --format pretty --status error` (lancée avant de reproduire).

---

## Étape 3 : vérifier le masquage et le secret

**Lancez d'abord le suivi des journaux**, dans un second terminal. L'avertissement sur le secret n'est écrit qu'une
fois par instance du Worker : s'il est émis avant que `tail` écoute, vous ne le verrez pas.

```sh
cd ~/Projects/ultra-tv-main/cloudflare-config
npx wrangler tail --format pretty
```

Ensuite, dans le premier terminal, envoyez une ligne d'essai **fictive**. Elle passe par la route sans compte
(`/api/anon/events`), avec :
- un identifiant d'installation valide (16 à 64 caractères parmi les lettres, chiffres et tirets) ;
- le niveau `error`, pour qu'elle ne soit pas écartée par l'échantillonnage, qui garde 1 ligne d'info sur 10.

Elle reprend la forme déjà masquée par l'ancien Worker (`password=<redacted> ZZFINZZ`).

```sh
curl -sS -X POST https://tv.weedy.dpdns.org/api/anon/events \
  -H 'content-type: application/json' \
  --data-raw '{"install":"essai-masquage-0000-0000-000000000001","version":"0.0.0-essai","device":"Essai masquage","events":[{"level":"error","tag":"EssaiMasquage","message":"essai masquage 1 http://essai-masquage.invalid/live/ZZUSERZZ/ZZPASSZZ ZZFINZZ/1.ts password=<redacted> ZZFINZZ code=401"}]}'
```

Réponse attendue : `{"ok":true,"accepted":1,"stored":1,"dropped":{"filtered":0,"invalid":0,"overflow":0}}`.

Sur `https://ultratv.dpdns.org/logs` (filtre de version `0.0.0-essai`), la ligne doit apparaître ainsi :

```
essai masquage 1 http://<host#XXXXXXXXXXXX>/live/*.ts password=<redacted> code=401
```

**Critère de réussite** :
- `<host#` suivi d'exactement **12 caractères hexadécimaux** (`0-9a-f`) ;
- aucune trace de `ZZUSERZZ`, `ZZPASSZZ`, `ZZFINZZ` ni `essai-masquage.invalid` ;
- dans le terminal de `tail`, aucun message `LOG_HOST_SALT absent ou trop court`.

**Notez l'empreinte** (les 12 caractères) : elle sert à l'étape 4.

### Si quelque chose se passe mal

- **`<host>` sans `#`**, ou l'avertissement `LOG_HOST_SALT absent ou trop court` dans `tail` : le secret est vide
  ou fait moins de 32 caractères. Refaites l'étape 1 (nouvelle valeur), puis renvoyez une ligne en changeant le
  numéro du message (`essai masquage 2`). Un secret posé après coup est pris en compte sans redéployer.
- **`"stored":0` avec `"filtered":1`** : une ligne identique (même appareil, même étiquette, même message) a déjà été
  reçue il y a moins de 30 minutes. Changez le numéro dans le message et renvoyez.
- **`429`** : limite de débit de la route sans compte. Attendez la durée indiquée par `Retry-After`, puis renvoyez
  avec un autre identifiant d'installation (changez le dernier chiffre).
- **`200` avec `"ok":false` et `"storage"`** : le Worker n'a pas pu écrire dans Turso. Regardez `tail` (ligne
  `telemetry … error: "storage"`). Turso est-il disponible ? Le jeton `TURSO_AUTH_TOKEN` du Worker est-il valide ?
  Si l'écriture échoue aussi pour les vrais appareils, faites le retour arrière de l'étape 2.
- **`ZZFINZZ` ou `ZZPASSZZ` visible** : le code déployé n'est pas le bon. Vérifiez la branche et le commit, puis
  revenez en arrière.

---

## Étape 4 : réécrire les anciennes lignes Turso

Les lignes antérieures au déploiement ont été écrites par l'ancien Worker. Elles sont masquées en partie seulement
(`password=<redacted> FIN`, `http://<redacted>@hôte`, colonne `mac`). Le script les repasse toutes par le nettoyage
actuel. Il n'affiche jamais le contenu d'une ligne : seulement des compteurs et des identifiants numériques.

### 4.a Jeton Turso avec droit d'écriture

Le script a besoin d'un jeton Turso en lecture et écriture. Créez-en un à durée courte, depuis la CLI Turso
(`turso auth login` au besoin) ou depuis le tableau de bord Turso (base `ultratv`, onglet des jetons) :

```sh
turso db tokens create ultratv --expiration 1d
```

Ne lancez **jamais** `turso db tokens invalidate` : cette commande révoque tous les jetons de la base, y compris
celui du Worker, et tout le stockage tomberait.

### 4.b Variables, sans rien laisser dans l'historique

```sh
export TURSO_URL=libsql://ultratv-vantaflow.aws-eu-central-1.turso.io   # valeur de wrangler.toml
printf 'Jeton Turso : '; read -rs TURSO_AUTH_TOKEN; echo; export TURSO_AUTH_TOKEN
printf 'LOG_HOST_SALT : '; read -rs LOG_HOST_SALT; echo; export LOG_HOST_SALT   # collé depuis le gestionnaire
printf %s "$LOG_HOST_SALT" | wc -c      # doit afficher 64 : ni espace ni retour à la ligne en trop
```

### 4.c Contrôle du secret, AVANT toute écriture

Une valeur mal recopiée (une espace, un caractère en moins) donnerait aux anciennes lignes des empreintes
différentes de celles du Worker. Comme le script est idempotent, ces empreintes ne seraient plus jamais corrigées.
Calculez l'empreinte de l'hôte fictif de l'étape 3 avec la valeur saisie :

```sh
node --input-type=module -e 'import { hostFingerprint } from "./src/sanitize.js"; console.log(hostFingerprint("essai-masquage.invalid", process.env.LOG_HOST_SALT))'
```

Le résultat doit être **identique**, caractère pour caractère, aux 12 caractères notés à l'étape 3. Sinon, n'allez pas
plus loin : ressaisissez `LOG_HOST_SALT` (4.b) et recommencez le contrôle.

### 4.d Comptage, réécriture, contrôle indépendant

`--before` vaut **T + 5 minutes**, où T est l'heure notée à l'étape 2. Pendant quelques secondes après la fin du
déploiement, d'anciennes instances du Worker peuvent encore écrire. Les 5 minutes de marge les couvrent. Les lignes
du nouveau Worker comprises dans ces 5 minutes repassent sans changement, puisque le nettoyage est idempotent. Le
script refuse une date dans le futur : attendez que T + 5 minutes soit passé.

Par exemple, si T = `2026-11-22T10:00:00Z`, alors `--before 2026-11-22T10:05:00Z` :

```sh
B=2026-11-22T10:05:00Z     # à remplacer par T + 5 minutes

node scripts/resanitize-events.mjs --before "$B"           # 1) compte seulement (aucune écriture)
node scripts/resanitize-events.mjs --before "$B" --apply   # 2) écrit, par lots de 50, chaque lot dans une transaction
node scripts/resanitize-events.mjs --before "$B"           # 3) contrôle
```

Chaque passe se termine par une ligne JSON, par exemple :

```
{"apply":false,"scanned":10000,"changed":6571,"written":0,"residue":{"before":4000,"after":0},…}
```

- **Passe 1** : `residue.before` donne le nombre de lignes où un `<redacted>` est encore suivi d'une fin
  d'identifiant. `residue.after` doit valoir **0** : c'est ce qui resterait après réécriture. S'il n'est pas nul, ou
  si le message `ATTENTION : … reste d'identifiant` apparaît, **n'écrivez pas**. Une règle manque, il faut la
  signaler.
- **Passe 2** : `written` doit être égal à `changed`.
- **Passe 3 (critère de fin)** : `"residue":{"before":0,"after":0}`. Ce comptage ne dépend pas des règles du
  nettoyage : c'est une expression simple qui cherche `password=<redacted> mot`, `user=<redacted> mot`,
  `authorization=<redacted> jeton`… Ne vous fiez pas à `"changed":0` seul : une règle manquante donne aussi
  `changed: 0` alors que la fin du mot de passe est toujours là.

Ensuite, effacez les variables : `unset TURSO_AUTH_TOKEN LOG_HOST_SALT TURSO_URL B`. Le jeton à durée courte
expire de lui-même.

Coût : une lecture de 50 lignes par lot et au plus une écriture groupée par lot. Pour environ 10 000 lignes, cela
fait environ 400 requêtes, ce qui est négligeable devant les quotas Turso.

Essai préalable possible, sans toucher à la production, sur une copie locale :
`TURSO_URL=file:/chemin/copie.sqlite node scripts/resanitize-events.mjs --before "$B"`.

### Si quelque chose se passe mal

- **Le script s'arrête (`échec : …`) au milieu** : chaque lot est atomique, donc rien n'est à moitié écrit. Relancez
  la même commande avec `--after-id <dernier id affiché>`, ou relancez depuis le début : c'est sans risque.
- **`--before : date dans le futur refusée`** : attendez que l'heure soit passée.
- **`LOG_HOST_SALT manquant ou trop court`** : la variable n'est pas exportée, ou fait moins de 32 caractères.
  Reprenez 4.b.
- **Erreur d'authentification Turso (401 ou 403)** : jeton expiré ou en lecture seule. Recréez-en un (4.a).
- **L'empreinte de 4.c diffère** : n'écrivez rien. Si la valeur du gestionnaire de mots de passe est perdue ou
  douteuse, recommencez l'étape 1 avec une nouvelle valeur, puis l'étape 3 (nouvelle empreinte notée), puis 4.c.
- **`--apply` a été lancé avec une mauvaise valeur** : les fuites sont bien retirées. Seul le regroupement par
  fournisseur des anciennes lignes est faussé, pendant 7 jours pour les journaux et 30 jours pour les plantages, le
  temps qu'elles expirent. Ce n'est pas un problème de confidentialité.
- **Passe 3 avec `residue.before` > 0** : relancez `--apply` une fois. Si le nombre ne baisse pas, une règle manque :
  arrêtez-vous et signalez-le, avec les seuls compteurs, sans copier de ligne.

---

## Étape 5 : base D1 de secours `ultratv-telemetry` (décision de la propriétaire)

Le Worker n'est plus lié à cette base depuis le passage à Turso (15/11/2026). Elle garde les journaux et plantages
d'avant la bascule, **non nettoyés** : identifiants à moitié masqués, étiquettes MAC, peut-être des e-mails. Le
script de l'étape 4 ne la touche pas. Trois options ; aucune n'est appliquée sans votre décision.

Pour compter d'abord (lecture seule) :

```sh
npx wrangler d1 execute ultratv-telemetry --remote --command "SELECT kind, COUNT(*) AS n FROM events GROUP BY kind"
```

| Option | Commandes | Conséquences |
|---|---|---|
| **Vider les journaux** (recommandé) | `npx wrangler d1 execute ultratv-telemetry --remote --command "DELETE FROM events; UPDATE anon_pings SET model = NULL"`, puis la requête de comptage (0 attendu) | La base et le chemin de retour arrière vers D1 (`scripts/turso-migrate.mjs`) restent ; les anciennes lignes non nettoyées disparaissent. |
| **Supprimer la base** | `npx wrangler d1 delete ultratv-telemetry` (demande confirmation ; **irréversible**) | Plus aucune copie. Plus de retour arrière vers D1 sans tout recréer (`wrangler d1 create`, migrations, `turso-migrate.mjs`). Il faut ensuite mettre à jour le commentaire de `wrangler.toml` et `D1_NAME` dans `scripts/turso-migrate.mjs`. |
| **Laisser** | aucune | Les anciennes lignes ne sont jamais purgées. Un retour arrière vers D1 recopierait en plus les lignes de Turso vers D1, d'où l'intérêt d'avoir fait l'étape 4 avant. |

Dans tous les cas :
- la restauration à un instant donné de D1 (Time Travel) et celle de Turso gardent l'historique pendant leur durée
  de rétention, même après un `DELETE` ;
- l'espace KV `CONFIG`, conservé, contient peut-être encore d'anciennes clés `crash:*`, qui expirent d'elles-mêmes
  sous 30 jours.

### Si quelque chose se passe mal

- **`no such table: anon_pings`** : la table n'existe pas dans cette base. Lancez seulement `DELETE FROM events`.
- **Base introuvable** : vérifiez le compte Cloudflare (`npx wrangler whoami`) et la liste (`npx wrangler d1 list`).
- **Suppression lancée par erreur** : elle est irréversible. Le Worker n'en dépend pas : rien ne casse en
  production. Seul le chemin de retour arrière vers D1 est perdu.
