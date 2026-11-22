# Ultra TV dans le firmware KM2 OS — contrat d'intégration

Demande de la session « KM2 OS » le 2026-11-22 : firmware maison pour la box salon Mecool KM2 Plus (192.168.0.41,
Android 14 / API 34) où Ultra TV est l'appli TV intégrée et Kairo (`com.vantaflow.kairo`) le launcher par défaut.
À copier dans `vantaflow/km2-custom-tv-os/integrations/`. Le contrat Ultra TV ↔ Kairo détaillé reste
`docs/superpowers/specs/2026-11-21-integration-kairo.md` (dépôt Ultra TV) ; ce fichier ne le contredit pas.

## 1. Identité

| | Valeur |
|---|---|
| Paquet (release) | `com.ultratv.tv.nativeapp` |
| Paquet de développement | `com.ultratv.tv.nativeapp.debug` (pré-versions `-rcN`, à NE PAS intégrer au firmware) |
| Signature | APK Signature Scheme v3 avec chaîne de rotation (lineage) |
| Certificat actuel (SHA-256) | `702f820557cdc30a043a4181d87c21eb3f78126d4c244c4dac4a4156e99ca75d` (CN=Ultra TV, O=vantaflow, RSA 4096) |
| Anciens certificats de la chaîne | `d9f167658dbfdcc8aefae4986bed37aea836098a0529f6ae73c8910c3885d219` (O=khalilbenaz), `f2e0ec80dcadb8a57c18648f5ba08b0ce2520bc2fd1fbcf04123b4e11337185f` (Android Debug) |

Relevé le 2026-11-22 sur `UltraTV-1.3.72-arm64-v8a.apk` de la release GitHub v1.3.72 (`apksigner verify` + `apksigner lineage`).

- Pour un contrôle d'identité (allowlist, `signature` d'un `privapp-permissions`, vérification par Kairo) : utiliser le
  certificat actuel `702f82…a75d`. Le système accepte aussi les mises à jour signées par une clé plus récente de la même
  chaîne : une rotation future ne casserait pas les mises à jour, mais toute liste d'empreintes figée devrait être
  complétée. Aucune rotation n'est prévue ; si elle devait arriver, Kairo et KM2 OS seraient prévenus avant.
- Applis à intégrer : l'APK `arm64-v8a` de la release (la KM2 Plus est 64 bits), jamais l'APK `debug`.

## 2. Points d'entrée pour Kairo

Activité principale exportée, `ACTION_VIEW`, schéma `ultratv` (filtre déjà présent). Un lien ne porte jamais d'adresse de
flux : il est résolu dans le catalogue local. Un lien inconnu ou mal formé ouvre l'accueil.

| Besoin | Lien | État |
|---|---|---|
| Chaîne précise | `ultratv://live/{providerId}/{remoteId}` | existe |
| Film (lecture) / fiche film | `ultratv://movie/…` / `ultratv://moviedetail/{providerId}/{remoteId}` | existe |
| Série / épisode | `ultratv://series/…` / `ultratv://episode/…` | existe |
| Recherche | `ultratv://search?q=…` (+ `ACTION_SEARCH`) | existe |
| Accueil, TV en direct, Guide, Films, Séries | `ultratv://home`, `ultratv://live`, `ultratv://guide`, `ultratv://movies`, `ultratv://series` | en cours (branche `feat/accueil-tv-streaming`) |
| Catégorie de chaînes | `ultratv://live?category=…` | en cours si simple |
| Reprise de lecture | liens de la rangée Android TV « Continuer à regarder » (WatchNext) | existe |

Rangées système Android TV (fournisseur `android.media.tv`, lisibles par tout launcher) : « Continuer à regarder »,
chaînes de prévisualisation « Favoris » et « Nouveautés » ; « Chaînes récentes » en cours ; images paysage 16:9 en cours.

Fournisseur réservé à Kairo (en cours) : autorité `com.ultratv.tv.nativeapp.kairo`, `content://…/now` (en cours / suivant
des chaînes favorites puis récentes, 20 lignes au plus) et `content://…/profile` (profil enfant ou non). Protégé par le
paquet appelant `com.vantaflow.kairo` ET l'empreinte de signature de Kairo
`b5c0901798aa6a67b3cd619c56c27057431eec2681c9b1799f9afc2aba5e8c3e`. Colonnes dans le contrat Kairo.
« Continuer à regarder » et les favoris restent servis par les rangées système ci-dessus (pas de doublon dans ce
fournisseur).

## 3. Permissions en appli système

Ultra TV fonctionne sans privilège ; tout ce qui suit est facultatif.

| Permission | Pourquoi | Type |
|---|---|---|
| `android.permission.RECEIVE_BOOT_COMPLETED` | déjà demandée (rangées TV, synchro planifiée) | normale |
| `android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION` | mise à jour intégrée sans écran de confirmation (API 31+, Ultra TV doit être l'installeur de référence de son propre paquet) | normale, pas de liste blanche |
| `android.permission.INSTALL_PACKAGES` | seulement si la précédente ne suffit pas sur ce firmware | privilégiée → `privapp-permissions` |
| `com.android.providers.tv.permission.WRITE_EPG_DATA` | déjà demandée (rangées TV) | déjà accordée |
| Exclusion de l'optimisation batterie / veille | synchro de nuit et enregistrements planifiés | `<allow-in-power-save package="com.ultratv.tv.nativeapp"/>` dans `sysconfig` |

Décodage matériel : rien de spécifique (Media3 / MediaCodec et VLC utilisent les décodeurs du SoC). Pas de permission
système demandée pour l'audio. Ultra TV n'a pas besoin d'être `persistent` ni signée avec la clé plateforme.

Côté Ultra TV, le code de mise à jour silencieuse n'existe pas encore : il sera ajouté (repli sur l'écran actuel si la
permission manque) quand le firmware sera prêt. À prévenir.

## 4. Intégration recommandée

- APK dans `/product/priv-app/UltraTV/` seulement si `INSTALL_PACKAGES` est retenue, sinon `/product/app/UltraTV/`.
- Les mises à jour arrivent par la release GitHub (même paquet, même chaîne de signature) et s'installent par-dessus
  l'APK système ; ne pas bloquer ce chemin.
- Aucune donnée d'Ultra TV à préremplir (sources, comptes) dans l'image.
