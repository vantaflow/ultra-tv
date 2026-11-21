# Intégration avec le launcher Kairo — contrat

Accord du propriétaire le 2026-11-21 : « Faut exposer ce que Kairo veut pour l'intégration. » Contrat convenu avec la
session qui développe Kairo (`com.vantaflow.kairo`). À réaliser après les correctifs en cours de la branche
`feat/accueil-tv-streaming`.

## Ce qui existe et ne doit pas changer

- Liens profonds (activité principale, `ACTION_VIEW`, schéma `ultratv`) :
  `ultratv://live|movie|episode|series|moviedetail/{providerId}/{remoteId}` et `ultratv://search?q=…`.
  Un lien ne porte jamais d'adresse de flux : il est résolu dans le catalogue local.
- Rangées Android TV : « Continuer à regarder » (films et épisodes en 2:3, chaînes en 16:9), chaîne de prévisualisation
  « Favoris », chaîne de prévisualisation « Nouveautés » (20 éléments au plus, affiches 2:3).

## Ajouts, par ordre d'utilité pour Kairo

1. **Images paysage 16:9** des films et séries dans « Continuer à regarder » et « Nouveautés » : le fond du titre quand
   il existe, sinon l'affiche comme aujourd'hui.
2. **Liens profonds de rubrique** : `ultratv://home`, `ultratv://live` (liste des chaînes), `ultratv://guide`,
   `ultratv://movies`, `ultratv://series` ; une catégorie si c'est simple (`ultratv://live?category=…`), sinon plus tard.
   Un lien inconnu ou mal formé ouvre l'accueil, jamais un écran vide ni un plantage.
3. **Chaîne de prévisualisation « Chaînes récentes »**, distincte des favoris.
4. **En cours / suivant par chaîne**, lisible par Kairo seulement (voir ci-dessous).
5. Correction liée : la rangée « Nouveautés » ne suit pas le profil actif (elle prend tout le catalogue de la source) ;
   elle doit respecter le profil, en particulier un profil enfant.

## Fournisseur « en cours / suivant »

- `ContentProvider` en lecture seule (insert / update / delete refusés), exporté.
- Autorité : `{applicationId}.kairo` — `com.ultratv.tv.nativeapp.kairo` ou `com.ultratv.tv.nativeapp.debug.kairo`.
- Protection : à CHAQUE requête, vérification du paquet appelant (`com.vantaflow.kairo`) ET de l'empreinte SHA-256 de
  son certificat de signature contre une LISTE d'empreintes autorisées (plusieurs entrées possibles). Tout autre
  appelant reçoit un curseur vide et une ligne de journal (sans donnée sensible). Pas de permission déclarée : les deux
  applis n'ont pas la même clé.
  - Empreinte communiquée par Kairo (APK publié v1.26.0 et build installé sur la box, identiques) :
    `b5c0901798aa6a67b3cd619c56c27057431eec2681c9b1799f9afc2aba5e8c3e`.
  - Décision convenue avec Kairo le 2026-11-21 : la liste ne contient QUE cette empreinte. L'ancienne clé
    (`51ad3989…`) a signé Kairo 1.0.0 à 1.21.0, versions qui ne liront jamais ce fournisseur ; Kairo garde `b5c09017…`
    pour toutes ses versions à venir (publiées par l'automatisation de son dépôt) et prévient avant tout changement de
    clé. La liste reste une liste : une nouvelle empreinte s'ajoute par une nouvelle version d'Ultra TV.
  - À l'implémentation : revérifier l'empreinte sur le paquet réellement installé sur la box (ne pas se fier au seul
    message), et la figer dans une constante couverte par un test.
- `content://{autorité}/now` — une ligne par chaîne, 20 lignes au plus, favoris d'abord puis chaînes récentes, du profil
  actif et de la source active. Colonnes :

  | Colonne | Type | Contenu |
  |---|---|---|
  | `provider_id` | long | identifiant local de la source |
  | `remote_id` | texte | identifiant de la chaîne chez le fournisseur |
  | `name` | texte | nom de la chaîne |
  | `number` | entier | numéro affiché, peut être nul |
  | `logo` | texte | adresse de l'image, vide si elle porterait un identifiant de fournisseur |
  | `now_title` | texte | vide si le guide n'a rien, ou si la chaîne est verrouillée |
  | `now_start_ms`, `now_end_ms` | long | millisecondes UTC, 0 si inconnu |
  | `now_image` | texte | facultatif, peut être vide |
  | `next_title` | texte | vide si inconnu ou chaîne verrouillée |
  | `next_start_ms`, `next_end_ms` | long | millisecondes UTC, 0 si inconnu |
  | `deep_link` | texte | `ultratv://live/{providerId}/{remoteId}` |
  | `locked` | entier | 1 si la chaîne est sous contrôle parental (titres vides) |

- `content://{autorité}/profile` — une ligne, colonne `kids` (0 / 1) du profil actif. Ni nom ni identifiant de profil.
- Jamais d'adresse de flux, d'identifiant de fournisseur, de nom de profil ni de description de programme.
- `notifyChange` sur l'URI concernée, au plus une fois par minute. Kairo lit quand son accueil revient au premier plan
  et sur notification ; si l'autorité est absente (Ultra TV non installée ou trop ancienne), il retombe sur les rangées
  Android TV.
- Coût : aucune requête lourde sur le fil principal, résultat borné, rien calculé tant que personne ne lit.

## Tests attendus

Appelant autorisé / paquet inconnu / bon paquet avec mauvaise signature ; 20 lignes au plus et ordre ; chaîne
verrouillée sans titres ; profil enfant ; aucune colonne ne contient d'adresse de flux ni d'identifiant (vecteurs de
`cloudflare-config/test/fixtures/sanitize-vectors.json`) ; liens de rubrique valides, inconnus et mal formés.

Chaque ajout livré est annoncé à la session Kairo avec le numéro de version d'Ultra TV qui le contient.
